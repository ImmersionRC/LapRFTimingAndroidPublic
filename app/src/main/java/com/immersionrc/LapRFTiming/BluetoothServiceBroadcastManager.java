package com.immersionrc.LapRFTiming;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

/**
 * Created by urssc on 13-Apr-17.
 */
public class BluetoothServiceBroadcastManager {

    BluetoothServiceBroadcastReceiver mReceiver;
    Context mContext;
    StatusReceiver mStatusReceiver;

    public BluetoothServiceBroadcastManager(BluetoothServiceBroadcastReceiver receiver, Context context)
    {
        mReceiver  = receiver;
        mContext = context;
    }

    // Broadcast receiver for receiving status updates from the IntentService
    private class StatusReceiver extends BroadcastReceiver
    {
        // Prevents instantiation
        private StatusReceiver()
        {
        }

        // Called when the BroadcastReceiver gets an Intent it's registered to receive
        @Override
        public void onReceive(Context context, Intent intent)
        {

        /*
         * Handle Intents here.
         */
            if (intent.getAction() == protocolDecoder.Constants.BROADCAST_PROTOCOL_STATUS)
            {
                // show status of gate
                protocolDecoder.communicationStatus status = (protocolDecoder.communicationStatus) intent.getSerializableExtra(protocolDecoder.Constants.EXTENDED_DATA_PROTOCOL_STATUS);
                mReceiver.receivedStatus(status);
            }
            else if (intent.getAction() == BluetoothBackgroundService.Constants.BROADCAST_SERVICE_STATE)
            {
                // show progression of connection to a device
                String data = intent.getStringExtra(BluetoothBackgroundService.Constants.EXTENDED_DATA);
                mReceiver.receivedServiceState(data);
            }
            else if (intent.getAction() == BluetoothBackgroundService.Constants.BROADCAST_RACE_ACTIVE)
            {
                // show state of connection
                boolean data = intent.getBooleanExtra(BluetoothBackgroundService.Constants.EXTENDED_DATA, false);
                boolean active = intent.getBooleanExtra(BluetoothBackgroundService.Constants.EXTENDED_DATA_STATUS_RACE_ACTIVE, false);
                mReceiver.receivedRaceActiveStatus(active);
            }
            else if (intent.getAction() == BluetoothBackgroundService.Constants.BROADCAST_CONNECTED_STATUS)
            {
                // show state of connection
                boolean data = intent.getBooleanExtra(BluetoothBackgroundService.Constants.EXTENDED_DATA, false);
                int rssi = intent.getIntExtra(BluetoothBackgroundService.Constants.EXTENDED_DATA_STATUS_RSSI, 0);
                mReceiver.receivedConnectionStatus(data, rssi);
            }
            else if (intent.getAction() == protocolDecoder.Constants.BROADCAST_PROTOCOL_DETECTION)
            {
                // receive notification of new detection
                protocolDecoder.communicationDetection detection = (protocolDecoder.communicationDetection) intent.getSerializableExtra(protocolDecoder.Constants.EXTENDED_DATA_PROTOCOL_DETECTION);
                mReceiver.receivedDetection(detection);
            }
            else if (intent.getAction() == protocolDecoder.Constants.BROADCAST_PROTOCOL_RF_SETTINGS)
            {
                communicationRfSettings rf_settings = (communicationRfSettings) intent.getSerializableExtra(protocolDecoder.Constants.EXTENDED_DATA_PROTOCOL_RF_SETTINGS);
                mReceiver.receivedRFSettings(rf_settings);
            }
            else if (intent.getAction() == protocolDecoder.Constants.BROADCAST_PROTOCOL_SETTINGS)
            {
                extraSettings extra_settings = (extraSettings) intent.getSerializableExtra(protocolDecoder.Constants.EXTENDED_DATA_PROTOCOL_SETTINGS);
                mReceiver.receivedSettings(extra_settings);
            }
        }
    }

    public void subscribeToIntentReceiver()
    {
        // intent filter to receive notifications from background service
        IntentFilter statusIntentFilter = new IntentFilter( protocolDecoder.Constants.BROADCAST_PROTOCOL_DETECTION );
        statusIntentFilter.addAction(protocolDecoder.Constants.BROADCAST_PROTOCOL_STATUS);
        statusIntentFilter.addAction(BluetoothBackgroundService.Constants.BROADCAST_SERVICE_STATE );
        statusIntentFilter.addAction(BluetoothBackgroundService.Constants.BROADCAST_RACE_ACTIVE );
        statusIntentFilter.addAction(BluetoothBackgroundService.Constants.BROADCAST_CONNECTED_STATUS);
        statusIntentFilter.addAction(protocolDecoder.Constants.BROADCAST_PROTOCOL_RF_SETTINGS);
        statusIntentFilter.addAction(protocolDecoder.Constants.BROADCAST_PROTOCOL_SETTINGS);
        statusIntentFilter.addAction(protocolDecoder.Constants.BROADCAST_PROTOCOL_RSSI_STATS);
        mStatusReceiver = new StatusReceiver();
        // Registers the DownloadStateReceiver and its intent filters
        LocalBroadcastManager.getInstance(mContext).registerReceiver(
                mStatusReceiver,
                statusIntentFilter);

    }

    public void unsubscribe()
    {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mStatusReceiver);
    }
}
