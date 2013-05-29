package org.thoughtcrime.redphone.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseFactory {
  private static final String DATABASE_NAME    = "database.db";
  private static final int    DATABASE_VERSION = 1;

  private static DatabaseFactory instance;

  private final DatabaseHelper databaseHelper;
  private final RetainedSecretsDatabase retainedSecretsDatabase;

  public static RetainedSecretsDatabase getRetainedSecretsDatabase(Context context) {
    return getInstance(context).retainedSecretsDatabase;
  }

  public static synchronized DatabaseFactory getInstance(Context context) {
    if (instance == null) {
      instance = new DatabaseFactory(context);
    }

    return instance;
  }

  private DatabaseFactory(Context context) {
    this.databaseHelper          = new DatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
    this.retainedSecretsDatabase = new RetainedSecretsDatabase(context, databaseHelper);
  }

  private static class DatabaseHelper extends SQLiteOpenHelper {

    public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
      super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(RetainedSecretsDatabase.CREATE_TABLE);
      db.execSQL(RetainedSecretsDatabase.CREATE_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
  }
}
