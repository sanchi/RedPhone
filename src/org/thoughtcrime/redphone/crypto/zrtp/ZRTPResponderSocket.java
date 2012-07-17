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

package org.thoughtcrime.redphone.crypto.zrtp;

import org.thoughtcrime.redphone.crypto.SecureRtpSocket;

/**
 * The "responder" side of a ZRTP handshake.  We've received a signal from the
 * initiator, and they're waiting for us to kick off the handshake with a hello
 * message.
 *
 * @author Moxie Marlinspike
 *
 */

public class ZRTPResponderSocket extends ZRTPSocket {

  private HelloPacket localHello;
  private HelloPacket foreignHello;
  private CommitPacket foreignCommit;
  private DHPartOnePacket localDH;
  private DHPartTwoPacket foreignDH;

  private final byte[] zid;

  public ZRTPResponderSocket(SecureRtpSocket socket, byte[] zid) {
    super(socket, EXPECTING_HELLO);
    this.zid        = zid;
    this.localHello = new HelloPacket(hashChain, zid);
  }

  @Override
  protected void handleHello(HandshakePacket packet) {
    foreignHello = new HelloPacket(packet, true);

    setState(EXPECTING_COMMIT);
    sendFreshPacket(new HelloAckPacket());
  }

  @Override
  protected void handleCommit(HandshakePacket packet) throws InvalidPacketException {
    foreignCommit = new CommitPacket(packet, true);
    localDH       = new DHPartOnePacket(hashChain, getPublicKey());

    foreignHello.verifyMac(foreignCommit.getHash());

    setState(EXPECTING_DH_2);
    sendFreshPacket(localDH);
  }

  @Override
  protected void handleDH(HandshakePacket packet) throws InvalidPacketException {
    foreignDH = new DHPartTwoPacket(packet, true);

    foreignCommit.verifyMac(foreignDH.getHash());
    foreignCommit.verifyHvi(localHello.getMessageBytes(), foreignDH.getMessageBytes());

    SecretCalculator calculator = new SecretCalculator();
    byte[] dhResult     = calculator.calculateDHSecret(keyPair, foreignDH.getPvr());
    byte[] totalHash    = calculator.calculateTotalHash(localHello, foreignCommit,
                                                        localDH, foreignDH);
    byte[] sharedSecret = calculator.calculateSharedSecret(dhResult, totalHash,
                                                           foreignHello.getZID(),
                                                           localHello.getZID());

    this.masterSecret   = new MasterSecret(sharedSecret, totalHash, foreignHello.getZID(),
                                           localHello.getZID());

    setState(EXPECTING_CONFIRM_TWO);
    sendFreshPacket(new ConfirmOnePacket(masterSecret.getResponderMacKey(),
                                         masterSecret.getResponderZrtpKey(),
                                         this.hashChain));
  }

  @Override
  protected void handleConfirmTwo(HandshakePacket packet) throws InvalidPacketException {
    ConfirmTwoPacket confirmPacket = new ConfirmTwoPacket(packet);

    confirmPacket.verifyMac(masterSecret.getInitiatorMacKey());
    confirmPacket.decrypt(masterSecret.getInitiatorZrtpKey());

    byte[] preimage = confirmPacket.getPreimage();
    foreignDH.veifyMac(preimage);

    setState(HANDSHAKE_COMPLETE);
    sendFreshPacket(new ConfAckPacket());
  }

  @Override
  protected void handleConfirmOne(HandshakePacket packet) throws InvalidPacketException {
    throw new InvalidPacketException("Responder received a Confirm1 Packet?");
  }

  @Override
  protected void handleHelloAck(HandshakePacket packet) {
    throw new AssertionError("Invalid state!");
  }

  @Override
  protected void handleConfirmAck(HandshakePacket packet) {
    throw new AssertionError("Invalid state!");
  }

  @Override
  public void negotiate() throws NegotiationFailedException {
    sendFreshPacket(localHello);
    super.negotiate();
  }


}
