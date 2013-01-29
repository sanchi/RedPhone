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
import org.thoughtcrime.redphone.signaling.AccountCreationException;
import org.thoughtcrime.redphone.signaling.AccountCreationSocket;
import org.thoughtcrime.redphone.signaling.DirectoryResponse;
import org.thoughtcrime.redphone.signaling.SignalingException;
import org.thoughtcrime.redphone.ui.AccountVerificationTimeoutException;
import org.thoughtcrime.redphone.ui.RegistrationProgressActivity;
import org.thoughtcrime.redphone.util.Util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegistrationService extends Service {

  public static final String NOTIFICATION_TITLE     = "org.thoughtcrime.redphone.NOTIFICATION_TITLE";
  public static final String NOTIFICATION_TEXT      = "org.thoughtcrime.redphone.NOTIFICATION_TEXT";
  public static final String REGISTER_NUMBER_ACTION = "org.thoughtcrime.redphone.RegistrationService.REGISTER_NUMBER";
  public static final String CHALLENGE_EVENT        = "org.thoughtcrime.redphone.CHALLENGE_EVENT";
  public static final String REGISTRATION_EVENT     = "org.thoughtcrime.redphone.REGISTRATION_EVENT";
  public static final String CHALLENGE_EXTRA        = "CAAChallenge";

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Binder          binder   = new RegistrationServiceBinder();

  private volatile int               registrationState = RegistrationProgressActivity.STATE_IDLE;
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

    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    executor.shutdown();

    if (receiver != null) {
      unregisterReceiver(receiver);
      receiver = null;
    }

    markAsVerifying(false);

    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  public synchronized int getSecondsRemaining() {
    long millisPassed;

    if (verificationStartTime == 0) millisPassed = 0;
    else                            millisPassed = System.currentTimeMillis() - verificationStartTime;

    return Math.max((int)(120000 - millisPassed) / 1000, 0);
  }

  public int getRegistrationState() {
    return registrationState;
  }

  private void initializeChallengeListener() {
    this.challenge      = null;
    receiver            = new ChallengeReceiver();
    IntentFilter filter = new IntentFilter(CHALLENGE_EVENT);
    registerReceiver(receiver, filter);
  }

  private void handleRegistrationIntent(Intent intent) {
    markAsVerifying(true);
    AccountCreationSocket socket = null;

    try {
      String number   = intent.getStringExtra("e164number");
      String password = Util.getSecret(18);
      String key      = Util.getSecret(40);

      initializeChallengeListener();

      setState(RegistrationProgressActivity.STATE_CONNECTING);
      socket = new AccountCreationSocket(this, number, password);
      socket.createAccount();

      setState(RegistrationProgressActivity.STATE_VERIFYING);
      String challenge = waitForChallenge();
      socket.verifyAccount(challenge, key);
      markAsVerified(number, password, key);

      GCMRegistrarHelper.registerClient(this, true);
      retrieveDirectory(socket);
      setState(RegistrationProgressActivity.STATE_COMPLETE);
      broadcastComplete(true);
      stopService(new Intent(this, RedPhoneService.class));
    } catch (SignalingException se) {
      Log.w("RegistrationService", se);
      setState(RegistrationProgressActivity.STATE_NETWORK_ERROR);
      broadcastComplete(false);
    } catch (AccountVerificationTimeoutException avte) {
      Log.w("RegistrationService", avte);
      setState(RegistrationProgressActivity.STATE_TIMEOUT);
      broadcastComplete(false);
    } catch (AccountCreationException ace) {
      Log.w("RegistrationService", ace);
      setState(RegistrationProgressActivity.STATE_NETWORK_ERROR);
      broadcastComplete(false);
    } finally {
      if (socket != null)
        socket.close();
    }
  }

  private synchronized String waitForChallenge() throws AccountVerificationTimeoutException {
    this.verificationStartTime = System.currentTimeMillis();

    if (this.challenge == null) {
      try {
        wait(120000);
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

    DirectoryUpdateReceiver.scheduleDirectoryUpdate(this);
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

  private void setState(int state) {
    this.registrationState = state;
    if (registrationStateHandler != null) {
      registrationStateHandler.obtainMessage(state).sendToTarget();
    }
  }

  private void broadcastComplete(boolean success) {
    Intent intent = new Intent();
    intent.setAction(REGISTRATION_EVENT);

    if (success) {
      intent.putExtra(NOTIFICATION_TITLE, R.string.RegistrationService_registration_complete);
      intent.putExtra(NOTIFICATION_TEXT, R.string.RegistrationService_redphone_registration_has_successfully_completed);
    } else {
      intent.putExtra(NOTIFICATION_TITLE, R.string.RegistrationService_registration_error);
      intent.putExtra(NOTIFICATION_TEXT, R.string.RegistrationService_redphone_registration_has_encountered_a_problem);
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
}
