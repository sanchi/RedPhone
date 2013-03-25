package org.thoughtcrime.redphone.monitor;

import android.os.SystemClock;
import com.google.thoughtcrimegson.Gson;

import java.util.HashMap;
import java.util.Map;

/**
 * The call data log is a sequence of MonitoredEvent instances
 */
public class MonitoredEvent {
  private final long timestamp = SystemClock.elapsedRealtime();
  private final Map<String, Object> values;

  MonitoredEvent(Map<String, Object> values) {
    this.values = new HashMap<>(values);
  }

  MonitoredEvent(String name, Object value) {
    values = new HashMap<>();
    values.put(name, value);
  }
}
