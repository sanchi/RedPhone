package org.thoughtcrime.redphone.call;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;
import org.thoughtcrime.redphone.RedPhone;

/**
 * Maintains wake lock state.
 *
 * @author Stuart O. Anderson
 */
public class LockManager {
  private final Context context;
  private final PowerManager.WakeLock fullLock;
  private final PowerManager.WakeLock partialLock;
  private final KeyguardManager.KeyguardLock keyGuardLock;

  private boolean keyguardDisabled;

  public enum PhoneState {
    IDLE,
    PROCESSING,  //used when the phone is active but before the user should be alerted.
    INTERACTIVE,
    IN_CALL,
  }

  private enum LockState {
    FULL,
    PARTIAL,
    SLEEP
  }

  public LockManager(Context context) {
    this.context = context.getApplicationContext();

    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    fullLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "RedPhone");
    partialLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "RedPhone");

    KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    keyGuardLock = km.newKeyguardLock("RedPhone");
  }

  public void updatePhoneState(PhoneState state) {
    switch(state) {
      case IDLE:
        setLockState(LockState.SLEEP);
        maybeEnableKeyguard();
        break;
      case PROCESSING:
        setLockState(LockState.PARTIAL);
        maybeEnableKeyguard();
        break;
      case INTERACTIVE:
        setLockState(LockState.FULL);
        disableKeyguard();
        break;
      case IN_CALL:
        //TODO(Stuart Anderson): Use proximity wake mode during calls.
        setLockState(LockState.PARTIAL);
        maybeEnableKeyguard();
        break;
    }
  }

  private void setLockState(LockState newState) {
    switch(newState) {
      case FULL:
        fullLock.acquire();
        if(partialLock.isHeld()) {
          partialLock.release();
        }
        break;
      case PARTIAL:
        partialLock.acquire();
        if(fullLock.isHeld()) {
          fullLock.release();
        }
      case SLEEP:
      default:
        if(fullLock.isHeld()) {
          fullLock.release();
        }
        if(partialLock.isHeld()) {
          partialLock.release();
        }
    }
  }

  private void disableKeyguard() {
    keyGuardLock.disableKeyguard();
    keyguardDisabled = true;
  }

  private void maybeEnableKeyguard() {
    if (keyguardDisabled) {
      keyGuardLock.reenableKeyguard();
      keyguardDisabled = false;
    }
  }
}
