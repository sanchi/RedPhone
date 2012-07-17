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

import android.util.Log;

/**
 * Timer utility used for profiling the audio pipeline
 *
 * @author Stuart O. Anderson
 */
public class ProfilingTimer {
  private long startTime = 0;
  private long accumTime = 0;
  private boolean running = false;
  private String name;

  public ProfilingTimer(String name) {
    this.name = name;
  }

  public void start() {
    running = true;
    startTime = System.currentTimeMillis();
  }

  public void stop() {
    if( running == false ) return;
    running = false;
    accumTime += System.currentTimeMillis() - startTime;
  }

  public double getAccumTime() {
    if( running ) {
      stop();
      start();
    }
    return accumTime/1000.0;
  }

  public void reset() {
    running = false;
    accumTime = 0;
  }

  public void print(double totalTime) {
    Log.d("TimeProfiler", name + ": pct:" + getAccumTime() / totalTime + " total:" + getAccumTime());
  }

  public void restart() {
    reset();
    start();
  }

  public String getName() {
    return name;
  }
}
