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

import android.content.Context;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import org.thoughtcrime.redphone.crypto.EncryptedSignalMessage;
import org.thoughtcrime.redphone.crypto.InvalidEncryptedSignalException;
import org.thoughtcrime.redphone.signaling.signals.CompressedInitiateSignalProtocol.CompressedInitiateSignal;

/**
 * Parses SMS messages to determine whether they could be RedPhone related (initiate or verify).
 *
 * @author Moxie Marlinspike
 *
 */

public class SMSProcessor {

  private static final String INITIATE_PREFIX     = "RedPhone call:";
  private static final String VERIFY_PREFIX       = "A RedPhone is trying to verify you:";
  private static final String GOOGLE_VOICE_PREFIX = " - ";

  public static String checkMessagesForVerification(Bundle bundle) {
    SmsMessage[] messages = parseMessages(bundle);

    for (SmsMessage message : messages) {
      String challenge = getVerificationChallenge(message);

      if (challenge != null) {
        return challenge;
      }
    }

    return null;
  }

  public static IncomingCallDetails checkMessagesForInitiate(Context context, Bundle bundle) {
    SmsMessage[] messages = parseMessages(bundle);

    for (SmsMessage message : messages) {
      IncomingCallDetails details = getIncomingCallDetails(context, message);

      if (details != null) {
        return details;
      }
    }

    return null;
  }

  private static String getVerificationChallenge(SmsMessage message) {
    String body = message.getDisplayMessageBody();

    if (!body.startsWith(VERIFY_PREFIX) &&
        !body.contains(GOOGLE_VOICE_PREFIX + VERIFY_PREFIX))
    {
      Log.w("SMSProcessor", "Not a verifier challenge...");
      return null;
    }

    return body.substring(body.lastIndexOf(":")+1).trim();
  }

  private static IncomingCallDetails getIncomingCallDetails(Context context, SmsMessage message) {
    String body = message.getDisplayMessageBody();

    if (body.startsWith(INITIATE_PREFIX)) {
      return extractDetailsFromMessage(context, body, INITIATE_PREFIX.length());
    } else if (WirePrefix.isCall(body)) {
      return extractDetailsFromMessage(context, body, WirePrefix.PREFIX_SIZE);
    }

    if (body.indexOf(GOOGLE_VOICE_PREFIX) != -1) {
      String googleVoiceInitiatePrefix = GOOGLE_VOICE_PREFIX + INITIATE_PREFIX;
      int googleVoiceInitiateIndex     = body.lastIndexOf(googleVoiceInitiatePrefix);

      if (googleVoiceInitiateIndex != -1) {
        return extractDetailsFromMessage(context, body,
                                         googleVoiceInitiatePrefix.length() +
                                         googleVoiceInitiateIndex);
      } else if (WirePrefix.isCall(body, body.lastIndexOf(GOOGLE_VOICE_PREFIX) +
                                   GOOGLE_VOICE_PREFIX.length())) {
        return extractDetailsFromMessage(context, body, body.lastIndexOf(GOOGLE_VOICE_PREFIX) +
                                         GOOGLE_VOICE_PREFIX.length() + WirePrefix.PREFIX_SIZE);
      }
    }

    Log.w( "SMSProcessor", "Not one of ours");
    return null;
  }

  private static SmsMessage[] parseMessages(Bundle bundle) {
    Object[] pdus         = (Object[])bundle.get("pdus");
    SmsMessage[] messages = new SmsMessage[pdus.length];

    for (int i=0;i<pdus.length;i++)
      messages[i] = SmsMessage.createFromPdu((byte[])pdus[i]);

    return messages;
  }

  private static IncomingCallDetails extractDetailsFromMessage(Context context,
                                                               String body,
                                                               int offset)
  {
    try {
      String signalString                           = body.substring(offset);
      EncryptedSignalMessage encryptedSignalMessage = new EncryptedSignalMessage(context,
                                                                                 signalString);
      CompressedInitiateSignal signal               = CompressedInitiateSignal
                                                      .parseFrom(encryptedSignalMessage
                                                                 .getPlaintext());

      return new IncomingCallDetails(signal.getInitiator(), signal.getPort(),
                                     signal.getSessionId(), signal.getServerName());

    } catch (InvalidEncryptedSignalException e) {
      Log.w("SMSProcessor", e);
      return null;
    } catch (InvalidProtocolBufferException e) {
      Log.w("SMSProcessor", e);
      return null;
    }
  }
}
