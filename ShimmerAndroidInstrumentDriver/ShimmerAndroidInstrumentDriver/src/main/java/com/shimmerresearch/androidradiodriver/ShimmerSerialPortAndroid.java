package com.shimmerresearch.androidradiodriver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import android.widget.Toast;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.comms.serialPortInterface.AbstractSerialPortHal;
import com.shimmerresearch.comms.serialPortInterface.ErrorCodesSerialPort;
import com.shimmerresearch.comms.serialPortInterface.SerialPortListener;


import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by ASaez on 11-Aug-16.
 */

public class ShimmerSerialPortAndroid extends AbstractSerialPortHal {

    //generic UUID for serial port protocol
    private UUID mSPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public ShimmerBluetooth.BT_STATE mState = ShimmerBluetooth.BT_STATE.DISCONNECTED;

    public String mBluetoothAddress = "";
    transient private BluetoothAdapter mBluetoothAdapter;
    transient  private BluetoothDevice device;
    transient private BluetoothSocket mBluetoothSocket;
    transient private DataInputStream mInStream;
    transient private OutputStream mOutStream;
    private transient SerialPortListener mShimmerSerialEventCallback;
    private boolean mUseListenerThread;
    private volatile boolean mKeepListening;
    private transient Thread mListeningThread;

    public ShimmerSerialPortAndroid(String bluetoothAddress){
        mBluetoothAddress = bluetoothAddress;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public ShimmerSerialPortAndroid(String bluetoothAddress, boolean useListenerThread){
        mBluetoothAddress = bluetoothAddress;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mUseListenerThread = useListenerThread;
    }

    @Override
    public void connect(){
        System.out.println("initialize process shimmer serial port android");
        if (mState == ShimmerBluetooth.BT_STATE.DISCONNECTED) {

            createBluetoothSocket();
            connectBluetoothSocket();

            if(mBluetoothSocket!=null) {
                boolean isStream = getIOStreams();
                if(isStream) {
                    mState = ShimmerBluetooth.BT_STATE.CONNECTED;
                    eventDeviceConnected();
                }
                else{
                    eventDeviceDisconnected();
                    Log.e("Shimmer", "Could not get IO Stream!");
                    NullPointerException e = new NullPointerException();
                    catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTON_OPENING);
                }
            }
            else {
                eventDeviceDisconnected();
                Log.e("Shimmer", "Shimmer device is not online!");
                NullPointerException e = new NullPointerException();
                catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTION);
            }
        }
    }

    private void createBluetoothSocket() {
        try {
            device = mBluetoothAdapter.getRemoteDevice(mBluetoothAddress);
            //mBluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(mSPP_UUID);
            mBluetoothSocket = device.createRfcommSocketToServiceRecord(mSPP_UUID);// If your device fails to pair try: device.createInsecureRfcommSocketToServiceRecord(mSPP_UUID)
        } catch (IOException e) {
            catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTON_OPENING);
        } catch (SecurityException e) {
            catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTON_OPENING);
        }
    }

    private void connectBluetoothSocket(){
        try {
            mBluetoothSocket.connect();
        } catch (IOException e) {
            closeBluetoothSocket();
            catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTON_OPENING);
        } catch (SecurityException e) {
            closeBluetoothSocket();
            catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTON_OPENING);
        }
    }

    private boolean getIOStreams(){
        try {
            InputStream tmpIn = mBluetoothSocket.getInputStream();
            mInStream =  new DataInputStream(tmpIn);
            mOutStream = mBluetoothSocket.getOutputStream();
            if(mUseListenerThread) {
                startListening();
            }
        } catch (IOException e) {
            catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTON_OPENING);
            return false;
        }
        return true;
    }

    private void closeBluetoothSocket(){
        try {
            if(mBluetoothSocket != null) {
                mBluetoothSocket.close();
            }
        } catch (IOException e) {
            catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTON_CLOSING);
        }
        finally {
            mBluetoothSocket = null;
        }
    }

    private void closeInputStream(){
        try {
            if(mInStream != null) {
                mInStream.close();
            }
        }
        catch (IOException e){
            catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTON_CLOSING);
        }
        finally {
            mInStream = null;
        }
    }



    private void closeOutputStream(){
        try {
            if(mOutStream != null) {
                mOutStream.close();
            }
        }
        catch (IOException e){
            catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTON_CLOSING);
        }
        finally {
            mOutStream = null;
        }
    }


    @Override
    public void disconnect()  {
        closeBTConnection();
        eventDeviceDisconnected();
    }

    @Override
    public void closeSafely()  {
        closeBTConnection();
        eventDeviceDisconnected();
    }

    private void closeBTConnection()  {
        mKeepListening = false;
        if (mListeningThread != null) {
            mListeningThread.interrupt();
            mListeningThread = null;
        }
        closeInputStream();
        closeOutputStream();
        closeBluetoothSocket();
        mState = ShimmerBluetooth.BT_STATE.DISCONNECTED;
    }

    @Override
    public void clearSerialPortRxBuffer()  {
//        try {
//            mInStream.skipBytes(mInStream.available());
//        } catch (IOException e) {
//            catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_READING_DATA);
//        }
    }

    @Override
    public void txBytes(byte[] bytes)  {

        synchronized (this) {
            if (mState == ShimmerBluetooth.BT_STATE.DISCONNECTED ) return;
        }
        // Perform the write unsynchronized
        write(bytes);
    }

    private void write(byte[] buffer)  {
        try {
            mOutStream.write(buffer);
        } catch (IOException e) {
            catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_WRITING_DATA);
        }
    }

    @Override
    public byte[] rxBytes(int numBytes){
        byte[] buffer = new byte[numBytes];
        if (mInStream!=null) {
            try {
                mInStream.readFully(buffer, 0, numBytes);
                return (buffer);
            } catch (IOException e) {
                catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_READING_DATA);
            }
        }
        return null;
    }


    @Override
    public boolean isSerialPortReaderStarted() {
        return mInStream != null ? true : false;
    }

    @Override
    public void setVerboseMode(boolean b, boolean b1) {

    }

    @Override
    public boolean bytesAvailableToBeRead()  {
        try {
            if (mInStream!=null){
                return mInStream.available() !=0 ? true : false;
            } else {
                System.out.println("IN STREAM NULL");
                return false;
            }
        } catch (IOException e) {
            catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_READING_DATA);
            return false;
        }
    }

    @Override
    public int availableBytes()  {
        try {
            return mInStream.available();
        } catch (IOException e) {
            catchException(e, ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_READING_DATA);
            return 0;
        }
    }

    @Override
    public boolean isConnected() {
        if (mBluetoothSocket!=null){
            return mBluetoothSocket.isConnected();
        }
        return false;
    }

    @Override
    public boolean isDisonnected() {
        return !isConnected();
    }

    @Override
    public void registerSerialPortRxEventCallback(SerialPortListener serialPortListener) {
        mShimmerSerialEventCallback = serialPortListener;
    }

    private void catchException(Exception e, int errorCode)  {
        Log.e("Shimmer", "Serial port error, code: " + errorCode, e);
        e.printStackTrace();
        eventDeviceDisconnected();

    }


    public void startListening() {
        mKeepListening = true;
        mListeningThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (mKeepListening) {
                        // Read data from the input stream
                        InputStream inStream = mInStream;
                        if (inStream == null) {
                            break;
                        }

                        int availableBytes = inStream.available();

                        if (availableBytes < 1) {
                            // No data available, yield briefly before polling again
                            Thread.sleep(5);
                        } else {
                            // Notify listeners with the read data
                            mShimmerSerialEventCallback.serialPortRxEvent(availableBytes);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            }
        });
        mListeningThread.start();
    }

    public BluetoothSocket getBluetoothSocket(){
        return mBluetoothSocket;
    }


}
