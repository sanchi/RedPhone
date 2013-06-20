/*
 * Copyright (C) 2013 Open Whisper Systems
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

package org.thoughtcrime.redphone.monitor.stream;

import android.content.res.Resources;
import android.util.Log;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Android specific helpers for working with EncryptedStream objects
 *
 * @author Stuart O. Anderson
 */
public class EncryptedStreamUtils {
  static {
    Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
  }

  public static final int HMAC_SIZE = 20;

  static public PublicKey getPublicKeyFromResource(Resources resources, int res)
    throws InvalidKeySpecException, IOException {
    try {
      InputStream keyStream = resources.openRawResource(res);
      X509EncodedKeySpec keySpec = new X509EncodedKeySpec(readAllBytes(keyStream));
      keyStream.close();

      KeyFactory keyFactory = KeyFactory.getInstance("RSA", "SC");

      return keyFactory.generatePublic(keySpec);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (NoSuchProviderException e) {
      throw new AssertionError(e);
    }
  }

  public static Mac makeMac(SecretKey macKey) throws InvalidKeyException {
    try {
      Mac mac = Mac.getInstance("HmacSHA1", "SC");
      mac.init(macKey);
      return mac;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (NoSuchProviderException e) {
      throw new AssertionError(e);
    }
  }

  static public PrivateKey getPrivateKeyFromResource(Resources resources, int res)
    throws IOException, InvalidKeySpecException {
    try {
      InputStream keyStream = resources.openRawResource(res);
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(readAllBytes(keyStream));
      keyStream.close();
      KeyFactory keyFactory = KeyFactory.getInstance("RSA", "SC");
      return keyFactory.generatePrivate(keySpec);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (NoSuchProviderException e) {
      throw new AssertionError(e);
    }
  }

  static public byte[] readAllBytes(InputStream input) throws IOException {
    byte[] buffer = new byte[2048];
    ByteArrayOutputStream os = new ByteArrayOutputStream();

    int bytesRead;
    while( (bytesRead = input.read(buffer)) != -1) {
      os.write(buffer, 0, bytesRead);
    }
    return os.toByteArray();
  }
}
