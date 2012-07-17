/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.redphone.audio;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import org.thoughtcrime.redphone.ApplicationContext;
import org.thoughtcrime.redphone.profiling.PeriodicTimer;
import org.thoughtcrime.redphone.ui.ApplicationPreferencesActivity;
import org.thoughtcrime.redphone.util.LeakyIntegrator;

/**
 * LatencyMinimizingAudioPlayer transfers audio data from an audio source to and audio sink
 * It attempts to learn the minimum desired buffer level the sink can have while avoiding
 * underflows.  Data is only read from the source when needed to refill the sink to the desired
 * buffer level.  The audio source must always provide new audio data when requested, and must
 * be robust to having fewer than the expected number of requests made.
 *
 * @author Stuart O. Anderson
 */
public class LatencyMinimizingAudioPlayer {
  // maximum leaky integrated zero buffers before we push up the desired buffer level
  private final float zeroBufferEventsBeforeIncrease = 4;
  private final float zeroBufferEventsBeforeDecrease = 1;
  //msec before we try reducing the desired buffer level
  private final long baseRecoveryTime = 15000;

  //these change dynamically
  private int dynamicDesiredBufferLevel;
  private long recoveryTime = baseRecoveryTime; //time before we try reducing the desired buffer level
  private LeakyIntegrator zeroBufferObserver = new LeakyIntegrator( 80000 );
  private int lastDesBufferChange = -1; //start in the 'just decreased' state
  private long lastZeroBufferEventTime;

  private static final boolean DEBUG = true;

  //debug variables [accumulated since last debug print]
  private int zeroBufferCount = 0;
  PeriodicTimer debugTimer = new PeriodicTimer(5000);

  private RobustAudioTrack audioPlayer;
  private CallAudioProvider audioStream;
  private static final String TAG = "LatencyMinimizingAudioPlayer";

  public LatencyMinimizingAudioPlayer(CallAudioProvider audioStream,
                                      RobustAudioTrack audioPlayer) {
    this.audioStream = audioStream;
    this.audioPlayer = audioPlayer;
    //load the desired level from the preferences
    Context context = ApplicationContext.getInstance().getContext();
    dynamicDesiredBufferLevel = ApplicationPreferencesActivity.getAudioTrackDesBufferLevel(context);
  }

  public void update() {
    if (DEBUG)
      printDebug();

    audioPlayer.update();

    int remainder = audioPlayer.getBufferRemaining();
    if (remainder <= 0) {
      zeroBufferCount++;
      lastZeroBufferEventTime =  SystemClock.uptimeMillis();
      zeroBufferObserver.observe( 1 );
    } else {
      zeroBufferObserver.observe( 0 );
    }

    updateDynamicBufferLevelAdjustment();

    getAudio();
  }

  private void updateDynamicBufferLevelAdjustment() {
    //increase buffer level if we've had too many zero buffers lately
    if( zeroBufferObserver.get() > zeroBufferEventsBeforeIncrease ) {
      dynamicDesiredBufferLevel += 50;
      zeroBufferObserver.reset();
      if( lastDesBufferChange == -1 ) {
        recoveryTime *= 2; //if we reduced, and are now increasing again, double the recovery time
      }
      lastDesBufferChange = 1; //indicates last change was an increase
    }

    //if it has been a while since we had a zero-buffer, and the zero-buffer rate is very low, try decreasing the desired buffer level
    long now = SystemClock.uptimeMillis();
    long timeSinceZeroBufferEvent = now - lastZeroBufferEventTime;
    if( timeSinceZeroBufferEvent > recoveryTime &&
      zeroBufferObserver.get() < zeroBufferEventsBeforeDecrease ) {
      lastZeroBufferEventTime = now;
      dynamicDesiredBufferLevel -= 25;
      if( lastDesBufferChange == -2 ) {
        recoveryTime = baseRecoveryTime; //if we've already had a pair, and we get a 3rd, reset to baseline
      } else
      if( lastDesBufferChange == -1 ) {
        recoveryTime = Math.max( recoveryTime/2, baseRecoveryTime );
        lastDesBufferChange = -2; //-2 indicates we already has a pair of des level reductions
      } else {
       lastDesBufferChange = -1; //indicates prior change was a reduction
      }
    }

  }

  //pulls audio from the stream to fill up to threshold
  private void getAudio() {
    int framePulls = 0; //this gets us out of the loop if we never manage to get enough audio out of the stream, so we can die gracefully
    while (audioPlayer.getBufferRemaining() <= dynamicDesiredBufferLevel &&
        framePulls < 100 ) {

      short nextData[] = audioStream.getFrame();
      int frameSize = audioStream.getFrameSize();
      //Log.d("ATM", "buf: " + getBufferRemaining() + " nxt: "
      //		+ frameSize + " t: " + SystemClock.uptimeMillis());
      audioPlayer.writeChunk(nextData, frameSize);
      //Log.d("ATM", "aft: " + getBufferRemaining() + " t: "
      //		+ SystemClock.uptimeMillis());
      framePulls++;
    }
  }

  private void printDebug() {
    if (!debugTimer.periodically())
      return;
    Log.d("ATM"," zeroBufferCount: " + zeroBufferCount
          + " zeroBufferObserver: " + zeroBufferObserver.get()
          + " bufLevel: "+ audioPlayer.getBufferRemaining()
          + " desBufLvl: " + dynamicDesiredBufferLevel);
    zeroBufferCount = 0;
  }

  public void terminate() {
    if( audioPlayer != null ) audioPlayer.terminate();

    //store the current dynDesBufferLevel for next time
    ApplicationPreferencesActivity.setAudioTrackDesBufferLevel(ApplicationContext.getInstance().getContext(),
            dynamicDesiredBufferLevel );
  }
}