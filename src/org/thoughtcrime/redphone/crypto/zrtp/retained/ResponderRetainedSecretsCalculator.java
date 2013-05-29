package org.thoughtcrime.redphone.crypto.zrtp.retained;

import java.util.Arrays;

public class ResponderRetainedSecretsCalculator extends RetainedSecretsCalculator {

  private static final String ROLE = "Responder";

  public ResponderRetainedSecretsCalculator(RetainedSecrets retainedSecrets) {
    super(ROLE, retainedSecrets);
  }

  @Override
  public byte[] getS1(byte[] rs1IDi, byte[] rs2IDi) {
    InitiatorRetainedSecretsCalculator calculator = new InitiatorRetainedSecretsCalculator(retainedSecrets);
    RetainedSecretsDerivatives derivatives        = calculator.getRetainedSecretsDerivatives();

    byte[] rs1IDr = derivatives.getRetainedSecretOneDerivative();
    byte[] rs2IDr = derivatives.getRetainedSecretTwoDerivative();

    if (rs1IDr != null && Arrays.equals(rs1IDi, rs1IDr)) return retainedSecrets.getRetainedSecretOne();
    if (rs2IDr != null && Arrays.equals(rs1IDi, rs2IDr)) return retainedSecrets.getRetainedSecretTwo();
    if (rs1IDr != null && Arrays.equals(rs2IDi, rs1IDr)) return retainedSecrets.getRetainedSecretOne();
    if (rs2IDr != null && Arrays.equals(rs2IDi, rs2IDr)) return retainedSecrets.getRetainedSecretTwo();

    return null;
  }
}
