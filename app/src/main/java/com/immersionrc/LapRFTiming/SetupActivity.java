// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
package com.immersionrc.LapRFTiming;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.util.Log;

import java.util.ArrayList;
import java.util.Vector;

import static java.lang.Math.abs;

/**
 * Handle setup of LapRF
 *  - pilot frequencies and gain
 *  - puck name
 *
 *  TODO: - use adapter and list for pilots
 *
 */

class ViewHolder {
    TextView tvPilotId;
    TextView tvFrequency;
    Spinner tvGain;
    EditText etThreshold;
    ToggleButton tgEnable;
    int position;
}

public class SetupActivity
        extends AppCompatActivity
        implements BluetoothServiceBroadcastReceiver
{
    public Handler mHandler;

    public final int numSlots = lapRFConstants.numPilotSlots;

    // Manager to abstract broadcast receiver for receiving data from the bluetooth Service
    BluetoothServiceBroadcastManager mBluetoothServiceManager;

    SlotSettingsAdapter slotsettingsadapter;

    ArrayList<Integer> gainList;
    ArrayAdapter<Integer> gainAdapter;

    boolean bReceivedRFSettings = false;

    // adapter to display settings for each slot of the LapRF
    //
    public class SlotSettingsAdapter extends BaseAdapter {
        communicationRfSettings slots[] = new communicationRfSettings[numSlots];
        public boolean slot_active[] = new boolean[numSlots];

        float slots_rssi[] = new float[numSlots];

        private LayoutInflater mInflater;                   // The layout inflator turns XML objects into corresponding view objects

        // constructor
        public SlotSettingsAdapter() {
            for (int i = 0; i < numSlots; i++)
            {
                slots[i] = new communicationRfSettings();

                slots[i].slotid = (byte)(i + 1);
                slots[i].band = (short)(1);
                slots[i].channel = (short)(1 + i);
                slots[i].frequency = (short)(5740 + 20*i);
                slots[i].enable = (short)(0);
                slots[i].threshold = 1200.0f;
                slots[i].gain = (short)(63);

                slot_active[i] = false;
            }

            mInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public void setActive(byte slotid, boolean active)
        {
            if ( slot_active[slotid - 1] != active ) {
                slot_active[slotid - 1] = active;
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

        @Override
        public communicationRfSettings getItem(int position) {
            if (position >= 0 && position < numSlots )
            {
                return slots[position];
            }
            else
                return null;
        }

        @Override
        public long getItemId(int position) {
            if (position >= 0 && position < numSlots )
            {
                return slots[position].slotid;
            }
            else
                return 0;
        }

        Integer maxGateSpeedkmh[] = { 160, 160, 90, 75, 50, 40, 35, 30, 25 };

        public void updateRequest()
        {
            Integer nChannelsEnabled = 0;
            for ( int i = 0; i < 8; i++ )
            {
                communicationRfSettings rfSettings = slotsettingsadapter.getItem(i);
                if(rfSettings.enable == 1)
                    ++nChannelsEnabled;
            }

            TextView resolutionAndSpeedText = (TextView) findViewById(R.id.textViewMaxGateSpeed);
            if(nChannelsEnabled == 0)
                nChannelsEnabled = 1;
            String res = String.format("resolution %d ms, max gate speed of %d kmh", nChannelsEnabled * 30, maxGateSpeedkmh[nChannelsEnabled]);
            resolutionAndSpeedText.setText(res);

            notifyDataSetChanged();
        }

        public void updateControlsFromSettings()
        {



        }

        //----------------------------------------------------------------------------------------------------------------------------------------------
        // getView is called once for each item in the list, corresponds to settings for one slot
        // note: position is the position within the view, but not necessarily the slot number (important when the view is not entirely displayed)
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder viewHolder = null;

            // Check if an existing view is being reused, otherwise inflate the view
            // NOTE: getView is called repeatedly, for example when a view is scrolled.
            if (convertView == null) {
                convertView = mInflater.inflate(com.immersionrc.LapRFTiming.R.layout.slot_setup_item, parent, false);

                viewHolder = new ViewHolder();

                // Lookup views for each control
                viewHolder.tvPilotId = (TextView) convertView.findViewById(R.id.slot_item_pilotNameEditText);
                viewHolder.tvFrequency = (TextView) convertView.findViewById(R.id.slot_item_frequencyEditText);
                viewHolder.tvGain = (Spinner) convertView.findViewById(R.id.slot_item_gainSpinner);
                viewHolder.etThreshold = (EditText) convertView.findViewById(R.id.slot_item_thresholdEditText);
                viewHolder.tgEnable = (ToggleButton) convertView.findViewById(R.id.slot_item_toggleButton);
                viewHolder.position = position;

                convertView.setTag(viewHolder);

                viewHolder.tvGain.setAdapter(gainAdapter);
            }
            else
            {
                viewHolder = (ViewHolder)convertView.getTag();
            }

            // Get the data item for this position
            final communicationRfSettings slot = getItem(position);

            // Populate the data into the template view using the data object
            viewHolder.tvPilotId.setText(Integer.toString(slot.slotid));
            viewHolder.tvFrequency.setText(Integer.toString(slot.frequency));
            viewHolder.tgEnable.setChecked(slot.enable == 1);
            viewHolder.tgEnable.setTag(position);
            viewHolder.tgEnable.setEnabled( true ); //current_slot_active );

            boolean current_slot_active = true; //slot_active[slot.slotid - 1];
            viewHolder.tvFrequency.setEnabled( current_slot_active );
            viewHolder.tvPilotId.setEnabled( current_slot_active );
            viewHolder.tvGain.setEnabled( current_slot_active );
            viewHolder.etThreshold.setEnabled( current_slot_active );
            // btCal.setEnabled( current_slot_active );

            class DoneThresholdEditorActionListener implements TextView.OnEditorActionListener {

                public byte slotid;

                public DoneThresholdEditorActionListener(byte slotid_in)
                {
                    super();
                    slotid = slotid_in;
                }

                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        InputMethodManager imm = (InputMethodManager)v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                        float thr = Float.parseFloat( v.getText().toString() );
                        if ( abs(slots[slotid - 1].threshold - thr) > 0.5  )
                        {
                            // if the value is different, set the adapter and reload
                            slots[slotid - 1].threshold = thr;

                            updateRequest();
                        }

                        return true;
                    }
                    return false;
                }
            }

            class OnCalClickListener implements View.OnClickListener
            {
                public byte slotid;

                public OnCalClickListener(byte slotid_in)
                {
                    super();
                    slotid = slotid_in;
                }

                @Override
                public void onClick(View v)
                {
                    startStaticCalibration(slotid);
                }
            }

            class OnFreqClickListener implements View.OnClickListener
            {
                public byte slotid;

                public OnFreqClickListener(byte slotid_in)
                {
                    super();
                    slotid = slotid_in;
                }

                @Override
                public void onClick(View v)
                {
                    showFrequencyActivity(slotid);
                }
            }

            class OnEnableChangeListener implements CompoundButton.OnCheckedChangeListener
            {
                public byte slotid;

                public OnEnableChangeListener(byte slotid_in)
                {
                    super();
                    slotid = slotid_in;
                }

                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                {
                    int slotIdx = (int) buttonView.getTag();
                    boolean slotEnabled = slots[slotIdx].enable == 1;

                    if ( isChecked != slotEnabled )
                    {
                        if (isChecked)
                            slots[slotIdx].enable = 1;
                        else
                            slots[slotIdx].enable = 0;

                        updateRequest();
                    }
                }
            }

            class OnGainItemSelectedListener implements AdapterView.OnItemSelectedListener
            {
                public byte slotid;

                public OnGainItemSelectedListener(byte slotid_in)
                {
                    super();
                    slotid = slotid_in;
                }

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
                {

                    if ( (short)position != slots[slotid - 1].gain)
                    {
                        slots[slotid - 1].gain = (short)position;
                        updateRequest();
                    }
                }

                @Override
                public void onNothingSelected (AdapterView<?> parent)
                {

                }
            }

            viewHolder.etThreshold.setOnEditorActionListener(new DoneThresholdEditorActionListener(slot.slotid));
            viewHolder.tvFrequency.setOnClickListener( new OnFreqClickListener( slot.slotid ) );
            // btCal.setOnClickListener( new OnCalClickListener( slot.slotid ) );

            viewHolder.tvGain.setOnItemSelectedListener( new OnGainItemSelectedListener( slot.slotid ) );

            slot.gain = (short) Math.min(slot.gain, 63);                    // gain is sometimes returned as a value > 63 (puck bug)
            viewHolder.tvGain.setSelection(slot.gain);
            viewHolder.etThreshold.setText(Float.toString(slot.threshold));

            viewHolder.tgEnable.setOnCheckedChangeListener( new OnEnableChangeListener(slot.slotid) );

            if (slot == null)
                return null;

            // Return the completed view to render on screen
            return convertView;
        }
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
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

    //----------------------------------------------------------------------------------------------------------------------------------------------
    // handle the popup where a user is prompted to enter a new name for his LapRF Timing Puck
    //
    void showPopupChangePuckName()
    {
        // We only want names which include letters and digits
        // TODO: We need to understand the full range of entries supported, and more importantly
        // those which are not (e.g. names starting with a number, etc.)
        InputFilter filter = new InputFilter()
        {
            public CharSequence filter(CharSequence source, int start, int end,
                                       Spanned dest, int dstart, int dend)
            {
                for (int i = start; i < end; i++)
                {
                    if (!Character.isLetterOrDigit(source.charAt(i)))
                    {
                        return "";
                    }
                }
                return null;
            }
        };

        AlertDialog.Builder helpBuilder = new AlertDialog.Builder(this);
        helpBuilder.setTitle(R.string.title_rename_puck);
        helpBuilder.setMessage(R.string.title_enter_new_name);
        final EditText input = new EditText(this);
        input.setSingleLine();
        input.setText("");
        input.setFilters(new InputFilter[]{ filter, new InputFilter.LengthFilter(8)});
        helpBuilder.setView(input);

        helpBuilder.setPositiveButton("Change", new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog, int which)
                {
                    mService.sendPuckName(input.getText().toString());
                }
            });

        helpBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    // Do nothing
                }
            });

        // Remember, create doesn't show the dialog
        AlertDialog helpDialog = helpBuilder.create();
        helpDialog.show();
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void writeIntPreference(String name, int value) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SharedPreferences.Editor editor = settings.edit();
        editor.putInt(name, value);
        editor.apply();
    }


    //----------------------------------------------------------------------------------------------------------------------------------------------
    public int readIntPreference(String name, int defaultValue, int maxValue)
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int val = settings.getInt(name, defaultValue);
        val = Math.min(val, maxValue);
        return val;
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    // create the activity, called once per viewing
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(com.immersionrc.LapRFTiming.R.layout.activity_setup);

        // create a list of channel numbers
        ArrayList<Integer> channelsList = new ArrayList<Integer>() ;
        for(int i = 0; i < 9; i++)
            channelsList.add(i);

        // and a list of gain settings
        gainList = new ArrayList<Integer>() ;
        for(int i = 0; i< 64; i++)
            gainList.add(i);

        gainAdapter = new ArrayAdapter<Integer>(this, android.R.layout.simple_spinner_item, gainList);

        // find the list view, embedded in the activity, and set it up
        ListView slotSettingsListView = (ListView) findViewById(R.id.racerRowListView);

        slotsettingsadapter = new SlotSettingsAdapter();
        slotSettingsListView.setAdapter(slotsettingsadapter);
        slotSettingsListView.setOnItemClickListener(onItemClickListener);
        slotsettingsadapter.updateRequest();


        Button btRead = (Button) findViewById(R.id.readButton);
        btRead.setOnClickListener( new View.OnClickListener()
        {
            public void onClick(View v)
            {
                readFromDevice();
            }
        } ) ;

        Button btSend = (Button) findViewById(R.id.sendButton);
        btSend.setOnClickListener( new View.OnClickListener()
        {
            public void onClick(View v)
            {
                sendToDeviceWithVerification();

            }
        } ) ;

        Button btRenamePuck = (Button) findViewById(R.id.buttonRenamePuck);
        btRenamePuck.setOnClickListener( new View.OnClickListener()
        {
            public void onClick(View v)
            {
                showPopupChangePuckName();
            }
        } ) ;

        // minimum lap time spinner
        //
        Spinner spinnerMinLapTime = (Spinner) findViewById(R.id.spinnerMinLapTime);
        spinnerMinLapTime.setSelection(readIntPreference("minLapTime", 0, spinnerMinLapTime.getCount()-1));
        spinnerMinLapTime.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                writeIntPreference("minLapTime", position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

                // sometimes you need nothing here
            }
        });

        // make the necessary connections into Bluetooth to allow us to be called when settings change
        mBluetoothServiceManager = new BluetoothServiceBroadcastManager(this, this);
        mBluetoothServiceManager.subscribeToIntentReceiver();

        Intent intent = new Intent(this, BluetoothBackgroundService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void defaults25mWButton(View view)
    {
        setAllGainSelection(58);
        setAllThresholds(800);
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void defaults200mWButton(View view)
    {
        setAllGainSelection(44);
        setAllThresholds(800);
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void defaults600mWButton(View view)
    {
        setAllGainSelection(40);
        setAllThresholds(800);
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void defaultsRaceBandButton(View view)
    {
        setAllGainSelection(58);
        setAllThresholds(800);
        int rbFrequencies[] = { 5658, 5695, 5732, 5769, 5806, 5843, 5880, 5917 };
        setFrequencyList(rbFrequencies);
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void defaultsIRCBandButton(View view)
    {
        setAllGainSelection(58);
        setAllThresholds(800);
        int rbFrequencies[] = { 5740, 5760, 5780, 5800, 5820, 5840, 5860, 5880 };
        setFrequencyList(rbFrequencies);
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if ( mService != null )
            mService.sendUpdatePeriod(2000);
    }

    //---------------------------------------------------------------------------------------------------------
    public void writeSettingsToPreferences()
    {
        Vector<communicationRfSettings> settings = new Vector<communicationRfSettings>();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        for (int i = 0; i < numSlots; i++)
        {
            communicationRfSettings set = slotsettingsadapter.getItem(i);

            final SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt("slot" + Integer.toString(i) + "band", set.band);
            editor.putInt("slot" + Integer.toString(i) + "channel", set.channel);
            editor.putInt("slot" + Integer.toString(i) + "enable", set.enable);
            editor.putInt("slot" + Integer.toString(i) + "frequency", set.frequency);
            editor.putInt("slot" + Integer.toString(i) + "gain", set.gain);
            editor.putFloat("slot" + Integer.toString(i) + "threshold", set.threshold);
            editor.apply();

            Log.d("SetupActivity", Integer.toString(i) + " " +  Integer.toString(set.gain));
        }
    }

    //---------------------------------------------------------------------------------------------------------
    // Process either killed, or user navigates to another activity
    @Override
    public void onPause()
    {
        Log.d("SetupActivity", "OnPause");
        super.onPause();

        writeSettingsToPreferences();

    }

    //---------------------------------------------------------------------------------------------------------
    public void getSettingsFromPreferences()
    {
        Vector<communicationRfSettings> settings = new Vector<communicationRfSettings>();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        for (int i = 0; i < numSlots; i++)
        {
            communicationRfSettings set = slotsettingsadapter.getItem(i);

            set.band = (short) sharedPref.getInt("slot" + Integer.toString(i) + "band", 1 /* def. band 1 */);
            set.channel = (short) sharedPref.getInt("slot" + Integer.toString(i) + "channel", 1+1);
            set.frequency = (short) sharedPref.getInt("slot" + Integer.toString(i) + "frequency", 5800);
            set.enable = (short) sharedPref.getInt("slot" + Integer.toString(i) + "enable", 1);
            set.gain = (short) sharedPref.getInt("slot" + Integer.toString(i) + "gain", 58 );
            set.threshold = (float) sharedPref.getFloat("slot" + Integer.toString(i) + "threshold", 800.0f );

            Log.d("SetupActivity", Integer.toString(i) + " " +  Integer.toString(set.gain));

            slotsettingsadapter.updateRequest();
        }
    }

    //---------------------------------------------------------------------------------------------------------
    // Process either killed, or user navigates to another activity
    @Override
    public void onResume()
    {
        Log.d("SetupActivity", "onResume");
        super.onResume();

        getSettingsFromPreferences();
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    private void showFrequencyActivity(int pilotID)
    {
        Intent startIntent = new Intent(getApplicationContext(), SelectFrequencyActivity.class);

        communicationRfSettings rfSettings = slotsettingsadapter.getItem(pilotID - 1);
        startIntent.putExtra("current_channel", (int)rfSettings.channel);
        startIntent.putExtra("current_band", (int)rfSettings.band);
        startIntent.putExtra("current_frequency", rfSettings.frequency );
        startIntent.putExtra("pilot_id", pilotID);

        startActivityForResult( startIntent, SelectFrequencyActivity.SELECT_FREQUENCY );
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    @Override
    protected void onActivityResult (int requestCode,
                                     int resultCode,
                                     Intent data)
    {
        if (requestCode == SelectFrequencyActivity.SELECT_FREQUENCY)
        {
            if ( resultCode == RESULT_OK )
            {
                int channel = data.getIntExtra("set_channel", 0);
                int band = data.getIntExtra("set_band", 0);
                int frequency = data.getIntExtra("set_frequency", 0);
                int pilotID = data.getIntExtra("pilot_id", 0);

                communicationRfSettings settings = slotsettingsadapter.getItem(pilotID - 1);
                if (settings != null)
                {
                    settings.frequency = (short)frequency;
                    settings.band = (short)band;
                    settings.channel = (short)channel;

                    slotsettingsadapter.updateRequest();
                    writeSettingsToPreferences();
                }
            }
            else if ( resultCode == RESULT_CANCELED )
            {

            }
        }
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void sendUpdatePeriod(View view)
    {
        /*
        EditText updatePeriodEditText = (EditText) findViewById(R.id.updatePeriodEditText);
        int val = Integer.parseInt(updatePeriodEditText.getText().toString());
        mService.sendUpdatePeriod(val);
        */
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void sendPuckName(View view)
    {
        //EditText puckNameEditText = (EditText) findViewById(com.immersionrc.LapRFTiming.R.id.puckNameEditText);

        //String name = puckNameEditText.getText().toString();
        //name = name.substring(0, Math.min(name.length(), 8));
        //mService.sendPuckName(name);
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    void sendSettingsForChannel(int slotid)
    {
        communicationRfSettings rfSettings = slotsettingsadapter.getItem(slotid - 1);

        // disable list entry while sending
        slotsettingsadapter.setActive((byte)slotid, false);

        mService.sendChannelSettings(   slotid,
                rfSettings.enable == 1,
                rfSettings.channel,
                rfSettings.band,
                rfSettings.gain,
                rfSettings.threshold,
                rfSettings.frequency );
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    private void setAllGainSelection(int g)
    {
        for ( int i = 0; i < 8; i++ )
        {
            communicationRfSettings rfSettings = slotsettingsadapter.getItem(i);
            rfSettings.gain = (short)g;
        }

        slotsettingsadapter.updateRequest();
        writeSettingsToPreferences();
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    private void setAllThresholds(float thr)
    {
        for ( int i = 0; i < 8; i++ )
        {
            communicationRfSettings rfSettings = slotsettingsadapter.getItem(i);
            rfSettings.threshold = thr;
        }

        slotsettingsadapter.updateRequest();
        writeSettingsToPreferences();
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    // default to raceband frequencies, first channel enabled, 25mW gain and threshold
    //
    private void setFrequencyList(int frequencyList[])
    {
        for ( int i = 0; i < 8; i++ )
        {
            communicationRfSettings rfSettings = slotsettingsadapter.getItem(i);
            if(i == 0)
                rfSettings.enable = 1;
            else
                rfSettings.enable = 0;

            rfSettings.band = 1;
            rfSettings.channel = (short) (i+1);
            rfSettings.frequency = (short) frequencyList[i];
        }

        slotsettingsadapter.updateRequest();
        writeSettingsToPreferences();
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void sendShutdown(View view)
    {
        mService.sendShutdown();
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void sendReset(View view)
    {
        mService.sendReset();
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void showCalibration(View view)
    {
        Intent intent = new Intent(this, DynamicCalibrationActivity.class);

        startActivity(intent);
    }

    boolean sendAllSettings = false;
    int sendSettingsIndex = 1;
    boolean readFromDevice = false;
    int readPilot = 1;

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void startSendPing(View view)
    {
        if (mService != null)
            mService.sendPing(1);
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    void sendToDevice()
    {
        CheckBox resolutionAndSpeedText = (CheckBox) findViewById(R.id.checkBoxReadOk);
        resolutionAndSpeedText.setChecked(false);

        Log.d("SetupActivity", "sendToDevice");

        Spinner spinnerMinLapTime = (Spinner) findViewById(R.id.spinnerMinLapTime);
        int spinnerIdx = spinnerMinLapTime.getSelectedItemPosition();
        float minLapTime = (spinnerIdx + 1 ) * 5;

        mService.sendMinLapTime(minLapTime);
        try
        {
            Thread.sleep(20);
        }
        catch (Exception ex)
        {
            System.out.println("Sleep exception: " + ex);
        }

        Vector<communicationRfSettings> settings = new Vector<communicationRfSettings>();

        for (int i = 0; i < numSlots; i++)
        {
            communicationRfSettings set = slotsettingsadapter.getItem(i);
            communicationRfSettings set_out = new communicationRfSettings();

            set_out.band = set.band;
            set_out.channel = set.channel;
            set_out.enable = set.enable;
            set_out.frequency = set.frequency;
            set_out.gain = set.gain;
            set_out.slotid = set.slotid;
            set_out.threshold = set.threshold;

            // disable list entry while sending
            slotsettingsadapter.setActive(set.slotid, false);

            settings.add(set_out);
        }

        mService.sendMultiChannelSettings (settings);

    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    //  version of sendToDevice which verifies that each setting was sent correctly to the device by reading it back.
    //
    // NOTE: This is a bit of an ugly workaround for the problem sending settings on some devices, but seems to work well.
    // the root cause does need to be identified.
    void sendToDeviceWithVerification()
    {
        try
        {
            CheckBox resolutionAndSpeedText = (CheckBox) findViewById(R.id.checkBoxReadOk);
            resolutionAndSpeedText.setChecked(false);

            Log.d("SetupActivity", "sendToDevice");

            Spinner spinnerMinLapTime = (Spinner) findViewById(R.id.spinnerMinLapTime);
            int spinnerIdx = spinnerMinLapTime.getSelectedItemPosition();
            float minLapTime = (spinnerIdx + 1 ) * 5;

            mService.sendMinLapTime(minLapTime);

            Vector<communicationRfSettings> settings = new Vector<communicationRfSettings>();

            // send settings for each slot separately
            for (int i = 0; i < numSlots; i++)
            {
                short nTotalFailed = 0;
                communicationRfSettings set = slotsettingsadapter.getItem(i);

                // disable list entry while sending
                slotsettingsadapter.setActive(set.slotid, false);

                boolean bWrittenAndVerified = false;
                while(!bWrittenAndVerified)
                {
                    // send the setting for this slot
                    mService.decode.bReceivedRFSettings = false;
                    mService.sendChannelSettings(set.slotid, set.enable == 1 ? true : false, set.band, set.channel, set.gain, set.threshold, set.frequency);

                    // wait for confirmation that we received the echo back
                    for (int nRetries = 0; nRetries < 10; ++nRetries)
                    {
                        if (mService.decode.bReceivedRFSettings == true)
                            break;
                        Thread.sleep(20);

                    }

                    // ensure that the settings are as we sent them (note that channel and band are less important than frequency, do to the fact
                    // that arbitrary frequencies may be sent, which don't correspond to a particular channel.
                    if (mService.decode.bReceivedRFSettings == true &&
                        mService.decode.lastRFSettingsReceived.slotid == set.slotid &&
                        mService.decode.lastRFSettingsReceived.frequency == set.frequency &&
                        mService.decode.lastRFSettingsReceived.gain == set.gain)
                    {
                        System.out.println("bReceivedRFSettings == true");

                        bWrittenAndVerified = true;
                    }
                    else
                    {
                        // if we time out waiting for settings to be echoed back, wait a bit, and go around again. If the total failure count
                        // is way larger than expected, inform the user and stop trying
                        System.out.println("bReceivedRFSettings == false");
                        Thread.sleep(200);          // time to recover (maybe)

                        if(++nTotalFailed > 10)
                        {
                            new AlertDialog.Builder(this)
                                    .setTitle("LapRF")
                                    .setMessage("Settings write FAILED")
                                    .setPositiveButton("OK", null)
                                    .show();
                            return;
                        }
                    }
                }
            }
        }
        catch (Exception ex)
        {
            System.out.println("Sleep exception: " + ex);
        }

        new AlertDialog.Builder(this)
                .setTitle("LapRF")
                .setMessage("Settings write OK")
                .setPositiveButton("OK", null)
                .show();

    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    void readFromDevice()
    {
        CheckBox resolutionAndSpeedText = (CheckBox) findViewById(R.id.checkBoxReadOk);
        resolutionAndSpeedText.setChecked(false);

        Log.d("SetupActivity", "readFromDevice");
        for (int i = 0; i < numSlots; i++)
        {
            // disable list entry while sending
            slotsettingsadapter.setActive((byte)(i+1), false);
        }

        mService.requestChannelSettings(readPilot);

        mService.requestMinLapTime();
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    void resetStaticCalibration(View view)
    {
        mService.sendStartStaticCalibration(0);
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    void startStaticCalibration(byte slotid)
    {
        Intent startIntent = new Intent(getApplicationContext(), StaticCalibrationWizardActivity.class);

        startIntent.putExtra("channel", slotid);
        startActivityForResult( startIntent, StaticCalibrationWizardActivity.REQUEST_EMPTY_CAL );

    }


    public void receivedServiceState(String state){};
    public void receivedConnectionStatus(boolean connected, int rssi){};
    public void receivedDetection(protocolDecoder.communicationDetection detection){};
    public void receivedRaceActiveStatus(boolean bIsActive) {}

    //----------------------------------------------------------------------------------------------------------------------------------------------
    // change the color of the connection icon
    public void receivedStatus(protocolDecoder.communicationStatus status )
    {
        if (status != null)
        {
            TextView textView = (TextView) findViewById(R.id.setupStatusTextBox);
            if (status.connection == 0)
            {
                textView.setBackgroundColor(Color.GREEN);
            }
            else if (status.connection == -1)
            {
                textView.setBackgroundColor(Color.RED);
            }
            else
            {
                textView.setBackgroundColor(Color.rgb(0x00, 0xDD, 0x00));
            }
        }
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void receivedRFSettings(communicationRfSettings rf_settings)
    {
        CheckBox resolutionAndSpeedText = (CheckBox) findViewById(R.id.checkBoxReadOk);
        resolutionAndSpeedText.setChecked(true);

        int slotidx = rf_settings.slotid - 1;
        communicationRfSettings rfSettings = slotsettingsadapter.getItem(slotidx);

        rfSettings.gain = rf_settings.gain;
        rfSettings.threshold = rf_settings.threshold;
        rfSettings.slotid = rf_settings.slotid;
        rfSettings.band = rf_settings.band;
        rfSettings.channel = rf_settings.channel;
        rfSettings.frequency = rf_settings.frequency;
        rfSettings.enable = rf_settings.enable;

        // enable list entry we received
        slotsettingsadapter.setActive(rf_settings.slotid, true);

        slotsettingsadapter.updateRequest();

        bReceivedRFSettings = true;
    }

    public void receivedSettings(extraSettings extra_settings)
    {
        int spinnerIdx = (int) (extra_settings.minLapTime / 5000.0f) - 1;

        Spinner spinnerMinLapTime = (Spinner) findViewById(R.id.spinnerMinLapTime);
        int numItems = spinnerMinLapTime.getCount();
        if(spinnerIdx >= numItems)
            spinnerMinLapTime.setSelection(0);
        else
            spinnerMinLapTime.setSelection(spinnerIdx);
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
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
            mBound = true;

            //EditText puckNameEditText = (EditText) findViewById(com.immersionrc.LapRFTiming.R.id.puckNameEditText);
            //puckNameEditText.setText(mService.getPuckName());

            mService.sendUpdatePeriod(5000);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

}
