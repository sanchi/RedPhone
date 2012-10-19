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

package org.thoughtcrime.redphone.audio;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.thoughtcrime.redphone.ApplicationContext;
import org.thoughtcrime.redphone.Release;
import org.thoughtcrime.redphone.profiling.PeriodicTimer;
import org.thoughtcrime.redphone.util.Util;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * FileLogger writes lines of text to a file.  It knows where debug output files
 * should be stored, and it can disable logging in production releases.
 *
 * @author Stuart O. Anderson
 */
public class FileLogger {
  private static final String TAG = "FileLogger";
  private static final int MAX_LOGGED_EXCEPTIONS = 25;
  protected final PeriodicTimer pt = new PeriodicTimer(5000);
  private final OutputStream debugOutput;
  private int loggedExceptions;

  public FileLogger(Context context, String fileName) {
    debugOutput = null;
    if( Release.DELIVER_DIAGNOSTIC_DATA ) {
      try {
        FileOutputStream outputStream = context.openFileOutput(fileName, Context.MODE_WORLD_READABLE);
        debugOutput = new BufferedOutputStream(outputStream);
        Log.d(TAG, "Writing debug output to: " + Environment.getDataDirectory());
      } catch (IOException e) {
        Log.e(TAG, "Failed to create log file: " + fileName, e);
      }
    }
  }

  public void writeLine( final String line ) {
    if( !Release.DELIVER_DIAGNOSTIC_DATA
        || debugOutput == null) {
      return;
    }
    try {
      debugOutput.write( line.getBytes() );
    } catch (IOException e) {
      if(loggedExceptions < MAX_LOGGED_EXCEPTIONS) {
        loggedExceptions++;
        Log.w(TAG, e);
      }
    }
  }

  //TODO(Stuart Anderson): Should be safe to remove this function entirely.
  public void terminate() {
    if( debugOutput != null ) {
      try {
        //TODO(Stuart Anderson): Pretty sure we don't need to flush() here.
        debugOutput.flush();
        debugOutput.close();
      } catch (IOException e) {
      }
    }
  }
}
