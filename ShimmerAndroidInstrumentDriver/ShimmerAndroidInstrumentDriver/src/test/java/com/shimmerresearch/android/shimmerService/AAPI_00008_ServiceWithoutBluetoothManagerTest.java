package com.shimmerresearch.android.shimmerService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import android.os.Message;

import org.junit.Before;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.ShimmerDevice;

/**
 * DEV-1402: with Bluetooth off, ShimmerService.onCreate() cannot create its
 * BluetoothManager ("Bluetooth not Enabled") and leaves btManager null. The
 * ShimmerCapture options menu then called getListOfConnectedDevices() on it, and
 * the app crashed at launch. Google Play's pre-launch test refused the 1.3.4
 * release for it.
 * <p>
 * DEV-1427: the same was true of every other method that uses btManager. A
 * missing BluetoothManager now means no devices throughout: each method returns
 * what ShimmerBluetoothManagerAndroid returns for a device it does not know.
 * Commands do nothing, and queries return -1, 0, false, "", null, DISCONNECTED
 * or an empty list.
 * <p>
 * No hardware: a service that has not been through onCreate() has no
 * BluetoothManager, which is the state Bluetooth being off leaves it in. Paths
 * that show a Toast are left out: Toast.makeText() returns null on the stub
 * android.jar these tests run against.
 */
public class AAPI_00008_ServiceWithoutBluetoothManagerTest {

	private static final String ADDRESS = "00:06:66:00:00:01";

	private ShimmerService service;

	@Before
	public void createServiceWithoutBluetoothManager() {
		service = new ShimmerService();
		assertNull("precondition: no BluetoothManager", service.getBluetoothManager());
	}

	@Test
	public void listOfConnectedDevicesIsEmpty() {
		List<ShimmerDevice> devices = service.getListOfConnectedDevices();
		assertNotNull(devices);
		assertTrue(devices.isEmpty());
	}

	@Test
	public void getShimmerFindsNoDevice() {
		assertNull(service.getShimmer(ADDRESS));
	}

	@Test
	public void disconnectAllDevicesClearsTheServiceState() {
		service.mMultiShimmer.put(ADDRESS, new Object());
		service.disconnectAllDevices();
		assertTrue(service.mMultiShimmer.isEmpty());
	}

	/** Each does what the BluetoothManager does for a device it does not know: nothing. */
	@Test
	@SuppressWarnings("deprecation")
	public void commandsDoNothing() throws Exception {
		service.toggleAllLEDS();
		service.toggleLED(ADDRESS);
		service.clickToggle();
		service.stopStreamingAllDevices();
		service.startStreamingAllDevices();
		service.setAllAccelRange(0);
		service.setAllGSRRange(0);
		service.setAllEnabledSensors(0);
		service.writePMux(ADDRESS, 0);
		service.write5VReg(ADDRESS, 0);
		service.writeAccelRange(ADDRESS, 0);
		service.writeGyroRange(ADDRESS, 0);
		service.writePressureResolution(ADDRESS, 0);
		service.writeMagRange(ADDRESS, 0);
		service.writeGSRRange(ADDRESS, 0);
		service.startStreaming(ADDRESS);
		service.stopStreaming(ADDRESS);
		service.startLogging(ADDRESS);
		service.stopLogging(ADDRESS);
		service.startLogAndStreaming(ADDRESS);
		service.stopLogAndStreaming(ADDRESS);
		service.setBlinkLEDCMD(ADDRESS);
		service.enableLowPowerMag(ADDRESS, true);
		service.setBattLimitWarning(ADDRESS, 3.3);
		service.writeEXGSetting(ADDRESS, 0);
		service.readStatusLogAndStream(ADDRESS);
		service.configureShimmer(null);
		service.configureShimmers(new ArrayList<ShimmerDevice>());
	}

	/** Each returns the value the BluetoothManager returns for a device it does not know. */
	@Test
	@SuppressWarnings("deprecation")
	public void queriesReturnTheUnknownDeviceValues() {
		assertTrue(service.getListofEnabledSensorSignals(ADDRESS).isEmpty());
		assertEquals(0, service.getEnabledSensors(ADDRESS));
		assertEquals(-1, service.getSamplingRate(ADDRESS), 0);
		assertEquals(-1, service.getAccelRange(ADDRESS));
		assertEquals(BT_STATE.DISCONNECTED, service.getShimmerState(ADDRESS));
		assertEquals(-1, service.getGSRRange(ADDRESS));
		assertEquals(-1, service.get5VReg(ADDRESS));
		assertFalse(service.isLowPowerMagEnabled(ADDRESS));
		assertEquals(-1, service.getpmux(ADDRESS));
		assertEquals(0, service.sensorConflictCheckandCorrection(ADDRESS, 0xFF, 0));
		assertNull(service.getListofEnabledSensors(ADDRESS));
		assertEquals(-1, service.getBattLimitWarning(ADDRESS), 0);
		assertEquals(-1, service.getPacketReceptionRate(ADDRESS), 0);
		assertFalse(service.DevicesConnected(ADDRESS));
		assertFalse(service.DeviceIsLogging(ADDRESS));
		assertFalse(service.DeviceIsStreaming(ADDRESS));
		assertEquals("", service.getFWVersion(ADDRESS));
		assertEquals(0, service.getShimmerVersion(ADDRESS));
		assertFalse(service.isUsingLogAndStreamFW(ADDRESS));
		assertFalse(service.isSensing(ADDRESS));
		assertFalse(service.isDocked(ADDRESS));
	}

	/** A comparison of two strings, so it needs no device and still answers. */
	@Test
	@SuppressWarnings("deprecation")
	public void addressComparatorStillCompares() {
		assertTrue(service.bluetoothAddressComparator(ADDRESS, "000666000001"));
		assertFalse(service.bluetoothAddressComparator(ADDRESS, "00:06:66:00:00:02"));
	}

	/** State messages look the device up in, or remove it from, the BluetoothManager. */
	@Test
	public void stateMessagesAreHandled() {
		for (BT_STATE state : new BT_STATE[] {BT_STATE.CONNECTED, BT_STATE.SDLOGGING, BT_STATE.DISCONNECTED}) {
			Message msg = new Message();
			msg.obj = new CallbackObject(0, state, ADDRESS, "");
			service.handleMsgStateChange(msg);
		}
	}
}
