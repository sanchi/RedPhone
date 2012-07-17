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

import org.thoughtcrime.redphone.ApplicationContext;
import org.thoughtcrime.redphone.profiling.PacketLogger;
import org.thoughtcrime.redphone.ui.ApplicationPreferencesActivity;
import org.thoughtcrime.redphone.util.LeakyIntegrator;

/**
 * Selects the number of packets to buffer to avoid underruns while minimizing latency.
 * The desired buffer level is adjusted smoothly within fixed bounds.
 *
 * @author Stuart O. Anderson
 */
public class DesiredCallAudioDelayChooser {
  private static final String TAG = "DelayChooser";
  private final boolean minimizeLatency =
          ApplicationPreferencesActivity.isMinimizeLatency(ApplicationContext.getInstance().getContext());

  private final float maxDesFrameDelay = 12;
  private final float minDesFrameDelay = 0.5f;

  private static final int dropoutThreshold = 10;

  private LeakyIntegrator lateCount = new LeakyIntegrator(1000); //how many packets arrived late
  private LeakyIntegrator jitCount  = new LeakyIntegrator(1000);  //how many packets arrived 'just in time'
  private LeakyIntegrator lostCount = new LeakyIntegrator(1000); //how many packets never arrived
  private LeakyIntegrator veryLateCount = new LeakyIntegrator(1000);

  private DropoutTracker dropoutTracker;

  //these just decay slowly after state changes to limit max rate of change
  private LeakyIntegrator lateCountDelay = new LeakyIntegrator(25);
  private LeakyIntegrator jitCountDelay = new LeakyIntegrator(25);

  private float dynDesFrameDelay;

  public DesiredCallAudioDelayChooser( PacketLogger packetLogger) {
    dropoutTracker = new DropoutTracker( packetLogger );
    dynDesFrameDelay = ApplicationPreferencesActivity
            .getCallStreamDesBufferLevel(ApplicationContext.getInstance().getContext() );
  }

  public float getDesFrameDelay() {
    return dynDesFrameDelay;
  }

  public void notifyArrival( long seqNum ) {
    dropoutTracker.observeSequenceNumber(seqNum);
  }

  public void notifyMissing() {
    lostCount.observe(1);
  }

  public void notifyLate( int nLate ) {
    lateCount.observe( nLate );
    lostCount.observe( -nLate );
  }

  public void notifyVeryLate( long nVLate ) {
    if( nVLate >= maxDesFrameDelay ) {
      lateCount.observe( maxDesFrameDelay );
      veryLateCount.observe( nVLate - maxDesFrameDelay );
    } else {
      lateCount.observe( nVLate );
    }
    lostCount.observe( -nVLate );
  }

  public void notifyJustInTime() {
    jitCount.observe( 1 );
  }

  private void setDebugVariables() {
    CallLogger.jitCount = jitCount.get();
    CallLogger.lateCount = lateCount.get();
    CallLogger.lostCount = lostCount.get();
    CallLogger.desBufLvl = dynDesFrameDelay;
    CallLogger.veryLateCount = veryLateCount.get();
  }

  public void updateDesired() {
    setDebugVariables();

    lateCount.observe(0);
    lostCount.observe(0);
    jitCount.observe(0);
    veryLateCount.observe(0);

    lateCountDelay.observe(0);
    jitCountDelay.observe(0);

    if( minimizeLatency ) {
      dynDesFrameDelay = 0.5f;
      return;
    }

    //Event Threshold Based Logic
    float bestThreshold = dropoutTracker.getDepthForThreshold( dropoutThreshold );
    final float w = 1 - 1/100f;
    dynDesFrameDelay = dynDesFrameDelay * w + (1-w) * bestThreshold;


    if( dynDesFrameDelay > maxDesFrameDelay ) dynDesFrameDelay = maxDesFrameDelay;
    if( dynDesFrameDelay < minDesFrameDelay ) dynDesFrameDelay = minDesFrameDelay;
  }

  public void terminate() {
    ApplicationPreferencesActivity
            .setCallStreamDesBufferLevel(ApplicationContext.getInstance().getContext(),dynDesFrameDelay);
  }
}
