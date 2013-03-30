package org.thoughtcrime.redphone.crypto.stream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;

/**
 * Writes an encrypted stream.
 *
 * @author Stuart O. Anderson
 */
public class EncryptedOutputStream extends FilterOutputStream {
  public static final String HEADER_PREFIX = "org.whispersys.redphone.crypto.stream.EncryptedOutputStream-";

  public EncryptedOutputStream(OutputStream out, PublicKey publicKey) throws IOException {
    super(null);
    try {
      SecretKey cipherKey = makeSecretKey("AES", 256);
      SecretKey hmacKey = makeSecretKey("HmacSHA1", 160);

      byte[] iv = new byte[16];
      SecureRandom.getInstance("SHA1PRNG").nextBytes(iv);

      Mac mac = EncryptedStreamUtils.makeMac(hmacKey);
      this.out = new CipherOutputStream(new HmacAccululatorStream(out, mac), makeSymmetricCipher(cipherKey, iv));

      out.write((HEADER_PREFIX + "0001").getBytes("UTF8"));
      out.write(encryptSymmetricKey(cipherKey, publicKey));
      out.write(encryptSymmetricKey(hmacKey, publicKey));
      out.write(iv);

      mac.update(iv);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      throw new AssertionError(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] encryptSymmetricKey(SecretKey key, PublicKey publicKey)
    throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException,
    BadPaddingException {
    Cipher rsaCipher = Cipher.getInstance("RSA");
    rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
    return rsaCipher.doFinal(key.getEncoded());
  }

  private SecretKey makeSecretKey(String algorithm, int bits) throws NoSuchAlgorithmException {
    KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm);
    keyGenerator.init(bits);
    return keyGenerator.generateKey();
  }

  private Cipher makeSymmetricCipher(SecretKey secretKey, byte iv[])
    throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
    Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    IvParameterSpec ivSpec = new IvParameterSpec(iv);

    aesCipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
    return aesCipher;
  }

  /**
   * Computes HMAC of encrypted bytes, writes it to the end of the stream.
   */
  private static class HmacAccululatorStream extends FilterOutputStream {
    private final Mac mac;
    public HmacAccululatorStream(OutputStream out, Mac mac) {
      super(out);
      this.mac = mac;

    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
      mac.update(buffer, offset, length);
      super.write(buffer, offset, length);
    }

    @Override
    public void close() throws IOException {
      write(mac.doFinal());
      super.close();
    }
  }
}
