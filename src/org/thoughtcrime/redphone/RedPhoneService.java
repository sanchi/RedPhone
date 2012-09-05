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
import org.thoughtcrime.redphone.signaling.OtpCounterProvider;
import org.thoughtcrime.redphone.signaling.SessionDescriptor;
import org.thoughtcrime.redphone.signaling.SignalingException;
import org.thoughtcrime.redphone.signaling.SignalingSocket;
import org.thoughtcrime.redphone.ui.ApplicationPreferencesActivity;
import org.thoughtcrime.redphone.ui.DialerActivity;
import org.thoughtcrime.redphone.ui.StatusBarManager;
import org.thoughtcrime.redphone.util.Base64;
import org.thoughtcrime.redphone.util.CallLogger;

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

  public static final String ACTION_INCOMING_CALL = "org.thoughtcrime.redphone.RedPhoneService.INCOMING_CALL";
  public static final String ACTION_OUTGOING_CALL = "org.thoughtcrime.redphone.RedPhoneService.OUTGOING_CALL";
  public static final String ACTION_ANSWER_CALL   = "org.thoughtcrime.redphone.RedPhoneService.ANSWER_CALL";
  public static final String ACTION_DENY_CALL     = "org.thoughtcrime.redphone.RedPhoneService.DENYU_CALL";
  public static final String ACTION_HANGUP_CALL   = "org.thoughtcrime.redphone.RedPhoneService.HANGUP";

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
    if (intent == null) return;
    new Thread(new IntentRunnable(intent)).start();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  @Override
  public void onDestroy() {
    statusBarManager.setCallEnded();
  }

  private synchronized void onIntentReceived(Intent intent) {
    Log.w("RedPhoneService", "Received Intent: " + intent.getAction());

    if      (intent.getAction().equals(ACTION_INCOMING_CALL) && isBusy()) handleBusyCall(intent);
    else if (intent.getAction().equals(ACTION_INCOMING_CALL))             handleIncomingCall(intent);
    else if (intent.getAction().equals(ACTION_OUTGOING_CALL) && isIdle()) handleOutgoingCall(intent);
    else if (intent.getAction().equals(ACTION_ANSWER_CALL))               handleAnswerCall(intent);
    else if (intent.getAction().equals(ACTION_DENY_CALL))                 handleDenyCall(intent);
    else if (intent.getAction().equals(ACTION_HANGUP_CALL))               handleHangupCall(intent);
  }

  ///// Initializers

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

  /// Intent Handlers

  private void handleIncomingCall(Intent intent) {
    SessionDescriptor session = (SessionDescriptor)intent.getParcelableExtra(Constants.SESSION);
    remoteNumber              = extractRemoteNumber(intent);
    state                     = RedPhone.STATE_RINGING;

    fullWakeLock.acquire();

    this.currentCallManager = new ResponderCallManager(this, remoteNumber, localNumber,
                                                       password, session, zid);
    this.currentCallManager.start();
  }

  private void handleOutgoingCall(Intent intent) {
    remoteNumber = extractRemoteNumber(intent);

    sendMessage(RedPhone.HANDLE_OUTGOING_CALL, remoteNumber);

    state = RedPhone.STATE_DIALING;

    fullWakeLock.acquire();
    keyGuardLock.disableKeyguard();
    keyguardDisabled = true;

    this.currentCallManager = new InitiatingCallManager(this, localNumber, password,
                                                        remoteNumber, zid);
    this.currentCallManager.start();

    statusBarManager.setCallInProgress();

    CallLogger.logOutgoingCall(this, remoteNumber);
  }

  private void handleBusyCall(Intent intent) {
    SessionDescriptor session = (SessionDescriptor)intent.getParcelableExtra(Constants.SESSION);

    if (currentCallManager != null && session.equals(currentCallManager.getSessionDescriptor())) {
      Log.w("RedPhoneService", "Duplicate incoming call signal, ignoring...");
      return;
    }

    handleMissedCall(extractRemoteNumber(intent), System.currentTimeMillis());

    try {
      SignalingSocket signalingSocket = new SignalingSocket(this, session.getFullServerName(),
                                                            Release.SERVER_PORT,
                                                            localNumber, password,
                                                            OtpCounterProvider.getInstance());

      signalingSocket.setBusy(session.sessionId);
      signalingSocket.close();
    } catch (SignalingException e) {
      Log.w("RedPhoneService", e);
    }
  }

  private void handleMissedCall(String remoteNumber, long timestamp) {
    CallLogger.logMissedCall(this, remoteNumber, timestamp);

    Intent intent                           = new Intent(this, DialerActivity.class);
    NotificationManager notificationManager = (NotificationManager)this.getSystemService(NOTIFICATION_SERVICE);
    Notification notification               = new Notification(android.R.drawable.stat_notify_missed_call, "Missed RedPhone Call", timestamp);
    PendingIntent launchIntent              = PendingIntent.getActivity(this, 0, intent, 0);

    notification.setLatestEventInfo(this, "Missed RedPhone Call", "Missed RedPhone Call", launchIntent);
    notification.defaults |= Notification.DEFAULT_VIBRATE;
    notificationManager.notify(DialerActivity.MISSED_CALL, notification);
  }

  private void handleAnswerCall(Intent intent) {
    state = RedPhone.STATE_ANSWERING;
    incomingRinger.stop();
    ((ResponderCallManager)this.currentCallManager).answer(true);
  }

  private void handleDenyCall(Intent intent) {
    state = RedPhone.STATE_IDLE;
    incomingRinger.stop();
    ((ResponderCallManager)this.currentCallManager).answer(false);
    this.terminate();
  }

  private void handleHangupCall(Intent intent) {
    this.terminate();
  }

  /// Helper Methods

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

  private synchronized void terminate() {
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

  ///////// CallStateListener Implementation

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
    CallLogger.logIncomingCall(this, remoteNumber);
  }

  public void notifyBusy() {
    Log.w("RedPhoneService", "Got busy signal from responder!");
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

  private class IntentRunnable implements Runnable {
    private final Intent intent;

    public IntentRunnable(Intent intent) {
      this.intent = intent;
    }

    public void run() {
      onIntentReceived(intent);
    }
  }

  public class RedPhoneServiceBinder extends Binder {
    public RedPhoneService getService() {
      return RedPhoneService.this;
    }
  }
}
