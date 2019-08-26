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
