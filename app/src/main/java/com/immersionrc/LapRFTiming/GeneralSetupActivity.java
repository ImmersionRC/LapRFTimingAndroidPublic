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

/**
 * Handle setup of LapRF
 *  - pilot frequencies and gain
 *  - puck name
 *
 *  TODO: - use adapter and list for pilots
 *
 */

public class GeneralSetupActivity
        extends AppCompatActivity {
    public Handler mHandler;

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
        setContentView(com.immersionrc.LapRFTiming.R.layout.activity_generalsetup);

        // race start delay
        Spinner spinnerRaceStartDelay = (Spinner) findViewById(R.id.spinnerRaceStartDelay);
        spinnerRaceStartDelay.setSelection(readIntPreference("raceStartDelay", 0, spinnerRaceStartDelay.getCount()-1));
        spinnerRaceStartDelay.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                writeIntPreference("raceStartDelay", position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

                // sometimes you need nothing here
            }
        });

        // race start delay
        Spinner spinnerStartTimeFrom = (Spinner) findViewById(R.id.spinnerStartTimeFrom);
        spinnerStartTimeFrom.setSelection(readIntPreference("startTimeFrom", 0, spinnerStartTimeFrom.getCount()-1));
        spinnerStartTimeFrom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                writeIntPreference("startTimeFrom", position);

                switch (position)
                {
                    case 0:     // Start Tone
                        break;
                    case 1:     // First To Gate
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

                // sometimes you need nothing here
            }
        });

        // race type
        Spinner spinnerRaceType = (Spinner) findViewById(R.id.spinnerRaceType);
        int rt = readIntPreference("raceType", 0, spinnerRaceType.getCount()-1);
        spinnerRaceType.setSelection(rt);
        spinnerRaceType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                writeIntPreference("raceType", position);

                Spinner spinnerNumLaps = (Spinner) findViewById(R.id.spinnerNumLaps);
                Spinner spinnerRaceTime = (Spinner) findViewById(R.id.spinnerRaceTime);

                switch (position)
                {
                    case 0:     // Practice
                        spinnerNumLaps.setEnabled(false);
                        spinnerRaceTime.setEnabled(false);
                        break;
                    case 1:     // Lap count
                        spinnerNumLaps.setEnabled(true);
                        spinnerRaceTime.setEnabled(false);
                        break;
                    case 2:     // Fixed time
                        spinnerNumLaps.setEnabled(false);
                        spinnerRaceTime.setEnabled(true);
                        break;

                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

                // sometimes you need nothing here
            }
        });

        // number of laps
        Spinner spinnerNumLaps = (Spinner) findViewById(R.id.spinnerNumLaps);
        int nl = readIntPreference("numLaps", 0, spinnerNumLaps.getCount()-1);
        spinnerNumLaps.setSelection(nl);
        spinnerNumLaps.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                writeIntPreference("numLaps", position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

                // sometimes you need nothing here
            }
        });

        // race time
        Spinner spinnerRaceTime = (Spinner) findViewById(R.id.spinnerRaceTime);
        int rtime = readIntPreference("raceTime", 0, spinnerRaceTime.getCount()-1);
        spinnerRaceTime.setSelection(rtime);
        spinnerRaceTime.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                writeIntPreference("raceTime", position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

                // sometimes you need nothing here
            }
        });

        // end race behaviour
        Spinner spinnerEndRaceBehaviour = (Spinner) findViewById(R.id.spinnerEndRaceBehaviour);
        int erb = readIntPreference("endRaceBehaviour", 0, spinnerRaceTime.getCount()-1);
        spinnerEndRaceBehaviour.setSelection(erb);
        spinnerEndRaceBehaviour.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                writeIntPreference("endRaceBehaviour", position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

                // sometimes you need nothing here
            }
        });



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
