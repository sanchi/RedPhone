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

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Base DH packet, from which DH part one and DH part two derive.
 *
 * @author Moxie Marlinspike
 *
 */

public abstract class DHPacket extends HandshakePacket {

  private static final int DH_LENGTH = 468;

  private static final int LENGTH_OFFSET = MESSAGE_BASE + 2;
  private static final int HASH_OFFSET   = MESSAGE_BASE + 12;
  private static final int RS1_OFFSET    = MESSAGE_BASE + 44;
  private static final int RS2_OFFSET    = MESSAGE_BASE + 52;
  private static final int AUX_OFFSET    = MESSAGE_BASE + 60;
  private static final int PBX_OFFSET    = MESSAGE_BASE + 68;
  private static final int PVR_OFFSET    = MESSAGE_BASE + 76;
  private static final int MAC_OFFSET    = MESSAGE_BASE + 460;

  public DHPacket(RtpPacket packet) {
    super(packet);
  }

  public DHPacket(RtpPacket packet, boolean deepCopy) {
    super(packet, deepCopy);
  }

  public DHPacket(String partTag, HashChain hashChain, byte[] pvr) {
    super(partTag, DH_LENGTH);
    setHash(hashChain.getH1());
    setState();
    setPvr(pvr);
    setMac(hashChain.getH0(), MAC_OFFSET, DH_LENGTH - 8);
  }

  public byte[] getPvr() {
    byte[] pvr = new byte[384];
    System.arraycopy(this.data, PVR_OFFSET, pvr, 0, pvr.length);
    return pvr;
  }

  public byte[] getHash() {
    byte[] hash = new byte[32];
    System.arraycopy(this.data, HASH_OFFSET, hash, 0, hash.length);
    return hash;
  }

  public void veifyMac(byte[] key) throws InvalidPacketException {
    super.verifyMac(key, MAC_OFFSET, DH_LENGTH-8, getHash());
  }

  private void setHash(byte[] hash) {
    System.arraycopy(hash, 0, this.data, HASH_OFFSET, hash.length);
  }

  private void setPvr(byte[] pvr) {
    System.arraycopy(pvr, 0, this.data, PVR_OFFSET, pvr.length);
  }

  private void setState() {
    try {
      SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
      byte[] randomBytes  = new byte[8];

      for (int i=0;i<4;i++) {
        random.nextBytes(randomBytes);
        System.arraycopy(randomBytes, 0, this.data, RS1_OFFSET + (i * 8), randomBytes.length);
      }
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
