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
import android.util.Log;

import org.thoughtcrime.redphone.Release;

import java.text.DecimalFormat;

/**
 * Call logger writes several variables related to call quality to a file that can later be
 * used for debugging.
 *
 * @author Stuart O. Anderson
 */
public class CallLogger extends FileLogger {
  public static final String TIMING_DATA_FILENAME = "timingData.txt";
  public static final String TAG                  = "CallAudioStream";

  private DecimalFormat logFormat = new DecimalFormat("0.00");

  static long sequenceNumber, streamPlayheadPosition, largestHeldFrame;
  static int shiftMode, waitingFrames;
  static float desBufLvl, lateCount, lostCount, jitCount, veryLateCount;
  static float avgDelay;

  static int gapLengthCounts[] = new int[100];

  CallLogger() {
    super( TIMING_DATA_FILENAME );
  }

  private void writeLogLine() {
    if( !Release.DELIVER_DIAGNOSTIC_DATA ) return;
    String info = new String() +
      SystemClock.uptimeMillis() + " " +
      streamPlayheadPosition + " " +
      waitingFrames + " " +
      lateCount + " " +
      lostCount + " " +
      jitCount + " " +
      veryLateCount + " " +
      desBufLvl + " " +
      avgDelay + " " +
      shiftMode + " " + //10
      largestHeldFrame + "\n";

      writeLine( info );
  }

  private void printDebug() {
    if( !pt.periodically() )return;

    final String line =  ""
        + " waiting:" + waitingFrames
        //+ " framedelay: " + frameDelay
        //+ " avgframedelay: " + logFormat.format(averageFrameDelay.getAvg())
        + " dynDes: "+ desBufLvl
        + " late: " + logFormat.format(lateCount)
        + " lost: " + logFormat.format(lostCount)
        + " jit: "  + logFormat.format(jitCount)
        + " veryLate: " + logFormat.format(veryLateCount)
        + " avgDelay: " + logFormat.format(avgDelay)
        + " shiftMode: " + shiftMode
        //+ " rate: " + logFormat.format(playRate)
        //+ " frameavgsize: " + logFormat.format(avgFrameObs.getAvg())
        //+ " avgsframespp: " + logFormat.format(avgSamplesPerPacket.getAvg()
        ;
    Log.d(TAG, line);

  }

  @Override
  public void terminate() {
    super.terminate();
    printGapLengths();
  }

  private void printGapLengths() {
    String gapLengthStr = new String( "Gap Length Counts\n");
    for( int i=0; i < gapLengthCounts.length; i++ ) {
      if( gapLengthCounts[i] != 0 ) {
        gapLengthStr += " " + i + " : " + gapLengthCounts[i] + "\n";
      }
    }

    Log.d( TAG, gapLengthStr );
  }

  public void update() {
    writeLogLine();
    printDebug();
  }
}
