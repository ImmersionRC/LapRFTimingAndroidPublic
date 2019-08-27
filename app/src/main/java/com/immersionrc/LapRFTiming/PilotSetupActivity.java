// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
package com.immersionrc.LapRFTiming;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

//----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// Pilot Setup Activity
//    Presents pilot names, and enables them to be edited
//
// Anthony Cake: July 2017
//----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

class PilotViewHolder
{
    TextView tvPilotId;
    TextView tvPilotName;
    int position;
}

class pilotSettings implements Serializable
{
    public byte slotid;
    String pilotName;
}

public class PilotSetupActivity
        extends AppCompatActivity {
    public Handler mHandler;
    SlotSettingsAdapter slotsettingsadapter;

    public final int numSlots = lapRFConstants.numPilotSlots;

    //----------------------------------------------------------------------------------------------
    // adapter to display settings for each slot of the LapRF
    //
    public class SlotSettingsAdapter extends BaseAdapter
    {
        pilotSettings slots[] = new pilotSettings[numSlots];
        public boolean slot_active[] = new boolean[numSlots];

        private LayoutInflater mInflater;                   // The layout inflator turns XML objects into corresponding view objects

        //----------------------------------------------------------------------------------------------------------------------------------------------
        // constructor
        public SlotSettingsAdapter()
        {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            for (int i = 0; i < numSlots; i++) {
                slots[i] = new pilotSettings();

                slots[i].slotid = (byte) (i + 1);
                slots[i].pilotName = settings.getString("pilot_name_" + Integer.toString(i + 1), "Pilot " + Integer.toString(i + 1));
            }

            mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        //----------------------------------------------------------------------------------------------------------------------------------------------
        // NOTE: These next two methods, getViewTypeCount and getItemViewType, disable view recycling
        // ESSENTIAL at the moment since this view doesn't deal with cases where not all items are visible
        // in the list well.
        @Override
        public int getViewTypeCount() {

            return getCount();
        }

        //----------------------------------------------------------------------------------------------------------------------------------------------
        @Override
        public int getItemViewType(int position) {

            return position;
        }

        //----------------------------------------------------------------------------------------------------------------------------------------------
        @Override
        public int getCount() {
            return numSlots;
        }

        //----------------------------------------------------------------------------------------------------------------------------------------------
        @Override
        public pilotSettings getItem(int position) {
            if (position >= 0 && position < numSlots) {
                return slots[position];
            } else
                return null;
        }

        //----------------------------------------------------------------------------------------------------------------------------------------------
        @Override
        public long getItemId(int position) {
            if (position >= 0 && position < numSlots) {
                return slots[position].slotid;
            } else
                return 0;
        }

        public void updateRequest()
        {
            notifyDataSetChanged();
        }

        public void updateControlsFromSettings() {


        }

        //----------------------------------------------------------------------------------------------------------------------------------------------
        // getView is called once for each item in the list, corresponds to settings for one slot
        // note: position is the position within the view, but not necessarily the slot number (important when the view is not entirely displayed)
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            PilotViewHolder viewHolder = null;

            // Check if an existing view is being reused, otherwise inflate the view
            // NOTE: getView is called repeatedly, for example when a view is scrolled.
            if (convertView == null) {
                convertView = mInflater.inflate(com.immersionrc.LapRFTiming.R.layout.pilot_setup_item, parent, false);

                viewHolder = new PilotViewHolder();

                // Lookup views for each control
                viewHolder.tvPilotId = (TextView) convertView.findViewById(R.id.slot_item_pilotIdEditText);
                viewHolder.tvPilotName = (TextView) convertView.findViewById(R.id.slot_item_pilotNameEditText);

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (PilotViewHolder) convertView.getTag();
            }

            // Get the data item for this position
            final pilotSettings slot = getItem(position);
            viewHolder.tvPilotId.setText(Integer.toString(slot.slotid));

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            viewHolder.tvPilotName.setText(settings.getString("pilot_name_" + Integer.toString(slot.slotid), "Pilot " + Integer.toString(slot.slotid)));

            //----------------------------------------------------------------------------------------------------------------------------------------------
            class DonePilotNameEditorActionListener implements EditText.OnEditorActionListener
            {
                public byte slotid;

                //----------------------------------------------------------------------------------------------------------------------------------------------
                public DonePilotNameEditorActionListener(byte slotid_in) {
                    super();
                    slotid = slotid_in;
                }

                //----------------------------------------------------------------------------------------------------------------------------------------------
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                        String newName = v.getText().toString();
                        slots[slotid - 1].pilotName = newName;

                        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        final SharedPreferences.Editor editor = settings.edit();
                        editor.putString("pilot_name_" + Integer.toString(slotid), newName);
                        editor.apply();

                        updateRequest();

                        Intent intent = new Intent();
                        setResult( RESULT_OK, intent );

                        return true;
                    }
                    return false;
                }
            }

            viewHolder.tvPilotName.setOnEditorActionListener(new DonePilotNameEditorActionListener(slot.slotid));

            // Return the completed view to render on screen
            return convertView;
        }
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    boolean clicked = false;
    private AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
            // TODO Auto-generated method stub

            //do your job here, position is the item position in ListView
            clicked = true;
        }
    };

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
        setContentView(com.immersionrc.LapRFTiming.R.layout.activity_pilotsetup);

        // find the list view, embedded in the activity, and set it up
        ListView slotSettingsListView = (ListView) findViewById(R.id.racerRowListView);

        slotsettingsadapter = new SlotSettingsAdapter();
        slotSettingsListView.setAdapter(slotsettingsadapter);
        slotSettingsListView.setOnItemClickListener(onItemClickListener);
        slotsettingsadapter.updateRequest();

        // puck name
        //mBluetoothService.getPuckName
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }
}
