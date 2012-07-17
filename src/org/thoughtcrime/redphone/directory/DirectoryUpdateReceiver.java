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

package org.thoughtcrime.redphone.directory;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.thoughtcrime.redphone.Release;
import org.thoughtcrime.redphone.signaling.DirectoryResponse;
import org.thoughtcrime.redphone.signaling.SignalingException;
import org.thoughtcrime.redphone.signaling.SignalingSocket;

import java.util.Random;

/**
 * A broadcast receiver that is responsible for scheduling and handling notifications
 * for periodic directory update events.
 *
 * @author Moxie Marlinspike
 *
 */

public class DirectoryUpdateReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w("DirectoryUpdateReceiver", "Initiating scheduled directory update...");

    try {
      SharedPreferences preferences   = PreferenceManager.getDefaultSharedPreferences(context);
      String number                   = preferences.getString("Number", "NO_SAVED_NUMBER!");
      String password                 = preferences.getString("Password", "NO_SAVED_PASSWORD!");
      SignalingSocket signalingSocket = new SignalingSocket(context, Release.MASTER_SERVER_HOST,
                                                            Release.SERVER_PORT, number, password,
                                                            null);

      DirectoryResponse response      = signalingSocket.getNumberFilter();

      if (response != null) {
        NumberFilter filter = new NumberFilter(response.getFilter(), response.getHashCount());
        filter.serializeToFile(context);
      }
    } catch (SignalingException se) {
      Log.w("DirectoryUpdateReceiver", se);
    } catch (Exception e) {
      Log.w("DirectoryUpdateReceiver", e);
      return;
    }

    scheduleDirectoryUpdate(context);
  }

  public static void scheduleDirectoryUpdate(Context context) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    AlarmManager am               = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
    Intent intent                 = new Intent(context, DirectoryUpdateReceiver.class);
    PendingIntent sender          = PendingIntent.getBroadcast(context, 0, intent,
                                                               PendingIntent.FLAG_UPDATE_CURRENT);
    Random random                 = new Random(System.currentTimeMillis());
    long offset                   = random.nextLong() % (12 * 60 * 60* 1000);
    long interval                 = (24 * 60 * 60 * 1000) + offset;
    long scheduledTime            = preferences.getLong("pref_scheduled_directory_update", -1);

    if (scheduledTime == -1 || scheduledTime <= System.currentTimeMillis()) {
      scheduledTime = System.currentTimeMillis() + interval;
      preferences.edit().putLong("pref_scheduled_directory_update", scheduledTime).commit();
      Log.w("DirectoryUpdateReceiver", "Scheduling for all new time: " + scheduledTime);
    } else {
      Log.w("DirectoryUpdateReceiver", "Scheduling for time found in preferences: " +
            scheduledTime);
    }

    am.cancel(sender);
    am.set(AlarmManager.RTC_WAKEUP, scheduledTime, sender);

    Log.w("DirectoryUpdateReceiver", "Scheduled for: " + scheduledTime);
  }
}
