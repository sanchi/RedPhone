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
