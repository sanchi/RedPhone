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

import java.util.HashMap;
import java.util.Map;

/**
 * Counts events
 *
 * @author Stuart O. Anderson
 */
public class CountMetric implements SampledMetrics {
  private Map<String, Object> accum = new HashMap<String, Object>(1);

  @Override
  public synchronized Map<String, Object> sample() {
    HashMap<String, Object> result = new HashMap<String, Object>(accum);
    accum.clear();
    return result;
  }

  public synchronized void increment(String key, int amount) {
    Integer v = (Integer) accum.get(key);
    if (v == null) {
      accum.put(key, amount);
    } else {
      accum.put(key, v + amount);
    }
  }
}
