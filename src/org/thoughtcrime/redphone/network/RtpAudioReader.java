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

import android.util.Log;

import org.thoughtcrime.redphone.audio.EncodedAudioData;
import org.thoughtcrime.redphone.crypto.SecureRtpPacket;
import org.thoughtcrime.redphone.crypto.SecureRtpSocket;
import org.thoughtcrime.redphone.profiling.PacketLogger;
import org.thoughtcrime.redphone.profiling.PeriodicTimer;
import org.thoughtcrime.redphone.profiling.TimeProfiler;

import java.io.IOException;
import java.util.List;
/**
 * RtpAudioReader listens to a {@link SecureRtpSocket} and writes the incoming {@link EncodedAudioData} to
 * a queue.
 *
 * @author Stuart O. Anderson
 */
public class RtpAudioReader {

  private final List<EncodedAudioData> audioQueue;
  private final SecureRtpSocket socket;
  private final PeriodicTimer recvTimer = new PeriodicTimer((long) (1000/60.0));
  private long expectedSequenceNumber = 0;
  private int sequenceAnomalies = 0;
  private PacketLogger packetLogger;

  public RtpAudioReader(List<EncodedAudioData> incomingAudio,
                        SecureRtpSocket socket, PacketLogger packetLogger) {
    this.socket = socket;
    audioQueue = incomingAudio;
    this.packetLogger = packetLogger;
  }

  private int consecutiveReads = 0;
  private int totalReads = 0;
  public void go() throws IOException {
    //if( !recvTimer.periodically()) return;

    SecureRtpPacket inPacket = socket.receive();

    if( inPacket == null ) {
      consecutiveReads=0;
      packetLogger.logPacket( expectedSequenceNumber, PacketLogger.FAILED_READ );
      return;
    }
    consecutiveReads++;
    totalReads++;
    if( consecutiveReads > 30 ) {
      Log.e("RtpAudioReader", "consecutiveReads=" + consecutiveReads + " totalReads=" + totalReads );
    }

    packetLogger.logPacket( inPacket.getLogicalSequence(), PacketLogger.PACKET_RECEIVED, audioQueue.size() );

    if( inPacket.getLogicalSequence() != expectedSequenceNumber ) {
      sequenceAnomalies++;
      Log.d("RtpAudioReader", "Sequence Anomaly: " + inPacket.getLogicalSequence() + " != " + expectedSequenceNumber );
      expectedSequenceNumber = inPacket.getLogicalSequence();
    }
    expectedSequenceNumber++;

    TimeProfiler.startBlock("VR:receiveAudio:getPayload" );
    byte packetData[] = inPacket.getPayload();
    TimeProfiler.stopBlock("VR:receiveAudio:getPayload" );

    audioQueue.add(new EncodedAudioData(packetData, inPacket.getLogicalSequence(), inPacket.getSequenceNumber() ));
  }

  public long sequenceNumber() {
    return expectedSequenceNumber;
  }
}
