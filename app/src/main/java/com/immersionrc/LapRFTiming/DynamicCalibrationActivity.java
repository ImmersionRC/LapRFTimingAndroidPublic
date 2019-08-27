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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.TextView;

public class DynamicCalibrationActivity extends AppCompatActivity {

    // Broadcast receiver for receiving status updates from the IntentService
    private class CalibrationReceiver extends BroadcastReceiver
    {
        // Prevents instantiation
        private CalibrationReceiver() {

        }
        // Called when the BroadcastReceiver gets an Intent it's registered to receive
        @Override
        public void onReceive(Context context, Intent intent) {

            /*
             * Handle Intents here.
             */
            if (intent.getAction() == protocolDecoder.Constants.BROADCAST_PROTOCOL_STATUS)
            {
                protocolDecoder.communicationStatus status = (protocolDecoder.communicationStatus)intent.getSerializableExtra(protocolDecoder.Constants.EXTENDED_DATA_PROTOCOL_STATUS);

                if (status != null) {
                    TextView textView = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.dynamicCalibrationStatusTextBox);

                    if (status.connection == 0) {
                        textView.setBackgroundColor(Color.GREEN);
                    } else if (status.connection == -1) {
                        textView.setBackgroundColor(Color.RED);
                    } else {
                        textView.setBackgroundColor(Color.rgb(0x00, 0xDD, 0x00));
                    }
                }
            }
            else if (intent.getAction() == protocolDecoder.Constants.BROADCAST_PROTOCOL_CALIBRATION_LOG)
            {
                protocolDecoder.communicationCalibrationLog calibration_log = (protocolDecoder.communicationCalibrationLog)intent.getSerializableExtra(protocolDecoder.Constants.EXTENDED_DATA_PROTOCOL_CALIBRATION_LOG);

                TextView textView = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.calibrationLog);
                textView.append(      "P: "  + calibration_log.pilot
                                    + " t: " + calibration_log.time
                                    + " h: " + calibration_log.height
                                    + " np: " + calibration_log.numPeak
                                    + " b: " + calibration_log.baseLevel
                                    + "\r\n" );
            }
        }
    }
    CalibrationReceiver mCalibrationReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.immersionrc.LapRFTiming.R.layout.activity_calibration);
        Toolbar toolbar = (Toolbar) findViewById(com.immersionrc.LapRFTiming.R.id.toolbar);
        setSupportActionBar(toolbar);

        IntentFilter statusIntentFilter = new IntentFilter(        protocolDecoder.Constants.BROADCAST_PROTOCOL_DETECTION);
        statusIntentFilter.addAction(protocolDecoder.Constants.BROADCAST_PROTOCOL_STATUS);
        statusIntentFilter.addAction(BluetoothBackgroundService.Constants.BROADCAST_CONNECTED_STATUS);
        statusIntentFilter.addAction(protocolDecoder.Constants.BROADCAST_PROTOCOL_CALIBRATION_LOG);

        TextView textView = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.calibrationLog);
        textView.setMovementMethod(new ScrollingMovementMethod());

        mCalibrationReceiver = new CalibrationReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mCalibrationReceiver,
                statusIntentFilter);

        Intent intent = new Intent(this, BluetoothBackgroundService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public void startCalibration(View view)
    {
        TextView textView = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.calibrationLog);
        textView.setText("");
        mService.sendStartCalibration(true);
    }

    public void copyToClipboard(View view)
    {
        TextView textView = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.calibrationLog);
        CharSequence seq = textView.getText();

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Calibration", seq);

        clipboard.setPrimaryClip(clip);
    }

    BluetoothBackgroundService mService;
    boolean mBound;
    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothBackgroundService.LocalBinder binder = (BluetoothBackgroundService.LocalBinder) service;
            mService = binder.getService();
            mService.referenceCount(1);

            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

}
