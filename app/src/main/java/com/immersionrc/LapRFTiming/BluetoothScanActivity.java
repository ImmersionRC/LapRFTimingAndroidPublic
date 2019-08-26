// This class implements the view of bluetooth devices scanned by the background scanner
// Background scanner transmits updates using 'intents', namely BROADCAST_DISCOVERED_DEVICE
//
// If this doesn't work, and the view is empty, ensure that the view is sized correctly within
// activity_bluetooth-scan.xml
//
package com.immersionrc.LapRFTiming;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class BluetoothScanActivity extends AppCompatActivity implements BluetoothServiceBroadcastReceiver
{
    BluetoothScanActivity mBluetoothScanActivity;

    private ProgressBar connectionProgress;

    // Manager to abstract broadcast receiver for receiving data from the bluetooth Service
    BluetoothServiceBroadcastManager mBluetoothServiceManager;

    // adapter to display devices discovered by bluetooth service
    public class DevicesArrayAdapter extends BaseAdapter
    {
        private LayoutInflater mInflater;

        public DevicesArrayAdapter()
        {
            mInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public int getCount()
        {
            if (mService != null)
            {
                int numDevices = mService.discoveredDevices.size();
                Log.d("BluetoothScanActivity", "getCount " + Integer.toString(numDevices));
                return numDevices;
            }
            else
                return 0;
        }

        @Override
        public BluetoothDetectedDevice getItem(int position)
        {
            if (mService != null)
                return mService.discoveredDevices.get(position);
            else
                return null;
        }

        @Override
        public long getItemId(int position)
        {
            if (mService != null)
                return position;
            else
                return 0;
        }

        public void updateRequest()
        {
            Log.d("BluetoothScanActivity", "updateRequest");
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            // Get the data item for this position
            BluetoothDetectedDevice device = getItem(position);

            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null)
            {
                // device_item represents a single row of the bluetooth device table
                convertView = mInflater.inflate(com.immersionrc.LapRFTiming.R.layout.device_item, parent, false);
            }

            // Lookup view for mLaptimes <- AC: Laptimes, really?... this view has nothing to do with laptimes
            TextView tvIdx = (TextView) convertView.findViewById(com.immersionrc.LapRFTiming.R.id.tvIndex);
            TextView tvDevice = (TextView) convertView.findViewById(com.immersionrc.LapRFTiming.R.id.tvDevice);

            // Populate the data into the template view using the data object
            tvIdx.setText(Integer.toString(device.deviceIndex));
            tvDevice.setText(device.deviceName +" ( " + device.deviceMAC + " )");

            // Return the completed view to render on screen
            return convertView;
        }
    }

    DevicesArrayAdapter devicesAdapter;

    // Broadcast receiver for receiving status updates from the IntentService
    private class StatusReceiver extends BroadcastReceiver
    {
        // Prevents instantiation
        private StatusReceiver()
        {
        }

        // Called when the BroadcastReceiver gets an Intent it's registered to receive
        @Override
        public void onReceive(Context context, Intent intent)
        {

        /*
         * Handle Intents here.
         */
            if (intent.getAction() == BluetoothBackgroundService.Constants.BROADCAST_DISCOVERED_DEVICE )
            {
                Log.d("BluetoothScanActivity", "BROADCAST_DISCOVERED_DEVICE");
                devicesAdapter.updateRequest();

                // enable connect button as soon as we received one discovered device
                Button connectButton = (Button) findViewById(com.immersionrc.LapRFTiming.R.id.ConnectButton);
                connectButton.setEnabled(true);

            }
            else if (intent.getAction() == BluetoothBackgroundService.Constants.BROADCAST_CONNECTED_STATUS )
            {
                Log.d("BluetoothScanActivity", "BROADCAST_CONNECTED_STATUS");
                boolean status = intent.getBooleanExtra(BluetoothBackgroundService.Constants.EXTENDED_DATA, false);

                if (status)
                {
                    mBluetoothScanActivity.setResult(RESULT_OK);
                    finish();
                }
                else
                {
                    mBluetoothScanActivity.setResult(RESULT_CANCELED);
                    finish();
                }


            }
            else if (intent.getAction() == BluetoothBackgroundService.Constants.BROADCAST_DISCOVERY_STATUS )
            {
                Log.d("BluetoothScanActivity", "BROADCAST_DISCOVERY_STATUS");
                boolean status = intent.getBooleanExtra(BluetoothBackgroundService.Constants.EXTENDED_DATA, false);

                Button scanButton = (Button) findViewById(com.immersionrc.LapRFTiming.R.id.scanButton);
                scanButton.setEnabled(!status);

                Button connectButton = (Button) findViewById(com.immersionrc.LapRFTiming.R.id.ConnectButton);
                connectButton.setEnabled(!status);
            }
            else if (intent.getAction() == BluetoothBackgroundService.Constants.BROADCAST_SERVICE_STATE )
            {
                Log.d("BluetoothScanActivity", "BROADCAST_SERVICE_STATE");
                TextView textView = (TextView) findViewById(com.immersionrc.LapRFTiming.R.id.connectionStateView);
                String data = intent.getStringExtra(BluetoothBackgroundService.Constants.EXTENDED_DATA);

                textView.setText(data);

                if (data.equals("Scanning..."))
                    connectionProgress.setVisibility(View.VISIBLE);
                else if (data.equals("Connecting"))
                    connectionProgress.setVisibility(View.VISIBLE);
                else if (data.equals("Disonnected"))
                    connectionProgress.setVisibility(View.GONE);
                else
                    connectionProgress.setVisibility(View.GONE);

                devicesAdapter.updateRequest();
            }

        }
    }
    StatusReceiver mStatusReceiver;
    ListView devicesListView;

    @Override
    public void onCreate(Bundle savedState)
    {
        super.onCreate(savedState);

        setContentView(com.immersionrc.LapRFTiming.R.layout.activity_bluetooth_scan);       // activity_bluetooth_scan

        // reference for subclasses
        mBluetoothScanActivity = this;

        // list of detected devices display
        devicesAdapter = new DevicesArrayAdapter();

        devicesListView = (ListView) findViewById(com.immersionrc.LapRFTiming.R.id.devicesListView);
        devicesListView.setAdapter(devicesAdapter);

        // get the bluetooth service up and running, and subscribe to various notifications
        //
            mBluetoothServiceManager = new BluetoothServiceBroadcastManager(this, this);
            mBluetoothServiceManager.subscribeToIntentReceiver();

            if ( runBluetoothChecks())
            {
                // Bluetooth is granted and on, start service, service connection will start scanning
                startBluetoothService();
            }

        // broadcast receiver to subscribe to service notifications
        //
            IntentFilter statusIntentFilter = new IntentFilter(
                    BluetoothBackgroundService.Constants.BROADCAST_DISCOVERED_DEVICE);
            statusIntentFilter.addAction(BluetoothBackgroundService.Constants.BROADCAST_DISCOVERY_STATUS);
            statusIntentFilter.addAction(BluetoothBackgroundService.Constants.BROADCAST_CONNECTED_STATUS);
            statusIntentFilter.addAction(BluetoothBackgroundService.Constants.BROADCAST_SERVICE_STATE );

            mStatusReceiver = new StatusReceiver();

            LocalBroadcastManager.getInstance(this).registerReceiver(
                    mStatusReceiver,
                    statusIntentFilter);

        // UI elements,connect button and connection progress bar
        //
            Button connectButton = (Button) findViewById(com.immersionrc.LapRFTiming.R.id.ConnectButton);
            connectButton.setEnabled(false);

            connectionProgress = (ProgressBar) findViewById(com.immersionrc.LapRFTiming.R.id.connectionProgress);
            connectionProgress.setVisibility(View.GONE);

        // initialise connection to service
        // service is started from app to stay permanent
            //Intent intent = new Intent(this, BluetoothBackgroundService.class);
           // bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        //code to execute when the service is shutting down

        // Unregister broadcast listeners
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mStatusReceiver);

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


    //--------------------------------------------------------------------------------------------------------------------------------------------------
    // treats result of spawned activities (basically return codes for async. tasks.
    @Override
    protected void onActivityResult (int requestCode,
                                     int resultCode,
                                     Intent data)
    {
        if (requestCode == REQUEST_GENERAL_SETTINGS)
        {
            if (resultCode == RESULT_OK)
            {
                // general settings activity finished, update the table (update pilot names)
                //mDetectionTableAdapter.updateRequest();
            }
        }

        if (requestCode == REQUEST_CONNECT_BT)
        {
            if (resultCode == RESULT_OK)
            {

            }
        }

        if (requestCode == REQUEST_ENABLE_BT)
        {
            if ( resultCode == RESULT_OK )
            {
                // bluetooth was started, we are OK to start service
                startBluetoothService();
            }
            else if ( resultCode == RESULT_CANCELED )
            {
                // cancelled, can't work, bye bye
                //Toast.makeText(this, com.immersionrc.LapRFTiming.R.string.ble_enabled, Toast.LENGTH_SHORT).show();
                // finish();
            }
        }
    }

    private BluetoothAdapter mBluetoothAdapter;

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    private void startBluetoothService()
    {
        Log.d("BluetoothScanActivity", "startBluetoothService");

        // why on earth are each of these activities re-establishing the connection to the
        // BT background service? Surely we want one connection, and maintain it centrally?
        //
        Intent intent = new Intent(this, BluetoothBackgroundService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    private boolean runBluetoothChecks()
    {
        // ask for bluetooth permissions and bail out if it doesn't exist
        // the asking is asynchronous
        boolean bluetoothAlreadyGranted = askBluetoothPermissions();

        if (bluetoothAlreadyGranted)
        {
            // if bluetooth permissions are already granted, we can check to enable bluetooth
            // if not enabled, ask to enable it
            boolean bluetoothIsOn = checkForBluetoothOn();
            if (bluetoothIsOn)
            {
                // we can start connecting
                return true;
            }
        }
        // if bluetooth is not granted, it will ask for it and reply asynchronously
        return false;
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    private boolean checkForBluetoothOn()
    {
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return false;
        }
        else if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() )
        {
            return true;
        }

        return false;
    }

    // ********************************************************************************************
    // * handle bluetooth permission requests
    private final static int REQUEST_CONNECT_BT = 0x05;
    private final static int REQUEST_GENERAL_SETTINGS = 0x09;

    private final static int REQUEST_ENABLE_BT = 0x42;
    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_BLUETOOTH = 0x43;
    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_BLUETOOTH_LOCATION = 0x44;


    //--------------------------------------------------------------------------------------------------------------------------------------------------
    // are we running on the simulator?
    public static boolean isGenymotion()
    {
        return Build.PRODUCT != null && Build.PRODUCT.contains("x86");
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    protected boolean askBluetoothPermissions()
    {
        // Use this check to determine whether BLE is supported on the device.
        //
        if (!isGenymotion() && !getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            Toast.makeText(this, com.immersionrc.LapRFTiming.R.string.ble_not_supported, Toast.LENGTH_LONG).show();
            finish();

            return false;
        }

        // Here, thisActivity is the current activity
        //
        if ( ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_ADMIN)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.BLUETOOTH) && ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.BLUETOOTH_ADMIN)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                // TODO: still needs asynchronous explanation

                Toast.makeText(this, com.immersionrc.LapRFTiming.R.string.ble_needed, Toast.LENGTH_LONG).show();
            }
            else
            {
                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN}, PERMISSIONS_REQUEST_CODE_ACCESS_BLUETOOTH);

            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED
                    )
            {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH,
                                Manifest.permission.BLUETOOTH_ADMIN},
                        PERMISSIONS_REQUEST_CODE_ACCESS_BLUETOOTH);
                //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method

            }
            else
            {
                //do something, permission was previously granted; or legacy device

                return true;
            }
        }

        if ( ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED )
        {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) )
            {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Toast.makeText(this, com.immersionrc.LapRFTiming.R.string.location_needed, Toast.LENGTH_LONG).show();
                // TODO: still needs asynchronous explanation
            }
            else
            {
                // No explanation needed, we can request the permission.
                //
                // TODO: Does this actually work?
                //
                ActivityCompat.requestPermissions(this,
                                                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                                    PERMISSIONS_REQUEST_CODE_ACCESS_BLUETOOTH_LOCATION);

            }
        }
        else
            return true;

        return false;
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------------
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode)
        {
            case PERMISSIONS_REQUEST_CODE_ACCESS_BLUETOOTH:
            {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    // permission was granted
                    // enable bluetooth
                    if ( checkForBluetoothOn() )
                    {
                        // Bluetooth is granted and on, start service, service connection will start scanning
                        startBluetoothService();
                    }
                }
                else
                {
                    // permission denied
                    finish();
                    Toast.makeText(this, com.immersionrc.LapRFTiming.R.string.ble_needed, Toast.LENGTH_LONG).show();
                }
                return;
            }
            case PERMISSIONS_REQUEST_CODE_ACCESS_BLUETOOTH_LOCATION:
            {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    // permission was granted
                    // enable bluetooth
                    if ( checkForBluetoothOn() )
                    {
                        // Bluetooth is granted and on, start service, service connection will start scanning
                        startBluetoothService();
                    }
                }
                else
                {
                    // permission denied
                    finish();
                    Toast.makeText(this, com.immersionrc.LapRFTiming.R.string.ble_needed, Toast.LENGTH_LONG).show();
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    // ********************************************************************************************
    // interface functions

    // Start scanning
    public void scanBluetooth(View view)
    {
        Log.d("BluetoothScanActivity", "scanBluetooth");
        mService.startBluetoothDiscovery();
    }

    // Connect to selected device
    public void connectBluetooth(View view)
    {
        ListView deviceListView = (ListView) findViewById(com.immersionrc.LapRFTiming.R.id.devicesListView);

        int item_idx = deviceListView.getCheckedItemPosition();

        if (item_idx != -1)
        {
            BluetoothDetectedDevice dev = (BluetoothDetectedDevice)devicesAdapter.getItem(item_idx);

            mService.connectToDiscoveredDevice(dev.deviceIndex);
        }
    }

    // ********************************************************************************************
    // Service connection handling
    BluetoothBackgroundService mService;
    boolean mBound;
    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                   IBinder service)
        {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothBackgroundService.LocalBinder binder = (BluetoothBackgroundService.LocalBinder) service;
            mService = binder.getService();
            mService.referenceCount(1);

            Log.d("BluetoothScanActivity", "onServiceConnected");

            mBound = true;

            //mService.startBluetoothDiscovery();

            // automagically start scan
            scanBluetooth(null);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        mBound = false;
    }
    };

    // ********************************************************************************************
    // instance saving
    // ToDO: add storing to keep data when flipping phone and remember what we were doing
    @Override
    protected void onSaveInstanceState (Bundle outState) {
//        outState.putBoolean(DATA1_KEY, value1);
    }

    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState) {
//        value1 = savedInstanceState.getBoolean(DATA1_KEY);
    }


    public void receivedServiceState(String state){}
    public void receivedConnectionStatus(boolean connected, int rssi){}
    public void receivedRFSettings(communicationRfSettings rf_settings){}
    public void receivedSettings(extraSettings extra_settings) {}
    public void receivedStatus(protocolDecoder.communicationStatus status ) {}
    public void receivedDetection(protocolDecoder.communicationDetection detection) {}
    public void receivedRaceActiveStatus(boolean bIsActive) {}

}
