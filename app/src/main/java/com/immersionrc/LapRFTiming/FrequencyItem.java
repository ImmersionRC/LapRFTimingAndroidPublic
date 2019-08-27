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
 * Created by urssc on 11-Apr-17.
 */
public class FrequencyItem {
    public int frequency;
    public boolean selected;

    public FrequencyItem() {
        frequency = 5000;
        selected = false;
    }


    public FrequencyItem(int freq, boolean sel) {
        frequency = freq;
        selected = sel;
    }
}
