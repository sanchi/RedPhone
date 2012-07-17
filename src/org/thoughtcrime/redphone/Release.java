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

/**
 * Release-specific variables.
 *
 * @author Moxie Marlinspike
 *
 */
public interface Release {
  public static final boolean SSL                     = true;
  public static final boolean DEBUG                   = false;
  public static final boolean INTERNATIONAL           = true;
  public static final boolean DELIVER_DIAGNOSTIC_DATA = false;
  public static final String  SERVER_ROOT             = ".whispersystems.org";
  public static final String MASTER_SERVER_HOST       = "master.whispersystems.org";
  public static final String RELAY_SERVER_HOST        = "relay.whispersystems.org";
  public static final int     SERVER_PORT             = 31337;
}