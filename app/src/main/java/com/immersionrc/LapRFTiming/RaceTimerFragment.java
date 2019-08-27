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
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

/**
 * A fragment with a Google +1 button.
 * Activities that contain this fragment must implement the
 * {@link RaceTimerFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link RaceTimerFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RaceTimerFragment extends Fragment implements BluetoothServiceBroadcastReceiver
{
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private Timer updateTimer;
    private TimerTask updateTimerRefresher;
    final private static int TIMER_UPDATE = 69;
    long startTime = 0;
    TextView raceTimeText;

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    boolean m_bRaceIsActive = false;

    BluetoothServiceBroadcastManager mBluetoothServiceManager;
    private RaceTimerFragment.OnRaceTimeFragmentInteractionListener mListener;

    //private OnFragmentInteractionListener mListener;

    public RaceTimerFragment()
    {
        // Required empty public constructor
    }

    // runs without a timer by reposting this handler at the end of the runnable
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {

        @Override
        public void run()
        {
            if(mService != null) {
                long seconds = mService.decode.allDetectionTable.getRaceTimeSecs();

                int minutes = (int) (seconds / 60);
                seconds = seconds % 60;

                raceTimeText.setText(String.format("%d:%02d", minutes, seconds));

                // if the first crossing hasn't happened yet, then the clock is red
                //
                if (mService.decode.allDetectionTable.getIsFirstCrossing())
                    raceTimeText.setTextColor(Color.RED);
                else
                    raceTimeText.setTextColor(Color.WHITE);

            }
            else
            {
                if(mService == null)
                    Log.d("RaceTimerFragment", "mService == null" );
                if(!mService.decode.allDetectionTable.getRaceActive())
                    Log.d("RaceTimerFragment", "race not active" );

            }
            timerHandler.postDelayed(this, 500);
        }
    };



    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment RaceTimerFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static RaceTimerFragment newInstance(String param1, String param2)
    {
        RaceTimerFragment fragment = new RaceTimerFragment();

        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if (getArguments() != null)
        {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        Intent intent = new Intent(getActivity(), BluetoothBackgroundService.class);
        getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_race_timer, container, false);

        //Find the +1 button
        //mPlusOneButton = (PlusOneButton) view.findViewById(R.id.plus_one_button);

        raceTimeText = (TextView) view.findViewById(R.id.raceTimeText);

        startTime = System.currentTimeMillis();

        return view;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        timerHandler.postDelayed(timerRunnable, 0);
        // Refresh the state of the +1 button each time the activity receives focus.
        //mPlusOneButton.initialize(PLUS_ONE_URL, PLUS_ONE_REQUEST_CODE);
    }

    public void onPause()
    {
        super.onPause();

        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if (mBound) {
            mService.referenceCount(-1);
        }
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri)
    {
       // if (mListener != null)
        //{
            //mListener.onFragmentInteraction(uri);
        //}
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        if (context instanceof RaceTimerFragment.OnRaceTimeFragmentInteractionListener)
        {
            mListener = (RaceTimerFragment.OnRaceTimeFragmentInteractionListener) context;

            mBluetoothServiceManager = new BluetoothServiceBroadcastManager(this, context);
            mBluetoothServiceManager.subscribeToIntentReceiver();

        }
        else
        {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        mListener = null;

        mBluetoothServiceManager.unsubscribe();
    }

    /******************************************************************************************
     * Service handling code
     *
     * when connected to service,
     */
    BluetoothBackgroundService mService;
    boolean mBound;
    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service)
        {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothBackgroundService.LocalBinder binder = (BluetoothBackgroundService.LocalBinder) service;
            mService = binder.getService();     // TODO: Need to get this elsewhere, service is good even if BT bad
            mService.referenceCount(1);

            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0)
        {
            mBound = false;
            mService = null;
        }
    };

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    //public interface OnFragmentInteractionListener
    //{
    //    // TODO: Update argument type and name
    //    void onFragmentInteraction(Uri uri);
    //}

    public interface OnRaceTimeFragmentInteractionListener
    {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }

    public void receivedStatus(protocolDecoder.communicationStatus status ) {}
    public void receivedRaceActiveStatus(boolean bIsActive)
    {
        timerHandler.postDelayed(timerRunnable, 500);
        m_bRaceIsActive = bIsActive;
    }
    public void receivedServiceState(String state) {}
    public void receivedConnectionStatus(boolean connected, int rssi) {}
    public void receivedDetection(protocolDecoder.communicationDetection detection) {}
    public void receivedRFSettings(communicationRfSettings rf_settings){}
    public void receivedSettings(extraSettings extra_settings) {}

}
