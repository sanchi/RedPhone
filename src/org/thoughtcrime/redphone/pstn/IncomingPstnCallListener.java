package org.thoughtcrime.redphone.pstn;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.thoughtcrime.redphone.RedPhoneService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.nfc.Tag;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Listens for incoming PSTN calls and rejects them if a RedPhone call is already in progress.
 *
 * Unstable use of reflection employed to gain access to ITelephony.
 *
 * @author Stuart O. Anderson
 */
public class IncomingPstnCallListener extends BroadcastReceiver {
  private static final String TAG = IncomingPstnCallListener.class.getName();
  private final RedPhoneService service;

  public IncomingPstnCallListener(RedPhoneService service) {
    this.service = service;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if(!(intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) != null
        && intent.getStringExtra(TelephonyManager.EXTRA_STATE).equals(TelephonyManager.EXTRA_STATE_RINGING)
        && service.isInCall())) {
      return;
    }
    Log.d(TAG, "Attempting to deny incoming PSTN call.");
    TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
    try {
      Method getTelephony = tm.getClass().getDeclaredMethod("getITelephony");
      getTelephony.setAccessible(true);
      Object telephonyService = getTelephony.invoke(tm);
      Method endCall = telephonyService.getClass().getDeclaredMethod("endCall");
      endCall.invoke(telephonyService);
      Log.d(TAG, "Denied Incoming Call From: " + intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER));
    } catch (NoSuchMethodException e) {
      Log.d(TAG, "Unable to access ITelephony API", e);
    } catch (IllegalAccessException e) {
      Log.d(TAG, "Unable to access ITelephony API", e);
    } catch (InvocationTargetException e) {
      Log.d(TAG, "Unable to access ITelephony API", e);
    }
  }
}
