package org.thoughtcrime.redphone.ui;

import android.content.Context;
import android.graphics.drawable.LayerDrawable;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.PopupMenu;
import org.thoughtcrime.redphone.R;
import org.thoughtcrime.redphone.Release;
import org.thoughtcrime.redphone.util.AudioUtils;

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
public class InCallAudioButton implements PopupMenu.OnMenuItemClickListener, PopupMenu.OnDismissListener{
  private static final String TAG = InCallAudioButton.class.getName();
  private final CompoundButton mAudioButton;
  private boolean headsetAvailable;
  private AudioUtils.AudioMode currentMode;
  private PopupMenu popupMenu;
  private Context context;
  private CallControls.AudioButtonListener listener;

  public InCallAudioButton(CompoundButton audioButton) {
    mAudioButton = audioButton;

    currentMode = AudioUtils.AudioMode.DEFAULT;
    headsetAvailable = false;

    updateView();
    setListener(new CallControls.AudioButtonListener() {
      @Override
      public void onAudioChange(AudioUtils.AudioMode mode) {
        //No Action By Default.
      }
    });
    context = audioButton.getContext();
  }

  public void setHeadsetAvailable(boolean available) {
    headsetAvailable = available;
    updateView();
  }

  public void setAudioMode(AudioUtils.AudioMode newMode) {
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

    boolean speakerOn = currentMode == AudioUtils.AudioMode.SPEAKER;

    if (headsetAvailable) {
      if (Release.DEBUG) log("- updateAudioButton: 'popup menu action button' mode...");

      mAudioButton.setEnabled(true);

      // The audio button is NOT a toggle in this state.  (And its
      // setChecked() state is irrelevant since we completely hide the
      // btn_compound_background layer anyway.)

      // Update desired layers:
      showMoreIndicator = true;
      if (currentMode == AudioUtils.AudioMode.HEADSET) {
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

  public void setListener(final CallControls.AudioButtonListener listener) {
    this.listener = listener;
    mAudioButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if(headsetAvailable) {
          displayAudioChoiceDialog();
        } else {
          currentMode = b ? AudioUtils.AudioMode.SPEAKER : AudioUtils.AudioMode.DEFAULT;
          listener.onAudioChange(currentMode);
          updateView();
        }
      }
    });
  }

  private void displayAudioChoiceDialog() {
    popupMenu = new PopupMenu(context, mAudioButton);
    popupMenu.getMenuInflater().inflate(R.menu.incall_audio_choice, popupMenu.getMenu());
    popupMenu.setOnMenuItemClickListener(this);
    popupMenu.setOnDismissListener(this);
    popupMenu.show();
  }

  @Override
  public void onDismiss(PopupMenu menu) {
    if (popupMenu != null) {
      popupMenu.dismiss();
      popupMenu = null;
    }
  }

  @Override
  public boolean onMenuItemClick(android.view.MenuItem item) {
    switch (item.getItemId()) {
      case R.id.audio_mode_earpiece:
        currentMode = AudioUtils.AudioMode.DEFAULT;
        break;
      case R.id.audio_mode_speaker:
        currentMode = AudioUtils.AudioMode.SPEAKER;
        break;
      case R.id.audio_mode_bluetooth:
        currentMode = AudioUtils.AudioMode.HEADSET;
        break;
      default:
        Log.w(TAG, "Unknown item selected in audio popup menu: " + item.toString());
    }
    listener.onAudioChange(currentMode);
    updateView();
    return true;
  }
}
