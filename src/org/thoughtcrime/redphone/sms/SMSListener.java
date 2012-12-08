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
import android.telephony.SmsMessage;
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

  public static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";
  public static final String GV_RECEIVED_ACTION  = "com.google.android.apps.googlevoice.SMS_RECEIVED";

  private void checkForIncomingCallSMS(Context context, String[] messages) {
    IncomingCallDetails call = SMSProcessor.checkMessagesForInitiate(context, messages);

    if (call == null) return;

    if(isOrderedBroadcast()) {
      abortBroadcast();
    }

    Intent intent = new Intent(context, RedPhoneService.class);
    intent.setAction(RedPhoneService.ACTION_INCOMING_CALL);
    intent.putExtra(Constants.REMOTE_NUMBER, call.getInitiator());
    intent.putExtra(Constants.SESSION, new SessionDescriptor(call.getHost(),
                                                             call.getPort(),
                                                             call.getSessionId()));
    context.startService(intent);
  }

  private void checkForVerificationSMS(Context context, String[] messages) {
    String challenge = SMSProcessor.checkMessagesForVerification(messages);

    if (challenge == null) return;

    if(isOrderedBroadcast()) {
      abortBroadcast();
    }

    Intent challengeIntent = new Intent(CreateAccountActivity.CHALLENGE_EVENT);
    challengeIntent.putExtra(CreateAccountActivity.CHALLENGE_EXTRA, challenge);
    context.sendBroadcast(challengeIntent);
  }

  private String[] parseSmsMessages(Intent intent) {
    Object[] pdus     = (Object[])intent.getExtras().get("pdus");
    String[] messages = new String[pdus.length];

    for (int i=0;i<pdus.length;i++) {
      messages[i] = SmsMessage.createFromPdu((byte[])pdus[i]).getDisplayMessageBody();
    }

    return messages;
  }

  private String[] parseGvMessages(Intent intent) {
    String body = intent.getStringExtra("com.google.android.apps.googlevoice.TEXT");
    return new String[] {body};
  }


  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w("SMSListener", "Got broadcast...");

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    String[] messages             = null;

    if (intent.getAction().equals(SMS_RECEIVED_ACTION)) {
      Log.w("SMSListener", "Got SMS message...");
      messages = parseSmsMessages(intent);
    } else if (intent.getAction().equals(GV_RECEIVED_ACTION)) {
      Log.w("SMSListener", "Got GV message...");
      messages = parseGvMessages(intent);
    } else {
      Log.w("RedPhone", "Unexpected action in SMSListener: " + intent.getAction());
      return;
    }

    if (preferences.getBoolean(Constants.REGISTERED_PREFERENCE, false)) {
      checkForIncomingCallSMS(context, messages);
    }

    checkForVerificationSMS(context, messages);
  }

}
