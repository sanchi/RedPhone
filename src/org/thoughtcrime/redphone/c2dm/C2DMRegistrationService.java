/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.redphone.c2dm;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import org.thoughtcrime.redphone.ApplicationContext;
import org.thoughtcrime.redphone.Release;
import org.thoughtcrime.redphone.signaling.SignalingException;
import org.thoughtcrime.redphone.signaling.SignalingSocket;

/**
 * Service that handles the asynchronous C2DM registration process.
 *
 * @author Moxie Marlinspike
 *
 */

public class C2DMRegistrationService extends Service {

  public static final String C2DM_ACTION_COMPLETE           = "org.thoughtcrime.redphone.c2dm.DONE";
  public static final String REGISTRATION_COMPLETE_ACTION   = "org.thoughtcrime.redphone.c2dm.REGISTRATION_COMPLETE";
  public static final String UNREGISTRATION_COMPLETE_ACTION = "org.thoughtcrime.redphone.c2dm.UNREGISTRATION_COMPLETE";
  public static final String ERROR_ACTION                   = "org.thoughtcrime.redphone.c2dm.ERROR";
  public static final String REGISTER_ACTION                = "org.thoughtcrime.redphone.c2dm.REGISTER";
  public static final String UNREGISTER_ACTION              = "org.thoughtcrime.redphone.c2dm.UNREGISTER";

  @Override
  public void onCreate() {
    super.onCreate();
    initializeApplicationContext();
  }

  @Override
  public IBinder onBind(Intent arg0) {
    return null;
  }

  @Override
  public void onStart(Intent intent, int startId) {
    if (intent == null || intent.getAction() == null)
      return;

    String action = intent.getAction();

    Log.w("C2DMRegistrationService", "Handling C2DM service action: " + intent.getAction());

    if      (action.equals(REGISTRATION_COMPLETE_ACTION))   handleRegistrationComplete(intent);
    else if (action.equals(UNREGISTRATION_COMPLETE_ACTION)) handleUnregistrationComplete(intent);
    else if (action.equals(ERROR_ACTION))                   handleError(intent);
    else if (action.equals(REGISTER_ACTION))                handleRegistrationRequest(intent);
    else if (action.equals(UNREGISTER_ACTION))              handleUnregistrationRequest(intent);
  }

  private void initializeApplicationContext() {
    ApplicationContext context = ApplicationContext.getInstance();
    context.setContext(this);
  }

  private void handleError(Intent intent) {
    sendCompletionBroadcast(ERROR_ACTION, false);
//		ApplicationPreferencesActivity.setC2dm(this, false);
//		sendBroadcast(new Intent(C2DM_ACTION_COMPLETE));
//		Toast.makeText(this, "Error communicating with Google's C2DM service!", Toast.LENGTH_LONG);
  }

  private void handleUnregistrationRequest(Intent intent) {
    new Thread(new ServerUnregistrationAgent()).start();
  }

  private void handleRegistrationRequest(Intent intent) {
    Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
    registrationIntent.putExtra("app", PendingIntent.getBroadcast(this, 0, new Intent(), 0));
    registrationIntent.putExtra("sender", "info@whispersystems.org");
    startService(registrationIntent);
  }

  private void handleRegistrationComplete(Intent intent) {
    String registration = intent.getStringExtra("registration_id");
    new Thread(new ServerRegistrationAgent(registration)).start();
  }

  private void handleUnregistrationComplete(Intent intent) {
    sendCompletionBroadcast(UNREGISTER_ACTION, true);
//		sendBroadcast(new Intent(C2DM_ACTION_COMPLETE));
  }

  private void sendCompletionBroadcast(String type, boolean success) {
    Intent intent = new Intent(C2DM_ACTION_COMPLETE);
    intent.putExtra("type", type);
    intent.putExtra("success", success);
    sendBroadcast(intent);
  }

  private class ServerUnregistrationAgent implements Runnable {
    public void run() {
      try {
        Context context                 = C2DMRegistrationService.this;
        SharedPreferences preferences   = PreferenceManager.getDefaultSharedPreferences(context);
        String number                   = preferences.getString("Number", "NO_SAVED_NUMBER!");
        String password                 = preferences.getString("Password", "NO_SAVED_PASSWORD!");
        SignalingSocket signalingSocket = new SignalingSocket(C2DMRegistrationService.this,
                                                              Release.MASTER_SERVER_HOST,
                                                              Release.SERVER_PORT, number, password,
                                                              null);

        Log.w("C2DMRegistrationService", "Making call to unregister on whisperswitch...");
        signalingSocket.unregisterC2dm();
        Log.w("C2DMRegistrationService", "unregister success");

        Log.w("C2DMRegistrationService", "Firing C2DM unregister intent...");
        Intent unregIntent = new Intent("com.google.android.c2dm.intent.UNREGISTER");
        unregIntent.putExtra("app", PendingIntent.getBroadcast(C2DMRegistrationService.this,
                                                               0, new Intent(), 0));
        startService(unregIntent);
      } catch (SignalingException e) {
        Log.w("C2DMRegistrationService", e);
        sendCompletionBroadcast(UNREGISTER_ACTION, false);
      }
    }
  }

  private class ServerRegistrationAgent implements Runnable {
    private final String registrationId;

    public ServerRegistrationAgent(String registrationId) {
      this.registrationId = registrationId;
    }

    public void run() {
      try {
        Context context                 = C2DMRegistrationService.this;
        SharedPreferences preferences   = PreferenceManager.getDefaultSharedPreferences(context);
        String number                   = preferences.getString("Number", "NO_SAVED_NUMBER!");
        String password                 = preferences.getString("Password", "NO_SAVED_PASSWORD!");
        SignalingSocket signalingSocket = new SignalingSocket(C2DMRegistrationService.this,
                                                              Release.MASTER_SERVER_HOST,
                                                              Release.SERVER_PORT, number, password,
                                                              null);

        Log.w("C2DMRegistrationService", "Making call to whisperswitch registration...");
        signalingSocket.registerC2dm(registrationId);
        Log.w("C2DMRegistrationService", "Success...");
        sendCompletionBroadcast(REGISTER_ACTION, true);
      } catch (SignalingException e) {
        Log.w("C2DMRegistrationService", e);
        sendCompletionBroadcast(REGISTER_ACTION, false);
        return;
      }
    }
  }
}
