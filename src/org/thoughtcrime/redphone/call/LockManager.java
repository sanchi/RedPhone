package org.thoughtcrime.redphone.call;

import android.app.KeyguardManager;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.nfc.Tag;
import android.os.PowerManager;
import android.util.Log;
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
  private final WifiManager.WifiLock wifiLock;

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
    fullLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "RedPhone Full");
    partialLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RedPhone Partial");

    KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    keyGuardLock = km.newKeyguardLock("RedPhone KeyGuard");

    WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RedPhone Wifi");

    fullLock.setReferenceCounted(false);
    partialLock.setReferenceCounted(false);
    wifiLock.setReferenceCounted(false);
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
        disableKeyguard();
        break;
    }
  }

  private void setLockState(LockState newState) {
    switch(newState) {
      case FULL:
        fullLock.acquire();
        partialLock.acquire();
        wifiLock.acquire();
        break;
      case PARTIAL:
        partialLock.acquire();
        wifiLock.acquire();
        fullLock.release();
        break;
      case SLEEP:
        fullLock.release();
        partialLock.release();
        wifiLock.release();
        break;
      default:
        throw new IllegalArgumentException("Unhandled Mode: " + newState);
    }
    Log.d("LockManager", "Entered Lock State: " + newState);
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
