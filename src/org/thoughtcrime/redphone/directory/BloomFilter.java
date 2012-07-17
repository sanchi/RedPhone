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

package org.thoughtcrime.redphone.directory;

import org.thoughtcrime.redphone.util.Conversions;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A simple bloom filter implementation that backs the RedPhone directory.
 *
 * @author Moxie Marlinspike
 *
 */

public class BloomFilter {

  private final byte[] byteArray;
  private final int hashCount;

  public BloomFilter(byte[] byteArray, int hashCount) {
    this.byteArray = byteArray;
    this.hashCount = hashCount;
  }

  public byte[] getFilter() {
    return byteArray;
  }

  public int getHashCount() {
    return hashCount;
  }

  private boolean isBitSet(long bitIndex) {
    int byteInQuestion = this.byteArray[(int)(bitIndex / 8)];
    int bitOffset      = (0x01 << (bitIndex % 8));

    return (byteInQuestion & bitOffset) > 0;
  }

  public boolean contains(String entity) {
    try {
      for (int i=0;i<this.hashCount;i++) {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec((i+"").getBytes(), "HmacSHA1"));

        byte[] hashValue = mac.doFinal(entity.getBytes());
        long bitIndex    = Conversions.byteArray4ToLong(hashValue, 0) % (this.byteArray.length * 8);

        if (!isBitSet(bitIndex))
          return false;
      }

      return true;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

}
