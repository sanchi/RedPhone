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

package org.thoughtcrime.redphone.crypto;

/**
 * A class for managing RTP sequence numbers, wraparound, etc.
 *
 * @author Moxie Marlinspike
 *
 */

public class SequenceCounter {

  private static final int LOW_ROLLOVER_THRESHOLD  = 1000;
  private static final int HIGH_ROLLOVER_THRESHOLD = 64535;

  private long lastLogicalSequenceNumber;
  private long lastPhysicalSequenceNumber;
  private long rolloverCount;

  private boolean isRollover(long physicalSequenceNumber) {
    return
      (physicalSequenceNumber < lastPhysicalSequenceNumber)  &&
      (lastPhysicalSequenceNumber > HIGH_ROLLOVER_THRESHOLD) &&
      (physicalSequenceNumber < LOW_ROLLOVER_THRESHOLD);
  }

  private boolean isHoldover(long physicalSequenceNumber) {
    return
      (physicalSequenceNumber > lastPhysicalSequenceNumber) &&
      (lastPhysicalSequenceNumber < LOW_ROLLOVER_THRESHOLD) &&
      (physicalSequenceNumber > HIGH_ROLLOVER_THRESHOLD);
  }

  private long logicalIndexFor(long rollover, long sequence) {
    return (rollover * (2^16)) + sequence;
  }

  private long logicalIndexForRollover(long physicalSequenceNumber) {
    rolloverCount++;
    return logicalIndexFor(rolloverCount, physicalSequenceNumber);
  }

  private long logicalIndexForHoldover(long physicalSequenceNumber) {
    long previousRollover = rolloverCount - 1;
    previousRollover      = (previousRollover < 0) ? 0 : previousRollover;

    return logicalIndexFor(previousRollover, physicalSequenceNumber);
  }

  private long logicalIndexForSequence(long physicalSequenceNumber) {
    if (physicalSequenceNumber > lastPhysicalSequenceNumber)
      lastPhysicalSequenceNumber = physicalSequenceNumber;

    return logicalIndexFor(rolloverCount, physicalSequenceNumber);
  }

  public void updateSequence(SecureRtpPacket packet) {
    long physicalSequenceNumber = packet.getSequenceNumber();

    if (isRollover(physicalSequenceNumber)) {
      packet.setLogicalSequence(logicalIndexForRollover(physicalSequenceNumber));
    } else if (isHoldover(physicalSequenceNumber)) {
      packet.setLogicalSequence(logicalIndexForHoldover(physicalSequenceNumber));
    } else {
      packet.setLogicalSequence(logicalIndexForSequence(physicalSequenceNumber));
    }
  }

}
