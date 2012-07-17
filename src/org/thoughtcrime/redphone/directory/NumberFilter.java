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

package org.thoughtcrime.redphone.directory;

import android.content.Context;
import android.util.Log;

import com.google.thoughtcrimegson.Gson;
import com.google.thoughtcrimegson.JsonParseException;
import com.google.thoughtcrimegson.annotations.SerializedName;

import org.thoughtcrime.redphone.util.Base64;
import org.thoughtcrime.redphone.util.PhoneNumberFormatter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * Handles providing lookups, serializing, and deserializing the RedPhone directory.
 *
 * @author Moxie Marlinspike
 *
 */

public class NumberFilter {

  private static final String DIRECTORY_FILE = "directory";

  private final BloomFilter bloomFilter;

  public NumberFilter(BloomFilter bloomFilter) {
    this.bloomFilter = bloomFilter;
  }

  public NumberFilter(byte[] numberFilter, int hashCount) {
    this.bloomFilter = new BloomFilter(numberFilter, hashCount);
  }

  public boolean containsNumber(Context context, String number) {
    if (bloomFilter == null) return false;
    else 					           return bloomFilter.contains(PhoneNumberFormatter.formatNumber(context,
                                                                                           number));
  }

  public void serializeToFile(Context context) {
    if (this.bloomFilter == null)
      return;

    try {
      FileOutputStream fout       = context.openFileOutput(DIRECTORY_FILE, 0);
      String numberFilter         = Base64.encodeBytes(bloomFilter.getFilter());
      NumberFilterStorage storage = new NumberFilterStorage(numberFilter,
                                                            bloomFilter.getHashCount());

      storage.serializeToStream(fout);
      fout.close();
    } catch (IOException ioe) {
      Log.w("NumberFilter", ioe);
    }
  }

  public static NumberFilter deserializeFromFile(Context context) {
    try {
      FileInputStream fis         = context.openFileInput(DIRECTORY_FILE);
      NumberFilterStorage storage = NumberFilterStorage.fromStream(fis);
      return new NumberFilter(new BloomFilter(Base64.decode(storage.getFilterData()),
                                              storage.getHashCount()));
    } catch (IOException ioe) {
      Log.w("NumberFilter", ioe);
      return new NumberFilter(null);
    }
  }

  private static class NumberFilterStorage {
    @SerializedName("filter_data")
    private String filterData;

    @SerializedName("hash_count")
    private int hashCount;

    public NumberFilterStorage(String filterData, int hashCount) {
      this.filterData = filterData;
      this.hashCount  = hashCount;
    }

    public String getFilterData() {
      return filterData;
    }

    public int getHashCount() {
      return hashCount;
    }

    public void serializeToStream(OutputStream out) throws IOException {
      out.write(new Gson().toJson(this).getBytes());
    }

    public static NumberFilterStorage fromStream(InputStream in) throws IOException {
      try {
        return new Gson().fromJson(new BufferedReader(new InputStreamReader(in)),
                                   NumberFilterStorage.class);
      } catch (JsonParseException jpe) {
        Log.w("NumberFilter", jpe);
        throw new IOException("JSON Parse Exception");
      }
    }
  }
}