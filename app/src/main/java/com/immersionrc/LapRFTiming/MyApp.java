// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
package com.immersionrc.LapRFTiming;

import android.app.Application;
import android.content.Intent;

/**
 * Created by nab on 06/12/16.
 */

public class MyApp extends Application
{
    @Override
    public void onCreate()
    {
        super.onCreate();

        // start the background service which handles keeping BT alive, reading from the gate
        // this is the only part of the app. which runs when the main window ('activity') is
        // closed.
        startService(new Intent(this, BluetoothBackgroundService.class));
    }
}
