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

package org.thoughtcrime.redphone.c2dm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BroadcastReceiver which handles C2DM registration events.
 * We'll pass these on to the service to complete.
 *
 * @author Moxie Marlinspike
 *
 */
public class C2DMRegistrationReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w("C2DMRegistrationReceiver", "C2DM Receiver got intent: " + intent);
    String registration = intent.getStringExtra("registration_id");

    if (intent.getStringExtra("error") != null) {
      handleError(context, intent);
    } else if (intent.getStringExtra("unregistered") != null) {
      handleUnregistered(context, intent);
      } else if (registration != null) {
        handleRegistered(context, intent);
      }
  }

  private void handleRegistered(Context context, Intent i) {
    startServiceWithAction(context, i, C2DMRegistrationService.REGISTRATION_COMPLETE_ACTION);
  }

  private void handleUnregistered(Context context, Intent i) {
    startServiceWithAction(context, i, C2DMRegistrationService.UNREGISTRATION_COMPLETE_ACTION);
  }

  private void handleError(Context context, Intent i) {
    startServiceWithAction(context, i, C2DMRegistrationService.ERROR_ACTION);
  }

  private void startServiceWithAction(Context context, Intent i, String action) {
    Intent intent = new Intent(context, C2DMRegistrationService.class);
    intent.setAction(action);
    intent.putExtras(i.getExtras());
    context.startService(intent);
  }

}
