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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Provides synchronized logging from several {@link TimeProfiler} instances.
 *
 * Periodically writes to log a summary of how much time has been spent executing in each
 * of several named contexts.  Multiple contexts can be active simultaneously.
 *
 * @author Stuart O. Anderson
 */
public class TimeProfiler extends Thread {
  private static List<ProfilingTimer> timers = new LinkedList<ProfilingTimer>();
  private static ProfilingTimer printTimer = new ProfilingTimer("PrintTimer");
  private static PeriodicTimer outputTimer = new PeriodicTimer(3000);
  private static HashMap<String, ProfilingTimer> timerHash = new HashMap<String, ProfilingTimer>();
  private static boolean debug = false;
  private static boolean running;

  public static void registerTimer(ProfilingTimer timer) {
    timers.add(timer);
    if (timerHash.get(timer.getName()) == null) {
      timerHash.put(timer.getName(), timer);
    }
    Log.d("TimeProfiler", "registered " + timer.getName() + " total: "
        + timers.size());
  }

  synchronized public static void startBlock(String name) {
    if( !debug ) return;
    ProfilingTimer timer = timerHash.get(name);
    if (timer == null) {
      timer = new ProfilingTimer(name);
      registerTimer(timer);
    }
    timer.start();
  }

  synchronized public static void stopBlock( String name ) {
    if( !debug )return;
    ProfilingTimer timer = timerHash.get(name);
    if (timer == null) {
      timer = new ProfilingTimer(name);
      registerTimer(timer);
    }
    timer.stop();
  }


  @Override
  public void run() {
    if( !debug ) return;
    registerTimer(printTimer);
    running = true;
    printTimer.start();
    while (running) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
      }
      printTiming();
    }
  }

  public static void printTiming() {
    if (!outputTimer.periodically())
      return;
    Log.d("TimeProfiler", "Timing Results");
    Iterator<ProfilingTimer> i = timers.iterator();
    while (i.hasNext()) {
      ProfilingTimer p = (ProfilingTimer) i.next();
      p.print(printTimer.getAccumTime());
    }
  }

  public static void terminate() {
    TimeProfiler.running = false;
  }
}
