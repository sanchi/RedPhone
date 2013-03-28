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

package org.thoughtcrime.redphone.signaling;

import android.os.Parcel;
import android.os.Parcelable;
import org.thoughtcrime.redphone.Release;

/**
 * A helper tuple that encapsulates both a call's session ID
 * and remote UDP port.
 *
 * @author Moxie Marlinspike
 *
 */

public class SessionDescriptor implements Parcelable {
  public int relayPort;
  public long sessionId;
  public String serverName;

  public SessionDescriptor() {}

  public SessionDescriptor(String serverName, int relayPort, long sessionId) {
    this.serverName = serverName;
    this.relayPort  = relayPort;
    this.sessionId  = sessionId;
  }

  public SessionDescriptor(Parcel in) {
    this.relayPort  = in.readInt();
    this.sessionId  = in.readLong();
    this.serverName = in.readString();
  }

  public String getFullServerName() {
    //return serverName + Release.SERVER_ROOT;
	  return "192.168.2.23"; //DNC
  }

  @Override
  public boolean equals(Object other) {
    if (other == null)                         return false;
    if (!(other instanceof SessionDescriptor)) return false;

    SessionDescriptor that = (SessionDescriptor)other;

    return this.relayPort == that.relayPort &&
           this.sessionId == that.sessionId &&
           this.serverName.equals(that.serverName);
  }

  @Override
  public int hashCode() {
    return this.relayPort ^ ((int)this.sessionId) ^ this.serverName.hashCode();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(relayPort);
    dest.writeLong(sessionId);
    dest.writeString(serverName);
  }

  public static final Parcelable.Creator<SessionDescriptor> CREATOR =
    new Parcelable.Creator<SessionDescriptor>()
  {
    public SessionDescriptor createFromParcel(Parcel in) {
      return new SessionDescriptor(in);
    }

    public SessionDescriptor[] newArray(int size) {
      return new SessionDescriptor[size];
    }
  };
}
