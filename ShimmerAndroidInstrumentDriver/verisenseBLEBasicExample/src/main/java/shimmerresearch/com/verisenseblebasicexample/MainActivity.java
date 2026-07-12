package shimmerresearch.com.verisenseblebasicexample;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.android.VerisenseDeviceAndroid;
import com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog;
import com.shimmerresearch.androidradiodriver.VerisenseBleAndroidRadioByteCommunication;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.exceptions.ShimmerException;

import com.shimmerresearch.verisense.communication.VerisenseProtocolByteCommunication;
import com.clj.fastble.BleManager;
import com.shimmerresearch.verisense.sensors.SensorLIS2DW12;

import java.io.IOException;
import java.util.Collection;

import static com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog.EXTRA_DEVICE_ADDRESS;
import static com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog.REQUEST_CONNECT_SHIMMER;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private final static String LOG_TAG = "VeriBLEBasicExample";
    String macAddress = "C0:04:19:85:9A:D5";

    VerisenseBleAndroidRadioByteCommunication radio1 = new VerisenseBleAndroidRadioByteCommunication(macAddress);
    //AndroidBleRadioByteCommunication radio1 = new AndroidBleRadioByteCommunication("C9:61:17:53:74:02");
    VerisenseProtocolByteCommunication protocol1 = new VerisenseProtocolByteCommunication(radio1);
    VerisenseDeviceAndroid device1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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

        }

    }

    public void connectDevice(View v) {

        Intent pairedDevicesIntent = new Intent(this.getApplicationContext(), ShimmerBluetoothDialog.class);
        startActivityForResult(pairedDevicesIntent, REQUEST_CONNECT_SHIMMER);

    }

    public void disconnectDevice(View v) {
        Thread thread = new Thread(){
            public void run(){
                try {
                    device1.disconnect();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
            }
        };

        thread.start();


    }

    public void readOpConfig(View v) {
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

    public void readProdConfig(View v)  {
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

    public void startStreaming(View v) throws InterruptedException, IOException, ShimmerException {
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

    public void stopStreaming(View v) throws IOException, ShimmerException {
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
    public void startSpeedTest(View v) throws IOException, ShimmerException {
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
    public void stopSpeedTest(View v) throws IOException, ShimmerException {
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


    /**
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
        if(requestCode == REQUEST_CONNECT_SHIMMER) {
            if (resultCode == Activity.RESULT_OK) {
                BleManager.getInstance().init(getApplication());
                device1 = new VerisenseDeviceAndroid(mHandler);
                //Get the Bluetooth mac address of the selected device:
                String macAdd = data.getStringExtra(EXTRA_DEVICE_ADDRESS);
                radio1 = new VerisenseBleAndroidRadioByteCommunication(macAdd);
                protocol1 = new VerisenseProtocolByteCommunication(radio1);

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

        }
        super.onActivityResult(requestCode, resultCode, data);
    }


}
