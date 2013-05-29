package org.thoughtcrime.redphone.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.thoughtcrime.redphone.crypto.zrtp.retained.RetainedSecrets;
import org.thoughtcrime.redphone.util.Base64;
import org.thoughtcrime.redphone.util.PhoneNumberFormatter;
import org.thoughtcrime.redphone.util.Util;

import java.io.IOException;

public class RetainedSecretsDatabase {

  private static final String TABLE_NAME = "retained_secrets";
  private static final String ID         = "_id";
  private static final String NUMBER     = "number";
  private static final String ZID        = "zid";
  private static final String EXPIRES    = "expires";
  private static final String RS1        = "rs1";
  private static final String RS2        = "rs2";
  private static final String VERIFIED   = "verified";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
      " (" + ID + " integer PRIMARY KEY, " + NUMBER + " TEXT, " + ZID + " TEXT, " +
      EXPIRES + " INTEGER, " + RS1 + " TEXT, " + RS2 + " TEXT, " + VERIFIED + " INTEGER);";

  public static final String CREATE_INDEX = "CREATE INDEX IF NOT EXISTS cached_secrets_zid_number_index ON " +
      TABLE_NAME + " (" + NUMBER +"," + ZID + ");";

  private final Context context;
  private final SQLiteOpenHelper databaseHelper;

  public RetainedSecretsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    this.context        = context.getApplicationContext();
    this.databaseHelper = databaseHelper;
  }

  public void setVerified(String number, byte[] zid) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    String encodedNumber    = PhoneNumberFormatter.formatNumber(context, number);
    String encodedZid       = Base64.encodeBytes(zid);

    ContentValues values = new ContentValues();
    values.put(VERIFIED, 1);

    database.update(TABLE_NAME, values, NUMBER + " = ? AND " + ZID + " = ?",
                    new String[] {encodedNumber, encodedZid});
  }

  public boolean isVerified(String number, byte[] zid) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    String encodedZid       = Base64.encodeBytes(zid);
    String encodedNumber    = PhoneNumberFormatter.formatNumber(context, number);
    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {VERIFIED},
                              NUMBER + " = ? AND " + ZID + " = ?",
                              new String[] {encodedNumber, encodedZid},
                              null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(VERIFIED)) == 1;
      }

      return false;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public void setRetainedSecret(String number, byte[] zid, byte[] rs1, long expiration, boolean continuity) {
    if (System.currentTimeMillis() >= expiration)
      return;

    String encodedNumber    = PhoneNumberFormatter.formatNumber(context, number);
    String encodedZid       = Base64.encodeBytes(zid);
    String encodedRs1       = Base64.encodeBytes(rs1);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, NUMBER + " = ? AND " + ZID + " = ?",
                              new String[] {encodedNumber, encodedZid}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        long          id     = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        ContentValues values = new ContentValues();

        values.put(RS2, cursor.getString(cursor.getColumnIndexOrThrow(RS1)));
        values.put(RS1, encodedRs1);
        values.put(EXPIRES, expiration);
        if (!continuity) values.put(VERIFIED, 0);

        database.update(TABLE_NAME, values, ID + " = ?", new String[] {id+""});
      } else {
        ContentValues values = new ContentValues();
        values.put(RS1, encodedRs1);
        values.put(RS2, (String)null);
        values.put(ZID, encodedZid);
        values.put(NUMBER, encodedNumber);
        values.put(VERIFIED, false);
        values.put(EXPIRES, expiration);

        database.insert(TABLE_NAME, null, values);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }


  public RetainedSecrets getRetainedSecrets(String number, byte[] zid) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    String encodedZid       = Base64.encodeBytes(zid);
    String encodedNumber    = PhoneNumberFormatter.formatNumber(context, number);

    Cursor cursor = null;

    try {
      cursor = database.query(TABLE_NAME, null, NUMBER + " = ? AND " + ZID + " = ?",
                              new String[] {encodedNumber, encodedZid}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        try {
          long expiration = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRES));

          if (System.currentTimeMillis() > expiration)
            continue;

          byte[] rs1 = null;
          byte[] rs2 = null;

          String encodedR1 = cursor.getString(cursor.getColumnIndexOrThrow(RS1));
          String encodedR2 = cursor.getString(cursor.getColumnIndexOrThrow(RS2));

          if (!Util.isEmpty(encodedR1)) rs1 = Base64.decode(encodedR1);
          if (!Util.isEmpty(encodedR2)) rs2 = Base64.decode(encodedR2);

          return new RetainedSecrets(rs1, rs2);
        } catch (IOException e) {
          Log.w("RetainedSecretsDatabase", e);
        }
      }

      return new RetainedSecrets(null, null);
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }
}
