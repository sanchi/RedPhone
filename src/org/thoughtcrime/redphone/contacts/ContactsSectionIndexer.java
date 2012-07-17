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

package org.thoughtcrime.redphone.contacts;

import android.database.Cursor;
import android.widget.SectionIndexer;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Indexer for contact list.
 *
 * @author Moxie Marlinspike
 *
 */

public class ContactsSectionIndexer implements SectionIndexer {

  private final String[] sections;
  private final Integer[] positions;

    public ContactsSectionIndexer(Cursor cursor, String columnName) {
      List<String> sections   = new LinkedList<String>();
      List<Integer> positions = new LinkedList<Integer>();

      if (cursor != null) {
        char lastSection = ' ';
        int i            = 0;

        while (cursor.moveToNext()) {
          String item = cursor.getString(cursor.getColumnIndex(columnName)).trim().toUpperCase();

          if (item.length() > 0) {
            char firstChar = item.charAt(0);
            if (firstChar != lastSection) {
              sections.add(firstChar+"");
              positions.add(i);
              lastSection = firstChar;
            }
          }

          i++;
        }
      }

      this.sections = sections.toArray(new String[0]);
      this.positions = positions.toArray(new Integer[0]);
    }

    public Object[] getSections() {
        return sections;
    }

    public int getPositionForSection(int section) {
        if (section < 0 || section >= sections.length) {
            return -1;
        }

        return positions[section];
    }

    public int getSectionForPosition(int position) {
        if (position < 0) {
            return -1;
        }

        int index = Arrays.binarySearch(this.positions, position);

        /*
         * Consider this example: section positions are 0, 3, 5; the supplied
         * position is 4. The section corresponding to position 4 starts at
         * position 3, so the expected return value is 1. Binary search will not
         * find 4 in the array and thus will return -insertPosition-1, i.e. -3.
         * To get from that number to the expected value of 1 we need to negate
         * and subtract 2.
         */
        return index >= 0 ? index : -index - 2;
    }
}
