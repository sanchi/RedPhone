package org.thoughtcrime.redphone.monitor;

/**
 * A stream of discrete events emitted at different points in time.
 */
public interface EventStream {
  void emitEvent(String value);
}
