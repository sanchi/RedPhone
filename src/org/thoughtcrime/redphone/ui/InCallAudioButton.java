package org.thoughtcrime.redphone.ui;

import android.graphics.drawable.LayerDrawable;
import android.util.Log;
import android.widget.CompoundButton;
import org.thoughtcrime.redphone.R;
import org.thoughtcrime.redphone.Release;

/**
 * Manages the audio button displayed on the in-call screen
 *
 * The behavior of this button depends on the availability of headset audio, and changes from being a regular
 * toggle button (enabling speakerphone) to bringing up a model dialog that includes speakerphone, bluetooth,
 * and regular audio options.
 *
 * Based on com.android.phone.InCallTouchUI
 *
 * @author Stuart O. Anderson
 */
public class InCallAudioButton {
  private static final String TAG = InCallAudioButton.class.getName();
  private final CompoundButton mAudioButton;
  private boolean headsetAvailable;
  private AudioMode currentMode;

  public InCallAudioButton(CompoundButton audioButton) {
    mAudioButton = audioButton;
    currentMode = AudioMode.DEFAULT;
    headsetAvailable = false;
    updateView();
    setListener(new CallControls.AudioButtonListener() {
      @Override
      public void onAudioChange(AudioMode mode) {
        //No Action By Default.
      }
    });
  }


  public void setHeadsetAvailable(boolean available) {
    //TODO(Stuart Anderson): Bluetooth Support
    /*headsetAvailable = available;
    updateView();*/
  }

  public void setAudioMode(AudioMode newMode) {
    currentMode = newMode;
    updateView();
  }

  private void updateView() {
    if (Release.DEBUG) log("setAudioButton: " + currentMode);

    // The various layers of artwork for this button come from
    // btn_compound_audio.xml.  Keep track of which layers we want to be
    // visible:
    //
    // - This selector shows the blue bar below the button icon when
    //   this button is a toggle *and* it's currently "checked".
    boolean showToggleStateIndication = false;
    //
    // - This is visible if the popup menu is enabled:
    boolean showMoreIndicator = false;
    //
    // - Foreground icons for the button.  Exactly one of these is enabled:
    boolean showSpeakerOnIcon = false;
    boolean showSpeakerOffIcon = false;
    boolean showHandsetIcon = false;
    boolean showHeadsetIcon = false;

    boolean speakerOn = currentMode == AudioMode.SPEAKER;

    if (headsetAvailable) {
      if (Release.DEBUG) log("- updateAudioButton: 'popup menu action button' mode...");

      mAudioButton.setEnabled(true);

      // The audio button is NOT a toggle in this state.  (And its
      // setChecked() state is irrelevant since we completely hide the
      // btn_compound_background layer anyway.)

      // Update desired layers:
      showMoreIndicator = true;
      if (currentMode == AudioMode.HEADSET) {
        showHeadsetIcon = true;
      } else if (speakerOn) {
        showSpeakerOnIcon = true;
      } else {
        showHandsetIcon = true;
      }
    } else {
      if (Release.DEBUG) log("- updateAudioButton: 'speaker toggle' mode...");

      mAudioButton.setEnabled(true);

      mAudioButton.setChecked(speakerOn);
      showSpeakerOnIcon = speakerOn;
      showSpeakerOffIcon = !speakerOn;

      showToggleStateIndication = true;
    }

    final int HIDDEN = 0;
    final int VISIBLE = 255;

    LayerDrawable layers = (LayerDrawable) mAudioButton.getBackground();
    if (Release.DEBUG) log("- 'layers' drawable: " + layers);

    layers.findDrawableByLayerId(R.id.compoundBackgroundItem)
      .setAlpha(showToggleStateIndication ? VISIBLE : HIDDEN);

    layers.findDrawableByLayerId(R.id.moreIndicatorItem)
      .setAlpha(showMoreIndicator ? VISIBLE : HIDDEN);

    layers.findDrawableByLayerId(R.id.bluetoothItem)
      .setAlpha(showHeadsetIcon ? VISIBLE : HIDDEN);

    layers.findDrawableByLayerId(R.id.handsetItem)
      .setAlpha(showHandsetIcon ? VISIBLE : HIDDEN);

    layers.findDrawableByLayerId(R.id.speakerphoneOnItem)
      .setAlpha(showSpeakerOnIcon ? VISIBLE : HIDDEN);

    layers.findDrawableByLayerId(R.id.speakerphoneOffItem)
      .setAlpha(showSpeakerOffIcon ? VISIBLE : HIDDEN);
  }

  private void log(String msg) {
    Log.d(TAG, msg);
  }

  public static enum AudioMode {
    DEFAULT,
    HEADSET,
    SPEAKER,
  }

  public void setListener(final CallControls.AudioButtonListener listener) {
    mAudioButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        //TODO: Stuart O. Anderson - Add bluetooth support here.
        currentMode = b ? AudioMode.SPEAKER : AudioMode.DEFAULT;
        listener.onAudioChange(currentMode);
        updateView();
      }
    });
  }

}
