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

import org.thoughtcrime.redphone.signaling.DirectoryResponse;
import org.thoughtcrime.redphone.signaling.SignalingException;
import org.thoughtcrime.redphone.signaling.SignalingSocket;
import org.thoughtcrime.redphone.util.PeriodicActionUtils;

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
      SignalingSocket signalingSocket = new SignalingSocket(context);
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

    PeriodicActionUtils.scheduleUpdate(context, DirectoryUpdateReceiver.class);
  }
}
