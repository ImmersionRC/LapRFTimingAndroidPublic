// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
// Popup dialog responsible for determining frequency from a selected band and channel
//
//
package com.immersionrc.LapRFTiming;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;

public class SelectFrequencyActivity extends AppCompatActivity {

    public final static int SELECT_FREQUENCY = 0x42;

    private RecyclerView mFrequenciesRecyclerView;
    private FrequenciesAdapter mFrequenciesAdapter;
    private RecyclerView.LayoutManager mFrequenciesLayoutManager;

    private ArrayList<FrequencyItem> mFrequencies = new ArrayList<FrequencyItem>(); // contains the frequencies for the selected band only
/*
             5740, 5760, 5780, 5800, 5820, 5840, 5860, 5880,			// airwave
             5658, 5695, 5732, 5769, 5806, 5843, 5880, 5917,			// RaceBand
             5705, 5685, 5665, 5645, 5885, 5905, 5925, 5945,			// Boscam E
             5733, 5752, 5771, 5790, 5809, 5828, 5847, 5866, 			// Boscam B
             5865, 5845, 5825, 5805, 5785, 5765, 5745, 5725,			// Boscam A
             5362, 5399, 5436, 5473, 5510, 5547, 5584, 5621,			// 'Test Band' (indoor/development use only)
             5740, 5764, 5788, 5812, 5836, 5860, 0xFFFF, 0xFFFF };		// 'RaceBand EU' (indoor/development use only)
*/

private FrequencyItem[][] mFrequenciesTable = {                         // contains the frequencies for all bands
            {
                    new FrequencyItem(5362, false),                     // Low Band
                    new FrequencyItem(5399, false),
                    new FrequencyItem(5436, false),
                    new FrequencyItem(5473, false),
                    new FrequencyItem(5510, false),
                    new FrequencyItem(5547, false),
                    new FrequencyItem(5584, false),
                    new FrequencyItem(5621, false)
            },
            {
                    new FrequencyItem(5740, false),                     // IRC Band
                    new FrequencyItem(5760, false),
                    new FrequencyItem(5780, false),
                    new FrequencyItem(5800, false),
                    new FrequencyItem(5820, false),
                    new FrequencyItem(5840, false),
                    new FrequencyItem(5860, false),
                    new FrequencyItem(5880, false)
            },
            {
                    new FrequencyItem(5658, false),                     // Race Band
                    new FrequencyItem(5695, false),
                    new FrequencyItem(5732, false),
                    new FrequencyItem(5769, false),
                    new FrequencyItem(5806, false),
                    new FrequencyItem(5843, false),
                    new FrequencyItem(5880, false),
                    new FrequencyItem(5917, false)
            },
            {
                    new FrequencyItem(5705, false),                     // Boscam E
                    new FrequencyItem(5685, false),
                    new FrequencyItem(5665, false),
                    new FrequencyItem(5645, false),
                    new FrequencyItem(5885, false),
                    new FrequencyItem(5905, false),
                    new FrequencyItem(5925, false),
                    new FrequencyItem(5945, false)
            },
            {
                    new FrequencyItem(5733, false),                     // Boscam B
                    new FrequencyItem(5752, false),
                    new FrequencyItem(5771, false),
                    new FrequencyItem(5790, false),
                    new FrequencyItem(5809, false),
                    new FrequencyItem(5828, false),
                    new FrequencyItem(5847, false),
                    new FrequencyItem(5866, false)
            },
            {
                    new FrequencyItem(5865, false),                     // Boscam A
                    new FrequencyItem(5845, false),
                    new FrequencyItem(5825, false),
                    new FrequencyItem(5805, false),
                    new FrequencyItem(5785, false),
                    new FrequencyItem(5765, false),
                    new FrequencyItem(5745, false),
                    new FrequencyItem(5725, false)
            }
    };

    int displayed_band = 1;
    int set_band = 0;
    int set_channel = 0;
    int set_frequency = 0;
    int pilot_id = 0;

    public class SpacesItemDecoration extends RecyclerView.ItemDecoration
    {
        private final int mSpace;
        public SpacesItemDecoration(int space) {
            this.mSpace = space;
        }
        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state)
        {
            outRect.left = mSpace;
            outRect.right = mSpace;
            outRect.bottom = mSpace;
            outRect.top = mSpace;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_frequency);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent thisintent = getIntent();

        // extra casting to avoid casting exceptions
        //
        pilot_id = new Integer(thisintent.getIntExtra("pilot_id", 0));
        set_band = new Integer(thisintent.getIntExtra("current_band", 0));
        set_channel = new Integer(thisintent.getIntExtra("current_channel", 0));
        set_frequency = new Integer(thisintent.getIntExtra("current_frequency", 0));

        setBandChannelFrequencyText();

/*
        if (savedInstanceState == null)
        {
            // we got started with a call, reread
            channel = thisintent.getIntExtra("channel", -2);
        }
        else
        {
            channel = savedInstanceState.getInt("channel", -1);
        }

        if (channel < 0)
        {
            Toast.makeText(this, "No cal operation selected", Toast.LENGTH_SHORT).show();
        }
        */

         mFrequenciesRecyclerView = (RecyclerView) findViewById(R.id.frequenciesRecyclerView);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        //
        mFrequenciesRecyclerView.setHasFixedSize(true);

        SpacesItemDecoration decoration = new SpacesItemDecoration(4);

        mFrequenciesRecyclerView.addItemDecoration(decoration);
        // use a linear layout manager
/*
        mFrequenciesLayoutManager = new GridLayoutManager(this);
        mFrequenciesRecyclerView.setLayoutManager(mFrequenciesLayoutManager);
*/
        mFrequenciesLayoutManager =
                new StaggeredGridLayoutManager(8, StaggeredGridLayoutManager.VERTICAL);
        // Attach the layout manager to the recycler view
        mFrequenciesRecyclerView.setLayoutManager(mFrequenciesLayoutManager);

        // specify an adapter (see also next example)
        mFrequenciesAdapter = new FrequenciesAdapter(mFrequencies);
        mFrequenciesRecyclerView.setAdapter(mFrequenciesAdapter);

        displayBandChannelSelection();

        // Listener definitions
        mFrequenciesAdapter.setOnItemClickListener(new FrequenciesAdapter.OnItemClickListener()
        {
            @Override
            public void onItemClick(View view, int position)
            {
                EditText eText = (EditText)findViewById(R.id.frequencyEditText);
                eText.setText(Integer.toString(mFrequencies.get(position).frequency));

                for (int i = 0; i < mFrequencies.size(); i++)
                    mFrequencies.get(i).selected = false;

                mFrequencies.get(position).selected = true;

                set_channel = position + 1;
                set_frequency = mFrequencies.get(position).frequency;

                mFrequenciesAdapter.notifyDataSetChanged();

                setBandChannelFrequencyText();
            }
        });

        // on click handler for frequency text
        //
        EditText frequencyEditText = (EditText)findViewById(R.id.frequencyEditText);
        frequencyEditText.setOnKeyListener(new View.OnKeyListener()
        {
            public boolean onKey(View v, int keyCode, KeyEvent event)
            {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    setFrequencyDirect();
                    return true;
                }
                return false;
            }
        });

    }

    //------------------------------------------------------------------------------------------------
    public void displayBandChannelSelection()
    {
        if (set_band == 0)
        {
            RadioGroup bandRadioGroup = (RadioGroup) findViewById(R.id.bandRadioGroup);
            bandRadioGroup.clearCheck();

            mFrequencies.clear();
            set_band = 0;
            set_channel = 0;
        }
        else
        {
            RadioGroup bandRadioGroup = (RadioGroup) findViewById(R.id.bandRadioGroup);
            if (set_band == 1)
                bandRadioGroup.check(R.id.bandLRadioButton);
            else if (set_band == 2)
                bandRadioGroup.check(R.id.bandIRadioButton);
            else if (set_band == 3)
                bandRadioGroup.check(R.id.bandRRadioButton);
            else if (set_band == 4)
                bandRadioGroup.check(R.id.bandERadioButton);
            else if (set_band == 5)
                bandRadioGroup.check(R.id.bandBRadioButton);
            else if (set_band == 6)
                bandRadioGroup.check(R.id.bandARadioButton);

            mFrequencies.clear();
            mFrequencies.addAll(Arrays.asList(mFrequenciesTable[set_band - 1]));

            set_frequency = mFrequenciesTable[set_band - 1][set_channel - 1].frequency;

            for (int i = 0; i < mFrequencies.size(); i++)
                mFrequencies.get(i).selected = false;

            mFrequencies.get(set_channel - 1).selected = true;

            setBandChannelFrequencyText();
        }
    }

    //------------------------------------------------------------------------------------------------
    public void setBand(int band)
    {
        if (band == 0)
        {
            RadioGroup bandRadioGroup = (RadioGroup) findViewById(R.id.bandRadioGroup);
            bandRadioGroup.clearCheck();

            mFrequencies.clear();
            set_band = 0;
            set_channel = 0;
        }
        else
        {
            mFrequencies.clear();
            mFrequencies.addAll(Arrays.asList(mFrequenciesTable[band - 1]));

            set_band = band;
            set_channel = 1;
            set_frequency = mFrequenciesTable[set_band - 1][set_channel - 1].frequency;

            for (int i = 0; i < mFrequencies.size(); i++)
                mFrequencies.get(i).selected = false;

            mFrequencies.get(set_channel - 1).selected = true;
            setBandChannelFrequencyText();
        }
         mFrequenciesAdapter.notifyDataSetChanged();
    }

    //------------------------------------------------------------------------------------------------
    public void setBandChannelFrequencyText()
    {
        EditText frequencyText = (EditText)findViewById( R.id.frequencyEditText );
        frequencyText.setText(Integer.toString(set_frequency));
    }

    //------------------------------------------------------------------------------------------------
    public void onBandRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch(view.getId())
        {
            case R.id.bandLRadioButton:
                if (checked)
                    setBand(1);
                break;
            case R.id.bandIRadioButton:
                if (checked)
                    setBand(2);
                    break;
            case R.id.bandRRadioButton:
                if (checked)
                    setBand(3);
                break;
            case R.id.bandERadioButton:
                if (checked)
                    setBand(4);
                break;
            case R.id.bandBRadioButton:
                if (checked)
                    setBand(5);
                break;
            case R.id.bandARadioButton:
                if (checked)
                    setBand(6);
                break;
        }
    }

    //------------------------------------------------------------------------------------------------
    public void setFrequencyDirect()
    {
        // protect against erroneous user entry here (blank string for example)
        //
        Integer freqMHz;
        EditText eText = (EditText) findViewById(R.id.frequencyEditText);
        try {
            Float freqMHzFloat = Float.parseFloat(eText.getText().toString());

            freqMHz = freqMHzFloat.intValue();
            // Limit to 5200-6000MHz, the range of the PLL
            if (freqMHz < 5200)
                freqMHz = 5200;
            else if (freqMHz > 6000)
                freqMHz = 6000;
        }
        catch(Exception e) {
            freqMHz = 5800;             // default value if all goes wrong
        }
        eText.setText(freqMHz.toString());

        set_band = 0;
        set_channel = 0;
        set_frequency = freqMHz;

        setBand(0);

        setBandChannelFrequencyText();
    }

    //------------------------------------------------------------------------------------------------
    public void setFrequencyClick(View view)
    {
        Intent intent = new Intent();
        intent.putExtra("set_band",set_band);
        intent.putExtra("set_channel",set_channel);
        intent.putExtra("set_frequency",set_frequency);
        intent.putExtra("pilot_id",pilot_id);
        setResult(RESULT_OK,intent);
        finish();//finishing activity
    }

    //------------------------------------------------------------------------------------------------
    public void cancelClick(View view)
    {
        Intent intent = new Intent();
        setResult( RESULT_CANCELED, intent );
        finish();//finishing activity
    }
}
