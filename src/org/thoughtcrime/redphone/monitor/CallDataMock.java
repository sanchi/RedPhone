package org.thoughtcrime.redphone.monitor;

import java.io.File;
import java.io.IOException;

/**
 * Mock CallData to use if when the regular implementation isn't available.
 */
public class CallDataMock implements CallData {
  @Override
  public void putNominal(String name, Object value) {
  }

  @Override
  public void addEvent(MonitoredEvent event) {
  }

  @Override
  public File finish() throws IOException {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
