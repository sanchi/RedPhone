package org.thoughtcrime.redphone.registration;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import org.thoughtcrime.redphone.Constants;
import org.thoughtcrime.redphone.R;
import org.thoughtcrime.redphone.RedPhoneService;
import org.thoughtcrime.redphone.directory.DirectoryUpdateReceiver;
import org.thoughtcrime.redphone.directory.NumberFilter;
import org.thoughtcrime.redphone.gcm.GCMRegistrarHelper;
import org.thoughtcrime.redphone.monitor.MonitorConfigUpdateReceiver;
import org.thoughtcrime.redphone.signaling.AccountCreationException;
import org.thoughtcrime.redphone.signaling.AccountCreationSocket;
import org.thoughtcrime.redphone.signaling.DirectoryResponse;
import org.thoughtcrime.redphone.signaling.SignalingException;
import org.thoughtcrime.redphone.ui.AccountVerificationTimeoutException;
import org.thoughtcrime.redphone.util.PeriodicActionUtils;
import org.thoughtcrime.redphone.util.Util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The RegisterationService handles the actual process of registration.  If it receives an
 * intent with a REGISTER_NUMBER_ACTION, it does the following through an executor:
 *
 * 1) Generate secrets.
 * 2) Register the specified number and those secrets with the server.
 * 3) Wait for a challenge SMS.
 * 4) Verify the challenge with the server.
 * 5) Start the GCM registration process.
 * 6) Retrieve the current directory.
 *
 * The RegistrationService broadcasts its state throughout this process, and also makes its
 * state available through service binding.  This enables a View to display progress.
 *
 * @author Moxie Marlinspike
 *
 */

public class RegistrationService extends Service {

  public static final String NOTIFICATION_TITLE     = "org.thoughtcrime.redphone.NOTIFICATION_TITLE";
  public static final String NOTIFICATION_TEXT      = "org.thoughtcrime.redphone.NOTIFICATION_TEXT";
  public static final String REGISTER_NUMBER_ACTION = "org.thoughtcrime.redphone.RegistrationService.REGISTER_NUMBER";
  public static final String CHALLENGE_EVENT        = "org.thoughtcrime.redphone.CHALLENGE_EVENT";
  public static final String REGISTRATION_EVENT     = "org.thoughtcrime.redphone.REGISTRATION_EVENT";
  public static final String CHALLENGE_EXTRA        = "CAAChallenge";

  private static final long REGISTRATION_TIMEOUT_MILLIS = 120000;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Binder          binder   = new RegistrationServiceBinder();

  private volatile RegistrationState registrationState = new RegistrationState(RegistrationState.STATE_IDLE);

  private volatile Handler           registrationStateHandler;
  private volatile ChallengeReceiver receiver;
  private          String            challenge;
  private          long              verificationStartTime;

  @Override
  public int onStartCommand(final Intent intent, int flags, int startId) {
    if (intent != null && intent.getAction().equals(REGISTER_NUMBER_ACTION)) {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          handleRegistrationIntent(intent);
        }
      });
    }

    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    executor.shutdown();
    shutdown();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  public void shutdown() {
    shutdownChallengeListener();
    markAsVerifying(false);
    registrationState = new RegistrationState(RegistrationState.STATE_IDLE);
  }

  public synchronized int getSecondsRemaining() {
    long millisPassed;

    if (verificationStartTime == 0) millisPassed = 0;
    else                            millisPassed = System.currentTimeMillis() - verificationStartTime;

    return Math.max((int)(REGISTRATION_TIMEOUT_MILLIS - millisPassed) / 1000, 0);
  }

  public RegistrationState getRegistrationState() {
    return registrationState;
  }

  private void initializeChallengeListener() {
    this.challenge      = null;
    receiver            = new ChallengeReceiver();
    IntentFilter filter = new IntentFilter(CHALLENGE_EVENT);
    registerReceiver(receiver, filter);
  }

  private void shutdownChallengeListener() {
    if (receiver != null) {
      unregisterReceiver(receiver);
      receiver = null;
    }
  }

  private void handleRegistrationIntent(Intent intent) {
    markAsVerifying(true);

    AccountCreationSocket socket = null;
    String number                = intent.getStringExtra("e164number");

    try {
      String password = Util.getSecret(18);
      String key      = Util.getSecret(40);

      initializeChallengeListener();

      setState(new RegistrationState(RegistrationState.STATE_CONNECTING, number));
      socket = new AccountCreationSocket(this, number, password);
      socket.createAccount();
      socket.close();

      setState(new RegistrationState(RegistrationState.STATE_VERIFYING, number));
      String challenge = waitForChallenge();
      socket           = new AccountCreationSocket(this, number, password);
      socket.verifyAccount(challenge, key);
      socket.close();

      markAsVerified(number, password, key);

      GCMRegistrarHelper.registerClient(this, true);
      retrieveDirectory(socket);
      MonitorConfigUpdateReceiver.maybeUpdateConfig(this);
      setState(new RegistrationState(RegistrationState.STATE_COMPLETE, number));
      broadcastComplete(true);
      stopService(new Intent(this, RedPhoneService.class));
    } catch (SignalingException se) {
      Log.w("RegistrationService", se);
      setState(new RegistrationState(RegistrationState.STATE_NETWORK_ERROR, number));
      broadcastComplete(false);
    } catch (AccountVerificationTimeoutException avte) {
      Log.w("RegistrationService", avte);
      setState(new RegistrationState(RegistrationState.STATE_TIMEOUT, number));
      broadcastComplete(false);
    } catch (AccountCreationException ace) {
      Log.w("RegistrationService", ace);
      setState(new RegistrationState(RegistrationState.STATE_NETWORK_ERROR, number));
      broadcastComplete(false);
    } finally {
      if (socket != null)
        socket.close();

      shutdownChallengeListener();
    }
  }

  private synchronized String waitForChallenge() throws AccountVerificationTimeoutException {
    this.verificationStartTime = System.currentTimeMillis();

    if (this.challenge == null) {
      try {
        wait(REGISTRATION_TIMEOUT_MILLIS);
      } catch (InterruptedException e) {
        throw new IllegalArgumentException(e);
      }
    }

    if (this.challenge == null)
      throw new AccountVerificationTimeoutException();

    return this.challenge;
  }

  private synchronized void challengeReceived(String challenge) {
    this.challenge = challenge;
    notifyAll();
  }

  private void retrieveDirectory(AccountCreationSocket socket) {
    try {
      DirectoryResponse response = socket.getNumberFilter();

      if (response != null) {
        NumberFilter numberFilter = new NumberFilter(response.getFilter(), response.getHashCount());
        numberFilter.serializeToFile(this);
      }
    } catch (SignalingException se) {
      Log.w("RegistrationService", se);
    }

    PeriodicActionUtils.scheduleUpdate(this, DirectoryUpdateReceiver.class);
  }

  private void markAsVerifying(boolean verifying) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    Editor editor                 = preferences.edit();

    editor.putBoolean(Constants.VERIFYING_PREFERENCE, verifying);
    editor.commit();
  }

  private void markAsVerified(String number, String password, String key) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    Editor editor                 = preferences.edit();

    editor.putBoolean(Constants.VERIFYING_PREFERENCE, false);
    editor.putBoolean(Constants.REGISTERED_PREFERENCE, true);
    editor.putString(Constants.NUMBER_PREFERENCE, number);
    editor.putString(Constants.PASSWORD_PREFERENCE, password);
    editor.putString(Constants.KEY_PREFERENCE, key);
    editor.putLong(Constants.PASSWORD_COUNTER_PREFERENCE, 1L);
    editor.commit();
  }

  private void setState(RegistrationState state) {
    this.registrationState = state;

    if (registrationStateHandler != null) {
      registrationStateHandler.obtainMessage(state.state, state.number).sendToTarget();
    }
  }

  private void broadcastComplete(boolean success) {
    Intent intent = new Intent();
    intent.setAction(REGISTRATION_EVENT);

    if (success) {
      intent.putExtra(NOTIFICATION_TITLE, getString(R.string.RegistrationService_registration_complete));
      intent.putExtra(NOTIFICATION_TEXT, getString(R.string.RegistrationService_redphone_registration_has_successfully_completed));
    } else {
      intent.putExtra(NOTIFICATION_TITLE, getString(R.string.RegistrationService_registration_error));
      intent.putExtra(NOTIFICATION_TEXT, getString(R.string.RegistrationService_redphone_registration_has_encountered_a_problem));
    }

    this.sendOrderedBroadcast(intent, null);
  }

  public void setRegistrationStateHandler(Handler registrationStateHandler) {
    this.registrationStateHandler = registrationStateHandler;
  }

  public class RegistrationServiceBinder extends Binder {
    public RegistrationService getService() {
      return RegistrationService.this;
    }
  }

  private class ChallengeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w("RegistrationService", "Got a challenge broadcast...");
      challengeReceived(intent.getStringExtra(CHALLENGE_EXTRA));
    }
  }

  public static class RegistrationState {

    public static final int STATE_IDLE          = 0;
    public static final int STATE_CONNECTING    = 1;
    public static final int STATE_VERIFYING     = 2;
    public static final int STATE_TIMER         = 3;
    public static final int STATE_COMPLETE      = 4;
    public static final int STATE_TIMEOUT       = 5;
    public static final int STATE_NETWORK_ERROR = 6;

    public final int    state;
    public final String number;

    public RegistrationState(int state) {
      this(state, null);
    }

    public RegistrationState(int state, String number) {
      this.state  = state;
      this.number = number;
    }
  }
}
