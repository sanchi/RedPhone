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

import org.thoughtcrime.redphone.codec.AudioCodec;
import org.thoughtcrime.redphone.crypto.SecureRtpSocket;
import org.thoughtcrime.redphone.network.RtpAudioReader;
import org.thoughtcrime.redphone.network.RtpAudioSender;
import org.thoughtcrime.redphone.profiling.PacketLogger;
import org.thoughtcrime.redphone.profiling.StatisticsWatcher;
import org.thoughtcrime.redphone.profiling.TimeProfiler;
import org.thoughtcrime.redphone.ui.ApplicationPreferencesActivity;

import java.io.IOException;
import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * CallAudioManager controls the reading and writing of audio data from between the network stack
 * and the device's audio subsystem.  Audio data is read from the
 * {@link org.thoughtcrime.redphone.network.RtpAudioReader}, queued, and then sent
 * to the {@link CallAudioStream} where it is decoded and written to the hardware audio buffer.
 *
 * Raw audio from the microphone input buffer is encoded by the {@link MicrophoneReader}, then
 * queued before being sent to the {@link org.thoughtcrime.redphone.network.RtpAudioSender}.
 *
 * @author Stuart O. Anderson
 */
public class CallAudioManager {
  private final LinkedList<EncodedAudioData> outgoingAudio = new LinkedList<EncodedAudioData>();
  private final LinkedList<EncodedAudioData> incomingAudio = new LinkedList<EncodedAudioData>();

  private boolean callDone = false;
  private long extraReads = 0;

  private MicrophoneReader micReader;
  private RtpAudioSender netSender;
  private RtpAudioReader netReader;
  private CallAudioStream audioStream;
  private AudioCodec codec;
  private boolean runStarted = false;
  private boolean loopbackMode;
  private boolean simDrops;
  private LinkedList<EncodedAudioData> stolenAudio = new LinkedList<EncodedAudioData>();

  private PacketLogger packetLogger = new PacketLogger();

  public CallAudioManager( SecureRtpSocket socket, String codecID, Context context ) {
    codec = AudioCodec.getInstance( codecID ); //begins init

    netSender   = new RtpAudioSender( outgoingAudio, socket, packetLogger );
    netReader   = new RtpAudioReader( incomingAudio, socket, packetLogger );
    //create audioStream before micreader, so they pick up the same audio mode, since audiomode is set in audioStream
    audioStream = new CallAudioStream( incomingAudio, codec, packetLogger );
    micReader   = new MicrophoneReader( outgoingAudio, codec, packetLogger );

    //setup preferences
    loopbackMode  = ApplicationPreferencesActivity.getLoopbackEnabled(context);
    simDrops = ApplicationPreferencesActivity.isSimulateDroppedPackets(context);
  }

  public void run() throws AudioException, IOException {
    try {
      doRun();
    } finally {
      doTerminate();
    }
  }

  //TODO(Stuart Anderson): Split this up
  private void doRun() throws AudioException, IOException {
    synchronized(this) {
      if( callDone ) return;
      runStarted = true;
    }

    if( codec == null ) return;
    codec.waitForInitializationComplete();

    int cycleCount = 0;
    new TimeProfiler().start();
    StatisticsWatcher mrS = new StatisticsWatcher();
    mrS.debugName = "MicReader";
    StatisticsWatcher nsS = new StatisticsWatcher();
    nsS.debugName = "NetSender";
    StatisticsWatcher nrS = new StatisticsWatcher();
    nrS.debugName = "NetReader";
    StatisticsWatcher ewS = new StatisticsWatcher();
    ewS.debugName = "EarWriter";

    micReader.flush();

    while( !callDone ) {
      /*if( pt.periodically() ) {
        Log.w( "CallAudioManager", "executed " + cycleCount + " cycles" + " sendSequenceNumber=" + netSender.getSequenceNumber() + " recvSequenceNumber=" + netReader.sequenceNumber() );
        cycleCount = 0;
        sendCount =0;
        receiveCount = 0;
      }*/
      cycleCount++;
      long t1,t2,t3,t4,t5;
      t1 = SystemClock.uptimeMillis();
      micReader.go();

      if( loopbackMode ) {
        try {
          Thread.sleep(1);//this is where network reader would block
        } catch (InterruptedException e1) {
        }
        t2 = t3 = SystemClock.uptimeMillis();
        if( simDrops ) {
          if( Math.random() < .25 ) {
            try {
              stolenAudio.add(outgoingAudio.remove());
              if( stolenAudio.size() > 1 ) {
                incomingAudio.add( stolenAudio.remove() );

              }
              outgoingAudio.remove();
            }catch ( NoSuchElementException e ) {

            }
          }
        }
        if (outgoingAudio.size() > 0) {
          try {
            EncodedAudioData ead = outgoingAudio.remove();
            incomingAudio.add(ead);
          } catch (NoSuchElementException e) {
          }
        }
      } else {

        t2 = SystemClock.uptimeMillis();
        netSender.go(); //TODO remove debug
        t3 = SystemClock.uptimeMillis();
        netReader.go(); //TODO remove debug
      }

      t4 = SystemClock.uptimeMillis();
      audioStream.go();
      t5 = SystemClock.uptimeMillis();
      if( netSender.sentPackets() > extraReads ) {
        extraReads++;
        netReader.go();
        audioStream.go();
      }

      if( t2-t1 > 100 ) {
        Log.e( "CAM", "micReader time=" + (t2-t1) );
      }
      if( t3-t2 > 100 ) {
        Log.e( "CAM", "netSender time=" + (t3-t2) );
      }
      if( t4-t3 > 100 ) {
        Log.e( "CAM", "netReader time=" + (t4-t3) );
      }
      if( t5-t4 > 100 ) {
        Log.e( "CAM", "earWriter time=" + (t5-t4) );
      }

      mrS.observeValue( (int)(t2-t1) );
      nsS.observeValue( (int)(t3-t2) );
      nrS.observeValue((int) (t4-t3) );
      ewS.observeValue( (int) (t5-t4) );
      if( incomingAudio.size() > 5 ) {
        //Log.e( "CAM", "incoming audio queue length=" + incomingAudio.size() );
      }
      if( outgoingAudio.size() > 5 ) {
        Log.e( "CAM", "outgoing audio queue length=" + outgoingAudio.size() );
      }

    }
  }

  private void doTerminate() {
    TimeProfiler.terminate();
    micReader.terminate();
    audioStream.terminate();
  }

  public void terminate() {
    Log.d("CallAudioManager", "Terminated");
    boolean callTerm = false;
    synchronized( this ) {
      callDone = true;
      if( !runStarted ) callTerm = true;
    }
    if( callTerm ) doTerminate();
  }

  public void setMute(boolean enabled) {
    micReader.setMute(enabled);
  }
}
