package com.movesense.samples.dataloggersample;

import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanResult;

/**
 * Created by Lipponep on 26.9.2017.
 */

class MyScanResult {
    public int rssi;
    public String macAddress;
    public String name;
    public String connectedSerial;
    public MyScanResult(ScanResult scanResult) {
        this.macAddress = scanResult.getBleDevice().getMacAddress();
        this.rssi = scanResult.getRssi();
        this.name = scanResult.getBleDevice().getName();
    }

    public boolean isConnected() {return connectedSerial != null;}
    public void markConnected(String serial) {connectedSerial = serial;}
    public void markDisconnected() {connectedSerial = null;}

    public boolean equals(Object object) {
        if(object instanceof MyScanResult && ((MyScanResult)object).macAddress.equals(this.macAddress)) {
            return true;
        }
        else if(object instanceof RxBleDevice && ((RxBleDevice)object).getMacAddress().equals(this.macAddress)) {
            return true;
        } else {
            return false;
        }
    }

    public String toString() {
        return (isConnected()?"*** ":"") + macAddress + " - " + name + " [" + rssi + "]" + (isConnected()?" ***":"");
    }
}
