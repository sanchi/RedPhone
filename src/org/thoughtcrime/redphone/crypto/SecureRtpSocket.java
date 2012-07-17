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

package org.thoughtcrime.redphone.crypto;

import android.util.Log;

import org.thoughtcrime.redphone.crypto.zrtp.HandshakePacket;
import org.thoughtcrime.redphone.network.RtpPacket;
import org.thoughtcrime.redphone.network.RtpSocket;
import org.thoughtcrime.redphone.profiling.TimeProfiler;

import java.io.IOException;

/**
 * A socket that does SRTP.
 *
 * Every outgoing packet is encrypted/authenticated, and every incoming
 * packet is verified/decrypted.
 *
 * @author Moxie Marlinspike
 *
 */

public class SecureRtpSocket {

  private SecureStream incomingContext;
  private SecureStream outgoingContext;
  private final RtpSocket socket;

  public SecureRtpSocket(RtpSocket socket) {
    this.socket = socket;
    initializeStreamContexts();
  }

  public void close() {
    this.socket.close();
  }

  public void setKeys(byte[] incomingCipherKey, byte[] incomingMacKey, byte[] incomingSalt,
            byte[] outgoingCipherKey, byte[] outgoingMacKey, byte[] outgoingSalt)
  {
    this.incomingContext = new SecureStream(incomingCipherKey, incomingMacKey, incomingSalt);
    this.outgoingContext = new SecureStream(outgoingCipherKey, outgoingMacKey, outgoingSalt);
  }

  private void initializeStreamContexts() {
    byte[] incomingCipherKey = new byte[16];
    byte[] outgoingCipherKey = new byte[16];
    byte[] incomingMacKey    = new byte[20];
    byte[] outgoingMacKey    = new byte[20];
    byte[] incomingSalt      = new byte[14];
    byte[] outgoingSalt      = new byte[14];

    this.incomingContext = new SecureStream(incomingCipherKey, incomingMacKey, incomingSalt);
    this.outgoingContext = new SecureStream(outgoingCipherKey, outgoingMacKey, outgoingSalt);
  }

  public void send(HandshakePacket packet) {
    packet.setCRC();
    socket.send(packet);
  }

  public HandshakePacket receiveHandshakePacket() throws IOException {
    RtpPacket barePacket = socket.receive();
    if (barePacket == null)
      return null;

    HandshakePacket handshakePacket = new HandshakePacket(barePacket);
    if (handshakePacket.verifyCRC())
      return handshakePacket;
    else {
      Log.w("SecureRedPhoneSocket", "Bad CRC!");
      return null;
    }
  }

  public void setTimeout(int timeoutMillis) {
    socket.setTimeout(timeoutMillis);
  }

  public void send(SecureRtpPacket packet) {
    TimeProfiler.startBlock("SRPS:send:updateSeq" );
    outgoingContext.updateSequence(packet);
    TimeProfiler.stopBlock("SRPS:send:updateSeq" );
    TimeProfiler.startBlock("SRPS:send:encrypt" );
    outgoingContext.encrypt(packet);
    TimeProfiler.stopBlock("SRPS:send:encrypt" );
    TimeProfiler.startBlock("SRPS:send:mac" );
    outgoingContext.mac(packet);
    TimeProfiler.stopBlock("SRPS:send:mac" );
    TimeProfiler.startBlock("SRPS:send:send" );
    socket.send(packet);
    TimeProfiler.stopBlock("SRPS:send:send" );
  }

  public SecureRtpPacket receive() {
    TimeProfiler.startBlock( "SecureRedphoneSocket::receive" );
    RtpPacket barePacket;
    barePacket = socket.receive();
    TimeProfiler.stopBlock( "SecureRedphoneSocket::receive" );

    if (barePacket == null)
      return null;

    SecureRtpPacket packet = new SecureRtpPacket(barePacket);

    TimeProfiler.startBlock( "VerfiyRcvMac" );
    if (incomingContext.verifyMac(packet)) {
      TimeProfiler.stopBlock( "VerfiyRcvMac" );
      incomingContext.updateSequence(packet);
      TimeProfiler.startBlock( "RecvDecrypt" );
      incomingContext.decrypt(packet);
      TimeProfiler.stopBlock( "RecvDecrypt" );
      return packet;
    }

    Log.w("SecureRedPhoneSocket", "Bad mac on packet...");
    return null;
  }
}
