// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
package com.immersionrc.LapRFTiming;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.TextView;

/**
 * Created by urssc on 13-Apr-17.
 *
 * this class wraps reception of broadcast from bluetooth service
 */
public interface BluetoothServiceBroadcastReceiver
{
    // Override these
    public void receivedStatus(protocolDecoder.communicationStatus status );
    public void receivedServiceState(String state);
    public void receivedRaceActiveStatus(boolean bIsActive);
    public void receivedConnectionStatus(boolean connected, int rssi);
    public void receivedDetection(protocolDecoder.communicationDetection detection);
    public void receivedRFSettings(communicationRfSettings rf_settings);
    public void receivedSettings(extraSettings extra_settings);
}

