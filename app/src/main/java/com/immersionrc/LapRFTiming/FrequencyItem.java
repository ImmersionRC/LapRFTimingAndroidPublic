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
