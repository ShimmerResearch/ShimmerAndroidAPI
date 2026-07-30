package com.shimmerresearch.androidradiodriver;

import java.util.UUID;

public class Shimmer3RAndroidRadioByteCommunication extends VerisenseBleAndroidRadioByteCommunication{



    /**
     * Initialize a ble radio
     *
     * @param mac mac address of the verisense device e.g. d0:2b:46:3d:a2:bb
     */
    public Shimmer3RAndroidRadioByteCommunication(String mac) {
        super(mac);
        // Match the established Shimmer3R mapping used by Shimmer3BLEAndroid:
        // write to ...0101 (TX), notify from ...0102 (RX).
        TxID = "65333333-A115-11E2-9E9A-0800200CA101";
        RxID = "65333333-A115-11E2-9E9A-0800200CA102";
        ServiceID = "65333333-A115-11E2-9E9A-0800200CA100";
        sid = UUID.fromString(ServiceID);
        txid = UUID.fromString(TxID);
        rxid = UUID.fromString(RxID);
    }
}
