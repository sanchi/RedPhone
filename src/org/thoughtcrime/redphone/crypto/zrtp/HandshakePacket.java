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

import android.util.Log;

import org.thoughtcrime.redphone.Release;
import org.thoughtcrime.redphone.network.RtpPacket;
import org.thoughtcrime.redphone.util.Conversions;
import org.thoughtcrime.redphone.util.Hex;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.CRC32;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Base ZRTP handshake packet, from which all
 * handshake packets derive.
 *
 *
 * @author Moxie Marlinspike
 *
 */

public class HandshakePacket extends RtpPacket {

  private static final int PREFIX_OFFSET  = 0;
  private static final int PREFIX_VALUE   = 0x20;
  private static final int COOKIE_OFFSET  = 4;

  protected static final int MESSAGE_BASE = 12 + RtpPacket.HEADER_LENGTH;
  private static final int LENGTH_OFFSET  = MESSAGE_BASE + 2;

  private static final int MAGIC_VALUE    = 0x505a;
  private static final int MAGIC_OFFSET   = MESSAGE_BASE + 0;
  private static final int TYPE_OFFSET    = MESSAGE_BASE + 4;

  private static final int ZRTP_HEADERS_AND_FOOTER_LENGTH = 16;

  public HandshakePacket(RtpPacket packet) {
    super(packet.getPacket(), packet.getPacketLength());
  }

  public HandshakePacket(RtpPacket packet, boolean deepCopy) {
    super(packet.getPacket(), packet.getPacketLength(), deepCopy);
  }

  public HandshakePacket(String type, int length) {
    super(length + ZRTP_HEADERS_AND_FOOTER_LENGTH);

    setLength(length);
    setPrefix();
    setCookie();
    setMagic();
    setType(type);
  }

  public byte[] getMessageBytes() throws InvalidPacketException {
    if (this.getPacketLength() < LENGTH_OFFSET + 3)
      throw new InvalidPacketException("Packet length shorter than length header.");

    int messagePacketLength = this.getLength();

    if (messagePacketLength + 4 > this.getPacketLength())
      throw new InvalidPacketException("Encoded packet length longer than length of packet.");

    byte[] messageBytes     = new byte[messagePacketLength];
    System.arraycopy(this.data, MESSAGE_BASE, messageBytes, 0, messagePacketLength);

    return messageBytes;
  }

  private void setCookie() {
    Conversions.longTo4ByteArray(this.data, COOKIE_OFFSET, 0x5a525450);
  }

  private void setPrefix() {
    data[PREFIX_OFFSET] = PREFIX_VALUE;
  }

  private int getLength() {
    return Conversions.byteArrayToShort(this.data, LENGTH_OFFSET);
  }

  protected void setLength(int length) {
    Conversions.shortToByteArray(this.data, LENGTH_OFFSET, length);
  }

  private byte[] calculateMac(byte[] key, int messageLength) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      mac.update(this.data, MESSAGE_BASE, messageLength);
      return mac.doFinal();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    }
  }

  protected void setMac(byte[] key, int macOffset, int messageLength) {
    byte[] digest = calculateMac(key, messageLength);
    System.arraycopy(digest, 0, this.data, macOffset, 8);

    if (Release.DEBUG)
      Log.w("HandshakePacket", "Setting MAC: " + Hex.toString(digest));
  }

  protected void verifyMac(byte[] key, int macOffset, int messageLength, byte[] subhash)
      throws InvalidPacketException
  {
    byte[] digest          = calculateMac(key, messageLength);
    byte[] truncatedDigest = new byte[8];
    byte[] messageDigest   = new byte[8];

    System.arraycopy(digest, 0, truncatedDigest, 0, truncatedDigest.length);
    System.arraycopy(this.data, macOffset, messageDigest, 0, messageDigest.length);

    if (!Arrays.equals(truncatedDigest, messageDigest))
      throw new InvalidPacketException("Bad MAC!");

    if (!verifySubHash(key, subhash))
      throw new InvalidPacketException("MAC key is not preimage of hash included in message!");
  }

  private boolean verifySubHash(byte[] key, byte[] subhash) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest    = md.digest(key);
      return Arrays.equals(digest, subhash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private long calculateCRC() {
    CRC32 crc = new CRC32();
    crc.update(this.data, 0, this.getPacketLength()-4);
    return crc.getValue();
  }

  public boolean verifyCRC() {
    long myCRC    = calculateCRC();
    long theirCRC = Conversions.byteArray4ToLong(this.data, this.getPacketLength()-4);
    byte crcb[] = new byte[4];
    Conversions.longTo4ByteArray(crcb, 0, myCRC);
    return myCRC == theirCRC;
  }

  public void setCRC() {
    Conversions.longTo4ByteArray(this.data, this.getPacketLength()-4, calculateCRC());
  }

  public String getType() {
    try {
      return new String(data, TYPE_OFFSET, 8, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private void setMagic() {
    Conversions.shortToByteArray(this.data, MAGIC_OFFSET, MAGIC_VALUE);
  }

  private void setType(String type) {
    type.getBytes(0, type.length(), this.data, TYPE_OFFSET);
  }

}
