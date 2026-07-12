//Rev_1.7
/*
 * Copyright (c) 2010 - 2014, Shimmer Research, Ltd.
 * All rights reserved

 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:

 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Shimmer Research, Ltd. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @author Jong Chern Lim, Ruaidhri Molloy
 * @date   October, 2013
 *
 * Changes since 1.6
 * - cancel timers for log and stream upon disconnect
 *
 * Changes since 1.5.1
 * - move response time out to ShimmerBluetooth
 *
 * Changes since 1.5 (12 May 2014, RM first revision)
 * - Addition of Strain Gauge for Shimmer3
 *
 *  Changes since beta 1.4
 *  - updated lowbattindicator for Shimmer2r
 *
 *  Changes since beta 1.3.1 (17 Oct 2013)
 *  - added get method for mPressureResolution 
 *  - updated the smart accel mode, which was over complicating things, right now there are two modes, smart accel mode will include 'Accelerometer X'. 'Accelerometer X' is picked based on which accelerometer meets the defined range and has the lowest noise. For backward compatibility users are advised to use Smart Mode. Normal_Mode will not add 'Accelerometer X'.
 *  - fixed boolean indicator for gyro on the fly method  
 *  - fixed getListofEnabledSensors method
 *  - fixed decimalformat which was returning commas for decimal points for certain regions/locale
 *  - fixed a bug when using max number of sensors on Shimmer3
 *  - updated initialize2r method which was not reading the magrange
 *  - fixed a bug with the pressure sensor calibration parameters
 *  - rename NORMAL_MODE to ACCEL_NORMAL_MODE
 *  - added support for Shimmer3 GSR
 *  - minor fix to ACCEL_NORMAL_MODE
 *  - added rawdata and systemtimestamp to objectcluster
 *  - add option to disable the calibration of data, see function enableCalibration()
 *  - removed mgetdatainstructions, not used for anything
 *  - added support for saving and retrieving rawcalibrationparams, should only be used after connected to a device
 *  - updated various variables to protected so it can be inherited
 *  - switch logfile id to mClassName from "Shimmer"
 *  - fixed initialize Shimmer3, maggain and gyrorange being set wrongly
 *  - Shimmer3 GSR support
 *  - Updated the structure to allow future addons (.eg. ShimmerFile , ShimmerBTLE, Shimmer802_15_4) which will all inherit ShimmerObject, also will allow exporting the code to non andoid use, users will just have to remove Shimmer.java which has the Android related code
 *  - Support for internal exp power (Shimmer3) added
 *  
 *  Changes since beta 1.2 (14 Oct 2013)
 *  - added support read data for Shimmer3 pressure sensor
 *  - added support to set pressure resolution
 * 
 *  Changes since beta 1.1.3 (10 Oct 2013)
 *  - updated to work with Boilerplate and to fix a bug with version control as there is BTStream 0.1.0 and Boilerplate 0.1.0 
 *  - minor change to comments
 *  - fixed twos complement function
 *   
 *  Changes since beta 1.1.2 (1 Oct 2013)
 *  - mag gain command implemented for Shimmer2
 *  
 *  Changes since beta 1.1.1 (17 July 2013)
 *  - 
 *  - added support for dual accelerometer mode for Shimmer 3
 *  - updated wide range accel from i12> to i16 and updated default calibration values
 *  
 *  Changes since beta 1.0.2 (17 July 2013)
 *  - started integration with Shimmer 3, major changes include the use of i16* now. This indicates array of bytes where MSB is on the far left/smallest index number of the array.
 *  - minor fix to the stop streaming command, causing it to block the inputstream, and not being able to clear the bytes from minstream
 *  - added default calibration parameters for Shimmer 3
 *  - added functionality for internal and external adc
 *  - added new constructor to support setup device on connect (Shimmer 3)
 *  
 * Changes since beta 1.0.1 (20 June 2013)
 * - Fix the no response bug, through the use of the function dummyreadSamplingRate()
 * - Updates to allow operation with Boilerplate
 * - add get functions for lower power mag and gyro cal on the fly
 * 
 * Changes since beta 1.0 (21 May 2013)
 * - Added support for on the fly gyro offset calibration
 * - Added quartenions
 * - Convert to an instruction stack format, no longer supports Boilerplate
 * 
 * Changes since beta 0.9 (1 January 2013)
 * 
 * - Packet Reception Rate is now provided, whenever a packet loss detected a message is sent via handler, see MESSAGE_PACKET_LOSS_DETECTED
 * - Changed Accel cal parameters
 * - Added default cal parameters for the other accel ranges, if default accel range is being used, changing the accel range will change the defaults automatically as well
 * - Revised the GSR calibration method, now uses a linear fit
 * - Batt Voltage Monitoring
 * - Sensor Conflict checks, have to wire the handler in order to see the msgs
 * - Bug fix, timer wasnt triggering when waiting for response which was not received, causing the driver to get stuck in a loop
 * - Added retrieve all,ecg & emg calibration parameters, only works with Boilerplate >= 1.0
 * - Rearranged the data reception section, to accommodate for in streaming ack detection
 * - Added uncalibrated heart rate, which is a pulse now, with the value being the time difference between the last pulse and the current one
 * - Updated the name of the formats, units and property names, so as to stay consistent with the rest of the instrument drivers
 * - Low Battery Voltage warning at 3.4V where LED turns yellow
 * - Added Packet Reception Rate monitoring
 * - Added MESSAGE_NOT_SYNC for MSS support
 * - Updated the initialization process, if a connection fails during the initialization process, disconnect is done immediately
 * - Update Toggle LED
 * - Switched to the use of createInsecureRfcommSocketToServiceRecord 
 * - ECG and EMG units have a * (mVolts*) as an indicator when default parameters are used
 * - SR 30 support
 * - Orientation 
 * - Support for low and high power Mag (high power == high sampling rate Mag)
 * - Support for different mag range
 * - Updated the execution model when transmitting commands, now uses a thread, and will improve Main UI thread latency 
 
 * */

package com.shimmerresearch.android;

import static android.content.Context.BLUETOOTH_SERVICE;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.shimmerresearch.bluetooth.BluetoothProgressReportPerCmd;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driver.shimmer2r3.ConfigByteLayoutShimmer3;
import com.shimmerresearch.driver.shimmer4sdk.Shimmer4sdk;
import com.shimmerresearch.driverUtilities.ShimmerBattStatusDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.exgConfig.ExGConfigOptionDetails;

import org.apache.commons.lang3.ArrayUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import it.gerdavax.easybluetooth.BtSocket;
import it.gerdavax.easybluetooth.LocalDevice;
import it.gerdavax.easybluetooth.RemoteDevice;
//import java.io.FileOutputStream;

public class Shimmer extends ShimmerBluetooth{
	//generic UUID for serial port protocol
	private UUID mSPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	// Message types sent from the Shimmer Handler

	/**
	 *
	 * @deprecated This MSG are now in ShimmerBluetooth, the values have been changed to match those in ShimmerBluetooth
	 */
	@Deprecated
	public static final int MESSAGE_STATE_CHANGE = 0; //changed to 0 to match Shimmer Bluetooth, this messages should no longer be used but kept for backward compatibility
	@Deprecated
	public static final int MESSAGE_READ = 2;
	@Deprecated
	public static final int MESSAGE_ACK_RECEIVED = 4;
	@Deprecated
	public static final int MESSAGE_DEVICE_NAME = 5;

	public static final int MESSAGE_TOAST = 999;

	@Deprecated
	public static final int MESSAGE_STOP_STREAMING_COMPLETE = 9;
	@Deprecated
	public static final int MESSAGE_PACKET_LOSS_DETECTED = 11;
	@Deprecated
	public static final int MESSAGE_LOG_AND_STREAM_STATUS_CHANGED = 13;
	@Deprecated
	public static final int MESSAGE_PROGRESS_REPORT = 14;
	
	// Key names received from the Shimmer Handler 
	public static final String TOAST = "toast";
	transient private final BluetoothAdapter mAdapter;
	//transient public final Handler mHandler;

	transient private ConnectThread mConnectThread;
	transient private ConnectedThread mConnectedThread;
	private boolean mDummy=false;
	transient private LocalDevice localDevice;
	//private InputStream mInputStream=null;
	//private DataInputStream mInStream=null;
	transient private DataInputStream mInStream;
	//private BufferedInputStream mInStream=null;
	transient private OutputStream mmOutStream=null;

	@Deprecated
	public static final int MSG_STATE_FULLY_INITIALIZED = 3;  // This is the connected state, indicating the device has establish a connection + tx/rx commands and reponses (Initialized)
	public static final int MSG_STATE_STREAMING = 4;
	public static final int MSG_STATE_STOP_STREAMING = 5;

	transient private Context mContext;
	protected String mClassName="Shimmer";

	private int mBluetoothLib=0;												// 0 = default lib, 1 = arduino lib
	transient private BluetoothAdapter mBluetoothAdapter = null;
	transient List<Handler> mHandlerList = new CopyOnWriteArrayList<Handler>();
	private boolean mReceiverRegistered = false;
	{
		setEnableProcessMarker(false);
	}
	private boolean mContinuousStateUpdates = true;


	{
		setUseInfoMemConfigMethod(true);
	}

	protected  void unregisterDisconnectListener(){
		if(mContext!=null && mReceiverRegistered) {
			try {
				mContext.unregisterReceiver(mReceiver);
			} catch (IllegalArgumentException ex){
				System.out.println(ex);
			}
			mReceiverRegistered = false;
		}
	}

	protected  void registerDisconnectListener(){
		if(mContext!=null && !mReceiverRegistered) {
			System.out.println("initialize process 0) register disconnect listener");
			BluetoothAdapter bluetoothAdapter = null;
			if (android.os.Build.VERSION.SDK_INT >= 18) {
				BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(BLUETOOTH_SERVICE);
				bluetoothAdapter = bluetoothManager.getAdapter();
			} else bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

			IntentFilter filter = new IntentFilter(bluetoothAdapter.ACTION_STATE_CHANGED);
			filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
			filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
			ContextCompat.registerReceiver(mContext, mReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
			mReceiverRegistered = true;
		}
	}

	/**
	 * This constructor is for applications that only require one Handler.
	 * @param handler add handler to receive msgs from the shimmer class
	 */
	public Shimmer(Handler handler, Context context) {
		super();
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mBluetoothRadioState = BT_STATE.DISCONNECTED;
		mHandlerList.add(0, handler);
//		mContinousSync=continousSync;
		mSetupDeviceWhileConnecting=false;
		mUseProcessingThread = true;
		mContext = context;
	}
	// The BroadcastReceiver that listens for discovered devices and
	// changes the title when discovery is finished
	transient private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			// When discovery finds a device
			if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				String macAdd = device.getAddress();
				if (macAdd.equals(mMyBluetoothAddress)){
					connectionLost();
				}
			}
		}
	};

	/**
	 * This constructor is for applications requiring more than one Handler so as to receive the msg
	 * in multiple threads.
	 * @param handlerList this is an ArrayList containing multiple Handlers
	 */
	public Shimmer(ArrayList<Handler> handlerList, Context context) {
		super();
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mBluetoothRadioState = BT_STATE.DISCONNECTED;
		mHandlerList = new CopyOnWriteArrayList<Handler>(handlerList);
		mSetupDeviceWhileConnecting = false;
		mUseProcessingThread = true;
		mContext = context;
	}

	/**
	 *
	 * @deprecated Do not use this method!
	 * myName will not be set as the shimmer user assigned name
	 */
	@Deprecated
	public Shimmer(Handler handler, String myName, Boolean continousSync) {
		super();
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mBluetoothRadioState = BT_STATE.DISCONNECTED;
		mHandlerList.add(handler);
		mShimmerUserAssignedName=myName;
//		mContinousSync=continousSync;
		mSetupDeviceWhileConnecting=false;
		mUseProcessingThread = true;
	}
	/**
	 *
	 * @deprecated Do not use this method!
	 * myName will not be set as the shimmer user assigned name
	 */
	@Deprecated
	public Shimmer(Context context, Handler handler, String myName, Boolean continousSync) {
		super();
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mBluetoothRadioState = BT_STATE.DISCONNECTED;
		mHandlerList.add(handler);
		mShimmerUserAssignedName=myName;
//		mContinousSync=continousSync;
		mSetupDeviceWhileConnecting=false;
		mUseProcessingThread = true;
	}
	
	
	/**
	 * Constructor. Prepares a new Bluetooth session. Additional fields allows the device to be set up immediately.
	 * @param context  The UI Activity Context
	 * @param handler  A Handler to send messages back to the UI Activity
	 * @param myName  To allow the user to set a unique identifier for each Shimmer device
	 * @param samplingRate Defines the sampling rate
	 * @param accelRange Defines the Acceleration range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 * @param gsrRange Numeric value defining the desired gsr range. Valid range settings are 0 (10kOhm to 56kOhm),  1 (56kOhm to 220kOhm), 2 (220kOhm to 680kOhm), 3 (680kOhm to 4.7MOhm) and 4 (Auto Range).
	 * @param setEnabledSensors Defines the sensors to be enabled (e.g. 'Shimmer.SENSOR_ACCEL|Shimmer.SENSOR_GYRO' enables the Accelerometer and Gyroscope)
	 * @param continousSync A boolean value defining whether received packets should be checked continuously for the correct start and end of packet.
	 */
	@Deprecated
	public Shimmer(Context context, Handler handler, String myName, double samplingRate, int accelRange, int gsrRange, long setEnabledSensors, boolean continousSync) {
		super();
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mBluetoothRadioState = BT_STATE.DISCONNECTED;
		mHandlerList.add(handler);
		setSamplingRateShimmer(samplingRate);
		setDigitalAccelRange(accelRange); //		mAccelRange = accelRange;
		mGSRRange = gsrRange;
		mSetEnabledSensors=setEnabledSensors;
		mShimmerUserAssignedName = myName;
		mSetupDeviceWhileConnecting = true;
//		mContinousSync = continousSync;
		mUseProcessingThread = true;
	}

	/**
	 * Constructor. Prepares a new Bluetooth session. Additional fields allows the device to be set up immediately.
	 * @param context  The UI Activity Context
	 * @param handler  A Handler to send messages back to the UI Activity
	 * @param myName  To allow the user to set a unique identifier for each Shimmer device
	 * @param samplingRate Defines the sampling rate
	 * @param accelRange Defines the Acceleration range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 * @param gsrRange Numeric value defining the desired gsr range. Valid range settings are 0 (10kOhm to 56kOhm),  1 (56kOhm to 220kOhm), 2 (220kOhm to 680kOhm), 3 (680kOhm to 4.7MOhm) and 4 (Auto Range).
	 * @param setEnabledSensors Defines the sensors to be enabled (e.g. 'Shimmer.SENSOR_ACCEL|Shimmer.SENSOR_GYRO' enables the Accelerometer and Gyroscope)
	 * @param continousSync A boolean value defining whether received packets should be checked continuously for the correct start and end of packet.
	 */
	@Deprecated
	public Shimmer(Context context, Handler handler, String myName, double samplingRate, int accelRange, int gsrRange, long setEnabledSensors, boolean continousSync, int magRange) {
		super();
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mBluetoothRadioState = BT_STATE.DISCONNECTED;
		mHandlerList.add(handler);
		setSamplingRateShimmer(samplingRate);
		setDigitalAccelRange(accelRange); //		mAccelRange = accelRange;
		setMagRange(magRange); // mMagRange = magRange;
		mGSRRange = gsrRange;
		mSetEnabledSensors=setEnabledSensors;
		mShimmerUserAssignedName = myName;
		mSetupDeviceWhileConnecting = true;
//		mContinousSync = continousSync;
		mUseProcessingThread = true;
	}

	
	/**
	 * Constructor. Prepares a new Bluetooth session. Additional fields allows the device to be set up immediately.
	 * @param context  The UI Activity Context
	 * @param handler  A Handler to send messages back to the UI Activity
	 * @param myName  To allow the user to set a unique identifier for each Shimmer device
	 * @param samplingRate Defines the sampling rate
	 * @param accelRange Defines the Acceleration range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 * @param gsrRange Numeric value defining the desired gsr range. Valid range settings are 0 (10kOhm to 56kOhm),  1 (56kOhm to 220kOhm), 2 (220kOhm to 680kOhm), 3 (680kOhm to 4.7MOhm) and 4 (Auto Range).
	 * @param setEnabledSensors Defines the sensors to be enabled (e.g. 'Shimmer.SENSOR_ACCEL|Shimmer.SENSOR_GYRO' enables the Accelerometer and Gyroscope)
	 * @param continousSync A boolean value defining whether received packets should be checked continuously for the correct start and end of packet.
	 */
	@Deprecated
	public Shimmer(Context context, Handler handler, String myName, double samplingRate, int accelRange, int gsrRange, long setEnabledSensors, boolean continousSync, boolean enableLowPowerAccel, boolean enableLowPowerGyro, boolean enableLowPowerMag, int gyroRange, int magRange) {
		super();
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mBluetoothRadioState = BT_STATE.DISCONNECTED;
		mHandlerList.add(handler);
		setSamplingRateShimmer(samplingRate);
		setDigitalAccelRange(accelRange); // mAccelRange = accelRange;
		mGSRRange = gsrRange;
		mSetEnabledSensors=setEnabledSensors;
		mShimmerUserAssignedName = myName;
		mSetupDeviceWhileConnecting = true;
//		mContinousSync = continousSync;

		setLowPowerMag(enableLowPowerMag);
		setLowPowerAccelWR(enableLowPowerAccel);
		setLowPowerGyro(enableLowPowerGyro);

		setGyroRange(gyroRange); //	mGyroRange = gyroRange;
		setMagRange(magRange); // mMagRange = magRange;
		mUseProcessingThread = true;
	}


	/**
	 * Constructor. Prepares a new Bluetooth session. Additional fields allows the device to be set up immediately.
	 * @param context  The UI Activity Context
	 * @param handler  A Handler to send messages back to the UI Activity
	 * @param myName  To allow the user to set a unique identifier for each Shimmer device
	 * @param samplingRate Defines the sampling rate
	 * @param accelRange Defines the Acceleration range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 * @param gsrRange Numeric value defining the desired gsr range. Valid range settings are 0 (10kOhm to 56kOhm),  1 (56kOhm to 220kOhm), 2 (220kOhm to 680kOhm), 3 (680kOhm to 4.7MOhm) and 4 (Auto Range).
	 * @param setEnabledSensors Defines the sensors to be enabled (e.g. 'Shimmer.SENSOR_ACCEL|Shimmer.SENSOR_GYRO' enables the Accelerometer and Gyroscope)
	 * @param continousSync A boolean value defining whether received packets should be checked continuously for the correct start and end of packet.
	 */
	@Deprecated
	public Shimmer(Context context, Handler handler, String myName, double samplingRate, int accelRange, int gsrRange, long setEnabledSensors, boolean continousSync, boolean enableLowPowerAccel, boolean enableLowPowerGyro, boolean enableLowPowerMag, int gyroRange, int magRange,byte[] exg1,byte[] exg2) {
		super();
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mBluetoothRadioState = BT_STATE.DISCONNECTED;
		mHandlerList.add(handler);
		setSamplingRateShimmer(samplingRate);
		setDigitalAccelRange(accelRange); //		mAccelRange = accelRange;
		mGSRRange = gsrRange;
		mSetEnabledSensors=setEnabledSensors;
		mShimmerUserAssignedName = myName;
		mSetupDeviceWhileConnecting = true;
//		mContinousSync = continousSync;

		setLowPowerMag(enableLowPowerMag);
		setLowPowerAccelWR(enableLowPowerAccel);
		setLowPowerGyro(enableLowPowerGyro);

		setGyroRange(gyroRange); //	mGyroRange = gyroRange;
		setMagRange(magRange); // mMagRange = magRange;
		mSetupEXG = true;
		mEXG1RegisterArray = exg1;
		mEXG2RegisterArray = exg2;
		mUseProcessingThread = true;
	}

	/** Shimmer 3 Constructor
	 * @param userAssignedName
	 * @param samplingRate
	 * @param accelRange
	 * @param gsrRange
	 * @param sensorIdsToEnable
	 * @param gyroRange
	 * @param magRange
	 * @param orientation
	 * @param pressureResolution
	 * @param context
	 */
	public Shimmer(Handler handler, String userAssignedName, double samplingRate, int accelRange, int gsrRange, Integer[] sensorIdsToEnable, int gyroRange, int magRange, int orientation, int pressureResolution, Context context){
		super(userAssignedName, samplingRate, sensorIdsToEnable, accelRange, gsrRange, gyroRange, magRange, pressureResolution);
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mBluetoothRadioState = BT_STATE.DISCONNECTED;
		mHandlerList.add(handler);
		setupOrientation(orientation, samplingRate);
		mUseProcessingThread = true;
		mContext = context;
	}

	/** Shimmer2R Constructor
	 * @param myName
	 * @param samplingRate
	 * @param accelRange
	 * @param gsrRange
	 * @param setEnabledSensors
	 * @param magGain
	 * @param orientation
	 */
	public Shimmer(Handler handler, String myName, double samplingRate, int accelRange, int gsrRange, int setEnabledSensors, int magGain, int orientation, Context context) {
		super(myName,samplingRate, setEnabledSensors, accelRange, gsrRange, magGain);
		setupOrientation(orientation, samplingRate);
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mBluetoothRadioState = BT_STATE.DISCONNECTED;
		mHandlerList.add(handler);
		setupOrientation(orientation, samplingRate);
		mUseProcessingThread = true;
		mContext = context;
	}

	/**
	 * Shimmer 3 Constructor with option to enable/disable calibration
	 * @param handler
	 * @param userAssignedName
	 * @param samplingRate
	 * @param accelRange
	 * @param gsrRange
	 * @param sensorIdsToEnable
	 * @param gyroRange
	 * @param magRange
	 * @param orientation
	 * @param pressureResolution
	 * @param enableCalibration
	 */
	public Shimmer(Handler handler, String userAssignedName, double samplingRate, int accelRange, int gsrRange, Integer[] sensorIdsToEnable, int gyroRange, int magRange, int orientation, int pressureResolution, boolean enableCalibration, Context context){
		super(userAssignedName, samplingRate, sensorIdsToEnable, accelRange, gsrRange, gyroRange, magRange, pressureResolution);
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mBluetoothRadioState = BT_STATE.DISCONNECTED;
		mHandlerList.add(handler);
		setupOrientation(orientation, samplingRate);
		setEnableCalibration(enableCalibration);
		mUseProcessingThread = true;
		mContext = context;
	}

	/**
	 * Shimmer 2R Constructor with option to enable/disable calibration
	 * @param handler
	 * @param myName
	 * @param samplingRate
	 * @param accelRange
	 * @param gsrRange
	 * @param setEnabledSensors
	 * @param magGain
	 * @param orientation
	 * @param enableCalibration
	 */
	public Shimmer(Handler handler, String myName, double samplingRate, int accelRange, int gsrRange, int setEnabledSensors, int magGain, int orientation, boolean enableCalibration, Context context) {
		super(myName,samplingRate, setEnabledSensors, accelRange, gsrRange, magGain);
		setupOrientation(orientation, samplingRate);
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mBluetoothRadioState = BT_STATE.DISCONNECTED;
		mHandlerList.add(handler);
		setupOrientation(orientation, samplingRate);
		setEnableCalibration(enableCalibration);
		mUseProcessingThread = true;
		mContext = context;
	}

	/**
	 * Set the current state of the chat connection
	 * @param state  An integer defining the current connection state
	 */
	/*protected synchronized void setState(int state) {
		mState = state;
		// Give the new state to the Handler so the UI Activity can update
		mHandler.obtainMessage(Shimmer.MESSAGE_STATE_CHANGE, state, -1, new ObjectCluster(mShimmerUserAssignedName,getBluetoothAddress())).sendToTarget();
	}*/

	/**
	 * Return the current connection state. */
	/*public synchronized int getShimmerState() {
		return mState;
	}
*/
	/**
	 * Start the ConnectThread to initiate a connection to a remote device. The purpose of having two libraries is because some Stock firmware do not implement the full Bluetooth Stack. In such cases use 'gerdavax'. If problems persist consider installing an aftermarket firmware, with a mature Bluetooth stack.
	 * @param address Bluetooth Address of Device to connect too
	 * @param bluetoothLibrary Supported libraries are 'default' and 'gerdavax'  
	 */
	public synchronized void connect(final String address, String bluetoothLibrary) {
		registerDisconnectListener();
		mIamAlive = false;
		getListofInstructions().clear();
		mFirstTime=true;

		if ("default".equals(bluetoothLibrary)){
			mMyBluetoothAddress=address;
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

			// Cancel any thread attempting to make a connection
			if (mBluetoothRadioState == BT_STATE.CONNECTING) {
				if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
			}
			// Cancel any thread currently running a connection
			if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

			// Start the thread to connect with the given device
			mConnectThread = new ConnectThread(device);
			mConnectThread.start();
			setBluetoothRadioState(BT_STATE.CONNECTING);
		} else if ("gerdavax".equals(bluetoothLibrary)){
			mMyBluetoothAddress=address;
			// Cancel any thread attempting to make a connection
			if (mBluetoothRadioState == BT_STATE.CONNECTING) {
				if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
			}
			// Cancel any thread currently running a connection
			if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

			if (address == null) return;
			Log.d("ConnectionStatus","Get Local Device  " + address);

			localDevice = LocalDevice.getInstance();
			RemoteDevice device = localDevice.getRemoteForAddr(address);
			new ConnectThreadArduino(device, this).start();
			setBluetoothRadioState(BT_STATE.CONNECTING);
			/*localDevice.init(this, new ReadyListener() {
   			@Override
   			public synchronized void ready() {


   				//localDevice.destroy();


   			}
   		});*/


		}
	}

	/**
	 * Start the ConnectedThread to begin managing a Bluetooth connection
	 * @param socket  The BluetoothSocket on which the connection was made
	 */
	public synchronized void connected(BluetoothSocket socket) {
		System.out.println("initialize process 2) connected and start initialize");
		// Cancel the thread that completed the connection
		if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
		// Start the thread to manage the connection and perform transmissions
		mConnectedThread = new ConnectedThread(socket);
		mIOThread = new IOThread();
		mIOThread.setName("IO Thread " + socket.getRemoteDevice().getAddress());
		mIOThread.start();
		if (mUseProcessingThread){
			mPThread = new ProcessingThread();
			mPThread.setName("P Thread " + socket.getRemoteDevice().getAddress());
			mPThread.start();
		}

		// Send the name of the connected device back to the UI Activity
		//TODO: Delete this...
//		Message msg = mHandler.obtainMessage(Shimmer.MESSAGE_DEVICE_NAME);
//		mHandler.sendMessage(msg);
		sendMsgToHandlerList(Shimmer.MESSAGE_DEVICE_NAME);

		//setState(BT_STATE.CONNECTED);
		initialize();
	}

	/**
	 * Stop all threads
	 */
	public void stop() {
		if (mTimerReadStatus!=null) {
			mTimerReadStatus.cancel();
			mTimerReadStatus.purge();
		}
		
		if (mTimerCheckAlive!=null){
			mTimerCheckAlive.cancel();
			mTimerCheckAlive.purge();
			mTimerCheckAlive = null;
		}
		
		if (mTimerCheckForAckOrResp!=null){
			mTimerCheckForAckOrResp.cancel();
			mTimerCheckForAckOrResp.purge();
		}
		setBluetoothRadioState(BT_STATE.DISCONNECTED);
		mIsStreaming = false;
		mIsInitialised = false;
		if (mIOThread != null) {
			mIOThread.stop = true;
			mIOThread = null;
			if(mUseProcessingThread && mPThread != null){
				mPThread.stop =true;
				mPThread = null;
			}

		}
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

	}

	@Override
	protected void clearSingleDataPacketFromBuffers(byte[] bufferTemp, int packetSize) {
		byte[] fullBuffer = mByteArrayOutputStream.toByteArray();
		if ((fullBuffer.length-packetSize)<0){
			return;
		}
		byte[] keepBuffer = new byte[fullBuffer.length-packetSize];
		System.arraycopy(fullBuffer,packetSize,keepBuffer,0,keepBuffer.length);
		this.mByteArrayOutputStream.reset();
		try {
			this.mByteArrayOutputStream.write(keepBuffer);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		if (this.mEnablePCTimeStamps) {
			for(int i = 0; i < packetSize; ++i) {
				try {
					this.mListofPCTimeStamps.remove(0);
				} catch (Exception var5) {
					this.consolePrintException(var5.getMessage(), var5.getStackTrace());
				}
			}
		}

	}
	@Override
	protected void processPacket() {
		setIamAlive(true);
		byte[] allBytes = mByteArrayOutputStream.toByteArray();
		//consolePrintLn("Byte Array Size: " + allBytes.length);
		if (mByteArrayOutputStream.size()>10000){
			mByteArrayOutputStream.reset();
			mListofPCTimeStamps.clear();
			consolePrintLn("Byte Array Size (reset): " + mByteArrayOutputStream.size());
			return;
		}
		byte[] bufferTemp = new byte[getPacketSizeWithCrc()+2];
		System.arraycopy(allBytes,0,bufferTemp,0,bufferTemp.length);
		//Data packet followed by another data packet
		if(bufferTemp[0]==DATA_PACKET
				&& bufferTemp[getPacketSizeWithCrc()+1]==DATA_PACKET){

			if (mBtCommsCrcModeCurrent != BT_CRC_MODE.OFF && !checkCrc(bufferTemp, getPacketSize() + 1)) {
				discardFirstBufferByte();
				return;
			}

			//Handle the data packet
			processDataPacket(bufferTemp);
			clearSingleDataPacketFromBuffers(bufferTemp, getPacketSizeWithCrc()+1);
		}

		//Data packet followed by an ACK (suggesting an ACK in response to a SET BT command or else a BT response command)
		else if(bufferTemp[0]==DATA_PACKET
				&& bufferTemp[getPacketSizeWithCrc()+1]==ACK_COMMAND_PROCESSED){

			if(mByteArrayOutputStream.size()>getPacketSizeWithCrc()+2){
				allBytes = mByteArrayOutputStream.toByteArray();
				bufferTemp = new byte[getPacketSizeWithCrc()+3]; //check if the next byte is data packet
				System.arraycopy(allBytes,0,bufferTemp,0,bufferTemp.length);
				if(bufferTemp[getPacketSizeWithCrc()+2]==DATA_PACKET){
					//Firstly handle the data packet
					processDataPacket(bufferTemp);
					clearSingleDataPacketFromBuffers(bufferTemp, getPacketSizeWithCrc()+2); //clear an extra byte which is the ack

					//Then handle the ACK from the last SET command
					if(isKnownSetCommand(mCurrentCommand)){
						stopTimerCheckForAckOrResp(); //cancel the ack timer
						mWaitForAck=false;

						processAckFromSetCommand(mCurrentCommand);

						mTransactionCompleted = true;
						setInstructionStackLock(false);
					}
					printLogDataForDebugging("Ack Received for Command: \t\t\t" + btCommandToString(mCurrentCommand));
				}

				//this is for LogAndStream support, command is transmitted and ack received
				else if(isSupportedInStreamCmds() && bufferTemp[getPacketSizeWithCrc()+2]==INSTREAM_CMD_RESPONSE){
					printLogDataForDebugging("COMMAND TXed and ACK RECEIVED IN STREAM");
					printLogDataForDebugging("INS CMD RESP");

					//Firstly handle the in-stream response
					stopTimerCheckForAckOrResp(); //cancel the ack timer
					mWaitForResponse=false;
					mWaitForAck=false;

					processInstreamResponse(true);

					// Need to remove here because it is an
					// in-stream response while streaming so not
					// handled elsewhere
					if(getListofInstructions().size()>0){
						removeInstruction(0);
					}

					mTransactionCompleted=true;
					setInstructionStackLock(false);

					//Then process the Data packet
					//processDataPacket(bufferTemp);
					//clearBuffers();
				}
				else {
					printLogDataForDebugging("Unknown parsing error while streaming");
					discardFirstBufferByte(); //throw the first byte away
				}
			}
			/*
			if(mByteArrayOutputStream.size()>getPacketSizeWithCrc()+2){
				printLogDataForDebugging("Unknown packet error (check with JC):\tExpected: " + (getPacketSizeWithCrc()+2) + "bytes but buffer contains " + mByteArrayOutputStream.size() + "bytes");
				discardFirstBufferByte(); //throw the first byte away
			}
			 */

		}
		//TODO: ACK in bufferTemp[0] not handled
		//else if
		else {
			printLogDataForDebugging("Packet syncing problem:\tExpected: " + (getPacketSizeWithCrc()+2) + "bytes. Buffer contains " + mByteArrayOutputStream.size() + "bytes"
					+ "\nBuffer = " + UtilShimmer.bytesToHexStringWithSpacesFormatted(mByteArrayOutputStream.toByteArray()));
			discardFirstBufferByte(); //throw the first byte away
		}
	}

	protected void discardFirstBufferByte(){
		byte[] bTemp = mByteArrayOutputStream.toByteArray();
		mByteArrayOutputStream.reset();
		mByteArrayOutputStream.write(bTemp, 1, bTemp.length-1); //this will throw the first byte away
		if(mEnablePCTimeStamps) {
			mListofPCTimeStamps.remove(0);
		}
		consolePrintLn("Throw Byte" + UtilShimmer.bytesToHexStringWithSpacesFormatted(bTemp));
	}

	protected byte[] getDataFromArrayOutputStream(int extraBytesLength){
		if (mByteArrayOutputStream.size() >= getPacketSizeWithCrc() + extraBytesLength) {
			byte[] allBytes = mByteArrayOutputStream.toByteArray();
			byte[] bufferTemp = new byte[getPacketSizeWithCrc() + extraBytesLength]; //check if the next byte is data packet
			System.arraycopy(allBytes, 0, bufferTemp, 0, bufferTemp.length);
			return bufferTemp;
		}
		return null;
	}

	/** process responses to in-stream response */
	@Override
	protected void processInstreamResponse(boolean shouldClearCrcFromBuffer) {

		if (!mBluetoothRadioState.equals(BT_STATE.STREAMING)){ //every other start besides streaming
			super.processInstreamResponse(shouldClearCrcFromBuffer);
		} else { //streaming state
			//byte[] inStreamResponseCommandBuffer = readBytes(1, INSTREAM_CMD_RESPONSE);
			byte[] bufferTemp = getDataFromArrayOutputStream(4);
			if (bufferTemp != null) {
				byte inStreamResponseCommand = bufferTemp[bufferTemp.length - 1];
				consolePrintLn("In-stream received = " + btCommandToString(inStreamResponseCommand));

				if (inStreamResponseCommand == DIR_RESPONSE) {
					printLogDataForDebugging("IN STREAM: DIR_RESPONSE");
					//byte[] responseData = readBytes(1, inStreamResponseCommand);
					bufferTemp = getDataFromArrayOutputStream(5);
					if (bufferTemp != null) {
						int directoryNameLength = bufferTemp[bufferTemp.length - 1];
						byte[] bufferDirectoryName = new byte[directoryNameLength];
						bufferTemp = getDataFromArrayOutputStream(5 + directoryNameLength);
						System.arraycopy(bufferTemp, bufferTemp.length - bufferDirectoryName.length, bufferDirectoryName, 0, bufferDirectoryName.length);
						if (bufferDirectoryName != null) {
							String tempDirectory = new String(bufferDirectoryName);
							mDirectoryName = tempDirectory;
							printLogDataForDebugging("Directory Name = " + mDirectoryName);
						}
						processDataPacket(bufferTemp);
						clearSingleDataPacketFromBuffers(bufferTemp, bufferTemp.length + mBtCommsCrcModeCurrent.getNumCrcBytes());
					}
				} else if (inStreamResponseCommand == STATUS_RESPONSE) {
					printLogDataForDebugging("IN STREAM: STATUS_RESPONSE");
					int length = 5;
					int statusBytesToRead = 0;
					if(isSupportedUSBPluggedInStatus()) {
						length+=1;
						statusBytesToRead = 2;
					} else {
						statusBytesToRead = 1;
					}
					bufferTemp = getDataFromArrayOutputStream(length);
					if (bufferTemp != null) {
						byte[] responseData = new byte[statusBytesToRead];
						System.arraycopy(bufferTemp, bufferTemp.length - responseData.length, responseData, 0, responseData.length);
						if (responseData != null) {
							parseStatusByte(responseData);

							if (!isSupportedRtcStateInStatus()) {
								if (!mIsSensing && !isInitialised()) {
									writeRealTimeClock();
								}
							} else {
								//New case to make sure RTC is set if it hasn't been already
								if (!isSDLogging() && (!isInitialised() || !mIsRtcSet)) {
									writeRealTimeClock();
								}
							}
							eventLogAndStreamStatusChanged(mCurrentCommand);
							processDataPacket(bufferTemp);
							clearSingleDataPacketFromBuffers(bufferTemp, bufferTemp.length + mBtCommsCrcModeCurrent.getNumCrcBytes());
						}
					}
				} else if (inStreamResponseCommand == VBATT_RESPONSE) {
					printLogDataForDebugging("IN STREAM: VBATT_RESPONSE");
					bufferTemp = getDataFromArrayOutputStream(7);
					if (bufferTemp != null) {
						byte[] responseData = new byte[3];
						System.arraycopy(bufferTemp, bufferTemp.length - responseData.length, responseData, 0, responseData.length);
						if (responseData != null) {
							ShimmerBattStatusDetails battStatusDetails = new ShimmerBattStatusDetails(((responseData[1] & 0xFF) << 8) + (responseData[0] & 0xFF), responseData[2]);
							setBattStatusDetails(battStatusDetails);
							printLogDataForDebugging("Battery Status:"
									+ "\tVoltage=" + battStatusDetails.getBattVoltageParsed()
									+ "\tCharging status=" + battStatusDetails.getChargingStatusParsed()
									+ "\tBatt %=" + battStatusDetails.getEstimatedChargePercentageParsed());
						}
						processDataPacket(bufferTemp);
						clearSingleDataPacketFromBuffers(bufferTemp, bufferTemp.length + mBtCommsCrcModeCurrent.getNumCrcBytes());
					}

				} else {
					discardFirstBufferByte();
				}
			} else {
				printLogDataForDebugging("IN STREAM: buffer temp is null, byte array output stream size is " + mByteArrayOutputStream.size());
			}
		}
	}


	@Override
	protected void processWhileStreaming() {
		byte[] byteBuffer = readBytes(availableBytes());
		if(byteBuffer!=null){
			try {
				if (byteBuffer.length>0) {
					//consolePrintLn("Byte Array Size put in : " + byteBuffer.length);
					mByteArrayOutputStream.write(byteBuffer);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			//Everytime a byte is received the timestamp is taken
			if(mEnablePCTimeStamps) {
				for (int index:byteBuffer) {
					mListofPCTimeStamps.add(System.currentTimeMillis());
				}
			}
		}
		else {
			printLogDataForDebugging("readbyte null");
		}

		//If there is a full packet and the subsequent sequence number of following packet
		while(mByteArrayOutputStream.size()>=getPacketSizeWithCrc()+2){ // +2 because there are two acks
			int size = mByteArrayOutputStream.size();
			processPacket();
			int newSize = mByteArrayOutputStream.size();
			if (size == newSize){
				consolePrintLn("processWhileStreaming: Leaving while loop");
				break;
			}
			size = newSize;
		}
	}

	/**this is to clear the buffer
	 *
	 */
	@Override
	protected void clearSerialBuffer() {
		startTimerCheckForSerialPortClear();
		byte[] buffer = new byte[0];
		while (availableBytes() != 0) {
//			int available = availableBytes();
			if (bytesAvailableToBeRead()) {
				//AA-283 : the clearing of the serial bytes , is too slow when streaming 1024Hz (e.g. exg test)
				buffer = readBytes(availableBytes());

				if (mSerialPortReadTimeout) {
					break;
				}
			}
		}

		if (buffer.length > 0) {
			String msg = "Clearing Buffer:\t\t" + UtilShimmer.bytesToHexStringWithSpacesFormatted(buffer);
			printLogDataForDebugging(msg);
		}

		stopTimerCheckForSerialPortClear();
	}

	/**
	 * Write to the ConnectedThread in an unsynchronized manner
	 * @param out The bytes to write
	 * @see ConnectedThread write(byte[])
	 */
	public void write(byte[] out) {
		/*
		// Create temporary object
		ConnectedThread r;
		// Synchronize a copy of the ConnectedThread
		synchronized (this) {
			//if (mState != BT_STATE.CONNECTED && mState != BT_STATE.STREAMING) return;
			if (mBluetoothRadioState == BT_STATE.DISCONNECTED ) return;
			r = mConnectedThread;
		}
		// Perform the write unsynchronized
		r.write(out);
		*/
		if (mConnectedThread == null) return;
		mConnectedThread.write(out);
	}

	/**
	 * Indicate that the connection attempt failed and notify the UI Activity.
	 */
	private void connectionFailed() {
		setBluetoothRadioState(BT_STATE.DISCONNECTED);
		mIsInitialised = false;
		// Send a failure message back to the Activity
		//TODO: Delete this...
		//Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(TOAST, "Unable to connect device");
		sendMsgToHandlerList(MESSAGE_TOAST, bundle);
//		msg.setData(bundle);
//		mHandler.sendMessage(msg);
	}

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
	protected void connectionLost() {
		if (mIOThread != null) {
			mIOThread.stop = true;
			mIOThread = null;
			if(mUseProcessingThread && mPThread != null){
			mPThread.stop =true;
			mPThread = null;
			}

		}
		setBluetoothRadioState(BT_STATE.DISCONNECTED);
		mIsInitialised = false;
		// Send a failure message back to the Activity
		//TODO: Delete this...
		//Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(TOAST, "Device connection was lost");
		sendMsgToHandlerList(MESSAGE_TOAST, bundle);
//		msg.setData(bundle);
//		mHandler.sendMessage(msg);
	}

	/**
	 * This thread runs while attempting to make an outgoing connection
	 * with a device. It runs straight through; the connection either
	 * succeeds or fails.
	 */
	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public ConnectThread(BluetoothDevice device) {
			mmDevice = device;
			BluetoothSocket tmp = null;
			Log.d(mClassName,"Start of Default ConnectThread");
			// Get a BluetoothSocket for a connection with the
			// given BluetoothDevice
			try {
				//tmp = device.createInsecureRfcommSocketToServiceRecord(mSPP_UUID);
				tmp = device.createRfcommSocketToServiceRecord (mSPP_UUID); // If your device fails to pair try: device.createInsecureRfcommSocketToServiceRecord(mSPP_UUID)
			} catch (IOException e) {
				connectionLost();
				
			}
			mmSocket = tmp;
		}

		public void run() {
			System.out.println("initialize process 1) start connecting thread");
			setName("ConnectThread");

			if (mmSocket == null) {
				connectionFailed();
				return;
			}

			// Always cancel discovery because it will slow down a connection
			try {
				mAdapter.cancelDiscovery();
			} catch (SecurityException securityException) {
				connectionFailed();
				return;
			}

			try {
				// Connect the device through the socket. This will block
				// until it succeeds or throws an exception
				mmSocket.connect();
			} catch (IOException connectException) {
				connectionFailed();
				// Unable to connect; close the socket and get out
				try {
					mmSocket.close();
				} catch (IOException closeException) { }
				return;
			} catch (SecurityException securityException) {
				connectionFailed();
				try {
					mmSocket.close();
				} catch (IOException closeException) { }
				return;
			}
			// Reset the ConnectThread because we're done
			synchronized (Shimmer.this) {
				mConnectThread = null;
			}
			// Start the connected thread
			connected(mmSocket);
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) { }
		}
	}
	//arduino
	private class ConnectThreadArduino extends Thread {

		//private static final String TAG = "ConnectThread";
		private final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

		private final RemoteDevice mDevice;
		private BtSocket mSocket;
		Shimmer shimmer;
		public ConnectThreadArduino(RemoteDevice device, Shimmer shimmer) {
			this.shimmer = shimmer;
			mDevice = device;
			Log.d(mClassName," Start of ArduinoConnectThread");
		}

		public void run() {
			try {
				boolean isPaired = false;

				try {
					isPaired = mDevice.ensurePaired();


				}
				catch (RuntimeException re){
					re.printStackTrace();
				}

				//add a timer to wait for the user to pair the device otherwise quit
				if (!isPaired){
					Thread.sleep(10000);
					isPaired = mDevice.ensurePaired();
				}

				if (!isPaired){
					Log.d(mClassName, "not paired!");
					connectionFailed();


				}
				else {
					Log.d(mClassName, "is paired!");
					// Let main thread do some stuff to render UI immediately
					//Thread.yield();
					// Get a BluetoothSocket to connect with the given BluetoothDevice
					try {
						mSocket = mDevice.openSocket(SPP_UUID);
					} catch (Exception e) {
						Log.d(mClassName, "Connection via SDP unsuccessful, try to connect via port directly");
						// 1.x Android devices only work this way since SDP was not part of their firmware then
						mSocket = mDevice.openSocket(1);
						//connectionFailed();
						Log.d(mClassName, "I am here");
					}

					// Do work to manage the connection (in a separate thread)
					Log.d(mClassName, "Going to Manage Socket");
					if (shimmer.getState() != BT_STATE.DISCONNECTED){
						Log.d(mClassName, "ManagingSocket");
						manageConnectedSocket(mSocket);
					}
				}
			}

			catch (Exception e) {
				Log.d(mClassName,"Connection Failed");
				//sendConnectionFailed(mDevice.getAddress());
				connectionFailed();
				e.printStackTrace();
				if (mSocket != null)
					try {
						mSocket.close();
						Log.d(mClassName,"Arduinothreadclose");
					} catch (IOException e1) {}

				return;
			}
		}

		/** Will cancel an in-progress connection, and close the socket */
		@SuppressWarnings("unused")
		public void cancel() {
			try {
				if (mSocket != null) mSocket.close();
				//sendConnectionDisconnected(mDevice.getAddress());
			} 
			catch (IOException e) { Log.e("Shimmer", "cannot close socket to " + mDevice.getAddress()); }
		}

		private void manageConnectedSocket(BtSocket socket){
			//	    	Logger.d(TAG, "connection established.");
			// pass the socket to a worker thread
			String address = mDevice.getAddress();
			mConnectedThread = new ConnectedThread(socket, address);
			Log.d(mClassName, "ConnectedThread is about to start");
			mIOThread = new IOThread();
			mIOThread.start();
			if(mUseProcessingThread){
			mPThread = new ProcessingThread();
			mPThread.start();
			}
			// Send the name of the connected device back to the UI Activity
			mMyBluetoothAddress = mDevice.getAddress();
			//TODO: Delete this...
//			Message msg = mHandler.obtainMessage(Shimmer.MESSAGE_DEVICE_NAME);
//			mHandler.sendMessage(msg);
			sendMsgToHandlerList(Shimmer.MESSAGE_DEVICE_NAME);
			// Send the name of the connected device back to the UI Activity
			Log.d(mClassName, "alive!!");
			//shimmer.setState(BT_STATE.CONNECTED);
			//startStreaming();
			initialize();
		}
	}




	/**
	 * This thread runs during a connection with a remote device.
	 * It handles all incoming and outgoing transmissions.
	 */
	private class ConnectedThread{
		private BluetoothSocket mmSocket=null;

		private BtSocket mSocket=null;
		
		public ConnectedThread(BluetoothSocket socket) {

			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the BluetoothSocket input and output streams
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				connectionLost();
			}

			//mInStream = new BufferedInputStream(tmpIn);
			mInStream = new DataInputStream(tmpIn);
			mmOutStream = tmpOut;
		}

		public ConnectedThread(BtSocket socket, String address) {
			mSocket = socket;
			//this.mAddress = address;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the input and output streams, using temp objects because
			// member streams are final
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (Exception e) { Log.d(mClassName,"Connected Thread Error");
			connectionLost();}

			//mInStream = new BufferedInputStream(tmpIn);
			mInStream = new DataInputStream(tmpIn);
			mmOutStream = tmpOut;

		}

		/**
		 *The received packets are processed here 
		 */
	
		/**
		 * Write to the connected OutStream.
		 * @param buffer  The bytes to write
		 */
		private void write(byte[] buffer) {
			try {
				mmOutStream.write(buffer);
				Log.d(mClassName, "Command transmitted: " + mMyBluetoothAddress + "; Command Issued: " + mCurrentCommand );

			} catch (IOException e) {
				Log.d(mClassName, "Command NOT transmitted: " + mMyBluetoothAddress + "; Command Issued: " + mCurrentCommand );
			}
		}

		public void cancel() {
			if(mInStream != null) {
				try {
					mInStream.close();
				} catch (IOException e) {}
			}
			if(mmOutStream != null) {
				try {
					mmOutStream.close();
				} catch (IOException e) {}
			}
			if(mmSocket != null) {
				try {
					if (mBluetoothLib==0){
						mmSocket.close();
					}	else {
						mSocket.close();
					}
				} catch (IOException e) {}
			}
		}
	}





	@Override
	protected void inquiryDone() {
		//TODO: Delete this...
//		Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(TOAST, "Inquiry done for device-> " + mMyBluetoothAddress);
		sendMsgToHandlerList(MESSAGE_TOAST, bundle);
//		msg.setData(bundle);
//		mHandler.sendMessage(msg);
		isReadyForStreaming();
	}   

	@Override
	protected void isReadyForStreaming(){
		//TODO: Delete this...
//		Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(TOAST, "Device " + mMyBluetoothAddress +" is ready for Streaming");
		sendMsgToHandlerList(MESSAGE_TOAST, bundle);
//		msg.setData(bundle);
//		mHandler.sendMessage(msg);
		if (mIsInitialised == false){
			//only do this during the initialization process to indicate that it is fully initialized, dont do this for a normal inqiuiry
			mIsInitialised = true;
		}
		if(isSDLogging()){
			if (mIsInitialised){
				setBluetoothRadioState(BT_STATE.SDLOGGING);
			} else {
				setBluetoothRadioState(BT_STATE.CONNECTED);
			}
		} else {
			setBluetoothRadioState(BT_STATE.CONNECTED);
		}
		CallbackObject callBackObject = new CallbackObject(ShimmerBluetooth.NOTIFICATION_SHIMMER_FULLY_INITIALIZED, getMacId(), getComPort());
		sendMsgToHandlerListTarget(ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE, callBackObject);
		sendMsgToHandlerListTarget(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, -1, -1,
				new ObjectCluster(mShimmerUserAssignedName, getBluetoothAddress(), mBluetoothRadioState));
		//TODO: Delete this...
		//mHandler.obtainMessage(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, -1, -1, new ObjectCluster(mShimmerUserAssignedName,getBluetoothAddress(),mBluetoothRadioState)).sendToTarget();
		Log.d(mClassName,"Shimmer " + mMyBluetoothAddress +" Initialization completed and is ready for Streaming");
		if(mAutoStartStreaming){
			try {
				startStreaming();
			} catch (ShimmerException e) {
				connectionLost();
				e.printStackTrace();
			}
		}
	}

	protected void isNowStreaming() {
		//TODO: Delete this...
//		Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(TOAST, "Device " + mMyBluetoothAddress + " is now Streaming");
		sendMsgToHandlerList(MESSAGE_TOAST, bundle);
//		msg.setData(bundle);
//		mHandler.sendMessage(msg);
		Log.d(mClassName,"Shimmer " + mMyBluetoothAddress +" is now Streaming");
		if (isSDLogging()){
			setBluetoothRadioState(BT_STATE.STREAMING_AND_SDLOGGING);
			
		} else{
			setBluetoothRadioState(BT_STATE.STREAMING);
				
		}
		CallbackObject callBackObject = new CallbackObject(ShimmerBluetooth.NOTIFICATION_SHIMMER_START_STREAMING, getMacId(), getComPort());
		sendMsgToHandlerListTarget(ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE, callBackObject);
		sendMsgToHandlerListTarget(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, MSG_STATE_STREAMING, -1,
				new ObjectCluster(mShimmerUserAssignedName, getBluetoothAddress(), mBluetoothRadioState));
		//TODO: Delete this...
		//mHandler.obtainMessage(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, MSG_STATE_STREAMING, -1, new ObjectCluster(mShimmerUserAssignedName,getBluetoothAddress(),mBluetoothRadioState)).sendToTarget();
		
	}

	/*
	 * Set and Get Methods
	 * */    
//	public void setContinuousSync(boolean continousSync){
//		mContinousSync=continousSync;
//	}

	public boolean getStreamingStatus(){
		return mIsStreaming;
	}

	


	/**
	 * This returns the variable mTransactionCompleted which indicates whether the Shimmer device is in the midst of a command transaction. True when no transaction is taking place. This is deprecated since the update to a thread model for executing commands
	 * @return mTransactionCompleted
	 */
	public boolean getInstructionStatus()
	{	
		boolean instructionStatus=false;
		if (mTransactionCompleted == true) {
			instructionStatus=true;
		} else {
			instructionStatus=false;
		}
		return instructionStatus;
	}


	@Override
	protected void sendStatusMsgPacketLossDetected() {
		//TODO: Delete this...
		//mHandler.obtainMessage(Shimmer.MESSAGE_PACKET_LOSS_DETECTED,  new ObjectCluster(mShimmerUserAssignedName,getBluetoothAddress())).sendToTarget();
		sendMsgToHandlerListTarget(Shimmer.MESSAGE_PACKET_LOSS_DETECTED,
				new ObjectCluster(mShimmerUserAssignedName, getBluetoothAddress()));
	}
	



	@Override
	protected boolean bytesAvailableToBeRead() {
		
		try {
			if (mInStream.available()!=0){
				return true;
			} else {
			 return false;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			connectionLost();
			e.printStackTrace();
			return false;
		}
	}

	@Override
	protected int availableBytes() {
		try {
			return mInStream.available();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			connectionLost();
			e.printStackTrace();
			return 0;
		}
	}

	

	@Override
	protected void writeBytes(byte[] data) {
		
		write(data);
	}

	/*
	public byte[] readBytes(int numberofBytes){
		  byte[] b = new byte[numberofBytes];  
		  try{

			   int timeoutMillis = 500;
			   int bufferOffset = 0;
			   long maxTimeMillis = System.currentTimeMillis() + timeoutMillis;
			   while (System.currentTimeMillis() < maxTimeMillis && bufferOffset < b.length && mState!=STATE_NONE) {
			    int readLength = java.lang.Math.min(mInStream.available(),b.length-bufferOffset);
			    // can alternatively use bufferedReader, guarded by isReady():
			    int readResult = mInStream.read(b, bufferOffset, readLength);
			    if (readResult == -1) break;
			    bufferOffset += readResult;
		   }
			   return b;
		  } catch (IOException e) {
		   // TODO Auto-generated catch block
			   connectionLost();
			   e.printStackTrace();
			   return b;
		  }
	}*/
	
	@Override
	protected byte[] readBytes(int numberofBytes) {
		
		byte[] b = new byte[numberofBytes];
		try {
			//mIN.read(b,0,numberofBytes);
			mInStream.readFully(b,0,numberofBytes);
			return(b);
		} catch (IOException e) {
			connectionLost();
			System.out.println("Connection Lost");
			e.printStackTrace();
		}
			
			
		return null;
	}

	@Override
	protected byte readByte() {
		byte[] tb = new byte[1];
		try {
			//mInStream.read(tb,0,1);
			mInStream.readFully(tb,0,1);
			return tb[0];
		} catch (IOException e) {
			// TODO Auto-generated catch block
			connectionLost();
			e.printStackTrace();
			return 0;
		}
	}

	@Override
	protected void dataHandler(ObjectCluster ojc) {
		// TODO: Delete this...
		//mHandler.obtainMessage(ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET, ojc).sendToTarget();
		sendMsgToHandlerListTarget(ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET, ojc);
	}

	@Override
	protected void sendStatusMSGtoUI(String smsg) {
		// TODO: Delete this...
//		Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(TOAST, smsg);
		sendMsgToHandlerList(MESSAGE_TOAST, bundle);
//		msg.setData(bundle);
//		mHandler.sendMessage(msg);
	}

	@Override
	protected void printLogDataForDebugging(String msg) {
		
		Log.d(mClassName,msg);
	}

	@Override
	protected void hasStopStreaming() {
		startTimerReadStatus();
		//TODO: Delete out commented lines
//		Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(TOAST, "Device " + mMyBluetoothAddress +" stopped streaming");
//		msg.setData(bundle);
		if (isSDLogging()){
			setBluetoothRadioState(BT_STATE.SDLOGGING);
		} else {
			setBluetoothRadioState(BT_STATE.CONNECTED);
		}

		sendMsgToHandlerList(MESSAGE_TOAST, bundle);
		CallbackObject callBackObject = new CallbackObject(ShimmerBluetooth.NOTIFICATION_SHIMMER_STOP_STREAMING, getMacId(), getComPort());
		sendMsgToHandlerListTarget(ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE, callBackObject);
		sendMsgToHandlerListTarget(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, MSG_STATE_STOP_STREAMING, -1,
				new ObjectCluster(mShimmerUserAssignedName, getBluetoothAddress(), mBluetoothRadioState));
		
	}

	protected void logAndStreamStatusChanged() {

		
//		if(mCurrentCommand==START_LOGGING_ONLY_COMMAND){
//			TODO this causing a problem Shimmer Bluetooth disconnects
//			setState(BT_STATE.SDLOGGING);
//		}
		if(mCurrentCommand==STOP_LOGGING_ONLY_COMMAND){
			//TODO need to query the Bluetooth connection here!
			if(mIsStreaming){
				setBluetoothRadioState(BT_STATE.STREAMING);
			}
			else if(mIsConnected){
				setBluetoothRadioState(BT_STATE.CONNECTED);
			}
			else{
				setBluetoothRadioState(BT_STATE.DISCONNECTED);
			}
		}
		else{
			if(mIsStreaming && isSDLogging()){
				setBluetoothRadioState(BT_STATE.STREAMING_AND_SDLOGGING);
			}
			else if(mIsStreaming){
				setBluetoothRadioState(BT_STATE.STREAMING);
			}
			else if(isSDLogging()){
				setBluetoothRadioState(BT_STATE.SDLOGGING);
			}
			else{
//				if(getBTState() == BT_STATE.INITIALISED){
//					
//				}
//				else if(getBTState() != BT_STATE.CONNECTED){
//					setState(BT_STATE.CONNECTED);
//				}
				setBluetoothRadioState(BT_STATE.CONNECTED);
				//TODO: Delete this...
//				mHandler.obtainMessage(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, -1, -1, new ObjectCluster(mShimmerUserAssignedName,getBluetoothAddress(),getBluetoothRadioState())).sendToTarget();

				if(mContinuousStateUpdates) {
					sendMsgToHandlerListTarget(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, -1, -1,
							new ObjectCluster(mShimmerUserAssignedName, getBluetoothAddress(), getBluetoothRadioState()));
				}
			}
		}
		
	
		
		/*
		int docked, sensing;
		
		if(isDocked())
			docked=1;
		else
			docked=0;
		
		if(isSensing())
			sensing=1;
		else
			sensing=0;
		
		mHandler.obtainMessage(Shimmer.MESSAGE_LOG_AND_STREAM_STATUS_CHANGED, docked, sensing).sendToTarget();
		Log.d(mClassName,"Shimmer " + mMyBluetoothAddress +" Status has changed. Docked: "+docked+" Sensing: "+sensing);
		*/
	}

	@Override
	protected void processMsgFromCallback(ShimmerMsg shimmerMSG) {
		

	}

	@Override
	protected void sendProgressReport(BluetoothProgressReportPerCmd pr) {
		// TODO: Delete this...
//		mHandler.obtainMessage(MESSAGE_PROGRESS_REPORT, pr).sendToTarget();
		sendMsgToHandlerListTarget(MESSAGE_PROGRESS_REPORT, pr);
	}
	
	public BT_STATE getState() {
		return mBluetoothRadioState;
	}
	
	@Override
	public boolean setBluetoothRadioState(BT_STATE state) {
		boolean changed = super.setBluetoothRadioState(state);

		if(mBluetoothRadioState==BT_STATE.CONNECTED){
			mIsConnected = true;
			mIsStreaming = false;
		} else if(mBluetoothRadioState==BT_STATE.SDLOGGING){
			mIsConnected = true;
			mIsInitialised = true;
			mIsStreaming = false;
		}
		else if(mBluetoothRadioState==BT_STATE.STREAMING){
			mIsStreaming = true;
		}		
		else if((mBluetoothRadioState==BT_STATE.DISCONNECTED)
				||(mBluetoothRadioState==BT_STATE.CONNECTION_LOST)
				||(mBluetoothRadioState==BT_STATE.CONNECTION_FAILED)){
			mIsConnected = false;
			mIsStreaming = false;
			mIsInitialised = false;
			unregisterDisconnectListener();
		}
		
		// Give the new state to the Handler so the UI Activity can update
		//TODO: Delete this...
//		mHandler.obtainMessage(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, -1, -1, new ObjectCluster(mShimmerUserAssignedName,getBluetoothAddress(),state)).sendToTarget();

		if(mContinuousStateUpdates) {
			sendMsgToHandlerListTarget(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, -1, -1,
					new ObjectCluster(mShimmerUserAssignedName, getBluetoothAddress(), state));
		}
		return changed;
	}

	public boolean isConnected(){
		return mIsConnected;
	}

	@Override
	public void disconnect(){
		this.stop();
	}

	@Override
	protected void startOperation(BT_STATE currentOperation) {
		

	}

	@Override
	protected void startOperation(BT_STATE currentOperation, int totalNumOfCmds) {
		

	}

	@Override
	protected void eventLogAndStreamStatusChanged(byte b) {
		logAndStreamStatusChanged();
	}

	@Override
	protected void batteryStatusChanged() {
		

	}

	@Override

	public ShimmerDevice deepClone() {

		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(this);

			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (Shimmer) ois.readObject();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	protected void interpretDataPacketFormat(Object object,
			COMMUNICATION_TYPE commType) {
	}

	@Override
	public void createConfigBytesLayout() {
		//TODO check this is ok
		mConfigByteLayout = new ConfigByteLayoutShimmer3(getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal(), getHardwareVersion());
	}

	protected void finishOperation(BT_STATE currentOperation) {

		

	}

	@Override
	protected void dockedStateChange() {
		

	}

	@Override
	public boolean doesSensorKeyExist(int sensorKey) {
		
		return false;
	}

	@Override
	public Set<Integer> getSensorIdsSet() {
		return super.getSensorIdsSet();
	}

	@Override
	public void readBattery() {
		if (isStreaming()) {
			writeBytes(new byte[]{GET_VBATT_COMMAND});
		} else {
			super.readBattery();
		}
	}

	@Override
	public void readStatusLogAndStream() {
		if (this.isSupportedInStreamCmds()) {
			if (isStreaming()) {
				writeBytes(new byte[]{GET_STATUS_COMMAND});
			} else {
				super.readStatusLogAndStream();
			}
		}

	}

//	@Override
//	protected void checkBatteryShimmer2r() {
//		if (!mWaitForAck) {	
//			if (mVSenseBattMA.getMean()<mLowBattLimit*1000) {
//				if (mCurrentLEDStatus!=1) {
//					writeLEDCommand(1);
//				}
//			} else if(mVSenseBattMA.getMean()>mLowBattLimit*1000+100) { //+100 is to make sure the limits are different to prevent excessive switching when the batt value is at the threshold
//				if (mCurrentLEDStatus!=0) {
//					writeLEDCommand(0);
//				}
//			}
//
//		}
//	}
//	
//	@Override
//	protected void checkBatteryShimmer3(){
//		if (!mWaitForAck){
//			if(mVSenseBattMA.getMean()<mLowBattLimit*1000){
//				if (mCurrentLEDStatus!=1) {
//					writeLEDCommand(1);
//				}
//			}
//			else if(mVSenseBattMA.getMean()>mLowBattLimit*1000+100){ //+100 is to make sure the limits are different to prevent excessive switching when the batt value is at the threshold
//				if (mCurrentLEDStatus!=0) {
//					writeLEDCommand(0);
//				}
//			}
//		}
//	}
	public void setRadio(BluetoothSocket socket){
		System.out.println("initialize process set radio");
		registerDisconnectListener();
		if (socket.isConnected()){
			setBluetoothRadioState(BT_STATE.CONNECTING);
			mMyBluetoothAddress = socket.getRemoteDevice().getAddress();
			connected(socket);
		}
	}

	private void sendMsgToHandlerList(int obtainMessage) {
		for(Handler handler : mHandlerList) {
			if (handler!=null) {
				Message msg = handler.obtainMessage(obtainMessage);
				handler.sendMessage(msg);
			}
		}
	}

	private void sendMsgToHandlerList(int obtainMessage, Bundle bundle) {
		for(Handler handler : mHandlerList) {
			if (handler != null) {
				Message msg = handler.obtainMessage(obtainMessage);
				msg.setData(bundle);
				handler.sendMessage(msg);
			}
		}
	}

	private void sendMsgToHandlerListTarget(int what, Object object) {
		for(Handler handler : mHandlerList) {
			if (handler!=null) {
				handler.obtainMessage(what, object).sendToTarget();
			}
		}
	}

	private void sendMsgToHandlerListTarget(int what, int arg1, int arg2, Object object) {
		for(Handler handler : mHandlerList) {
			if (handler!=null) {
				handler.obtainMessage(what, arg1, arg2, object).sendToTarget();
			}
		}
	}

	public void addHandler(Handler handler) {
		mHandlerList.add(handler);
	}

	/**
	 * If true, Handlers associated with this device will receive continuous status updates
	 * @param status
	 */
	public void setContinuousStateUpdates(boolean status) {

		mContinuousStateUpdates = status;

	}

	@Override
	public void stateHandler(Object obj){
		//sendMsgToHandlerListTarget(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, obj);
	}

	private static final byte PING_BYTE = (byte) 0xB5;
	private transient java.util.concurrent.ScheduledExecutorService pingExec;
	private transient java.util.concurrent.ScheduledFuture<?> pingTask;

	public void startPingTask() {
		if (pingExec != null && !pingExec.isShutdown()) return;

		pingExec = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "ShimmerPing");
			t.setDaemon(true);
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
			return t;
		});

		// schedule every 2 seconds
		pingTask = pingExec.scheduleWithFixedDelay(() -> {
			try {
				if (mIsStreaming)
				{
					write(new byte[]{PING_BYTE});
				} else {
					stopPingTask();
				}
			} catch (Throwable e) {
				// If device disconnects, stop pings
				stopPingTask();
			}
		}, 2, 2, java.util.concurrent.TimeUnit.SECONDS);
	}

	public void stopPingTask() {
		if (pingTask != null) pingTask.cancel(true);
		if (pingExec != null) {
			pingExec.shutdownNow();
			pingExec = null;
		}
	}


	@Override
	public void startStreaming() throws ShimmerException {
		super.startStreaming();
		if (getHardwareVersion()== ShimmerVerDetails.HW_ID.SHIMMER_3R
				&& isThisVerCompatibleWith(ShimmerVerDetails.FW_ID.LOGANDSTREAM, 1, 0, 46)) {
			startPingTask();
		}
	}

	@Override
	public void stopStreaming(){
		super.stopStreaming();
		if (getHardwareVersion()== ShimmerVerDetails.HW_ID.SHIMMER_3R
		&& isThisVerCompatibleWith(ShimmerVerDetails.FW_ID.LOGANDSTREAM, 1, 0, 46)) {
			stopPingTask();
		}
	}
}
