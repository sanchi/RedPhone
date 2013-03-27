package org.thoughtcrime.redphone.signaling;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores data included with the response sent when the relay connection is made.
 */
public class RelayOpenResponse {
  private List<String> callQualityQuestions;

  public RelayOpenResponse() {
    callQualityQuestions = new ArrayList<String>(0);
  }

  public List<String> getCallQualityQuestions() {
    return callQualityQuestions;
  }
}
