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

import org.thoughtcrime.redphone.profiling.ProfilingTimer;
import org.thoughtcrime.redphone.profiling.TimeProfiler;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A StreamCipher handles the block cipher operations for
 * a stream of SRTP data.
 *
 * @author Moxie Marlinspike
 *
 */

public class StreamCipher {
  private final Cipher cipher;
  private final Key secret;
  private final byte[] salt;

  public StreamCipher(byte[] secret, byte[] salt) {
    try {
      this.cipher = Cipher.getInstance("AES/CTR/NoPadding");
      this.secret = new SecretKeySpec(secret, 0, secret.length, "AES");
      this.salt   = salt;
    } catch (NoSuchPaddingException nspe) {
      throw new IllegalArgumentException(nspe);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }

  }

  public void encrypt(SecureRtpPacket packet) {
    try {
      TimeProfiler.startBlock("SC:enc:gCypher" );
      Cipher cipher = getCipher(Cipher.ENCRYPT_MODE, packet);
      TimeProfiler.stopBlock("SC:enc:gCypher" );
      TimeProfiler.startBlock("SC:enc:setPayLoad" );
      packet.setPayload(cipher.doFinal(packet.getPayload()));
      TimeProfiler.stopBlock("SC:enc:setPayLoad" );
    } catch (IllegalBlockSizeException e) {
      throw new IllegalArgumentException(e);
    } catch (BadPaddingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public void decrypt(SecureRtpPacket packet) {
    try {
      TimeProfiler.startBlock("SC:dec:gCypher" );
      Cipher cipher = getCipher(Cipher.DECRYPT_MODE, packet);
      TimeProfiler.stopBlock("SC:dec:gCypher" );
      TimeProfiler.startBlock("SC:dec:setPayl" );
      packet.setPayload(cipher.doFinal(packet.getPayload()));
      TimeProfiler.stopBlock("SC:dec:setPayl" );
    } catch (IllegalBlockSizeException e) {
      throw new IllegalArgumentException(e);
    } catch (BadPaddingException e) {
      throw new IllegalArgumentException(e);
    }
  }


  private byte[] getIvForSequence(SecureRtpPacket packet) {
    long logicalSequence = packet.getLogicalSequence();
    long ssrc            = packet.getSSRC();
    byte[] iv            = new byte[16];

    System.arraycopy(this.salt, 0, iv, 0, this.salt.length);

    iv[6]   ^= (byte)(ssrc >> 8);
    iv[7]   ^= (byte)(ssrc);
    iv[8]   ^= (byte)(logicalSequence >> 40);
    iv[9]   ^= (byte)(logicalSequence >> 32);
    iv[10]  ^= (byte)(logicalSequence >> 24);
    iv[11]  ^= (byte)(logicalSequence >> 16);
    iv[12]  ^= (byte)(logicalSequence >> 8);
    iv[13]  ^= (byte)(logicalSequence);

    return iv;
  }

  ProfilingTimer cgiT = new ProfilingTimer( "getCipher::getInstance" );
  ProfilingTimer ivbT = new ProfilingTimer( "getCipher::getIvForSequence");
  ProfilingTimer ivpT = new ProfilingTimer( "getCipher::IvParameterSpec" );
  ProfilingTimer cinT = new ProfilingTimer( "getCipher::init");

  private Cipher getCipher(int mode, SecureRtpPacket packet)  {
    try {
      ivbT.start();
      byte[] ivBytes     = getIvForSequence(packet);
      ivbT.stop();
      ivpT.start();
      IvParameterSpec iv = new IvParameterSpec(ivBytes);
      ivpT.stop();
      cinT.start();
      cipher.init(mode, secret, iv);
      cinT.stop();

      return cipher;
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException("Invaid Key?");
    } catch (InvalidAlgorithmParameterException e) {
      throw new IllegalArgumentException("Bad IV?");
    }
  }


}
