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

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;
import com.google.thoughtcrimegson.Gson;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * CallMonitor manages accumulation data and upload of call quality data.
 *
 * @author Stuart O. Anderson
 */
public class CallMonitor {
  private final List<Pair<String, SampledMetrics>> metrics = new ArrayList<Pair<String, SampledMetrics>>();
  private final CallData data;
  private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor();
  private final ScheduledFuture sampleFuture;

  public CallMonitor(Context context) {
    CallData data;
    try {
      data = new CallDataImpl(context);
    } catch (IOException e) {
      Log.e("CallMonitor", "Failed to create call data store", e);
      data = new CallDataMock();
    }
    this.data = data;

    Log.d("CallMonitor", "Scheduling periodic sampler");
    sampleFuture = sampler.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        sample();
      }
    }, 0, 10, TimeUnit.SECONDS);

    addSampledMetrics("system", new SystemMetrics());
  }

  public void addNominalValue(String name, Object value) {
    Log.d("CallMonitor", "Nominal: " + name + " = " + value);
    data.putNominal(name, value);
  }

  public EventStream addEventStream(final String name) {
    return new EventStream() {
      @Override
      public void emitEvent(String value) {
        data.addEvent(new MonitoredEvent(name, value));
      }
    };
  }

  public synchronized void addSampledMetrics(String name, SampledMetrics metrics) {
    this.metrics.add(Pair.create(name, metrics));
  }

  public synchronized void sample() {
    Log.d("CallMonitor", "Sampling now");
    Map<String, Object> datapoint = new HashMap<String, Object>();
    for (Pair<String, SampledMetrics> metric : metrics) {
      try {
        for (Map.Entry<String, Object> entry : metric.second.sample().entrySet()) {
          datapoint.put(metric.first + ":" + entry.getKey(), entry.getValue());
        }
      } catch (Exception e) {
        Log.e("CallMonitor", "A SampledMetric threw an uncaught exception", e);
      }
    }
    data.addEvent(new MonitoredEvent(datapoint));
  }

  /**
   * Finalize the on-disk JSON representation of the monitor data and starts the UploadService
   *
   * Calling this function more than once will result in an error.
   */
  public void startUpload(Context context, String callId) {
    try {
      Log.d("CallMonitor", "Shutting down call monitoring, starting upload process");
      sampleFuture.cancel(false);
      sampler.shutdown();
      if (!sampler.awaitTermination(1, TimeUnit.SECONDS)) {
        Log.e("CallMonitor", "Sampler didn't stop cleanly");
        return;
      }

      File datafile = data.finish();
      if (datafile == null) {
        return;
      }

      UploadService.beginUpload(context, callId, "call-metrics", datafile);
    } catch (IOException e) {
      Log.e("CallMonitor", "Failed to upload quality data", e);
    } catch (InterruptedException e) {
      Log.e("CallMonitor", "Interrupted trying to upload quality data", e);
    }
  }
}
