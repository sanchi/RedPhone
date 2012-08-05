package org.thoughtcrime.redphone.util;

import android.content.ContentValues;
import android.content.Context;
import android.provider.CallLog.Calls;
import android.util.Log;

import org.thoughtcrime.redphone.contacts.PersonInfo;

public class CallLogger {

  private static ContentValues getCallLogContentValues(Context context, String number, long timestamp) {
    PersonInfo pi        = PersonInfo.getInstance(context, number);
    ContentValues values = new ContentValues();

    values.put(Calls.DATE, System.currentTimeMillis());
    values.put(Calls.NUMBER, number);
    values.put(Calls.CACHED_NAME, pi.getName() );
    values.put(Calls.TYPE, pi.getType() );

    return values;
  }

  private static ContentValues getCallLogContentValues(Context context, String number) {
    return getCallLogContentValues(context, number, System.currentTimeMillis());
  }

  public static void logMissedCall(Context context, String number, long timestamp) {
    ContentValues values = getCallLogContentValues(context, number, timestamp);
    values.put(Calls.TYPE, Calls.MISSED_TYPE);
    context.getContentResolver().insert(Calls.CONTENT_URI, values);
  }

  public static void logOutgoingCall(Context context, String number) {
    ContentValues values = getCallLogContentValues(context, number);
    values.put(Calls.TYPE, Calls.OUTGOING_TYPE);
    try{
      context.getContentResolver().insert(Calls.CONTENT_URI, values);
    } catch (IllegalArgumentException e ) {
      Log.w("RedPhoneService", "Failed call log insert", e );
    }
  }

  public static void logIncomingCall(Context context, String number) {
    ContentValues values = getCallLogContentValues(context, number);
    values.put(Calls.TYPE, Calls.INCOMING_TYPE);
    context.getContentResolver().insert(Calls.CONTENT_URI, values);
  }

}
