package com.shimmerresearch.efficientdataarrayexample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.clj.fastble.BleManager;
import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.android.guiUtilities.FileListActivity;
import com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog;
import com.shimmerresearch.android.manager.ShimmerBluetoothManagerAndroid;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.HwDriverShimmerDeviceDetails;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.tools.FileUtils;
import com.shimmerresearch.verisense.VerisenseDevice;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog.EXTRA_DEVICE_ADDRESS;
import static com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog.EXTRA_DEVICE_NAME;
import static com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog.REQUEST_CONNECT_SHIMMER;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

/**
 * This example demonstrates the use of the arrays data structure, {@link ObjectCluster#sensorDataArray}, in the following scenarios:
 * <ul>
 *     <li>Enabling the arrays data structure</li>
 *     <li>Different methods of retrieving signal data from the arrays</li>
 *     <li>Writing the signal data to a CSV file</li>
 * </ul>
 * Note: The arrays data structure is an alternative to the standard Multimap ({@link ObjectCluster#mPropertyCluster}) data structure.
 * Switching to using the arrays can improve packet reception rate on slower Android devices.
 */
public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_FILE_REQUEST_SHIMMER = 99;
    ShimmerBluetoothManagerAndroid btManager;
    private String bluetoothAdd = "";
    private final static String LOG_TAG = "ArraysExample";
    private final static String CSV_FILE_NAME_PREFIX = "Data";
    private final static String APP_FOLDER_NAME = "ShimmerArraysExample";
    private String APP_DIR_PATH = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + APP_FOLDER_NAME + File.separator;
    /** This can be found in the Manifest */
    private final static String APP_FILE_PROVIDER_AUTHORITY = "com.shimmerresearch.efficientdataarrayexample.fileprovider";
    private final static int PERMISSIONS_REQUEST_WRITE_STORAGE = 5;

    //Write to CSV variables
    //private FileWriter fw;
    private BufferedWriter bw;
    private File file;
    boolean firstTimeWrite = true;
    Uri mTreeUri;

    ShimmerBluetoothManagerAndroid.BT_TYPE preferredBtType;
    Looper looper = Looper.myLooper();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        boolean permissionGranted = true;
        int permissionCheck = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT);

            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionGranted = false;
            }
            permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionGranted = false;
            }
        } else {
            permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionGranted = false;
            }
        }
        permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            permissionGranted = false;
        }
        permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            permissionGranted = false;
        }


        if (!permissionGranted) {
            // Should we show an explanation?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 110);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 110);
            }
        } else {

            Intent intent =new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

            startActivityForResult(intent, PERMISSION_FILE_REQUEST_SHIMMER);


            try {
                BleManager.getInstance().init(getApplication());
                btManager = new ShimmerBluetoothManagerAndroid(this, mHandler);


            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public void connectDevice(View v) {
        Intent intent = new Intent(getApplicationContext(), ShimmerBluetoothDialog.class);
        startActivityForResult(intent, REQUEST_CONNECT_SHIMMER);
    }

    public void disconnectDevice(View v){
        btManager.disconnectAllDevices();
    }

    public void startStreaming(View v) {
        ShimmerBluetooth shimmer = (ShimmerBluetooth) btManager.getShimmer(bluetoothAdd);
        if(shimmer != null) {   //this is null if Shimmer device is not connected
            setupCSV();
            //Disable PC timestamps for better performance. Disabling this takes the timestamps on every full packet received instead of on every byte received.
            shimmer.enablePCTimeStamps(false);
            //Disable timers for better performance.
            shimmer.stopAllTimers();
            //Enable the arrays data structure. Note that enabling this will disable the Multimap/FormatCluster data structure
            shimmer.enableArraysDataStructure(true);
            try {
                btManager.startStreaming(bluetoothAdd);
            } catch (ShimmerException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Can't start streaming\nShimmer device is not connected", Toast.LENGTH_SHORT).show();
        }
    }

    public void stopStreaming(View v) {
        if(btManager.getShimmer(bluetoothAdd) != null) {
            try {
                ShimmerBluetooth shimmer = (ShimmerBluetooth) btManager.getShimmer(bluetoothAdd);
                btManager.stopStreaming(bluetoothAdd);
            } catch (ShimmerException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Can't stop streaming\nShimmer device is not connected", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Get the result from the paired devices dialog
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {


        if(requestCode == 2) {
            if (resultCode == Activity.RESULT_OK) {
                //Get the Bluetooth mac address of the selected device:
                bluetoothAdd = data.getStringExtra(EXTRA_DEVICE_ADDRESS);
                String deviceName = data.getStringExtra(EXTRA_DEVICE_NAME);
                preferredBtType = ShimmerBluetoothManagerAndroid.BT_TYPE.BT_CLASSIC;
                if (deviceName.contains(HwDriverShimmerDeviceDetails.DEVICE_TYPE.SHIMMER3R.toString())){
                    showBtTypeConnectionOption();
                }
                btManager.connectShimmerThroughBTAddress(bluetoothAdd,deviceName,preferredBtType); //Connect to the selected device
            }

        }
        if (resultCode == RESULT_OK && requestCode == PERMISSION_FILE_REQUEST_SHIMMER) {
            if (data != null) {
                mTreeUri = data.getData();
                FileUtils futils = new FileUtils(MainActivity.this);
                File file = new File(futils.getPath(mTreeUri, FileUtils.UriType.FOLDER));
                APP_DIR_PATH = file.getAbsolutePath();
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public void showBtTypeConnectionOption(){
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setCancelable(false);
        alertDialog.setMessage("Choose preferred Bluetooth type");
        alertDialog.setButton( Dialog.BUTTON_POSITIVE, "BT CLASSIC", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                preferredBtType = ShimmerBluetoothManagerAndroid.BT_TYPE.BT_CLASSIC;
                looper.quit();
            };
        });
        alertDialog.setButton( Dialog.BUTTON_NEGATIVE, "BLE", new DialogInterface.OnClickListener()    {
            public void onClick(DialogInterface dialog, int which) {
                preferredBtType = ShimmerBluetoothManagerAndroid.BT_TYPE.BLE;
                looper.quit();
            };
        });
        alertDialog.show();
        try{ looper.loop(); }
        catch(RuntimeException e){}
    }
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int countNonNulls(String[] dataArray){
        int count =0;
        for (String data:dataArray){
            if (data!=null){
                count++;
            }
        }
        return count;
    }

    int numberOfChannels = 0;
    private void writeDataToFile(ObjectCluster objc) {
        if (firstTimeWrite) {
            //Write headers on first-time
            numberOfChannels = countNonNulls(objc.sensorDataArray.mSensorNames);
            int count = 1;
            for (String channelName : objc.sensorDataArray.mSensorNames) {
                try {
                    if(channelName!=null) {
                        if (count < numberOfChannels) {
                            bw.write(channelName + ",");
                        } else {
                            bw.write(channelName);
                        }
                    }
                   count++;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                bw.write("\n");
            } catch (IOException e2) {
                e2.printStackTrace();
            }
            firstTimeWrite = false;
        }
        int count = 1;
        for (double calData : objc.sensorDataArray.mCalData) {

            String dataString = String.valueOf(calData);
            try {
                if(objc.sensorDataArray.mSensorNames[count-1]!=null) {
                    if (count < numberOfChannels) {
                        bw.write(dataString + ",");
                    } else {
                        bw.write(dataString);
                    }
                }
                count++;
            } catch (IOException e3) {
                e3.printStackTrace();
            }
        }
        try {
            bw.write("\n");
        } catch (IOException e2) {
            e2.printStackTrace();
        }
    }


    Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET:
                    if ((msg.obj instanceof ObjectCluster)) {


                            ObjectCluster objc = (ObjectCluster) msg.obj;

                            /**
                             * ---------- Printing a channel to Logcat ----------
                             */
                            //Method 1 - retrieve data from the ObjectCluster using get method
                            double data = objc.getFormatClusterValue(Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_WR_X, ChannelDetails.CHANNEL_TYPE.CAL.toString());
                            Log.i(LOG_TAG, Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_WR_X + " data: " + data);

                            //Method 2a - retrieve data from the ObjectCluster by manually parsing the arrays
                            int index = -1;
                            for (int i = 0; i < objc.sensorDataArray.mSensorNames.length; i++) {
                                if (objc.sensorDataArray.mSensorNames[i] != null) {
                                    if (objc.sensorDataArray.mSensorNames[i].equals(Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_WR_X)) {
                                        index = i;
                                    }
                                }
                            }
                            if (index != -1) {
                                //Index was found
                                data = objc.sensorDataArray.mCalData[index];
                                Log.w(LOG_TAG, Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_WR_X + " data: " + data);
                            }

                            //Method 2b - retrieve data from the ObjectCluster by getting the index, then accessing the arrays
                            index = objc.getIndexForChannelName(Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_WR_X);
                            if (index != -1) {
                                data = objc.sensorDataArray.mCalData[index];
                                Log.e(LOG_TAG, Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_WR_X + " data: " + data);
                            }

                            //Method 2b - retrieve data from the ObjectCluster by getting the index, then accessing the arrays
                            index = objc.getIndexForChannelName(Configuration.Shimmer3.ObjectClusterSensorName.PACKET_RECEPTION_RATE_OVERALL);
                            if (index != -1) {
                                data = objc.sensorDataArray.mCalData[index];
                                Log.e(LOG_TAG, Configuration.Shimmer3.ObjectClusterSensorName.PACKET_RECEPTION_RATE_OVERALL + " data: " + data);
                            }

                            /**
                             * ---------- Writing all channels of CAL data to CSV file ----------
                             */
/*
                            if (firstTimeWrite) {
                                //Write headers on first-time
                                for (String channelName : objc.sensorDataArray.mSensorNames) {
                                    try {
                                        bw.write(channelName + ",");
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                try {
                                    bw.write("\n");
                                } catch (IOException e2) {
                                    e2.printStackTrace();
                                }
                                firstTimeWrite = false;
                            }
                            for (double calData : objc.sensorDataArray.mCalData) {
                                String dataString = String.valueOf(calData);
                                try {
                                    bw.write(dataString + ",");
                                } catch (IOException e3) {
                                    e3.printStackTrace();
                                }
                            }
                            try {
                                bw.write("\n");
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
*/

                        // Execute file writing task in a separate thread
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                writeDataToFile(objc);
                            }
                        });
                    }
                    break;
                case Shimmer.MESSAGE_TOAST:
                    /** Toast messages sent from {@link Shimmer} are received here. E.g. device xxxx now streaming.
                     *  Note that display of these Toast messages is done automatically in the Handler in {@link com.shimmerresearch.android.shimmerService.ShimmerService} */
                    Toast.makeText(getApplicationContext(), msg.getData().getString(Shimmer.TOAST), Toast.LENGTH_SHORT).show();
                    break;
                case ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE:
                    ShimmerBluetooth.BT_STATE state = null;
                    String macAddress = "";

                    if (msg.obj instanceof ObjectCluster) {
                        state = ((ObjectCluster) msg.obj).mState;
                        macAddress = ((ObjectCluster) msg.obj).getMacAddress();
                    } else if (msg.obj instanceof CallbackObject) {
                        state = ((CallbackObject) msg.obj).mState;
                        macAddress = ((CallbackObject) msg.obj).mBluetoothAddress;
                    }
                    switch (state) {
                        case CONNECTED:
                            if (bw!=null && !firstTimeWrite) {
                                try {   //Stop CSV writing
                                    bw.flush();
                                    bw.close();
                                    //fw.close();
                                    firstTimeWrite = true;
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        case CONNECTING:
                            break;
                        case STREAMING:
                            break;
                        case STREAMING_AND_SDLOGGING:
                            break;
                        case SDLOGGING:
                            break;
                        case DISCONNECTED:
                            break;
                    }
                    break;
            }
            super.handleMessage(msg);
        }
    };

    /**
     * Permission request callback
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 110){
            Intent intent =new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

            startActivityForResult(intent, PERMISSION_FILE_REQUEST_SHIMMER);


            try {
                btManager = new ShimmerBluetoothManagerAndroid(this, mHandler);


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Setup CSV writing
     */
    private void setupCSV() {
        File dir = new File(APP_DIR_PATH);
        if(!dir.exists()) {
            //Create the directory if it doesn't already exist
            dir.mkdir();
        }

        String fileName = CSV_FILE_NAME_PREFIX + "_" + new SimpleDateFormat("dd-MM-yy_HHmm").format(new Date());
        String filePath = APP_DIR_PATH + File.separator + fileName;
        DocumentFile pickedDir = DocumentFile.fromTreeUri(MainActivity.this, mTreeUri);
        DocumentFile newFile = pickedDir.createFile("text/comma-separated-values", fileName);
        if (newFile != null) {
            try {
                OutputStream outputStream = MainActivity.this.getContentResolver().openOutputStream(newFile.getUri());
                bw = new BufferedWriter(new OutputStreamWriter(outputStream));
            } catch (IOException e) {
                System.out.println();
            }
        } else {

        }


    }

    /**
     * Launch the files list activity, which is themed as a dialog in the Android Manifest
     * @param v
     */
    public void openLogFilesList(View v) {
        Intent intent = new Intent(getApplicationContext(), FileListActivity.class);
        intent.putExtra(FileListActivity.INTENT_EXTRA_DIR_PATH, APP_DIR_PATH);
        intent.putExtra(FileListActivity.INTENT_EXTRA_PROVIDER_AUTHORITY, APP_FILE_PROVIDER_AUTHORITY);
        startActivityForResult(intent, REQUEST_CONNECT_SHIMMER);
    }

}
