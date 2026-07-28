package shimmerresearch.com.multiverisenseblebasicexample;

import static com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog.EXTRA_DEVICE_ADDRESS;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.clj.fastble.BleManager;
import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.android.VerisenseDeviceAndroid;
import com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog;
import com.shimmerresearch.android.manager.ShimmerBluetoothManagerAndroid;
import com.shimmerresearch.androidradiodriver.VerisenseBleAndroidRadioByteCommunication;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.verisense.communication.VerisenseProtocolByteCommunication;
import com.shimmerresearch.verisense.sensors.SensorLIS2DW12;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final static String LOG_TAG = "MultiVeriBLEExample";
    private static final int REQUEST_CONNECT_SENSOR_1 = 201;
    private static final int REQUEST_CONNECT_SENSOR_2 = 202;
    private static final int REQUEST_CONNECT_SENSOR_3 = 203;

    VerisenseBleAndroidRadioByteCommunication radio1 = new VerisenseBleAndroidRadioByteCommunication("DA:A6:19:F0:4A:D7");
    VerisenseProtocolByteCommunication protocol1 = new VerisenseProtocolByteCommunication(radio1);
    VerisenseDeviceAndroid device1;

    VerisenseBleAndroidRadioByteCommunication radio2 = new VerisenseBleAndroidRadioByteCommunication("C9:61:17:53:74:02");
    VerisenseProtocolByteCommunication protocol2 = new VerisenseProtocolByteCommunication(radio2);
    VerisenseDeviceAndroid device2;
//BTHLE\Dev_f2527c20d97e
    VerisenseBleAndroidRadioByteCommunication radio3 = new VerisenseBleAndroidRadioByteCommunication("F2:52:7C:20:D9:7E");
    VerisenseProtocolByteCommunication protocol3 = new VerisenseProtocolByteCommunication(radio3);
    VerisenseDeviceAndroid device3;
    TextView txtViewThroughput1;
    TextView txtViewThroughput2;
    TextView txtViewThroughput3;
    boolean isSpeedTestSensor1 = false;
    boolean isSpeedTestSensor2 = false;
    boolean isSpeedTestSensor3 = false;
    private Thread t1 = null;
    private Thread t2 = null;
    private Thread t3 = null;

    private void initializeBleAndDevices() {
        BleManager.getInstance().init(getApplication());
        device1 = new VerisenseDeviceAndroid(mHandler);
        device2 = new VerisenseDeviceAndroid(mHandler);
        device3 = new VerisenseDeviceAndroid(mHandler);
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private String[] getMissingRequiredPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                missingPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                missingPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }
        return missingPermissions.toArray(new String[0]);
    }

    private boolean hasRequiredBluetoothPermissions() {
        return getMissingRequiredPermissions().length == 0;
    }

    private boolean ensureDevicesReady() {
        if (device1 == null || device2 == null || device3 == null) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "Bluetooth permissions are required before connecting.",
                    Toast.LENGTH_SHORT).show());
            return false;
        }
        return true;
    }

    private void launchDevicePicker(int requestCode) {
        Intent pairedDevicesIntent = new Intent(this.getApplicationContext(), ShimmerBluetoothDialog.class);
        startActivityForResult(pairedDevicesIntent, requestCode);
    }

    private void connectToSelectedDevice(int requestCode, String macAddress) {
        if (macAddress == null || macAddress.isEmpty()) {
            Toast.makeText(this, "No device selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        switch (requestCode) {
            case REQUEST_CONNECT_SENSOR_1:
                radio1 = new VerisenseBleAndroidRadioByteCommunication(macAddress);
                protocol1 = new VerisenseProtocolByteCommunication(radio1);
                connectDeviceInThread(device1, protocol1, "Sensor 1");
                break;
            case REQUEST_CONNECT_SENSOR_2:
                radio2 = new VerisenseBleAndroidRadioByteCommunication(macAddress);
                protocol2 = new VerisenseProtocolByteCommunication(radio2);
                connectDeviceInThread(device2, protocol2, "Sensor 2");
                break;
            case REQUEST_CONNECT_SENSOR_3:
                radio3 = new VerisenseBleAndroidRadioByteCommunication(macAddress);
                protocol3 = new VerisenseProtocolByteCommunication(radio3);
                connectDeviceInThread(device3, protocol3, "Sensor 3");
                break;
        }
    }

    private void connectDeviceInThread(VerisenseDeviceAndroid device,
                                       VerisenseProtocolByteCommunication protocol,
                                       String sensorName) {
        Thread thread = new Thread() {
            public void run() {
                device.setProtocol(Configuration.COMMUNICATION_TYPE.BLUETOOTH, protocol);
                try {
                    device.connect();
                } catch (ShimmerException e1) {
                    Log.e(LOG_TAG, "Connect failed for " + sensorName, e1);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "Connect failed for " + sensorName,
                            Toast.LENGTH_SHORT).show());
                }
            }
        };
        thread.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String[] missingPermissions = getMissingRequiredPermissions();
        if (missingPermissions.length > 0) {
            ActivityCompat.requestPermissions(this, missingPermissions, 110);
        } else {
            initializeBleAndDevices();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 110){
            // Re-check actual permission state because Android may split/merge dialog results.
            if (hasRequiredBluetoothPermissions()) {
                initializeBleAndDevices();
            } else {
                Toast.makeText(this,
                        "Required Bluetooth permission denied. Cannot connect to devices.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    //Sensor 1
    public void connectDevice1(View v) {
        if (!ensureDevicesReady()) {
            return;
        }
        launchDevicePicker(REQUEST_CONNECT_SENSOR_1);
    }

    public void disconnectDevice1(View v) {
        Thread thread = new Thread(){
            public void run(){
                try {
                    isSpeedTestSensor1 = false;
                    device1.disconnect();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void readOpConfig1(View v) {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol1.readOperationalConfig();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void readProdConfig1(View v)  {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol1.readProductionConfig();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void startStreaming1(View v) throws InterruptedException, IOException, ShimmerException {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol1.startStreaming();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void stopStreaming1(View v) throws IOException, ShimmerException {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol1.stopStreaming();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }
    public void startSpeedTest1(View v) throws IOException, ShimmerException {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol1.startSpeedTest();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }
    public void stopSpeedTest1(View v) throws IOException, ShimmerException {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol1.stopSpeedTest();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    //Sensor 2
    public void connectDevice2(View v) {
        if (!ensureDevicesReady()) {
            return;
        }
        launchDevicePicker(REQUEST_CONNECT_SENSOR_2);
    }

    public void disconnectDevice2(View v) {
        Thread thread = new Thread(){
            public void run(){
                try {
                    isSpeedTestSensor2 = false;
                    device2.disconnect();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void readOpConfig2(View v) {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol2.readOperationalConfig();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void readProdConfig2(View v)  {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol2.readProductionConfig();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void startStreaming2(View v) throws InterruptedException, IOException, ShimmerException {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol2.startStreaming();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void stopStreaming2(View v) throws IOException, ShimmerException {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol2.stopStreaming();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }
    public void startSpeedTest1And2(View v) throws IOException, ShimmerException {
         t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    protocol1.startSpeedTest();
                    protocol2.startSpeedTest();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        });
        t1.start();
    }
    public void stopSpeedTest1And2(View v) throws IOException, ShimmerException {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol1.stopSpeedTest();
                    protocol2.stopSpeedTest();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };

        thread.start();
    }
    //Sensor 3
    public void connectDevice3(View v) {
        if (!ensureDevicesReady()) {
            return;
        }
        launchDevicePicker(REQUEST_CONNECT_SENSOR_3);
    }

    public void disconnectDevice3(View v) {
        Thread thread = new Thread(){
            public void run(){
                try {
                    isSpeedTestSensor3 = false;
                    device3.disconnect();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void readOpConfig3(View v) {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol3.readOperationalConfig();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void readProdConfig3(View v)  {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol3.readProductionConfig();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void startStreaming3(View v) throws InterruptedException, IOException, ShimmerException {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol3.startStreaming();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void stopStreaming3(View v) throws IOException, ShimmerException {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol3.stopStreaming();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void startAllSpeedTest(View v) throws IOException, ShimmerException {

        t1 = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    protocol1.startSpeedTest();
                    protocol2.startSpeedTest();
                    protocol3.startSpeedTest();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        });
        t1.start();
    }
    public void stopAllSpeedTest(View v) throws IOException, ShimmerException {
        Thread thread = new Thread(){
            public void run(){
                try {
                    protocol1.stopSpeedTest();
                    protocol2.stopSpeedTest();
                    protocol3.stopSpeedTest();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };

        thread.start();
    }
    /**
     * Sensor 1
     * Messages from the Shimmer device including sensor data are received here
     */
    Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET:
                    if ((msg.obj instanceof ObjectCluster)) {

                        //Print data to Logcat
                        ObjectCluster objectCluster = (ObjectCluster) msg.obj;

                        //Retrieve all possible formats for the current sensor device:
                        Collection<FormatCluster> allFormats = objectCluster.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP);
                        FormatCluster timeStampCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(allFormats,"CAL"));
                        double timeStampData = timeStampCluster.mData;
                        Log.i(LOG_TAG, "Time Stamp: " + timeStampData);
                        allFormats = objectCluster.getCollectionOfFormatClusters(SensorLIS2DW12.ObjectClusterSensorName.LIS2DW12_ACC_X);
                        FormatCluster accelXCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(allFormats,"CAL"));
                        if (accelXCluster!=null) {
                            double accelXData = accelXCluster.mData;
                            Log.i(LOG_TAG, "Accel X: " + accelXData);
                        }
                        allFormats = objectCluster.getCollectionOfFormatClusters(SensorLIS2DW12.ObjectClusterSensorName.LIS2DW12_ACC_Y);
                        FormatCluster accelYCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(allFormats,"CAL"));
                        if (accelXCluster!=null) {
                            double accelYData = accelYCluster.mData;
                            Log.i(LOG_TAG, "Accel Y: " + accelYData);
                        }
                        allFormats = objectCluster.getCollectionOfFormatClusters(SensorLIS2DW12.ObjectClusterSensorName.LIS2DW12_ACC_Z);
                        FormatCluster accelZCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(allFormats,"CAL"));
                        if (accelZCluster!=null) {
                            double accelZData = accelZCluster.mData;
                            Log.i(LOG_TAG, "Accel Z: " + accelZData);
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
                            isSpeedTestSensor1 = false;
                            isSpeedTestSensor2 = false;
                            isSpeedTestSensor3 = false;
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
        if (requestCode == REQUEST_CONNECT_SENSOR_1
                || requestCode == REQUEST_CONNECT_SENSOR_2
                || requestCode == REQUEST_CONNECT_SENSOR_3) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String macAdd = data.getStringExtra(EXTRA_DEVICE_ADDRESS);
                connectToSelectedDevice(requestCode, macAdd);
            }

        }
        super.onActivityResult(requestCode, resultCode, data);
    }


}