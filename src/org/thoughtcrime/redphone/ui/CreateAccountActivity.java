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

package org.thoughtcrime.redphone.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.redphone.ApplicationContext;
import org.thoughtcrime.redphone.R;
import org.thoughtcrime.redphone.RedPhoneService;
import org.thoughtcrime.redphone.Release;
import org.thoughtcrime.redphone.directory.DirectoryUpdateReceiver;
import org.thoughtcrime.redphone.directory.NumberFilter;
import org.thoughtcrime.redphone.signaling.AccountCreationException;
import org.thoughtcrime.redphone.signaling.AccountCreationSocket;
import org.thoughtcrime.redphone.signaling.DirectoryResponse;
import org.thoughtcrime.redphone.signaling.SignalingException;
import org.thoughtcrime.redphone.util.PhoneNumberFormatter;
import org.thoughtcrime.redphone.util.Util;

/**
 * The create account activity.  Kicks off an account creation event, then waits
 * the server to respond with a challenge via SMS, receives the challenge, and
 * verifies it with the server.
 *
 * @author Moxie Marlinspike
 *
 */

public class CreateAccountActivity extends SherlockActivity implements Runnable {

  private static final int SUCCESS         = 1;
  private static final int FAILURE         = 2;
  private static final int FETCHING_FILTER = 3;

  public static final String CHALLENGE_EVENT = "org.thoughtcrime.redphone.CHALLENGE_EVENT";
  public static final String CHALLENGE_EXTRA = "CAAChallenge";

  private TextView number;
  private Button createButton;

  private ChallengeReceiver receiver;
  private ProgressDialog progressDialog;
  private String challenge;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.account_creation);

    ActionBar actionBar = this.getSupportActionBar();
    actionBar.setTitle("Register with RedPhone");

    initializeResources();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfiguration) {
    super.onConfigurationChanged(newConfiguration);
  }

  @Override
  public void onDestroy() {
    if (receiver != null) {
      Log.w("CreateAccountActivity", "Unregistering receiver...");
      unregisterReceiver(receiver);
      receiver = null;
    }

    super.onDestroy();
  }

  private void initializeResources() {
    ApplicationContext.getInstance().setContext(this);

    String localNumber = ((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE))
                         .getLine1Number();

    if (localNumber != null && localNumber.length() > 0 && !localNumber.startsWith("+")) {
      if (localNumber.length() == 10) localNumber = "+1" + localNumber;
      else                            localNumber = "+"  + localNumber;
    }

    this.number       = (TextView)findViewById(R.id.number);
    this.createButton = (Button)findViewById(R.id.registerButton);

    this.number.setText(localNumber);
    this.createButton.setOnClickListener(new CreateButtonListener());
  }

  private void markAsVerified(String number, String password, String key) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    Editor editor                 = preferences.edit();

    editor.putBoolean("REGISTERED", true);
    editor.putString("Number", number);
    editor.putString("Password", password);
    editor.putString("Key", key);
    editor.putLong("PasswordCounter", 1L);
    editor.commit();

    this.stopService(new Intent(this, RedPhoneService.class));
  }

  private void createAccount(AccountCreationSocket socket)
      throws AccountCreationException, SignalingException
  {
    socket.createAccount();
  }

  private String verifyAccount(AccountCreationSocket socket, String number, String password)
      throws SignalingException, AccountCreationException
  {
    String key       = Util.getSecret(40);
    String challenge = waitForChallenge();
    socket.verifyAccount(challenge, key);

    return key;
  }

  private void retrieveDirectory(AccountCreationSocket socket) {
    try {
      DirectoryResponse response = socket.getNumberFilter();

      if (response != null) {
        NumberFilter numberFilter = new NumberFilter(response.getFilter(), response.getHashCount());
        numberFilter.serializeToFile(CreateAccountActivity.this);
      }
    } catch (SignalingException se) {
      Log.w("CreateAccountActivity", se);
    }

    DirectoryUpdateReceiver.scheduleDirectoryUpdate(this);
  }

  public void run() {
    AccountCreationSocket socket = null;

    try {
      String number   = this.number.getText().toString();
      String password = Util.getSecret(18);

      initiateChallengeListener();

      socket = new AccountCreationSocket(CreateAccountActivity.this, number, password);
      createAccount(socket);

      String key = verifyAccount(socket, number, password);
      markAsVerified(number, password, key);

      retrieveDirectory(socket);

      Message message = handler.obtainMessage(SUCCESS);
      handler.sendMessage(message);
    } catch (AccountCreationException ace) {
      Log.w("CreateAccountActivity", ace);
      Message message = handler.obtainMessage(FAILURE);
      message.obj     = ace.getMessage();
      handler.sendMessage(message);
    } catch (SignalingException e) {
      Log.w("CreateAccountActivity", e);
      Message message = handler.obtainMessage(FAILURE);
      message.obj     = "Server error.  Got internet connectivity?";
      handler.sendMessage(message);
    } finally {
      if (socket != null)
        socket.close();
    }
  }

  private synchronized void initiateChallengeListener() {
    this.challenge      = null;
    receiver            = new ChallengeReceiver();
    IntentFilter filter = new IntentFilter(CHALLENGE_EVENT);
    registerReceiver(receiver, filter);
  }

  private synchronized String waitForChallenge() throws AccountCreationException {
    if (this.challenge == null)
      try {
        wait(60000);
      } catch (InterruptedException e) {
        throw new IllegalArgumentException(e);
      }

    if (this.challenge == null)
      throw new AccountCreationException("Verification timed out. " +
                                         "Did you enter your number correctly?");

    return this.challenge;
  }

  private synchronized void setChallenge(String challenge) {
    Log.w("CreateAccountActivity", "Setting challenge: " + challenge);
    this.challenge = challenge;
    notifyAll();
  }

  private class ChallengeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w("CreateAccountActivity", "Got a challenge broadcast...");
      setChallenge(intent.getStringExtra(CHALLENGE_EXTRA));
    }
  };

  private Handler handler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
      case SUCCESS:
        progressDialog.dismiss();
        Toast.makeText(CreateAccountActivity.this, "Account created!", Toast.LENGTH_LONG).show();
        ApplicationPreferencesActivity.setC2dm(CreateAccountActivity.this, false);

        Intent intent = new Intent("org.thoughtcrime.redphone.ui.DialerActivity");
        intent.setClass(CreateAccountActivity.this, DialerActivity.class);
        startActivity(intent);
        finish();
        break;
      case FAILURE:
        progressDialog.dismiss();
        showAlertDialog("Error creating account", msg.obj+"");
        break;
      case FETCHING_FILTER:
        progressDialog.setTitle("Account Created");
        progressDialog.setMessage("Retrieving updates...");
        break;
      }
    }
  };

  private class CreateButtonListener implements View.OnClickListener {
    public void onClick(View v) {
      CreateAccountActivity self = CreateAccountActivity.this;
      CharSequence number        = self.number.getText();

      if (number == null || number.toString().equals("")) {
        Toast toast = Toast.makeText(CreateAccountActivity.this,
                                     "You must specify your phone number!",
                                     Toast.LENGTH_LONG);
        toast.show();
        return;
      }

      self.number.setText(number.toString().replaceAll("[^0-9+]", ""));

      if (!PhoneNumberFormatter.isValidNumber(self.number.getText().toString())) {
        showAlertDialog("Incorrect number format", "You must specify your number in " +
                                                   "international format, eg: +14151231234");
        return;
      }

      if ((!Release.INTERNATIONAL) &&
        (CreateAccountActivity.this.number.getText().length() != 12 ||
        !CreateAccountActivity.this.number.getText().toString().startsWith("+1")))
      {
        Toast toast = Toast.makeText(CreateAccountActivity.this,
                                     "Sorry, only USA numbers are supported in the Beta.",
                                     Toast.LENGTH_LONG);
        toast.show();
        return;
      }

      CreateAccountActivity.this.progressDialog = ProgressDialog.show(CreateAccountActivity.this,
                                                                      "Creating account",
                                                                      "This could take a moment...",
                                                                      true, false);
      new Thread(CreateAccountActivity.this).start();
    }
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
      MenuInflater inflater = this.getSupportMenuInflater();
      inflater.inflate(R.menu.about_menu, menu);
      return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
      case R.id.aboutItem:
          launchAboutActivity();
          return true;
      }
      return false;
  }

  private void showAlertDialog(String title, String message) {
    AlertDialog.Builder dialog = new AlertDialog.Builder(CreateAccountActivity.this);
    dialog.setTitle(title);
    dialog.setMessage(message);
    dialog.setIcon(android.R.drawable.ic_dialog_alert);
    dialog.setPositiveButton("Ok", null);
    dialog.show();
  }

  private void launchAboutActivity() {
    Intent aboutIntent = new Intent();
    aboutIntent.setClass(this, AboutActivity.class);
    this.startActivity(aboutIntent);
  }

}
