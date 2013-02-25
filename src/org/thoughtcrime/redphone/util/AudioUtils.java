package org.thoughtcrime.redphone.util;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

/**
 * Utilities for manipulating device audio configuration
 */
public class AudioUtils {
  private static final String TAG = AudioUtils.class.getName();
  public static void enableDefaultRouting(Context context) {
    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    am.setSpeakerphoneOn(false);
    Log.d(TAG, "Set default audio routing");

  }

  public static void enableSpeakerphoneRouting(Context context) {
    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    am.setSpeakerphoneOn(true);
    Log.d(TAG, "Set speakerphone audio routing");
  }

  public static void resetConfiguration(Context context) {
    enableDefaultRouting(context);
  }
}
