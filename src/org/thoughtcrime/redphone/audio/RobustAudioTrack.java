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
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.SystemClock;
import android.util.Log;

import org.thoughtcrime.redphone.ApplicationContext;
import org.thoughtcrime.redphone.Release;
import org.thoughtcrime.redphone.codec.AudioCodec;
import org.thoughtcrime.redphone.ui.ApplicationPreferencesActivity;
import org.thoughtcrime.redphone.util.Util;

/**
 * A robust wrapper for {@link AudioTrack}
 *
 * RobustAudioTrack contains black magic for creating AudioTracks that play out the earpiece rather
 * than the main speaker on most devices.  It also detects locked-up audio tracks and restarts them
 * automatically.
 *
 * @author Stuart O. Anderson
 */
public class RobustAudioTrack  {
  private final int deadMsecThreshold = 1000;
  private final static int AUDIO_BUFFER_SIZE = 16000 + AudioTrack.getMinBufferSize(
      AudioCodec.SAMPLE_RATE, AudioFormat.CHANNEL_CONFIGURATION_MONO,
      AudioFormat.ENCODING_PCM_16BIT);
  private short[] silence = new short[2048];
  private int bufferedSamples;
  private long lastPlayheadUpdateTime;
  private int lastPlayheadPosition;
  private AudioTrack audioPlayer;
  private final static String TAG = "RobustAudioTrack";

  public RobustAudioTrack() {
    //play a tone on init, instead of silence
    if (Release.DEBUG) {
      for (int i = 0; i < silence.length; i++) {
        silence[i] = (short) (10000 * ((i / 20) % 2));
      }
    }

    Context context = ApplicationContext.getInstance().getContext();
    //initialize the audioTrack, based on preferred mode
    AudioManager audioManager = (AudioManager) context
        .getSystemService(Context.AUDIO_SERVICE);
    if (ApplicationPreferencesActivity.getAudioModeIncall(context)) {
      Log.d("ATM", "Initialized using IN CALL mode");
      audioManager.setMode(AudioManager.MODE_IN_CALL);
    } else {
      Log.d("ATM", "Initialized using NORMAL MODE");
      audioManager.setRouting(AudioManager.MODE_NORMAL,
      AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);
      audioManager.setMode(AudioManager.MODE_NORMAL);
    }
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,	(int) (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*0.9), 0);
    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,(int) (audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) * 0.9),0);
    audioManager.setMicrophoneMute(false);
    audioManager.setSpeakerphoneOn(false);

    audioPlayer = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
        AudioCodec.SAMPLE_RATE, AudioFormat.CHANNEL_CONFIGURATION_MONO,
        AudioFormat.ENCODING_PCM_16BIT, AUDIO_BUFFER_SIZE,
        AudioTrack.MODE_STREAM);
    waitForAudioTrackReady();
    //writeChunk(audioPlayer.silence, audioPlayer.silence.length);
  }

  private void waitForAudioTrackReady() {
    int waitCount = 0;
    while (audioPlayer.getState() != AudioTrack.STATE_INITIALIZED) {
      waitCount++;
      try {
        Thread.sleep(100);
        Log.d( TAG , "Waiting for AudioTrack to initialize...["+waitCount+"]" );
      } catch (InterruptedException e) {
      }
      if (waitCount > 50) {
        Util.dieWithError("AudioTrack did not initialize");
        throw new RuntimeException("AudioTrack did not initialize");
      }
    }
    Log.d("ATM", "track initialized, buffer size = " + AUDIO_BUFFER_SIZE);
  }
  //writes a chunk of audio to the system buffer
  public void writeChunk(short chunk[], int chunkLen ) {
    if( chunkLen == 0 ) return;
    long writeStart = SystemClock.uptimeMillis();
    int written = audioPlayer.write(chunk, 0, chunkLen);
    bufferedSamples += written;
    long writeStop = SystemClock.uptimeMillis();
    if (writeStop - writeStart > 10) {
      Log.e("EW", "Long write, len=" + chunk.length + " buf="
          + getBufferRemaining());
    }

    if (written != chunkLen) {
      Log.e("ATM", "===== ONLY WROTE " + written
          + " SAMPLES IN CHUNK [" + chunk.length + " " + chunkLen + "] ====");
    }
  }

  public int getBufferRemaining() {
    return bufferedSamples - audioPlayer.getPlaybackHeadPosition();
  }

  public void update() {
    if (audioPlayer.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
      Log.d("LatencyMinimizingAudioPlayer",
          "AudioTrack stopped: starting and inserting silence");
      audioPlayer.play();
      writeChunk(silence, silence.length);
    }

    checkForDeadTrack();
  }

  private void checkForDeadTrack() {
    int remainder = audioPlayer.getPlaybackHeadPosition();
    long now = SystemClock.uptimeMillis();
    if (remainder != lastPlayheadPosition) {
      lastPlayheadUpdateTime = now;
    }

    lastPlayheadPosition = remainder;
    if (now - lastPlayheadUpdateTime > deadMsecThreshold) {
      Log.e(TAG, "Dead AudioTrack Detected");
      writeChunk(silence, silence.length);
    }
  }


  public void terminate() {
    if (audioPlayer.getState() == AudioTrack.STATE_INITIALIZED) {
      audioPlayer.stop();
      audioPlayer.release();
    }

    //Set audio mode back to 'normal'
    AudioManager aM = (AudioManager) ApplicationContext.getInstance()
        .getContext().getSystemService(Context.AUDIO_SERVICE);
    aM.setMode(AudioManager.MODE_NORMAL);
  }
}