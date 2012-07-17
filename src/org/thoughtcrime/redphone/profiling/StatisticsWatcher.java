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
 * Utility class that tracks statistics of a sequence of observations
 *
 * @author Stuart O. Anderson
 */
public class StatisticsWatcher {
  private float avgSize = 0;
  private float avgVar = 0;
  private float w = .05f;
  public String debugName = "default";
  private boolean doPrintDebug = false;
  private boolean doReset = false;
  private float trueAverage = 0;
  private int nSample = 0;
  public void reset() {
    nSample = 0;
  }
  public void setW( float val ) {
    w = val;
  }
  public void setAvg( float val ) {
    avgSize = val;
  }
  public void observeValue( int currentSize ) {
    avgSize = (currentSize-avgSize)*w + avgSize;
    float d = (currentSize - avgSize);
    avgVar  = avgVar  * (1-w) + d*d         * w;

    nSample++;
    trueAverage = (nSample-1)/(float)nSample * trueAverage + 1.0f/nSample * currentSize;

    if( pt.periodically() && doPrintDebug ) {
      Log.d( "StatsWatcher", "[" + debugName + "] avg: " + avgSize + " stddev: " + Math.sqrt(avgVar) + " trueAvg=" + trueAverage );
    }


  }

  private PeriodicTimer pt = new PeriodicTimer(5000);

  public float getAvgBufferSize() {

    return avgSize;
  }

  public float getAvgBufferVar() {
    return avgVar;
  }

  public float getTrueAverage() {
    return trueAverage;
  }


  public void setPrintDebug(boolean b) {
    doPrintDebug = b;
  }
}

