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

import org.thoughtcrime.redphone.network.RtpPacket;
import org.thoughtcrime.redphone.util.Conversions;

/**
 * ZRTP 'hello' handshake packet.
 *
 * @author Moxie Marlinspike
 *
 */

public class HelloPacket extends HandshakePacket {
  public  static final String TYPE        = "Hello   ";
  private static final int HELLO_LENGTH   = 88;

  private static final int LENGTH_OFFSET  = MESSAGE_BASE + 2;
  private static final int VERSION_OFFSET = MESSAGE_BASE + 12;
  private static final int CLIENT_OFFSET  = MESSAGE_BASE + 16;
  private static final int HASH_OFFSET    = MESSAGE_BASE + 32;
  private static final int ZID_OFFSET     = MESSAGE_BASE + 64;
  private static final int FLAGS_OFFSET   = MESSAGE_BASE + 76;
  private static final int MAC_OFFSET     = MESSAGE_BASE + 80;

  private static final int ZID_LENGTH	    = 12;

  public HelloPacket(RtpPacket packet) {
    super(packet);
  }

  public HelloPacket(RtpPacket packet, boolean deepCopy) {
    super(packet, deepCopy);
  }

  public HelloPacket(HashChain hashChain, byte[] zid) {
    super(TYPE, HELLO_LENGTH);
    setZrtpVersion();
    setClientId();
    setHash(hashChain.getH3());
    setZID(zid);
    setFlags();
    setMac(hashChain.getH2(), MAC_OFFSET, HELLO_LENGTH - 8);
  }

  public int getLength() {
    return Conversions.byteArrayToShort(data, LENGTH_OFFSET);
  }

  public byte[] getZID() {
    byte[] zid = new byte[ZID_LENGTH];
    System.arraycopy(this.data, ZID_OFFSET, zid, 0, zid.length);
    return zid;
  }

  public byte[] getHash() {
    byte[] hashValue = new byte[32];
    System.arraycopy(this.data, HASH_OFFSET, hashValue, 0, hashValue.length);

    return hashValue;
  }

  public void verifyMac(byte[] key) throws InvalidPacketException {
    if (getLength() < HELLO_LENGTH)
      throw new InvalidPacketException("Encoded length longer than data length.");

    super.verifyMac(key, MAC_OFFSET, HELLO_LENGTH - 8, getHash());
  }

  private void setZrtpVersion() {
    "1.10".getBytes(0, 4, this.data, VERSION_OFFSET);
  }

  private void setClientId() {
    "RedPhone 0.1".getBytes(0, 12, this.data, CLIENT_OFFSET);
  }

  private void setHash(byte[] hash) {
    System.arraycopy(hash, 0, this.data, HASH_OFFSET, hash.length);
  }

  private void setZID(byte[] zid) {
    System.arraycopy(zid, 0, this.data, ZID_OFFSET, zid.length);
  }

  private void setFlags() {
    // Leave flags empty.
  }

}
