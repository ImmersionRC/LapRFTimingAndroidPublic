package com.immersionrc.LapRFTiming;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import static com.immersionrc.LapRFTiming.StaticCalibrationWizardActivity.gate_state_e.STATUS_GATE_STATE_CAL_DYN;
import static com.immersionrc.LapRFTiming.StaticCalibrationWizardActivity.gate_state_e.STATUS_GATE_STATE_CAL_STATIC_EMPTY;
import static com.immersionrc.LapRFTiming.StaticCalibrationWizardActivity.gate_state_e.STATUS_GATE_STATE_CAL_STATIC_QUAD;
import static com.immersionrc.LapRFTiming.StaticCalibrationWizardActivity.gate_state_e.STATUS_GATE_STATE_IDLE;
import static com.immersionrc.LapRFTiming.StaticCalibrationWizardActivity.gate_state_e.STATUS_GATE_STATE_RACE;
import static com.immersionrc.LapRFTiming.StaticCalibrationWizardActivity.gate_state_e.STATUS_GATE_STATE_UNKNOWN;

/**
 * Calibration wizard
 * controls and tracks calibration status of gate in static calibration mode
 */
public class StaticCalibrationWizardActivity extends AppCompatActivity {

    public final static int REQUEST_EMPTY_CAL = 0x05;

    // channel request machine
    // 0:   request empty calibration
    // 1-4: request channel calibration.
    //      comes from intent request
    int channel = 0;

    // status of state machine in gate
    enum gate_state_e
    {
        STATUS_GATE_STATE_UNKNOWN,
        STATUS_GATE_STATE_IDLE,
        STATUS_GATE_STATE_RACE,
        STATUS_GATE_STATE_CAL_STATIC_EMPTY,
        STATUS_GATE_STATE_CAL_STATIC_QUAD,
        STATUS_GATE_STATE_CAL_DYN;
    } ;

    private gate_state_e gate_state = STATUS_GATE_STATE_UNKNOWN;

    // can be called when receiving state info from gate
    private void calWizardStateMachine()
    {
        switch(gate_state)
        {
            case STATUS_GATE_STATE_UNKNOWN :
            {
                calWizardTextView.setText("Waiting for gate state");
                // we don't know any states, wait 'till we receive something
                break;
            }
            case STATUS_GATE_STATE_IDLE :
            {
                // gate is idle, if we have a request, send it
                logTextView.setText("Start Empty Cal");

                if (channel >= 0) {
                    if (mService != null)
                    mService.sendStartStaticCalibration(channel);
//                            calibration_wizard_state = CAL_WIZARD_STATE_REQUEST_EMPTY;

                    calWizardTextView.setText("Start Cal");
                }
                else
                {
                    calWizardTextView.setText("Calibration stopped");
                }


                break;
            }
            case STATUS_GATE_STATE_RACE :
            {
                // gate is in race mode, if we have a request, send it

                logTextView.setText("Start Empty Cal");

                if (channel >= 0) {
                    if (mService != null)
                        mService.sendStartStaticCalibration(channel);
//                            calibration_wizard_state = CAL_WIZARD_STATE_REQUEST_EMPTY;

                    calWizardTextView.setText("Start Cal");
                }
                else
                {
                    calWizardTextView.setText("Calibration stopped");
                }

                break;
            }
            case STATUS_GATE_STATE_CAL_STATIC_EMPTY :
            {
                // gate is in empty static cal mode
                calWizardTextView.setText("Empty Cal, turn off all transmitters");
                channel = -1;
                break;
            }
            case STATUS_GATE_STATE_CAL_STATIC_QUAD :
            {
                // gate is in quad cal mode
                calWizardTextView.setText("Quad Cal, turn on quad and hold over gate");
                channel = -1;
                break;
            }
            case STATUS_GATE_STATE_CAL_DYN :
            {
                // gate is in dynamic quad cal mode
                calWizardTextView.setText("Dynamic Quad Cal");
                break;
            }
        }
    }

    TextView logTextView;
    TextView calWizardTextView;
    // Broadcast receiver for receiving status updates from the IntentService
    private class packetReceiver extends BroadcastReceiver
    {
        // Prevents instantiation
        private packetReceiver() {
        }
        // Called when the BroadcastReceiver gets an Intent it's registered to receive
        @Override
        public void onReceive(Context context, Intent intent) {

        /*
         * Handle Intents here.
         */
            if (intent.getAction() == protocolDecoder.Constants.BROADCAST_PROTOCOL_STATUS)
            {
                protocolDecoder.communicationStatus status = (protocolDecoder.communicationStatus)intent.getSerializableExtra(protocolDecoder.Constants.EXTENDED_DATA_PROTOCOL_STATUS);
                logTextView.append(  "st("+ Byte.toString(status.state) + ") 1: "  + status.base[0]
                                    + "\t2:"  + status.base[1]
                                    + "\t3:"  + status.base[2]
                                    + "\t4:"  + status.base[3]
                                    + "\r\n" );

                if (status.state == 0 )
                    gate_state = STATUS_GATE_STATE_IDLE;
                else if (status.state == 1 )
                    gate_state = STATUS_GATE_STATE_RACE;
                else if (status.state == 2 )
                    gate_state = STATUS_GATE_STATE_CAL_STATIC_EMPTY;
                else if (status.state == 3 )
                    gate_state = STATUS_GATE_STATE_CAL_STATIC_QUAD;
                else if (status.state == 4 )
                    gate_state = STATUS_GATE_STATE_CAL_DYN;

                calWizardStateMachine();

                TextView textView = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.calibrationWizStatusTextBox);
                textView.setText( Short.toString(status.noise) );
                if (status.connection == 0) {
                    textView.setBackgroundColor(Color.GREEN);
                } else if (status.connection == -1) {
                    textView.setBackgroundColor(Color.RED);
                } else {
                    textView.setBackgroundColor(Color.rgb(0x00, 0xDD, 0x00));
                }

                ProgressBar p1 = (ProgressBar) findViewById(com.immersionrc.LapRFTiming.R.id.progress_bar_1);
                ProgressBar p2 = (ProgressBar) findViewById(com.immersionrc.LapRFTiming.R.id.progress_bar_2);
                ProgressBar p3 = (ProgressBar) findViewById(com.immersionrc.LapRFTiming.R.id.progress_bar_3);
                ProgressBar p4 = (ProgressBar) findViewById(com.immersionrc.LapRFTiming.R.id.progress_bar_4);

                p1.setProgress((int)status.base[0]);
                p2.setProgress((int)status.base[1]);
                p3.setProgress((int)status.base[2]);
                p4.setProgress((int)status.base[3]);

                TextView level_text_view_1 = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.level_text_view_1);
                TextView level_text_view_2 = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.level_text_view_2);
                TextView level_text_view_3 = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.level_text_view_3);
                TextView level_text_view_4 = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.level_text_view_4);

                level_text_view_1.setText(Integer.toString( (int)status.base[0] ) );
                level_text_view_2.setText(Integer.toString( (int)status.base[1] ) );
                level_text_view_3.setText(Integer.toString( (int)status.base[2] ) );
                level_text_view_4.setText(Integer.toString( (int)status.base[3] ) );
            }
            else if (intent.getAction() == protocolDecoder.Constants.BROADCAST_PROTOCOL_CALIBRATION_LOG)
            {
                protocolDecoder.communicationCalibrationLog calibration_log = (protocolDecoder.communicationCalibrationLog)intent.getSerializableExtra(protocolDecoder.Constants.EXTENDED_DATA_PROTOCOL_CALIBRATION_LOG);

                TextView textView = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.calLogEditText);
                textView.append(      "P: "  + calibration_log.pilot
                        + " t: " + calibration_log.time
                        + " h: " + calibration_log.height
                        + " np: " + calibration_log.numPeak
                        + " b: " + calibration_log.baseLevel
                        + "\r\n" );
            }
            else if (intent.getAction() == protocolDecoder.Constants.BROADCAST_PROTOCOL_CALIBRATION_STATE)
            {
                protocolDecoder.communicationStaticCalibration calibration_state = (protocolDecoder.communicationStaticCalibration)intent.getSerializableExtra(protocolDecoder.Constants.EXTENDED_DATA_PROTOCOL_CALIBRATION_STATE);

                TextView textView = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.calLogEditText);
                textView.append(      "cal state: "  + calibration_state.state + "\r\n" );
            }

        }
    }

    packetReceiver mpacketReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.immersionrc.LapRFTiming.R.layout.activity_calibration_wizard_activity);
        Toolbar toolbar = (Toolbar) findViewById(com.immersionrc.LapRFTiming.R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent thisintent = getIntent();

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



        calWizardTextView = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.calWizardText);

        logTextView = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.calLogEditText);

        logTextView.setMovementMethod(new ScrollingMovementMethod());

        // The filter's action is BROADCAST_ACTION
        IntentFilter statusIntentFilter = new IntentFilter(        protocolDecoder.Constants.BROADCAST_PROTOCOL_STATUS);
        statusIntentFilter.addAction(protocolDecoder.Constants.BROADCAST_PROTOCOL_RF_SETTINGS);
        statusIntentFilter.addAction(protocolDecoder.Constants.BROADCAST_PROTOCOL_SETTINGS);
        statusIntentFilter.addAction(BluetoothBackgroundService.Constants.BROADCAST_CONNECTED_STATUS);
        statusIntentFilter.addAction(protocolDecoder.Constants.BROADCAST_PROTOCOL_CALIBRATION_LOG);
        statusIntentFilter.addAction(protocolDecoder.Constants.BROADCAST_PROTOCOL_CALIBRATION_STATE);

        mpacketReceiver = new packetReceiver();
        // Registers the DownloadStateReceiver and its intent filters
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mpacketReceiver,
                statusIntentFilter);

        // bind to bluetooth service
        Intent intent = new Intent(this, BluetoothBackgroundService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        // initialise state machine tracker
        gate_state = STATUS_GATE_STATE_UNKNOWN;
        calWizardStateMachine();
    }

    void abortStaticCalibration(View view)
    {
        mService.sendStartStaticCalibration(-1);
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


        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

}
