//v0.2 -  8 January 2013

/*
 * Copyright (c) 2010, Shimmer Research, Ltd.
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
 * @author Jong Chern Lim
 * @date   October, 2013
 */

//Future updates needed
//- the handler should be converted to static

package com.shimmerresearch.android.shimmerService;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.shimmerresearch.algorithms.Filter;
import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.android.manager.ShimmerBluetoothManagerAndroid;
import com.shimmerresearch.biophysicalprocessing.ECGtoHRAdaptive;
import com.shimmerresearch.biophysicalprocessing.PPGtoHRAlgorithm;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driverUtilities.BluetoothDeviceDetails;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.tools.Logging;
import com.shimmerresearch.tools.PlotManagerAndroid;
import com.shimmerresearch.verisense.VerisenseDevice;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static com.shimmerresearch.bluetooth.ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE;

public class ShimmerService extends Service {
	private static final String TAG = "ShimmerService";
    public Logging shimmerLog1 = null;
    private boolean mEnableLogging = false;
    private String mLogFolderName = "ShimmerCapture";
	private BluetoothAdapter mBluetoothAdapter = null;
	private final IBinder mBinder = new LocalBinder();
	public HashMap<String, Object> mMultiShimmer = new HashMap<String, Object>(7);
	public HashMap<String, Logging> mLogShimmer = new HashMap<String, Logging>(7);
	private List<Handler> mHandlerList = new ArrayList<Handler>();
	protected Handler mHandlerGraph=null;
	private boolean mGraphing=false;
	public String mLogFileName="Default";

	public Uri mFileURI= null;
	public ContentResolver mResolver = null;
	public Context mContext = null;
	Filter mFilter;
	Filter mLPFilterECG;
	Filter mHPFilterECG;
	private double[] mLPFcECG = {51.2};
	private double[] mHPFcECG = {0.5};
	PPGtoHRAlgorithm mPPGtoHR;
	ECGtoHRAdaptive mECGtoHR;
	private int mNumberOfBeatsToAvg = 2;
	private int mNumberOfBeatsToAvgECG = 2;
	private int mECGTrainingInterval = 10;
	double[] mLPFc = {5};
	private boolean mPPGtoHREnabled = false;
	private boolean mECGtoHREnabled = false;
	private boolean mConvertGSRtoSiemens = true;
	private String mPPGtoHRSignalName = Configuration.Shimmer3.ObjectClusterSensorName.INT_EXP_ADC_A13;
	private String mECGtoHRSignalName = Configuration.Shimmer3.ObjectClusterSensorName.ECG_LA_RA_24BIT;
	public PlotManagerAndroid mPlotManager;

	/**
	 * Null while Bluetooth was off when the service was created (onCreate logs "Bluetooth not
	 * Enabled"), until ensureBluetoothManager() creates it. With no BluetoothManager there are no
	 * devices, so every method that uses it first returns what the BluetoothManager returns for a
	 * device it does not know: commands do nothing, and queries return -1, 0, false, "", null,
	 * DISCONNECTED or an empty list, as that method would.
	 */
	protected ShimmerBluetoothManagerAndroid btManager;

	/**	For logging to file	*/
	public enum FILE_TYPE {
		DAT("dat", 0),
		CSV("csv", 1);

		private String fileName;
		private int fileTypeOrder;

		FILE_TYPE(String fileName, int fileType) {
			this.fileName = fileName;
			this.fileTypeOrder = fileType;
		}

		public String getName() {
			return fileName;
		}

		public int getFileTypeOrder() {
			return fileTypeOrder;
		}
	}

	public FILE_TYPE mLoggingFileType = FILE_TYPE.CSV;

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	public boolean isGSRtoSiemensEnabled(){
		return mConvertGSRtoSiemens;
	}

	@Override
	public void onCreate() {
		Toast.makeText(this, "Shimmer Service Created", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onCreate");

		try {
			btManager = new ShimmerBluetoothManagerAndroid(this, mHandler);
		} catch (Exception e) {
			Log.e(TAG, "ERROR! " + e);
			Toast.makeText(this, "Error! Could not create Bluetooth Manager!", Toast.LENGTH_LONG).show();
		}

		//btManager.configureShimmer();
	}

	public class LocalBinder extends Binder {
        public ShimmerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return ShimmerService.this;
        }
    }

	public void setPPGtoHRSignal(String ppgtoHR){
		mPPGtoHRSignalName = ppgtoHR;
	}

	public void setECGtoHRSignal(String ecgtoHR){
		mECGtoHRSignalName = ecgtoHR;
	}

	@Override
	public void onDestroy() {
		Toast.makeText(this, "Shimmer Service Stopped", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onDestroy");
		if (btManager != null) {
			btManager.disconnectAllDevices();
		}
	}

	/**
	 * btManager is null when Bluetooth was off as the service was created (onCreate logs "Bluetooth
	 * not Enabled"). Creates it if Bluetooth has been switched on since; an existing one is kept, so
	 * whatever holds it from getBluetoothManager() still matches the service. Call this rather than
	 * createNewBluetoothManager() once Bluetooth comes on.
	 * @return whether there is a BluetoothManager to use
	 */
	public boolean ensureBluetoothManager() {
		if (btManager == null) {
			createNewBluetoothManager();
		}
		return btManager != null;
	}

	public void disconnectAllDevices(){
		if (btManager != null) {
			btManager.disconnectAllDevices();
		}
		mMultiShimmer.clear();
		mLogShimmer.clear();
	}

	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("LocalService", "Received start id " + startId + ": " + intent);

        return START_NOT_STICKY;
	}

	@Override
	public void onStart(Intent intent, int startid) {
		Toast.makeText(this, "My Service Started", Toast.LENGTH_LONG).show();

		Log.d(TAG, "onStart");

	}

	public void enablePPGtoHR(String bluetoothAddress, boolean enable){
		if (enable){
			double sR = getSamplingRate(bluetoothAddress);
			mPPGtoHR = new PPGtoHRAlgorithm(sR, mNumberOfBeatsToAvg, true);
			try {
				mFilter = new Filter(Filter.LOW_PASS, sR,mLPFc);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		mPPGtoHREnabled = enable;
	}

	public void enableECGtoHR(String bluetoothAddress, boolean enable){
		if (enable){
			double sR = getSamplingRate(bluetoothAddress);
			mECGtoHR = new ECGtoHRAdaptive(sR);
	    	try {
				mLPFilterECG = new Filter(Filter.LOW_PASS, sR, mLPFcECG);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

	    	try {
				mHPFilterECG = new Filter(Filter.HIGH_PASS, sR, mHPFcECG);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		mECGtoHREnabled = enable;
	}

	public boolean isPPGtoHREnabled(){
		return mPPGtoHREnabled;
	}

	public boolean isECGtoHREnabled(){
		return mECGtoHREnabled;
	}

	public void connectShimmer(final String bluetoothAddress,Context context){
		if (!ensureBluetoothManager()) return;
		btManager.connectShimmerThroughBTAddress(bluetoothAddress,"",context);
	}

	public void connectShimmer(final String bluetoothAddress,final String deviceName, Context context){
		if (!ensureBluetoothManager()) return;
		btManager.connectShimmerThroughBTAddress(bluetoothAddress,deviceName,context);
	}

	public void connectShimmer(final String bluetoothAddress,final String deviceName, ShimmerBluetoothManagerAndroid.BT_TYPE preferredBtType, Context context){
		if (!ensureBluetoothManager()) return;
		boolean isVerisense = false;
		if (deviceName!=null){
			if (deviceName.contains(VerisenseDevice.VERISENSE_PREFIX)) {
				isVerisense = true;
			}
		}

		if (isVerisense){
			btManager.connectVerisenseDevice(new BluetoothDeviceDetails("",bluetoothAddress,deviceName));
		} else {
			btManager.connectShimmerThroughBTAddress(bluetoothAddress, deviceName, preferredBtType);   //Connect to the selected device
		}



	}

	public void connectShimmer(final String bluetoothAddress){
		if (!ensureBluetoothManager()) return;
		btManager.connectShimmerThroughBTAddress(bluetoothAddress);
	}

	public void onStop(){
		Toast.makeText(this, "My Service Stopped", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onDestroy");

		if (btManager != null) {
			btManager.disconnectAllDevices();
		}
	}

	/**
	 * We recommend using ShimmerBluetoothManagerAndroid when configuring the Shimmer LEDs.
	 * This method is kept for backwards compatibility with existing apps.
	 */
	@Deprecated
	public void toggleAllLEDS(){
		if (btManager == null) return;
		btManager.toggleAllLEDS();
	}

	/**
	 * We recommend using ShimmerBluetoothManagerAndroid when configuring the Shimmer LEDs.
	 * This method is kept for backwards compatibility with existing apps.
	 * @param bluetoothAddress
	 */
	@Deprecated
	public void toggleLED(String bluetoothAddress) {
		if (btManager == null) return;
		btManager.toggleLED(bluetoothAddress);
	}


	//TODO: Remove This when done testing
	public void clickToggle() {
		if (btManager == null) return;
		btManager.toggleAllLEDS();
	}


	  public final Handler mHandler = new Handler() {
	        public void handleMessage(Message msg) {
				for(Handler handler : mHandlerList) {
					//Rebroadcast the message received to the List of Handlers
					handler.obtainMessage(msg.what, msg.arg1, msg.arg2, msg.obj).sendToTarget();
				}
	            switch (msg.what) { // handlers have a what identifier which is used to identify the type of msg
	            case ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET:
					/*	---------- Handle the data packet message ----------	*/
					handleMsgDataPacket(msg);
	                break;
	                 case Shimmer.MESSAGE_TOAST:
	                	Log.d("toast",msg.getData().getString(Shimmer.TOAST));
	                	Toast.makeText(getApplicationContext(), msg.getData().getString(Shimmer.TOAST),
	                            Toast.LENGTH_SHORT).show();
	                	if (msg.getData().getString(Shimmer.TOAST).equals("Device connection was lost")){

	                	}
	                break;
	                 case ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE:
	                 	/*	---------- Handle the state change message ----------	*/
	                 	handleMsgStateChange(msg);
	                 	break;
                 case Shimmer.MESSAGE_STOP_STREAMING_COMPLETE:
                	 String address =  msg.getData().getString("Bluetooth Address");
                	 boolean stop  =  msg.getData().getBoolean("Stop Streaming");
                	 if (stop==true ){
                		 closeAndRemoveFile(address);
                	 }
                	break;
                 case Shimmer.MESSAGE_LOG_AND_STREAM_STATUS_CHANGED:
                 	mHandlerGraph.obtainMessage(Shimmer.MESSAGE_LOG_AND_STREAM_STATUS_CHANGED, msg.arg1, msg.arg2).sendToTarget();
                 	break;
				case MSG_IDENTIFIER_NOTIFICATION_MESSAGE:
					handleNotificationMsg(msg);
					break;

				}
	        }
	    };


	/**
	 * Handles the data packet message received by the Handler
	 * Override this method to change how the message is handled
	 * @param msg
	 */
	public void handleMsgDataPacket(Message msg) {
		if ((msg.obj instanceof ObjectCluster)){	// within each msg an object can be include, objectclusters are used to represent the data structure of the shimmer device
			ObjectCluster objectCluster =  (ObjectCluster) msg.obj;

			//Filter Signal
			//PPG to HR
			if (mPPGtoHREnabled){
				Collection<FormatCluster> dataFormats = objectCluster.getCollectionOfFormatClusters(mPPGtoHRSignalName);  // first retrieve all the possible formats for the current sensor device
				FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(dataFormats,CHANNEL_TYPE.CAL.toString())); // retrieve the calibrated data
				if (formatCluster!=null){
					double ppgdata = formatCluster.mData;

					try {
						ppgdata = mFilter.filterData(ppgdata);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					dataFormats = objectCluster.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP);  // first retrieve all the possible formats for the current sensor device
					formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(dataFormats,CHANNEL_TYPE.CAL.toString())); // retrieve the calibrated data

					double calts = formatCluster.mData;

					double hr = mPPGtoHR.ppgToHrConversion(ppgdata, calts);
					System.out.print("Heart Rate: " + Integer.toString((int)hr) + "\n");

					objectCluster.addData(Configuration.Shimmer3.ObjectClusterSensorName.PPG_TO_HR,CHANNEL_TYPE.CAL,Configuration.CHANNEL_UNITS.BEATS_PER_MINUTE,hr);
				}
			}

			//Filter Signal
			//ECG to HR
			if (mECGtoHREnabled){
				Collection<FormatCluster> dataFormats = objectCluster.getCollectionOfFormatClusters(mECGtoHRSignalName);  // first retrieve all the possible formats for the current sensor device
				FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(dataFormats,CHANNEL_TYPE.CAL.toString())); // retrieve the calibrated data
				if (formatCluster!=null){
					double ecgdata = formatCluster.mData;

					try {
						ecgdata = mLPFilterECG.filterData(ecgdata);
						ecgdata = mHPFilterECG.filterData(ecgdata);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					dataFormats = objectCluster.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP);  // first retrieve all the possible formats for the current sensor device
					formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(dataFormats,CHANNEL_TYPE.CAL.toString())); // retrieve the calibrated data

					double calts = formatCluster.mData;

					double hr = mECGtoHR.ecgToHrConversion(ecgdata, calts);
					System.out.print("Heart Rate: " + Integer.toString((int)hr) + "\n");
					objectCluster.addData(Configuration.Shimmer3.GuiLabelSensors.ECG_TO_HR, CHANNEL_TYPE.CAL, Configuration.CHANNEL_UNITS.BEATS_PER_MINUTE, hr);
				}
			}

			//PPG to HR

			if (mConvertGSRtoSiemens){
				Collection<FormatCluster> dataFormatsGSR = objectCluster.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.GSR_RESISTANCE);  // first retrieve all the possible formats for the current sensor device
				FormatCluster formatClusterGSR = ((FormatCluster)ObjectCluster.returnFormatCluster(dataFormatsGSR,CHANNEL_TYPE.CAL.toString())); // retrieve the calibrated data
				if (formatClusterGSR!=null){
					double gsrdata = formatClusterGSR.mData * 1000; //in ohms
					double conductance = 1/gsrdata;
					conductance = conductance * 1000000; //convert to microSiemens
					//objectCluster.mPropertyCluster.remove(Configuration.Shimmer3.ObjectClusterSensorName.GSR, formatClusterGSR);
					objectCluster.addData(Configuration.Shimmer3.ObjectClusterSensorName.GSR_CONDUCTANCE,CHANNEL_TYPE.CAL,"microSiemens",conductance);
				}
			}

			//Plot the data in the ObjectCluster
			try {
				mPlotManager.filterDataAndPlot((ObjectCluster) msg.obj);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}


			objectCluster.removeAll(Configuration.Shimmer3.ObjectClusterSensorName.BATT_PERCENTAGE);
			//objectCluster.removeAll(Configuration.Shimmer3.ObjectClusterSensorName.SYSTEM_TIMESTAMP_PLOT);
			objectCluster.removeAll(Configuration.Shimmer3.ObjectClusterSensorName.PACKET_RECEPTION_RATE_OVERALL);
			objectCluster.removeAll(Configuration.Shimmer3.ObjectClusterSensorName.PACKET_RECEPTION_RATE_CURRENT);
			objectCluster.removeAll(Configuration.Shimmer3.ObjectClusterSensorName.SYSTEM_TIMESTAMP);

			if (mEnableLogging==true){
				shimmerLog1= mLogShimmer.get(objectCluster.getMacAddress());
				if (shimmerLog1!=null){
					shimmerLog1.logData(objectCluster);
				} else {
					char[] bA=objectCluster.getMacAddress().toCharArray();
					Logging shimmerLog;
					if (mLogFileName.equals("Default")){
						if(mFileURI==null) {
							shimmerLog = new Logging(fromMilisecToDate(System.currentTimeMillis()) + " Device" + bA[12] + bA[13] + bA[15] + bA[16], ",", mLogFolderName, mLoggingFileType);
						} else {
							shimmerLog = new Logging(mFileURI,mContext,fromMilisecToDate(System.currentTimeMillis()) + " Device" + bA[12] + bA[13] + bA[15] + bA[16], ",", mLogFolderName, mLoggingFileType);
						}
					} else {
						if(mFileURI==null) {
							shimmerLog = new Logging(fromMilisecToDate(System.currentTimeMillis()) + mLogFileName, ",", mLogFolderName, mLoggingFileType);
						}else {
							shimmerLog = new Logging(mFileURI,mContext,fromMilisecToDate(System.currentTimeMillis()) + mLogFileName, ",", mLogFolderName, mLoggingFileType);
						}
					}
					mLogShimmer.remove(objectCluster.getMacAddress());
					if (mLogShimmer.get(objectCluster.getMacAddress())==null){
						mLogShimmer.put(objectCluster.getMacAddress(),shimmerLog);
					}
				}
			}

			if (mGraphing==true){
				// Log.d("ShimmerGraph","Sending");
				mHandlerGraph.obtainMessage(ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET, objectCluster)
						.sendToTarget();
			}
		}
	}


	/**
	 * Handles the state change message received by the Handler
	 * Override this method to change how the message is handled
	 * @param msg
	 */
	public void handleMsgStateChange(Message msg) {

		Intent intent = new Intent("com.shimmerresearch.service.ShimmerService");
		Log.d("ShimmerGraph","Sending");
		if(mHandlerGraph != null) {
			mHandlerGraph.obtainMessage(msg.what, msg.arg1, -1, msg.obj).sendToTarget();
		}
		if(msg.arg1==Shimmer.MSG_STATE_STOP_STREAMING){ //deprecated shimmer
			closeAndRemoveFile(((ObjectCluster)msg.obj).getMacAddress());
		} else {
			BT_STATE state=null;
			String macAddress = "";
			String shimmerName = "";
			if (msg.obj instanceof ObjectCluster){
				state = ((ObjectCluster)msg.obj).mState;
				macAddress = ((ObjectCluster)msg.obj).getMacAddress();
				shimmerName = ((ObjectCluster) msg.obj).getShimmerName();
			} else if(msg.obj instanceof CallbackObject){
				state = ((CallbackObject)msg.obj).mState;
				macAddress = ((CallbackObject)msg.obj).mBluetoothAddress;
				shimmerName = "";
			}
			switch (state) {
				case CONNECTED:
					ShimmerDevice shimmerDevice = btManager == null ? null : btManager.getShimmerDeviceBtConnectedFromMac(macAddress);
					mMultiShimmer.remove(macAddress);
					if (mMultiShimmer.get(macAddress)==null) { mMultiShimmer.put(macAddress,shimmerDevice); }
					intent.putExtra("ShimmerBluetoothAddress", macAddress);
					intent.putExtra("ShimmerDeviceName", shimmerName);
					intent.putExtra("ShimmerState",BT_STATE.CONNECTED);
					sendBroadcast(intent);
					break;
				case CONNECTING:
					intent.putExtra("ShimmerBluetoothAddress", macAddress);
					intent.putExtra("ShimmerDeviceName", shimmerName);
					intent.putExtra("ShimmerState",BT_STATE.CONNECTING);
					sendBroadcast(intent);
					break;
				case STREAMING:
					intent.putExtra("ShimmerBluetoothAddress", macAddress);
					intent.putExtra("ShimmerDeviceName", shimmerName);
					intent.putExtra("ShimmerState", BT_STATE.STREAMING);
					sendBroadcast(intent);
					break;
				case STREAMING_AND_SDLOGGING:
					intent.putExtra("ShimmerBluetoothAddress", macAddress);
					intent.putExtra("ShimmerDeviceName", shimmerName);
					intent.putExtra("ShimmerState", BT_STATE.STREAMING_AND_SDLOGGING);
					sendBroadcast(intent);
					break;
				case SDLOGGING:
					shimmerDevice = btManager == null ? null : btManager.getShimmerDeviceBtConnectedFromMac(macAddress);
					mMultiShimmer.remove(macAddress);
					if (mMultiShimmer.get(macAddress)==null) { mMultiShimmer.put(macAddress,shimmerDevice); }
					intent.putExtra("ShimmerBluetoothAddress", macAddress );
					intent.putExtra("ShimmerDeviceName", shimmerName );
					intent.putExtra("ShimmerState",BT_STATE.SDLOGGING);
					sendBroadcast(intent);
					break;
				case DISCONNECTED:
					if (btManager != null) {
						btManager.removeShimmerDeviceBtConnected(macAddress);
					}
					intent.putExtra("ShimmerBluetoothAddress", macAddress );
					intent.putExtra("ShimmerDeviceName", shimmerName );
					intent.putExtra("ShimmerState",BT_STATE.DISCONNECTED);
					sendBroadcast(intent);
					break;
			}

		}
	}

	/**
	 * Handles the notification message received by the Handler
	 * Note: Shimmer FULLY_INITIALIZED messages are received here
	 * Override this method to change how the message is handled
	 * @param msg
	 */
	public void handleNotificationMsg(Message msg) {
		//Handle message here
	}

    public void stopStreamingAllDevices() {
		if (btManager == null) return;
		btManager.stopStreamingAllDevices();
	}

	public void startStreamingAllDevices() {
		if (btManager == null) return;
		btManager.startStreamingAllDevices();
	}

    /**
     * Enable or disable logging to file
     * @param enableLogging
     */
	public void setEnableLogging(boolean enableLogging){
		mEnableLogging=enableLogging;
		Log.d("Shimmer","Logging :" + Boolean.toString(mEnableLogging));
	}

    /**
     * Enable or disable logging to file with selection of file output type
     * @param enableLogging
     * @param fileType File output type
     */
	public void setEnableLogging(boolean enableLogging, FILE_TYPE fileType) {
		setEnableLogging(enableLogging);
		mLoggingFileType = fileType;
	}

	public void setEnableLogging(boolean enableLogging, FILE_TYPE fileType, String folderName) {
		setEnableLogging(enableLogging, fileType);
		mLogFolderName = folderName;
	}

	public void setLogFolderName(String folderName) {
		mLogFolderName = folderName;
	}

	public String getLogFolderName() {
		return mLogFolderName;
	}

	public void setLoggingFileType(FILE_TYPE fileType) {
		mLoggingFileType = fileType;
	}

	public boolean getEnableLogging(){
		return mEnableLogging;
	}

	public void setAllSampingRate(double samplingRate) {

		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if ((stemp.getBluetoothRadioState()==BT_STATE.CONNECTED || stemp.getBluetoothRadioState()==BT_STATE.SDLOGGING)){
				stemp.writeShimmerAndSensorsSamplingRate(samplingRate);
				if (mPPGtoHREnabled){
					mPPGtoHR = new PPGtoHRAlgorithm(samplingRate, mNumberOfBeatsToAvg, true);
					try {
						mFilter = new Filter(Filter.LOW_PASS, samplingRate,mLPFc);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				if (mECGtoHREnabled){
					mECGtoHR = new ECGtoHRAdaptive(samplingRate);
					try {
						mLPFilterECG = new Filter(Filter.LOW_PASS, samplingRate, mLPFcECG);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					try {
						mHPFilterECG = new Filter(Filter.HIGH_PASS, samplingRate, mHPFcECG);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			}
		}
	}

	/**
	 * This method is kept for backwards compatibility with existing apps.
	 * @param accelRange
	 */
	@Deprecated
	public void setAllAccelRange(int accelRange) {
		if (btManager == null) return;
		//TODO: Test this.
		btManager.setAllAccelRange(accelRange);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps.
	 * @param gsrRange
	 */
	@Deprecated
	public void setAllGSRRange(int gsrRange) {
		if (btManager == null) return;
		// TODO: Test this.
		btManager.setAllGSRRange(gsrRange);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps.
	 * @param enabledSensors
	 */
	@Deprecated
	public void setAllEnabledSensors(int enabledSensors) {
		if (btManager == null) return;
		//TODO: Test this.
		btManager.setAllEnabledSensors(enabledSensors);
	}

	public void setEnabledSensors(long enabledSensors,String bluetoothAddress) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if ((stemp.getBluetoothRadioState()==BT_STATE.CONNECTED || stemp.getBluetoothRadioState()==BT_STATE.SDLOGGING) && stemp.getBluetoothAddress().equals(bluetoothAddress)){

				if (((enabledSensors & Shimmer.SENSOR_EXG1_16BIT)>0 && (enabledSensors & Shimmer.SENSOR_EXG2_16BIT)>0)||((enabledSensors & Shimmer.SENSOR_EXG1_24BIT)>0 && (enabledSensors & Shimmer.SENSOR_EXG2_24BIT)>0)){

				} else {
					mECGtoHREnabled = false;
				}

				if (stemp.getInternalExpPower()==1 && (((enabledSensors & Shimmer.SENSOR_INT_ADC_A1)>0)||((enabledSensors & Shimmer.SENSOR_INT_ADC_A12)>0)|((enabledSensors & Shimmer.SENSOR_INT_ADC_A13)>0)||((enabledSensors & Shimmer.SENSOR_INT_ADC_A14)>0))){

				} else {
					mPPGtoHREnabled = false;
				}
				stemp.writeEnabledSensors(enabledSensors);
			}
		}
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @param setBit
	 */
	@Deprecated
	public void writePMux(String bluetoothAddress,int setBit) {
		if (btManager == null) return;
		btManager.writePMux(bluetoothAddress, setBit);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @param setBit
	 */
	@Deprecated
	public void write5VReg(String bluetoothAddress,int setBit) {
		if (btManager == null) return;
		btManager.write5VReg(bluetoothAddress, setBit);
	}


	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public List<String[]> getListofEnabledSensorSignals(String bluetoothAddress) {
		if (btManager == null) return new ArrayList<String[]>();
		List<String[]> listofSensors = new ArrayList<String[]>();
		listofSensors = btManager.getListofEnabledSensorSignals(bluetoothAddress);
		return listofSensors;
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public long getEnabledSensors(String bluetoothAddress) {
		if (btManager == null) return 0;
		long enabledSensors = btManager.getEnabledSensors(bluetoothAddress);
		return enabledSensors;
	}

	public void writeSamplingRate(String bluetoothAddress,double samplingRate) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if ((stemp.getBluetoothRadioState()==BT_STATE.CONNECTED || stemp.getBluetoothRadioState()==BT_STATE.SDLOGGING) && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				//Jos removed Oct 2017 -> reverted Nov 2017
				stemp.writeShimmerAndSensorsSamplingRate(samplingRate);
//				stemp.setShimmerAndSensorsSamplingRate(samplingRate);
				if (mPPGtoHREnabled){
					mPPGtoHR = new PPGtoHRAlgorithm(samplingRate, mNumberOfBeatsToAvg, true);
					try {
						mFilter = new Filter(Filter.LOW_PASS, samplingRate,mLPFc);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				if (mECGtoHREnabled){
					mECGtoHR = new ECGtoHRAdaptive(samplingRate);
					try {
						mLPFilterECG = new Filter(Filter.LOW_PASS, samplingRate, mLPFcECG);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					try {
						mHPFilterECG = new Filter(Filter.HIGH_PASS, samplingRate, mHPFcECG);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @param accelRange
	 */
	@Deprecated
	public void writeAccelRange(String bluetoothAddress,int accelRange) {
		if (btManager == null) return;
		btManager.writeAccelRange(bluetoothAddress, accelRange);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @param range
	 */
	@Deprecated
	public void writeGyroRange(String bluetoothAddress,int range) {
		if (btManager == null) return;
		btManager.writeGyroRange(bluetoothAddress, range);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @param resolution
	 */
	@Deprecated
	public void writePressureResolution(String bluetoothAddress,int resolution) {
		if (btManager == null) return;
		btManager.writePressureResolution(bluetoothAddress, resolution);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @param range
	 */
	@Deprecated
	public void writeMagRange(String bluetoothAddress,int range) {
		if (btManager == null) return;
		btManager.writeMagRange(bluetoothAddress, range);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @param gsrRange
	 */
	@Deprecated
	public void writeGSRRange(String bluetoothAddress,int gsrRange) {
		if (btManager == null) return;
		btManager.writeGSRRange(bluetoothAddress, gsrRange);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public double getSamplingRate(String bluetoothAddress) {
		if (btManager == null) return -1;
		double SRate = -1;
		SRate = btManager.getSamplingRate(bluetoothAddress);
		return SRate;
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public int getAccelRange(String bluetoothAddress) {
		if (btManager == null) return -1;
		return btManager.getAccelRange(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public BT_STATE getShimmerState(String bluetoothAddress){
		if (btManager == null) return BT_STATE.DISCONNECTED;
		BT_STATE status=BT_STATE.DISCONNECTED;
		status = btManager.getShimmerState(bluetoothAddress);
		return status;
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public int getGSRRange(String bluetoothAddress) {
		if (btManager == null) return -1;
		return btManager.getGSRRange(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public int get5VReg(String bluetoothAddress) {
		if (btManager == null) return -1;
		int fiveVReg=-1;
		fiveVReg = btManager.get5VReg(bluetoothAddress);
		return fiveVReg;
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public boolean isLowPowerMagEnabled(String bluetoothAddress) {
		if (btManager == null) return false;
		boolean enabled=false;
		enabled = btManager.isLowPowerMagEnabled(bluetoothAddress);
		return enabled;
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public int getpmux(String bluetoothAddress) {
		if (btManager == null) return -1;
		int pmux=-1;
		pmux = btManager.getpmux(bluetoothAddress);
		return pmux;
	}

/*	TODO: Remove this?
	public void startStreaming(String bluetoothAddress) {
		// TODO Auto-generated method stub
				Collection<Object> colS=mMultiShimmer.values();
				Iterator<Object> iterator = colS.iterator();
				while (iterator.hasNext()) {
					ShimmerDevice stemp=(ShimmerDevice) iterator.next();
					String address = stemp.getMacId();
					address = address.replace(":","");
					bluetoothAddress = bluetoothAddress.replace(":","");
					if ((stemp.getBluetoothRadioState()==BT_STATE.CONNECTED || stemp.getBluetoothRadioState()==BT_STATE.SDLOGGING) && address.equals(bluetoothAddress)){
						if (mPPGtoHREnabled){
							enablePPGtoHR(stemp.getMacId(),true);
						}
						if (mECGtoHREnabled){
							enableECGtoHR(stemp.getMacId(),true);
						}
						if (stemp instanceof Shimmer) {
							((Shimmer)stemp).startStreaming();
						} else if (stemp instanceof Shimmer4Android){
//							((Shimmer4Android)stemp).mShimmerRadioHWLiteProtocol.startStreaming();
						}
					}
				}
	}
*/

	public void startStreaming(String bluetoothAddress) throws Exception{
		if (btManager == null) return;
			btManager.startStreaming(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 */
	@Deprecated
	public void startLogging(String bluetoothAddress) {
		if (btManager == null) return;
		btManager.startLogging(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 */
	@Deprecated
	public void stopLogging(String bluetoothAddress) {
		if (btManager == null) return;
		btManager.stopLogging(bluetoothAddress);
	}

	public void startLogAndStreaming(String bluetoothAddress) {
		if (btManager == null) return;
		btManager.startLoggingAndStreaming(bluetoothAddress);
	}

	public void stopLogAndStreaming(String bluetoothAddress) {
		if (btManager == null) return;
		btManager.stopLoggingAndStreaming(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @param enabledSensors
	 * @param sensorToCheck
	 * @return
	 */
	@Deprecated
	public long sensorConflictCheckandCorrection(String bluetoothAddress, long enabledSensors, int sensorToCheck) {
		if (btManager == null) return 0;
		long newSensorBitmap = 0;
		newSensorBitmap = btManager.sensorConflictCheckandCorrection(bluetoothAddress, enabledSensors, sensorToCheck);
		return newSensorBitmap;
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public List<String> getListofEnabledSensors(String bluetoothAddress) {
		if (btManager == null) return null;
		List<String> listofSensors = null;
		listofSensors = btManager.getListofEnabledSensors(bluetoothAddress);
		return listofSensors;
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @param address
	 * @return
	 */
	@Deprecated
	public boolean bluetoothAddressComparator(String bluetoothAddress, String address){
		// A comparison of two addresses with the colons removed, which needs no device.
		if (btManager == null) return bluetoothAddress.replace(":","").equals(address.replace(":",""));
		return btManager.bluetoothAddressComparator(bluetoothAddress, address);
	}

/*	TODO: Remove this?
	public void stopStreaming(String bluetoothAddress) {
		// TODO Auto-generated method stub
				Collection<Object> colS=mMultiShimmer.values();
				Iterator<Object> iterator = colS.iterator();
				while (iterator.hasNext()) {
					ShimmerDevice stemp=(ShimmerDevice) iterator.next();
					String address = stemp.getMacId();
					address = address.replace(":","");
					bluetoothAddress = bluetoothAddress.replace(":","");
					if ((stemp.getBluetoothRadioState()==BT_STATE.STREAMING || stemp.getBluetoothRadioState()==BT_STATE.STREAMING_AND_SDLOGGING) && address.equals(bluetoothAddress)){
						if (stemp instanceof Shimmer){
							((Shimmer)stemp).stopStreaming();
						} else if (stemp instanceof Shimmer4Android){
//							((Shimmer4Android)stemp).mShimmerRadioHWLiteProtocol.stopStreaming();
						}
						if (mPlotManager!=null){
							mPlotManager.removeAllSignals();
						}

					}
				}
	}
*/

	public void stopStreaming(String bluetoothAddress) throws Exception{
		if (btManager == null) return;
			btManager.stopStreaming(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 */
	@Deprecated
	public void setBlinkLEDCMD(String bluetoothAddress) {
		if (btManager == null) return;
		btManager.setBlinkLEDCMD(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @param enable
	 */
	@Deprecated
	public void enableLowPowerMag(String bluetoothAddress,boolean enable) {
		if (btManager == null) return;
		btManager.enableLowPowerMag(bluetoothAddress, enable);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @param limit
	 */
	@Deprecated
	public void setBattLimitWarning(String bluetoothAddress, double limit) {
		if (btManager == null) return;
		btManager.setBattLimitWarning(bluetoothAddress, limit);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public double getBattLimitWarning(String bluetoothAddress) {
		if (btManager == null) return -1;
		return btManager.getBattLimitWarning(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public double getPacketReceptionRate(String bluetoothAddress) {
		if (btManager == null) return -1;
		double rate = btManager.getPacketReceptionRate(bluetoothAddress);
		return rate;
	}


	public void disconnectShimmer(String bluetoothAddress){
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if ((stemp.getBluetoothRadioState()==BT_STATE.CONNECTED || stemp.getBluetoothRadioState()==BT_STATE.SDLOGGING) && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				stemp.stop();

			}
		}
		mLogShimmer.remove(bluetoothAddress);
		mMultiShimmer.remove(bluetoothAddress);
	}

	public void setGraphHandler(Handler handler){
		mHandlerGraph=handler;
	}

	public Handler getGraphHandler() {
		return mHandlerGraph;
	}

	public void addHandlerToList(Handler handler) {
		mHandlerList.add(handler);
	}

	public void enableGraphingHandler(boolean setting){
		mGraphing=setting;
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public boolean DevicesConnected(String bluetoothAddress){
		if (btManager == null) return false;
		return btManager.DevicesConnected(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public boolean DeviceIsLogging(String bluetoothAddress){
		if (btManager == null) return false;
		return btManager.DeviceIsLogging(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public boolean DeviceIsStreaming(String bluetoothAddress){
		if (btManager == null) return false;
		return btManager.DeviceIsStreaming(bluetoothAddress);
	}

	public void setLoggingName(String name){
		mLogFileName=name;
	}

	public void closeAndRemoveFile(String bluetoothAddress){
		if (mLogShimmer.get(bluetoothAddress)!=null){
			mLogShimmer.get(bluetoothAddress).closeFile();

			try {
				MediaScannerConnection.scanFile(this, new String[] { mLogShimmer.get(bluetoothAddress).getAbsoluteName() }, null, null);
			} catch (Exception e) {
				System.out.println(e);
			}
			mLogShimmer.remove(bluetoothAddress);

		}
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public String getFWVersion (String bluetoothAddress){
		if (btManager == null) return "";
		return btManager.getFWVersion(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public int getShimmerVersion (String bluetoothAddress){
		if (btManager == null) return 0;
		return btManager.getShimmerVersion(bluetoothAddress);
	}

	public ShimmerDevice getShimmer(String bluetoothAddress){
		return btManager == null ? null : btManager.getShimmer(bluetoothAddress);
	}

	public void test(){
		Log.d("ShimmerTest","Test");
	}


	public boolean isEXGUsingTestSignal24Configuration(String bluetoothAddress){

		Shimmer tmp = (Shimmer) mMultiShimmer.get(bluetoothAddress);
		return tmp.isEXGUsingTestSignal24Configuration();
	}

	public boolean isEXGUsingTestSignal16Configuration(String bluetoothAddress){

		Shimmer tmp = (Shimmer) mMultiShimmer.get(bluetoothAddress);
		return tmp.isEXGUsingTestSignal16Configuration();
	}

	public boolean isEXGUsingECG24Configuration(String bluetoothAddress){

		Shimmer tmp = (Shimmer) mMultiShimmer.get(bluetoothAddress);
		return tmp.isEXGUsingECG24Configuration();
	}

	public boolean isEXGUsingECG16Configuration(String bluetoothAddress){

		Shimmer tmp = (Shimmer) mMultiShimmer.get(bluetoothAddress);
		return tmp.isEXGUsingECG16Configuration();
	}

	public boolean isEXGUsingEMG24Configuration(String bluetoothAddress){

		Shimmer tmp = (Shimmer) mMultiShimmer.get(bluetoothAddress);
		return tmp.isEXGUsingEMG24Configuration();
	}

	public boolean isEXGUsingEMG16Configuration(String bluetoothAddress){

		Shimmer tmp = (Shimmer) mMultiShimmer.get(bluetoothAddress);
		return tmp.isEXGUsingEMG16Configuration();
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @param setting
	 */
	@Deprecated
	public void writeEXGSetting(String bluetoothAddress,int setting) {
		if (btManager == null) return;
		btManager.writeEXGSetting(bluetoothAddress, setting);
	}

	//convert the system time in miliseconds to a "readable" date format with the next format: YYYY MM DD HH MM SS
	private String fromMilisecToDate(long miliseconds){

		String date="";
		Date dateToParse = new Date(miliseconds);
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		date = dateFormat.format(dateToParse);

		return date;
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public boolean isUsingLogAndStreamFW(String bluetoothAddress){
		if (btManager == null) return false;
		return btManager.isUsingLogAndStreamFW(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 */
	@Deprecated
	public void readStatusLogAndStream(String bluetoothAddress){
		if (btManager == null) return;
		btManager.readStatusLogAndStream(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public boolean isSensing(String bluetoothAddress){
		if (btManager == null) return false;
		return btManager.isSensing(bluetoothAddress);
	}

	/**
	 * This method is kept for backwards compatibility with existing apps
	 * @param bluetoothAddress
	 * @return
	 */
	@Deprecated
	public boolean isDocked(String bluetoothAddress){
		if (btManager == null) return false;
		return btManager.isDocked(bluetoothAddress);
	}

	/**
	 * @return the BluetoothManager, or null if Bluetooth was off when the service was created and it
	 * has not been switched on since (see ensureBluetoothManager()). Callers must check for null.
	 */
	public ShimmerBluetoothManagerAndroid getBluetoothManager() {
		return btManager;
	}

	public List<ShimmerDevice> getListOfConnectedDevices() {
		// No BluetoothManager (Bluetooth was off when the service started) means no devices.
		return btManager == null ? new ArrayList<ShimmerDevice>() : btManager.getListOfConnectedDevices();
	}

	public void configureShimmer(ShimmerDevice device) {
		if (btManager == null) return;
		btManager.configureShimmer(device);
	}

	public void configureShimmers(List<ShimmerDevice> listOfShimmers) {
		if (btManager == null) return;
		btManager.configureShimmers(listOfShimmers);
	}

	public Handler getHandler() {	return mHandler;	}

	/**
	 * Replaces the current BluetoothManager with a new BluetoothManager. Call this if Bluetooth is enabled only after ShimmerService onCreate() is called.
	 */
	public void createNewBluetoothManager() {

		try {
			btManager = new ShimmerBluetoothManagerAndroid(this, mHandler);
			Log.d(TAG, "Bluetooth Manager created");
		} catch (Exception e) {
			Log.e(TAG, "ERROR! " + e);
			Toast.makeText(this, "Error! Could not create Bluetooth Manager!", Toast.LENGTH_LONG).show();
		}

	}

	public PlotManagerAndroid getPlotManager() {
		return mPlotManager;
	}


}
