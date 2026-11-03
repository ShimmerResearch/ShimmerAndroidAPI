package com.shimmerresearch.android.shimmerService;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.shimmerresearch.driver.ShimmerDevice;

/**
 * DEV-1402: with Bluetooth off, ShimmerService.onCreate() cannot create its
 * BluetoothManager ("Bluetooth not Enabled") and leaves btManager null. The
 * ShimmerCapture options menu then called getListOfConnectedDevices() on it, and
 * the app crashed at launch. Google Play's pre-launch test refused the 1.3.4
 * release for it.
 * <p>
 * A missing BluetoothManager now means no devices: these calls return empty or
 * null, or do nothing, instead of throwing.
 * <p>
 * No hardware: a service that has not been through onCreate() has no
 * BluetoothManager, which is the state Bluetooth being off leaves it in.
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
}
