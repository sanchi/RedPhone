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

import android.util.Log;

import org.thoughtcrime.redphone.codec.AudioCodec;
import org.thoughtcrime.redphone.monitor.CallMonitor;
import org.thoughtcrime.redphone.profiling.PacketLogger;
import org.thoughtcrime.redphone.profiling.StatisticsWatcher;

import java.util.TreeMap;

/**
 * The CallAudioProvider stretches and shrinks audio on the fly to mask issues like
 * packet loss or audio clock mismatches.
 *
 * Encoded audio data from the network stack is provided to the CallAudioProvider, which
 * stores it until it is ready to be played.
 *
 * The audio player requests raw audio data which is returned in variable sized chunks.
 * If too much audio is available the audio is sped up or dropped
 * If too little audio is available the audio is slowed down or synthesized based on the
 * last few packets decoded.
 *
 * @author Stuart O. Anderson
 */
public class CallAudioProvider {
  private static final String TAG = "CallAudioProvider";

  private static final int RATE_NORMAL = 0;
  private static final int RATE_BIG    = 1;
  private static final int RATE_LITTLE = 2;

  private static final float bigRateShift    = .5f;
  private static final float littleRateShift = .05f;

  private static final int   bigStart = 10; //start a quick correction if we exceed the average delay by this amount
  private static final float littleStartShrink = 3f;
  private static final float littleStartStretch = 2f;

  private static final int maxGap = 1;
  private static final int maxBuffer = 25;

  private PacketLogger packetLogger;

  private int shiftMode = RATE_NORMAL;
  private float playRate = 1.0f;

  private long streamPlayheadPosition;
  private long lastGoodFrame;

  private CallLogger callAudioLogger;

  private int decodeBufferLength;
  private int outputFrameLength;

  private final short decodeBuffer[] = new short[1024];
  private final short rateBuffer[] = new short[2048];

  private StatisticsWatcher frameDelayStats = new StatisticsWatcher();
  private StatisticsWatcher samplesPerPacketStats = new StatisticsWatcher();
  private StatisticsWatcher frameSizeStats = new StatisticsWatcher();

  private AudioCodec codec;
  private TreeMap<Long, EncodedAudioData> audioFrames = new TreeMap<Long, EncodedAudioData>();
  private DesiredCallAudioDelayChooser delayChooser;

  private int gapLength;

  private int decodedCount;

  CallAudioProvider(AudioCodec _codec, PacketLogger packetLogger, CallLogger callLogger, CallMonitor monitor) {
    delayChooser = new DesiredCallAudioDelayChooser( packetLogger );
    codec = _codec;
    this.packetLogger = packetLogger;
    this.callAudioLogger = callLogger;
    frameDelayStats.setW(1 / 20.0f);
    frameSizeStats.setW(1 / 20f);
    samplesPerPacketStats.setW(1 / 20f);

    monitor.addSampledMetrics("cap-latency", frameDelayStats.getSampler());
    monitor.addSampledMetrics("cap-samples-per-packet", samplesPerPacketStats.getSampler());
    monitor.addSampledMetrics("cap-frame-size", frameSizeStats.getSampler());
  }

  private void pullAudio() {
    EncodedAudioData ead = null;
    EncodedAudioData eadAtHead = null;
    if( audioFrames.size() != 0 ) {
      //see if the next sample is the one we want
      ead       = audioFrames.get(audioFrames.firstKey());
      eadAtHead = audioFrames.get(streamPlayheadPosition);
    }

    if( ead != null && ead.sequenceNumber < streamPlayheadPosition - maxGap ) {
        //if we've played more than 'maxGap' past this first frame, reset the playhead to that frame
        long totalLate = streamPlayheadPosition - ead.sequenceNumber;
        delayChooser.notifyVeryLate( totalLate );
        streamPlayheadPosition = ead.sequenceNumber;
        packetLogger.logPacket(ead.sequenceNumber, PacketLogger.PLAYHEAD_JUMP_BACK );
        Log.d( "CAP", "Very Late Event" );
    }
    else if( (ead == null || ead.sequenceNumber < streamPlayheadPosition) && eadAtHead != null ) {
        //we've got the packet for the current playhead position, and it's not a big gap, so drop the late frames
        ead = eadAtHead;
        streamPlayheadPosition = ead.sequenceNumber;
        packetLogger.logPacket(ead.sequenceNumber, PacketLogger.PLAYHEAD_JUMP_FORWARD );
        //Log.d( "CAP", "Drop Frames to " + eadAtHead );
    }

    if( ead != null && ead.sequenceNumber == streamPlayheadPosition ) {
      decodeBufferLength = codec.decode( ead.data, decodeBuffer, ead.data.length );
      decodedCount++;
      packetLogger.logPacket( ead.sourceSequenceNumber, PacketLogger.PACKET_DECODED );
      if( gapLength < CallLogger.gapLengthCounts.length &&
        gapLength > 0 ) {
        CallLogger.gapLengthCounts[gapLength]++;
      }
      gapLength = 0;
      lastGoodFrame = ead.sequenceNumber;
      audioFrames.remove(ead.sequenceNumber);
      if( audioFrames.size() == 0 ) delayChooser.notifyJustInTime();
      return;
    }
    if( ead != null ) {
      //Log.d( "CAP", "PLC - PH: " + streamPlayheadPosition + " ead " + ead.sequenceNumber + " ead2 " + ead.sourceSequenceNumber );
      packetLogger.logPacket(streamPlayheadPosition, PacketLogger.FILLING_GAP);
    } else {
      //Log.d( "CAP", "PLCNULL" );
      packetLogger.logPacket(streamPlayheadPosition, PacketLogger.PLAY_BUFFER_EMPTY );
    }
    decodeBufferLength = codec.decode(null, decodeBuffer, 0 );
    if( gapLength % 2 == 0 ) streamPlayheadPosition--; //FIXME: Assumes 2 frames per packet here...
    delayChooser.notifyMissing();
    gapLength++;

    if( decodeBufferLength == 0 ) {
      Log.e( TAG, "zero length decode buffer returned" );
      decodeBufferLength = 160; //v.bad, just feed it _something_
    }
  }

  public void addFrame( EncodedAudioData ead ) {
    //Log.d( "CAP", "added: " + ead.sequenceNumber );
    audioFrames.put( ead.sequenceNumber, ead );
    delayChooser.notifyArrival(ead.sequenceNumber);
  }
  private void updatePlayRate() {
    long frameDelay = lastGoodFrame - streamPlayheadPosition;
    if( audioFrames.size() > 0 ) {
      frameDelay = audioFrames.lastKey() - streamPlayheadPosition;
    }
    frameDelayStats.observeValue((int)frameDelay);

    float desFrameDelay = delayChooser.getDesFrameDelay();

    if( frameDelay > desFrameDelay + bigStart &&
      shiftMode != RATE_BIG ) {
      playRate = 1-bigRateShift;
      shiftMode = RATE_BIG;
      Log.d( TAG,"Dumping Glut");
    }
    if( frameDelay <= desFrameDelay &&
      shiftMode == RATE_BIG ) {
      playRate = 1;
      shiftMode = RATE_NORMAL;
      frameDelayStats.setAvg(desFrameDelay);
      Log.d( TAG,"Normal Rate" );
    }

    if( frameDelayStats.getAvg() > desFrameDelay + littleStartShrink &&
      shiftMode == RATE_NORMAL ) {
      shiftMode = RATE_LITTLE;
      playRate = 1-littleRateShift;
      Log.d( TAG, "Small shrink" );
    }
    if( frameDelayStats.getAvg() < desFrameDelay &&
      shiftMode == RATE_LITTLE && playRate < 1 ) {
      playRate = 1;
      shiftMode = RATE_NORMAL;
      Log.d(TAG, "Small shrink complete" );
    }

    if( frameDelayStats.getAvg() < desFrameDelay - littleStartStretch &&
      shiftMode == RATE_NORMAL ) {
      shiftMode = RATE_LITTLE;
      playRate = 1+littleRateShift;
      Log.d(TAG, "Small stretch" );
    }
    if (frameDelayStats.getAvg() > desFrameDelay
        && shiftMode == RATE_LITTLE && playRate > 1) {
      shiftMode = RATE_NORMAL;
      playRate = 1;
      Log.d(TAG, "Small stretch complete" );
    }
  }

  private void discardStaleFrames() {
    //discard frames that happened before the last data-frame we played
    int sizeBeforeDiscard = audioFrames.size();
    audioFrames.headMap(lastGoodFrame).clear();
    while( audioFrames.size() > maxBuffer ) {
      audioFrames.remove( audioFrames.firstKey());
      streamPlayheadPosition = audioFrames.firstKey();
    }
    int sizeAfterDiscard = audioFrames.size();
    if( sizeAfterDiscard != sizeBeforeDiscard ) {
      Log.d( "CAP", "Discard: " + (sizeBeforeDiscard - sizeAfterDiscard ) );
    }
    delayChooser.notifyLate( sizeBeforeDiscard - sizeAfterDiscard );
  }

  private void setDebugInfo() {
    CallLogger.waitingFrames = audioFrames.size();
    CallLogger.streamPlayheadPosition = streamPlayheadPosition;
    CallLogger.avgDelay = frameDelayStats.getAvg();
    CallLogger.shiftMode = shiftMode;
    if( audioFrames.size() > 0 )
      CallLogger.largestHeldFrame = audioFrames.lastKey();
  }

  public short[] getFrame() {
    discardStaleFrames();
    setDebugInfo();

    pullAudio();
    samplesPerPacketStats.observeValue(decodeBufferLength);

    updatePlayRate();
    //model prediction frame delay offset ... is this really a good idea - confirm that it improves our estimates
    frameDelayStats.setAvg(frameDelayStats.getAvg() + playRate-1 );//include our actions in the buffer model

    if( lastGoodFrame == streamPlayheadPosition ) {
      outputFrameLength = PacketLossConcealer.changeSpeed(rateBuffer, decodeBuffer, decodeBufferLength, playRate);
    } else {
      outputFrameLength = PacketLossConcealer.changeSpeed(rateBuffer, decodeBuffer, decodeBufferLength, 1 );
    }
    frameSizeStats.observeValue(outputFrameLength);
    streamPlayheadPosition++;
    packetLogger.logPacket(streamPlayheadPosition, PacketLogger.PLAYHEAD);

    delayChooser.updateDesired();
    callAudioLogger.update();
    if( decodedCount % 500 == 1 ) {
      Log.d( "CAP", "Decoded: " + decodedCount );
    }
    return rateBuffer;
  }

  public int getFrameSize() {
    return outputFrameLength;
  }

  public void terminate() {
    delayChooser.terminate();
  }
}
