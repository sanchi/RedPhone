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

package org.thoughtcrime.redphone.network;

import android.os.SystemClock;
import android.util.Log;

import org.thoughtcrime.redphone.audio.EncodedAudioData;
import org.thoughtcrime.redphone.crypto.SecureRtpPacket;
import org.thoughtcrime.redphone.crypto.SecureRtpSocket;
import org.thoughtcrime.redphone.profiling.PacketLogger;
import org.thoughtcrime.redphone.profiling.StatisticsWatcher;

import java.io.IOException;
import java.util.LinkedList;

/**
 * RtpAudioSender bundles one or more {@link EncodedAudioData} objects into a
 * {@link SecureRtpPacket} and writes that packet to the provided {@link SecureRtpSocket}
 *
 * @author Stuart O. Anderson
 */
public class RtpAudioSender {
  public final static int audioChunksPerPacket = 2;
  private int packetSequenceNumber = 0;
  private SecureRtpSocket socket;
  private LinkedList<EncodedAudioData> audioQueue;
  private final byte payloadBuffer[] = new byte[1024];
  private final SecureRtpPacket outPacket = new SecureRtpPacket(1024);

  private StatisticsWatcher timeWatcher = new StatisticsWatcher();
  private long lastTime;
  private long mSentPackets = 0;
  private int consecutiveSends = 0;
  private int totalSends = 0;

  private PacketLogger packetLogger;

  public RtpAudioSender(LinkedList<EncodedAudioData> outgoingAudio,
                        SecureRtpSocket socket, PacketLogger packetLogger) {
    this.socket       = socket;
    this.audioQueue   = outgoingAudio;
    this.packetLogger = packetLogger;

    timeWatcher.debugName = "RtpAudioSender";
  }



  public void go() throws IOException {

    if( audioQueue.size() < audioChunksPerPacket ) {
      consecutiveSends = 0;
      return;
    }

    consecutiveSends++;
    totalSends++;
    if( consecutiveSends > 15 && consecutiveSends%20==0) {
      Log.e( "RtpAudioSender", "consecutiveSends=" + consecutiveSends + " totalSends=" + totalSends);
    }

    int payloadOffset = 0;
    for( int packet = 0; packet < audioChunksPerPacket; packet++ ) {
      if( audioQueue.size() != 0 ) {
        EncodedAudioData ead = audioQueue.removeFirst();
        byte chunkData[] = ead.data;
        System.arraycopy(chunkData, 0, payloadBuffer, payloadOffset, chunkData.length );

        if( payloadOffset == 0 ) {
          //TODO is the cast-to-int a problem?
          packetLogger.logPacket( ead.sequenceNumber, PacketLogger.PACKET_SENDING );
        }
        else {
          packetLogger.logPacket( ead.sequenceNumber, PacketLogger.PACKET_BUNDLED );
        }

        payloadOffset += chunkData.length;
      }
    }

    outPacket.setPayload(payloadBuffer, payloadOffset);

    outPacket.setSequenceNumber(packetSequenceNumber);
    socket.send(outPacket);
    mSentPackets++;
    packetSequenceNumber++;

    printDebug();
  }

  private void printDebug() {
    long now = SystemClock.uptimeMillis();
    timeWatcher.observeValue((int)(now-lastTime));
    lastTime = now;
  }

  public int getSequenceNumber() {
    return packetSequenceNumber;
  }

  public long sentPackets() {
    return mSentPackets;
  }
}
