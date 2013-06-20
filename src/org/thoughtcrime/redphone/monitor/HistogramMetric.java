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
 * Tracks a counts over bins
 *
 * @author Stuart O. Anderson
 */
public class HistogramMetric implements SampledMetrics {
  private final int min;
  private final int range;
  private final Map<String, Object> result = new HashMap<String, Object>(2);
  private final int[] counts;
  private final int[] outputCounts;
  private int outOfRangeCount;

  public HistogramMetric(int min, int max, int bins) {
    this.min = min;
    this.range = max - min;
    counts = new int[bins];
    outputCounts = new int[bins];
  }

  @Override
  public Map<String, Object> sample() {
    outOfRangeCount = 0;

    System.arraycopy(counts, 0, outputCounts, 0, counts.length);

    for (int i = 0; i < counts.length; i++) {
      counts[i] = 0;
    }

    result.put("counts", outputCounts);
    result.put("out-of-range", outOfRangeCount);
    result.put("min", min);
    result.put("range", range);

    return result;
  }

  /**
   * Careful not to overflow int representation here....
   * @param value
   */
  public void addEvent(int value) {
    int bin = ((value - min) * counts.length) / (range + 1);
    if(bin < 0 || bin >= counts.length) {
      outOfRangeCount++;
    } else {
      counts[bin]++;
    }
  }
}
