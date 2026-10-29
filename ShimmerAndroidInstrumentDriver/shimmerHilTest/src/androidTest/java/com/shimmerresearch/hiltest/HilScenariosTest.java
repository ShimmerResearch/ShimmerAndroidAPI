package com.shimmerresearch.hiltest;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_CRC_MODE;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;

import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hardware-in-the-loop scenarios against one real Shimmer3 or Shimmer3R, paired with the phone over
 * Bluetooth Classic. Arguments come from `am instrument -e key value`:
 *
 * <pre>
 *   mac             XX:XX:XX:XX:XX:XX (required; without it every test is skipped)
 *   transport       classic (default; the unit must be paired) or ble (no pairing)
 *   type            Shimmer3 (default) or Shimmer3R; over BLE it picks the device class
 *   rate            sampling rate in Hz, default 256
 *   seconds         stream_rest duration, default 30
 *   cycles          mid_stream_set / reconnect_loop / in_stream repetitions, default 3 / 5 / 3
 *   crc             link CRC to ask for: off, 1 or 2 (default 2; the driver may settle on less)
 *   maxLossPercent  default 1.0
 *   queueStressSeconds  how long queue_stress queues reads, default 60
 *   accelTolerance  m/s^2 either side of 9.81 at rest, default 1.0
 * </pre>
 *
 * tools/hil/run-android-hil.ps1 runs each scenario for each unit in a rig file and pulls the reports.
 */
@RunWith(AndroidJUnit4.class)
public class HilScenariosTest {

	private static final double GRAVITY = 9.81;

	/**
	 * Connecting to an already paired unit needs no runtime permission before Android 12 (BLUETOOTH is
	 * granted at install; location is only for scanning, which these tests never do). From Android 12,
	 * BLUETOOTH_CONNECT is a runtime permission. Some phones (Xiaomi) refuse runtime grants over USB
	 * without "USB debugging (Security settings)", so grant nothing where nothing is needed.
	 */
	@Rule
	public TestRule permissions = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
			? GrantPermissionRule.grant(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
			: RuleChain.emptyRuleChain();

	private Bundle args;
	private Context context;
	private ShimmerSession session;
	private HilReport report;

	private String arg(String key, String fallback) {
		String v = args.getString(key);
		return v == null || v.isEmpty() ? fallback : v;
	}

	private double arg(String key, double fallback) {
		return Double.parseDouble(arg(key, String.valueOf(fallback)));
	}

	private int arg(String key, int fallback) {
		return Integer.parseInt(arg(key, String.valueOf(fallback)));
	}

	private void begin(String scenario) throws Exception {
		args = InstrumentationRegistry.getArguments();
		String mac = args.getString("mac");
		assumeTrue("No unit given: pass -e mac XX:XX:XX:XX:XX:XX", mac != null && !mac.isEmpty());
		mac = mac.toUpperCase(Locale.ROOT);
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		String crc = arg("crc", "2");
		ShimmerBluetooth.setDefaultBtCrcModeToUseIfFwSupported(
				"off".equals(crc) ? BT_CRC_MODE.OFF : "1".equals(crc) ? BT_CRC_MODE.ONE_BYTE_CRC : BT_CRC_MODE.TWO_BYTE_CRC);
		String transport = arg("transport", "classic");
		assumeTrue("transport must be classic or ble, not " + transport, "classic".equals(transport) || "ble".equals(transport));
		report = new HilReport(context, scenario, transport, mac, args);
		session = new ShimmerSession(context, mac, transport, arg("type", "Shimmer3"));
	}

	@After
	public void end() throws Exception {
		try {
			if (session != null) {
				session.close();
			}
		} finally {
			if (report != null) {
				report.write(session != null ? session.events() : new ArrayList<String>());
			}
		}
	}

	private void finish() {
		if (session != null && session.hung != null) {
			report.put("hungCall", session.hung);
			report.check(false, session.hung + " never returned: the API call blocked (thread dump in the report's events)");
		}
		if (!report.failures().isEmpty()) {
			Assert.fail(android.text.TextUtils.join("; ", report.failures()));
		}
	}

	/** Connects, records what the unit is, and writes the accel-only config at the test's rate. */
	private boolean connectAndConfigure(double rateHz) throws Exception {
		long start = System.currentTimeMillis();
		boolean connected = session.connect(30000);
		report.put("connectMs", System.currentTimeMillis() - start);
		report.check(connected, "did not connect and initialise within 30 s (state " + session.state() + ")");
		if (!connected) {
			return false;
		}
		ShimmerBluetooth d = session.device();
		if (d == null) {
			report.check(false, "the connection was lost right after connecting (state " + session.state() + ")");
			return false;
		}
		report.put("hardware", d.getHardwareVersionParsed());
		report.put("firmware", d.getFirmwareVersionParsed());
		report.put("crcMode", String.valueOf(d.getCurrentBtCommsCrcMode()));
		boolean configured = session.configureAccelOnly(rateHz, 20000);
		report.put("rateOnDeviceHz", d.getSamplingRateShimmer());
		report.check(configured, "configuration did not complete within 20 s (state " + session.state() + ")");
		if (configured && session.device() == null) {
			report.check(false, "the connection was lost right after configuring (state " + session.state() + ")");
			return false;
		}
		return configured;
	}

	private void checkStream(StreamAnalysis a, List<StreamAnalysis.Sample> samples, double stoppedAtMs,
			double maxLossPercent, double deviceRateHz, String what) {
		report.check(a.received > 0, what + ": no packets received");
		report.check(a.lossPercent() <= maxLossPercent, String.format(Locale.ROOT,
				"%s: %.3f%% packets lost (%d of %d), limit %.2f%%", what, a.lossPercent(), a.missing,
				a.received + a.missing, maxLossPercent));
		report.check(a.nonIncreasing == 0, what + ": " + a.nonIncreasing + " timestamps did not increase");
		checkJumps(a, what);
		checkTimeline(a, samples, stoppedAtMs, deviceRateHz, what);
	}

	/**
	 * Loss is counted between packets received, so two things it cannot see are checked here: the
	 * device timestamps must give a rate (at least two usable ones) within 1 % of the configured
	 * rate, and packets must have kept arriving until the test stopped recording, since a stream
	 * that fell silent leaves no gap between the packets it did deliver.
	 */
	private void checkTimeline(StreamAnalysis a, List<StreamAnalysis.Sample> samples, double stoppedAtMs,
			double deviceRateHz, String what) {
		if (Double.isNaN(a.effectiveRateHz) || Double.isInfinite(a.effectiveRateHz)) {
			report.check(false, what + ": no usable device timestamps to measure the rate from (" + a.received + " packets)");
		} else {
			report.check(Math.abs(a.effectiveRateHz - deviceRateHz) <= 0.01 * deviceRateHz, String.format(Locale.ROOT,
					"%s: effective rate %.2f Hz, device set to %.2f Hz", what, a.effectiveRateHz, deviceRateHz));
		}
		if (!samples.isEmpty()) {
			double silentMs = stoppedAtMs - samples.get(samples.size() - 1).receivedMs;
			report.check(silentMs <= MAX_SILENCE_AT_END_MS, String.format(Locale.ROOT,
					"%s: no packet in the last %.1f s before recording stopped (the stream went silent)", what, silentMs / 1000.0));
		}
	}

	/** At 256 Hz a second without a packet is ~256 packets the gap count cannot see. */
	private static final double MAX_SILENCE_AT_END_MS = 1000;

	/** Every stream check is relative to the unit's rate, so a run without one cannot pass. */
	private void checkRate(double deviceRateHz, String what) {
		report.check(!Double.isNaN(deviceRateHz) && deviceRateHz > 0,
				what + ": the API has no sampling rate for the unit (" + deviceRateHz + " Hz; connection lost?)");
	}

	/** A device timestamp that leapt ahead of the arrival time is wrong data (DEV-1023 class), never loss. */
	private void checkJumps(StreamAnalysis a, String what) {
		for (StreamAnalysis.Gap j : a.timestampJumps) {
			report.check(false, String.format(Locale.ROOT,
					"%s: timestamp jumped %.1f s ahead at %.0f ms (device time %.0f ms) while packets kept arriving",
					what, (j.missing + 1) * a.expectedPeriodMs / 1000.0, j.afterReceivedMs, j.afterTimestampMs));
		}
	}

	/** Connect, configure, stream at rest, stop, disconnect. */
	@Test
	public void streamRest() throws Exception {
		begin("stream_rest");
		double rate = arg("rate", 256.0);
		int seconds = arg("seconds", 30);
		double maxLoss = arg("maxLossPercent", 1.0);
		double accelTolerance = arg("accelTolerance", 1.0);
		if (connectAndConfigure(rate)) {
			double deviceRate = session.rateOnDevice();
			checkRate(deviceRate, "stream");
			String marker = session.logMarker("stream_rest");
			session.startRecording();
			report.check(session.startStreaming(10000), "streaming did not start within 10 s");
			Thread.sleep(seconds * 1000L);
			double stoppedAtMs = session.nowMs();
			List<StreamAnalysis.Sample> samples = session.stopRecording();
			report.check(session.stopStreaming(10000), "streaming did not stop within 10 s");
			StreamAnalysis a = StreamAnalysis.analyse(samples, deviceRate);
			session.event("stream: " + a);
			report.putStream("stream", a);
			report.putGaps(a);
			report.samples(samples);
			checkStream(a, samples, stoppedAtMs, maxLoss, deviceRate, "stream");
			if (!Double.isNaN(a.accelMagnitudeMean)) {
				report.check(Math.abs(a.accelMagnitudeMean - GRAVITY) <= accelTolerance, String.format(Locale.ROOT,
						"accel at rest %.3f m/s^2, expected %.2f±%.2f (is the unit lying still?)",
						a.accelMagnitudeMean, GRAVITY, accelTolerance));
			} else {
				report.check(false, "no calibrated accel in the packets");
			}
			int parserErrors = session.parserErrorsSince(marker);
			report.put("parserErrors", parserErrors);
			report.check(parserErrors == 0, parserErrors + " streaming parser errors logged (\"Packet syncing problem\")");
			report.check(session.disconnect(10000), "did not disconnect within 10 s");
		}
		finish();
	}

	/**
	 * Commands while streaming: start and stop SD logging (SET_RWC_COMMAND, whose bare ACK carries the
	 * link CRC: DEV-1147 in the driver, DEV-1148 for Android's own parser) and read the battery. No
	 * packet may be lost around any of them, and logging must actually start and stop. Needs an SD
	 * card in the unit, off the dock and USB, and a link CRC on to exercise DEV-1148.
	 */
	@Test
	public void midStreamSet() throws Exception {
		begin("mid_stream_set");
		double rate = arg("rate", 256.0);
		int cycles = arg("cycles", 3);
		double maxLoss = arg("maxLossPercent", 1.0);
		if (connectAndConfigure(rate) && checkCanLog()) {
			ShimmerBluetooth d = session.device();
			double deviceRate = d.getSamplingRateShimmer();
			checkRate(deviceRate, "stream");
			BT_CRC_MODE crc = d.getCurrentBtCommsCrcMode();
			if (crc == BT_CRC_MODE.OFF) {
				session.event("link CRC is off: the DEV-1148 path is not exercised");
			}
			String marker = session.logMarker("mid_stream_set");
			session.startRecording();
			report.check(session.startStreaming(10000), "streaming did not start within 10 s");
			Thread.sleep(3000);
			List<Double> sdMoments = new ArrayList<>();
			List<Double> batteryMoments = new ArrayList<>();
			for (int i = 1; i <= cycles && session.hung == null; i++) {
				sdMoments.add(session.nowMs());
				session.event("cycle " + i);
				// A call that threw, hung or was skipped is a failure in itself, not a reason to skip the
				// state check; the events say which.
				boolean started = session.startSdLogging();
				report.check(started, "cycle " + i + ": startSDLogging did not complete: it threw, hung or was skipped after a hung call (see the events)");
				if (started) {
					report.check(session.waitFor(() -> session.state() == BT_STATE.STREAMING_AND_SDLOGGING, 5000),
							"cycle " + i + ": SD logging did not start while streaming (state " + session.state()
									+ "; is there an SD card? With a link CRC on, DEV-1148)");
				}
				Thread.sleep(2000);
				sdMoments.add(session.nowMs());
				boolean stopped = session.stopSdLogging();
				report.check(stopped, "cycle " + i + ": stopSDLogging did not complete: it threw, hung or was skipped after a hung call (see the events)");
				if (stopped) {
					report.check(session.waitFor(() -> session.state() == BT_STATE.STREAMING, 5000),
							"cycle " + i + ": SD logging did not stop (state " + session.state() + ")");
				}
				Thread.sleep(1000);
				batteryMoments.add(session.nowMs());
				// readBattery() only sends the command, so wait for the reply to be decoded as well.
				final Object batteryBefore = session.batteryDetails();
				boolean batteryAsked = session.readBattery();
				report.check(batteryAsked, "cycle " + i + ": readBattery did not complete: it threw, hung or was skipped after a hung call (see the events)");
				if (batteryAsked) {
					report.check(session.waitFor(() -> {
						Object now = session.batteryDetails();
						return now != null && now != batteryBefore;
					}, 5000), "cycle " + i + ": no battery reply decoded within 5 s of readBattery");
				}
				Thread.sleep(2000);
			}
			double stoppedAtMs = session.nowMs();
			List<StreamAnalysis.Sample> samples = session.stopRecording();
			report.check(session.stopStreaming(10000), "streaming did not stop within 10 s");
			StreamAnalysis a = StreamAnalysis.analyse(samples, deviceRate);
			session.event("stream: " + a);
			report.putStream("stream", a);
			report.putGaps(a);
			report.samples(samples);
			// Loss around SD logging start/stop is reported, not failed: with no parser error and
			// logging starting, the unit itself did not send those samples while it opened or closed
			// its file, which is firmware behaviour for the firmware HIL bench to judge. Loss around a
			// battery read, or anywhere else, is the Android API's.
			int[] attributed = a.attribute(new double[][] {toArray(sdMoments), toArray(batteryMoments)}, 1000);
			int nearSd = attributed[0];
			int nearBattery = attributed[1];
			report.put("missingNearSdLoggingStartStop", nearSd);
			report.put("missingNearBatteryReads", nearBattery);
			if (nearSd > 0) {
				session.event(nearSd + " packets not received within 1 s of SD logging start/stop (firmware side; not failed)");
			}
			report.check(nearBattery == 0, nearBattery + " packets lost within 1 s of a battery read while streaming");
			int elsewhere = a.missing - nearSd;
			double elsewherePercent = 100.0 * elsewhere / Math.max(1, a.received + a.missing);
			report.put("missingElsewhere", elsewhere);
			report.check(elsewherePercent <= maxLoss, String.format(Locale.ROOT,
					"stream: %.3f%% packets lost away from SD logging start/stop (%d), limit %.2f%%",
					elsewherePercent, elsewhere, maxLoss));
			report.check(a.received > 0, "stream: no packets received");
			report.check(a.nonIncreasing == 0, "stream: " + a.nonIncreasing + " timestamps did not increase");
			checkJumps(a, "stream");
			checkTimeline(a, samples, stoppedAtMs, deviceRate, "stream");
			int parserErrors = session.parserErrorsSince(marker);
			report.put("parserErrors", parserErrors);
			report.check(parserErrors == 0, parserErrors + " streaming parser errors logged (\"Packet syncing problem\")");
			report.check(session.disconnect(10000), "did not disconnect within 10 s");
		}
		finish();
	}

	/** Reads sent after each SD logging stop in inStream, alternately battery and status */
	private static final int READS_PER_BURST = 10;

	/** Between those reads: the unit pauses for about 100 ms, so two or three land in the pause */
	private static final int READ_INTERVAL_MS = 40;

	/** What the driver logs when a command gets no reply in time: "Command:	[0x95] GET_VBATT_COMMAND timeout" */
	private static final String[] COMMAND_TIMEOUTS = {"_COMMAND timeout", "UNKNOWN timeout"};

	/**
	 * In-stream replies while the unit pauses its data packets (DEV-1291). After STOP_LOGGING_ONLY the
	 * unit sends no data packets for about 100 ms while it closes its SD file, and answers a command
	 * sent then at once, with no data packet before the reply. Android's Classic parser threw such a
	 * reply away. Each round starts SD logging, then stops it and reads the battery and the status
	 * every 40 ms for 400 ms: Android writes those reads straight out, so some land in the pause. Then
	 * each restart stops logging and starts it again back to back, so the queued START_LOGGING_ONLY goes
	 * out in the pause and its bare ACK comes first. Every battery read must be answered, logging must
	 * restart, and nothing may log a parser error or a command timeout. Needs an SD card in the unit,
	 * off the dock and USB.
	 */
	@Test
	public void inStream() throws Exception {
		begin("in_stream");
		double rate = arg("rate", 256.0);
		int cycles = arg("cycles", 3);
		double maxLoss = arg("maxLossPercent", 1.0);
		if (connectAndConfigure(rate) && checkCanLog()) {
			double deviceRate = session.rateOnDevice();
			checkRate(deviceRate, "stream");
			LogTally tally = new LogTally();
			String marker = session.logMarker("in_stream start");
			session.startRecording();
			report.check(session.startStreaming(10000), "streaming did not start within 10 s");
			Thread.sleep(2000);
			tally.add(marker);
			List<Double> sdMoments = new ArrayList<>();
			List<Double> readMoments = new ArrayList<>();
			int batteryReads = 0;
			int statusReads = 0;
			for (int i = 1; i <= cycles && session.hung == null; i++) {
				String what = "round " + i;
				session.event(what);
				marker = session.logMarker("in_stream " + what);
				if (!startSdLoggingWhileStreaming(what, sdMoments)) {
					break;
				}
				Thread.sleep(1000);
				sdMoments.add(session.nowMs());
				report.check(session.stopSdLogging(), what + ": stopSDLogging did not complete (see the events)");
				for (int k = 0; k < READS_PER_BURST && session.hung == null; k++) {
					readMoments.add(session.nowMs());
					if (k % 2 == 0) {
						batteryReads += session.readBattery() ? 1 : 0;
					} else {
						statusReads += session.readStatus() ? 1 : 0;
					}
					Thread.sleep(READ_INTERVAL_MS);
				}
				report.check(session.waitFor(() -> session.state() == BT_STATE.STREAMING && session.queuedInstructions() == 0, 5000),
						what + ": SD logging did not stop (state " + session.state() + ", " + session.queuedInstructions() + " instructions queued)");
				Thread.sleep(1000);
				tally.add(marker);
			}
			marker = session.logMarker("in_stream restarts");
			if (session.hung == null && startSdLoggingWhileStreaming("restarts", sdMoments)) {
				for (int i = 1; i <= cycles && session.hung == null; i++) {
					String what = "restart " + i;
					session.event(what);
					Thread.sleep(1000);
					tally.add(marker);
					marker = session.logMarker("in_stream " + what);
					sdMoments.add(session.nowMs());
					report.check(session.stopSdLogging() && session.startSdLogging(),
							what + ": stopSDLogging or startSDLogging did not complete (see the events)");
					report.check(session.waitFor(() -> session.state() == BT_STATE.STREAMING_AND_SDLOGGING && session.queuedInstructions() == 0, 5000),
							what + ": logging did not restart after a stop and a start back to back (state " + session.state()
									+ ", " + session.queuedInstructions() + " instructions queued)");
				}
				Thread.sleep(1000);
				sdMoments.add(session.nowMs());
				report.check(session.stopSdLogging() && session.waitFor(() -> session.state() == BT_STATE.STREAMING, 5000),
						"SD logging did not stop at the end (state " + session.state() + ")");
			}
			Thread.sleep(1000);
			double stoppedAtMs = session.nowMs();
			List<StreamAnalysis.Sample> samples = session.stopRecording();
			report.check(session.stopStreaming(10000), "streaming did not stop within 10 s");
			tally.add(marker);

			report.put("batteryReads", batteryReads);
			report.put("batteryReplies", tally.batteryReplies);
			report.put("statusReads", statusReads);
			report.put("statusReplies", tally.statusReplies);
			report.put("repliesInAPause", tally.repliesInAPause);
			report.put("commandTimeouts", tally.timeouts);
			report.put("parserErrors", tally.parserErrors);
			session.event(String.format(Locale.ROOT, "battery %d reads, %d replies; status %d reads, %d replies (and pushes); %d with no data packet before them",
					batteryReads, tally.batteryReplies, statusReads, tally.statusReplies, tally.repliesInAPause));
			report.check(tally.complete, "the phone's log rolled past a round's start, so its replies and errors could not all be counted (see the events)");
			report.check(tally.batteryReplies == batteryReads, batteryReads + " battery reads but " + tally.batteryReplies + " replies decoded");
			report.check(tally.statusReplies >= statusReads, statusReads + " status reads but " + tally.statusReplies + " status replies decoded");
			report.check(tally.timeouts == 0, tally.timeouts + " command timeouts logged");
			report.check(tally.parserErrors == 0, tally.parserErrors + " streaming parser errors logged (\"Packet syncing problem\")");

			// Loss near SD logging start/stop is the unit's own (see midStreamSet). A Shimmer3 also skips a
			// sample now and then around a battery read, with no parser error, so loss near the reads is
			// reported, not failed.
			StreamAnalysis a = StreamAnalysis.analyse(samples, deviceRate);
			session.event("stream: " + a);
			report.putStream("stream", a);
			report.putGaps(a);
			int[] attributed = a.attribute(new double[][] {toArray(sdMoments), toArray(readMoments)}, 1000);
			report.put("missingNearSdLoggingStartStop", attributed[0]);
			report.put("missingNearReads", attributed[1]);
			int elsewhere = a.missing - attributed[0] - attributed[1];
			double elsewherePercent = 100.0 * elsewhere / Math.max(1, a.received + a.missing);
			report.put("missingElsewhere", elsewhere);
			report.check(elsewherePercent <= maxLoss, String.format(Locale.ROOT,
					"stream: %.3f%% packets lost away from SD logging start/stop and the reads (%d), limit %.2f%%",
					elsewherePercent, elsewhere, maxLoss));
			report.check(a.received > 0, "stream: no packets received");
			report.check(a.nonIncreasing == 0, "stream: " + a.nonIncreasing + " timestamps did not increase");
			checkJumpsAwayFrom(a, sdMoments, "stream");
			checkTimeline(a, samples, stoppedAtMs, deviceRate, "stream");
			report.check(session.disconnect(10000), "did not disconnect within 10 s");
		}
		finish();
	}

	/** inStream's in-stream replies, pause replies, timeouts and parser errors, counted a round at a time */
	private final class LogTally {
		int batteryReplies;
		int statusReplies;
		int repliesInAPause;
		int timeouts;
		int parserErrors;
		/** False once a round's lines could not be counted */
		boolean complete = true;

		/** Adds the lines logged since the marker. Called soon after it, before the phone's small log buffer rolls past it. */
		void add(String marker) {
			// Each parser words a reply differently: Android's own over Classic, the driver's over BLE
			boolean ble = "ble".equals(session.transport);
			String[] battery = {ble ? "In-stream received = [0x94] VBATT_RESPONSE" : "IN STREAM: VBATT_RESPONSE"};
			String[] status = {ble ? "In-stream received = [0x71] STATUS_RESPONSE" : "IN STREAM: STATUS_RESPONSE"};
			int[] c = session.logCountsSince(marker, battery, status,
					new String[] {"with no data packet before it"}, COMMAND_TIMEOUTS, ShimmerSession.PARSER_ERRORS);
			if (c == null) {
				complete = false;
				return;
			}
			batteryReplies += c[0];
			statusReplies += c[1];
			repliesInAPause += c[2];
			timeouts += c[3];
			parserErrors += c[4];
		}
	}

	/**
	 * As checkJumps(), except that a jump within 3 s after an SD logging start or stop is reported, not
	 * failed. The unit can pause for over a second while it opens its file, then send a packet or two it
	 * held from before the pause and, a millisecond later, the next one it sampled: a timestamp leap
	 * with no pause in arrival, but the unit's own (the bytes captured on a Shimmer3 show it).
	 */
	private void checkJumpsAwayFrom(StreamAnalysis a, List<Double> sdMoments, String what) {
		int nearSd = 0;
		for (StreamAnalysis.Gap j : a.timestampJumps) {
			boolean near = false;
			for (double moment : sdMoments) {
				near |= j.afterReceivedMs >= moment && j.afterReceivedMs - moment <= 3000;
			}
			if (near) {
				nearSd++;
				session.event(String.format(Locale.ROOT, "%s: timestamp jumped %.1f s ahead at %.0f ms, within 3 s of an SD logging start or stop (the unit's own; not failed)",
						what, (j.missing + 1) * a.expectedPeriodMs / 1000.0, j.afterReceivedMs));
			} else {
				report.check(false, String.format(Locale.ROOT,
						"%s: timestamp jumped %.1f s ahead at %.0f ms (device time %.0f ms) while packets kept arriving",
						what, (j.missing + 1) * a.expectedPeriodMs / 1000.0, j.afterReceivedMs, j.afterTimestampMs));
			}
		}
		report.put("timestampJumpsNearSdLoggingStartStop", nearSd);
	}

	/**
	 * For a scenario that logs to the SD card: fails it at once, saying why, if the unit is docked or
	 * on USB. The firmware then ignores the start, and the API still shows the unit logging, so the
	 * scenario would test nothing, or fail later on an unrelated check.
	 */
	private boolean checkCanLog() {
		String why = session.whyCannotLog();
		report.check(why == null, why + ": the firmware will not start SD logging, so this scenario cannot run. Take the unit off the dock and USB, then run it again");
		return why == null;
	}

	/** Starts SD logging while streaming and waits until it has started */
	private boolean startSdLoggingWhileStreaming(String what, List<Double> sdMoments) throws InterruptedException {
		sdMoments.add(session.nowMs());
		boolean started = session.startSdLogging()
				&& session.waitFor(() -> session.state() == BT_STATE.STREAMING_AND_SDLOGGING && session.queuedInstructions() == 0, 5000);
		report.check(started, what + ": SD logging did not start while streaming (state " + session.state() + "; is there an SD card?)");
		return started;
	}

	/**
	 * The unit is reported ready only once its connect sequence has ended (DEV-1275), which
	 * NOTIFICATION_SHIMMER_FULLY_INITIALIZED marks: CONNECTED, or SDLOGGING for a unit that is logging
	 * to its SD card. The sequence reads the status part way through, before the calibration dump and
	 * the inquiry, and that reply used to report the unit ready early, so an app's next command went
	 * out in among the rest of the sequence. Connects to the unit idle, then again while it logs.
	 * Needs an SD card in the unit, off the dock and USB.
	 */
	@Test
	public void connectReady() throws Exception {
		begin("connect_ready");
		if (connectAndCheckReady("idle", BT_STATE.CONNECTED)) {
			boolean canLog = checkCanLog();
			boolean logging = canLog && session.startSdLogging()
					&& session.waitFor(() -> session.state() == BT_STATE.SDLOGGING && session.queuedInstructions() == 0, 5000);
			report.check(!canLog || logging, "SD logging did not start (state " + session.state() + "; is there an SD card?)");
			report.check(session.disconnect(10000), "did not disconnect within 10 s");
			if (logging && session.hung == null) {
				Thread.sleep(2000);
				if (!connectAndCheckReady("logging", BT_STATE.SDLOGGING) && session.hung == null) {
					// Leave the unit as it was found, not logging: connect again to stop it
					session.disconnect(10000);
					Thread.sleep(2000);
					session.connect(30000, BT_STATE.CONNECTED, BT_STATE.SDLOGGING);
				}
				stopSdLoggingIfLogging();
				report.check(session.disconnect(10000), "did not disconnect within 10 s");
			}
		}
		finish();
	}

	private void stopSdLoggingIfLogging() throws InterruptedException {
		ShimmerBluetooth d = session.device();
		if (session.hung == null && d != null && d.isSDLogging()) {
			report.check(session.stopSdLogging() && session.waitFor(() -> session.state() == BT_STATE.CONNECTED, 5000),
					"SD logging did not stop, so the unit was left logging (state " + session.state() + ")");
		}
	}

	/**
	 * Not a check: brings the unit back to idle after a run that left it logging to its SD card. It
	 * connects, stops SD logging if the unit logs, and disconnects. Run it on its own, with
	 * -Scenario resetUnit; it isn't part of a default run.
	 */
	@Test
	public void resetUnit() throws Exception {
		begin("reset_unit");
		boolean connected = session.connect(30000, BT_STATE.CONNECTED, BT_STATE.SDLOGGING);
		report.check(connected, "did not connect within 30 s (state " + session.state() + ")");
		if (connected) {
			session.event("connected: " + session.state());
			stopSdLoggingIfLogging();
			report.check(session.disconnect(10000), "did not disconnect within 10 s");
		}
		finish();
	}

	/** Connects and checks the unit was first reported ready, in readyState, at the end of the connect sequence */
	private boolean connectAndCheckReady(String what, BT_STATE readyState) throws InterruptedException {
		boolean ready = session.connect(30000, readyState)
				&& session.waitFor(() -> !Double.isNaN(session.fullyInitialisedAtMs()), 2000);
		report.check(ready, what + ": did not connect, end in " + readyState + " and send FULLY_INITIALIZED within 30 s (state " + session.state() + ")");
		session.event(what + ": " + session.readyTimeline());
		report.put(what + "ReadyEarlyMs", session.readyEarlyMs());
		if (ready) {
			report.check(session.readyEarlyMs() <= 250, String.format(Locale.ROOT,
					"%s: reported ready %.0f ms before the connect sequence ended (%s)", what, session.readyEarlyMs(), session.readyTimeline()));
		}
		return ready;
	}

	private static double[] toArray(List<Double> values) {
		double[] out = new double[values.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = values.get(i);
		}
		return out;
	}

	/** The rates samplingRates writes in turn, as the Java and shimmer-logandstream HILs' scenarios of the same name do */
	private static final double[] SAMPLING_RATES_HZ = {51.2, 256, 512};

	/**
	 * Writes each rate in SAMPLING_RATES_HZ in turn, the way the examples configure, and streams 5 s
	 * at each; then writes the unit's own rate back. Every configure but the first comes straight
	 * after a stopStreaming(), as an app that changes the rate does. Up to driver 0.11.10_beta the
	 * stop's reply then retired the configuration's first chunk, the one holding the sampling rate,
	 * and the unit kept its old rate (DEV-1306). No SD card is needed.
	 */
	@Test
	public void samplingRates() throws Exception {
		begin("sampling_rates");
		double maxLoss = arg("maxLossPercent", 1.0);
		long start = System.currentTimeMillis();
		boolean connected = session.connect(30000);
		report.put("connectMs", System.currentTimeMillis() - start);
		report.check(connected, "did not connect and initialise within 30 s (state " + session.state() + ")");
		ShimmerBluetooth d = connected ? session.device() : null;
		if (d != null) {
			report.put("hardware", d.getHardwareVersionParsed());
			report.put("firmware", d.getFirmwareVersionParsed());
			report.put("crcMode", String.valueOf(d.getCurrentBtCommsCrcMode()));
			double ownRate = d.getSamplingRateShimmer();
			report.put("ownRateHz", ownRate);
			List<String> streams = new ArrayList<>();
			for (double rate : SAMPLING_RATES_HZ) {
				if (session.hung != null) {
					break;
				}
				String what = String.format(Locale.ROOT, "%.1f Hz", rate);
				if (!configureRate(rate, what)) {
					continue;
				}
				double deviceRate = session.rateOnDevice();
				String marker = session.logMarker("sampling_rates " + what);
				session.startRecording();
				report.check(session.startStreaming(10000), what + ": streaming did not start within 10 s");
				Thread.sleep(5000);
				double stoppedAtMs = session.nowMs();
				List<StreamAnalysis.Sample> samples = session.stopRecording();
				report.check(session.stopStreaming(10000), what + ": streaming did not stop within 10 s");
				StreamAnalysis a = StreamAnalysis.analyse(samples, deviceRate);
				session.event(what + " stream: " + a);
				streams.add(what + ": " + a);
				checkStream(a, samples, stoppedAtMs, maxLoss, deviceRate, what);
				int parserErrors = session.parserErrorsSince(marker);
				report.check(parserErrors == 0, what + ": " + parserErrors + " streaming parser errors logged");
			}
			report.put("streams", new org.json.JSONArray(streams));
			if (session.hung == null) {
				configureRate(ownRate, "the unit's own rate");
			}
			report.put("rateOnDeviceHz", session.rateOnDevice());
			report.check(session.disconnect(10000), "did not disconnect within 10 s");
		}
		finish();
	}

	/**
	 * Writes rateHz and checks the unit took it. The configure ends by reading the configuration
	 * back, so the API's rate is then the unit's. configureAccelOnly can finish on the manager's
	 * SUCCESS alone, without looking at the rate, so the rate is checked here.
	 */
	private boolean configureRate(double rateHz, String what) throws InterruptedException {
		boolean configured = session.configureAccelOnly(rateHz, 20000);
		double onDevice = session.rateOnDevice();
		report.check(configured, String.format(Locale.ROOT, "%s: the configuration did not complete within 20 s (rate on device %.2f Hz, state %s)",
				what, onDevice, session.state()));
		boolean took = Math.abs(onDevice - rateHz) < 0.5;
		report.check(!configured || took, String.format(Locale.ROOT,
				"%s: the unit kept %.2f Hz after %.2f Hz was written (straight after a stop, that is DEV-1306)", what, onDevice, rateHz));
		return configured && took;
	}

	/** queueStress: the app threads, the most instructions they keep queued, and how long a stuck call or queue takes to count as one */
	private static final int QUEUE_STRESS_THREADS = 4;
	private static final int QUEUE_STRESS_MAX_QUEUED = 8;
	private static final long QUEUE_STRESS_STALL_MS = 10000;

	/**
	 * DEV-1266: the driver guards its instruction queue with two locks, the device and the queue. Its
	 * IO thread took them queue first when it fetched the next instruction, and every call that queues
	 * one takes them device first, so the two could deadlock and every later call hung (5 of 12
	 * inStream runs on driver 0.11.7). With the unit connected and idle, timers on as in an app, four
	 * threads queue battery and status reads in tight loops for queueStressSeconds (default 60),
	 * keeping the queue under 8, so a call is nearly always taking the locks while the IO thread
	 * fetches. Idle, because while streaming Android writes both reads straight out, unqueued. No SD
	 * card is needed.
	 */
	@Test
	public void queueStress() throws Exception {
		begin("queue_stress");
		int seconds = arg("queueStressSeconds", 60);
		long start = System.currentTimeMillis();
		boolean connected = session.connect(30000);
		report.put("connectMs", System.currentTimeMillis() - start);
		report.check(connected, "did not connect and initialise within 30 s (state " + session.state() + ")");
		final ShimmerBluetooth d = connected ? session.device() : null;
		if (d != null) {
			report.put("hardware", d.getHardwareVersionParsed());
			report.put("firmware", d.getFirmwareVersionParsed());
			report.put("crcMode", String.valueOf(d.getCurrentBtCommsCrcMode()));
			final AtomicBoolean stop = new AtomicBoolean();
			final AtomicLong calls = new AtomicLong();
			// When each thread's current call began (elapsedRealtime), or 0 between calls
			final AtomicLongArray inCallSince = new AtomicLongArray(QUEUE_STRESS_THREADS);
			final AtomicReference<Throwable> thrown = new AtomicReference<>();
			List<Thread> threads = new ArrayList<>();
			for (int i = 0; i < QUEUE_STRESS_THREADS; i++) {
				final int k = i;
				Thread t = new Thread(() -> {
					try {
						while (!stop.get()) {
							if (session.queuedInstructions() >= QUEUE_STRESS_MAX_QUEUED) {
								Thread.sleep(1);
								continue;
							}
							inCallSince.set(k, SystemClock.elapsedRealtime());
							if ((calls.get() + k) % 2 == 0) {
								d.readBattery();
							} else {
								d.readStatusLogAndStream();
							}
							inCallSince.set(k, 0);
							calls.incrementAndGet();
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} catch (Throwable e) {
						thrown.compareAndSet(null, e);
						stop.set(true);
					}
				}, "QueueStress" + i);
				t.setDaemon(true); // a deadlocked thread must not keep the test process alive
				threads.add(t);
			}
			session.event("queue stress: " + QUEUE_STRESS_THREADS + " threads for " + seconds + " s");
			Object battery = session.batteryDetails();
			int batteryReplies = 0;
			int maxQueued = 0;
			long lastCalls = 0;
			long lastProgress = SystemClock.elapsedRealtime();
			long end = lastProgress + seconds * 1000L;
			String stall = null;
			for (Thread t : threads) {
				t.start();
			}
			while (SystemClock.elapsedRealtime() < end && stall == null && !stop.get()) {
				Thread.sleep(100);
				long now = SystemClock.elapsedRealtime();
				Object b = session.batteryDetails();
				if (b != null && b != battery) {
					battery = b;
					batteryReplies++;
				}
				int queued = session.queuedInstructions();
				maxQueued = Math.max(maxQueued, queued);
				long c = calls.get();
				if (c != lastCalls) {
					lastCalls = c;
					lastProgress = now;
				}
				for (int i = 0; i < QUEUE_STRESS_THREADS && stall == null; i++) {
					long since = inCallSince.get(i);
					if (since != 0 && now - since > QUEUE_STRESS_STALL_MS) {
						stall = String.format(Locale.ROOT, "a read queued by QueueStress%d has not returned for %.0f s", i, (now - since) / 1000.0);
					}
				}
				if (stall == null && now - lastProgress > QUEUE_STRESS_STALL_MS) {
					stall = String.format(Locale.ROOT, "no read returned for %.0f s, with %d instructions queued", (now - lastProgress) / 1000.0, queued);
				}
			}
			stop.set(true);
			report.put("calls", calls.get());
			report.put("batteryReplies", batteryReplies);
			report.put("maxQueued", maxQueued);
			session.event(String.format(Locale.ROOT, "queue stress: %d reads queued, %d battery replies seen, at most %d instructions queued",
					calls.get(), batteryReplies, maxQueued));
			if (stall != null) {
				session.hung = "readBattery/readStatusLogAndStream (queueStress)";
				session.event(stall + "; thread dump follows");
				session.dumpThreads();
				report.check(false, stall + " (the calls and the IO thread are stuck; DEV-1266's deadlock if the dump shows them waiting on each other)");
			} else {
				for (Thread t : threads) {
					t.join(2000);
				}
				report.check(thrown.get() == null, "a read threw " + thrown.get());
				report.check(calls.get() > 0, "no read was queued");
				boolean drained = session.waitFor(() -> session.queuedInstructions() == 0, 15000);
				report.check(drained, "the queue did not drain within 15 s after the reads stopped ("
						+ session.queuedInstructions() + " left; the IO thread is stuck, see the thread dump in the events)");
				if (!drained) {
					session.event("the queue did not drain; thread dump follows");
					session.dumpThreads();
				}
				report.check(session.disconnect(10000), "did not disconnect within 10 s");
			}
		}
		finish();
	}

	/** Connect, stream briefly and disconnect, repeatedly: the reconnect path apps hit every day. */
	@Test
	public void reconnectLoop() throws Exception {
		begin("reconnect_loop");
		double rate = arg("rate", 256.0);
		int cycles = arg("cycles", 5);
		double maxLoss = arg("maxLossPercent", 1.0);
		List<Long> connectTimes = new ArrayList<>();
		for (int i = 1; i <= cycles; i++) {
			String what = "cycle " + i;
			long start = System.currentTimeMillis();
			boolean ready = i == 1 ? connectAndConfigure(rate) : session.connect(30000);
			connectTimes.add(System.currentTimeMillis() - start);
			report.check(ready, what + ": did not connect within 30 s (state " + session.state() + ")");
			if (!ready) {
				break;
			}
			double deviceRate = session.rateOnDevice();
			checkRate(deviceRate, what);
			String marker = session.logMarker(what);
			session.startRecording();
			report.check(session.startStreaming(10000), what + ": streaming did not start within 10 s");
			Thread.sleep(5000);
			double stoppedAtMs = session.nowMs();
			List<StreamAnalysis.Sample> samples = session.stopRecording();
			report.check(session.stopStreaming(10000), what + ": streaming did not stop within 10 s");
			StreamAnalysis a = StreamAnalysis.analyse(samples, deviceRate);
			session.event(what + " stream: " + a);
			checkStream(a, samples, stoppedAtMs, maxLoss, deviceRate, what);
			int parserErrors = session.parserErrorsSince(marker);
			report.check(parserErrors == 0, what + ": " + parserErrors + " streaming parser errors logged");
			report.check(session.disconnect(10000), what + ": did not disconnect within 10 s");
			Thread.sleep(2000);
		}
		report.put("connectMs", new org.json.JSONArray(connectTimes));
		finish();
	}
}
