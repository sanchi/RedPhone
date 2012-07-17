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

package org.thoughtcrime.redphone.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.thoughtcrime.redphone.ApplicationContext;

/**
 * Phone number formats are a pain.
 *
 * @author Moxie Marlinspike
 *
 */
public class PhoneNumberFormatter {

  public static boolean isValidNumber(String number) {
    return number.startsWith("+") && !number.contains(".") &&
          !number.contains("-") && !number.contains(" ") && number.length() >= 12;
  }

  public static String formatNumber(Context context, String number) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    String localNumber            = preferences.getString("Number", "No Stored Number");
    number                        = number.replaceAll("[^0-9+]", "");

    if (number.charAt(0) == '+')
      return number;

    if (localNumber.charAt(0) == '+')
      localNumber = localNumber.substring(1);

    if (localNumber.length() == number.length() || number.length() > localNumber.length())
      return "+" + number;

    int difference = localNumber.length() - number.length();

    return "+" + localNumber.substring(0, difference) + number;
  }

  public static String formatNumber(String number) {
    return formatNumber(ApplicationContext.getInstance().getContext(), number);
  }

}
