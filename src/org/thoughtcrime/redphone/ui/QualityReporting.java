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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import org.thoughtcrime.redphone.R;
import org.thoughtcrime.redphone.audio.CallLogger;
import org.thoughtcrime.redphone.profiling.PacketLogger;
import org.thoughtcrime.redphone.util.LogUtil;

import java.util.ArrayList;

/**
 * Utilities for reporting call quality data
 *
 * @author Stuart O. Anderson
 */
public class QualityReporting {
  public static void sendDiagnosticData(final Activity ctx) {

    AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
    builder.setMessage( R.string.QualityReporting_would_you_like_to_send_diagnostic_information_about_this_call_to_whisper_systems );
    builder.setCancelable(false);
    builder.setNegativeButton( R.string.QualityReporting_never, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        ApplicationPreferencesActivity.setAskUserToSendDiagnosticData( ctx, false );
        ctx.finish();
      }
    });
    builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        deliverTimingData(ctx);
        ctx.finish();
      }
    });
    builder.setNeutralButton(android.R.string.no, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        ctx.finish();
      }
    });
    builder.setTitle( R.string.QualityReporting_send_diagnostics );
    builder.setIcon( android.R.drawable.ic_dialog_alert );
    builder.show();
  }


  public static void deliverTimingData(Context ctx) {
    Intent sendIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
    sendIntent.setType("application/octet-stream");
    sendIntent.putExtra(Intent.EXTRA_EMAIL,
            new String[] {"info@whispersys.com"});
    sendIntent.putExtra(Intent.EXTRA_TEXT,
            "Attached is RedPhone timing and log data...");
    sendIntent.putExtra(Intent.EXTRA_SUBJECT, "RedPhone Timing Data");

    Uri timingAttachmentLocation = LogUtil.copyDataToSdCard(ctx, CallLogger.TIMING_DATA_FILENAME);
    Uri packetAttachmentLocation = LogUtil.copyDataToSdCard(ctx, PacketLogger.PACKET_DATA_FILENAME);
    Uri logAttachmentLocation = LogUtil.generateCompressedLogFile();

    //has to be an ArrayList
    ArrayList<Uri> uris = new ArrayList<Uri>();

    if( timingAttachmentLocation != null ) {
      uris.add( timingAttachmentLocation );
    }
    if( packetAttachmentLocation != null ) {
      uris.add( packetAttachmentLocation );
    }
    if( logAttachmentLocation != null ) {
      uris.add( logAttachmentLocation );
    }

    sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
    ctx.startActivity(Intent.createChooser(sendIntent, "Send Email:"));
  }
}
