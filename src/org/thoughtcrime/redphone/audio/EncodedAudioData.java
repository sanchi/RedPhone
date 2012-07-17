/**
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
 * Stores encoded (not encrypted) audio data along with sequence information from the encoding
 * stream and the packet stream this data arrived in.
 *
 * @author Stuart O. Anderson
 */
//TODO(Stuart Anderson): Should know how to decode and encode itself.
public class EncodedAudioData implements Comparable<EncodedAudioData>{
  public byte[] data;
  public long sequenceNumber;
  public long sourceSequenceNumber;

  public EncodedAudioData( byte data[], long sequenceNumber, long sourceSequenceNumber ) {
    this.data = data;
    this.sequenceNumber = sequenceNumber;
    this.sourceSequenceNumber = sourceSequenceNumber;
  }

  public int compareTo(EncodedAudioData data) {
    if( sequenceNumber < data.sequenceNumber )
      return -1;
    if( sequenceNumber > data.sequenceNumber )
      return 1;
    return 0;
  }

  public boolean equals(EncodedAudioData data) {
    if( data == null ) return false;
    return sequenceNumber == data.sequenceNumber;
  }

  @Override
  public int hashCode() {
    return (int)sequenceNumber;
  }
}