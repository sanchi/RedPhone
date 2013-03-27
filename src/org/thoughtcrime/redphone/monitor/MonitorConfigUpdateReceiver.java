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

package org.thoughtcrime.redphone.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.http.AndroidHttpClient;
import android.util.Log;
import com.google.thoughtcrimegson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.thoughtcrime.redphone.Release;
import org.thoughtcrime.redphone.ui.ApplicationPreferencesActivity;
import org.thoughtcrime.redphone.util.PeriodicActionUtils;

import java.io.IOException;
import java.io.InputStreamReader;

/**
 * A broadcast receiver that is responsible for scheduling and handling notifications
 * for periodic monitor config update events.
 *
 * @author Stuart O. Anderson
 * @author Moxie Marlinspike
 *
 */

public class MonitorConfigUpdateReceiver extends BroadcastReceiver {
  private static final Gson gson = new Gson();
  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w("MonitorConfigUpdateReceiver", "Initiating scheduled monitor config update...");

    maybeUpdateConfig(context);
  }

  public static void maybeUpdateConfig(Context context) {
    AndroidHttpClient client = AndroidHttpClient.newInstance("RedPhone");
    try {
      String uri = String.format("http://%s/collector/call_quality_questions", Release.DATA_COLLECTION_SERVER_HOST);
      HttpGet getRequest = new HttpGet(uri);

      HttpResponse response = client.execute(getRequest);
      InputStreamReader jsonReader = new InputStreamReader(response.getEntity().getContent());
      CallQualityConfig config = gson.fromJson(jsonReader, CallQualityConfig.class);
      ApplicationPreferencesActivity.setCallQualityConfig(context, config);
    } catch (IOException e) {
      Log.d("MonitorConfigUpdateReceiver", "update failed", e);
    } catch (Exception e) {
      Log.e("MonitorConfigUpdateReceiver", "update error", e);
    } finally {
      client.close();
    }

    PeriodicActionUtils.scheduleUpdate(context, MonitorConfigUpdateReceiver.class);
  }

}
