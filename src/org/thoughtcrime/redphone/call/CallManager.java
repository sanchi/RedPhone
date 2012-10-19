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
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Process;
import android.text.format.DateFormat;
import android.util.Log;

import org.thoughtcrime.redphone.ClientException;
import org.thoughtcrime.redphone.audio.CallAudioManager;
import org.thoughtcrime.redphone.crypto.SecureRtpSocket;
import org.thoughtcrime.redphone.crypto.zrtp.MasterSecret;
import org.thoughtcrime.redphone.crypto.zrtp.NegotiationFailedException;
import org.thoughtcrime.redphone.crypto.zrtp.RecipientUnavailableException;
import org.thoughtcrime.redphone.crypto.zrtp.SASCalculator;
import org.thoughtcrime.redphone.crypto.zrtp.ZRTPSocket;
import org.thoughtcrime.redphone.signaling.SessionDescriptor;
import org.thoughtcrime.redphone.signaling.SignalingSocket;
import org.thoughtcrime.redphone.ui.ApplicationPreferencesActivity;

import java.util.Date;

/**
 * The base class for both Initiating and Responder call
 * managers, which coordinate the setup of an outgoing or
 * incoming call.
 *
 * @author Moxie Marlinspike
 *
 */

public abstract class CallManager {
  private static final String CODEC_NAME = "SPEEX";

  protected final String remoteNumber;
  protected final CallStateListener callStateListener;
  protected final Context context;

  private boolean terminated;
  private boolean loopbackMode;
  private CallAudioManager callAudioManager;
  private SignalManager signalManager;

  protected SessionDescriptor sessionDescriptor;
  protected ZRTPSocket zrtpSocket;
  protected SecureRtpSocket secureSocket;
  protected SignalingSocket signalingSocket;

  public CallManager(Context context, CallStateListener callStateListener,
                    String remoteNumber)
  {
    this.remoteNumber      = remoteNumber;
    this.callStateListener = callStateListener;
    this.terminated        = false;
    this.context           = context;
    this.loopbackMode      = ApplicationPreferencesActivity.getLoopbackEnabled(context);

    printInitDebug();
  }

  public void run() throws ClientException {
    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

    try {
      Log.d( "CallManager", "negotiating..." );
      if (!terminated) callAudioManager = new CallAudioManager(secureSocket, CODEC_NAME, context);
      if (!terminated) zrtpSocket.negotiate();
      if (!terminated) setSecureSocketKeys(zrtpSocket.getMasterSecret());
      if (!terminated) callStateListener
                        .notifyCallConnected(SASCalculator
                                              .calculateSAS(zrtpSocket.getMasterSecret().getSAS()));

      if (!terminated) {
        Log.d("CallManager", "Finished handshake, calling run() on CallAudioManager...");
        callAudioManager.run();
      }

    } catch (RecipientUnavailableException rue) {
      Log.w("CallManager", rue);
      if (!terminated) callStateListener.notifyRecipientUnavailable();
    } catch (NegotiationFailedException nfe) {
      Log.w("CallManager", nfe);
      if (!terminated) callStateListener.notifyHandshakeFailed();
    }
  }

  public void terminate() {
    this.terminated = true;

    if (callAudioManager != null)
      callAudioManager.terminate();

    if (signalManager != null)
      signalManager.terminate();

    if (zrtpSocket != null)
      zrtpSocket.close();
  }

  public SessionDescriptor getSessionDescriptor() {
    return this.sessionDescriptor;
  }

  protected void processSignals() {
    Log.w("CallManager", "Starting signal processing loop...");
    this.signalManager = new SignalManager(callStateListener, signalingSocket, sessionDescriptor);
  }

  protected abstract void setSecureSocketKeys(MasterSecret masterSecret);

  ///**********************
  // Methods below are SOA's loopback and testing shims.

  private void printInitDebug() {
    Context c = context;
    String vName = "unknown";
    try {
        vName = c.getPackageManager().getPackageInfo("org.thoughtcrime.redphone", 0).versionName;
    } catch (NameNotFoundException e) {
    }

    ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo networkInfo = cm.getActiveNetworkInfo();

    Date date = new Date();
    Log.d( "CallManager", "Initializing:"
            + " audioMode: " + ApplicationPreferencesActivity.getAudioModeIncall(c)
            + " singleThread: " + ApplicationPreferencesActivity.isSingleThread(c)
            + " device: " + Build.DEVICE
            + " manufacturer: " + Build.MANUFACTURER
            + " os-version: " + Build.VERSION.RELEASE
            + " product: " + Build.PRODUCT
            + " redphone-version: " + vName
            + " network-type: " + (networkInfo == null ? null : networkInfo.getTypeName())
            + " network-subtype: " + (networkInfo == null ? null : networkInfo.getSubtypeName())
            + " network-extra: " + (networkInfo == null ? null : networkInfo.getExtraInfo())
            + " time: " + DateFormat.getDateFormat(context).format(date) + DateFormat.getTimeFormat(context).format(date)
            );
  }
}
