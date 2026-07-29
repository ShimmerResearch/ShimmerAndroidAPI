package com.shimmerresearch.shimmerlegacyexample;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.android.guiUtilities.EnableSensorsDialog;
import com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog;
import com.shimmerresearch.android.guiUtilities.ShimmerDialogConfigurations;
import com.shimmerresearch.android.manager.ShimmerBluetoothManagerAndroid;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.exceptions.ShimmerException;

import java.util.Collection;
import java.util.HashMap;

import static com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog.EXTRA_DEVICE_ADDRESS;
import static com.shimmerresearch.bluetooth.ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE;
import static com.shimmerresearch.bluetooth.ShimmerBluetooth.NOTIFICATION_SHIMMER_FULLY_INITIALIZED;

/**
 * This example demonstrates connecting and streaming from a Shimmer2/2r device.
 * Note: for Shimmer3 and newer devices, use the Bluetooth Manager Example instead
 */
public class MainActivity extends AppCompatActivity {

    ShimmerBluetoothManagerAndroid btManager;
    String shimmerBtAdd = "";
    final static String LOG_TAG = "ShimmerLegacyExample";
    private boolean mFirstTimeConnection = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            btManager = new ShimmerBluetoothManagerAndroid(this, mHandler);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Messages from the Shimmer device, including sensor data, are received here
     */
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch(msg.what) {

                case ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE:
                    ShimmerBluetooth.BT_STATE state = null;
                    String macAddress = "";
                    String shimmerName = "";

                    if (msg.obj instanceof ObjectCluster) {
                        state = ((ObjectCluster) msg.obj).mState;
                        macAddress = ((ObjectCluster) msg.obj).getMacAddress();
                        shimmerName = ((ObjectCluster) msg.obj).getShimmerName();
                    } else if (msg.obj instanceof CallbackObject) {
                        state = ((CallbackObject) msg.obj).mState;
                        macAddress = ((CallbackObject) msg.obj).mBluetoothAddress;
                        shimmerName = ((ObjectCluster) msg.obj).getShimmerName();
                    }

                    switch(state) {
                        case CONNECTING:
                            Log.i(LOG_TAG, "Connecting to device: " + macAddress);
                            break;
                        case CONNECTED:
                            Log.i(LOG_TAG, "Device connected: " + macAddress);
                            //Check if Accel is enabled on the Shimmer, and if not, enable it
                            if(mFirstTimeConnection) {
                                Shimmer shimmer = (Shimmer) btManager.getShimmerDeviceBtConnectedFromMac(macAddress);
                                ((ShimmerBluetooth) shimmer).writeEnabledSensors(ShimmerBluetooth.SENSOR_ACCEL);
                            }
                            mFirstTimeConnection = false;
                            break;
                        case STREAMING:
                            Log.i(LOG_TAG, "Device: " + macAddress + " now streaming");
                            break;
                        case STREAMING_AND_SDLOGGING:
                            break;
                        case DISCONNECTED:
                            Log.i(LOG_TAG, "Device disconnected: " + macAddress);
                            break;
                    }
                    break;
                case ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET:
                    if ((msg.obj instanceof ObjectCluster)) {

                        ObjectCluster objectCluster = (ObjectCluster) msg.obj;

                        Collection<FormatCluster> allFormats = objectCluster.getCollectionOfFormatClusters(Configuration.Shimmer2.ObjectClusterSensorName.TIMESTAMP);
                        FormatCluster timeStampCluster = ((FormatCluster) ObjectCluster.returnFormatCluster(allFormats, "CAL"));
                        double timeStampData = timeStampCluster.mData;
                        Log.i(LOG_TAG, "Time Stamp: " + timeStampData);
                        allFormats = objectCluster.getCollectionOfFormatClusters(Configuration.Shimmer2.ObjectClusterSensorName.ACCEL_X);
                        FormatCluster accelXCluster = ((FormatCluster) ObjectCluster.returnFormatCluster(allFormats, "CAL"));
                        if (accelXCluster != null) {
                            double accelXData = accelXCluster.mData;
                            Log.i(LOG_TAG, "Accel LN X: " + accelXData);
                        }
                    }
                    break;
                case Shimmer.MESSAGE_TOAST:
                    /** Toast messages sent from {@link Shimmer} are received here. E.g. device xxxx now streaming.
                     *  Note that display of these Toast messages is done automatically in the Handler in {@link com.shimmerresearch.android.shimmerService.ShimmerService} */
                    Toast.makeText(getApplicationContext(), msg.getData().getString(Shimmer.TOAST), Toast.LENGTH_SHORT).show();
                    break;
                case ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE:
                    if (msg.obj instanceof CallbackObject) {
                        int ind = ((CallbackObject) msg.obj).mIndicator;
                        if (ind == NOTIFICATION_SHIMMER_FULLY_INITIALIZED) {
                            //FULLY_INITIALIZED state is returned when Shimmer is connected or after Shimmer has been configured
                        }
                    }
                    break;
            }

            super.handleMessage(msg);
        }
    };

    public void selectDevice(View v) {
        Intent intent = new Intent(getApplicationContext(), ShimmerBluetoothDialog.class);
        startActivityForResult(intent, ShimmerBluetoothDialog.REQUEST_CONNECT_SHIMMER);
    }

    public void startStreaming(View v) {
        try {
            btManager.startStreaming(shimmerBtAdd);
        } catch (ShimmerException e) {
            e.printStackTrace();
        }
    }

    public void stopStreaming(View v) {
        try {
            btManager.stopStreaming(shimmerBtAdd);
        } catch (ShimmerException e) {
            e.printStackTrace();
        }
    }

    public void disconnectDevice(View v){
        btManager.disconnectAllDevices();
        mFirstTimeConnection = true;
    }
/*  TODO: Add in configuration dialogs
    public void enableSensors(View v) {
        ShimmerDevice shimmerDevice = btManager.getShimmerDeviceBtConnectedFromMac(shimmerBtAdd);
        if(shimmerDevice != null) {
            EnableSensorsDialog dialog = new EnableSensorsDialog(shimmerDevice, btManager, this);
        } else {
            Toast.makeText(this, "Can't enable sensors: no device connected", Toast.LENGTH_LONG).show();
        }
    }

    public void configureSensors(View v) {
        ShimmerDevice shimmerDevice = btManager.getShimmerDeviceBtConnectedFromMac(shimmerBtAdd);
        if(shimmerDevice != null) {
            ShimmerDialogConfigurations.buildShimmerConfigOptions(shimmerDevice, this, btManager);
        } else {
            Toast.makeText(this, "Can't configure sensors: no device connected", Toast.LENGTH_LONG).show();
        }
    }
*/

    /**
     * Gets the selected Bluetooth address from the list of paired Bluetooth devices in ShimmerBluetoothDialog
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == 2) {
            if (resultCode == Activity.RESULT_OK) {
                //Ensure no previous device is connected to the App as it only supports a single device at a time:
                btManager.disconnectAllDevices();

                //Get the Bluetooth mac address of the selected device:
                String macAdd = data.getStringExtra(EXTRA_DEVICE_ADDRESS);
                btManager.connectShimmerThroughBTAddress(macAdd);   //Connect to the selected device
                shimmerBtAdd = macAdd;
            }

        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        btManager.disconnectAllDevices();
        super.onDestroy();
    }
}
