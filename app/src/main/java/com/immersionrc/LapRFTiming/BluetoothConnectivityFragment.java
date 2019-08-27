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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;



/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link BluetoothConnectivityFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link BluetoothConnectivityFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class BluetoothConnectivityFragment extends Fragment implements BluetoothServiceBroadcastReceiver
{
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    // Manager to abstract broadcast receiver for receiving data from the bluetooth Service
    BluetoothServiceBroadcastManager mBluetoothServiceManager;

    private OnBluetoothFragmentInteractionListener mListener;

    private ProgressBar connectionProgress;

    public BluetoothConnectivityFragment()
    {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment BluetoothConnectivityFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static BluetoothConnectivityFragment newInstance(String param1, String param2)
    {
        BluetoothConnectivityFragment fragment = new BluetoothConnectivityFragment();
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
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_bluetooth_connectivity, container, false);

        return v;

    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        if (context instanceof OnBluetoothFragmentInteractionListener)
        {
            mListener = (OnBluetoothFragmentInteractionListener) context;

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
    public void onDetach() {
        super.onDetach();
        mListener = null;

        mBluetoothServiceManager.unsubscribe();
    }

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
    public interface OnBluetoothFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }


    /******************************************************************************************
     *  Service broadcast reactions
     * @param status
     */
    //-------------------------------------------------------------------------------------------------------------------------------------
    public void receivedStatus(protocolDecoder.communicationStatus status )
    {
        if (status != null)
        {
            TextView textView = (TextView) getActivity().findViewById(com.immersionrc.LapRFTiming.R.id.bluetoothFragmentConnectionStateView);

            if (status.connection == 0 || status.connection == 1)                 // connected, toggles 0->1->0 as status packets received
            {
                textView.setBackgroundResource(R.drawable.rounded_corner_edittext_green);
            }
            else if (status.connection == -1)           // failed connection
            {
                textView.setBackgroundResource(R.drawable.rounded_corner_edittext_red);
            }
            else
            {
                textView.setBackgroundResource(R.drawable.rounded_corner_edittext_orange);
            }

            // set the background color of the puck voltage based upon the battery level
            //
            TextView voltageView = (TextView) getActivity().findViewById(R.id.bluetoothFragmentVoltageView);
            short STATUS_FLAGS_LOW_BATTERY = 0x0001;
            if (status.hasStatusFlags )
            {
                if ( (short)(status.statusFlags & STATUS_FLAGS_LOW_BATTERY) == STATUS_FLAGS_LOW_BATTERY)
                    voltageView.setBackgroundResource(R.drawable.rounded_corner_edittext_orange);
                else
                    voltageView.setBackgroundResource(R.drawable.rounded_corner_edittext_green);
             }

            // set the battery voltage text
            //
            Float v = (status.voltage) / 1000.0f;
            voltageView.setText("Batt:" + String.format ("%.1f", v) + " V" + " : " + status.firmwareVersion);
        }
    }

    //-------------------------------------------------------------------------------------------------------------------------------------
    // called when the race active state changes
    public void receivedRaceActiveStatus(boolean bIsActive)
    {

    }

    //-------------------------------------------------------------------------------------------------------------------------------------
    public void receivedServiceState(String state)
    {
        // show progression of connection to a device
        TextView textView = (TextView) getActivity().findViewById(com.immersionrc.LapRFTiming.R.id.bluetoothFragmentConnectionStateView);
        textView.setText(state);

        connectionProgress = (ProgressBar) getActivity().findViewById(com.immersionrc.LapRFTiming.R.id.bluetoothFragmentConnectionProgress);

        connectionProgress.setVisibility(View.GONE);

        if (state.equals("Scanning..."))
            connectionProgress.setVisibility(View.VISIBLE);
        else if (state.equals("Connecting"))
            connectionProgress.setVisibility(View.VISIBLE);
        else if (state.equals("Disonnected"))
            connectionProgress.setVisibility(View.GONE);
        else
            connectionProgress.setVisibility(View.GONE);

    }

    //-------------------------------------------------------------------------------------------------------------------------------------
    public void receivedConnectionStatus(boolean connected, int rssi)
    {
        // show state of connection
        FragmentActivity activity = getActivity();
        if(activity == null)
            return;

        TextView textView = (TextView) activity.findViewById(com.immersionrc.LapRFTiming.R.id.bluetoothFragmentBluetoothStatusTextBox);

        if ( connected )
        {
            textView.setBackgroundResource(R.drawable.rounded_corner_edittext_green);
        }
        else
        {
            textView.setBackgroundResource(R.drawable.rounded_corner_edittext_red);
        }

        String rssiText = String.format("%d dBm", rssi);
        textView.setText(rssiText);

    }

    //-------------------------------------------------------------------------------------------------------------------------------------
    public void receivedDetection(protocolDecoder.communicationDetection detection)
    {
    }

    public void receivedRFSettings(communicationRfSettings rf_settings){}
    public void receivedSettings(extraSettings extra_settings) {}
}
