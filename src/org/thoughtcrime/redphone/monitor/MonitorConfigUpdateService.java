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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads a new call metrics configuration
 */
public class MonitorConfigUpdateService extends Service {
  ExecutorService executor = Executors.newSingleThreadExecutor();
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, final int startId) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        MonitorConfigUpdateReceiver.maybeUpdateConfig(MonitorConfigUpdateService.this);
        stopSelf(startId);
      }
    });
    return START_NOT_STICKY;
  }
}
