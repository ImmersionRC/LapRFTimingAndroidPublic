// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
/**
 * Created by urssc on 1/24/2017.
 *
 * decodes packet sent out by LapRF timing gate
 */
package com.immersionrc.LapRFTiming;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

public class protocolDecoder
{
    public static final byte PACKET_START_SYMBOL = (byte)0x5a;
    public static final byte protocol_version = (byte)0x01;
    public static final byte PACKET_STOP_SYMBOL = (byte)0x5b;
    public static final byte PACKET_ESCAPE_SEQUENCE = (byte)0x5c;
    public static final byte ESCAPE_SEQUENCE_OFFSET = (byte)0x40;
        
    detectionTable allDetectionTable;
    communicationDesc versionDesc = new communicationDesc();

    private static crc crcCalc;
    private long mStatusPacketCounter = 0;

    private long mAppTimeForRaceStart = 0;
    private long mRtcTimeDeltaUs = 0;                         // difference in time between App clock, and puck clock, in microseconds

    // puck time 100
    // app time 10
    // mRtcTimeDeltaUs -90
    // getAppTimeforPuckTime 100 = 100 + -90 = 10

    //----------------------------------------------------------------------------------------------
    void recordAppTimeForRaceStart()
    {
        mAppTimeForRaceStart = java.lang.System.currentTimeMillis()*1000;     // note: long is 64-bit in Java
    }

    //----------------------------------------------------------------------------------------------
    // called at the start of the race to reset the time delta
    void resetRTCTimeDelta()
    {
        mRtcTimeDeltaUs = 0;                        // 0 is a dangerous value, but chances of both times being the same, about zero
    }

    //----------------------------------------------------------------------------------------------
    void setRTCTimeDelta(long puckTimeMicrosecs)
    {
        // for now, we only want the first one after reset, ignore all of the rest
        if(mRtcTimeDeltaUs != 0)
            return;

        long appTimeus = java.lang.System.currentTimeMillis()*1000;     // note: long is 64-bit in Java

        mRtcTimeDeltaUs = appTimeus - puckTimeMicrosecs;
    }

    //----------------------------------------------------------------------------------------------
    // return the App time corresponding to the given puck time, assuming that the setRTCTimeDelta function
    // has been called at the appropriate point.
    long getAppTimeForPuckTime(long puckTimeMicrosecs)
    {
        return puckTimeMicrosecs + mRtcTimeDeltaUs;
    }

    BluetoothBackgroundService mParentService;
    protocolDecoder(BluetoothBackgroundService parent)
    {
        mParentService = parent;

        crcCalc = new crc();

        rxSyncState = rxSyncStateMachine.LOOK_FOR_PACKET_STOP_SEQUENCE;

        allDetectionTable = new detectionTable(parent);
    }

    static enum protocolMessageId
    {
        PROTOCOL_MESSAGE_ID_IRC_RSSI             ( 0xDA01 ),
        PROTOCOL_MESSAGE_ID_IRC_RF_SETTINGS      ( 0xDA02 ),
        PROTOCOL_MESSAGE_ID_IRC_RF_ATTEN         ( 0xDA03 ),
        PROTOCOL_MESSAGE_ID_IRC_STATE_CTRL       ( 0xDA04 ),
        PROTOCOL_MESSAGE_ID_IRC_DATA             ( 0xDA05 ),
        PROTOCOL_MESSAGE_ID_IRC_CALIBRATION_LOG  ( 0xDA06 ),
        PROTOCOL_MESSAGE_ID_IRC_SETTINGS         ( 0xDA07 ),
        PROTOCOL_MESSAGE_ID_IRC_DESC             ( 0xDA08 ),
        PROTOCOL_MESSAGE_ID_DETECTION            ( 0xDA09 ),
        PROTOCOL_MESSAGE_ID_STATUS               ( 0xDA0A ),
        PROTOCOL_MESSAGE_ID_RESEND               ( 0xDA0B ),
        PROTOCOL_MESSAGE_ID_TIME                 ( 0xDA0C ),
        PROTOCOL_MESSAGE_ID_NETWORK              ( 0xDA0D );

        private final short val;
        protocolMessageId(int val){
        this.val = (short)val;
    }
    };
    
    static class fieldDefinition
    {
        fieldDefinition(int FIELD_ID, int length, protocolFieldDataTypes data_type)
        {
            this.FIELD_ID = (byte)FIELD_ID;
            this.length = (byte)length;
            this.data_type = data_type;
        }

        byte FIELD_ID;
        byte length;
        protocolFieldDataTypes data_type;
    }

    private static Map<String,fieldDefinition> fieldIdConstants;
    static {
        HashMap<String,fieldDefinition> aMap =  new HashMap<String, fieldDefinition>();

        // General purpose
        aMap.put("PILOT_ID",                 new fieldDefinition(0x01, 0x01, protocolFieldDataTypes.PROTOCOL_DATATYPE_BYTE) );
        aMap.put("RTC_TIME",                new fieldDefinition(0x02, 0x08, protocolFieldDataTypes.PROTOCOL_DATATYPE_LONG) );
        aMap.put("STATUS_FLAGS",            new fieldDefinition(0x03, 0x02, protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT) );

        // detections
        aMap.put("DECODER_ID",              new fieldDefinition(0x20, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_INT) );
        aMap.put("DETECTION_NUMBER",        new fieldDefinition(0x21, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_INT) );
        aMap.put("DETECTION_PEAK_HEIGHT",   new fieldDefinition(0x22, 0x02, protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT) );
        aMap.put("DETECTION_FLAGS",         new fieldDefinition(0x23, 0x02, protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT) );
        // status
        aMap.put("STATUS_NOISE",            new fieldDefinition(0x20, 0x02, protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT) );
        aMap.put("STATUS_INPUT_VOLTAGE",    new fieldDefinition(0x21, 0x02, protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT) );
        aMap.put("STATUS_RSSI",             new fieldDefinition(0x22, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_FLOAT) );
        // gate state
        aMap.put("STATUS_GATE_STATE",       new fieldDefinition(0x23, 0x01, protocolFieldDataTypes.PROTOCOL_DATATYPE_BYTE) );
        aMap.put("STATUS_COUNT",            new fieldDefinition(0x24, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_INT) );
        // RSSI
        aMap.put("RSSI_MIN",                new fieldDefinition(0x20, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_FLOAT) );
        aMap.put("RSSI_MAX",                new fieldDefinition(0x21, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_FLOAT) );
        aMap.put("RSSI_MEAN",               new fieldDefinition(0x22, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_FLOAT) );
        aMap.put("RSSI_COUNT",              new fieldDefinition(0x23, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_INT) );
        aMap.put("RSSI_ENABLE",             new fieldDefinition(0x24, 0x01, protocolFieldDataTypes.PROTOCOL_DATATYPE_BYTE) );
        aMap.put("RSSI_INTERVAL",           new fieldDefinition(0x25, 0x01, protocolFieldDataTypes.PROTOCOL_DATATYPE_BYTE) );
        aMap.put("RSSI_SDEV",               new fieldDefinition(0x26, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_FLOAT) );
        // resend
        aMap.put("DETECTION_COUNT_CURRENT",  new fieldDefinition(0x20, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_INT) );
        aMap.put("DETECTION_COUNT_FROM",     new fieldDefinition(0x21, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_INT) );
        aMap.put("DETECTION_COUNT_UNTIL",    new fieldDefinition(0x22, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_INT) );
        // rf settings
        aMap.put("RF_ENABLE",        new fieldDefinition(0x20, 0x02, protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT) );
        aMap.put("RF_CHANNEL",       new fieldDefinition(0x21, 0x02, protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT) );
        aMap.put("RF_BAND",          new fieldDefinition(0x22, 0x02, protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT) );
        aMap.put("RF_THRESHOLD",     new fieldDefinition(0x23, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_FLOAT) );
        aMap.put("RF_GAIN",          new fieldDefinition(0x24, 0x02, protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT) );
        aMap.put("RF_FREQUENCY",     new fieldDefinition(0x25, 0x02, protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT) );
        // time setting
        aMap.put("TIME_RTC_TIME",    new fieldDefinition(0x20, 0x08, protocolFieldDataTypes.PROTOCOL_DATATYPE_LONG) );
        // state control
        aMap.put("CTRL_REQ_RACE",    new fieldDefinition(0x20, 0x01, protocolFieldDataTypes.PROTOCOL_DATATYPE_BYTE) );
        aMap.put("CTRL_REQ_CAL",     new fieldDefinition(0x21, 0x01, protocolFieldDataTypes.PROTOCOL_DATATYPE_BYTE) );
        aMap.put("CTRL_REQ_DATA",    new fieldDefinition(0x22, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_INT) );
        aMap.put("CTRL_REQ_STATIC_CAL", new fieldDefinition(0x23, 0x01, protocolFieldDataTypes.PROTOCOL_DATATYPE_BYTE) );
        // data dump
        aMap.put("DATA_DUMP",           new fieldDefinition(0x20, 0x00, protocolFieldDataTypes.PROTOCOL_DATATYPE_STRUCT) );
        // calibration
        aMap.put("CALIBRATION_LOG_HEIGHT",      new fieldDefinition(0x20, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_FLOAT) );
        aMap.put("CALIBRATION_LOG_NUM_PEAK",    new fieldDefinition(0x21, 0x02, protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT) );
        aMap.put("CALIBRATION_LOG_BASE",        new fieldDefinition(0x22, 0x02, protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT) );
        // puck settings
        aMap.put("IRC_SETTINGS_NAME",           new fieldDefinition(0x20, 0x08, protocolFieldDataTypes.PROTOCOL_DATATYPE_STRUCT) );
        aMap.put("IRC_SETTINGS_BIND_ID",        new fieldDefinition(0x21, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_INT) );
        aMap.put("IRC_SETTINGS_UPDATE_PERIOD_MS", new fieldDefinition(0x22, 0x02, protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT) );
        aMap.put("IRC_SETTINGS_MIN_LAP_TIME",   new fieldDefinition(0x26, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_INT) );

        // system descriptor
        aMap.put("IRC_DESC_SYSTEM_VERSION",     new fieldDefinition(0x20, 0x01, protocolFieldDataTypes.PROTOCOL_DATATYPE_BYTE) );
        aMap.put("IRC_DESC_PROTOCOL_VERSION",   new fieldDefinition(0x21, 0x01, protocolFieldDataTypes.PROTOCOL_DATATYPE_BYTE) );

        // netowrk ping
        aMap.put("IRC_NETWORK_PING",            new fieldDefinition(0x20, 0x04, protocolFieldDataTypes.PROTOCOL_DATATYPE_INT) );
        fieldIdConstants = Collections.unmodifiableMap(aMap);
    }

    enum protocolStatus
    {
        PROTOCOL_STATUS_OK,
        PROTOCOL_STATUS_RECEIVED_PACKET,
        PROTOCOL_STATUS_RESPONSE,
        PROTOCOL_STATUS_RESPONSE_RF_SETTINGS,
        PROTOCOL_STATUS_RESPONSE_ATTEN_SETTINGS,
        PROTOCOL_STATUS_RESPONSE_CTRL_REQUEST,
        PROTOCOL_ERROR_BAD_CRC,
        PROTOCOL_ERROR_BAD_MESSAGE_ID,
        PROTOCOL_ERROR_BAD_FIELD_ID,
        PROTOCOL_ERROR_OVERFLOW
    };

    public final class Constants {

        // Defines a custom Intent action
        public static final String BROADCAST_PROTOCOL_DETECTION =
                "com.immersionrc.android.bluetooth.BROADCAST_PROTOCOL_DETECTION";

        // Defines a custom Intent action
        public static final String BROADCAST_PROTOCOL_STATUS =
                "com.immersionrc.android.bluetooth.BROADCAST_PROTOCOL_STATUS";

        // Defines a custom Intent action
        public static final String BROADCAST_PROTOCOL_RSSI_STATS =
                "com.immersionrc.android.bluetooth.BROADCAST_PROTOCOL_RSSI_STATS";

        // Defines a custom Intent action
        public static final String BROADCAST_VERSION =
                "com.immersionrc.android.bluetooth.BROADCAST_VERSION";

        // Defines a custom Intent action
        public static final String BROADCAST_PROTOCOL_RF_SETTINGS =
                "com.immersionrc.android.bluetooth.BROADCAST_PROTOCOL_RF_SETTINGS";

        // Defines a custom Intent action
        public static final String BROADCAST_PROTOCOL_SETTINGS =
                "com.immersionrc.android.bluetooth.BROADCAST_PROTOCOL_SETTINGS";

        // Defines the key for the status "extra" in an Intent
        public static final String EXTENDED_DATA_PROTOCOL_DETECTION =
                "com.immersionrc.android.bluetooth.DATA_PROTOCOL_DETECTION";

        public static final String EXTENDED_DATA_PROTOCOL_STATUS =
                "com.immersionrc.android.bluetooth.EXTENDED_DATA_PROTOCOL_STATUS";

        public static final String EXTENDED_DATA_PROTOCOL_RSSI_STATS =
                "com.immersionrc.android.bluetooth.EXTENDED_DATA_PROTOCOL_RSSI_STATS";

        public static final String EXTENDED_DATA_PROTOCOL_RF_SETTINGS =
                "com.immersionrc.android.bluetooth.EXTENDED_DATA_PROTOCOL_RF_SETTINGS";
        public static final String EXTENDED_DATA_PROTOCOL_SETTINGS =
                "com.immersionrc.android.bluetooth.EXTENDED_DATA_PROTOCOL_SETTINGS";

        public static final String EXTENDED_DATA_PROTOCOL_CTRL_REQUEST_RACE =
                "com.immersionrc.android.bluetooth.EXTENDED_DATA_PROTOCOL_CTRL_REQUEST_RACE";
        public static final String EXTENDED_DATA_PROTOCOL_CTRL_REQUEST_CALIBRATION =
                "com.immersionrc.android.bluetooth.EXTENDED_DATA_PROTOCOL_CTRL_REQUEST_CALIBRATION";


        // Defines a custom Intent action
        public static final String BROADCAST_PROTOCOL_CALIBRATION_LOG =
                "com.immersionrc.android.bluetooth.BROADCAST_PROTOCOL_CALIBRATION_LOG";

        public static final String EXTENDED_DATA_PROTOCOL_CALIBRATION_LOG =
                "com.immersionrc.android.bluetooth.EXTENDED_DATA_PROTOCOL_CALIBRATION_LOG";

        // Defines a custom Intent action
        public static final String BROADCAST_PROTOCOL_CALIBRATION_STATE =
                "com.immersionrc.android.bluetooth.BROADCAST_PROTOCOL_CALIBRATION_STATE";

        public static final String EXTENDED_DATA_PROTOCOL_CALIBRATION_STATE =
                "com.immersionrc.android.bluetooth.EXTENDED_DATA_PROTOCOL_CALIBRATION_STATE";

        public static final String EXTENDED_DATA_VERSION =
                "com.immersionrc.android.bluetooth.EXTENDED_DATA_VERSION";
    }

    public long time_us()
    {
        return java.lang.System.currentTimeMillis()*1000;
    }
    //*********************************************************************************
    // broadcast functions
    void broadcastRFSettings(communicationRfSettings rf_settings)
    {
        Intent localIntent =
                new Intent(Constants.BROADCAST_PROTOCOL_RF_SETTINGS)
                        // Puts the status into the Intent
                        .putExtra(Constants.EXTENDED_DATA_PROTOCOL_RF_SETTINGS, rf_settings );


        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(mParentService).sendBroadcast(localIntent);
    }

    void broadcastSettings(extraSettings extra_settings)
    {
        Intent localIntent =
                new Intent(Constants.BROADCAST_PROTOCOL_SETTINGS)
                        // Puts the status into the Intent
                        .putExtra(Constants.EXTENDED_DATA_PROTOCOL_SETTINGS, extra_settings );


        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(mParentService).sendBroadcast(localIntent);
    }

    void broadcastDetection(communicationDetection detection)
    {
        Intent localIntent =
                new Intent(Constants.BROADCAST_PROTOCOL_DETECTION)
                        // Puts the status into the Intent
                        .putExtra(Constants.EXTENDED_DATA_PROTOCOL_DETECTION, detection );

        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(mParentService).sendBroadcast(localIntent);
    }

    // Broadcast status witth disconnected state
    void broadcastFailedStatus()
    {
        communicationStatus status = new communicationStatus();
        status.connection = -1;

        broadcastStatus(status);
    }

    void broadcastStatus(communicationStatus status)
    {
        Intent localIntent =
            new Intent(Constants.BROADCAST_PROTOCOL_STATUS)
                // Puts the status into the Intent
                .putExtra(Constants.EXTENDED_DATA_PROTOCOL_STATUS, status );

        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(mParentService).sendBroadcast(localIntent);
    }

    void broadcastCalLog(communicationCalibrationLog cal_log)
    {
        Intent localIntent =
                new Intent(Constants.BROADCAST_PROTOCOL_CALIBRATION_LOG)
                        // Puts the status into the Intent
                        .putExtra(Constants.EXTENDED_DATA_PROTOCOL_CALIBRATION_LOG, cal_log );

        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(mParentService).sendBroadcast(localIntent);
    }

    void broadcastDesc(communicationDesc desc)
    {
        /*Intent localIntent =
                new Intent(Constants.BROADCAST_VERSION)
                        // Puts the status into the Intent
                        .putExtra(Constants.EXTENDED_DATA_VERSION, desc.systemVersion );

        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(mParentService).sendBroadcast(localIntent);*/

        Log.d("LapRFBluetoothBackground",      "System: "    + desc.systemVersion
                + " Protocol: " + desc.protocolVersion);
    }

    void broadcastStats(communicationRssiStatistics rssi_stats)
    {
        Intent localIntent =
                new Intent(Constants.BROADCAST_PROTOCOL_RSSI_STATS)
                        // Puts the status into the Intent
                        .putExtra(Constants.EXTENDED_DATA_PROTOCOL_RSSI_STATS, rssi_stats );

        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(mParentService).sendBroadcast(localIntent);
    }

    void broadcastCalState(communicationStaticCalibration state)
    {
        Intent localIntent =
                new Intent(Constants.BROADCAST_PROTOCOL_CALIBRATION_STATE)
                        // Puts the status into the Intent
                        .putExtra(Constants.EXTENDED_DATA_PROTOCOL_CALIBRATION_STATE, state );

        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(mParentService).sendBroadcast(localIntent);
    }

    void broadcastCount(int count)
    {

    }

    void broadcastBindID(int bindID)
    {

        Log.d("LapRFBluetoothBackgroundService",      "System BindID: "    + Integer.toString(bindID));

        mParentService.receivedBindID(bindID);
    }

    void broadcastPing(long ping_number)
    {

        Log.d("LapRFBluetoothBackgroundService",      "System ping: "    + Integer.toString((int)ping_number));

        mParentService.receivedPing((int)ping_number);
    }


    //*********************************************************************************
    // packet reception and decoding functions
    int receiveCount;

    // reception synchronisation state machine
    enum rxSyncStateMachine {
        LOOK_FOR_PACKET_STOP_SEQUENCE,
        ESCAPE_SEQUENCE,
        LOOK_FOR_PACKET_START_SEQUENCE
    };

    static final int RX_BUFFER_SIZE = 300;
    rxSyncStateMachine rxSyncState;
    private byte rxDataBuffer[] = new byte[RX_BUFFER_SIZE];

    public protocolStatus receiveByte(byte rx)
    {
        switch(rxSyncState) {
            case LOOK_FOR_PACKET_STOP_SEQUENCE:
            {
                if (rx == PACKET_START_SYMBOL)
                {
                    receiveCount = 0;
                    rxDataBuffer[receiveCount] = PACKET_START_SYMBOL;
                    receiveCount++;
                    rxSyncState = rxSyncStateMachine.LOOK_FOR_PACKET_START_SEQUENCE;
                }
                break;
            }
            case ESCAPE_SEQUENCE:
            {
                if (receiveCount < RX_BUFFER_SIZE)
                {
                    rxDataBuffer[receiveCount] = (byte)(rx - ESCAPE_SEQUENCE_OFFSET);
                    receiveCount++;
                    rxSyncState = rxSyncStateMachine.LOOK_FOR_PACKET_START_SEQUENCE;
                }
                else
                {
                    rxSyncState = rxSyncStateMachine.LOOK_FOR_PACKET_STOP_SEQUENCE;
                    return protocolStatus.PROTOCOL_ERROR_OVERFLOW;
                }

                break;
            }
            case LOOK_FOR_PACKET_START_SEQUENCE:
            {
                if (rx == PACKET_ESCAPE_SEQUENCE)
                {
                    rxSyncState = rxSyncStateMachine.ESCAPE_SEQUENCE;
                }
                else if (rx == PACKET_STOP_SYMBOL)
                {
                    rxDataBuffer[receiveCount] = rx;
                    receiveCount++;

                    rxSyncState = rxSyncStateMachine.LOOK_FOR_PACKET_STOP_SEQUENCE;

                    return protocolStatus.PROTOCOL_STATUS_RECEIVED_PACKET;
                }
                else if (receiveCount < RX_BUFFER_SIZE)
                {
                    rxDataBuffer[receiveCount] = rx;
                    receiveCount++;
                    rxSyncState = rxSyncStateMachine.LOOK_FOR_PACKET_START_SEQUENCE;
                }

                break;
            }
        }

        return protocolStatus.PROTOCOL_STATUS_OK;
    }

    // Copy internal buffer to output buffer while escaping characters
    byte[] escapeCharacters(byte[] data_in)
    {

        ByteBuffer buf = ByteBuffer.allocate(300);


        int length = 0;
        buf.put(PACKET_START_SYMBOL);
        length++;

        for ( int i = 1; i < data_in.length - 1; i++)
        {
            if ( data_in[i] == PACKET_ESCAPE_SEQUENCE ||  // PACKET_ESCAPE_SEQUENCE
                    data_in[i] == PACKET_START_SYMBOL ||  // PACKET_START_SYMBOL
                    data_in[i] == PACKET_STOP_SYMBOL // PACKET_STOP_SYMBOL
                    )
            {
                buf.put(PACKET_ESCAPE_SEQUENCE);
                length++;
                buf.put((byte)(data_in[i] + ESCAPE_SEQUENCE_OFFSET));
                length++;
            }
            else
            {
                buf.put(data_in[i]);

                length++;
            }
        }

        buf.put(PACKET_STOP_SYMBOL);
        length++;
        byte[] msg = Arrays.copyOfRange(buf.array(), 0, length);
        return msg;
    }

    //----------------------------------------------------------------------------------------------
    ByteBuffer preparePacketWithHeader(protocolMessageId message_id)
    {
        ByteBuffer txBuf = ByteBuffer.allocate(500);
        txBuf.order(ByteOrder.LITTLE_ENDIAN);
        // PACKET_START_SYMBOL
        txBuf.put((byte) PACKET_START_SYMBOL);

        // length
        txBuf.putShort((short)0x0000);

        // CRC
        txBuf.putShort((short)0x0000);

        // message id
        txBuf.putShort((short)message_id.val);

        return txBuf;
    }

    //----------------------------------------------------------------------------------------------
    byte[] closeAndGetPacket(ByteBuffer txBuf)
    {
        //append PACKET_STOP_SYMBOL end byte
        txBuf.put((byte) PACKET_STOP_SYMBOL);

        // write length
        txBuf.putShort(1, (short)txBuf.position());

        // compute CRC
        byte[] msg = Arrays.copyOfRange(txBuf.array(), 0, (short)txBuf.position());
        int crc = crcCalc.computeCrc(msg, (short)txBuf.position());

        txBuf.putShort(3, (short)crc);
        msg = Arrays.copyOfRange(txBuf.array(), 0, (short)txBuf.position());
        // escape chars

        byte[] msg2 = escapeCharacters(msg);

        return msg2;
    }

    //----------------------------------------------------------------------------------------------
    void appendMessageIdEmpty(ByteBuffer txBuf, String message_id)
    {
        int FIELD_ID = fieldIdConstants.get(message_id).FIELD_ID;
        int size = 0;

        // FIELD_ID
        txBuf.put((byte)FIELD_ID);

        // size
        txBuf.put((byte)size);
    }

    //----------------------------------------------------------------------------------------------
    void appendMessageIdInt(ByteBuffer txBuf, String message_id, int data)
    {
        int FIELD_ID = fieldIdConstants.get(message_id).FIELD_ID;
        int size = fieldIdConstants.get(message_id).length;
        protocolFieldDataTypes type = fieldIdConstants.get(message_id).data_type;

        // FIELD_ID
        txBuf.put((byte)FIELD_ID);

        // size
        txBuf.put((byte)size);

        if (type == protocolFieldDataTypes.PROTOCOL_DATATYPE_INT )
        {
            txBuf.putInt(data);
        }
        else if (type == protocolFieldDataTypes.PROTOCOL_DATATYPE_SHORT )
        {
            txBuf.putShort((short)data);
        }
        else if (type == protocolFieldDataTypes.PROTOCOL_DATATYPE_BYTE )
        {
            txBuf.put((byte)data);
        }
    }

    //----------------------------------------------------------------------------------------------
    void appendMessageIdString(ByteBuffer txBuf, String field_id, String data)
    {
        int FIELD_ID = fieldIdConstants.get(field_id).FIELD_ID;
        int size= fieldIdConstants.get(field_id).length;
        protocolFieldDataTypes type = fieldIdConstants.get(field_id).data_type;

        // FIELD_ID
        txBuf.put((byte)FIELD_ID);

        // size
        txBuf.put((byte)size);

        byte[] str = data.getBytes();
        for (int i = 0; i < str.length; i++)
        {
            txBuf.put( str[i]  );
        }

        // pad with 0
        for (int i = str.length; i < size; i++)
        {
            txBuf.put( (byte)0 );
        }
    }

    //----------------------------------------------------------------------------------------------
    void appendMessageIdFloat(ByteBuffer txBuf, String field_id, float data) {
        int FIELD_ID = fieldIdConstants.get(field_id).FIELD_ID;
        int size = fieldIdConstants.get(field_id).length;
        protocolFieldDataTypes type = fieldIdConstants.get(field_id).data_type;

        // FIELD_ID
        txBuf.put((byte) FIELD_ID);

        // size
        txBuf.put((byte) size);

        if (type == protocolFieldDataTypes.PROTOCOL_DATATYPE_FLOAT)
        {
            txBuf.putFloat(data);
        }
    }

    communicationRfSettings lastRFSettingsReceived;
    boolean bReceivedRFSettings = false;

    //*********************************************************************************
    protocolStatus decodeReceivedPacket(  ) {
        long current_timestamp = time_us();
        ByteBuffer buf = ByteBuffer.wrap(rxDataBuffer, 0, receiveCount);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        // grab header
        byte startOfMessage = buf.get();
        short length = buf.getShort();
        int crc = buf.getShort() & 0xFFFF;
        short messageId = buf.getShort();

        // check CRC
        int receivedCrc = crc;
        // nullify crc for crc check:
        rxDataBuffer[3] = 0;
        rxDataBuffer[4] = 0;

        int computedCrc = crcCalc.computeCrc(rxDataBuffer, receiveCount);

        if (computedCrc != receivedCrc)
            return protocolStatus.PROTOCOL_ERROR_BAD_CRC;

        // TODO: check version for future evolutions

        protocolStatus status = protocolStatus.PROTOCOL_STATUS_OK;

        int tempSlotId = 0;

            if (messageId == protocolMessageId.PROTOCOL_MESSAGE_ID_DETECTION.val )
            {
                communicationDetection detection = new communicationDetection();

                byte FIELD_ID = buf.get();
                while (FIELD_ID != PACKET_STOP_SYMBOL)
                {
                    int size = 0;

                    if (FIELD_ID == fieldIdConstants.get("PILOT_ID").FIELD_ID) {
                        size = buf.get();
                        detection.pilotId = buf.get();
                    } else if (FIELD_ID == fieldIdConstants.get("DECODER_ID").FIELD_ID) {
                        size = buf.get();
                        detection.decoderId = buf.getInt();
                    } else if (FIELD_ID == fieldIdConstants.get("RTC_TIME").FIELD_ID) {
                        size = buf.get();
                        detection.puckTime = buf.getLong();
                    } else if (FIELD_ID == fieldIdConstants.get("DETECTION_NUMBER").FIELD_ID) {
                        size = buf.get();
                        detection.detectionNumber = buf.getInt();
                    } else if (FIELD_ID == fieldIdConstants.get("DETECTION_PEAK_HEIGHT").FIELD_ID) {
                        size = buf.get();
                        detection.detectionPeakHeight = buf.getShort();
                    } else if (FIELD_ID == fieldIdConstants.get("DETECTION_FLAGS").FIELD_ID) {
                        size = buf.get();
                        detection.detectionFlags = buf.getShort();
                    }
                    FIELD_ID = buf.get();
                }

                setRTCTimeDelta(detection.puckTime);             // grab the rtc time, use it to correct clocks

                allDetectionTable.addItem(detection);

                broadcastDetection(detection);
            }
            else if (messageId == protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_SETTINGS.val )
            {
                int bindID = 0;
                extraSettings st = new extraSettings();

                byte FIELD_ID = buf.get();
                while (FIELD_ID != PACKET_STOP_SYMBOL)
                {
                    int size = 0;

                    if (FIELD_ID == fieldIdConstants.get("IRC_SETTINGS_BIND_ID").FIELD_ID)
                    {
                        size = buf.get();
                        bindID = buf.getInt();

                        broadcastBindID(bindID);
                    }

                    if (FIELD_ID == fieldIdConstants.get("IRC_SETTINGS_MIN_LAP_TIME").FIELD_ID)
                    {
                        size = buf.get();
                        st.minLapTime = buf.getInt();
                        Log.d("protocolDecoder", "receive RF settings");

                        broadcastSettings(st);
                    }

                    FIELD_ID = buf.get();
                }

            }
            else if (messageId == protocolMessageId.PROTOCOL_MESSAGE_ID_NETWORK.val )
            {
                byte FIELD_ID = buf.get();
                boolean got_ping_id = false;
                long ping_id = 0x00;
                while (FIELD_ID != PACKET_STOP_SYMBOL) {
                    int size = 0;

                    if (FIELD_ID == fieldIdConstants.get("IRC_NETWORK_PING").FIELD_ID) {
                        size = buf.get();
                        ping_id = buf.getInt();
                        got_ping_id = true;
                    }

                    FIELD_ID = buf.get();
                }
                if (got_ping_id)
                    broadcastPing(ping_id);
            }
            else if (messageId == protocolMessageId.PROTOCOL_MESSAGE_ID_STATUS.val )
            {
                int currentCount = 0;

                communicationStatus st = new communicationStatus();

                byte FIELD_ID = buf.get();
                while (FIELD_ID != PACKET_STOP_SYMBOL) {
                    int size = 0;

                    if (FIELD_ID == fieldIdConstants.get("STATUS_NOISE").FIELD_ID) {
                        size = buf.get();
                        st.noise = buf.getShort();
                    } else if (FIELD_ID == fieldIdConstants.get("STATUS_INPUT_VOLTAGE").FIELD_ID) {
                        size = buf.get();
                        st.voltage = buf.getShort();
                   } else if (FIELD_ID == fieldIdConstants.get("STATUS_COUNT").FIELD_ID) {
                        size = buf.get();
                        currentCount = buf.getInt();
                    } else if (FIELD_ID == fieldIdConstants.get("PILOT_ID").FIELD_ID) {
                        size = buf.get();
                        tempSlotId = buf.get();
                    } else if (FIELD_ID == fieldIdConstants.get("RSSI_MEAN").FIELD_ID) {
                        size = buf.get();
                        st.base[tempSlotId -1] = buf.getFloat();
                    } else if (FIELD_ID == fieldIdConstants.get("STATUS_GATE_STATE").FIELD_ID) {
                        size = buf.get();
                        st.state = buf.get();
                    }
                    else if (FIELD_ID == fieldIdConstants.get("STATUS_FLAGS").FIELD_ID) {
                        size = buf.get();
                        st.hasStatusFlags = true;
                        st.statusFlags = buf.getShort();
                    }

                    FIELD_ID = buf.get();
                }

                mStatusPacketCounter++;

                st.connection = (byte)(mStatusPacketCounter %2);
                if (mParentService != null)
                    mParentService.restartStatusTimer();

                // so when we receive a status message, we immediately request a lap count
                // WHY? this seems nuts! the lap count is already being queried at a 10s rate, and that is fine
                // to deal with the occasional disconnect.
               // Log.d("protocolDecoder", "receiveLapCount after status received");
                //allDetectionTable.receiveLapCount(currentCount);

                st.firmwareVersion = versionDesc.systemVersion;
                broadcastStatus(st);
            }
            else if (messageId == protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_CALIBRATION_LOG.val )
            {
                communicationCalibrationLog cal = new communicationCalibrationLog();

                byte FIELD_ID = buf.get();
                while (FIELD_ID != PACKET_STOP_SYMBOL) {
                    int size = 0;

                    if (FIELD_ID == fieldIdConstants.get("PILOT_ID").FIELD_ID) {
                        size = buf.get();
                        cal.pilot = buf.get();
                    } else if (FIELD_ID == fieldIdConstants.get("RTC_TIME").FIELD_ID) {
                        size = buf.get();
                        cal.time = ((float)buf.getLong())/1000000.0f;
                    } else if (FIELD_ID == fieldIdConstants.get("CALIBRATION_LOG_HEIGHT").FIELD_ID) {
                        size = buf.get();
                        cal.height = buf.getFloat();
                    } else if (FIELD_ID == fieldIdConstants.get("CALIBRATION_LOG_NUM_PEAK").FIELD_ID) {
                        size = buf.get();
                        cal.numPeak = buf.getShort();
                    } else if (FIELD_ID == fieldIdConstants.get("CALIBRATION_LOG_BASE").FIELD_ID) {
                        size = buf.get();
                        cal.baseLevel = buf.getShort();
                    }



                    FIELD_ID = buf.get();
                }

                broadcastCalLog(cal);
            }
            else if (messageId == protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_RF_SETTINGS.val )
            {
                communicationRfSettings st = new communicationRfSettings();

                byte FIELD_ID = buf.get();
                while (FIELD_ID != PACKET_STOP_SYMBOL) {
                    int size = 0;

                    if (FIELD_ID == fieldIdConstants.get("PILOT_ID").FIELD_ID) {
                        size = buf.get();
                        st.slotid = buf.get();
                    } else if (FIELD_ID == fieldIdConstants.get("RF_THRESHOLD").FIELD_ID) {
                        size = buf.get();
                        st.threshold = buf.getFloat();
                    } else if (FIELD_ID == fieldIdConstants.get("RF_GAIN").FIELD_ID) {
                        size = buf.get();
                        st.gain = buf.getShort();
                    } else if (FIELD_ID == fieldIdConstants.get("RF_ENABLE").FIELD_ID) {
                        size = buf.get();
                        st.enable = buf.getShort();
                    } else if (FIELD_ID == fieldIdConstants.get("RF_CHANNEL").FIELD_ID) {
                        size = buf.get();
                        st.channel = buf.getShort();
                    } else if (FIELD_ID == fieldIdConstants.get("RF_BAND").FIELD_ID) {
                        size = buf.get();
                        st.band = buf.getShort();
                    } else if (FIELD_ID == fieldIdConstants.get("RF_FREQUENCY").FIELD_ID) {
                        size = buf.get();
                        st.frequency = buf.getShort();
                    }

                    FIELD_ID = buf.get();
                }

                Log.d("protocolDecoder", "receive RF settings");

                // ugly, but save the last settings received, and set a flag confirming their reception
                // used by sendToDeviceWithVerification to confirm that settings were sent correctly
                lastRFSettingsReceived = st;
                bReceivedRFSettings = true;

                // broadcast settings to interested parties
                broadcastRFSettings(st);
            }
            else if (messageId == protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_SETTINGS.val )
            {
                extraSettings st = new extraSettings();

                byte FIELD_ID = buf.get();
                while (FIELD_ID != PACKET_STOP_SYMBOL) {
                    int size = 0;

                    if (FIELD_ID == fieldIdConstants.get("IRC_SETTINGS_MIN_LAP_TIME").FIELD_ID) {
                        size = buf.get();
                        st.minLapTime = buf.getFloat();
                    }

                    FIELD_ID = buf.get();
                }

                Log.d("protocolDecoder", "receive RF settings");
                broadcastSettings(st);
            }
            else if (messageId == protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_STATE_CTRL.val )
            {
                communicationStaticCalibration cal = new communicationStaticCalibration();

                byte FIELD_ID = buf.get();
                while (FIELD_ID != PACKET_STOP_SYMBOL) {
                    int size = 0;

                    if (FIELD_ID == fieldIdConstants.get("CTRL_REQ_STATIC_CAL").FIELD_ID) {
                        size = buf.get();
                        cal.state = buf.get();
                    }

                    FIELD_ID = buf.get();
                }

                broadcastCalState(cal);
            }
            else if (messageId == protocolMessageId.PROTOCOL_MESSAGE_ID_TIME.val )
            {
                long rtc_time = 0;
                long time_rtc_time = 0;

                int size = 0;

                byte FIELD_ID = buf.get();
                while (FIELD_ID != PACKET_STOP_SYMBOL)
                {
                    if (FIELD_ID == fieldIdConstants.get("RTC_TIME").FIELD_ID)
                    {
                        size = buf.get();
                        rtc_time = buf.getLong();
                    }
                    else if (FIELD_ID == fieldIdConstants.get("TIME_RTC_TIME").FIELD_ID)
                    {
                        size = buf.get();
                        time_rtc_time = buf.getLong();
                    }

                    FIELD_ID = buf.get();
                }

                mParentService.receivedTimestamp(time_rtc_time, rtc_time, current_timestamp);
            }
            else if (messageId == protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_DESC.val )
            {
                byte FIELD_ID = buf.get();
                while (FIELD_ID != PACKET_STOP_SYMBOL)
                {
                    int size = 0;

                    if (FIELD_ID == fieldIdConstants.get("IRC_DESC_SYSTEM_VERSION").FIELD_ID)
                    {
                        size = buf.get();
                        int sysVer = buf.getInt();
                        versionDesc.systemVersion = String.format("%X.%X.%X.%X", (sysVer >> 24) & 0xff, (sysVer >> 16) & 0xff, (sysVer >> 8) & 0xff, sysVer & 0xff);
                    }
                    else if (FIELD_ID == fieldIdConstants.get("IRC_DESC_PROTOCOL_VERSION").FIELD_ID)
                    {
                        size = buf.get();
                        versionDesc.protocolVersion = buf.get();
                    }

                    FIELD_ID = buf.get();
                }

                broadcastDesc(versionDesc);
            }
            else if ( messageId == protocolMessageId.PROTOCOL_MESSAGE_ID_IRC_RSSI.val )
            {
                communicationRssiStatistics stats = new communicationRssiStatistics();

                byte FIELD_ID = buf.get();

                while (FIELD_ID != PACKET_STOP_SYMBOL) {
                    int size = 0;

                    if (FIELD_ID == fieldIdConstants.get("PILOT_ID").FIELD_ID) {
                        size = buf.get();
                        stats.pilotId = buf.get();
                    } else if (FIELD_ID == fieldIdConstants.get("RSSI_MIN").FIELD_ID) {
                        size = buf.get();
                        stats.min = buf.getFloat();
                    } else if (FIELD_ID == fieldIdConstants.get("RSSI_MAX").FIELD_ID) {
                        size = buf.get();
                        stats.max = buf.getFloat();
                    } else if (FIELD_ID == fieldIdConstants.get("RSSI_MEAN").FIELD_ID) {
                        size = buf.get();
                        stats.mean = buf.getFloat();
                    } else if (FIELD_ID == fieldIdConstants.get("RSSI_SDEV").FIELD_ID) {
                        size = buf.get();
                        stats.sdev = buf.getFloat();
                    } else if (FIELD_ID == fieldIdConstants.get("RSSI_COUNT").FIELD_ID) {
                        size = buf.get();
                        stats.count = buf.getInt();
                    }

                    FIELD_ID = buf.get();
                }

                broadcastStats(stats);
            }
            else if (messageId == protocolMessageId.PROTOCOL_MESSAGE_ID_RESEND.val )
            {
                int currentCount = 0;

                byte FIELD_ID = buf.get();
                while (FIELD_ID != PACKET_STOP_SYMBOL) {
                    int size = 0;

                    if (FIELD_ID == fieldIdConstants.get("DETECTION_COUNT_CURRENT").FIELD_ID) {
                        size = buf.get();
                        currentCount = buf.getInt();
                    }

                    FIELD_ID = buf.get();
                }

                allDetectionTable.receiveLapCount(currentCount);

                broadcastCount(currentCount);
            }

            // zero out the data buffer
            for (int i = 0; i < rxDataBuffer.length; i++)
            {
                rxDataBuffer[i] = 0;
            }

        return status;
    }

    //*********************************************************************************
    // data storing received info
    // serializable so they can be communicated to activites

    public class communicationCalibrationLog implements Serializable
    {
        byte pilot;
        float time;
        float height;
        short numPeak;
        short baseLevel;

        public communicationCalibrationLog()
        {
            pilot = 0;
            height = 0;
            time = 0;
            numPeak = 0;
            baseLevel = 0;
        }
    }

    public class communicationStatus implements Serializable
    {
        byte connection; // status of connections       You are entering a 'useless comment zone'... what does each state mean?
        short voltage;
        short noise;
        byte state;
        float base[] = new float[8];
        short statusFlags;
        boolean hasStatusFlags;
        String firmwareVersion;

        public communicationStatus()
        {
            connection = 0;
            voltage = 0;
            noise = 0;
            state = -1;
            base[0] = 0.0f;
            base[1] = 0.0f;
            base[2] = 0.0f;
            base[3] = 0.0f;
            base[4] = 0.0f;
            base[5] = 0.0f;
            base[6] = 0.0f;
            base[7] = 0.0f;
            statusFlags = 0x0000;
            hasStatusFlags = false;
            firmwareVersion = "";
        }
    }

    public class communicationStaticCalibration implements Serializable
    {
        byte state = 0;
        byte slotId = 0;
    }

    public class communicationRssiStatistics implements Serializable
    {
        byte pilotId = 0;
        float min = 0.0f;
        float max  = 0.0f;
        float mean = 0.0f;
        float sdev = 0.0f;
        int count = 0;
    }

    public class communicationDesc implements Serializable
    {
        String systemVersion = "";
        byte protocolVersion = 0;
    }

    public static class communicationDetection implements Serializable
    {
        public int decoderId;
        public int detectionNumber;
        public int pilotId;                     // pilot ID, 1-based (1-8)
        public long puckTime;                   // puck time at crossing
        public long appTime;                    // app clock's time at crossing
        public short detectionPeakHeight;
        public short detectionHits;
        public short detectionFlags;

        public communicationDetection()
        {
            decoderId = 0;
            detectionNumber = 0;
            pilotId = 0;
            puckTime = 0;
            appTime = 0;
            detectionPeakHeight = 0;
            detectionHits = 0;
            detectionFlags = 0;
        }
    }

}
