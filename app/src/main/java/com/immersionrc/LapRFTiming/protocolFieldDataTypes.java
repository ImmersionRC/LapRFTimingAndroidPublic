// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
package com.immersionrc.LapRFTiming;

/**
 * Created by urssc on 03-Apr-17.
 */

public enum protocolFieldDataTypes
{
    PROTOCOL_DATATYPE_BYTE,    // 1 byte
    PROTOCOL_DATATYPE_SHORT,   // 2 bytes
    PROTOCOL_DATATYPE_INT,     // 4 bytes
    PROTOCOL_DATATYPE_LONG,     // 8 bytes
    PROTOCOL_DATATYPE_FLOAT,    // 4 bytes
    PROTOCOL_DATATYPE_STRUCT   // depending on content
};