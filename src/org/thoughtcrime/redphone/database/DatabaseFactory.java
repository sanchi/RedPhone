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

package org.thoughtcrime.redphone.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Factory for creating database objects for each table within
 * the larger 'database.db'.  Handles schema creation and migration.
 */
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
      RetainedSecretsDatabase.onCreate(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
  }
}
