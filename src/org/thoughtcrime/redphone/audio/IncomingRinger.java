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

import java.io.IOException;

import org.thoughtcrime.redphone.R;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;
import android.util.Log;

/**
 * Plays the 'incoming call' ringtone and manages the audio player state associated with this
 * process.
 *
 * @author Stuart O. Anderson
 */
public class IncomingRinger {
  private static final String TAG = IncomingRinger.class.getName();
  private static final long[] VIBRATE_PATTERN = {0, 1000, 1000};
  private final Context context;
  private final Vibrator vibrator;
  private final MediaPlayer player;

  public IncomingRinger(Context context) {
    this.context  = context;
    vibrator = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
    player = createPlayer(context);
  }

  private MediaPlayer createPlayer(Context context) {
    MediaPlayer newPlayer = new MediaPlayer();
    try {
      Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
      newPlayer.setDataSource(context, ringtoneUri);
      newPlayer.setLooping(true);
      newPlayer.setAudioStreamType(AudioManager.STREAM_RING);
      return newPlayer;
    } catch (IOException e) {
      Log.e(TAG, "Failed to create player for incoming call ringer");
      return null;
    }
  }

  public void start() {
    AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);

    int ringerMode            = audioManager.getRingerMode();
    int vibrateSetting        = audioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);

    //TODO request audio gain here
    //audioManager).requestAudioFocus( )

    if (ringerMode == AudioManager.RINGER_MODE_VIBRATE
        || (ringerMode == AudioManager.RINGER_MODE_NORMAL && vibrateSetting == AudioManager.VIBRATE_SETTING_ON)
        || (player == null)) {
      Log.i(TAG, "Starting vibration");
      vibrator.vibrate(VIBRATE_PATTERN, 1);
    }

    if (player != null && ringerMode == AudioManager.RINGER_MODE_NORMAL ) {
      audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
      audioManager.setMode(AudioManager.MODE_RINGTONE);
      try {
        if(!player.isPlaying()) {
          player.prepare();
          player.start();
          Log.d(TAG, "Playing ringtone now...");
        } else {
          Log.d(TAG, "Ringtone is already playing, declining to restart.");
        }
      } catch (IllegalStateException e) {
        Log.w(TAG, e);
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    } else {
      Log.d(TAG, " mode: " + ringerMode);
    }
  }

  public void stop() {
    if (player != null) {
      Log.d(TAG, "Stopping ringer");
      player.stop();
    }
    Log.d(TAG, "Cancelling vibrator" );
    vibrator.cancel();
  }
}
