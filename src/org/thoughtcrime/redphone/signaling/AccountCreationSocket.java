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

package org.thoughtcrime.redphone.signaling;

import android.content.Context;

import org.thoughtcrime.redphone.Release;
import org.thoughtcrime.redphone.signaling.signals.CreateAccountSignal;
import org.thoughtcrime.redphone.signaling.signals.VerifyAccountSignal;

/**
 * A socket that can using the signaling protocol to create and verify
 * accounts.  Verification is done through an SMS send-back.
 *
 * @author Moxie Marlinspike
 *
 */

public class AccountCreationSocket extends SignalingSocket {

  public AccountCreationSocket(Context context, String localNumber, String password)
      throws SignalingException
  {
    super(context, Release.MASTER_SERVER_HOST, Release.SERVER_PORT, localNumber, password, null);
  }

  public void createAccount() throws SignalingException, AccountCreationException {
    sendSignal(new CreateAccountSignal(localNumber, password));
    SignalResponse response = readSignalResponse();

    switch (response.getStatusCode()) {
    case 200: return;
    default:  throw new AccountCreationException("Account creation failed: " +
                                                 response.getStatusCode());
    }
  }

  public void verifyAccount(String challenge, String key)
      throws SignalingException, AccountCreationException
  {
    sendSignal(new VerifyAccountSignal(localNumber, password, challenge, key));
    SignalResponse response = readSignalResponse();

    switch (response.getStatusCode()) {
    case 200: return;
    default: throw new AccountCreationException("Account verification failed: " +
                                                response.getStatusCode());
    }
  }

}
