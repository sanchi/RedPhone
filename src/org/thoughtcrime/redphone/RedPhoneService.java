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

package org.thoughtcrime.redphone;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.CallLog.Calls;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.thoughtcrime.redphone.audio.IncomingRinger;
import org.thoughtcrime.redphone.audio.OutgoingRinger;
import org.thoughtcrime.redphone.call.CallManager;
import org.thoughtcrime.redphone.call.CallStateListener;
import org.thoughtcrime.redphone.call.InitiatingCallManager;
import org.thoughtcrime.redphone.call.ResponderCallManager;
import org.thoughtcrime.redphone.codec.CodecSetupException;
import org.thoughtcrime.redphone.contacts.PersonInfo;
import org.thoughtcrime.redphone.signaling.SessionDescriptor;
import org.thoughtcrime.redphone.ui.ApplicationPreferencesActivity;
import org.thoughtcrime.redphone.ui.DialerActivity;
import org.thoughtcrime.redphone.ui.StatusBarManager;
import org.thoughtcrime.redphone.util.Base64;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;

/**
 * The major entry point for all of the heavy lifting associated with
 * setting up, tearing down, or managing calls.  The service spins up
 * either from a broadcast listener that has detected an incoming call,
 * or from a UI element that wants to initiate an outgoing call.
 *
 * @author Moxie Marlinspike
 *
 */
public class RedPhoneService extends Service implements CallStateListener {
  private final List<Message> bufferedEvents = new LinkedList<Message>();
  private final IBinder binder               = new RedPhoneServiceBinder();

  private OutgoingRinger outgoingRinger;
  private IncomingRinger incomingRinger;

  private int state;
  private byte[] zid;
  private String localNumber;
  private String remoteNumber;
  private String password;
  private CallManager currentCallManager;
  private boolean keyguardDisabled;

  private PowerManager.WakeLock fullWakeLock;
  private PowerManager.WakeLock partialWakeLock;
  private KeyguardManager.KeyguardLock keyGuardLock;

  private StatusBarManager statusBarManager;
  private Handler handler;

  @Override
  public void onCreate() {
    super.onCreate();

    if (Release.DEBUG) Log.w("RedPhoneService", "Service onCreate() called...");

    initializeResources();
    initializeApplicationContext();
    initializeAudio();
  }

  @Override
  public void onStart(Intent intent, int startId) {
    if (Release.DEBUG) Log.w("RedPhoneService", "Service onStart() called...");

    SessionDescriptor session = (SessionDescriptor)intent.getParcelableExtra(Constants.SESSION);
    remoteNumber              = extractRemoteNumber(intent);

    if      (session != null && isBusy())      handleBusyCall(remoteNumber, session);
    else if (session != null)                  startIncomingCall(remoteNumber, session);
    else if (remoteNumber != null && isIdle()) startOutgoingCall(remoteNumber);
  }

  private boolean isBusy() {
    TelephonyManager telephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
    return ((currentCallManager != null && state != RedPhone.STATE_IDLE) ||
             telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE);

  }

  private boolean isIdle() {
    return state == RedPhone.STATE_IDLE;
  }

  public int getState() {
    return state;
  }

  public PersonInfo getRemotePersonInfo() {
    return PersonInfo.getInstance(this, remoteNumber);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  @Override
  public void onDestroy() {
    statusBarManager.setCallEnded();
  }

  private void initializeAudio() {
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);

    if (ApplicationPreferencesActivity.getAudioModeIncall(this))
      am.setMode(AudioManager.MODE_IN_CALL);
    else
      am.setMode(AudioManager.MODE_NORMAL);

    am.setSpeakerphoneOn(false);
    am.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                       am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0 );

    this.outgoingRinger = new OutgoingRinger(this);
    this.incomingRinger = new IncomingRinger(this);
  }

  private void shutdownAudio() {
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    am.setMode(AudioManager.MODE_NORMAL);
  }

  private void initializeApplicationContext() {
    ApplicationContext context = ApplicationContext.getInstance();
    context.setContext(this);
    context.setCallStateListener(this);
  }

  private void initializeResources() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

    this.state            = RedPhone.STATE_IDLE;
    this.zid              = getZID();
    this.localNumber      = preferences.getString("Number", "NO_SAVED_NUMBER!");
    this.password         = preferences.getString("Password", "NO_SAVED_PASSWORD!");

    PowerManager pm       = (PowerManager) getSystemService(Context.POWER_SERVICE);
    this.fullWakeLock     = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                                           PowerManager.ACQUIRE_CAUSES_WAKEUP, "RedPhone");
    this.partialWakeLock  = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "RedPhone");
    this.keyGuardLock     = ((KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE))
                              .newKeyguardLock("RedPhone");

    this.statusBarManager = new StatusBarManager(this);
  }

  private void handleBusyCall(String remoteNumber, SessionDescriptor session) {
    handleMissedCall(remoteNumber, System.currentTimeMillis());
    currentCallManager.sendBusySignal(remoteNumber, session.sessionId);
  }

  private void handleMissedCall(String remoteNumber, long timestamp) {
    logMissedCall(remoteNumber, timestamp);

    Intent intent                           = new Intent(this, DialerActivity.class);
    NotificationManager notificationManager = (NotificationManager)this.getSystemService(NOTIFICATION_SERVICE);
    Notification notification               = new Notification(android.R.drawable.stat_notify_missed_call, "Missed RedPhone Call", timestamp);
    PendingIntent launchIntent              = PendingIntent.getActivity(this, 0, intent, 0);

    notification.setLatestEventInfo(this, "Missed RedPhone Call", "Missed RedPhone Call", launchIntent);
    notification.defaults |= Notification.DEFAULT_VIBRATE;
    notificationManager.notify(DialerActivity.MISSED_CALL, notification);
  }

  private void startIncomingCall(String remoteNumber, SessionDescriptor session) {
    Log.w("RedPhoneService", "Service startIncomingCall() called...");
    state = RedPhone.STATE_RINGING;

    fullWakeLock.acquire();

    this.currentCallManager = new ResponderCallManager(this, remoteNumber, localNumber,
                                                       password, session, zid);
    this.currentCallManager.start();
  }

  private void startOutgoingCall(String remoteNumber) {
    Log.w("RedPhoneService", "Service startOutgoingCall() called...");
    sendMessage(RedPhone.HANDLE_OUTGOING_CALL, remoteNumber);

    state = RedPhone.STATE_DIALING;
    fullWakeLock.acquire();

    keyGuardLock.disableKeyguard();
    keyguardDisabled = true;

    this.currentCallManager = new InitiatingCallManager(this, localNumber, password,
                                                        remoteNumber, zid);
    this.currentCallManager.start();

    statusBarManager.setCallInProgress();
    logOutgoingCall(remoteNumber);
  }

  private ContentValues getCallLogContentValues(String number, long timestamp) {
    PersonInfo pi = PersonInfo.getInstance( this, number );
    ContentValues values = new ContentValues();
    values.put(Calls.DATE, System.currentTimeMillis());
    values.put(Calls.NUMBER, number);
    values.put(Calls.CACHED_NAME, pi.getName() );
    values.put(Calls.TYPE, pi.getType() );
    return values;
  }

  private ContentValues getCallLogContentValues(String number) {
    return getCallLogContentValues(number, System.currentTimeMillis());
  }

  private void logMissedCall(String number, long timestamp) {
    ContentValues values = getCallLogContentValues(number, timestamp);
    values.put(Calls.TYPE, Calls.MISSED_TYPE);
    getContentResolver().insert(Calls.CONTENT_URI, values);
  }

  private void logOutgoingCall(String number) {
    ContentValues values = getCallLogContentValues(number);
    values.put(Calls.TYPE, Calls.OUTGOING_TYPE);
    try{
      getContentResolver().insert(Calls.CONTENT_URI, values);
    } catch (IllegalArgumentException e ) {
      Log.w("RedPhoneService", "Failed call log insert", e );
    }
  }

  private void logIncomingCall(String number) {
    ContentValues values = getCallLogContentValues(number);
    values.put(Calls.TYPE, Calls.INCOMING_TYPE);
    getContentResolver().insert(Calls.CONTENT_URI, values);
  }

  private byte[] getZID() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

    if (preferences.contains("ZID")) {
      try {
        return Base64.decode(preferences.getString("ZID", null));
      } catch (IOException e) {
        return setZID();
      }
    } else {
      return setZID();
    }
  }

  private byte[] setZID() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

    try {
      byte[] zid        = new byte[12];
      SecureRandom.getInstance("SHA1PRNG").nextBytes(zid);
      String encodedZid = Base64.encodeBytes(zid);

      preferences.edit().putString("ZID", encodedZid).commit();

      return zid;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private String extractRemoteNumber(Intent i) {
    String number =  i.getStringExtra(Constants.REMOTE_NUMBER);

    if (number == null)
      number = i.getData().getSchemeSpecificPart();

    if (number.endsWith("*")) return number.substring(0, number.length()-1);
    else                      return number;
  }

  private void startCallCardActivity() {
    Intent activityIntent = new Intent();
    activityIntent.setClass(this, RedPhone.class);
    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(activityIntent);
  }

  public synchronized void terminate() {
    Log.w("RedPhoneService", "terminate() called");
    Log.w("RedPhoneService", "termination stack", new Exception() );
    statusBarManager.setCallEnded();

    incomingRinger.stop();
    outgoingRinger.stop();

    if (currentCallManager != null) {
      currentCallManager.terminate();
      currentCallManager = null;
    }

    if (keyguardDisabled) {
      keyGuardLock.reenableKeyguard();
      keyguardDisabled = false;
    }

    shutdownAudio();

    if (fullWakeLock.isHeld())    fullWakeLock.release();
    if (partialWakeLock.isHeld()) partialWakeLock.release();

    state = RedPhone.STATE_IDLE;

    // XXX moxie@thoughtcrime.org -- Do we still need to stop the Service?
//    Log.d("RedPhoneService", "STOP SELF" );
//    this.stopSelf();
  }

  public void setCallStateHandler(Handler handler) {
    this.handler = handler;

    if (handler != null) {
      for (Message message : bufferedEvents) {
        handler.sendMessage(message);
      }

      bufferedEvents.clear();
    }
  }

  public void notifyCallStale() {
    Log.w("RedPhoneService", "Got a stale call, probably an old SMS...");
    handleMissedCall(remoteNumber, System.currentTimeMillis());
    this.terminate();
  }

  public void notifyCallFresh() {
    Log.w("RedPhoneService", "Good call, time to ring and display call card...");
    sendMessage(RedPhone.HANDLE_INCOMING_CALL, remoteNumber);

    startCallCardActivity();
    incomingRinger.start();

    keyGuardLock.disableKeyguard();
    keyguardDisabled = true;

    statusBarManager.setCallInProgress();
    logIncomingCall(remoteNumber);
  }

  public void handleAnswerCall() {
    state = RedPhone.STATE_ANSWERING;
    incomingRinger.stop();
    ((ResponderCallManager)this.currentCallManager).answer(true);
  }

  public void handleDenyCall() {
    state = RedPhone.STATE_IDLE;
    incomingRinger.stop();
    ((ResponderCallManager)this.currentCallManager).answer(false);
    this.terminate();
  }

  public void notifyBusy() {
    sendMessage(RedPhone.HANDLE_CALL_BUSY, null);
    this.terminate();
  }

  public void notifyCallRinging() {
    outgoingRinger.playRing();
    sendMessage(RedPhone.HANDLE_CALL_RINGING, null);
  }

  public void notifyCallConnected(String sas) {
    outgoingRinger.playComplete();
    if (!partialWakeLock.isHeld()) partialWakeLock.acquire();
    if (fullWakeLock.isHeld())     fullWakeLock.release();
    state = RedPhone.STATE_CONNECTED;
    synchronized( this ) {
      sendMessage(RedPhone.HANDLE_CALL_CONNECTED, sas);
      try {
        wait();
      } catch (InterruptedException e) {
        throw new AssertionError( "Wait interrupted in RedPhoneService" );
      }
    }
  }
  public void notifyCallConnectionUIUpdateComplete() {
    synchronized( this ) {
      this.notify();
    }
  }
  public void notifyDebugInfo(String info) {
    sendMessage(RedPhone.HANDLE_DEBUG_INFO, info );
  }

  public void notifyConnectingtoInitiator() {
    sendMessage(RedPhone.HANDLE_CONNECTING_TO_INITIATOR, null);
  }

  public void notifyCallDisconnected() {
    sendMessage(RedPhone.HANDLE_CALL_DISCONNECTED, null);
    this.terminate();
  }

  public void notifyHandshakeFailed() {
    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_HANDSHAKE_FAILED, null);
    this.terminate();
  }

  public void notifyRecipientUnavailable() {
    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_RECIPIENT_UNAVAILABLE, null);
    this.terminate();
  }

  public void notifyPerformingHandshake() {
    outgoingRinger.playHandshake();
    sendMessage(RedPhone.HANDLE_PERFORMING_HANDSHAKE, null);
  }

  public void notifyServerFailure() {
    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_SERVER_FAILURE, null);
    this.terminate();
  }

  public void notifyClientFailure() {
    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_CLIENT_FAILURE, null);
    this.terminate();
  }

  public void notifyLoginFailed() {
    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_LOGIN_FAILED, null);
    this.terminate();
  }

  public void notifyNoSuchUser() {
    sendMessage(RedPhone.HANDLE_NO_SUCH_USER, remoteNumber);
    this.terminate();
  }

  public void notifyServerMessage(String message) {
    sendMessage(RedPhone.HANDLE_SERVER_MESSAGE, message);
    this.terminate();
  }

  public void notifyCodecInitFailed(CodecSetupException e) {
    sendMessage(RedPhone.HANDLE_CODEC_INIT_FAILED, e);
    this.terminate();
  }

  public void notifyClientError(String msg) {
    sendMessage(RedPhone.HANDLE_CLIENT_FAILURE,msg);
    this.terminate();
  }

  public void notifyCallConnecting() {
    outgoingRinger.playSonar();
  }

  public void notifyWaitingForResponder() {}

  private void sendMessage(int code, Object extra) {
    Message message = Message.obtain();
    message.what    = code;
    message.obj     = extra;

    if (handler != null) handler.sendMessage(message);
    else    			       bufferedEvents.add(message);
  }

  public class RedPhoneServiceBinder extends Binder {
    public RedPhoneService getService() {
      return RedPhoneService.this;
    }
  }
}
