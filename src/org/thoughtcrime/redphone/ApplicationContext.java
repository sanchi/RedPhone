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

package org.thoughtcrime.redphone;

import android.content.Context;

import org.thoughtcrime.redphone.call.CallStateListener;

/**
 * A application-level singleton for stashing objects which
 * aren't singletons on their own, but need to be globally
 * accessible.
 *
 * @author Moxie Marlinspike
 *
 */
public class ApplicationContext {

  private static final ApplicationContext applicationContext = new ApplicationContext();

    public static ApplicationContext getInstance() {
    return applicationContext;
  }

  private Context context;
  private CallStateListener callStateListener;

  public void setContext(Context context) {
    this.context = context;
  }

  public void setCallStateListener(CallStateListener callStateListener) {
    this.callStateListener = callStateListener;
  }

  public CallStateListener getCallStateListener() {
    return callStateListener;
  }

  public Context getContext() {
    return context;
  }
}
