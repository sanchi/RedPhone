package org.thoughtcrime.redphone.crypto.zrtp.retained;

import java.util.Arrays;

public class InitiatorRetainedSecretsCalculator extends RetainedSecretsCalculator {

  private static final String ROLE = "Initiator";

  public InitiatorRetainedSecretsCalculator(RetainedSecrets retainedSecrets) {
    super(ROLE, retainedSecrets);
  }

  @Override
  public byte[] getS1(byte[] rs1IDr, byte[] rs2IDr) {
    ResponderRetainedSecretsCalculator calculator = new ResponderRetainedSecretsCalculator(retainedSecrets);
    RetainedSecretsDerivatives derivatives        = calculator.getRetainedSecretsDerivatives();

    byte[] rs1IDi = derivatives.getRetainedSecretOneDerivative();
    byte[] rs2IDi = derivatives.getRetainedSecretTwoDerivative();

    if (rs1IDr != null && Arrays.equals(rs1IDi, rs1IDr)) return retainedSecrets.getRetainedSecretOne();
    if (rs2IDr != null && Arrays.equals(rs1IDi, rs2IDr)) return retainedSecrets.getRetainedSecretOne();
    if (rs1IDr != null && Arrays.equals(rs2IDi, rs1IDr)) return retainedSecrets.getRetainedSecretTwo();
    if (rs2IDr != null && Arrays.equals(rs2IDi, rs2IDr)) return retainedSecrets.getRetainedSecretTwo();

    return null;
  }
}
