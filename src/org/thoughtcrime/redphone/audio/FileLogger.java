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
  protected PeriodicTimer pt = new PeriodicTimer(5000);
  private OutputStream debugOutput;

  public FileLogger( String fileName ) {
    if( Release.DELIVER_DIAGNOSTIC_DATA ) {
      try {
        debugOutput = new BufferedOutputStream(ApplicationContext
            .getInstance().getContext().openFileOutput(
              fileName, Context.MODE_WORLD_READABLE));
        Log.d( TAG, "Writing debug output to: " + Environment.getDataDirectory());
      } catch (FileNotFoundException e) {
        Util.dieWithError(e);
      }
    }
  }

  public void writeLine( final String line ) {
    if( !Release.DELIVER_DIAGNOSTIC_DATA ) return;
    try {
      debugOutput.write( line.getBytes() );
    } catch (IOException e) {
      Util.dieWithError(e);
    }
  }

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
