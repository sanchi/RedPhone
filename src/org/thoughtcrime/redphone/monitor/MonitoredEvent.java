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

package org.thoughtcrime.redphone.monitor;

import android.os.SystemClock;

import java.util.HashMap;
import java.util.Map;

/**
 * The call data log is a sequence of MonitoredEvent instances
 *
 * @author Stuart O. Anderson
 */
public class MonitoredEvent {
  private final long timestamp = SystemClock.elapsedRealtime();
  private final Map<String, Object> values;

  MonitoredEvent(Map<String, Object> values) {
    this.values = new HashMap<String, Object>(values);
  }

  MonitoredEvent(String name, Object value) {
    values = new HashMap<String, Object>();
    values.put(name, value);
  }
}
