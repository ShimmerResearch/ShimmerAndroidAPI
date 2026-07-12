package com.shimmerresearch.androidradiodriver;

import static com.shimmerresearch.android.Shimmer.MESSAGE_TOAST;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleMtuChangedCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.bluetooth.BluetoothProgressReportPerCmd;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerDeviceCallbackAdapter;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driver.ThreadSafeByteFifoBuffer;
import com.shimmerresearch.driver.shimmer2r3.ConfigByteLayoutShimmer3;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.sensors.kionix.SensorKionixAccel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import bolts.TaskCompletionSource;

public class Shimmer3BLEAndroid extends ShimmerBluetooth implements Serializable {
    transient BleDevice mBleDevice;
    final String TxID_Shimmer3 = "49535343-8841-43f4-a8d4-ecbe34729bb3";
    final String RxID_Shimmer3 = "49535343-1e4d-4bd9-ba61-23c647249616";
    final String ServiceID_Shimmer3 = "49535343-fe7d-4ae5-8fa9-9fafd205e455";
    final String TxID_Shimmer3R = "65333333-A115-11E2-9E9A-0800200CA102";
    final String RxID_Shimmer3R = "65333333-A115-11E2-9E9A-0800200CA101";
    final String ServiceID_Shimmer3R = "65333333-A115-11E2-9E9A-0800200CA100";
    String TxID = "";
    String RxID = "";
    String ServiceID = "";
    UUID sid = null;
    UUID txid = null;
    UUID rxid = null;
    String uuid;
    transient ThreadSafeByteFifoBuffer mBuffer;
    protected transient ShimmerDeviceCallbackAdapter mDeviceCallbackAdapter = new ShimmerDeviceCallbackAdapter(this);
    transient public final Handler mHandler;
    public static final String TOAST = "toast";

    //"DA:A6:19:F0:4A:D7"
    //"E7:45:2C:6D:6F:14"

    /** Only support Shimmer3 and Shimmer3R
     *
     * @param hardwareID e.g. ShimmerVerDetails.HW_ID.SHIMMER_3R or ShimmerVerDetails.HW_ID.SHIMMER_3
     * @param mac
     * @param handler
     */
    public Shimmer3BLEAndroid(int hardwareID, String mac, Handler handler){
        if (hardwareID== ShimmerVerDetails.HW_ID.SHIMMER_3R){
            sid = UUID.fromString(ServiceID_Shimmer3R);
            txid = UUID.fromString(TxID_Shimmer3R);
            rxid = UUID.fromString(RxID_Shimmer3R);
        } else if (hardwareID== ShimmerVerDetails.HW_ID.SHIMMER_3) {
            sid = UUID.fromString(ServiceID_Shimmer3);
            txid = UUID.fromString(TxID_Shimmer3);
            rxid = UUID.fromString(RxID_Shimmer3);
        }
        mMyBluetoothAddress = mac;
        mHandler = handler;
    }
    transient TaskCompletionSource<String> mTaskConnect = new TaskCompletionSource<>();
    transient TaskCompletionSource<String> mTaskMTU = new TaskCompletionSource<>();

    @Override
    public void sendCallBackMsg(int msgid,Object obj){
        if (mHandler != null) {
            mHandler.obtainMessage(msgid, obj).sendToTarget();
        }
    }
    /**
     * Connect to the Shimmer3 BLE device
     */
    @Override
    public void connect(String s, String s1) {
        mTaskConnect = new TaskCompletionSource<>();
        BleManager.getInstance().connect(mMyBluetoothAddress, new BleGattCallback() {
            @Override
            public void onStartConnect() {
                System.out.println("Connecting");
            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                mTaskConnect.setResult("Connection Failed");
                sendCallBackMsg(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, new ObjectCluster("", mMyBluetoothAddress, BT_STATE.CONNECTION_FAILED));
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                mBuffer = new ThreadSafeByteFifoBuffer(1000000);
                mTaskMTU = new TaskCompletionSource<>();
                BleManager.getInstance().setMtu(bleDevice, 251, new BleMtuChangedCallback() {
                    @Override
                    public void onSetMTUFailure(BleException exception) {
                        System.out.println("MTU Failure");
                        mTaskMTU.setResult("MTU Failure");
                    }

                    @Override
                    public void onMtuChanged(int mtu) {
                        System.out.println("MTU Changed: " + mtu);
                        mTaskMTU.setResult("MTU Changed: " + mtu);
                    }
                });

                try {
                    boolean result = mTaskMTU.getTask().waitForCompletion(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                }

                mBleDevice = bleDevice;
                startServiceS(bleDevice);
                System.out.println(bleDevice.getMac() + " Connected");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    gatt.setPreferredPhy(BluetoothDevice.PHY_LE_2M_MASK, BluetoothDevice.PHY_LE_2M_MASK, BluetoothDevice.PHY_OPTION_NO_PREFERRED);
                }
                mTaskConnect.setResult("Connected");
                //mHandler.obtainMessage(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, -1, -1,
                        //new ObjectCluster("", bleDevice.getMac(), BT_STATE.CONNECTED)).sendToTarget();
                sendCallBackMsg(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, new ObjectCluster("", bleDevice.getMac(), BT_STATE.CONNECTED));
                Bundle bundle = new Bundle();
                bundle.putString(TOAST, "Device connection established");
                sendMsgToHandlerList(MESSAGE_TOAST, bundle);

                mIOThread = new IOThread();
                mIOThread.start();
                if (mUseProcessingThread){
                    mPThread = new ProcessingThread();
                    mPThread.start();
                }

                initialize();
            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice bleDevice, BluetoothGatt gatt, int status) {
                closeConnection();
                sendCallBackMsg(ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE, new ObjectCluster("", bleDevice.getMac(), BT_STATE.DISCONNECTED));

                Bundle bundle = new Bundle();
                bundle.putString(TOAST, "Device connection was lost");
                sendMsgToHandlerList(MESSAGE_TOAST, bundle);
            }
        });


        try {
            boolean result = mTaskConnect.getTask().waitForCompletion(10, TimeUnit.SECONDS);
            Thread.sleep(200);
            if (!result) {
                System.out.println("Connect fail");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void startServiceS(BleDevice bleDevice) {
        List<BluetoothGattService> services = BleManager.getInstance().getBluetoothGattServices(bleDevice);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {


            for (BluetoothGattService service : services) {
                System.out.println(service.getUuid());
                if (service.getUuid().compareTo(sid) == 0) {
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        if (characteristic.getUuid().compareTo(txid) == 0) {
                            //newConnectedBLEDevice(bleDevice,characteristic);
                            CharSequence text = "TXID!";
                        } else if (characteristic.getUuid().compareTo(rxid) == 0) {
                            newConnectedBLEDevice(bleDevice, characteristic);
                            CharSequence text = "RxID!";
                        }
                    }
                }
            }


        }
    }

    /**
     * Start notify for characteristics changed
     *
     * @param bleDevice      BLE device
     * @param characteristic
     */
    public void newConnectedBLEDevice(final BleDevice bleDevice, final BluetoothGattCharacteristic characteristic) {
        int count = 1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            BleManager.getInstance().notify(
                    bleDevice,
                    characteristic.getService().getUuid().toString(),
                    characteristic.getUuid().toString(),
                    new BleNotifyCallback() {


                        @Override
                        public void onNotifySuccess() {

                        }

                        @Override
                        public void onNotifyFailure(BleException exception) {

                        }

                        @Override
                        public void onCharacteristicChanged(byte[] data) {
                            try {
                                mBuffer.write(data);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                    });
        }
    }

    @Override
    protected boolean bytesAvailableToBeRead() {
        if (mBuffer.size()>0) {
            return true;
        }
        return false;
    }

    @Override
    protected int availableBytes() {
        return mBuffer.size();
    }

    @Override
    public void writeBytes(byte[] bytes) {
        BleManager.getInstance().write(mBleDevice, sid.toString(), txid.toString(), bytes, false, new BleWriteCallback() {
            @Override
            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                System.out.println("Write Success " + UtilShimmer.bytesToHexStringWithSpacesFormatted(justWrite));
            }

            @Override
            public void onWriteFailure(BleException exception) {
                System.out.println("Write Fail");
            }
        });
    }

    @Override
    protected void stop() {
        try {
            disconnect();
        } catch (ShimmerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    protected void sendProgressReport(BluetoothProgressReportPerCmd bluetoothProgressReportPerCmd) {
        mDeviceCallbackAdapter.sendProgressReport(bluetoothProgressReportPerCmd);
    }

    @Override
    protected void isReadyForStreaming() {
        mDeviceCallbackAdapter.isReadyForStreaming();
        restartTimersIfNull();
    }

    @Override
    protected void isNowStreaming() {
        mDeviceCallbackAdapter.isNowStreaming();
    }

    @Override
    protected void hasStopStreaming() {
        mDeviceCallbackAdapter.hasStopStreaming();
    }

    @Override
    protected void sendStatusMsgPacketLossDetected() {

    }

    @Override
    protected void inquiryDone() {
        mDeviceCallbackAdapter.inquiryDone();
        isReadyForStreaming();
    }

    @Override
    protected void sendStatusMSGtoUI(String s) {

    }

    @Override
    protected void printLogDataForDebugging(String s) {
        consolePrintLn(s);
    }

    @Override
    protected void connectionLost() {
        try {
            disconnect();
        } catch (ShimmerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        setBluetoothRadioState(BT_STATE.CONNECTION_LOST);
    }

    @Override
    public boolean setBluetoothRadioState(BT_STATE state) {
        boolean isChanged = super.setBluetoothRadioState(state);
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
        }
        mDeviceCallbackAdapter.setBluetoothRadioState(state, isChanged);
        return isChanged;
    }

    @Override
    protected void startOperation(BT_STATE bt_state) {
        this.startOperation(bt_state, 1);
        consolePrintLn(bt_state + " START");
    }

    @Override
    protected void finishOperation(BT_STATE bt_state) {
        mDeviceCallbackAdapter.finishOperation(bt_state);
    }

    @Override
    protected void startOperation(BT_STATE bt_state, int i) {
        mDeviceCallbackAdapter.startOperation(bt_state, i);
    }

    @Override
    protected void eventLogAndStreamStatusChanged(byte currentCommand) {
        if (currentCommand == STOP_LOGGING_ONLY_COMMAND) {
            //TODO need to query the Bluetooth connection here!
            if (mIsStreaming) {
                setBluetoothRadioState(BT_STATE.STREAMING);
            } else if (isConnected()) {
                setBluetoothRadioState(BT_STATE.CONNECTED);
            } else {
                setBluetoothRadioState(BT_STATE.DISCONNECTED);
            }
        } else {
            if (mIsStreaming && isSDLogging()) {
                setBluetoothRadioState(BT_STATE.STREAMING_AND_SDLOGGING);
            } else if (mIsStreaming) {
                setBluetoothRadioState(BT_STATE.STREAMING);
            } else if (isSDLogging()) {
                setBluetoothRadioState(BT_STATE.SDLOGGING);
            } else {
                //				if(!isStreaming() && !isSDLogging() && isConnected()){
                if (!mIsStreaming && !isSDLogging() && isConnected() && mBluetoothRadioState != BT_STATE.CONNECTED) {
                    setBluetoothRadioState(BT_STATE.CONNECTED);
                }
                //				if(getBTState() == BT_STATE.INITIALISED){
                //
                //				}
                //				else if(getBTState() != BT_STATE.CONNECTED){
                //					setState(BT_STATE.CONNECTED);
                //				}

                CallbackObject callBackObject = new CallbackObject(NOTIFICATION_SHIMMER_STATE_CHANGE, mBluetoothRadioState, getMacId(), getComPort());
                sendCallBackMsg(MSG_IDENTIFIER_STATE_CHANGE, callBackObject);
            }
        }
    }

    @Override
    protected void batteryStatusChanged() {
        mDeviceCallbackAdapter.batteryStatusChanged();
    }

    @Override
    protected byte[] readBytes(int numberofBytes) {
        try {
            return mBuffer.read(numberofBytes);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected byte readByte() {
        try {
            return mBuffer.read();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    protected void dockedStateChange() {
        mDeviceCallbackAdapter.dockedStateChange();
    }

    @Override
    public ShimmerDevice deepClone() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(this);

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            Object readdd = ois.readObject();
            return (ShimmerDevice) readdd;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void interpretDataPacketFormat(Object o, Configuration.COMMUNICATION_TYPE communication_type) {

    }

    @Override
    public void createConfigBytesLayout() {
        mConfigByteLayout = new ConfigByteLayoutShimmer3(getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal(), getHardwareVersion());
    }

    @Override
    protected void dataHandler(ObjectCluster objectCluster) {
        mDeviceCallbackAdapter.dataHandler(objectCluster);
    }

    @Override
    protected void processMsgFromCallback(ShimmerMsg shimmerMsg) {
        System.out.println(shimmerMsg.mIdentifier);
    }

    @Override
    public void disconnect() throws ShimmerException {
        //		super.disconnect();
        stopAllTimers();
        BleManager.getInstance().disconnect(mBleDevice);
        closeConnection();
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Device disconnected");
        sendMsgToHandlerList(MESSAGE_TOAST, bundle);
    }
    private void sendMsgToHandlerList(int obtainMessage, Bundle bundle) {
        if (mHandler != null) {
            Message msg = mHandler.obtainMessage(obtainMessage);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }

    }
    private void closeConnection() {
        try {
            if (mIOThread != null) {
                mIOThread.stop = true;
                mIOThread.interrupt();
                mIOThread.join(1000);

                mIOThread = null;

                if (mUseProcessingThread && mPThread != null) {
                    mPThread.stop = true;
                    mPThread.interrupt();
                    mPThread = null;
                }
            }
            mBuffer = null;
            mIsStreaming = false;
            mIsInitialised = false;

            setBluetoothRadioState(BT_STATE.DISCONNECTED);
        } catch (Exception ex) {
            consolePrintException(ex.getMessage(), ex.getStackTrace());
            setBluetoothRadioState(BT_STATE.DISCONNECTED);
        }
    }

    //Need to override here because ShimmerDevice class uses a different map
    @Override
    public String getSensorLabel(int sensorKey) {
        //TODO 2017-08-03 MN: super does this but in a different way, don't know is either is better
        super.getSensorLabel(sensorKey);
        SensorDetails sensor = mSensorMap.get(sensorKey);
        if (sensor != null) {
            return sensor.mSensorDetailsRef.mGuiFriendlyLabel;
        }
        return null;
    }

    public class SensorDataReceived extends BasicProcessWithCallBack {

        @Override
        protected void processMsgFromCallback(ShimmerMsg shimmerMSG) {
            // TODO Auto-generated method stub
            System.out.println(shimmerMSG.mIdentifier);


            // TODO Auto-generated method stub

            // TODO Auto-generated method stub
            int ind = shimmerMSG.mIdentifier;

            Object object = (Object) shimmerMSG.mB;

            if (ind == Shimmer.MSG_IDENTIFIER_STATE_CHANGE) {
                CallbackObject callbackObject = (CallbackObject) object;

                if (callbackObject.mState == BT_STATE.CONNECTING) {
                } else if (callbackObject.mState == BT_STATE.CONNECTED) {
                } else if (callbackObject.mState == BT_STATE.DISCONNECTED
                        //						|| callbackObject.mState == BT_STATE.NONE
                        || callbackObject.mState == BT_STATE.CONNECTION_LOST) {

                }
            } else if (ind == Shimmer.MSG_IDENTIFIER_NOTIFICATION_MESSAGE) {
                CallbackObject callbackObject = (CallbackObject) object;
                int msg = callbackObject.mIndicator;
                if (msg == Shimmer.NOTIFICATION_SHIMMER_FULLY_INITIALIZED) {
                }
                if (msg == Shimmer.NOTIFICATION_SHIMMER_STOP_STREAMING) {

                } else if (msg == Shimmer.NOTIFICATION_SHIMMER_START_STREAMING) {

                } else {
                }
            } else if (ind == Shimmer.MSG_IDENTIFIER_DATA_PACKET) {

                double accelX = 0;
                double accelY = 0;
                double accelZ = 0;
                FormatCluster formatx;
                FormatCluster formaty;
                FormatCluster formatz;

                int INVALID_RESULT = -1;

                ObjectCluster objc = (ObjectCluster) shimmerMSG.mB;

                Collection<FormatCluster> adcFormats = objc.getCollectionOfFormatClusters(SensorKionixAccel.ObjectClusterSensorName.ACCEL_LN_X);
                formatx = ((FormatCluster) ObjectCluster.returnFormatCluster(adcFormats, ChannelDetails.CHANNEL_TYPE.CAL.toString())); // retrieve the calibrated data

                adcFormats = objc.getCollectionOfFormatClusters(SensorKionixAccel.ObjectClusterSensorName.ACCEL_LN_Y);
                formaty = ((FormatCluster) ObjectCluster.returnFormatCluster(adcFormats, ChannelDetails.CHANNEL_TYPE.CAL.toString())); // retrieve the calibrated data

                adcFormats = objc.getCollectionOfFormatClusters(SensorKionixAccel.ObjectClusterSensorName.ACCEL_LN_Z);
                formatz = ((FormatCluster) ObjectCluster.returnFormatCluster(adcFormats, ChannelDetails.CHANNEL_TYPE.CAL.toString())); // retrieve the calibrated data

                if (formatx != null) {
                    System.out.println("X:" + formatx.mData + " Y:" + formaty.mData + " Z:" + formatz.mData);

                } else {
                    System.out.println("ERROR! FormatCluster is Null!");
                }

            } else if (ind == Shimmer.MSG_IDENTIFIER_PACKET_RECEPTION_RATE_OVERALL) {

            }


        }

    }
}
