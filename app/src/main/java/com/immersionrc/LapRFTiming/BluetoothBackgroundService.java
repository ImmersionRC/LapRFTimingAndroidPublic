package com.immersionrc.LapRFTiming;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

//------------------------------------------------------------------------------------------------------------------------------------
//      Bluetooth service.
//      Bluetooth connection management
//      Exchanges data with timing system
//      Storage of data from timing system
//
//      Broadcast to activities:
//          - connection status
//          - timing system state (race, calibration step state,...)
//          - detections
//
//      TODO: This class needs refactoring, partly to enable ethernet connections to the 8-way
//      - Class needs renaming to LapRFBackgroundService
//          - All race management tasks stay in the service (starting, stopping, announcements, etc.)
//      -
//
//------------------------------------------------------------------------------------------------------------------------------------

public class BluetoothBackgroundService extends Service
{
    // pointer to this for subclasses
    BluetoothBackgroundService mService;

    // TTS engine, and the media player
    TextToSpeech textToSpeechEngine;
    private static MediaPlayer mediaPlayer;

    // count of the number of LapRF activities currently active, used for reference counting, and shutting down the background service
    int numActiveActivities = 0;
    int numSecsSinceUserAppearedActive = 0;

    // Constants for broadcast
    public final class Constants
    {
        // Defines a custom Intent action
        public static final String BROADCAST_ACTION =               "com.immersionrc.android.bluetooth.BROADCAST";

        // Defines the key for the status "extra" in an Intent
        public static final String EXTENDED_DATA_STATUS_TEXT =      "com.immersionrc.android.bluetooth.STATUS_TEXT";

        // Defines the key for the status "extra" in an Intent
        public static final String EXTENDED_DATA =                  "com.immersionrc.android.bluetooth.EXTENDED_DATA";

        // Defines a custom Intent action
        public static final String BROADCAST_STATUS =               "com.immersionrc.android.bluetooth.BROADCAST_STATUS";

        // Defines a custom Intent action
        public static final String BROADCAST_CONNECTED_STATUS =     "com.immersionrc.android.bluetooth.BROADCAST_CONNECTED_STATUS";

        // Defines the key for the status "extra" in an Intent
        public static final String EXTENDED_DATA_STATUS =
                "com.immersionrc.android.bluetooth.STATUS";
        public static final String EXTENDED_DATA_STATUS_RSSI =      "com.immersionrc.android.bluetooth.EXTENDED_DATA_STATUS_RSSI";

        public static final String BROADCAST_RACE_ACTIVE =     "com.immersionrc.android.bluetooth.BROADCAST_RACE_ACTIVE";
        public static final String EXTENDED_DATA_STATUS_RACE_ACTIVE =      "com.immersionrc.android.bluetooth.EXTENDED_DATA_STATUS_RACE_ACTIVE";

        // Defines a custom Intent action
        public static final String BROADCAST_DISCOVERED_DEVICE =    "com.immersionrc.android.bluetooth.BROADCAST_DISCOVERED_DEVICE";

        // Defines the key for the status "extra" in an Intent
        public static final String DISCOVERED_DEVICE_NAME =         "com.immersionrc.android.bluetooth.DISCOVERED_DEVICE_NAME";

        // Defines the key for the status "extra" in an Intent
        public static final String DISCOVERED_DEVICE_INDEX =        "com.immersionrc.android.bluetooth.DISCOVERED_DEVICE_INDEX";
        // Defines the key for the status "extra" in an Intent
        public static final String DISCOVERED_DEVICE_MAC =          "com.immersionrc.android.bluetooth.DISCOVERED_DEVICE_MAC";

        // Defines a custom Intent action
        public static final String BROADCAST_DISCOVERY_STATUS =     "com.immersionrc.android.bluetooth.BROADCAST_DISCOVERY_STATUS";
        public static final String BROADCAST_SERVICE_STATE =        "com.immersionrc.android.bluetooth.BROADCAST_SERVICE_STATE";
        public static final String BROADCAST_SERVICE_STATE_DATA =   "com.immersionrc.android.bluetooth.BROADCAST_SERVICE_STATE_DATA";
    }

    // bluetooth adapter variables
    private BluetoothAdapter mBluetoothAdapter;

    // decoder
    public static protocolDecoder decode;                           // protocol decoder, owned by service, so never destroyed

    //-----------------------------------------------------------------------------------------------------------------
    // Timer and handlers
    // rssi readout timer
    private Timer watchdogTimer;
    private TimerTask watchdogTimerRefresher;

    private Timer rssiTimer;
    private TimerTask rssiTimerRefresher;

    // missing data timer
    private Timer dataTimer;
    private TimerTask dataTimerRefresher;

    // missing status update timer
    private Timer statusTimer;
    private TimerTask statusTimerRefresher;

    final private static int TIMER_RSSI = 42;
    final private static int TIMER_DATA = 43;
    final private static int TIMER_STATUS = 44;

    private int bind_id = 0x00000000;

    private Object writeLock = new Object();                // mutex for write method

    //-----------------------------------------------------------------------------------------------------------------
    public void referenceCount(int delta)
    {
        numActiveActivities += delta;
    }

    // runs without a timer by re-posting this handler at the end of the runnable
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if(mService != null)
            {
                long numActive = mService.numActiveActivities;
                //mService.decode.allDetectionTable.getRaceActive())
                Log.d("BluetoothBackground", "numActiveActivities " + Integer.toString(numActiveActivities));

                // if no activities are active (visible), and the race is not running, increment a counter
                // when that counter reaches some reasonable threshold, shutdown the service, terminate the
                // app, partly to save battery, and partly to avoid problems connecting the puck to another
                // device
                if(numActive == 0)
                {
                    if(!mService.decode.allDetectionTable.getRaceActive())
                    {
                        numSecsSinceUserAppearedActive += 5;
                        if(numSecsSinceUserAppearedActive > (1 * 60))      // 1 minutes idle, kill the puppy
                        {
                            //textToSpeechEngine.speak("LapRF Idle, shutting down", TextToSpeech.QUEUE_FLUSH, null);
                            //android.os.Process.killProcess(android.os.Process.myPid());

                            // in here we need
                        }
                    }
                    else
                        numSecsSinceUserAppearedActive = 0;
                }
                else
                    numSecsSinceUserAppearedActive = 0;
            }
            else
            {
            }
            timerHandler.postDelayed(this, 5000);           // every 5 seconds
        }
    };

    //-----------------------------------------------------------------------------------------------------------------
    private void restartRssiTimer()
    {
        // restart connection timeout timer
        //
        if (rssiTimer != null)
            rssiTimer.cancel();
        rssiTimerRefresher = new TimerTask()
        {
            public void run()
            {
                Message msg = Message.obtain();
                msg.arg1 = TIMER_RSSI;
                mHandler.sendMessage(msg);
            }

            ;
        };

        rssiTimer = new Timer();
        rssiTimer.schedule(rssiTimerRefresher, 1000);               // why 1,000?
    }

    //-----------------------------------------------------------------------------------------------------------------
    private void restartDataTimer()
    {
        // restart connection timeout timer
        if (dataTimer != null)
            dataTimer.cancel();
        dataTimerRefresher = new TimerTask()
        {
            // approx. 10 seconds between requests to obtain the lap count
            public void run()
            {
                Message msg = Message.obtain();
                msg.arg1 = TIMER_DATA;
                mHandler.sendMessage(msg);
            }
        };

        dataTimer = new Timer();
        dataTimer.schedule(dataTimerRefresher, 10000);              // why 10,000?
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void restartStatusTimer()
    {
        // restart connection timeout timer
        if (statusTimer != null)
            statusTimer.cancel();
        statusTimerRefresher = new TimerTask()
        {
            public void run()
            {
                Message msg = Message.obtain();
                msg.arg1 = TIMER_STATUS;
                mHandler.sendMessage(msg);
            }

            ;
        };

        statusTimer = new Timer();
        statusTimer.schedule(statusTimerRefresher, 7000);           // why 7,000?
    }

    // handler for timers
    private Handler mHandler;

    private boolean bluetoothConnected = false;

    //-----------------------------------------------------------------------------------------------------------------
    // Broadcast methods to activities
    public void sendConnectionStatusToListener(boolean b)
    {
        Intent localIntent =
                new Intent(Constants.BROADCAST_CONNECTED_STATUS)
                        // Puts the status into the Intent
                        .putExtra(BluetoothBackgroundService.Constants.EXTENDED_DATA, b );

        // Broadcasts the Intent to receivers in this app.
        //
            LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);

        // This is where the TTS announcing the connection state is handled
        //
            if (textToSpeechEngine != null)
            {
                if (b)
                    textToSpeechEngine.speak("Connected", TextToSpeech.QUEUE_FLUSH, null);
                else
                    textToSpeechEngine.speak("Link Lost", TextToSpeech.QUEUE_FLUSH, null);
            }
    }

    //-----------------------------------------------------------------------------------------------------------------
    // broadcast a race active change to listeners
    public void sendRaceActiveChangeToListener(boolean bRaceActive)
    {
        Intent localIntent =
                new Intent(Constants.BROADCAST_RACE_ACTIVE)
                        // Puts the status into the Intent
                        .putExtra(BluetoothBackgroundService.Constants.EXTENDED_DATA, true )
                        // Puts the status into the Intent
                        .putExtra(Constants.EXTENDED_DATA_STATUS_RACE_ACTIVE, bRaceActive )
                ;

        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    //-----------------------------------------------------------------------------------------------------------------
    // broadcast the current BT RSSI value to listeners
    public void sendConnectionRSSIToListener(int rssi)
    {
        Intent localIntent =
                new Intent(Constants.BROADCAST_CONNECTED_STATUS)
                        // Puts the status into the Intent
                        .putExtra(BluetoothBackgroundService.Constants.EXTENDED_DATA, true )
                        // Puts the status into the Intent
                        .putExtra(Constants.EXTENDED_DATA_STATUS_RSSI, rssi )
                ;

        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void sendStatusToListener(String status)
    {
        Intent localIntent =
                new Intent(BluetoothBackgroundService.Constants.BROADCAST_ACTION)
                        // Puts the status into the Intent
                        .putExtra(BluetoothBackgroundService.Constants.EXTENDED_DATA_STATUS_TEXT, status);

        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void sendDiscoveredDeviceToListener(String deviceName, int device_index, String deviceMAC)
    {
        Intent localIntent =
                new Intent(BluetoothBackgroundService.Constants.BROADCAST_DISCOVERED_DEVICE)
                        // Puts the status into the Intent
                        .putExtra(BluetoothBackgroundService.Constants.DISCOVERED_DEVICE_NAME, deviceName)
                        // Puts the status into the Intent
                        .putExtra(BluetoothBackgroundService.Constants.DISCOVERED_DEVICE_INDEX, device_index)
                        .putExtra(BluetoothBackgroundService.Constants.DISCOVERED_DEVICE_MAC, deviceMAC);

        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void sendDiscoveryStatus(boolean b)
    {
        Intent localIntent =
                new Intent(BluetoothBackgroundService.Constants.BROADCAST_DISCOVERY_STATUS)
                        // Puts the status into the Intent
                        .putExtra(BluetoothBackgroundService.Constants.EXTENDED_DATA, b );

        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    String mServiceState;

    //-----------------------------------------------------------------------------------------------------------------
    // set the service state, a text string containing either the timer name, or something like 'Disconnected', etc.
    // human-readable
    public void setServiceState(String s)
    {
        mServiceState = s;

        Intent localIntent = new Intent(Constants.BROADCAST_SERVICE_STATE)
                .putExtra(Constants.EXTENDED_DATA, s );

        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    //-----------------------------------------------------------------------------------------------------------------
    String getServiceState()
    {
        return mServiceState;
    }

    //-----------------------------------------------------------------------------------------------------------------
    // Constructor, create the main service loop
    public BluetoothBackgroundService()
    {
        Handler mh = new Handler(Looper.getMainLooper());

        mService = this;

        decode = new protocolDecoder(this);

        timerHandler.postDelayed(timerRunnable, 5000);           // every 5 seconds
    }

    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void onCreate()
    {
        textToSpeechEngine = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener()
        {
            @Override
            public void onInit(int status) {
                textToSpeechEngine.setLanguage(Locale.UK);
            }
        });

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Looper is a class which runs a message loop for a thread
        mHandler = new Handler(Looper.getMainLooper())
        {
            // called periodically to request RSSI, Timer data, and Status
            @Override
            public void handleMessage(Message inputMessage)
            {
                if (inputMessage.arg1 == TIMER_RSSI)
                {
                    if (bluetoothConnected)
                    {
                        if(mBluetoothGatt != null)
                            mBluetoothGatt.readRemoteRssi();
                    }
                }
                else if (inputMessage.arg1 == TIMER_DATA)
                {
                    if (bluetoothConnected)
                    {
                        decode.allDetectionTable.checkForMissing();
                        restartDataTimer();
                    }
                }
                else if (inputMessage.arg1 == TIMER_STATUS)
                {
                    // Timed out, send timeout status
                    decode.broadcastFailedStatus();
                }
            }
        };

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        bind_id = settings.getInt("bind_id", 0xDEADBEEF );

        // Register for broadcasts on BluetoothAdapter state change
        //
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mReceiver, filter);

        // Ensures Bluetooth is enabled
        //
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled())
            {
                waitForBluetoothOnToStartScan = true;
            }
            else
            {
                scanLeDevice(true);
            }
    }

    //-----------------------------------------------------------------------------------------------------------------
    public static void ShutdownService()
    {
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public void onDestroy()
    {
        // close bluetooth connection
        close();

        //code to execute when the service is shutting down

        // Unregister broadcast listeners
        unregisterReceiver(mReceiver);
    }

    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public int onStartCommand(Intent intent, int flags, int startid)
    {
        //code to execute when the service is starting up

        return START_STICKY;
    }

    // These methods are called by each of the foreground activities to get a handle to this
    // service. Not 100% clear how yet, when/how is onServiceConnected called? and where
    // does the service argument on that method come from?
    //
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder
    {
        public BluetoothBackgroundService getService()
        {
            return BluetoothBackgroundService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return mBinder;
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------
    public int readIntPreference(String name, int defaultValue, int maxValue)
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int val = settings.getInt(name, defaultValue);
        val = Math.min(val, maxValue);
        return val;
    }

    int timerDuration = 0;
    detectionTable.StartDelay startDelay;

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    // request from the button to start the race
    public void startRace()
    {
        final int resourceId = getResources().getIdentifier("timing_tone", "raw", getApplicationContext().getPackageName());
        final int staggeredStartDeltaTime = 5000;           // time between staggered start launches

        startDelay = detectionTable.StartDelay.values()[readIntPreference("raceStartDelay", 0 /* None */, detectionTable.StartDelay.values().length/* max */)];
        if(startDelay == detectionTable.StartDelay.Fixed5Secs)
            timerDuration = 5000;
        else if(startDelay == detectionTable.StartDelay.Fixed10Secs)
            timerDuration = 10000;
        else if(startDelay == detectionTable.StartDelay.Random5Secs)
        {
            Random r = new Random();
            timerDuration = r.nextInt(4000) + 4000;          // 4000 -> 8000ms random delay, should be async to start after the media
            playMediaAsync("as_we_go_live_on_the_tone_in_less_than_5");
        }
        else if(startDelay == detectionTable.StartDelay.Staggered)
        {
            // hum... whaddawedo here?
            timerDuration = 8 * staggeredStartDeltaTime;
        }
        else
            timerDuration = 0;

        // TODO look this up...
        new CountDownTimer(timerDuration /* total time */, 1000 /* callback frequency */)
        {
            public void onTick(long millisUntilFinished)
            {
                long countdownSecs = Math.round((float) millisUntilFinished / 1000.0);

                Resources res = getResources();
                String[] name = res.getStringArray(R.array.countdown_file_array);

                try
                {
                    // only speak the seconds when not in random mode
                    //
                    if(startDelay == detectionTable.StartDelay.Fixed5Secs || startDelay == detectionTable.StartDelay.Fixed10Secs)
                    {
                        //playMediaAsync("this_race_is_over");          this works in release mode, but the line below doesn't... why?
                        playMediaAsync(name[(int) countdownSecs]);
                    }
                    else if(startDelay == detectionTable.StartDelay.Staggered)
                    {
                        // play every 5 seconds, walk through list of pilots
                    }
                }
                catch(Exception e)
                {

                }
            }

            // start tones are complete, start the race
            //
            public void onFinish()
            {
                mediaPlayer = MediaPlayer.create(getApplicationContext(), resourceId);

                // start tones
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
                {
                    public void onCompletion(MediaPlayer mp)
                    {
                        initRace();
                    }
                });
                try
                {
                    // play the start tone
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    mediaPlayer.start();
                }
                catch(Exception e) {

                }
            }
        }.start();
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void stopRace(boolean bForce)
    {
        if(mService.decode.allDetectionTable.getRaceActive())
        {
            playMediaAsync("this_race_is_over");

            // stop the race immediately, unless the end race behavior is set to just warn
            int reb = readIntPreference("endRaceBehaviour", 0, 2);
            if(bForce || (reb == 0))
            {
                mService.decode.allDetectionTable.setRaceActive(false);
                sendRaceActiveChangeToListener(false);
            }
        }
    }

    //-----------------------------------------------------------------------------------------------------------------
    // are we running on the simulator?
    public static boolean isGenymotion()
    {
        return Build.PRODUCT != null && Build.PRODUCT.contains("x86");
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void playMediaAsync(String mediaFileName)
    {
        Log.d("BluetoothBackground", "playMediaAsync (" + mediaFileName + ")");

        final int resourceId = getResources().getIdentifier(mediaFileName, "raw", getApplicationContext().getPackageName());
        mediaPlayer = MediaPlayer.create(getApplicationContext(), resourceId);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.start();
    }

    long lastCountdownSecs = 0;

    //-----------------------------------------------------------------------------------------------------------------
    // this is where we actually start the race, after any countdown tones
    //
    public void initRace()
    {
        decode.recordAppTimeForRaceStart();
        decode.resetRTCTimeDelta();
        decode.allDetectionTable.setRaceActive(true);
        sendRaceActiveChangeToListener(true);

        decode.allDetectionTable.clear();

        // figure out what kind of race this is going to be
        //
        int raceType = readIntPreference("raceType", 0 /* Practice */, 2 /* max */);
        int raceTime = readIntPreference("raceTime", 0 /* min. value */, 9 /* max */);      // TODO: look up the max value
        int numLaps = readIntPreference("numLaps", 3 /* min. value */, 100 /* max */);      // TODO: look up the max value
        ++numLaps;                                                  // index 0 = 1 minute
        int timerDuration = 0;
        if(raceType == 2)                                           // race type = fixed time
        {
            timerDuration = (raceTime + 2) * 30 * 1000;             // race time in minutes -> ms
            decode.allDetectionTable.setRaceDurationSecs(timerDuration / 1000);
            decode.allDetectionTable.setRaceNumLaps(100);           // default to 100 lap race
        }
        else if(raceType == 1)                                      // race type == num laps
            decode.allDetectionTable.setRaceNumLaps(numLaps);
        else
            decode.allDetectionTable.setRaceNumLaps(100);           // default to 100 lap race

         // if we are in 'from the tone' mode, create the initial passing records
        //
        int startTimeFrom = readIntPreference("startTimeFrom", 0 /* None */, 2 /* max */);
        if(startTimeFrom == 0)       // start tone
        {
            // get rtc_time from the gate
            decode.allDetectionTable.createOnTheTonePassingRecords();
        }

        decode.allDetectionTable.initStartCount();
        requestRaceCount();                                     // reset detection count... or something similar
        requestTimestamp();                                     // we need at least one timestamp

        // Countdown timer for end of race
        //
        if (timerDuration > 0)
        {
            final Handler timerHandler = new Handler();
            Runnable timerRunnable = new Runnable()
            {

                @Override
                public void run()
                {
                    long countdownSecs = decode.allDetectionTable.getRaceTimeRemainingSecs();
                    if (countdownSecs == 0)
                    {
                        stopRace(false);    // don't force it

                        //this.cancel();
                    }
                    else
                        timerHandler.postDelayed(this,500);

                    if(countdownSecs != lastCountdownSecs)
                    {
                        lastCountdownSecs = countdownSecs;

                        // 1 minute of racing left (use 61 so that for 1 minute races it doesn't announce immediately
                        //
                        if (countdownSecs == 61)
                        {
                            playMediaAsync("time_1_minute_of_racing_left");
                        }

                        // 15 seconds to go...
                        //
                        if (countdownSecs == 15)
                        {
                            playMediaAsync("time_15_seconds_left");
                        }

                        // 30 seconds to go...
                        //
                        if (countdownSecs == 30)
                        {
                            playMediaAsync("time_30_seconds_to_go");
                        }
                    }

                }

            };
            timerHandler.postDelayed( timerRunnable,500);

        }

}

    //-----------------------------------------------------------------------------------------------------------------
    // called when a pilot finishes a lap
    public void pilotFinishedLap(int whichPilot, Laptime lt, int thisPilotNumLaps, int maxLapsThisRace)            // 1-based
    {
        // lookup this pilot's name
        //
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String namePilot = settings.getString("pilot_name_" + Integer.toString(whichPilot), "Pilot " + Integer.toString(whichPilot) );

        DecimalFormat df = new DecimalFormat("####.##");
        df.setRoundingMode(RoundingMode.DOWN);

        // if this is the first crossing, with lap time of zero, don't speak the zero
        //
        String announceTxt = namePilot;
        if(lt.laptime < 0.01)
            announceTxt = namePilot;        // first lap
        else
        {
            announceTxt += ", " + df.format(lt.laptime);

            if(thisPilotNumLaps == maxLapsThisRace)
                announceTxt += ", race done";
            else if(thisPilotNumLaps == maxLapsThisRace - 1)
                announceTxt += ", one more lap";
        }

        // we have a choice here, we can add to the queue, or replace it
        // adding seems to make the most sense for now, may want to make this
        // an advanced option
        textToSpeechEngine.speak( announceTxt, TextToSpeech.QUEUE_ADD, null);
    }

    public void tempInitTable()
    {
        decode.allDetectionTable.clear();
    }
    /*****************************************************************************************************************3
     * Protocol sendout functions
     */

    //-----------------------------------------------------------------------------------------------------------------
    public void sendMultiChannelSettings(Vector<communicationRfSettings> settings)
    {
        Log.d("BluetoothBackground", "sendMultiChannelSettings");

        ByteBuffer txBuf = decode.preparePacketWithHeader( protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_RF_SETTINGS );
        for (communicationRfSettings set : settings )
        {
            decode.appendMessageIdInt(txBuf, "PILOT_ID", set.slotid );
            decode.appendMessageIdInt(txBuf, "RF_ENABLE", set.enable );
            decode.appendMessageIdInt(txBuf, "RF_BAND", set.band );
            decode.appendMessageIdInt(txBuf, "RF_CHANNEL", set.channel );
            decode.appendMessageIdInt(txBuf, "RF_GAIN", set.gain );
            decode.appendMessageIdFloat(txBuf, "RF_THRESHOLD", set.threshold );
            decode.appendMessageIdInt(txBuf, "RF_FREQUENCY", set.frequency );
        }

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void sendChannelSettings(int slotid, boolean enable, int channel, int band, int gain, float threshold, int frequency)
    {
        Log.d("BluetoothBackground", "sendChannelSettings");
        ByteBuffer txBuf = decode.preparePacketWithHeader( protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_RF_SETTINGS );
        decode.appendMessageIdInt(txBuf, "PILOT_ID", slotid );
        decode.appendMessageIdInt(txBuf, "RF_ENABLE", enable?1:0 );
        decode.appendMessageIdInt(txBuf, "RF_BAND", band );
        decode.appendMessageIdInt(txBuf, "RF_CHANNEL", channel );
        decode.appendMessageIdInt(txBuf, "RF_GAIN", gain );
        decode.appendMessageIdFloat(txBuf, "RF_THRESHOLD", threshold );
        decode.appendMessageIdInt(txBuf, "RF_FREQUENCY", frequency );

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void sendMinLapTime(float minLapTimeSecs)
    {
        Log.d("BluetoothBackground", "sendMinLapTime");
        ByteBuffer txBuf = decode.preparePacketWithHeader( protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_SETTINGS );
        decode.appendMessageIdInt(txBuf, "IRC_SETTINGS_MIN_LAP_TIME", (int) (minLapTimeSecs * 1000.0f) );
        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void requestMinLapTime()
    {
        Log.d("BluetoothBackground", "requestMinLapTime");
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_SETTINGS);
        decode.appendMessageIdInt(txBuf, "IRC_SETTINGS_MIN_LAP_TIME", 0 );

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        dumpHexToLog(pckt);

        sendToDevice(pckt);
    }

    public void receivedPing(int ping)
    {
        sendPing(ping + 1);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void receivedBindID(int bindID)
    {
        Log.d("BluetoothBackground", "receivedBindID");
        int id_to_send = 0xFFFFFFFF;
        if (bindID == 0xFFFFFFFF || bindID == 0x00000000)  // request for ID
            id_to_send = bind_id;
        else
        {
            // received a new ID, store and send back
            bind_id = bindID;
            id_to_send = bind_id;

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

            SharedPreferences.Editor editor = settings.edit();
            editor.putInt("bind_id", bind_id );
            editor.commit();
        }
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_SETTINGS);
        decode.appendMessageIdInt(txBuf, "IRC_SETTINGS_BIND_ID", id_to_send );
        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void requestRaceCount()
    {
        Log.d("BluetoothBackground", "requestRaceCount");
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_RESEND);
        decode.appendMessageIdInt(txBuf, "DETECTION_COUNT_CURRENT", 0x00 );
        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    long t0 = 0;
    public void requestTimestamp()
    {
        Log.d("BluetoothBackground", "requestTimestamp");
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_TIME );
        decode.appendMessageIdEmpty(txBuf, "RTC_TIME" );
        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);

        // record the time that the packet left the PC, in microseconds
        //
        t0 = decode.time_us();

        // now wait for a packet to arrive with the details
    }

    // correction to timingpuck offset
    public long timestamp_offset = 0;

    //-----------------------------------------------------------------------------------------------------------------
    // compute time sync
    // This is an implementation of the internet NTP service, documented here:
    //     https://en.wikipedia.org/wiki/Network_Time_Protocol
    //
    // t0: client request send out timestamp        app clock
    // t1: server request receive timestamp         puck clock
    // t2: server response send out timestamp       puck clock      microsecs since puck power on
    // t3: client receive timestamp                 app clock
    //
    // Timestamps are being requested with every status message, WHY? are the clocks really
    // so shitty that we need to correct times 1x/sec?.
    public void receivedTimestamp(long t1, long t2, long t3)
    {
        // all times in microseconds
        long delta_p = ( t1 - t0 + t3 - t2 )/2;
        long delta_0 = ( t3 - t2 - t1 + t0 )/2;
        long delta_r = t2 - t1;                     // delay from puck receiving packet, and sending response
        long delta_t = t3 - t0;                     // time between client receiving timestamp, and sending request

        Log.d("Bluetooth timestamping", "t0:\t" + Long.toString(t0) +
                "\tt1:\t" + Long.toString(t1) +
                "\tt2:\t" + Long.toString(t2) +
                "\tt3:\t" + Long.toString(t3) +
                "\tdelta_p:\t" + Long.toString(delta_p) +
                "\tdelta_0:\t" + Long.toString(delta_0) +
                "\tdelta_r:\t" + Long.toString(delta_r) +
                "\tdelta_t:\t" + Long.toString(delta_t)
        );

        timestamp_offset = (long)(delta_0*0.5 + timestamp_offset*0.5);
        Log.d("Bluetooth clock sync", "timestamp_offset:\t" + Long.toString(timestamp_offset));
        // TODO: This value is never used!, all this BT traffic to obtain a value that IS NEVER USED!

        // TODO: use, for now, just the puck time of the sent packet, t2, not the final solution!
        // Need a firmware update in the puck to fix the fact that t1 is zero...
        decode.setRTCTimeDelta(t2);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void requestSystemVersion()
    {
        Log.d("BluetoothBackground", "requestSystemVersion");
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_DESC);

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void dumpHexToLog(byte[] msg)
    {
        String logMsg = "write ";
        for ( int i = 0; i < msg.length; i++)
        {
            Integer val = Integer.valueOf(msg[i]);
            logMsg += String.format("%02x", val);
            logMsg += " ";
        }
        Log.d("dumpHex", logMsg);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void requestChannelSettings(int slotid)
    {
        Log.d("BluetoothBackground", "requestChannelSettings");
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_RF_SETTINGS);
        //decode.appendMessageIdInt( "PILOT_ID", slotid );
        int numSlots = lapRFConstants.numPilotSlots;

        for (int i = 0; i < numSlots; i++)
        {
            decode.appendMessageIdInt(txBuf, "PILOT_ID", i + 1 );
        }

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        dumpHexToLog(pckt);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    // TODO: Not hooked up in firmware, ignore :-(
    public void sendStartRace(boolean enable)
    {
        Log.d("BluetoothBackground", "sendStartRace");
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_STATE_CTRL );
        if (enable)
            decode.appendMessageIdInt(txBuf, "CTRL_REQ_RACE", 1 );
        else
            decode.appendMessageIdInt(txBuf, "CTRL_REQ_RACE", 0 );

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void sendStartCalibration(boolean enable)
    {
        Log.d("BluetoothBackground", "sendStartCalibration");
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_STATE_CTRL );
        if (enable)
            decode.appendMessageIdInt(txBuf, "CTRL_REQ_CAL", 1 );
        else
            decode.appendMessageIdInt(txBuf, "CTRL_REQ_CAL", 0 );

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void sendShutdown()
    {
        Log.d("BluetoothBackground", "sendShutdown");
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_STATE_CTRL );

        decode.appendMessageIdInt(txBuf, "CTRL_REQ_RACE", 0xFF );

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void sendReset()
    {
        Log.d("BluetoothBackground", "sendReset");
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_STATE_CTRL );

        decode.appendMessageIdInt(txBuf, "CTRL_REQ_RACE", 0xFE );

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void sendStartStaticCalibration(int pilot_id)
    {
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_STATE_CTRL );

        decode.appendMessageIdInt(txBuf, "CTRL_REQ_STATIC_CAL", pilot_id );

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void resendPackets(int start, int stop)
    {
        Log.d("BluetoothBackground", "resendPackets");
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_RESEND );

        decode.appendMessageIdInt(txBuf, "DETECTION_COUNT_FROM", start );
        decode.appendMessageIdInt(txBuf, "DETECTION_COUNT_UNTIL", stop );

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void sendUpdatePeriod(int val)
    {
        Log.d("BluetoothBackground", "sendUpdatePeriod");
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_SETTINGS);
        decode.appendMessageIdInt(txBuf, "IRC_SETTINGS_UPDATE_PERIOD_MS", val );

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void sendPing(int ping)
    {
        Log.d("BluetoothBackground", "sendPing");
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_NETWORK);
        decode.appendMessageIdInt(txBuf, "IRC_NETWORK_PING", ping );

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public void sendPuckName(String name)
    {
        ByteBuffer txBuf = decode.preparePacketWithHeader(protocolDecoder.protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_SETTINGS);
        decode.appendMessageIdString(txBuf, "IRC_SETTINGS_NAME", name );

        byte[] pckt = decode.closeAndGetPacket(txBuf);

        sendToDevice(pckt);
    }

    //-----------------------------------------------------------------------------------------------------------------
    public String getPuckName()
    {
        if (mDevice != null)
        {
            return mDevice.getName();
        }
        else {
            return "Disconnected";
        }
    }

    /*****************************************************************************************************************3
     * Bluetooth handling
     */

    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    BluetoothDevice mDevice;

    //-----------------------------------------------------------------------------------------------------------------
    // Connect to selected device in device list
    public void connectToDiscoveredDevice(int device_idx)
    {
        Log.d("BluetoothBackground", "Connecting...");

        mDevice = discoveredDevices.get(device_idx).device;
        mBluetoothGatt = mDevice.connectGatt(this, false, mGattCallback);

        stopLeScanning();
    }

    BluetoothGattService UART_svc;
    BluetoothGattService rx_svc;
    BluetoothGattService tx_svc;
    BluetoothGattCharacteristic rxCharacteristic;
    BluetoothGattCharacteristic txCharacteristic;

    String UARTServiceString = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    String rxCharacteristicString = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"; // receive data from physical UART
    String txCharacteristicString = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"; // send data overphysical UART

    //-----------------------------------------------------------------------------------------------------------------
    // write a message, this cuts a message in 20 byte chunks,
    // sends the rest of the chunks on write complete notification for this characteristic
    int writeIndex = 0;
    byte[] message;
    BlockingQueue<byte[]> writeBuffer = new LinkedBlockingQueue<>();
//    byte[] bytes = writeBuffer.take();

    //-----------------------------------------------------------------------------------------------------------------
    public void sendToDevice(byte[] msg)
    {
        Log.d("BluetoothBackground", "SendBytes to devices");
        message = msg;
        writeIndex = 0;

        writeMessage();
    }

    //-----------------------------------------------------------------------------------------------------------------
    // 'public' entry point to write a message to bluetooth. All that this does is create multiple entries into the
    // write queue, correctly synchronized to avoid multithreading surprises.
    //
    private void writeMessage()
    {
        int maxPacketLen = 20;
        int len = message.length;
        int bytesLeft = len;

        ReentrantLock lock = new ReentrantLock ();
        try {
            if (lock.tryLock(2L, TimeUnit.SECONDS)) {
                try {
                    for (int writeStart = 0; writeStart < len; writeStart += maxPacketLen) {
                        int writeThisTime = Math.min(maxPacketLen, bytesLeft);                      // handle max packet length
                        bytesLeft -= writeThisTime;

                        byte[] sendPacket = Arrays.copyOfRange(message, writeStart, writeStart + writeThisTime);

                        Log.d("BluetoothBackground", "writeMessage: " + Integer.toString(writeStart) + " -> " + Integer.toString(writeStart + writeThisTime));
                        try {
                            // buffer the packet in the queue, in the correct order of course
                            writeBuffer.put(sendPacket);
                        } catch (InterruptedException ex) {
                            System.out.println("Exception in writeBuffer.put: " + ex);
                        }
                    }

                    // write the next entry in the queue, the write complete handler grabs the remainder
                    writeMessageFromQueue();
                } finally {
                    lock.unlock();
                }
            } else {
                // perform alternative actions
                Log.d("BluetoothBackground", "writeMessage deadlocked");
            }
        }
        catch(Exception e)
        {

        }
    }

    //-----------------------------------------------------------------------------------------------------------------
    // called to write a single message from the write queue.
    // can be called at any time, quits immediately if empty
    private void writeMessageFromQueue()
    {
        int nRetries = 2;

        if(writeBuffer.isEmpty() || (txCharacteristic == null))
            return;

        try {
            byte[] bytesThisTime = writeBuffer.take();
            if (bytesThisTime != null) {
                Log.d("BluetoothBackground", "writeMessageFromQueue: " + Integer.toString(bytesThisTime.length) + " bytes");

                while(--nRetries > 0)
                {
                    txCharacteristic.setValue(bytesThisTime);
                    boolean ok = mBluetoothGatt.writeCharacteristic(txCharacteristic);
                    if (!ok) {
                        Log.d("BluetoothBackground", "write FAILED" + Integer.toString(nRetries));
                        //Thread.sleep(10);
                    }
                    else
                        break;
                }
            }
        } catch (Exception ex) {
            System.out.println("Exception in writeMessageFromQueue: " + ex);
        }
    }

    boolean waitForBluetoothOnToStartScan = false;

    //-----------------------------------------------------------------------------------------------------------------
    // broadcast receiver managing bluetooth on/off state changes
    private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED))
            {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state)
                {
                    case BluetoothAdapter.STATE_OFF:
                        break;

                    case BluetoothAdapter.STATE_TURNING_OFF:
                        break;

                    case BluetoothAdapter.STATE_ON:
                        // Bluetooth is turned on, if we are waiting for it (at startup),
                        // start scanning
                        if (waitForBluetoothOnToStartScan)
                        {
                            scanLeDevice(true);
                            waitForBluetoothOnToStartScan = false;
                        }
                        break;

                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };

    //-----------------------------------------------------------------------------------------------------------------
    public void startBluetoothDiscovery()
    {
        // disconnect if currently connected
        if (mBluetoothGatt != null)
        {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
        }

        mDevice = null;
        // forget remembered connection

        SharedPreferences sharedPref = getSharedPreferences("BlueToothPreferences", Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("known_bluetooth_MAC", "" );
        editor.commit();

        // forget devices we know about
        // TODO: add timestamp to discovery, to reuse last scan

        discoveredDevices.clear();

        sendDiscoveredDeviceToListener("", 0, "");

        scanLeDevice(false);

        scanLeDevice(true);
    }

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private String TAG = "BluetoothThingy";

    //-----------------------------------------------------------------------------------------------------------------
    // Callback Methods defined by the BLE API.
    private final BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback()
            {
                // Connected/disconnected management
                // broadcast state to clients
                // start discovery of services when connected
                // on disconnection, restart connection
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                                    int newState)
                {
                    String intentAction;
                    if (newState == BluetoothProfile.STATE_CONNECTED)
                    {
                        mConnectionState = STATE_CONNECTED;
                        setServiceState( mDevice.getName() );
                        Log.i(TAG, "Connected to GATT server.");
                        Log.i(TAG, "Attempting to start service discovery:" +
                                mBluetoothGatt.discoverServices());
                    }
                    else if (newState == BluetoothProfile.STATE_DISCONNECTED)
                    {
                        mConnectionState = STATE_DISCONNECTED;
                        setServiceState("Disconnected...");
                        Log.i(TAG, "Disconnected from GATT server.");

                        mBluetoothGatt.connect();
// TODO:check this
//                        stopLeScanning();
                        bluetoothConnected = false;
// TODO:check this
                        sendConnectionStatusToListener(false);
                    }
                }

                @Override
                // After scanning and connection, look for services
                // New services discovered callback
                // connect to the service for uart rx and tx
                public void onServicesDiscovered(BluetoothGatt gatt, int status)
                {
                    if (status == BluetoothGatt.GATT_SUCCESS)
                    {
                        BluetoothGattService uart_gatt_service = gatt.getService(UUID.fromString(UARTServiceString));

                        if( uart_gatt_service != null )
                        {
                            UART_svc = uart_gatt_service;
                            Log.d("BluetoothBackground", "Connecting UART Characteristic");
                            //Once the service has been found, get the desired characteristics using UUIDs.

                            rxCharacteristic = UART_svc.getCharacteristic(UUID.fromString(rxCharacteristicString));

                            //Choose to get callbacks when the board receives manual input
                            boolean set_worked = mBluetoothGatt.setCharacteristicNotification(rxCharacteristic, true);

                            BluetoothGattDescriptor descriptor = rxCharacteristic.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb" ));
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            mBluetoothGatt.writeDescriptor(descriptor);

                            Log.d("BluetoothBackground", "Connecting RX Characteristic");
                            //Once the service has been found, get the desired characteristics using UUIDs.

                            txCharacteristic = UART_svc.getCharacteristic(UUID.fromString(txCharacteristicString));
                            boolean set_worked2 = mBluetoothGatt.setCharacteristicNotification(txCharacteristic, true);

/*
                            BluetoothGattDescriptor descriptor2 = txCharacteristic.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb" ));
                            descriptor2.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            mBluetoothGatt.writeDescriptor(descriptor2);
*/

                            // store this device we connected to
                            SharedPreferences sharedPref = getSharedPreferences("BlueToothPreferences", Context.MODE_PRIVATE);

                            SharedPreferences.Editor editor = sharedPref.edit();
                            editor.putString("known_bluetooth_MAC", mDevice.getAddress() );
                            editor.commit();

                            // Connected, read system version with a delay when connection is up
                            mHandler.postDelayed(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    requestSystemVersion();
                                    requestTimestamp();             // we need at least one timestamp
                                }
                            }, 10000);
                        }

                        Log.w(TAG, "onServicesDiscovered received: " + status);
                        bluetoothConnected = true;
                        sendConnectionStatusToListener(true);

                        mBluetoothGatt.readRemoteRssi();
                        restartDataTimer();
                    }
                }

                //-----------------------------------------------------------------------------------------------------------------
                @Override
                // Result of a characteristic read operation request
                // received value is stored in characteristic object
                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic characteristic,
                                                 int status)
                {
                    if (status == BluetoothGatt.GATT_SUCCESS)
                    {

                        if ( rxCharacteristic.getUuid().equals( characteristic.getUuid() ) )
                        {
                            byte[] data = characteristic.getValue();
                            Log.d("BluetoothBackground", "Characteristic read" + data.toString());
                        }
                    }

                }

                //-----------------------------------------------------------------------------------------------------------------
                @Override
                // Result of a characteristic write operation
                // writes the value stored in the characteristic
                // when done, we can write the next packet from the buffer
                public void onCharacteristicWrite(BluetoothGatt gatt,
                                                  BluetoothGattCharacteristic characteristic,
                                                  int status)
                {
                    if (status == BluetoothGatt.GATT_SUCCESS)
                    {

                        if ( txCharacteristic.getUuid().equals( characteristic.getUuid() ) )
                        {
                            Log.d("BluetoothBackground", "Characteristic wrote" );

                            writeMessageFromQueue();
                        }
                    }
                    else
                    {
                        Log.d("BluetoothBackground", "Characteristic write failed" );
                    }
                }

                //-----------------------------------------------------------------------------------------------------------------
                @Override
                // Characteristic notification
                public void onCharacteristicChanged(BluetoothGatt gatt,
                                                    BluetoothGattCharacteristic characteristic) {

                    //Log.d("BluetoothBackground", "Characteristic Changed");
                    // check characteristics that changed, start read
                    boolean success = false;
                    if ( rxCharacteristic.getUuid().equals( characteristic.getUuid() ) )
                    {
                        byte[] val = characteristic.getValue();
                        //Log.d("BluetoothBackground", "rx characteristic updated" +new String( val ));
                        //Log.d("BluetoothBackground", "rx characteristic updated " + Arrays.toString(val) );

                        for (byte b : val)
                        {
                            protocolDecoder.protocolStatus sync_receive_status = decode.receiveByte(b);
                            if ( sync_receive_status == protocolDecoder.protocolStatus.PROTOCOL_STATUS_RECEIVED_PACKET )
                            {
                                protocolDecoder.protocolStatus decode_status = decode.decodeReceivedPacket();
                            }
                        }

//                        success = gatt.readCharacteristic(characteristic);
                    }
                    /*
                    else if ( txCharacteristic.getUuid().equals( characteristic.getUuid() ) )
                    {
                        Log.d("BluetoothBackground", "tx characteristic notified" );

                    }
                    */
                }

                //-----------------------------------------------------------------------------------------------------------------
                // RSSI updated
                @Override
                public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status)
                {
                    if (status == BluetoothGatt.GATT_SUCCESS)
                    {
                        sendConnectionRSSIToListener(rssi);
                        // restart rssi readout
                        if (bluetoothConnected)
                        {
                            restartRssiTimer();
                        }
                    }
                }

                //-----------------------------------------------------------------------------------------------------------------
                // MTU was changed
                @Override
                public void onMtuChanged (BluetoothGatt gatt,
                                          int mtu,
                                          int status)
                {
                    if (status == BluetoothGatt.GATT_SUCCESS)
                    {

                    }
                }
            };

    //-----------------------------------------------------------------------------------------------------------------
    public void close()
    {
        if (mBluetoothGatt == null)
        {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    private void stopLeScanning()
    {
        if(mBluetoothAdapter == null)
            return;

        mBluetoothAdapter.stopLeScan(  mLeScanCallback );
    }

    //-----------------------------------------------------------------------------------------------------------------
    private void scanLeDevice(final boolean enable)
    {
        if(mBluetoothAdapter == null)
            return;

        if (enable)
        {
            setServiceState("Scanning...");

            final String[] uuids = new String[]
            {
                    "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
            };

            UUID[] uuids1 = new UUID[1];
            uuids1[0] = UUID.fromString(uuids[0]);
            mBluetoothAdapter.startLeScan(uuids1, mLeScanCallback);
        }
        else
        {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    //-----------------------------------------------------------------------------------------------------------------
    // Bluetooth scanning
    public Vector<BluetoothDetectedDevice> discoveredDevices  = new Vector<BluetoothDetectedDevice>();

    //-----------------------------------------------------------------------------------------------------------------
    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback()
            {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    boolean has_device = false;
                    for ( int i = 0; i < discoveredDevices.size(); i++)
                        if (discoveredDevices.get(i).device.getAddress().equals( device.getAddress() ) )
                            has_device = true;

                    if (!has_device)
                    {
                        BluetoothDetectedDevice detecteddevice = new BluetoothDetectedDevice(
                                device.getName(),
                                discoveredDevices.size(),
                                device.getAddress(),
                                device
                        );

                        // add to listof device
                        discoveredDevices.add(detecteddevice);

                        // notify listener client
                        sendDiscoveredDeviceToListener(device.getName(), discoveredDevices.size(), device.getAddress());

                        SharedPreferences sharedPref = getSharedPreferences("BlueToothPreferences", Context.MODE_PRIVATE);

                        String oldmac = sharedPref.getString( "known_bluetooth_MAC", "" );

                        if (device.getAddress().equals(oldmac))
                        {
                            //we can connect to this device, it's the one we remembered
                            mDevice = device;
                            mBluetoothGatt = mDevice.connectGatt(mService, false, mGattCallback);
                            setServiceState("Connecting to " + device.getName());
                        }
                    }
                }
            };

}
