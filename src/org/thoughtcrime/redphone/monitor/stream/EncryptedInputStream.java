package org.thoughtcrime.redphone.monitor.stream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;

/**
 * Reads an encrypted stream.
 *
 * The entire stream is read into memory and the MAC is verified when the EncryptedInputStream is constructed.
 *
 * The format of the stream is:
 * [HEADER][AES-KEY (RSA-2048-ENC)][HMAC-KEY (RSA-2048-ENC)][AES-IV][ENC-STREAM-DATA][HMAC of (AES-IV, ENC-STREAM-DATA)]
 *
 * @author Stuart O. Anderson
 */
public class EncryptedInputStream extends FilterInputStream {

  public EncryptedInputStream(InputStream in, PrivateKey privateKey) throws IOException {
    super(null);

    try {
      verifyHeader(in);

      SecretKey cipherKey = readSecretKey(in, privateKey);
      SecretKey macKey = readSecretKey(in, privateKey);
      byte[] iv = readIv(in);

      Cipher aesCipher = makeAesCipher(cipherKey, iv);

      byte[] allBytes = EncryptedStreamUtils.readAllBytes(in);
      int dataLength = allBytes.length - EncryptedStreamUtils.HMAC_SIZE;

      ByteBuffer dataBuffer = ByteBuffer.wrap(allBytes, 0, dataLength);
      ByteBuffer hmacBuffer = ByteBuffer.wrap(allBytes, dataLength, EncryptedStreamUtils.HMAC_SIZE);

      verifyMac(macKey, iv, dataBuffer, hmacBuffer);

      this.in = new CipherInputStream(new ByteArrayInputStream(allBytes, 0, dataLength), aesCipher);
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    }
  }

  private void verifyMac(SecretKey macKey, byte[] iv, ByteBuffer data, ByteBuffer macValue) throws InvalidKeyException {
    Mac mac = EncryptedStreamUtils.makeMac(macKey);
    mac.update(iv);
    mac.update(data);
    byte[] dataMac = mac.doFinal();
    if(0 != macValue.compareTo(ByteBuffer.wrap(dataMac))) {
      throw new IllegalStateException("Mac value doesn't match");
    }
  }

  public void verifyHeader(InputStream in) throws IOException {
    byte[] buf = new byte[EncryptedOutputStream.HEADER_PREFIX.length() + 4];
    DataInputStream inputStream = new DataInputStream(in);
    inputStream.readFully(buf);

    String header = new String(buf, "UTF8");
    if(!header.matches("^" + EncryptedOutputStream.HEADER_PREFIX + "[0-9]*")) {
      throw new IOException("Corrupted header (prefix mismatch");
    }
  }

  public byte[] readIv(InputStream in) throws IOException {
    byte[] buf = new byte[16];
    DataInputStream inputStream = new DataInputStream(in);
    inputStream.readFully(buf);
    return buf;
  }

  public SecretKey readSecretKey(InputStream in, PrivateKey privateKey) throws IOException {
    try {
      DataInputStream inputStream = new DataInputStream(in);
      byte[] encryptedKeyBytes = new byte[256];
      inputStream.readFully(encryptedKeyBytes);

      Cipher rsaCipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA1AndMGF1Padding", "SC");
      rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
      byte[] decryptedKeyBytes = rsaCipher.doFinal(encryptedKeyBytes);

      return new SecretKeySpec(decryptedKeyBytes, "AES");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      throw new AssertionError(e);
    } catch (NoSuchProviderException e) {
      throw new AssertionError(e);
    }
  }

  public Cipher makeAesCipher(SecretKey secretKey, byte[] iv) {
    try {
      Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SC");
      IvParameterSpec ivSpec = new IvParameterSpec(iv);

      aesCipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
      return aesCipher;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    } catch (NoSuchProviderException e) {
      throw new AssertionError(e);
    }
  }
}
