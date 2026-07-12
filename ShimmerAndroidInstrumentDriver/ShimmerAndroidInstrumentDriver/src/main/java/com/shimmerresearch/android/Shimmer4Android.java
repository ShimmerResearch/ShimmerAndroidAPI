package com.shimmerresearch.android;

import android.os.Handler;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.shimmer4sdk.Shimmer4sdk;

/**
 * Created by Lim on 06/06/2016.
 */

public class Shimmer4Android extends Shimmer4sdk {

    transient public final Handler mHandler;

    @Override
    public void sendCallBackMsg(int msgid,Object obj){
        if (mHandler != null) {
            mHandler.obtainMessage(msgid, obj).sendToTarget();
        }
    }

    public Shimmer4Android(Handler handler){
        mHandler = handler;
    }

}
