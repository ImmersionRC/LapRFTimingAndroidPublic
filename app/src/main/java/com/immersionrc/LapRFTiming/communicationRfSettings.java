// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
package com.immersionrc.LapRFTiming;

import java.io.Serializable;

/**
 * Created by urssc on 07-Apr-17.
 */

public class communicationRfSettings implements Serializable {
    public byte slotid;
    public short enable;
    public short channel;
    public short band;
    public short gain;
    public float threshold;
    public short frequency;
}
