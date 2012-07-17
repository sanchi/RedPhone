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

import org.thoughtcrime.redphone.ApplicationContext;
import org.thoughtcrime.redphone.Release;
import org.thoughtcrime.redphone.crypto.SecureRtpSocket;
import org.thoughtcrime.redphone.crypto.zrtp.MasterSecret;
import org.thoughtcrime.redphone.crypto.zrtp.ZRTPInitiatorSocket;
import org.thoughtcrime.redphone.network.RtpSocket;
import org.thoughtcrime.redphone.signaling.LoginFailedException;
import org.thoughtcrime.redphone.signaling.NetworkConnector;
import org.thoughtcrime.redphone.signaling.NoSuchUserException;
import org.thoughtcrime.redphone.signaling.OtpCounterProvider;
import org.thoughtcrime.redphone.signaling.ServerMessageException;
import org.thoughtcrime.redphone.signaling.SessionInitiationFailureException;
import org.thoughtcrime.redphone.signaling.SignalingException;
import org.thoughtcrime.redphone.signaling.SignalingSocket;
import org.thoughtcrime.redphone.ui.ApplicationPreferencesActivity;

import java.net.InetSocketAddress;

/**
 * Call Manager for the coordination of outgoing calls.  It initiates
 * signaling, negotiates ZRTP, and kicks off the call audio manager.
 *
 * @author Moxie Marlinspike
 *
 */
public class InitiatingCallManager extends CallManager {

  private final Context context;
  private final String localNumber;
  private final String password;
  private final byte[] zid;
  private boolean loopbackMode;

  public InitiatingCallManager(Context context, String localNumber, String password,
                               String remoteNumber, byte[] zid)
  {
    super(remoteNumber, "InitiatingCallManager Thread", context );
    this.context        = context;
    this.localNumber    = localNumber;
    this.password       = password;
    this.zid            = zid;
    this.loopbackMode   = ApplicationPreferencesActivity.getLoopbackEnabled(context);
  }

  @Override
  public void run() {
    if( loopbackMode ) {
      runLoopback();
      return;
    }

    try {
      notifyConnecting();

      signalingSocket = new SignalingSocket(context, Release.RELAY_SERVER_HOST,
                                            Release.SERVER_PORT, localNumber, password,
                                            OtpCounterProvider.getInstance());

      sessionDescriptor = signalingSocket.initiateConnection(remoteNumber);

      int localPort = new NetworkConnector(sessionDescriptor.sessionId, Release.RELAY_SERVER_HOST,
                                           sessionDescriptor.relayPort).makeConnection();

      secureSocket  = new SecureRtpSocket(new RtpSocket(localPort,
                                          new InetSocketAddress(Release.RELAY_SERVER_HOST,
                                          sessionDescriptor.relayPort)));
      zrtpSocket    = new ZRTPInitiatorSocket(secureSocket, zid);

      processSignals();

      ApplicationContext.getInstance().getCallStateListener().notifyWaitingForResponder();

      super.run();
    } catch (NoSuchUserException nsue) {
      Log.w("InitiatingCallManager", nsue);
      ApplicationContext.getInstance().getCallStateListener().notifyNoSuchUser();
    } catch (ServerMessageException ife) {
      Log.w("InitiatingCallManager", ife);
      ApplicationContext.getInstance().getCallStateListener().notifyServerMessage(ife.getMessage());
    } catch (LoginFailedException lfe) {
      Log.w("InitiatingCallManager", lfe);
      ApplicationContext.getInstance().getCallStateListener().notifyLoginFailed();
    } catch (SignalingException se) {
      Log.w("InitiatingCallManager", se);
      ApplicationContext.getInstance().getCallStateListener().notifyServerFailure();
    } catch( RuntimeException e ) {
      Log.e( "InitiatingCallManager", "Died with unhandled exception!");
      Log.w( "InitiatingCallManager", e );
      ApplicationContext.getInstance().getCallStateListener().notifyClientFailure();
    } catch (SessionInitiationFailureException e) {
      Log.w("InitiatingCallManager", e);
      ApplicationContext.getInstance().getCallStateListener().notifyServerFailure();
    }
  }

  private void notifyConnecting() {
    ApplicationContext.getInstance().getCallStateListener().notifyCallConnecting();
  }

  @Override
  protected void setSecureSocketKeys(MasterSecret masterSecret) {
    secureSocket.setKeys(masterSecret.getResponderSrtpKey(), masterSecret
        .getResponderMacKey(), masterSecret.getResponderSrtpSailt(),
        masterSecret.getInitiatorSrtpKey(), masterSecret
            .getInitiatorMacKey(), masterSecret
            .getInitiatorSrtpSalt());
  }

  //***************************
  // SOA's Loopback Code, for debugging.

  private void runLoopback() {
    try {
      super.doLoopback();
    } catch( RuntimeException e ) {
      Log.e( "InitiatingCallManager", "Died with exception!");
      Log.w( "InitiatingCallManager", e );
      ApplicationContext.getInstance().getCallStateListener().notifyClientFailure();
    }
  }

}
