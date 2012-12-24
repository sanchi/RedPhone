package org.thoughtcrime.redphone.gcm;

import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gcm.GCMRegistrar;

import org.thoughtcrime.redphone.Constants;

public class GCMRegistrarHelper {

  public static void registerClient(Context context, boolean forceServerUpdate) {
    if (!PreferenceManager.getDefaultSharedPreferences(context)
                          .getBoolean(Constants.REGISTERED_PREFERENCE, false))
    {
      return;
    }

    try {
      GCMRegistrar.checkDevice(context);
//      GCMRegistrar.checkManifest(context);

      Intent intent = new Intent(GCMRegistrationService.REGISTER_GCM_ACTION, null,
                                 context, GCMRegistrationService.class);
      intent.putExtra(GCMRegistrationService.FORCE_SERVER_REGISTRATION_EXTRA, forceServerUpdate);
      context.startService(intent);
    } catch (UnsupportedOperationException uoe) {
      Log.w("GCMRegistrarHelper", "GCM Not supported: " + uoe);
    }
  }

  public static void setRegistrationIdOnServer(Context context, String registrationId) {
    Intent intent = new Intent(GCMRegistrationService.REGISTER_SERVER_ACTION, null,
                               context, GCMRegistrationService.class);
    intent.putExtra(GCMRegistrationService.REGISTRATION_ID_EXTRA, registrationId);
    context.startService(intent);
  }

  public static void unsetRegistrationIdOnServer(Context context, String registrationId) {
    Intent intent = new Intent(GCMRegistrationService.UNREGISTER_SERVER_ACTION, null,
                               context, GCMRegistrationService.class);
    intent.putExtra(GCMRegistrationService.REGISTRATION_ID_EXTRA, registrationId);
    context.startService(intent);
  }
}
