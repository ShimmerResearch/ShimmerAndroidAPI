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
import java.util.Collection;

public class MainActivity extends AppCompatActivity {

    private final static String LOG_TAG = "MultiVeriBLEExample";
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
            BleManager.getInstance().init(getApplication());
            device1 = new VerisenseDeviceAndroid(mHandler);
            device2 = new VerisenseDeviceAndroid(mHandler);
            device3 = new VerisenseDeviceAndroid(mHandler);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 110){
            BleManager.getInstance().init(getApplication());
            device1 = new VerisenseDeviceAndroid(mHandler);
            device2 = new VerisenseDeviceAndroid(mHandler);
            device3 = new VerisenseDeviceAndroid(mHandler);
        }
    }

    //Sensor 1
    public void connectDevice1(View v) {

        Thread thread = new Thread(){
            public void run(){

                device1.setProtocol(Configuration.COMMUNICATION_TYPE.BLUETOOTH, protocol1);
                try {
                    device1.connect();
                } catch (ShimmerException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }

            }
        };

        thread.start();
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

        Thread thread = new Thread(){
            public void run(){

                device2.setProtocol(Configuration.COMMUNICATION_TYPE.BLUETOOTH, protocol2);
                try {
                    device2.connect();
                } catch (ShimmerException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }

            }
        };

        thread.start();
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

        Thread thread = new Thread(){
            public void run(){

                device3.setProtocol(Configuration.COMMUNICATION_TYPE.BLUETOOTH, protocol3);
                try {
                    device3.connect();
                } catch (ShimmerException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }

            }
        };

        thread.start();
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
        if(requestCode == 2) {
            if (resultCode == Activity.RESULT_OK) {
                //Get the Bluetooth mac address of the selected device:
                String macAdd = data.getStringExtra(EXTRA_DEVICE_ADDRESS);

            }

        }
        super.onActivityResult(requestCode, resultCode, data);
    }


}