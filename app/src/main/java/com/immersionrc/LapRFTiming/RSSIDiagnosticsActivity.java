// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
package com.immersionrc.LapRFTiming;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.ListView;
import android.widget.TextView;

/**
 * RSSI Diagnostic page, includes progress bars showing the RSSI level of each pilot slot
 *
 */

public class RSSIDiagnosticsActivity
        extends AppCompatActivity
        implements BluetoothServiceBroadcastReceiver
{
    public Handler mHandler;

    public final int numSlots = lapRFConstants.numPilotSlots;

    // Manager to abstract broadcast receiver for receiving data from the bluetooth Service
    BluetoothServiceBroadcastManager mBluetoothServiceManager;

    SlotSettingsAdapter slotsettingsadapter;

    // adapter to display each row of the slot list (each containing on/off, band, channel, etc.)
    public class SlotSettingsAdapter extends BaseAdapter {
        communicationRfSettings slots[] = new communicationRfSettings[numSlots];
        public boolean slot_active[] = new boolean[numSlots];

        float slots_rssi[] = new float[numSlots];

        private LayoutInflater mInflater;

        //----------------------------------------------------------------------------------------------------------------------------------------------
        public SlotSettingsAdapter() {
            for (int i = 0; i < numSlots; i++)
            {
                slots[i] = new communicationRfSettings();

                slots[i].slotid = (byte)(i + 1);
                slots_rssi[i] = 0.0f;
            }

            mInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        //----------------------------------------------------------------------------------------------------------------------------------------------
        public void setRssi(byte slotid, float rssi)
        {
            if ( slots_rssi[slotid - 1] != rssi )
            {
                slots_rssi[slotid - 1] = rssi;
                updateRequest();
            }
        }

        // NOTE: These next two methods, getViewTypeCount and getItemViewType, disable view recycling
        // ESSENTIAL at the moment since this view doesn't deal with cases where not all items are visible
        // in the list well.
        @Override
        public int getViewTypeCount() {

            return getCount();
        }

        @Override
        public int getItemViewType(int position) {

            return position;
        }

        @Override
        public int getCount() {
            return numSlots;
        }

        //----------------------------------------------------------------------------------------------------------------------------------------------
        @Override
        public communicationRfSettings getItem(int position) {
            if (position >= 0 && position < numSlots )
            {
                return slots[position];
            }
            else
                return null;
        }

        //----------------------------------------------------------------------------------------------------------------------------------------------
        @Override
        public long getItemId(int position) {
            if (position >= 0 && position < numSlots )
            {
                return slots[position].slotid;
            }
            else
                return 0;
        }

        public void updateRequest()
        {
            notifyDataSetChanged();
        }

        //----------------------------------------------------------------------------------------------------------------------------------------------
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            // Get the data item for this position
            final communicationRfSettings slot = getItem(position);

            // Check if an existing view is being reused, otherwise inflate the view

            if (convertView == null)
            {
                convertView = mInflater.inflate(R.layout.rssi_diagnostics_row, parent, false);
            }

            if (slot == null)
                return null;

            // grab the rssi value and set the progress bar position
            //
            Integer rssiVal = (int)slots_rssi[slot.slotid - 1];
            ProgressBar pgRssiBar = (ProgressBar) convertView.findViewById(R.id.rssi_progress_bar);
            pgRssiBar.setProgress(rssiVal);

            // set the textual version of the progress bar value
            //
            TextView pgRssiValue = (TextView) convertView.findViewById(R.id.textViewRSSI);
            pgRssiValue.setText(rssiVal.toString());

            // set the bold Slot ID number on the left of the row
            TextView slotNumber = (TextView) convertView.findViewById(R.id.slotIDText);
            slotNumber.setText(Integer.toString(slot.slotid));

            // handle the graying of controls
            boolean current_slot_active = slot_active[slot.slotid - 1];

            // Return the completed view to render on screen
            return convertView;
        }
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rssi_diagnostics);
//        Toolbar toolbar = (Toolbar) findViewById(com.immersionrc.LapRFTiming.R.id.toolbar);
//        setSupportActionBar(toolbar);

        ListView rssiDiagnosticsListView = (ListView) findViewById(R.id.rssiDiagnosticsListView);
        slotsettingsadapter = new SlotSettingsAdapter();

        rssiDiagnosticsListView.setAdapter(slotsettingsadapter);

        slotsettingsadapter.updateRequest();

        mBluetoothServiceManager = new BluetoothServiceBroadcastManager(this, this);
        mBluetoothServiceManager.subscribeToIntentReceiver();

        // why on earth are each of these activities re-establishing the connection to the
        // BT background service? Surely we want one connection, and maintain it centrally?
        //
        Intent intent = new Intent(this, BluetoothBackgroundService.class);
        /*if(mConnection != null) {
            unbindService(mConnection);
            mConnection = null;
        }*/
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }


    //----------------------------------------------------------------------------------------------------------------------------------------------
    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if ( mService != null ) {
            mService.sendUpdatePeriod(2000);

            if (mBound)
            {
                mService.referenceCount(-1);
                unbindService(mConnection);
                mBound = false;
            }
        }
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    @Override
    protected void onActivityResult (int requestCode,
                                     int resultCode,
                                     Intent data)
    {

    }

    public void receivedServiceState(String state){};
    public void receivedConnectionStatus(boolean connected, int rssi){};
    public void receivedDetection(protocolDecoder.communicationDetection detection){};
    public void receivedRaceActiveStatus(boolean bIsActive) {}

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void receivedStatus(protocolDecoder.communicationStatus status )
    {
        Log.d("RSSIDiagnosticsActivity", "receivedStatus (rssi)");
        if (status != null)
        {
            for(int i = 0; i < numSlots; i++)
            {
                slotsettingsadapter.setRssi((byte) (i + 1), status.base[i]);
            }
        }
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void receivedRFSettings(communicationRfSettings rf_settings)
    {
        int slotidx = rf_settings.slotid - 1;
        communicationRfSettings rfSettings = slotsettingsadapter.getItem(slotidx);

        rfSettings.slotid = rf_settings.slotid;

        slotsettingsadapter.updateRequest();
    }

    public void receivedSettings(extraSettings extra_settings) {}

    //----------------------------------------------------------------------------------------------------------------------------------------------
    BluetoothBackgroundService mService;
    boolean mBound;
    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothBackgroundService.LocalBinder binder = (BluetoothBackgroundService.LocalBinder) service;
            mService = binder.getService();
            mService.referenceCount(1);

            mBound = true;

            mService.sendUpdatePeriod(100);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

}