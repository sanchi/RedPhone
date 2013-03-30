package org.thoughtcrime.redphone.monitor.stream;

import android.content.res.Resources;
import android.test.AndroidTestCase;
import org.thoughtcrime.redphone.tests.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;

public class EncryptedStreamTest extends AndroidTestCase {

  static {
    Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
  }

  public void testSetup() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);

    Resources testResources = getContext().getPackageManager().getResourcesForApplication("org.thoughtcrime.redphone.tests");
    PublicKey publicKey = EncryptedStreamUtils.getPublicKeyFromResource(testResources, R.raw.test_pub);
    EncryptedOutputStream eos = new EncryptedOutputStream(baos, publicKey);

    String secretPayload = "Secret Yield";
    eos.write(secretPayload.getBytes("UTF8"));
    eos.close();
    byte[] encryptedData = baos.toByteArray();

    InputStream testInput = new ByteArrayInputStream(encryptedData);

    PrivateKey privateKey = EncryptedStreamUtils.getPrivateKeyFromResource(testResources, R.raw.test_pair);
    InputStream eis = new EncryptedInputStream(testInput, privateKey);

    DataInputStream dis = new DataInputStream(eis);
    byte[] buf = new byte[secretPayload.length()];
    dis.readFully(buf);

    String result = new String(buf, "UTF8");
    assertEquals(result, secretPayload);
  }

}
