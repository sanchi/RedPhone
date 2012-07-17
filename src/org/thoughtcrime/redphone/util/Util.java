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

package org.thoughtcrime.redphone.util;

import android.util.Log;

import org.thoughtcrime.redphone.ApplicationContext;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Random utility functions.
 *
 * @author Moxie Marlinspike
 *
 */

public class Util {

  public static byte[] getBytes(String fromString) {
    try {
      return fromString.getBytes("UTF8");
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  public static String getString(byte[] fromBytes) {
    try {
      return new String(fromBytes, "UTF8");
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  public static String getSecret(int size) {
    try {
      byte[] secret = new byte[size];
      SecureRandom.getInstance("SHA1PRNG").nextBytes(secret);
      return Base64.encodeBytes(secret);
    } catch (NoSuchAlgorithmException nsae) {
      throw new AssertionError(nsae);
    }
  }

  public static void dieWithError(String msg) {
    ApplicationContext.getInstance().getCallStateListener().notifyClientError( msg );
    Log.d("RedPhone:AC", "Dying with error:" + msg);
  }

  public static void dieWithError(Exception e) {
    Log.w( "RedPhone:AC", e );
    ApplicationContext.getInstance().getCallStateListener().notifyClientError( e.getMessage() );
  }
}

