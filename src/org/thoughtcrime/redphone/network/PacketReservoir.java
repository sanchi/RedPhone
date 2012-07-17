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

package org.thoughtcrime.redphone.network;

import android.util.Log;

import java.net.DatagramPacket;
import java.util.LinkedList;

/**
 * Keeps a pool of byte arrays that are used to construct DatagramPackets.  Recycles buffers
 * that are no longer in use
 *
 * @author Stuart O. Anderson
 */
 //TODO(Stuart Anderson): Refactor to use {@link org.thoughtcrime.redphone.util.Pool}
 //TODO(Stuart Anderson): Test if removing this results in a noticeable performance loss.

public class PacketReservoir {
  private int bufferSize;
  private int totalAllocatedBuffers = 0;
  private LinkedList<byte []> buffers = new LinkedList<byte[]>();
  public PacketReservoir( int bufferSize ) {
    this.bufferSize = bufferSize;
  }

  public synchronized DatagramPacket getPacket() {
    if( buffers.size() != 0 ) {
      return new DatagramPacket( buffers.removeFirst(), bufferSize );
    }

    totalAllocatedBuffers++;
    Log.d("PacketReservoir", "enlarging packet reservoir, new size = " + totalAllocatedBuffers );
    byte [] newBuffer = new byte [bufferSize];
    return new DatagramPacket( newBuffer, bufferSize );
  }

  public synchronized void returnPacket( DatagramPacket packet ) {
    byte [] returnedBuffer = packet.getData();
    if( returnedBuffer.length != bufferSize ) {
      Log.e( "PacketReservoir", "returned a mis-sized buffer: " + returnedBuffer.length );
      return;
    }
    buffers.add( returnedBuffer );
  }
}
