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

package org.thoughtcrime.redphone.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.thoughtcrime.redphone.Constants;
import org.thoughtcrime.redphone.RedPhoneService;
import org.thoughtcrime.redphone.signaling.SessionDescriptor;
import org.thoughtcrime.redphone.ui.CreateAccountActivity;

/**
 * A broadcast receiver that gets notified for incoming SMS
 * messages, and checks to see whether they are "push" signals
 * for call initiation or account verification.
 *
 * @author Moxie Marlinspike
 *
 */

public class SMSListener extends BroadcastReceiver {

  public static final String SMS_RECEIVED_ACTION  = "android.provider.Telephony.SMS_RECEIVED";

  private void checkForIncomingCallSMS(Context context, Intent intent) {
    IncomingCallDetails call = SMSProcessor.checkMessagesForInitiate(context, intent.getExtras());
    Log.w("SMSListener", "Incoming call details: " + call);
    if (call == null) return;

    abortBroadcast();
    intent.setClass(context, RedPhoneService.class);
    intent.putExtra(Constants.REMOTE_NUMBER, call.getInitiator());
    intent.putExtra(Constants.SESSION, new SessionDescriptor(call.getHost(),
                                                             call.getPort(),
                                                             call.getSessionId()));
    context.startService(intent);
  }

  private void checkForVerificationSMS(Context context, Intent intent) {
    String challenge = SMSProcessor.checkMessagesForVerification(intent.getExtras());

    if (challenge == null) return;

    abortBroadcast();

    Intent challengeIntent = new Intent(CreateAccountActivity.CHALLENGE_EVENT);
    challengeIntent.putExtra(CreateAccountActivity.CHALLENGE_EXTRA, challenge);
    context.sendBroadcast(challengeIntent);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w("SMSListener", "Got SMS broadcast...");

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

    if( !SMS_RECEIVED_ACTION.equals( intent.getAction() ) ) {
      Log.w( "RedPhone", "Unexpected action in SMSListener: " + intent.getAction() );
      return;
    }

    if (preferences.getBoolean("REGISTERED", false)) {
      checkForIncomingCallSMS(context, intent);
    }

    checkForVerificationSMS(context, intent);
  }

}