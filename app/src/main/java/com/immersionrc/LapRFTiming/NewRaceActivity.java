// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
// NewRaceActivity: Second generation race results activity
//
//---------------------------------------------------------------------------------------------------------------------

package com.immersionrc.LapRFTiming;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.IdRes;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Random;
import java.util.Vector;

import static java.lang.Math.abs;

public class NewRaceActivity
        extends AppCompatActivity implements BluetoothServiceBroadcastReceiver, RaceTimerFragment.OnRaceTimeFragmentInteractionListener,  BluetoothConnectivityFragment.OnBluetoothFragmentInteractionListener
{
    public Handler mHandler;

    public final int numSlots = lapRFConstants.numPilotSlots;
    int lapIndexInLeftColumn = 1;

    // Manager to abstract broadcast receiver for receiving data from the bluetooth Service
    BluetoothServiceBroadcastManager mBluetoothServiceManager;

    RacerRowAdapter RacerRowAdapter;

    // adapter to display each row of the table
    //
    public class RacerRowAdapter extends BaseAdapter 
    {
        private LayoutInflater mInflater;

        public RacerRowAdapter()
        {
            mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        // NOTE: These next two methods, getViewTypeCount and getItemViewType, disable view recycling
        // ESSENTIAL at the moment since this view doesn't deal with cases where not all items are visible
        // in the list well.
        @Override
        public int getViewTypeCount()
        {
            return getCount();
        }

        @Override
        public int getItemViewType(int position)
        {
            return position;
        }

        @Override
        public int getCount()
        {
            return numSlots + 1;            // +1 for the title
        }

        @Override
        public Laptime getItem(int position)
        {
            //if (mService != null)
            //    return mService.decode.allDetectionTable.getItem(position);
            //else
                return null;
        }

        @Override
        public long getItemId(int position)
        {
            //if (mService != null)
           //     return mService.decode.allDetectionTable.getItemId(position);
           // else
                return 0;
        }

        public void updateRequest()
        {
            notifyDataSetChanged();
        }

        //---------------------------------------------------------------------------------------------------------
        void formatLapTimeFloat(View convertView, @IdRes int id, float timeVal)
        {
            DecimalFormat df;

            TextView textView = (TextView) convertView.findViewById(id);

            if(timeVal >= 100.0f)
                df = new DecimalFormat("###0.0");
            else
                df = new DecimalFormat("###0.00");
            df.setRoundingMode(RoundingMode.DOWN);

            if (timeVal > 1e8f || timeVal < 0.0f)
                textView.setText("-");
            else
                textView.setText(df.format(timeVal));
        }

        //---------------------------------------------------------------------------------------------------------
        void formatLapCountInt(View convertView, @IdRes int id, int intVal)
        {
            TextView textView = (TextView) convertView.findViewById(id);

            textView.setText(Integer.toString(intVal));
        }


        //---------------------------------------------------------------------------------------------------------
        void formatCellText(View convertView, @IdRes int id, String cellValue)
        {
            TextView textView = (TextView) convertView.findViewById(id);

            textView.setText(cellValue);
        }

        //---------------------------------------------------------------------------------------------------------
        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            DecimalFormat df = new DecimalFormat("###0.00");
            df.setRoundingMode(RoundingMode.DOWN);

            // Check if an existing view is being reused, otherwise inflate the view
            //
                if (convertView == null)
                {
                    if(position == 0)
                        convertView = mInflater.inflate(R.layout.activity_new_race_titles, parent, false);      // titles
                    else
                        convertView = mInflater.inflate(R.layout.activity_new_race_row, parent, false);         // pilot result rows
                }

            // todo: move this so that we only do it once
            //
                Vector<Integer> lapTimeViewIDs = new Vector<Integer>();
                lapTimeViewIDs.add(R.id.lap1Text);
                lapTimeViewIDs.add(R.id.lap2Text);
                lapTimeViewIDs.add(R.id.lap3Text);
                lapTimeViewIDs.add(R.id.lap4Text);
                lapTimeViewIDs.add(R.id.lap5Text);
                lapTimeViewIDs.add(R.id.lap6Text);
                lapTimeViewIDs.add(R.id.lap7Text);
                lapTimeViewIDs.add(R.id.lap8Text);

            // only for pilot results, not for the titles
            //
                if(position == 0)
                {
                     for(int iLap = 1; iLap <= 8; ++iLap) {
                        formatCellText(convertView, lapTimeViewIDs.get(iLap-1), "Lap " + Integer.toString(iLap + (lapIndexInLeftColumn-1)));
                    }
                }
                else if(position > 0)
                {
                    // set the bold Slot ID number on the left of the row
                    //
                    TextView slotNumber = (TextView) convertView.findViewById(R.id.slotIDText);
                    slotNumber.setText(Integer.toString(position));

                    // lookup the pilot name from preferences
                    //
                    TextView pilotNameView = (TextView) convertView.findViewById(R.id.pilotNameText);
                    String namePilot = settings.getString("pilot_name_" + Integer.toString(position), "Pilot " + Integer.toString(position));
                    pilotNameView.setText(namePilot);

                    // lap count
                    //
                    Vector<Integer> lapCounts = mService.decode.allDetectionTable.getLapCount();
                    Integer thisCount = lapCounts.get(position-1);
                    formatLapCountInt(convertView, R.id.lapsTextView, thisCount);

                    // pilot order next, small icon to show who is in first place
                    //
                    TextView pilotPositionView = (TextView) convertView.findViewById(R.id.pilotPositionText);
                    Vector<Integer> pilotOrdering = mService.decode.allDetectionTable.getPilotOrdering();
                    if ((pilotOrdering != null) && (thisCount > 0))
                    {
                        int thisPilotOrdering = pilotOrdering.get(position-1);

                        if (thisPilotOrdering == 0 || thisPilotOrdering < 4)
                            pilotPositionView.setText("");
                        else
                            pilotPositionView.setText(Integer.toString(thisPilotOrdering));
                    }
                    else
                        pilotPositionView.setText("");

                    // pilot order icon
                    //
                    TextView pilotIconView = (TextView) convertView.findViewById(R.id.pilotPositionText);
                    if ((pilotOrdering != null) && (thisCount > 0))
                    {
                        int thisPilotOrdering = pilotOrdering.get(position-1);
                        if (thisPilotOrdering == 1)
                            pilotIconView.setBackground(getResources().getDrawable(R.drawable.first_place));
                        else if (thisPilotOrdering == 2)
                            pilotIconView.setBackground(getResources().getDrawable(R.drawable.second_place));
                        else if (thisPilotOrdering == 3)
                            pilotIconView.setBackground(getResources().getDrawable(R.drawable.third_place));
                        else
                            pilotIconView.setBackground(getResources().getDrawable(R.drawable.no_place));
                    }
                    else
                        pilotIconView.setBackground(getResources().getDrawable(R.drawable.no_place));

                    // best lap time
                    //
                    Vector<Float> pilotTimes = mService.decode.allDetectionTable.getBestLapTimes();
                    float thisTime = pilotTimes.get(position-1);
                    formatLapTimeFloat(convertView, R.id.bestTextView, thisTime);

                    // average lap time
                    //
                    pilotTimes = mService.decode.allDetectionTable.getAvgTimes();
                    thisTime = pilotTimes.get(position-1);
                    formatLapTimeFloat(convertView, R.id.avgTextView, thisTime);

                    // total lap time
                    //
                    pilotTimes = mService.decode.allDetectionTable.getPilotTimes();         // apparently 'pilot times' = total time
                    thisTime = pilotTimes.get(position-1);
                    formatLapTimeFloat(convertView, R.id.totalTextView, thisTime);

                    // lookup the index of the best lap time for this pilot
                    //
                    Integer bestLapTimeIndex = mService.decode.allDetectionTable.getBestLapTimeIndex(position);

                    // walk through all laps, fill the table of individual lap times
                    //
                    int iFastestLap = -1;
                    float timeFastestLap = 9999f;
                    for(int i = 0; i < 8; ++i)
                    {
                        int lapIndex = i + (lapIndexInLeftColumn - 1);
                        Vector<Laptime> lapTime1 = mService.decode.allDetectionTable.getTableItem(lapIndex+1);
                        Laptime thisPilotTime = lapTime1.get(position - 1);

                        if(thisPilotTime != null)
                        {
                            float thisLaptime = thisPilotTime.laptime;

                            formatLapTimeFloat(convertView, lapTimeViewIDs.get(i), thisLaptime);
                        }
                        else
                            formatLapTimeFloat(convertView, lapTimeViewIDs.get(i), -1.0f);
                    }

                    // highlight the fastest lap for this pilot
                    //
                    for(int i = 0; i < 8; ++i)
                    {
                        int id = lapTimeViewIDs.get(i);
                        TextView textView = (TextView) convertView.findViewById(id);
                        if(i + (lapIndexInLeftColumn - 1) == bestLapTimeIndex )
                        {
                            textView.setBackgroundColor(getResources().getColor(R.color.fastest_lap_color));
                        }
                        else
                        {
                            textView.setBackgroundColor(getResources().getColor(android.R.color.transparent));
                        }
                    }
                }

            // Return the completed view to render on screen
            return convertView;
        }
    }

    //---------------------------------------------------------------------------------------------------------
    void broadcastDetection(protocolDecoder.communicationDetection detection)
    {
        Intent localIntent =
                new Intent(protocolDecoder.Constants.BROADCAST_PROTOCOL_DETECTION)
                        // Puts the status into the Intent
                        .putExtra(protocolDecoder.Constants.EXTENDED_DATA_PROTOCOL_DETECTION, detection );

        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(mService).sendBroadcast(localIntent);
    }

    //---------------------------------------------------------------------------------------------------------
    // When the slot ID is clicked, create a dummy detection, using the current system time
    // NOTE: The detectionNumber of this record will be the same as the next detection read from the
    // hardware, and could confuse the 'missed passing records' algorithm.
    public void onClickSlotId(View view)
    {
        TextView slotNumber = (TextView) view;
        String txt = slotNumber.getText().toString();
        int position = Integer.parseInt(txt);

        Log.d("NewRaceActivity", "onClickSlotId" + Integer.toString(position));
        boolean raceActive = mService.decode.allDetectionTable.getRaceActive();

        protocolDecoder.communicationDetection dummy1 = new protocolDecoder.communicationDetection();

        dummy1.decoderId = 1;
        dummy1.detectionNumber = mService.decode.allDetectionTable.getCount();
        dummy1.pilotId = position;
        dummy1.detectionPeakHeight = 1;
        dummy1.detectionHits = 1;
        dummy1.detectionFlags = 0;          // 0xff if added due to BT loss

        dummy1.puckTime = System.currentTimeMillis() * 1000;
        dummy1.appTime = -1;
        mService.decode.allDetectionTable.addItem(dummy1);
        broadcastDetection(dummy1);
    }

    //---------------------------------------------------------------------------------------------------------
    boolean clicked = false;
    private AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int position,long arg3)
        {
            // TODO Auto-generated method stub

            //do your job here, position is the item position in ListView
            clicked = true;
        }
    };

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    // request from the button to start the race
    public void startRace(View view)
    {
        //mLaptimeAdapter.clear();
        RacerRowAdapter.updateRequest();

        if (mService != null)
        {
            mService.startRace();
        }
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    public void stopRace(View view)
    {
        if (mService != null)
        {
            mService.stopRace(true);        // force it to stop
        }

        RacerRowAdapter.updateRequest();
    }

    int numLapsAvailable = lapRFConstants.totalNumLaps;
    //--------------------------------------------------------------------------------------------------------------------------------------------------
    public void lapsPageLeft(View view)
    {
        // scroll laps to the left
        lapIndexInLeftColumn -= 8;
        if(lapIndexInLeftColumn < 1)
            lapIndexInLeftColumn = numLapsAvailable - 7;
        RacerRowAdapter.updateRequest();
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    public void lapsPageRight(View view)
    {
        // scroll laps to the right
        lapIndexInLeftColumn += 8;
        if(lapIndexInLeftColumn > numLapsAvailable)
            lapIndexInLeftColumn = 1;
        RacerRowAdapter.updateRequest();
    }

    //---------------------------------------------------------------------------------------------------------
    // gray/ungray the race start and stop buttons based upon the race active state
    //
    public void updateStartStopButtonGraying()
    {
        boolean raceActive = mService.decode.allDetectionTable.getRaceActive();

        Button btn = (Button) findViewById(R.id.startButton);
        btn.setEnabled(!raceActive);
        btn = (Button) findViewById(R.id.stopButton);
        btn.setEnabled(raceActive);
    }

    /**************************************************************************************
     * menu handling
     */

    // ********************************************************************************************
    // * handle bluetooth permission requests
    private final static int REQUEST_CONNECT_BT = 0x05;
    private final static int REQUEST_GENERAL_SETTINGS = 0x09;
    private final static int REQUEST_PILOT_SETTINGS = 0x0a;

    private final static int REQUEST_ENABLE_BT = 0x42;
    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_BLUETOOTH = 0x43;
    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_BLUETOOTH_LOCATION = 0x44;

    // Menu icons are inflated just as they were with actionbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(com.immersionrc.LapRFTiming.R.menu.race_menu, menu);

        return true;
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.race_analysis_item:
            {
                // User chose the "New Race View" item, show the app settings UI...
                Intent intent = new Intent(this, RaceAnalysisActivity.class);

                startActivity(intent);

                return true;
            }

            case R.id.lapRF_setup_item:
            {
                // User chose the "Settings" item, show the app settings UI...
                Intent intent = new Intent(this, SetupActivity.class);

                startActivity(intent);

                return true;
            }
            case R.id.general_settings_item:
            {
                // User chose the "General Settings" item, show the app settings UI...
                Intent intent = new Intent(this, GeneralSetupActivity.class);

                startActivityForResult( intent, REQUEST_GENERAL_SETTINGS );

                return true;
            }
            case R.id.pilot_settings_item:
            {
                // User chose the "General Settings" item, show the app settings UI...
                Intent intent = new Intent(this, PilotSetupActivity.class);

                startActivityForResult( intent, REQUEST_PILOT_SETTINGS );

                return true;
            }
            /*case R.id.lapRF_setup_item_new: {
                // User chose the "Settings" item, show the app settings UI...
                Intent intent = new Intent(this, LapRFSetupActivity.class);

                startActivity(intent);

                return true;
            }*/
            case R.id.rssi_diags_setup_item_new:
            {
                // User chose the "RSSI Diagnostics" item, show the app settings UI...
                Intent intent = new Intent(this, RSSIDiagnosticsActivity.class);

                startActivity(intent);

                return true;
            }
            case R.id.bt_connect_item:
                Intent startIntent = new Intent(getApplicationContext(), BluetoothScanActivity.class);

                startActivityForResult( startIntent, REQUEST_CONNECT_BT );

                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }
    //---------------------------------------------------------------------------------------------------------
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_race);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // setup the toolbar
        //
        Toolbar toolbar = (Toolbar) findViewById(com.immersionrc.LapRFTiming.R.id.toolbar);
        setSupportActionBar(toolbar);

        // gainAdapter = new ArrayAdapter<Integer>(this, android.R.layout.simple_spinner_item, gainList);

        ListView racerRowListView = (ListView) findViewById(R.id.racerRowListView);
        RacerRowAdapter = new RacerRowAdapter();

        racerRowListView.setAdapter(RacerRowAdapter);

        racerRowListView.setOnItemClickListener(onItemClickListener);

        RacerRowAdapter.updateRequest();

        mBluetoothServiceManager = new BluetoothServiceBroadcastManager(this, this);
        mBluetoothServiceManager.subscribeToIntentReceiver();

        Intent intent = new Intent(this, BluetoothBackgroundService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        // ensure that the Android phone doesn't go into sleep mode
        //
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // ensure that the start/stop buttons are grayed appropriately
        //
        updateStartStopButtonGraying();
    }

    //---------------------------------------------------------------------------------------------------------
    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if ( mService != null )
        {
            mService.sendUpdatePeriod(2000);

            if (mBound)
            {
                mService.referenceCount(-1);
                unbindService(mConnection);
                mBound = false;
            }
        }
    }

    int readPilot = 1;

    public void receivedServiceState(String state){}
    public void receivedConnectionStatus(boolean connected, int rssi){}
    public void receivedRFSettings(communicationRfSettings rf_settings){}
    public void receivedSettings(extraSettings extra_settings) {}
    public void receivedStatus(protocolDecoder.communicationStatus status )
    {
        if (status != null)
        {
            TextView textView = (TextView) findViewById(R.id.setupStatusTextBox);
            if(textView != null)
            {
                if (status.connection == 0) {
                    textView.setBackgroundColor(Color.GREEN);
                } else if (status.connection == -1) {
                    textView.setBackgroundColor(Color.RED);
                } else {
                    textView.setBackgroundColor(Color.rgb(0x00, 0xDD, 0x00));
                }}
        }
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    public void receivedDetection(protocolDecoder.communicationDetection detection)
    {
        RacerRowAdapter.notifyDataSetChanged();
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    public void receivedRaceActiveStatus(boolean bIsActive)
    {
        Log.d("NewRaceActivity", "bIsActive" + Boolean.toString(bIsActive) );

        // race active just changed (could be race started, or stopped), update the UI to reflect
        updateStartStopButtonGraying();

        // update the table
        RacerRowAdapter.updateRequest();
    }

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

            mService.sendUpdatePeriod(5000);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0)
        {
            mBound = false;
        }
    };

    @Override
    public void onFragmentInteraction(Uri uri){
        //you can leave it empty
    }

}
