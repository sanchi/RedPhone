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

package org.thoughtcrime.redphone.network;

import android.os.SystemClock;
import android.util.Log;

import org.thoughtcrime.redphone.call.CallStateListener;
import org.thoughtcrime.redphone.profiling.PeriodicTimer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * RtpSocket wraps a {@link DatagramSocket}, allowing {@link RtpPacket}s to be sent a received.
 *
 * @author Stuart O. Anderson
 */
public class RtpSocket {
  private static final String TAG = RtpSocket.class.getName();
  private final CallStateListener callStateListener;

  private final byte [] buf = new byte[4096];
  protected DatagramSocket socket;

  public RtpSocket(CallStateListener callStateListener, int localPort, InetSocketAddress remoteAddress)
      throws SocketException {
    this.callStateListener = callStateListener;

    socket = new DatagramSocket(localPort);
    socket.setSoTimeout(1);
    socket.connect(new InetSocketAddress(remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort())); //TODO(Stuart Anderson): Why do we clone the address here?  Simplify?
    Log.d(TAG, "Connected to: " + remoteAddress.toString());
  }

  public void setTimeout(int timeoutMillis) {
    try {
      socket.setSoTimeout(timeoutMillis);
    } catch (SocketException e) {
      Log.w(TAG, e);
    }
  }

  private long totalSendTime = 0;
  private PeriodicTimer pt = new PeriodicTimer(10000);

  public void send(RtpPacket outPacket) {
    long start = SystemClock.uptimeMillis();
    try {
      socket.send(new DatagramPacket(outPacket.getPacket(), outPacket.getPacketLength()));
    } catch (IOException e) {
      Log.w(TAG, e);
    }
    long stop = SystemClock.uptimeMillis();
    totalSendTime += stop - start;
    if( pt.periodically() ) {
      Log.d( TAG, "Send avg time:" + (totalSendTime/(double)pt.getPeriod()) );
      totalSendTime = 0;
    }
  }

  public RtpPacket receive() {
    try {
      DatagramPacket dataPack = new DatagramPacket(buf, buf.length);
      socket.setSoTimeout(1);
      socket.receive(dataPack);
      RtpPacket inPacket = new RtpPacket(dataPack.getData(), dataPack.getLength());
      return inPacket;
    } catch( SocketTimeoutException e ) {
      return null;
    } catch (SocketException e) {
      // XXX-S I don't think this should be reaching back up the stack from this level.
      // Instead, it seems like this should be throwing IOException/SocketException up
      // the stack to CallAudioManager or wherever, which should handle reaching back up
      // from that point in the stack.
      callStateListener.notifyCallDisconnected();
    } catch (IOException e) {
      callStateListener.notifyCallDisconnected();
    }
    return null;
  }

  public void close() {
    socket.close();
  }
}
