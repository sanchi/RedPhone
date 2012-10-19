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

import org.thoughtcrime.redphone.codec.AudioCodec;
import org.thoughtcrime.redphone.profiling.PacketLogger;

import java.util.List;

/**
 * CallAudioStream constructs the audio output processing pipeline that plays an incoming
 * queue of {@link EncodedAudioData} on the device's audio hardware and tracks call quality
 * performance.
 *
 * @author Stuart O. Anderson
 */
//TODO(Stuart Anderson): Separate the Builder aspect of this class from the audio pipeline it builds

public class CallAudioStream {
  private final LatencyMinimizingAudioPlayer audioPlayer;
  private final CallAudioProvider audioProvider;
  private final List<EncodedAudioData> incomingAudio;
  private final PacketLogger packetLogger;
  private final CallLogger callAudioLog;

  public CallAudioStream(List<EncodedAudioData> incomingAudio,
                         AudioCodec codec,
                         PacketLogger packetLogger,
                         CallLogger callAudioLog) {
    audioProvider = new CallAudioProvider( codec, packetLogger, callAudioLog );
    this.callAudioLog = callAudioLog;
    this.packetLogger = packetLogger;
    audioPlayer = new LatencyMinimizingAudioPlayer(audioProvider, new RobustAudioTrack());
    this.incomingAudio = incomingAudio;
  }

  public void go() {
    while( incomingAudio.size() != 0 ) {
      EncodedAudioData ead = incomingAudio.remove(0);
      packetLogger.logPacket( ead.sequenceNumber, PacketLogger.PLAY_QUEUE_INSERT,
              incomingAudio.size() );
      audioProvider.addFrame( ead );
    }
    audioPlayer.update();
  }

  public void terminate() {
    audioPlayer.terminate();
    audioProvider.terminate();
    callAudioLog.terminate();
  }
}
