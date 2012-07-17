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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import org.thoughtcrime.redphone.Constants;
import org.thoughtcrime.redphone.RedPhoneService;
import org.thoughtcrime.redphone.crypto.EncryptedSignalMessage;
import org.thoughtcrime.redphone.crypto.InvalidEncryptedSignalException;
import org.thoughtcrime.redphone.signaling.SessionDescriptor;
import org.thoughtcrime.redphone.signaling.signals.CompressedInitiateSignalProtocol.CompressedInitiateSignal;
import org.thoughtcrime.redphone.sms.IncomingCallDetails;

/**
 * This is the C2DM equivalent of the org.thoughtcrime.sms.SmsListener.
 *
 * It receives incoming C2DM events, processes the signal, and kicks off an incoming call
 * if everything verifies.
 *
 * @author Moxie Marlinspike
 *
 */

public class C2DMSignalListener extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    String data                     = intent.getStringExtra("signal");
    IncomingCallDetails callDetails = getIncomingCallDetails(context, data);

    if (callDetails != null) {
      intent.setClass(context, RedPhoneService.class);
      intent.putExtra(Constants.REMOTE_NUMBER, callDetails.getInitiator());
      intent.putExtra(Constants.SESSION, new SessionDescriptor(callDetails.getHost(),
                                                               callDetails.getPort(),
                                                               callDetails.getSessionId()));
      context.startService(intent);
    }
  }

  private IncomingCallDetails getIncomingCallDetails(Context context, String signalString) {
    try {
      Log.w("C2DMSignalListener", "Got C2DM Signal: " + signalString);
      EncryptedSignalMessage encryptedSignalMessage = new EncryptedSignalMessage(context,
                                                                                 signalString);
      CompressedInitiateSignal signal = CompressedInitiateSignal.parseFrom(encryptedSignalMessage
                                                                           .getPlaintext());

      return new IncomingCallDetails(signal.getInitiator(), signal.getPort(),
                                     signal.getSessionId(), signal.getServerName());
    } catch (InvalidEncryptedSignalException e) {
      Log.w("C2DMSignalListener", e);
      return null;
    } catch (InvalidProtocolBufferException e) {
      Log.w("C2DMSignalListener", e);
      return null;
    }
  }

}
