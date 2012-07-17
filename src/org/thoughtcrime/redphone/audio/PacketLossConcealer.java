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

/**
 * Interface to the native packet loss concealment code that stretches and shrinks audio
 * without changing pitch.
 *
 * @author Stuart O. Anderson
 */
public class PacketLossConcealer {
  static {
    System.loadLibrary( "redspeex" );
    init();
  }

  private static native void init();
  public static native void rx( short audio[] );
  public static native void fillIn( short audio[] );

  public static native int maxOutputLengthAtSpeed( int nSamples, float rate );
  public static native int changeSpeed( short output[], short input[], int inputLen, float rate );
}
