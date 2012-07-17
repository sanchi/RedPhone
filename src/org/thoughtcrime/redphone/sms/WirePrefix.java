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

package org.thoughtcrime.redphone.sms;

import android.util.Log;

import org.thoughtcrime.redphone.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A mechanism for a more expensive push message prefix, nominally to
 * make filtering on a large scale more expensive.
 *
 * @author Moxie Marlinspike
 *
 */

public abstract class WirePrefix {

  private static final int HASH_ITERATIONS = 1000;
  private static final int PREFIX_BYTES    = 3;
  public  static final int PREFIX_SIZE	   = 4;

  public static boolean isCall(String message) {
    return verifyPrefix("?RPC", message);
  }

  public static boolean isCall(String message, int offset) {
    message = message.substring(offset);
    return isCall(message);
  }

  public static String calculateCallPrefix(String message) {
    return calculatePrefix(("?RPC" + message).getBytes(), PREFIX_BYTES);
  }

  private static boolean verifyPrefix(String prefixType, String message) {
    if (message.length() <= PREFIX_SIZE)
      return false;

    String prefix           = message.substring(0, PREFIX_SIZE);
    message                 = message.substring(PREFIX_SIZE);

    Log.w("Prefix", "Calculating on message: " + message + "**");

    String calculatedPrefix = calculatePrefix((prefixType + message).getBytes(), PREFIX_BYTES);

    assert(calculatedPrefix.length() == PREFIX_SIZE);

    Log.w("Prefix", "Received prefix: " + prefix);
    Log.w("Prefix", "Calculated prefix: " + calculatedPrefix);

    return prefix.equals(calculatedPrefix);
  }

  private static String calculatePrefix(byte[] message, int byteCount) {
    try {
      MessageDigest md     = MessageDigest.getInstance("SHA1");
      byte[] runningDigest = message;

      for (int i=0;i<HASH_ITERATIONS;i++) {
        runningDigest = md.digest(runningDigest);
      }

      return Base64.encodeBytes(runningDigest, 0, byteCount);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
}