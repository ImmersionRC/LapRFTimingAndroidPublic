// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
package com.immersionrc.LapRFTiming;

import java.util.Collections;
import java.util.Comparator;
import java.util.Random;
import java.util.Vector;

//-----------------------------------------------------------------------------------------------------
// Created by urssc on 03-Apr-17.
//
// stores all the detections we received and checks for missing counts
// serves data to adapter used for display in race activity
//-----------------------------------------------------------------------------------------------------

public class detectionTable
{
    int numSlots = lapRFConstants.numPilotSlots;
    boolean bRaceActive = false;
    boolean bIsFirstCrossing = false;
    boolean bCreateOnTheToneRecordsPending = false;
    int maxLapsThisRace = 3;            // maximum number of laps for this race, set by service

    // needs to match the array race_type_array
    public enum RaceType {
        Practice, LapCount, FixedTime
    }

    // needs to match the array     public enum race_start_delay_array {

    public enum StartDelay {
        None, Fixed5Secs, Fixed10Secs, Random5Secs, Staggered
    }

    public class detectionTablePerPilot
    {
        Vector<Laptime> mLaptimesThisPilot = new Vector<Laptime>();
        public int mPilotID = 0;
        private float mTotalTime = 0.0f;

        private float mBestLapTime = 0.0f;
        private int mBestLapIdx = 0;

        public detectionTablePerPilot(int PilotID)
        {
            mPilotID = PilotID;
        }

        // TODO: make this smarter than rereading
        public void fillFromTable(Vector<Laptime> laptimes)
        {
            mLaptimesThisPilot.clear();

            long lastTimeStamp = 0;
            long lapnumber = 0;
            mTotalTime = 0.0f;
            mBestLapTime = 1e9f;
            mBestLapIdx = 0;
            for (Laptime lt : laptimes)
            {
                Laptime lt1 = new Laptime(lt);

                if (lt.pilot == mPilotID)
                {
                    // this is for this pilot
                    if ( lapnumber == 0 )
                    {
                        lt.laptime = 0;
                    }
                    else
                    {
                        lt.laptime = ((float)(lt.timestamp - lastTimeStamp))/1000000.0f;
                        if (lt.laptime < mBestLapTime)
                        {
                            mBestLapTime = lt.laptime;
                            mBestLapIdx = (int)lapnumber;
                        }
                    }

                    lastTimeStamp = lt.timestamp;
//                    lt.lapnumber = lapnumber;

                    mLaptimesThisPilot.add(lt);
                    lapnumber++;
                }
            }

            if (lapnumber > 0)
                mTotalTime = (mLaptimesThisPilot.lastElement().timestamp - mLaptimesThisPilot.firstElement().timestamp)/1000000.0f;
            else
                mTotalTime = 0.0f;
        }

        public float getTotalTime()
        {
            return  mTotalTime;
        }

        public float getBestLapTime()
        {
            return mBestLapTime;
        }

        public int getBestLapIdx()
        {
            return mBestLapIdx;
        }

        public int getCount() {
            return mLaptimesThisPilot.size();
        }

        public Laptime getItem(int position) {
            return mLaptimesThisPilot.get(position);
        }

        public long getItemId(int position) {
            return mLaptimesThisPilot.get(position).lapnumber;
        }

    }

    //----------------------------------------------------------------------------------------------
    // This guy is important, it contains all lap times, for all pilots
    Vector<Laptime> mLaptimes = new Vector<Laptime>();
    //----------------------------------------------------------------------------------------------

    Vector<Float> mTotalTimes = new Vector<Float>();
    Vector<Integer> mPilotOrdering = new Vector<Integer>();
    Vector<Integer> mLapCount = new Vector<Integer>();
    Vector<Float> mAvgTimes = new Vector<Float>();
    Vector<Integer> mBestLapIdx = new Vector<Integer>();
    Vector<Float> mBestLapTimes = new Vector<Float>();

    BluetoothBackgroundService mParentService;

    public Vector<detectionTablePerPilot> mPilotsTable = new Vector<detectionTablePerPilot>();

    boolean waitForStartCount = false;

    int startLapCount = 1;
    private long raceStartTimems = 0;
    private long raceDurationSecs = 0;            // 0 = infinite

    //----------------------------------------------------------------------------------------------
    void setRaceActive(boolean bActive)
    {
        if(bActive)
        {
            bIsFirstCrossing = true;
            raceStartTimems = System.currentTimeMillis();
        }
        bRaceActive = bActive;
        bCreateOnTheToneRecordsPending = false;
    }

    //----------------------------------------------------------------------------------------------
    void resetRaceTimeOnFirstCrossing()
    {
        raceStartTimems = System.currentTimeMillis();
    }

    //----------------------------------------------------------------------------------------------
    boolean getIsFirstCrossing()
    {
        return bIsFirstCrossing;
    }

    //----------------------------------------------------------------------------------------------
    boolean getRaceActive()
    {
        return bRaceActive;
    }

    //----------------------------------------------------------------------------------------------
    void setRaceNumLaps(int numLaps)
    {
        maxLapsThisRace = numLaps;
    }

    //----------------------------------------------------------------------------------------------
    int getRaceNumLaps()
    {
        return maxLapsThisRace;
    }

    long lastRaceTimeSecs = 0;

    //----------------------------------------------------------------------------------------------
    // return the time since the race started, in seconds
    long getRaceTimeSecs()
    {
        if(bRaceActive)
        {
            lastRaceTimeSecs = (System.currentTimeMillis() - raceStartTimems) / 1000;
        }
        return lastRaceTimeSecs;
    }

    //----------------------------------------------------------------------------------------------
    // set the race duration, set a value of zero to have an infinite race (practice mode)
    void setRaceDurationSecs(long raceDuration)
    {
        raceDurationSecs = raceDuration;
    }

    //----------------------------------------------------------------------------------------------
    // return the time since the race started, in seconds
    // returns -1 if infinite
    long getRaceTimeRemainingSecs()
    {
        if(raceDurationSecs == 0)
            return -1;
        else
            return raceDurationSecs - getRaceTimeSecs();
    }

    //----------------------------------------------------------------------------------------------
    void initStartCount()
    {
        waitForStartCount = true;
    }

    //----------------------------------------------------------------------------------------------
    public void receiveLapCount(int lapCount)
    {
        if (waitForStartCount)
        {
            // store the index for the first lap
            startLapCount = lapCount + 1;
            waitForStartCount = false;
        }
        else
        {
            // we are in race, got lap count in status, check that we have all the laps
            // check for missing data at start
            // TODO: This is where we can compensate for the dummy records added at the start tone in 'from the tone' mode.
            //
            if ( mLaptimes.size() > 0 )
            {
                Laptime lt = mLaptimes.lastElement();
                if ( lt.lapnumber < lapCount )
                {
                    mParentService.resendPackets( lt.lapnumber, lapCount );
                }
            }
            else
            {
                mParentService.resendPackets( startLapCount, lapCount );
            }
        }
    }

    //----------------------------------------------------------------------------------------------
    public detectionTable( BluetoothBackgroundService parentService )
    {
        mParentService = parentService;
        for (int i = 0; i < numSlots; i++)
            mPilotsTable.add(new detectionTablePerPilot(i+1));

        updatePilotTables();
    }

    //----------------------------------------------------------------------------------------------
    public void updatePilotTables()
    {
        mPilotOrdering.clear();
        mTotalTimes.clear();
        mLapCount.clear();
        mAvgTimes.clear();
        mBestLapIdx.clear();
        mBestLapTimes.clear();

        // loop over each pilot slot
        for (int i = 0; i < numSlots; i++)
        {
            mPilotsTable.get(i).fillFromTable(mLaptimes);
            mPilotOrdering.add(i);              // add the pilot slot index to the ordering list, even the ones with no laps

            // any passing records for this pilot?
            if (mPilotsTable.get(i).getCount() > 0)
            {
                float totalTime = mPilotsTable.get(i).getTotalTime();
                int lapCount = mPilotsTable.get(i).getCount();
                float bestTime = mPilotsTable.get(i).getBestLapTime();
                int bestIdx = mPilotsTable.get(i).getBestLapIdx();

                mTotalTimes.add(totalTime);
                if (lapCount > 0)
                {
                    lapCount = lapCount - 1;
                }
                else
                    lapCount = 0;

                mLapCount.add(lapCount);
                mBestLapTimes.add(bestTime);
                mBestLapIdx.add(bestIdx);
                if (lapCount > 0)
                    mAvgTimes.add(totalTime / lapCount);
                else
                    mAvgTimes.add(-1.0f);
            }
            else
            {
                mTotalTimes.add(-1.0f);
                mLapCount.add(0);
                mBestLapTimes.add(-1.0f);
                mBestLapIdx.add(-1);
                mAvgTimes.add(-1.0f);
            }
        }

    }

    //----------------------------------------------------------------------------------------------
    class WinningPilotComparator implements Comparator<Integer>
    {
        public int compare(Integer pilot1Idx, Integer pilot2Idx)
        {
            int lapCount1 = mLapCount.get((int) pilot1Idx);
            int lapCount2 = mLapCount.get((int) pilot2Idx);
            if (lapCount1 == lapCount2)         // lap counts the same, use lap times to compare pilots
            {
                float t1 = mTotalTimes.get((int) pilot1Idx);
                float t2 = mTotalTimes.get((int) pilot2Idx);
                if (t1 > 0.01 && t2 > 0.01)
                {
                    if (t1 < t2)
                        return -1;
                    else
                        return 1;
                }
                else if (t1 > 0.01)
                    return -1;
                else
                    return 1;
            } else if (lapCount1 < lapCount2)
                return 1;
            else
                return -1;
        }
    }

    //----------------------------------------------------------------------------------------------
    public Vector<Integer> getPilotOrdering()
    {
        Comparator comparator = new WinningPilotComparator();
        Collections.sort(mPilotOrdering, comparator);

        // turn a list of pilot indices for each position, into a list of positions
        // for each pilot index
        Vector<Integer> positionPerPilotSlot = new Vector(numSlots);
        Integer zeroInt = 0 ;
        for(int i = 0; i < numSlots; ++i)
            positionPerPilotSlot.add(zeroInt);
        for(int i = 0; i < numSlots; ++i)
            positionPerPilotSlot.set(mPilotOrdering.get(i), i+1);

        return positionPerPilotSlot;
    }

    //----------------------------------------------------------------------------------------------
    public Vector<Integer> getLapCount()
    {
        return mLapCount;
    }

    //----------------------------------------------------------------------------------------------
    public Vector<Float> getPilotTimes()
    {
        return mTotalTimes;
    }

    //----------------------------------------------------------------------------------------------
    public Vector<Integer> getBestLapIdx()
    {
        return mBestLapIdx;
    }

    //----------------------------------------------------------------------------------------------
    public Vector<Float> getBestLapTimes()
    {
        return mBestLapTimes;
    }

    //----------------------------------------------------------------------------------------------
    public Integer getBestLapTimeIndex(int iPilot)      // 1-based pilot index
    {
        int iFastestLap = -1;
        float timeFastestLap = 99999.0f;
        for(int i = 0; i < mLapCount.get(iPilot - 1); ++i)
        {
            Vector<Laptime> lapTime1 = getTableItem(i+1);
            Laptime thisPilotTime = lapTime1.get(iPilot - 1);

            if(thisPilotTime != null)
            {
                float thisLaptime = thisPilotTime.laptime;

                if(thisLaptime < timeFastestLap)
                {
                    timeFastestLap = thisLaptime;
                    iFastestLap = i;
                }
             }
         }

         return iFastestLap;
    }

    //----------------------------------------------------------------------------------------------
    public Vector<Float> getAvgTimes()
    {
        return mAvgTimes;
    }

    //----------------------------------------------------------------------------------------------
    // Simulate some believable random passing data to use during simulator development
    //
    public void simulateRandomPassing()
    {
        protocolDecoder.communicationDetection dummy1 = new protocolDecoder.communicationDetection();

        dummy1.decoderId = 1;
        dummy1.detectionNumber = 0;
        dummy1.pilotId = 1;
        dummy1.puckTime = 10000;
        dummy1.detectionPeakHeight = 1;
        dummy1.detectionHits = 1;
        dummy1.detectionFlags = 0xff;

        for(int iHits = 0; iHits < 80; ++iHits)
        {
            Random r = new Random();
            int iPilot = r.nextInt(8) + 1;          // 8 pilot slots
            dummy1.pilotId = iPilot;

            Random rTime = new Random();
            dummy1.puckTime += 10e6 + r.nextInt(500000);    // random time
            dummy1.appTime = -1;
            addItem(dummy1);

            ++dummy1.detectionNumber;
        }
    }

    //----------------------------------------------------------------------------------------------
    // create the first passing records for races of the type 'on the tone'
    //
    public void createOnTheTonePassingRecords()
    {
        bCreateOnTheToneRecordsPending = true;     // flag that when we finally receive the first passing record from the timer, we need to create the 'on the tone' records
    }

    //----------------------------------------------------------------------------------------------
    public void addItem(final protocolDecoder.communicationDetection detection)
    {
        // this is a little risky, when the race is not active (started), ignore the detection. Might be risky
        // if the puck goes out of range, then back in, and missing detections are gathered... needs more
        // thought. The puck itself should really handle this shit.
        //
        if(!bRaceActive)
            return;
        if(bIsFirstCrossing)
        {
            if(!bCreateOnTheToneRecordsPending)
                resetRaceTimeOnFirstCrossing();         // reset the clock on first crossing, but only if not in the 'start from the tone' mode
            bIsFirstCrossing = false;
        }

        // do we need to create the 'on the tone' records?
        if(bCreateOnTheToneRecordsPending)
        {
            // WARNING: In this section we do recurse back into this method. Ensure that we set
            // bCreateOnTheToneRecordsPending = false so that we don't come back in here
            bCreateOnTheToneRecordsPending = false;

            long timeNowMs = System.currentTimeMillis();
            long timeFromToneToNow = timeNowMs - raceStartTimems;

            protocolDecoder.communicationDetection onTheToneDetection = new protocolDecoder.communicationDetection();
            onTheToneDetection.puckTime = detection.puckTime - (timeFromToneToNow * 1000);                     // offset the crossing time by the time from tone to now
            onTheToneDetection.appTime = 0;
            onTheToneDetection.detectionNumber = detection.detectionNumber - 8;                                // make some room for the 8 detections (one per pilot)
            onTheToneDetection.detectionPeakHeight = 1;
            onTheToneDetection.detectionHits = 1;
            onTheToneDetection.detectionFlags = 0xff;

            // same time and detection number for all (potentially) 8 pilots
            for(int iPilot = 1; iPilot <= 8; ++iPilot)
            {
                onTheToneDetection.pilotId = iPilot;
                addItem(onTheToneDetection);

                ++onTheToneDetection.detectionNumber;
            }
        }

        boolean inserted = false;

        int pilot = detection.pilotId;          // 1-based
        Laptime lt = new Laptime(pilot, 0);
        lt.timestamp = detection.puckTime;
        lt.peak = detection.detectionPeakHeight;
        lt.lapnumber = detection.detectionNumber;

        lt.flags = detection.detectionFlags;

        if (lt.flags == 0xFF)
        {
            // resent lap, find where to insert it in the table

            int idxPrevious = 0;
            int idxNext= mLaptimes.size();
            int idxPreviousDetectionForThisPilot = -1;
            int idxNextDetectionForThisPilot = -1;

            boolean alreadyInArray = false;

            for (int i = 0; i < mLaptimes.size(); i++)
            {
                Laptime ltTmp = mLaptimes.get(i);
                if (ltTmp.lapnumber == lt.lapnumber)
                {
                    alreadyInArray = true;
                    break;
                }
                else
                {
                    if (ltTmp.lapnumber < lt.lapnumber)
                    {
                        idxPrevious = i;
                        if (ltTmp.pilot == lt.pilot)
                            idxPreviousDetectionForThisPilot = i;
                    }
                }
            }

            for (int i = mLaptimes.size() - 1; i >= 0 ; i--)
            {
                Laptime ltTmp = mLaptimes.get(i);
                if (ltTmp.lapnumber == lt.lapnumber)
                {
                    alreadyInArray = true;
                    break;
                }
                else
                {
                    if (ltTmp.lapnumber > lt.lapnumber)
                    {
                        idxNext = i;

                        if (ltTmp.pilot == lt.pilot)
                            idxNextDetectionForThisPilot = i;
                    }
                }
            }

            if ( !alreadyInArray )
            {
                // update timestamps
                if (idxPreviousDetectionForThisPilot > -1)
                    lt.laptime = (lt.timestamp - mLaptimes.get(idxPreviousDetectionForThisPilot).timestamp)/ 1000000.0f;
                else
                    lt.laptime = 0.0f;

                if (idxNextDetectionForThisPilot > -1)
                    mLaptimes.get(idxNextDetectionForThisPilot).laptime = (mLaptimes.get(idxNextDetectionForThisPilot).timestamp - lt.timestamp)/ 1000000.0f;

                // ensure that we respect the max. lap count for this pilot
                //
                long thisPilot = lt.pilot;
                long thisPilotNumLaps = getLapCount().get((int) thisPilot - 1);

                if(thisPilotNumLaps >= maxLapsThisRace)
                {
                    // don't record this lap, already hit the max. for the race
                    // TODO: When we re-read laps missed due to BT disconnect, do we need to worry here?
                    return;
                }

                // insert into array
                mLaptimes.add(idxNext, lt);
                inserted = true;
            }
        }
        else
        {
            // find last element for this pilot
            Laptime last = null;

            for (int i = mLaptimes.size() - 1; i >= 0 ; i--)
            {
                Laptime ltTmp = mLaptimes.get(i);
                if (ltTmp.pilot == lt.pilot)
                {
                    last = ltTmp;
                    break;
                }
            }
            lt.timestamp = detection.puckTime;
            if (last == null)
            {
                lt.laptime = 0.0f;
            }
            else
            {
                lt.laptime = (detection.puckTime - last.timestamp) / 1000000.0f;
            }

            // ensure that we respect the max. lap count for this pilot
            //
            long thisPilot = lt.pilot;
            long thisPilotNumLaps = getLapCount().get((int) thisPilot - 1);

            if(thisPilotNumLaps >= maxLapsThisRace)
            {
                // pilot has already reached max. laps, don't record this one
                return;
            }

            mLaptimes.add(lt);
            inserted = true;
            ++thisPilotNumLaps;

            mParentService.pilotFinishedLap((int) lt.pilot, lt, (int) thisPilotNumLaps, maxLapsThisRace);
        }

        // if we inserted a record, recompute various tables, including lap counts, etc.
        //
        if (inserted)
            updatePilotTables();
    }

    //----------------------------------------------------------------------------------------------
    public void checkForMissing()
    {
        // check for missing data at start
        //
        if ( mLaptimes.size() > 0 )
        {
            Laptime lt = mLaptimes.get(0);
            if ( lt.lapnumber > startLapCount )
            {
                mParentService.resendPackets(startLapCount, lt.lapnumber - 1);
            }
        }

        // Check for holes in data
        //
        for (int i = 0; i < mLaptimes.size() - 1; i++)
        {
            Laptime lt = mLaptimes.get(i);
            Laptime lt_n = mLaptimes.get(i + 1);

            if (lt.lapnumber != lt_n.lapnumber - 1 )
            {
                mParentService.resendPackets(lt.lapnumber + 1, lt_n.lapnumber - 1);
            }
        }
    }

    //----------------------------------------------------------------------------------------------
    public void clear()
    {
        if (mLaptimes.size() > 0)
            startLapCount = mLaptimes.lastElement().lapnumber + 1;

        mLaptimes.clear();
        updatePilotTables();
    }

    //----------------------------------------------------------------------------------------------
    public int getCount()
    {
        return mLaptimes.size();
    }

    //----------------------------------------------------------------------------------------------
    public Laptime getItem(int position)
    {
        return mLaptimes.get(position);
    }

    //----------------------------------------------------------------------------------------------
    public long getItemId(int position)
    {
        return mLaptimes.get(position).lapnumber;
    }

    //----------------------------------------------------------------------------------------------
    public int getTableCount()
    {
        int max = 0;
        for (int i = 0; i < numSlots; i++)
        {
            int size = mPilotsTable.get(i).getCount();
            if ( size > max )
                max = size;
        }

        return max;
    }

    //----------------------------------------------------------------------------------------------
        public Vector<Laptime> getTableItem(int position) {
        Vector<Laptime> vlt = new Vector<Laptime>();

        for (int i = 0; i < numSlots; i++)
        {
            int size = mPilotsTable.get(i).getCount();
            if ( position < size )
            {
                vlt.add(mPilotsTable.get(i).getItem(position));
            }
            else
                vlt.add(null);
        }

        return vlt;
    }

    //----------------------------------------------------------------------------------------------
    public long getTableItemId(int position)
    {
        return position;
    }
}
