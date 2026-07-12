package com.shimmerresearch.android;

import android.bluetooth.BluetoothAdapter;
import android.os.Handler;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.verisense.VerisenseDevice;
import com.shimmerresearch.verisense.communication.SyncProgressDetails;

import java.util.ArrayList;
import java.util.List;

public class VerisenseDeviceAndroid extends VerisenseDevice {
    transient List<Handler> mHandlerList = new ArrayList<Handler>();

    public VerisenseDeviceAndroid(Handler handler) {
        super();
        mHandlerList.add(0, handler);
    }

    private void sendMsgToHandlerListTarget(int what, int arg1, int arg2, Object object) {
        for(Handler handler : mHandlerList) {
            if (handler!=null) {
                handler.obtainMessage(what, arg1, arg2, object).sendToTarget();
            }
        }
    }

    @Override
    protected void dataHandler(ObjectCluster ojc){
        sendMsgToHandlerListTarget(ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET, ojc);
    }

    private void sendMsgToHandlerListTarget(int what, Object object) {
        for(Handler handler : mHandlerList) {
            if (handler!=null) {
                handler.obtainMessage(what, object).sendToTarget();
            }
        }
    }

    @Override
    public void sendCallBackMsg(int i, Object ojc){
        super.sendCallBackMsg(i, ojc);
        if(i == ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE && ojc instanceof CallbackObject){
            sendMsgToHandlerListTarget(i, -1, -1,
                    new ObjectCluster(mShimmerUserAssignedName, getMacId(), ((CallbackObject)ojc).mState));
        }
        else{
            sendMsgToHandlerListTarget(i, -1, -1, ojc);
        }
    }
}
