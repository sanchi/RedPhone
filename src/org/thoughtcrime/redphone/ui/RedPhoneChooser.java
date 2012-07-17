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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.thoughtcrime.redphone.Constants;
import org.thoughtcrime.redphone.R;
import org.thoughtcrime.redphone.RedPhone;
import org.thoughtcrime.redphone.RedPhoneService;
import org.thoughtcrime.redphone.call.CallListener;

/**
 * A lightweight dialog for prompting the user to upgrade their outgoing call.
 *
 * @author Moxie Marlinspike
 *
 */
public class RedPhoneChooser extends Activity {

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    initializeResources();
  }

  private void initializeResources() {
    final String number = getIntent().getStringExtra(Constants.REMOTE_NUMBER);

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIcon(R.drawable.redphone_icon);
    builder.setTitle("Upgrade to RedPhone?");
    builder.setMessage("This contact also uses RedPhone. " +
                       "Would you like to upgrade to a secure call?");

    builder.setPositiveButton("Secure Call", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Intent intent = new Intent();
        intent.setClass(RedPhoneChooser.this, RedPhoneService.class);
        intent.putExtra(Constants.REMOTE_NUMBER, number);
        startService(intent);

        Intent activityIntent = new Intent();
        activityIntent.setClass(RedPhoneChooser.this, RedPhone.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(activityIntent);

        finish();
      }
    });

    builder.setNegativeButton("Insecure Call", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Intent intent = new Intent("android.intent.action.CALL",
                                   Uri.fromParts("tel", getIntent()
                                                        .getStringExtra(Constants.REMOTE_NUMBER) +
                                                        CallListener.IGNORE_SUFFIX, null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
      }
    });

    builder.show();
  }
}
