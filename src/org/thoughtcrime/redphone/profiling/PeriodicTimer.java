/*
 * Copyright (C) 2011 Whisper Systems
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

package org.thoughtcrime.redphone.profiling;

import android.os.SystemClock;

/**
 * Allows periodic actions in a synchronous context.
 *
 * The periodically() method will return true at most once in each period,
 * where period is expressed in milliseconds.
 *
 * @author Stuart O. Anderson
 */
public class PeriodicTimer {
  private long last, period;
  private long lastPeriodActual;
  public PeriodicTimer( long period ) {
    this.period = period;
    last = SystemClock.uptimeMillis();
  }
  public long millisSinceLastPeriod() {
    long now = SystemClock.uptimeMillis();
    return now-last;
  }
  public boolean periodically() {
    long now = SystemClock.uptimeMillis();
    if( now > last + period ) {
      lastPeriodActual = now - last;
      last = now;
      return true;
    }
    return false;
  }

  public long getActualLastPeriod() {
    return lastPeriodActual;
  }

  public long getPeriod() {
    return period;
  }
}
