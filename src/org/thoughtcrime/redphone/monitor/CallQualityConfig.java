package org.thoughtcrime.redphone.monitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores information about call quality monitoring configuration
 */
public class CallQualityConfig {
  private final List<String> callQualityQuestions;

  public CallQualityConfig() {
    callQualityQuestions = new ArrayList<String>();
  }

  public List<String> getCallQualityQuestions() {
    return callQualityQuestions;
  }
}
