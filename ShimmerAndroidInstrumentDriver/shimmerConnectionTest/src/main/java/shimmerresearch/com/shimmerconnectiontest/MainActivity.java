package shimmerresearch.com.shimmerconnectiontest;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog;
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
import java.util.Timer;
import java.util.TimerTask;

import android.widget.EditText;

import static com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog.EXTRA_DEVICE_ADDRESS;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private final static String LOG_TAG = "ShimmerConnectionTest";
    ShimmerBluetoothManagerAndroid btManager;
    private EditText editTextShimmerStatus;
    private EditText editTextInterval;
    private EditText editTextSuccessCount;
    private EditText editTextFailureCount;
    private EditText editTextTotalIteration;
    private EditText editTextTestProgress;
    private EditText editTextFirmware;
    private EditText editTextAndroidDeviceModel;
    private EditText editTextShimmerDeviceName;
    private EditText editTextRetryCountLimit;
    private EditText editTextRetryCount;
    private EditText editTextTotalRetries;

    private int interval = 0;
    private int successCount = 0;
    private int failureCount = 0;
    private int totalIterationLimit = 10;
    private int currentIteration = 0;
    private int retryCount = 0;
    private int retryCountLimit = 5;
    private int durationBetweenTest = 1;
    private int totalRetries = 0;

    private String macAdd;
    private boolean isCurrentIterationSuccess;
    private boolean isTestStarted = false;
    private Timer timer;
    HashMap<Integer,Integer> ResultMap = new HashMap<>(); //-1,0,1 , unknown, fail, pass

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editTextShimmerStatus = (EditText) findViewById(R.id.shimmerStatus);
        editTextInterval = (EditText) findViewById(R.id.interval);
        editTextSuccessCount = (EditText) findViewById(R.id.success);
        editTextFailureCount = (EditText) findViewById(R.id.fail);
        editTextTotalIteration = (EditText) findViewById(R.id.testIterations);
        editTextTestProgress = (EditText) findViewById(R.id.testProgress);
        editTextFirmware = (EditText) findViewById(R.id.firmware);
        editTextAndroidDeviceModel = (EditText) findViewById(R.id.androidDeviceModel);
        editTextShimmerDeviceName = (EditText) findViewById(R.id.shimmerDeviceName);
        editTextRetryCountLimit = (EditText) findViewById(R.id.retryCountLimit);
        editTextRetryCount = (EditText) findViewById(R.id.retryCount);
        editTextTotalRetries = (EditText) findViewById(R.id.totalRetries);

        editTextAndroidDeviceModel.setText(Build.MANUFACTURER + " " + Build.PRODUCT);
        editTextRetryCountLimit.setText(Integer.toString(retryCountLimit));
        editTextTotalIteration.setText(Integer.toString(totalIterationLimit));
        editTextInterval.setText(Integer.toString(durationBetweenTest));

        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT);
        boolean permissionGranted = true;
        {
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionGranted = false;
            }
            permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionGranted = false;
            }
            permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionGranted = false;
            }
            permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionGranted = false;
            }
        }
        if (!permissionGranted) {
            // Should we show an explanation?
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION}, 110);

        } else {
            //startServiceandBTManager();
        }

    }

    public void startTest(View v){
        if(!isTestStarted){
            totalRetries = 0;
            if (btManager==null) {
                try {
                    btManager = new ShimmerBluetoothManagerAndroid(this, mHandler);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (btManager.getShimmer(macAdd)!=null) {
                btManager.disconnectShimmer(macAdd);
            }
            Intent intent = new Intent(getApplicationContext(), ShimmerBluetoothDialog.class);
            startActivityForResult(intent, ShimmerBluetoothDialog.REQUEST_CONNECT_SHIMMER);
        }
    }

    public void stopTest(View v){
        if(isTestStarted){
            editTextInterval.setEnabled(true);
            editTextTotalIteration.setEnabled(true);
            editTextRetryCountLimit.setEnabled(true);
            if(timer != null){
                timer.cancel();
            }
            isTestStarted = false;
        }
    }

    /**
     * Messages from the Shimmer device including sensor data are received here
     */
    Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE:
                    if (((CallbackObject)msg.obj).mIndicator==ShimmerBluetooth.NOTIFICATION_SHIMMER_FULLY_INITIALIZED) {


                            successCount += 1;
                            ResultMap.put(currentIteration,1);
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    editTextSuccessCount.setText(String.valueOf(successCount));
                                }
                            });
                            Log.i(LOG_TAG, "Success Count: " + successCount);
                            btManager.disconnectShimmer(macAdd);
                        if (isTestStarted) {
                            timer = new Timer();
                            timer.schedule(new ConnectTask(), Integer.parseInt(editTextInterval.getText().toString()) * 1000);
                        }
                    }
                    break;
                case ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET:
                    if ((msg.obj instanceof ObjectCluster)) {

                        //Print data to Logcat
                        ObjectCluster objectCluster = (ObjectCluster) msg.obj;

                        //Retrieve all possible formats for the current sensor device:
                        Collection<FormatCluster> allFormats = objectCluster.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP);
                        FormatCluster timeStampCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(allFormats,"CAL"));
                        double timeStampData = timeStampCluster.mData;
                        Log.i(LOG_TAG, "Time Stamp: " + timeStampData);
                        allFormats = objectCluster.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_X);
                        FormatCluster accelXCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(allFormats,"CAL"));
                        if (accelXCluster!=null) {
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
                            editTextShimmerStatus.setText("CONNECTED");
                            editTextShimmerDeviceName.setText(((ObjectCluster) msg.obj).getShimmerName());
                            ResultMap.put(currentIteration,1);
                            break;
                        case CONNECTING:
                            editTextShimmerStatus.setText("CONNECTING");
                            break;
                        case STREAMING:
                            editTextShimmerStatus.setText("STREAMING");
                            break;
                        case STREAMING_AND_SDLOGGING:
                            editTextShimmerStatus.setText("STREAMING AND LOGGING");
                            break;
                        case SDLOGGING:
                            editTextShimmerStatus.setText("SDLOGGING");
                            break;
                        case DISCONNECTED:
                            editTextShimmerStatus.setText("DISCONNECTED");
                            if (ResultMap.get(currentIteration)==-1){
                                Log.i(LOG_TAG, "Disconnected State " + "Retry Count: " + Integer.toString(retryCount) + "; Total number of retries:" + totalRetries);
                                if (retryCount<retryCountLimit) {
                                    retryCount++;
                                    totalRetries++;
                                    editTextRetryCount.setText(Integer.toString(retryCount));
                                    editTextTotalRetries.setText(Integer.toString(totalRetries));
                                    //Toast.makeText(getApplicationContext(), "Retry Count " + Integer.toString(retryCount), Toast.LENGTH_SHORT).show();
                                    try {
                                        Thread.sleep(500);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    //shimmer = null;
                                    //shimmer = new Shimmer(mHandler);
                                    //shimmer.connect(macAdd, "default");
                                    Log.i(LOG_TAG, "Connect Called, retry count: " + Integer.toString(retryCount) + "; Total number of retries:" + totalRetries);
                                    btManager.removeShimmerDeviceBtConnected(macAdd);
                                    btManager.putShimmerGlobalMap(macAdd,new Shimmer(mHandler,MainActivity.this));
                                    btManager.connectShimmerThroughBTAddress(macAdd);
                                    try {
                                        Thread.sleep(500);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                } else {

                                    if (ResultMap.get(currentIteration)==-1) {
                                        ResultMap.put(currentIteration, 0);

                                        timer.cancel();
                                        failureCount += 1;
                                        runOnUiThread(new Runnable() {
                                            public void run() {
                                                editTextFailureCount.setText(String.valueOf(failureCount));
                                                }
                                        });
                                    }
                                    if (isTestStarted && currentIteration< totalIterationLimit) {

                                        Log.i(LOG_TAG, "Failure Count: " + failureCount);

                                        timer = new Timer();
                                        timer.schedule(new ConnectTask(), Integer.parseInt(editTextInterval.getText().toString()) * 1000);
                                    }
                                }
                            }
                            break;
                    }
                    break;
            }
            super.handleMessage(msg);
        }
    };

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
                macAdd = data.getStringExtra(EXTRA_DEVICE_ADDRESS);
                totalIterationLimit = Integer.parseInt(editTextTotalIteration.getText().toString());
                retryCountLimit = Integer.parseInt(editTextRetryCountLimit.getText().toString());
                currentIteration = 0;
                successCount = 0;
                failureCount = 0;
                editTextFailureCount.setText(String.valueOf(failureCount));
                editTextSuccessCount.setText(String.valueOf(successCount));
                editTextTotalRetries.setText(String.valueOf(totalRetries));
                editTextTestProgress.setText("0 of" + String.valueOf(totalIterationLimit));
                editTextInterval.setEnabled(false);
                editTextTotalIteration.setEnabled(false);
                editTextRetryCountLimit.setEnabled(false);
                isCurrentIterationSuccess = true;
                isTestStarted = true;

                timer = new Timer();
                timer.schedule(new ConnectTask(),0);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    public class ConnectTask extends  TimerTask{
        @Override
        public void run() {
            if (isTestStarted) {
                Log.i(LOG_TAG, "Current Iteration: " + currentIteration);
                retryCount = 0;
                runOnUiThread(new Runnable() {
                    public void run() {
                        editTextRetryCount.setText(Integer.toString(retryCount));
                    }
                });

                if (currentIteration >= totalIterationLimit) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            editTextInterval.setEnabled(true);
                            editTextTotalIteration.setEnabled(true);
                        }
                    });
                    timer.cancel();
                    isTestStarted = false;
                    return;
                } else {
                    currentIteration += 1;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            editTextTestProgress.setText(String.valueOf(currentIteration) + " of " + String.valueOf(totalIterationLimit));

                        }
                    });
                    //isCurrentIterationSuccess = false;
                    ResultMap.put(currentIteration,-1);
                    //shimmer = null;
                    //shimmer = new Shimmer(mHandler);
                    //shimmer.connect(macAdd, "default");
                    Log.i(LOG_TAG, "Connect Called, retry count: " + Integer.toString(retryCount) + "; Total number of retries:" + totalRetries);
                    Shimmer shimmer = new Shimmer(mHandler, MainActivity.this);
                    shimmer.setMacIdFromUart(macAdd);
                    btManager.removeShimmerDeviceBtConnected(macAdd);
                    btManager.putShimmerGlobalMap(macAdd, shimmer);
                    btManager.connectShimmerThroughBTAddress(macAdd);
                }

            }
        }
    }


}
