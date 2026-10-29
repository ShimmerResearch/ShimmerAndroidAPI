package com.shimmerresearch.hiltest;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.clj.fastble.BleManager;
import com.shimmerresearch.android.manager.ShimmerBluetoothManagerAndroid;
import com.shimmerresearch.bluetooth.BluetoothProgressReportPerDevice;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driverUtilities.AssembleShimmerConfig;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * One Shimmer, driven through the public Android API the way an app would: the Bluetooth manager,
 * a Handler for callbacks, and the ShimmerBluetooth calls. Records state changes and, while
 * recording, every data packet.
 */
final class ShimmerSession implements AutoCloseable {

	static final String TAG = "ShimmerHil";

	/** What the API logs (Log.d, tag "Shimmer") when its streaming parser loses its place. */
	static final String[] PARSER_ERRORS = {"Packet syncing problem", "Unknown parsing error while streaming"};

	interface Condition {
		boolean holds();
	}

	final String mac;
	/** "classic" (Android's own Shimmer class and parser) or "ble" (Shimmer3BLEAndroid, the driver's parser). */
	final String transport;
	/** "Shimmer3" or "Shimmer3R": over BLE the manager picks the device class from it. */
	final String deviceType;
	private final HandlerThread callbackThread = new HandlerThread("ShimmerHilCallbacks");
	private final ShimmerBluetoothManagerAndroid manager;
	private final long t0 = SystemClock.elapsedRealtime();
	private final List<String> events = new ArrayList<>();
	private final List<StreamAnalysis.Sample> samples = new ArrayList<>();
	private volatile boolean recording;
	private volatile BT_STATE lastState;
	private volatile BluetoothProgressReportPerDevice.OperationState configureResult;
	private volatile int stateMessages;
	private final java.util.Set<Integer> otherMessagesSeen = new java.util.HashSet<>();

	/**
	 * Since the last connect: when the app was first told the unit is ready (a CONNECTED or
	 * SDLOGGING state message), when the API's state first showed it (polled), and when the API sent
	 * NOTIFICATION_SHIMMER_FULLY_INITIALIZED, which marks the end of the connect sequence. NaN until
	 * then.
	 */
	private volatile double readyMessageAtMs = Double.NaN;
	private volatile double readyStateAtMs = Double.NaN;
	private volatile double fullyInitialisedAtMs = Double.NaN;
	private volatile BT_STATE readyMessageState;
	private volatile int queuedAtReadyState = -1;

	ShimmerSession(Context context, String mac, String transport, String deviceType) throws Exception {
		this.mac = mac;
		this.transport = transport;
		this.deviceType = deviceType;
		callbackThread.start();
		Handler handler = new Handler(callbackThread.getLooper()) {
			@Override
			public void handleMessage(Message msg) {
				onMessage(msg);
			}
		};
		// On the main thread, as an app does: the manager creates a Handler on whatever thread
		// constructs it, which fails on the instrumentation thread (no Looper).
		final ShimmerBluetoothManagerAndroid[] created = new ShimmerBluetoothManagerAndroid[1];
		final Exception[] failure = new Exception[1];
		InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
			try {
				BleManager.getInstance().init((Application) context.getApplicationContext());
				created[0] = new ShimmerBluetoothManagerAndroid(context, handler);
			} catch (Exception e) {
				failure[0] = e;
			}
		});
		if (failure[0] != null) {
			callbackThread.quitSafely();
			throw failure[0];
		}
		manager = created[0];
	}

	double nowMs() {
		return SystemClock.elapsedRealtime() - t0;
	}

	void event(String text) {
		String line = String.format(Locale.ROOT, "%9.0f %s", nowMs(), text);
		synchronized (events) {
			events.add(line);
		}
		Log.i(TAG, mac + " " + line);
	}

	List<String> events() {
		synchronized (events) {
			return new ArrayList<>(events);
		}
	}

	private void onMessage(Message msg) {
		if (msg.what == ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE) {
			BT_STATE state = null;
			if (msg.obj instanceof ObjectCluster) {
				state = ((ObjectCluster) msg.obj).mState;
			} else if (msg.obj instanceof CallbackObject) {
				state = ((CallbackObject) msg.obj).mState;
			}
			if (state != null) {
				lastState = state;
				stateMessages++;
				event("state " + state);
				if (isReady(state) && Double.isNaN(readyMessageAtMs)) {
					readyMessageAtMs = nowMs();
					readyMessageState = state;
				}
			}
		} else if (msg.what == ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE && msg.obj instanceof CallbackObject
				&& ((CallbackObject) msg.obj).mIndicator == ShimmerBluetooth.NOTIFICATION_SHIMMER_FULLY_INITIALIZED) {
			if (Double.isNaN(fullyInitialisedAtMs)) {
				fullyInitialisedAtMs = nowMs();
			}
			event("fully initialised");
		} else if (msg.what == ShimmerBluetooth.MSG_IDENTIFIER_PROGRESS_REPORT_PER_DEVICE
				&& msg.obj instanceof BluetoothProgressReportPerDevice) {
			// The manager's own verdict on an operation such as configureShimmer.
			BluetoothProgressReportPerDevice p = (BluetoothProgressReportPerDevice) msg.obj;
			if (p.mCurrentOperationBtState == BT_STATE.CONFIGURING
					&& p.mOperationState != BluetoothProgressReportPerDevice.OperationState.PENDING
					&& p.mOperationState != BluetoothProgressReportPerDevice.OperationState.INPROGRESS) {
				configureResult = p.mOperationState;
				event("configure progress: " + p.mOperationState);
			}
		} else if (msg.what == ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET) {
			if (recording && msg.obj instanceof ObjectCluster) {
				recordPacket((ObjectCluster) msg.obj);
			}
		} else if (otherMessagesSeen.add(msg.what)) {
			event("message " + msg.what + (msg.obj == null ? "" : " " + msg.obj.getClass().getSimpleName()));
		}
	}

	private void recordPacket(ObjectCluster oc) {
		StreamAnalysis.Sample s = new StreamAnalysis.Sample(nowMs(),
				cal(oc, Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP),
				cal(oc, Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_X),
				cal(oc, Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_Y),
				cal(oc, Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_Z));
		synchronized (samples) {
			samples.add(s);
		}
	}

	private static double cal(ObjectCluster oc, String channel) {
		Collection<FormatCluster> formats = oc.getCollectionOfFormatClusters(channel);
		FormatCluster c = (FormatCluster) ObjectCluster.returnFormatCluster(formats, "CAL");
		return c == null ? Double.NaN : c.mData;
	}

	ShimmerBluetooth device() {
		return (ShimmerBluetooth) manager.getShimmer(mac);
	}

	/**
	 * The device, or an exception that the call reports. The API discards its device object when the
	 * link drops, so a lost connection shows up here rather than as a NullPointerException.
	 */
	ShimmerBluetooth requireDevice() {
		ShimmerBluetooth d = device();
		if (d == null) {
			throw new IllegalStateException("the API no longer has the unit (connection lost; last state " + lastState + ")");
		}
		return d;
	}

	/** The rate the API holds for the unit, or NaN once the connection is gone. */
	double rateOnDevice() {
		ShimmerBluetooth d = device();
		return d == null ? Double.NaN : d.getSamplingRateShimmer();
	}

	boolean streaming() {
		ShimmerBluetooth d = device();
		return d != null && d.isStreaming();
	}

	BT_STATE state() {
		ShimmerBluetooth d = device();
		return d == null ? lastState : d.getBluetoothRadioState();
	}

	boolean waitFor(Condition condition, long timeoutMs) throws InterruptedException {
		long deadline = SystemClock.elapsedRealtime() + timeoutMs;
		while (SystemClock.elapsedRealtime() < deadline) {
			if (condition.holds()) {
				return true;
			}
			Thread.sleep(50);
		}
		return condition.holds();
	}

	interface Call {
		void run() throws Exception;
	}

	/** Set to the first library call that never returned; nothing more is attempted after it. */
	volatile String hung;

	private final java.util.concurrent.ExecutorService calls = java.util.concurrent.Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "ShimmerHilCall");
		t.setDaemon(true);
		return t;
	});

	/**
	 * Runs one API call with a time limit, so a call that blocks cannot hang the run. A call that
	 * never returns is recorded with every thread's stack (the evidence a phone without root cannot
	 * otherwise give) and returns false; so does one that throws.
	 */
	boolean call(String what, long timeoutMs, Call call) {
		if (hung != null) {
			event("skipped " + what + ": " + hung + " never returned");
			return false;
		}
		event(what);
		java.util.concurrent.Future<?> f = calls.submit(() -> {
			call.run();
			return null;
		});
		try {
			f.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
			return true;
		} catch (java.util.concurrent.TimeoutException e) {
			hung = what;
			event(what + " did not return within " + timeoutMs + " ms; thread dump follows");
			dumpThreads();
			return false;
		} catch (java.util.concurrent.ExecutionException e) {
			event(what + " threw " + e.getCause());
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	void dumpThreads() {
		for (java.util.Map.Entry<Thread, StackTraceElement[]> t : Thread.getAllStackTraces().entrySet()) {
			StringBuilder b = new StringBuilder("  thread \"" + t.getKey().getName() + "\" " + t.getKey().getState());
			for (StackTraceElement frame : t.getValue()) {
				b.append("\n      at ").append(frame);
			}
			event(b.toString());
		}
	}

	/**
	 * Connects over the session's transport and waits until the API has read the device's
	 * configuration. Classic needs the unit paired with the phone; BLE does not.
	 */
	boolean connect(long timeoutMs) throws InterruptedException {
		return connect(timeoutMs, BT_STATE.CONNECTED);
	}

	/**
	 * As connect(long), for a unit that ends its connect in one of readyStates: SDLOGGING for one that
	 * is logging to its SD card. It records when the unit was first reported ready (readyEarlyMs()).
	 */
	boolean connect(long timeoutMs, final BT_STATE... readyStates) throws InterruptedException {
		final java.util.List<BT_STATE> accepted = java.util.Arrays.asList(readyStates);
		readyMessageAtMs = Double.NaN;
		readyStateAtMs = Double.NaN;
		fullyInitialisedAtMs = Double.NaN;
		readyMessageState = null;
		queuedAtReadyState = -1;
		final boolean ble = "ble".equals(transport);
		if (!call("connect over " + transport, 10000, () -> manager.connectShimmerThroughBTAddress(mac, deviceType,
				ble ? ShimmerBluetoothManagerAndroid.BT_TYPE.BLE : ShimmerBluetoothManagerAndroid.BT_TYPE.BT_CLASSIC))) {
			return false;
		}
		boolean ready = waitFor(() -> {
			ShimmerBluetooth d = device();
			if (d != null && isReady(d.getBluetoothRadioState()) && Double.isNaN(readyStateAtMs)) {
				readyStateAtMs = nowMs();
				queuedAtReadyState = queuedInstructions();
				event("state first shows " + d.getBluetoothRadioState() + " with " + queuedAtReadyState + " instructions queued");
			}
			return d != null && d.isInitialised() && accepted.contains(d.getBluetoothRadioState());
		}, timeoutMs);
		if (!ready) {
			// The connect call returned, so nothing else dumps the threads: a stalled connect sequence
			// (such as the DEV-1198 lock-order deadlock) shows here
			event("connect did not finish within " + timeoutMs + " ms (state " + state() + "); thread dump follows");
			dumpThreads();
		}
		ShimmerBluetooth d = device();
		if (ready && ble && d != null) {
			// Over BLE the driver logs parser errors with consolePrintLn, which only prints in verbose
			// mode; Android's Classic class logs them always. Same evidence either way.
			d.setVerboseMode(true);
		}
		return ready && d != null;
	}

	private static boolean isReady(BT_STATE state) {
		return state == BT_STATE.CONNECTED || state == BT_STATE.SDLOGGING;
	}

	/** When the last connect sent NOTIFICATION_SHIMMER_FULLY_INITIALIZED, or NaN if it has not */
	double fullyInitialisedAtMs() {
		return fullyInitialisedAtMs;
	}

	/**
	 * How long before NOTIFICATION_SHIMMER_FULLY_INITIALIZED the last connect first reported the unit
	 * ready, by state message or in the API's state, whichever came first. NaN if either is missing.
	 */
	double readyEarlyMs() {
		double first = Double.isNaN(readyMessageAtMs) ? readyStateAtMs
				: Double.isNaN(readyStateAtMs) ? readyMessageAtMs : Math.min(readyMessageAtMs, readyStateAtMs);
		return fullyInitialisedAtMs - first;
	}

	/** What readyEarlyMs() is made of, for the report */
	String readyTimeline() {
		return String.format(Locale.ROOT, "first ready message %s at %.0f ms, state first ready at %.0f ms with %d instructions queued, fully initialised at %.0f ms",
				readyMessageState, readyMessageAtMs, readyStateAtMs, queuedAtReadyState, fullyInitialisedAtMs);
	}

	/**
	 * The API's instruction queue length, or -1 without a device. The queue is protected, so an app
	 * can't see it; the test reads the field by reflection. Not through getListofInstructions(), which
	 * takes the device's lock: the IO thread takes the queue's lock and then the device's
	 * (DEV-1198), so holding the device's lock from here would widen that deadlock's window.
	 */
	int queuedInstructions() {
		ShimmerBluetooth d = device();
		if (d == null) {
			return -1;
		}
		try {
			java.lang.reflect.Field f = ShimmerBluetooth.class.getDeclaredField("mListofInstructions");
			f.setAccessible(true);
			return ((List<?>) f.get(d)).size();
		} catch (Exception e) {
			event("could not read the instruction queue: " + e);
			return -1;
		}
	}

	/**
	 * Why the unit will not log to its SD card, or null if nothing says it won't. Docked, or on USB
	 * (Shimmer3R), the firmware ACKs a start of logging and then ignores it (LogAndStream v1.01.002 on
	 * Shimmer3, v1.01.004 on Shimmer3R). The driver marks the unit logging on that ACK, so the API's
	 * state shows logging with nothing logged. Taken from the status the connect read.
	 */
	String whyCannotLog() {
		ShimmerBluetooth d = device();
		if (d == null) {
			return null;
		}
		if (d.isDocked()) {
			return "the unit reports itself docked";
		}
		if (d.isUsbPluggedIn()) {
			return "the unit reports USB plugged in";
		}
		return null;
	}

	/**
	 * Accel only (the low-noise accel on Shimmer3, the LSM6DSV's on Shimmer3R) at rateHz, written
	 * the way the examples do: a clone, AssembleShimmerConfig, then configureShimmer.
	 */
	boolean configureAccelOnly(double rateHz, long timeoutMs) throws InterruptedException {
		ShimmerDevice original = manager.getShimmer(mac);
		if (original == null) {
			event("configure: the API no longer has the unit (connection lost; last state " + lastState + ")");
			return false;
		}
		ShimmerDevice clone = original.deepClone();
		clone.disableAllSensors();
		if (clone.getHardwareVersion() == ShimmerVerDetails.HW_ID.SHIMMER_3) {
			clone.setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_ANALOG_ACCEL, true);
		} else {
			clone.setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_LSM6DSV_ACCEL_LN, true);
		}
		clone.setShimmerAndSensorsSamplingRate(rateHz);
		AssembleShimmerConfig.generateSingleShimmerConfig(clone, Configuration.COMMUNICATION_TYPE.BLUETOOTH);
		configureResult = null;
		final int stateMessagesBefore = stateMessages;
		if (!call(String.format(Locale.ROOT, "configure accel only at %.1f Hz", rateHz), 10000,
				() -> manager.configureShimmer(clone))) {
			return false;
		}
		// Done when the operation has finished: either the manager's progress report (not every
		// version forwards it to the Handler) or a "Ready" with the requested rate read back, and in
		// either case once the queue is empty. The rate and a "Ready" are not enough on their own: the
		// rate is in the first config chunk written, and a "Ready" can be the end of an earlier stop.
		// Straight after a stopStreaming() both came with commands still queued, so the test
		// disconnected under the IO thread (DEV-1372). The configure queues the whole operation before
		// it returns, so an empty queue means its last ACK has been processed.
		boolean done = waitFor(() -> state() == BT_STATE.CONNECTED
				&& queuedInstructions() == 0
				&& (configureResult != null
					|| (stateMessages > stateMessagesBefore && Math.abs(rateOnDevice() - rateHz) < 0.5)),
				timeoutMs);
		event(String.format(Locale.ROOT, "configure %s (%s): rate on device %.2f Hz, %d instructions queued",
				done ? "finished" : "timed out", configureResult != null ? configureResult : "no progress report",
				rateOnDevice(), queuedInstructions()));
		return done && (configureResult == null || configureResult == BluetoothProgressReportPerDevice.OperationState.SUCCESS);
	}

	void startRecording() {
		synchronized (samples) {
			samples.clear();
		}
		recording = true;
	}

	List<StreamAnalysis.Sample> stopRecording() {
		recording = false;
		synchronized (samples) {
			return new ArrayList<>(samples);
		}
	}

	int sampleCount() {
		synchronized (samples) {
			return samples.size();
		}
	}

	boolean startStreaming(long timeoutMs) throws InterruptedException {
		return call("startStreaming", 10000, () -> requireDevice().startStreaming())
				&& waitFor(this::streaming, timeoutMs);
	}

	boolean stopStreaming(long timeoutMs) throws InterruptedException {
		return call("stopStreaming", 10000, () -> requireDevice().stopStreaming())
				&& waitFor(() -> !streaming(), timeoutMs);
	}

	/** Returns once the call has returned; the caller waits for the state it expects. */
	boolean startSdLogging() {
		return call("startSDLogging", 10000, () -> requireDevice().startSDLogging());
	}

	boolean stopSdLogging() {
		return call("stopSDLogging", 10000, () -> requireDevice().stopSDLogging());
	}

	boolean readBattery() {
		return call("readBattery", 10000, () -> requireDevice().readBattery());
	}

	boolean readStatus() {
		return call("readStatusLogAndStream", 10000, () -> requireDevice().readStatusLogAndStream());
	}

	/**
	 * The API's current battery details. Both the Classic and the BLE parser replace the object with
	 * a new one for each battery reply they decode, so a different object means a reply arrived.
	 */
	Object batteryDetails() {
		ShimmerBluetooth d = device();
		return d == null ? null : d.getBattStatusDetails();
	}

	boolean disconnect(long timeoutMs) throws InterruptedException {
		final ShimmerBluetooth d = device();
		if (d != null && !call("disconnect", 10000, d::disconnect)) {
			return false;
		}
		return waitFor(() -> {
			BT_STATE s = state();
			return s == null || s == BT_STATE.DISCONNECTED || s == BT_STATE.CONNECTION_LOST;
		}, timeoutMs);
	}

	/** Writes a marker to this process's log; parserErrorsSince(marker) counts lines after it. */
	String logMarker(String label) {
		String marker = "HIL-MARK " + label + " " + SystemClock.elapsedRealtimeNanos();
		Log.i(TAG, marker);
		return marker;
	}

	/** Parser errors this process logged after the marker. Apps may read their own process's log. */
	int parserErrorsSince(String marker) {
		return logLinesSince(marker, PARSER_ERRORS);
	}

	/** Lines this process logged after the marker that contain any of the texts, or -1 if the log can't be read */
	int logLinesSince(String marker, String... texts) {
		int count = 0;
		try {
			java.lang.Process p = Runtime.getRuntime().exec(new String[] {"logcat", "-d", "--pid=" + Process.myPid()});
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
			boolean afterMarker = false;
			for (String line; (line = r.readLine()) != null; ) {
				if (line.contains(marker)) {
					afterMarker = true;
				} else if (afterMarker) {
					for (String e : texts) {
						if (line.contains(e)) {
							count++;
						}
					}
				}
			}
			p.waitFor();
		} catch (Exception e) {
			event("could not read logcat: " + e);
			return -1;
		}
		return count;
	}

	/**
	 * For each set of texts, the lines this process logged after the marker that contain any of them,
	 * or null if they can't be counted. Unlike logLinesSince(), that includes a log that no longer
	 * holds the marker: the phone's log buffer is small, and with the Bluetooth snoop log on it can
	 * roll past a marker within seconds, which would read as no lines at all.
	 */
	int[] logCountsSince(String marker, String[]... textSets) {
		int[] counts = new int[textSets.length];
		boolean afterMarker = false;
		try {
			java.lang.Process p = Runtime.getRuntime().exec(new String[] {"logcat", "-d", "--pid=" + Process.myPid()});
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
			for (String line; (line = r.readLine()) != null; ) {
				if (line.contains(marker)) {
					afterMarker = true;
				} else if (afterMarker) {
					for (int k = 0; k < textSets.length; k++) {
						for (String e : textSets[k]) {
							if (line.contains(e)) {
								counts[k]++;
							}
						}
					}
				}
			}
			p.waitFor();
		} catch (Exception e) {
			event("could not read logcat: " + e);
			return null;
		}
		if (!afterMarker) {
			event("the phone's log no longer holds \"" + marker + "\", so the lines after it can't be counted");
			return null;
		}
		return counts;
	}

	@Override
	public void close() throws Exception {
		try {
			// After a hung call the API is in an unknown state; leave the unit for the runner's
			// force-stop rather than queue more calls behind the stuck one.
			BT_STATE s = state();
			if (hung == null && s != null && s != BT_STATE.DISCONNECTED && s != BT_STATE.CONNECTION_LOST) {
				if (streaming()) {
					stopStreaming(5000);
				}
				disconnect(10000);
			}
		} finally {
			callbackThread.quitSafely();
			calls.shutdownNow();
		}
	}
}
