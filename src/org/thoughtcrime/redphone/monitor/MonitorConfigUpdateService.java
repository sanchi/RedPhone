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
