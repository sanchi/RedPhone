package org.thoughtcrime.redphone.call;

import android.app.KeyguardManager;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Maintains wake lock state.
 *
 * @author Stuart O. Anderson
 */
public class LockManager {
  private final Context context;
  private final PowerManager.WakeLock fullLock;
  private final PowerManager.WakeLock partialLock;
  private final PowerManager.WakeLock proximityLock;
  private final KeyguardManager.KeyguardLock keyGuardLock;
  private final KeyguardManager km;
  private final WifiManager.WifiLock wifiLock;

  private final Method wakelockParameterizedRelease;
  private final AccelerometerListener accelerometerListener;

  private boolean keyguardDisabled;

  private static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
  private static final int WAIT_FOR_PROXIMITY_NEGATIVE = 1;

  private PhoneState phoneState = PhoneState.IDLE;
  private int orientation = AccelerometerListener.ORIENTATION_UNKNOWN;

  public enum PhoneState {
    IDLE,
    PROCESSING,  //used when the phone is active but before the user should be alerted.
    INTERACTIVE,
    IN_CALL,
  }

  private enum LockState {
    FULL,
    PARTIAL,
    SLEEP,
    PROXIMITY
  }

  public LockManager(Context context) {
    this.context = context.getApplicationContext();

    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    fullLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "RedPhone Full");
    partialLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RedPhone Partial");
    proximityLock = pm.newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "RedPhone Incall");

    wakelockParameterizedRelease = getWakelockParamterizedReleaseMethod();

    km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    keyGuardLock = km.newKeyguardLock("RedPhone KeyGuard");

    WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RedPhone Wifi");

    fullLock.setReferenceCounted(false);
    partialLock.setReferenceCounted(false);
    proximityLock.setReferenceCounted(false);//This appears to have no effect on JB
    wifiLock.setReferenceCounted(false);

    accelerometerListener = new AccelerometerListener(context, new AccelerometerListener.OrientationListener() {
      @Override
      public void orientationChanged(int newOrientation) {
        orientation = newOrientation;
        Log.d("LockManager", "Orentation Update: " + newOrientation);
        updateInCallLockState();
      }
    });
  }

  private void updateInCallLockState() {
    boolean horizontal = orientation == AccelerometerListener.ORIENTATION_HORIZONTAL;
    if (phoneState == PhoneState.IN_CALL && !horizontal) {
      setLockState(LockState.PROXIMITY);
    } else {
      setLockState(LockState.PARTIAL);
    }
  }

  private Method getWakelockParamterizedReleaseMethod() {
    try {
      return proximityLock.getClass().getDeclaredMethod("release", Integer.TYPE);
    } catch (NoSuchMethodException e) {
      Log.d("LockManager", "Parameterized WakeLock release not available on this device.");
    }
    return null;
  }

  public void updatePhoneState(PhoneState state) {
    phoneState = state;
    switch(state) {
      case IDLE:
        setLockState(LockState.SLEEP);
        accelerometerListener.enable(false);
        maybeEnableKeyguard();
        break;
      case PROCESSING:
        setLockState(LockState.PARTIAL);
        accelerometerListener.enable(false);
        maybeEnableKeyguard();
        break;
      case INTERACTIVE:
        setLockState(LockState.FULL);
        accelerometerListener.enable(false);
        disableKeyguard();
        break;
      case IN_CALL:
        accelerometerListener.enable(true);
        updateInCallLockState();
        disableKeyguard();
        break;
    }
  }

  private synchronized void setLockState(LockState newState) {
    switch(newState) {
      case FULL:
        fullLock.acquire();
        partialLock.acquire();
        wifiLock.acquire();
        releaseProximityLock();
        break;
      case PARTIAL:
        partialLock.acquire();
        wifiLock.acquire();
        fullLock.release();
        releaseProximityLock();
        break;
      case SLEEP:
        fullLock.release();
        partialLock.release();
        wifiLock.release();
        releaseProximityLock();
        break;
      case PROXIMITY:
        partialLock.acquire();
        if (!proximityLock.isHeld()) {
          proximityLock.acquire();
        }
        wifiLock.acquire();
        fullLock.release();
        break;
      default:
        throw new IllegalArgumentException("Unhandled Mode: " + newState);
    }
    Log.d("LockManager", "Entered Lock State: " + newState);
  }

  private void releaseProximityLock() {
    if (!proximityLock.isHeld()) {
      return;
    }
    boolean released = false;
    if (wakelockParameterizedRelease != null) {
      try {
        wakelockParameterizedRelease.invoke(proximityLock, new Integer(WAIT_FOR_PROXIMITY_NEGATIVE));
        released = true;
      } catch (IllegalAccessException e) {
        Log.d("LockManager", "Failed to invoke release method", e);
      } catch (InvocationTargetException e) {
        Log.d("LockManager", "Failed to invoke release method", e);
      }
    }

    if(!released) {
      proximityLock.release();
    }
    Log.d("LockManager", "Released proximity lock:" + proximityLock.isHeld());
  }

  private void disableKeyguard() {
    if(keyguardLocked()) {
      keyGuardLock.disableKeyguard();
      keyguardDisabled = true;
    }
  }

  private void maybeEnableKeyguard() {
    if (keyguardDisabled) {
      keyGuardLock.reenableKeyguard();
      keyguardDisabled = false;
    }
  }

  private boolean keyguardLocked() {
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      return km.isKeyguardLocked();
    }
    return true;
  }
}
