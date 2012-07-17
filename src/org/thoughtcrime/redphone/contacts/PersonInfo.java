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

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;

import java.io.InputStream;

/**
 * A tuple class that encapsulates information about a contact.
 *
 * @author Moxie Marlinspike
 *
 */

public class PersonInfo {

  private final String phoneNumber;

  private String name;
  private String label;
  private String ringtone;
  private long   personId;
  private Bitmap image;
  private int    type;

  public PersonInfo() {
    this.phoneNumber = "";
  }

  private PersonInfo(String phoneNumber) {
    this.phoneNumber = phoneNumber;
    this.name        = phoneNumber;
    this.label       = "";
  }

  private PersonInfo(String phoneNumber, String name, String label,
                     long personId, String ringtone, Bitmap image,
                     int type)
  {
    this.phoneNumber = phoneNumber;
    this.name        = name;
    this.label       = label;
    this.personId    = personId;
    this.ringtone    = ringtone;
    this.image       = image;
    this.type		 = type;
  }

  public String getNumber() {
    return phoneNumber;
  }

  public String getName() {
    return name;
  }

  public String getLabel() {
    return label;
  }

  public int getType() {
    return type;
  }

  public String getRingtone() {
    return ringtone;
  }

  public long getPersonId() {
    return personId;
  }

  public Bitmap getImage() {
    return image;
  }

  private static Bitmap loadImage(Context context, long personId) {
    Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, personId);
    InputStream inputStream;

    if (Build.VERSION.SDK_INT >= 14) {
      inputStream = ContactsContract.Contacts
                    .openContactPhotoInputStream(context.getContentResolver(), uri, true);
    } else {
      inputStream = ContactsContract.Contacts
                    .openContactPhotoInputStream(context.getContentResolver(), uri);
    }

    if (inputStream != null) {
      return BitmapFactory.decodeStream(inputStream);
    } else {
      return null;
    }
  }

  public static PersonInfo getInstance(Context context, String phoneNumber) {
    //TODO handle empty phone numbers and other bad query states

    Uri uri       = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                                         Uri.encode(phoneNumber));
    Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);

    if (cursor == null || !cursor.moveToFirst()) return new PersonInfo(phoneNumber);

    String name         = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
    int type            = cursor.getInt(cursor.getColumnIndex(ContactsContract.PhoneLookup.TYPE));
    String label        = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.LABEL));
    String displayLabel = Phone.getTypeLabel(context.getResources(), type, label).toString();
    long personId       = cursor.getLong(cursor.getColumnIndex(ContactsContract.PhoneLookup._ID));
    String ringtone     = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.CUSTOM_RINGTONE));

    cursor.close();

    return new PersonInfo(phoneNumber, name, displayLabel, personId,
                          ringtone, loadImage(context, personId), type);
  }
}
