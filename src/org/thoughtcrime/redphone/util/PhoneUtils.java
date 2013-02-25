package org.thoughtcrime.redphone.util;

import android.content.Context;
import android.media.AudioManager;

/**
 * Utilities related to device audio and call state.
 *
 * @author Stuart O. Anderson
 */
public class PhoneUtils {

  public static boolean isSpeakerOn(Context context) {
    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    return audioManager.isSpeakerphoneOn();
  }

  /**
   * @return true if the Bluetooth on/off switch in the UI should be
   *         available to the user (i.e. if the device is BT-capable
   *         and a headset is connected.)
   */
  public static boolean isBluetoothAvailable() {
    return false; //TODO: Stuart O. Anderson
  }

  public static boolean isBluetoothAudioConnectedOrPending() {
    return false; //TODO: Stuart O. Anderson
  }
}
