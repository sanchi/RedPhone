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

/**
 * A SecureStream handles the cryptographic operations associated with an SRTP
 * connection.  There are typically two streams per connection, one for incoming
 * and one for outgoing data.
 *
 * @author Moxie Marlinspike
 *
 */

public class SecureStream {

  private SequenceCounter sequenceCounter;
  private StreamCipher streamCipher;
  private StreamMac streamMac;

  public SecureStream(byte[] cipherKey, byte[] macKey, byte[] salt) {
    this.sequenceCounter = new SequenceCounter();
    this.streamCipher    = new StreamCipher(cipherKey, salt);
    this.streamMac       = new StreamMac(macKey);
  }

  public void encrypt(SecureRtpPacket packet) {
    streamCipher.encrypt(packet);
  }

  public void decrypt(SecureRtpPacket packet) {
    streamCipher.decrypt(packet);
  }

  public void mac(SecureRtpPacket packet) {
    streamMac.macPacket(packet);
  }

  public boolean verifyMac(SecureRtpPacket packet) {
    return streamMac.verifyPacket(packet);
  }

  public void updateSequence(SecureRtpPacket packet) {
    sequenceCounter.updateSequence(packet);
  }

}
