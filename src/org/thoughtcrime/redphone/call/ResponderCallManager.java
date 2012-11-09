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

package org.thoughtcrime.redphone.call;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.redphone.Release;
import org.thoughtcrime.redphone.crypto.SecureRtpSocket;
import org.thoughtcrime.redphone.crypto.zrtp.MasterSecret;
import org.thoughtcrime.redphone.crypto.zrtp.ZRTPResponderSocket;
import org.thoughtcrime.redphone.network.RtpSocket;
import org.thoughtcrime.redphone.signaling.LoginFailedException;
import org.thoughtcrime.redphone.signaling.NetworkConnector;
import org.thoughtcrime.redphone.signaling.OtpCounterProvider;
import org.thoughtcrime.redphone.signaling.SessionDescriptor;
import org.thoughtcrime.redphone.signaling.SessionInitiationFailureException;
import org.thoughtcrime.redphone.signaling.SessionStaleException;
import org.thoughtcrime.redphone.signaling.SignalingException;
import org.thoughtcrime.redphone.signaling.SignalingSocket;

import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * CallManager responsible for coordinating incoming calls.
 *
 * @author Moxie Marlinspike
 *
 */
public class ResponderCallManager extends CallManager {

  private final String localNumber;
  private final String password;
  private final byte[] zid;

  private int answer = 0;

  public ResponderCallManager(Context context, CallStateListener callStateListener,
                              String remoteNumber, String localNumber,
                              String password, SessionDescriptor sessionDescriptor,
                              byte[] zid)
  {
    super(context, callStateListener, remoteNumber, "ResponderCallManager Thread");
    this.localNumber       = localNumber;
    this.password          = password;
    this.sessionDescriptor = sessionDescriptor;
    this.zid               = zid;
  }

  @Override
  public void run() {
    try {
      signalingSocket = new SignalingSocket(context,
                                            sessionDescriptor.getFullServerName(),
                                            Release.SERVER_PORT,
                                            localNumber, password,
                                            OtpCounterProvider.getInstance());

      signalingSocket.setRinging(sessionDescriptor.sessionId);
      callStateListener.notifyCallFresh();

      processSignals();

      if (!waitForAnswer()) {
        return;
      }

      int localPort = new NetworkConnector(sessionDescriptor.sessionId,
                                           sessionDescriptor.getFullServerName(),
                                           sessionDescriptor.relayPort).makeConnection();

      InetSocketAddress remoteAddress = new InetSocketAddress(sessionDescriptor.getFullServerName(),
                                                              sessionDescriptor.relayPort);

      secureSocket  = new SecureRtpSocket(new RtpSocket(localPort, remoteAddress));
      zrtpSocket    = new ZRTPResponderSocket(secureSocket, zid);

      callStateListener.notifyConnectingtoInitiator();

      super.run();
    } catch (SignalingException se) {
      Log.w( "ResponderCallManager", se );
      callStateListener.notifyServerFailure();
    } catch (SessionInitiationFailureException e) {
      Log.w("ResponderCallManager", e);
      callStateListener.notifyServerFailure();
    } catch (SessionStaleException e) {
      Log.w("ResponderCallManager", e);
      callStateListener.notifyCallStale();
    } catch (LoginFailedException lfe) {
      Log.w("ResponderCallManager", lfe);
      callStateListener.notifyLoginFailed();
    } catch (SocketException e) {
      Log.w("ResponderCallManager", e);
      callStateListener.notifyClientFailure();
    } catch( RuntimeException e ) {
      Log.e( "ResponderCallManager", "Died unhandled with exception!");
      Log.w( "ResponderCallManager", e );
      callStateListener.notifyClientFailure();
    }
  }

  public synchronized void answer(boolean answer) {
    this.answer = (answer ? 1 : 2);
    notifyAll();
  }

  private synchronized boolean waitForAnswer() {
    try {
      while (answer == 0)
        wait();
    } catch (InterruptedException ie) {
      throw new IllegalArgumentException(ie);
    }

    return this.answer == 1;
  }

  @Override
  public void terminate() {
    synchronized (this) {
      if (answer == 0) {
        answer(false);
      }
    }

    super.terminate();
  }

  @Override
  protected void setSecureSocketKeys(MasterSecret masterSecret) {
    secureSocket.setKeys(masterSecret.getInitiatorSrtpKey(), masterSecret.getInitiatorMacKey(),
                         masterSecret.getInitiatorSrtpSalt(), masterSecret.getResponderSrtpKey(),
                         masterSecret.getResponderMacKey(), masterSecret.getResponderSrtpSailt());
  }

}
