package org.thoughtcrime.redphone.gcm;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gcm.GCMRegistrar;

import org.thoughtcrime.redphone.signaling.SignalingException;
import org.thoughtcrime.redphone.signaling.SignalingSocket;
import org.thoughtcrime.redphone.util.Util;

public class GCMRegistrationService extends Service {

  public static final String GCM_SENDER_ID = "212271488844";

  public static final String REGISTER_GCM_ACTION      = "org.thoughtcrime.redphone.gcm.REGISTER_GCM_ACTION";
  public static final String REGISTER_SERVER_ACTION   = "org.thoughtcrime.redphone.gcm.REGISTER_SERVER_ACTION";
  public static final String UNREGISTER_SERVER_ACTION = "org.thoughtcrime.redphone.gcm.UNREGISTER_SERVER_ACTION";

  public static final String FORCE_SERVER_REGISTRATION_EXTRA = "force_server_registration";
  public static final String REGISTRATION_ID_EXTRA           = "registration_id";

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null || intent.getAction() == null)
      return START_STICKY;

    if      (intent.getAction().equals(REGISTER_GCM_ACTION))      handleRegisterGcm(intent);
    else if (intent.getAction().equals(REGISTER_SERVER_ACTION))   handleRegisterServer(intent);
    else if (intent.getAction().equals(UNREGISTER_SERVER_ACTION)) handleUnregisterServer(intent);

    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    GCMRegistrar.onDestroy(this);
  }

  private void handleRegisterServer(Intent intent) {
    String registrationId = intent.getStringExtra(REGISTRATION_ID_EXTRA);

    if (registrationId == null)
      return;

    handleServerRegistrationEvent(registrationId, true);
  }

  private void handleUnregisterServer(Intent intent) {
    String registrationId = intent.getStringExtra(REGISTRATION_ID_EXTRA);

    if (registrationId == null)
      return;

    handleServerRegistrationEvent(registrationId, false);
  }

  private void handleRegisterGcm(Intent intent) {
    if (GCMRegistrar.getRegistrationId(this).equals("")) {
      GCMRegistrar.register(this, GCM_SENDER_ID);
    } else if (!GCMRegistrar.isRegisteredOnServer(this) ||
               intent.getBooleanExtra(FORCE_SERVER_REGISTRATION_EXTRA, false))
    {
      intent.putExtra(REGISTRATION_ID_EXTRA, GCMRegistrar.getRegistrationId(this));
      handleRegisterServer(intent);
    }
  }

  private void handleServerRegistrationEvent(final String registrationId,
      final boolean isRegister)
  {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        for (int i=0;i<3;i++) {
          try {
            SignalingSocket signalingSocket = new SignalingSocket(GCMRegistrationService.this);

            if (isRegister) {
              Log.w("GCMRegistrationService", "Making call to whisperswitch registration...");
              signalingSocket.registerGcm(registrationId);
            } else {
              Log.w("GCMRegistrationService", "making call to whisperswitch unregistration...");
              signalingSocket.unregisterGcm(registrationId);
            }

            Log.w("GCMRegistrationService", "Success...");
            GCMRegistrar.setRegisteredOnServer(GCMRegistrationService.this, isRegister);
            return null;
          } catch (SignalingException se) {
            Log.w("GCMRegistrationService", "Registering with server failed:", se);
            Util.sleep(i * 5000);
          }
        }

        Log.w("GCMRegistrationService", "Failed to communicate GCM update server...");
        return null;
      }
    }.execute(null, null, null);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

}
