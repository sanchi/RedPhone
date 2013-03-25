package org.thoughtcrime.redphone.monitor;

import java.io.File;
import java.io.IOException;

/**
 * Interface to the CallData stored on disk.
 */
public interface CallData {
  /**
   * Add this name/value pair to the call data.
    * @param name
   * @param value
   */
  void putNominal(String name, Object value);

  /**
   * Add this event to the call data.
   * @param event
   */
  void addEvent(MonitoredEvent event);

  /**
   * Finishes writing the CallData object to disk.
   * @return
   * @throws IOException
   */
  File finish() throws IOException;
}
