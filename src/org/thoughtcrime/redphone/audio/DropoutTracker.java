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

import android.os.SystemClock;

import org.thoughtcrime.redphone.network.RtpAudioSender;
import org.thoughtcrime.redphone.profiling.PacketLogger;
import org.thoughtcrime.redphone.profiling.PeriodicTimer;

/**
 * When a network dropout occurs packet latency will increase quickly to a maximum latency before
 * quickly returning to a normal value.  These dropouts create local peaks in latency that we can
 * detect.
 *
 * DropoutTracker registers when packets with given sequence numbers arrive and
 * attempts to predict when additional packets should arrive based on this information.
 *
 * The predicted arrival times allow the estimation of an arrival  lateness value for each packet
 * The last several lateness values are tracked and local peaks in lateness are detected
 *
 * Peak latencies above a the "threshold of actionability" (300msec) are discarded since we never
 * want to buffer more than 300msec worth of audio packets.
 *
 * We track how many peaks occurred in several latency ranges (expressed as a packet count) and
 * provide the ability to answer the question:
 *
 * If we wanted to have only N buffer underflows in the past M seconds, how many packets would need
 * to be stored in the buffer?
 *
 * @author Stuart O. Anderson
 */
//TODO this class assumes a codec frame is 20msec - not true for non-speex
public class DropoutTracker {
  private final static long maxActionableLatency = 300;
  private float zeroTimeOffset;
  private static final float u = 1/50f; //adaptivity of zero estimate
  private static final float binsPerPacket = 2;

  private long priorLateness[] = new long[6];
  private EventWindow[] lateBins = new EventWindow[20];
  private PacketLogger packetLogger;
  private long zeroTime, zeroTimeBase;
  private boolean zeroTimeInitialized = false;
  private PeriodicTimer debugTimer = new PeriodicTimer(1000);


  public DropoutTracker( PacketLogger packetLogger ) {
    this.packetLogger = packetLogger;

    for( int i=0; i <lateBins.length; i++ ) {
      lateBins[i] = new EventWindow( 30000 );
    }
  }

  private long getExpectedSequenceNumber( long now ) {
    return (now - zeroTime)/(20 * RtpAudioSender.audioChunksPerPacket);
  }

  private long getExpectedTime( long seqNum ) {
    return zeroTime + seqNum * 20 * RtpAudioSender.audioChunksPerPacket;
  }

  //TODO(Stuart Anderson): Use a list or ring buffer here
  private void insertLateness( long msecLate ) {
    for( int i=0; i < priorLateness.length-1; i++ ) {
      priorLateness[i] = priorLateness[i+1];
    }
    priorLateness[priorLateness.length-1] = msecLate;
  }

  private long detectPeak() {
    long possiblePeakLatency = priorLateness[priorLateness.length/2];

    for( int i=0; i < priorLateness.length-1; i++ ) {
      if( priorLateness[i] > possiblePeakLatency ) {
        return -1;
      }
    }

    return possiblePeakLatency;
  }

  public void observeSequenceNumber( long seqNum ) {
    if( !zeroTimeInitialized ) {
      zeroTimeBase = SystemClock.uptimeMillis();
      zeroTimeInitialized = true;
    }
    zeroTime = zeroTimeBase + (long)zeroTimeOffset;

    long expectedTime = getExpectedTime( seqNum );
    long now = SystemClock.uptimeMillis();
    long msecLate = now-expectedTime;

    packetLogger.logPacket( getExpectedSequenceNumber( now ), PacketLogger.EXPECTED_PACKET_NUM, (int)msecLate );

    //Was the last packet a local peak?
    insertLateness( msecLate );
    long peakLatency;

    if( (peakLatency = detectPeak() ) > 0) {
      int lateBin = (int)(peakLatency/(20f * RtpAudioSender.audioChunksPerPacket / binsPerPacket ));
      if( lateBin < 0 ) lateBin = 0;
      if( lateBin >= lateBins.length ) {
        lateBin = lateBins.length - 1;
      }

      if( peakLatency <= maxActionableLatency )
        lateBins[lateBin].addEvent(now);

      packetLogger.logPacket( seqNum, PacketLogger.LATENCY_PEAK, (int) peakLatency );
    }

    //update zero time
    if( msecLate < 0 ) {	//if a packet arrives early, immediately update the timebase
      zeroTimeOffset += msecLate;
    } else {
      zeroTimeOffset += msecLate * u; //if it arrives late, conservatively update the timebase
    }
  }

  //How many packets would we have needed to buffer to
  //stay below the desired dropout event count
  public float getDepthForThreshold( int maxEvents ) {
    int eventCount = 0;
    int depth;
    long now = System.currentTimeMillis();
    for( depth = lateBins.length-1; depth >= 0; depth-- ) {
      eventCount += lateBins[depth].countEvents(now);
      if( eventCount > maxEvents ) {
        break;
      }
    }
    return depth/binsPerPacket;
  }
}
