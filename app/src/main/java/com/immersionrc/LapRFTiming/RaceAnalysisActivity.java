package com.immersionrc.LapRFTiming;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
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
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

// Race Analysis Activity, shows charts of pilot performance
//
//         <item
//           android:id="@+id/race_analysis_item"
//                   android:title="Race Analysis" />
//

public class RaceAnalysisActivity
        extends AppCompatActivity implements BluetoothServiceBroadcastReceiver
{
    public Handler mHandler;

    public final int numSlots = lapRFConstants.numPilotSlots;

    // Manager to abstract broadcast receiver for receiving data from the bluetooth Service
    BluetoothServiceBroadcastManager mBluetoothServiceManager;

    //----------------------------------------------------------------------------------------------------------------------------------------------
    boolean clicked = false;
    private AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener()
    {
        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3)
        {
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
        setContentView(com.immersionrc.LapRFTiming.R.layout.activity_raceanalysis);

        mBluetoothServiceManager = new BluetoothServiceBroadcastManager(this, this);
        mBluetoothServiceManager.subscribeToIntentReceiver();

        Intent intent = new Intent(this, BluetoothBackgroundService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        UpdateChartFromPassingRecords();
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public void UpdateChartFromPassingRecords()
    {
        // in this example, a LineChart is initialized from xml
        LineChart chart = (LineChart) findViewById(R.id.chart);
        chart.setBackgroundColor(Color.BLACK);

        ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();

        int[] colorPerPilot = {Color.RED,Color.GREEN,Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.DKGRAY, Color.LTGRAY };
        int maxLapsAllPilots = 8;
        // get the lap count for each pilot
        //
        Vector<Integer> lapCounts = mService.decode.allDetectionTable.getLapCount();
        for(int iPilot = 0; iPilot < 8; ++iPilot)
        {
            List<Entry> entries = new ArrayList<Entry>();

            int lapsThisPilot = lapCounts.get(iPilot);
            if(lapsThisPilot > maxLapsAllPilots)
                maxLapsAllPilots = lapsThisPilot;

            for (int iLap = 0; iLap < lapsThisPilot; ++iLap)
            {
                Vector<Laptime> lapTime1 = mService.decode.allDetectionTable.getTableItem(iLap + 1);
                Laptime thisPilotTime = lapTime1.get(iPilot);

                if (thisPilotTime != null)
                {
                    float thisLaptime = thisPilotTime.laptime;
                    entries.add(new Entry((float) iLap, thisLaptime));
                }
            }

            // lookup pilot name from defaults
            //
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String pilotName = settings.getString("pilot_name_" + Integer.toString(iPilot + 1), "Pilot " + Integer.toString(iPilot + 1));

            LineDataSet dataSet = new LineDataSet(entries, pilotName); // add entries to dataset
            dataSet.setLineWidth(3.0f);
            dataSet.setCircleRadius(3f);
            dataSet.setValueFormatter(new CustomFormatter());
            LineData lineData = new LineData(dataSet);
            dataSet.setColor(colorPerPilot[iPilot]);
            dataSet.setValueTextColor(Color.WHITE);
            if(!entries.isEmpty())
                dataSets.add(dataSet); // add the datasets
        }

        LineData data = new LineData(dataSets);

        chart.setData(data);

        // x-axis limit line
        //
        XAxis xAxis = chart.getXAxis();
        xAxis.enableGridDashedLine(10f, 10f, 0f);
        xAxis.setAxisMaximum(maxLapsAllPilots);
        xAxis.setAxisMinimum(1f);
        xAxis.setGridColor(Color.WHITE);
        xAxis.setAxisLineColor(Color.WHITE);
        xAxis.setTextColor(Color.WHITE);

        YAxis yAxis = chart.getAxisLeft();
        yAxis.setGridColor(Color.WHITE);
        yAxis.setAxisLineColor(Color.WHITE);
        yAxis.setTextColor(Color.WHITE);


        yAxis = chart.getAxisRight();
        yAxis.setGridColor(Color.WHITE);
        yAxis.setAxisLineColor(Color.WHITE);
        yAxis.setTextColor(Color.WHITE);

        chart.invalidate(); // refresh
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
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

    public void receivedServiceState(String state) {}
    public void receivedConnectionStatus(boolean connected, int rssi) {}
    public void receivedRFSettings(communicationRfSettings rf_settings){}
    public void receivedSettings(extraSettings extra_settings) {}
    public void receivedStatus(protocolDecoder.communicationStatus status ){}

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    public void receivedDetection(protocolDecoder.communicationDetection detection)
    {
        UpdateChartFromPassingRecords();
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    public void receivedRaceActiveStatus(boolean bIsActive)
    {
         // race active just changed (could be race started, or stopped), update the UI to reflect
        //updateStartStopButtonGraying();

        // update the table
        //RacerRowAdapter.updateRequest();
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

            // mService.sendUpdatePeriod(5000);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    // formatter for the time values shown at each datapoint
    //
    private class CustomFormatter implements IValueFormatter, IAxisValueFormatter
    {
        private DecimalFormat mFormat;

        public CustomFormatter()
        {
            mFormat = new DecimalFormat("###0.00");
        }

        // data
        @Override
        public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
            return mFormat.format(value);
        }

        // YAxis
        @Override
        public String getFormattedValue(float value, AxisBase axis) {
            return mFormat.format(value);
        }
    }

}
