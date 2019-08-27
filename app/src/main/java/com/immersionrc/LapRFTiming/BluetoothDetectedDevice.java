// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
package com.immersionrc.LapRFTiming;

import android.bluetooth.BluetoothDevice;

/**
 * Created by urssc on 28-Mar-17.
 */

public class BluetoothDetectedDevice {
    public String deviceName;
    public int deviceIndex;
    public String deviceMAC;
    public BluetoothDevice device;

    public BluetoothDetectedDevice(String deviceName, int deviceIndex, String deviceMAC, BluetoothDevice device)
    {
        this.deviceName = deviceName;
        this.deviceIndex = deviceIndex;
        this.deviceMAC = deviceMAC;
        this.device = device;
    }
}