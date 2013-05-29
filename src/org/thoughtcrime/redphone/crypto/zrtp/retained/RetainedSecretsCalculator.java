package org.thoughtcrime.redphone.crypto.zrtp.retained;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public abstract class RetainedSecretsCalculator {

  protected final RetainedSecrets            retainedSecrets;
  protected final RetainedSecretsDerivatives retainedSecretsDerivatives;

  public RetainedSecretsCalculator(String role, RetainedSecrets retainedSecrets) {
    this.retainedSecrets            = retainedSecrets;
    this.retainedSecretsDerivatives = calculateDerivatives(role, retainedSecrets);
  }

  public RetainedSecretsDerivatives getRetainedSecretsDerivatives() {
    return retainedSecretsDerivatives;
  }

  private RetainedSecretsDerivatives calculateDerivatives(String role, RetainedSecrets retainedSecrets) {
    byte[] rs1   = retainedSecrets.getRetainedSecretOne();
    byte[] rs2   = retainedSecrets.getRetainedSecretTwo();

    byte[] rs1ID = null;
    byte[] rs2ID = null;

    if (rs1 != null) rs1ID = calculateDerivative(role, rs1);
    if (rs2 != null) rs2ID = calculateDerivative(role, rs2);

    return new RetainedSecretsDerivatives(rs1ID, rs2ID);
  }

  private byte[] calculateDerivative(String role, byte[] secret) {
    try {
      Mac mac  = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));

      byte[] derivative = mac.doFinal(role.getBytes("UTF-8"));
      byte[] truncated  = new byte[8];

      System.arraycopy(derivative, 0, truncated, 0, truncated.length);

      return truncated;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  public boolean hasContinuity(byte[] receivedRs1ID, byte[] receivedRs2ID) {
    return getS1(receivedRs1ID, receivedRs2ID) != null;
  }

  public abstract byte[] getS1(byte[] receivedRs1ID, byte[] receivedRs2ID);
}
