// Splash screen activity, displays a bitmap during app. load, and then switches to the original
// launch activity
// Anthony Cake: July 2017

package com.immersionrc.LapRFTiming;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import java.util.Random;


public class SplashActivity extends AppCompatActivity
{
    private static MediaPlayer mediaPlayer;           // keep a copy around to avoid garbage collection (which cuts the sound short as the media player gets cleaned up)

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        String[] welcomePhrases = getResources().getStringArray(R.array.random_welcome_phrases);
        int numPhrases = welcomePhrases.length;

        Random r = new Random();
        int pickPhrase = r.nextInt(numPhrases);
        String phrase = welcomePhrases[pickPhrase];

        Context context = getApplicationContext(); // or getBaseContext(), or getApplicationContext()
        int resourceId = getResources().getIdentifier(phrase, "raw", context.getPackageName());


        mediaPlayer = MediaPlayer.create(this, resourceId);
        try
        {
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.start();
        }
        catch(Exception e) {

        }

        Intent intent = new Intent(this, NewRaceActivity.class);
        startActivity(intent);
        finish();
    }
}