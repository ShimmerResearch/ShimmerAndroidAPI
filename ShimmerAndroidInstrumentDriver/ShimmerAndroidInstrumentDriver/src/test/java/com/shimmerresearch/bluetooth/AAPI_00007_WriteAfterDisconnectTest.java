package com.shimmerresearch.bluetooth;

import android.content.Context;
import android.os.Handler;

import org.junit.Test;

import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.driver.ShimmerObject;

/**
 * DEV-1372: the driver's IO thread takes a queued command, sleeps 10-110 ms and
 * then writes it with writeBytes(), which is Shimmer.write() here. disconnect()
 * calls stop() on the caller's thread, and stop() nulls the connection without
 * waiting for the IO thread. A disconnect inside that sleep made write()
 * dereference null; nothing on the IO thread catches it, so Android killed the
 * app. The Android HIL hit it on Bluetooth Classic.
 * <p>
 * A write with no connection is now dropped and logged, as a write that fails on
 * a closed socket already was.
 * <p>
 * No hardware: a Shimmer that was never connected, or has been stopped, has no
 * connection, which is the state the IO thread meets after the disconnect.
 */
public class AAPI_00007_WriteAfterDisconnectTest {

	private static final byte[] QUEUED_COMMAND = new byte[] {ShimmerObject.GET_STATUS_COMMAND};

	/** What the IO thread does with the command it took off the queue before the disconnect */
	private static void sendQueuedCommand(ShimmerBluetooth device) {
		device.writeBytes(QUEUED_COMMAND);
	}

	@Test
	public void queuedCommandAfterStopIsDropped() {
		Shimmer device = new Shimmer((Handler) null, (Context) null);
		device.stop();
		sendQueuedCommand(device);
	}

	@Test
	public void queuedCommandAfterDisconnectIsDropped() {
		Shimmer device = new Shimmer((Handler) null, (Context) null);
		device.disconnect();
		sendQueuedCommand(device);
	}

	@Test
	public void writeWithNoConnectionIsDropped() {
		Shimmer device = new Shimmer((Handler) null, (Context) null);
		device.write(QUEUED_COMMAND);
	}
}
