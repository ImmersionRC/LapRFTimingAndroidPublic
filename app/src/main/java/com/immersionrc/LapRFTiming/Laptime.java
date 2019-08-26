package com.immersionrc.LapRFTiming;

/**
 * Created by urssc on 15-Mar-17.
 *
 * structure to keep laptime data
 */

public class Laptime {
    public long pilot;
    public float laptime;
    public int lapnumber;
    public long timestamp;
    public short peak;
    public int flags;

    public Laptime(long pilot, long laptime)
    {
        this.pilot = pilot;
        this.laptime = laptime;
    }

    // copy constructor
    public Laptime(Laptime lt)
    {
        this.pilot = lt.pilot;
        this.laptime = lt.laptime;
        this.lapnumber = lt.lapnumber;
        this.timestamp = lt.timestamp;
        this.peak = lt.peak;
        this.flags = lt.flags;
    }
}