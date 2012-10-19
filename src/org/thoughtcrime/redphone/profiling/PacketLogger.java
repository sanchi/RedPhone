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

package org.thoughtcrime.redphone.profiling;

import android.content.Context;
import android.os.SystemClock;

import org.thoughtcrime.redphone.Release;
import org.thoughtcrime.redphone.audio.FileLogger;

import java.io.IOException;

/**
 * Logs information about audio packet events to a file.
 *
 * The log line format is:
 * timeInMillis packetSequenceNumber stage extra
 *
 * where stage is one of the constants defined in this file.
 * This allows reconstruction of the at which a specific packet hit different parts of the audio
 * processing pipeline.
 *
 * The sequence number is always the codec sequence number, not the RTP sequence number
 *
 * @author Stuart O. Anderson
 */
public class PacketLogger extends FileLogger {
  public static final String PACKET_DATA_FILENAME = "packetData.txt";
  public static final String TAG = "PacketLogger";

  public static final int PACKET_IN_MIC_QUEUE = 0;
  public static final int PACKET_ENCODED      = 1;
  public static final int PACKET_SENDING      = 2;
  public static final int PACKET_BUNDLED      = 3;
  public static final int PACKET_RECEIVED     = 4;
  public static final int PACKET_DECODED      = 5;
  public static final int PLAYHEAD			= 6;
  public static final int PLAYHEAD_JUMP_FORWARD = 7;
  public static final int PLAYHEAD_JUMP_BACK  = 8;
  public static final int PLAY_BUFFER_EMPTY   = 9;
  public static final int FILLING_GAP			= 10;
  public static final int PLAY_QUEUE_INSERT   = 11;
  public static final int EXPECTED_PACKET_NUM = 12;
  public static final int LATENCY_PEAK		= 13;
  public static final int FAILED_READ			= 14;
  private static final long decimate = 1;

  public PacketLogger(Context context) {
    super(context, PACKET_DATA_FILENAME);
  }

  public void logPacket( long packetNumber, int stage ) {
    logPacket( packetNumber, stage, -1 );
  }

  public void logPacket( long packetNumber, int stage, int extra ) {
    if( !Release.DELIVER_DIAGNOSTIC_DATA ) return;
    if( packetNumber % decimate != 0 && stage <= 6 ) return;
    String info = new String() +
      SystemClock.uptimeMillis() + " " +
      packetNumber + " " +
      stage + " " +
      extra + "\n";
    writeLine( info );
  }

}
