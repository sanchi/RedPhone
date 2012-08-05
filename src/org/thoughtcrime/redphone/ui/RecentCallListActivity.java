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

package org.thoughtcrime.redphone.ui;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;

import org.thoughtcrime.redphone.Constants;
import org.thoughtcrime.redphone.R;
import org.thoughtcrime.redphone.RedPhone;
import org.thoughtcrime.redphone.RedPhoneService;

/**
 * A tab for the dialer activity which displays recent call history.
 *
 * @author Moxie Marlinspike
 *
 */
public class RecentCallListActivity extends SherlockListFragment
                                    implements LoaderManager.LoaderCallbacks<Cursor>
{

  @Override
  public void onActivityCreated(Bundle icicle) {
    super.onCreate(icicle);
    displayCalls();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.recent_calls, container, false);
  }

  @Override
  public void onResume() {
    super.onResume();
    NotificationManager notificationManager =
         (NotificationManager)getActivity().getSystemService(Activity.NOTIFICATION_SERVICE);
    notificationManager.cancel(DialerActivity.MISSED_CALL);
  }

  private void displayCalls() {
    setListAdapter(new CallListAdapter(getActivity(), null));
    this.getLoaderManager().initLoader(0, null, this);
  }

  private class CallListAdapter extends CursorAdapter {

    public CallListAdapter(Context context, Cursor c) {
      super(context, c);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      CallItemView view = new CallItemView(context);
      bindView(view, context, cursor);

      return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      String name   = cursor.getString(cursor.getColumnIndexOrThrow(Calls.CACHED_NAME));
      String label  = cursor.getString(cursor.getColumnIndexOrThrow(Calls.CACHED_NUMBER_LABEL));
      String number = cursor.getString(cursor.getColumnIndexOrThrow(Calls.NUMBER));
      int type      = cursor.getInt(cursor.getColumnIndexOrThrow(Calls.TYPE));
      long date     = cursor.getLong(cursor.getColumnIndexOrThrow(Calls.DATE));

      ((CallItemView)view).set(name, label, number, type, date);
    }
  }

  private class CallItemView extends RelativeLayout {
    private Context context;
    private ImageView callTypeIcon;
    private TextView date;
    private TextView label;
    private TextView number;
    private TextView line1;

    private Drawable incomingCallIcon;
    private Drawable outgoingCallIcon;
    private Drawable missedCallIcon;

    public CallItemView(Context context) {
      super(context.getApplicationContext());

      LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      li.inflate(R.layout.recent_call_item, this, true);

      this.context          = context.getApplicationContext();
      this.callTypeIcon     = (ImageView) findViewById(R.id.call_type_icon);
      this.date             = (TextView)  findViewById(R.id.date);
      this.label            = (TextView)  findViewById(R.id.label);
      this.number           = (TextView)  findViewById(R.id.number);
      this.line1            = (TextView)  findViewById(R.id.line1);
      this.incomingCallIcon = getResources().getDrawable(R.drawable.ic_call_log_list_incoming_call);
      this.outgoingCallIcon = getResources().getDrawable(R.drawable.ic_call_log_list_outgoing_call);
      this.missedCallIcon   = getResources().getDrawable(R.drawable.ic_call_log_list_missed_call);
    }

    public void set(String name, String label, String number, int type, long date) {
      this.line1.setText((name == null || name.equals("")) ? number : name);
      this.number.setText((name == null || name.equals("")) ? "" : number);
      this.label.setText(label);
      this.date.setText(DateUtils.getRelativeDateTimeString(context, date,
                                                            System.currentTimeMillis(),
                                                            DateUtils.MINUTE_IN_MILLIS,
                                                            DateUtils.FORMAT_ABBREV_RELATIVE));

      if (type == Calls.INCOMING_TYPE) {
        callTypeIcon.setImageDrawable(incomingCallIcon);
      } else if (type == Calls.OUTGOING_TYPE) {
        callTypeIcon.setImageDrawable(outgoingCallIcon);
      } else if (type == Calls.MISSED_TYPE) {
        callTypeIcon.setImageDrawable(missedCallIcon);
      }
    }

    public String getNumber() {
      if (this.number.getText().toString().equals(""))
        return this.line1.getText().toString();

      return this.number.getText().toString();
    }
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    Intent intent = new Intent(getActivity(), RedPhoneService.class);
    intent.setAction(RedPhoneService.ACTION_OUTGOING_CALL);
    intent.putExtra(Constants.REMOTE_NUMBER, ((CallItemView)v).getNumber());
    getActivity().startService(intent);

    Intent activityIntent = new Intent(getActivity(), RedPhone.class);
    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(activityIntent);

    getActivity().finish();
  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return new CursorLoader(getActivity(), Calls.CONTENT_URI,
                            null, null, null, Calls.DEFAULT_SORT_ORDER);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
    ((CursorAdapter)getListAdapter()).changeCursor(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    ((CursorAdapter)getListAdapter()).changeCursor(null);
  }

}
