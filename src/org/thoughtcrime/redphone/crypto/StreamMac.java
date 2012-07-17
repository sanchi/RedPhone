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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A StreamMac handles the authentication (Hmac) operations
 * for a stream of SRTP packets.
 *
 * @author Moxie Marlinspike
 *
 */

public class StreamMac {

  private final Mac mac;

  public StreamMac(byte[] macKey) {
    try {
      SecretKeySpec key = new SecretKeySpec(macKey, "HmacSHA1");
      this.mac          = Mac.getInstance("HmacSHA1");
      this.mac.init(key);
    } catch (NoSuchAlgorithmException nsae) {
      throw new IllegalArgumentException(nsae);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public boolean verifyPacket(SecureRtpPacket packet) {
    byte[] myMacBytes    = getMacForPacket(packet);
    byte[] theirMacBytes = packet.getMac();

    return Arrays.equals(myMacBytes, theirMacBytes);
  }

  public void macPacket(SecureRtpPacket packet) {
    byte[] macBytes = getMacForPacket(packet);
    packet.setMac(macBytes);
  }

  private byte[] getMacForPacket(SecureRtpPacket packet) {
      mac.update(packet.getDataToMac(), 0, packet.getDataToMacLength());
      return mac.doFinal();
  }

}
