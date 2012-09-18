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

package org.thoughtcrime.redphone.ui;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.redphone.R;
import org.thoughtcrime.redphone.RedPhone;

/**
 * Manages the state of the RedPhone items in the Android notification bar.
 *
 * @author Moxie Marlinspike
 *
 */

public class StatusBarManager {

  private static final int RED_PHONE_NOTIFICATION = 313388;

  private final Context context;
  private final NotificationManager notificationManager;

  public StatusBarManager(Context context) {
    this.context             = context;
    this.notificationManager = (NotificationManager)context
                               .getSystemService(Context.NOTIFICATION_SERVICE);
  }

  public void setCallEnded() {
    notificationManager.cancel(RED_PHONE_NOTIFICATION);
  }

  public void setCallInProgress() {
    Intent contentIntent        = new Intent(context, RedPhone.class);
    contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, 0);
    String notificationText     = context.getString(R.string.StatusBarManager_redphone_call_in_progress);
    Notification notification   = new Notification(R.drawable.stat_sys_phone_call, null,
                                                   System.currentTimeMillis());

    notification.setLatestEventInfo(context, notificationText, notificationText, pendingIntent);
    notification.flags = Notification.FLAG_NO_CLEAR;
    notificationManager.notify(RED_PHONE_NOTIFICATION, notification);
  }
}
