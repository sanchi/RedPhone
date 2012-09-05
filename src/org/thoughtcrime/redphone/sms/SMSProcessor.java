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

  private static final String INITIATE_PREFIX    = "RedPhone call:";
  private static final String GV_INITIATE_PREFIX = "^\\+[0-9]+ \\- " + INITIATE_PREFIX + ".+";

  private static final String VERIFY_PREFIX      = "A RedPhone is trying to verify you:";
  private static final String GV_VERIFY_PREFIX   = "^\\+[0-9]+ \\- " + VERIFY_PREFIX + ".+";

  public static String checkMessagesForVerification(String[] messages) {
    for (String message : messages) {
      String challenge = getVerificationChallenge(message);

      if (challenge != null) {
        return challenge;
      }
    }

    return null;
  }

  public static IncomingCallDetails checkMessagesForInitiate(Context context, String[] messages) {
    for (String message : messages) {
      IncomingCallDetails details = getIncomingCallDetails(context, message);

      if (details != null) {
        return details;
      }
    }

    return null;
  }

  private static String getVerificationChallenge(String message) {
    if (!message.startsWith(VERIFY_PREFIX) && !message.matches(GV_VERIFY_PREFIX)) {
      Log.w("SMSProcessor", "Not a verifier challenge...");
      return null;
    }

    return message.substring(message.lastIndexOf(":")+1).trim();
  }

  private static IncomingCallDetails getIncomingCallDetails(Context context, String message) {
    if (message.startsWith(INITIATE_PREFIX) || message.matches(GV_INITIATE_PREFIX)) {
      return extractDetailsFromMessage(context, message, message.lastIndexOf(":")+1);
    } else if (WirePrefix.isCall(message)) {
      return extractDetailsFromMessage(context, message, WirePrefix.PREFIX_SIZE);
    }

    Log.w( "SMSProcessor", "Not one of ours");
    return null;
  }

  private static IncomingCallDetails extractDetailsFromMessage(Context context,
                                                               String body,
                                                               int offset)
  {

    try {
      String signalString                           = body.substring(offset);
      EncryptedSignalMessage encryptedSignalMessage = new EncryptedSignalMessage(context,
                                                                                 signalString);
      CompressedInitiateSignal signal = CompressedInitiateSignal
                                        .parseFrom(encryptedSignalMessage.getPlaintext());

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
