/*
 * Copyright (C) 2013 Open Whisper Systems
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

package org.thoughtcrime.redphone.monitor;

import java.io.File;
import java.io.IOException;

/**
 * Interface to the CallData stored on disk.
 *
 * @author Stuart O. Anderson
 */
public interface CallData {
  /**
   * Add this name/value pair to the call data.
    * @param name
   * @param value
   */
  void putNominal(String name, Object value);

  /**
   * Add this event to the call data.
   * @param event
   */
  void addEvent(MonitoredEvent event);

  /**
   * Finishes writing the CallData object to disk.
   * @return
   * @throws IOException
   */
  File finish() throws IOException;
}
