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
    Log.d("FOO", "FOO");
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
