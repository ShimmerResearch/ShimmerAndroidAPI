package com.shimmerresearch.shimmerspeedtest;

import static com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog.EXTRA_DEVICE_ADDRESS;
import static com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog.EXTRA_DEVICE_NAME;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.widget.TextView;
import android.widget.Toast;

import android.util.Log;

import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog;
import com.shimmerresearch.androidradiodriver.Shimmer3BleAndroidRadioByteCommunication;
import com.shimmerresearch.androidradiodriver.Shimmer3RAndroidRadioByteCommunication;
import com.shimmerresearch.androidradiodriver.VerisenseBleAndroidRadioByteCommunication;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.shimmer3.communication.SpeedTestProtocol;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FirstFragment extends Fragment {
    private static final String LOG_TAG = "ShimmerSpeedTest";
    SpeedTestProtocol protocol;
    Shimmer shimmer;
    TextView tv;
    final static int REQUEST_CONNECT_SHIMMER = 2;
    volatile long time = 0;

    /**
     * Null-safe patched replacement for SpeedTestProtocol's static mQ field.
     * SpeedTestProtocol$ProcessingThread calls mQ.size() then polls that many
     * times without holding a lock — a TOCTOU race can make poll() return null
     * and crash with NPE (SpeedTestProtocol.java:127).
     * We replace the static mQ with this subclass so poll() never returns null.
     */
    private static ConcurrentLinkedQueue<Byte> patchedQueue = null;

    /**
     * Applies a one-time null-safety patch to SpeedTestProtocol's static mQ
     * queue field using reflection, then clears any stale data accumulated from
     * previous connections / orphaned ProcessingThread instances.
     */
    private void patchAndClearSpeedTestQueue() {
        if (patchedQueue != null) {
            // Already patched — just drain stale data so old ProcessingThreads
            // that may still be spinning don't interfere with the new run.
            patchedQueue.clear();
            Log.d(LOG_TAG, "Cleared stale data from SpeedTestProtocol mQ");
            return;
        }
        try {
            Field mQField = SpeedTestProtocol.class.getDeclaredField("mQ");
            mQField.setAccessible(true);

            // Drain the existing queue first
            @SuppressWarnings("unchecked")
            ConcurrentLinkedQueue<Byte> existing =
                    (ConcurrentLinkedQueue<Byte>) mQField.get(null);
            if (existing != null) {
                existing.clear();
            }

            // Replace with a null-safe subclass: poll() returns 0x00 instead of
            // null so ProcessingThread never calls byteValue() on null.
            patchedQueue = new ConcurrentLinkedQueue<Byte>() {
                @Override
                public Byte poll() {
                    Byte b = super.poll();
                    return b != null ? b : (byte) 0x00;
                }
            };
            mQField.set(null, patchedQueue);
            Log.d(LOG_TAG, "SpeedTestProtocol null-safety patch applied (mQ replaced)");

        } catch (Exception e) {
            // Reflection blocked (e.g. strict hidden-API policy on some ROMs).
            // Fall back to the UncaughtExceptionHandler installed below.
            Log.w(LOG_TAG, "SpeedTestProtocol patch via reflection failed: " + e.getMessage()
                    + " — relying on UncaughtExceptionHandler fallback");
            patchedQueue = null;
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_first, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Fallback safety net: if the reflection patch didn't work and
        // ProcessingThread still crashes, prevent the whole app from dying.
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            boolean isProcessingThreadNpe =
                    throwable instanceof NullPointerException
                    && throwable.getStackTrace().length > 0
                    && throwable.getStackTrace()[0].getClassName()
                            .contains("SpeedTestProtocol");
            if (isProcessingThreadNpe) {
                Log.e(LOG_TAG, "SpeedTestProtocol ProcessingThread NPE caught"
                        + " (library bug) — preventing app death", throwable);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireActivity(),
                                    "Speed test interrupted — please press Start again",
                                    Toast.LENGTH_LONG).show());
                }
                // Do NOT forward to defaultHandler — that would kill the process.
            } else {
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
            }
        });

        view.findViewById(R.id.button_first).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent pairedDevicesIntent = new Intent(getActivity().getApplicationContext(), ShimmerBluetoothDialog.class);
                startActivityForResult(pairedDevicesIntent, REQUEST_CONNECT_SHIMMER);
            }
        });

        view.findViewById(R.id.button_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (protocol == null) {
                    Toast.makeText(getActivity().getApplicationContext(), "Please connect to a device first", Toast.LENGTH_SHORT).show();
                    return;
                }
                Thread thread = new Thread() {
                    public void run() {
                        // Patch the library's static queue and clear stale data
                        // before creating a new ProcessingThread.
                        patchAndClearSpeedTestQueue();
                        Log.d(LOG_TAG, "startSpeedTest called");
                        protocol.startSpeedTest();
                        Log.d(LOG_TAG, "startSpeedTest returned");
                    }
                };
                thread.start();
            }
        });

        view.findViewById(R.id.button_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (protocol == null) {
                    Toast.makeText(getActivity().getApplicationContext(), "Please connect to a device first", Toast.LENGTH_SHORT).show();
                    return;
                }
                Thread thread = new Thread() {
                    public void run() {
                        Log.d(LOG_TAG, "stopSpeedTest called");
                        protocol.stopSpeedTest();
                        Log.d(LOG_TAG, "stopSpeedTest returned");
                    }
                };
                thread.start();
            }
        });

        view.findViewById(R.id.button_dc).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Thread thread = new Thread(){
                    public void run(){

                        if (protocol != null){
                            try {
                                protocol.disconnect();
                            } catch (ShimmerException e) {
                                e.printStackTrace();
                            }
                        }
                        if (shimmer != null) {
                            shimmer.disconnect();
                        }
                    }
                };

                thread.start();
            }
        });

        tv = view.findViewById(R.id.textView);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2) { //The devices paired list has returned a result
            if (resultCode == Activity.RESULT_OK) {
                //Get the Bluetooth mac address of the selected device:
                String macAdd = data.getStringExtra(EXTRA_DEVICE_ADDRESS);
                String deviceName = data.getStringExtra(EXTRA_DEVICE_NAME);
                shimmer = new Shimmer(mHandler,getActivity().getApplicationContext());
                VerisenseBleAndroidRadioByteCommunication port = null;
                if (deviceName.toUpperCase().contains("SHIMMER3-")) {
                    port = new Shimmer3BleAndroidRadioByteCommunication(macAdd);
                } else if (deviceName.toUpperCase().contains("SHIMMER3R-")){
                    port = new Shimmer3RAndroidRadioByteCommunication(macAdd);
                } else {
                    port = new VerisenseBleAndroidRadioByteCommunication(macAdd);
                }

                protocol = new SpeedTestProtocol(port);

                protocol.setListener(new SpeedTestProtocol.SpeedTestResult() {
                    @Override
                    public void onNewResult(String s) {
                        Log.d(LOG_TAG, "onNewResult: " + s);
                        if (!isAdded()) return;
                        long now = System.currentTimeMillis();
                        if ((now - time) > 1000) {
                            time = now;
                            requireActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tv.setText(s);
                                }
                            });
                        }
                    }

                    @Override
                    public void onConnected() {
                        Log.d(LOG_TAG, "onConnected");
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(requireActivity().getApplicationContext(), "Device Connected", Toast.LENGTH_LONG).show();
                                tv.setText("Device Connected");
                            }
                        });
                    }

                    @Override
                    public void onDisconnected() {
                        Log.d(LOG_TAG, "onDisconnected");
                        if (!isAdded()) return;
                        protocol = null;
                        requireActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(requireActivity().getApplicationContext(), "Device Disconnected", Toast.LENGTH_LONG).show();
                                tv.setText("Device Disconnected");
                            }
                        });
                    }
                });

                Thread connectThread = new Thread() {
                    public void run() {
                        try {
                            Log.d(LOG_TAG, "Connecting to: " + macAdd);
                            protocol.connect();
                            Log.d(LOG_TAG, "connect() returned");
                        } catch (ShimmerException e) {
                            Log.e(LOG_TAG, "connect() exception: " + e.getMessage(), e);
                        }
                    }
                };
                connectThread.start();
            }
        }
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
                    }
                    break;
                case Shimmer.MESSAGE_TOAST:
                case ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE:
                    ShimmerBluetooth.BT_STATE state = null;
                    if (msg.obj instanceof ObjectCluster) {
                        state = ((ObjectCluster) msg.obj).mState;
                    } else if (msg.obj instanceof CallbackObject) {
                        state = ((CallbackObject) msg.obj).mState;
                    }

                    if (state != null) {
                        switch (state) {
                            case CONNECTED:
                                break;
                            case CONNECTING:
                                break;
                            case STREAMING:
                            case STREAMING_AND_SDLOGGING:
                                break;
                            case SDLOGGING:
                                break;
                            case DISCONNECTED:
                                Toast.makeText(getActivity().getApplicationContext(), "Device Disconnected", Toast.LENGTH_LONG).show();
                                tv.setText("Device Disconnected");
                                break;
                        }
                    }
                    break;
            }
            super.handleMessage(msg);
        }
    };
}