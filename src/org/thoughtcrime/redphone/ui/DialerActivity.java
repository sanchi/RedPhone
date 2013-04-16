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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;

import org.thoughtcrime.redphone.Constants;
import org.thoughtcrime.redphone.R;
import org.thoughtcrime.redphone.directory.DirectoryUpdateReceiver;
import org.thoughtcrime.redphone.gcm.GCMRegistrarHelper;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.ActionBar.TabListener;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import org.thoughtcrime.redphone.monitor.MonitorConfigUpdateReceiver;
import org.thoughtcrime.redphone.util.PeriodicActionUtils;

/**
 * The base dialer activity.  A tab container for the contacts, call log, and favorites tab.
 *
 * @author Moxie Marlinspike
 *
 */

public class DialerActivity extends SherlockFragmentActivity {

  public static final int    MISSED_CALL     = 1;
  public static final String CALL_LOG_ACTION = "org.thoughtcrime.redphone.ui.DialerActivity";

  private static final int CALL_LOG_TAB_INDEX = 1;

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    ActionBar actionBar = this.getSupportActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
    actionBar.setDisplayShowHomeEnabled(false);
    actionBar.setDisplayShowTitleEnabled(false);
    actionBar.setDisplayUseLogoEnabled(false);

    checkForFreshInstall();
    setContentView(R.layout.dialer_activity);

    setupContactsTab();
    setupCallLogTab();
    setupFavoritesTab();

    GCMRegistrarHelper.registerClient(this, false);
  }

  @Override
  public void onNewIntent(Intent intent) {
    setIntent(intent);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (getIntent().getAction() != null &&
        getIntent().getAction().equals(CALL_LOG_ACTION))
    {
      getIntent().setAction(null);
      getSupportActionBar().setSelectedNavigationItem(CALL_LOG_TAB_INDEX);
    }
  }

  private ActionBar.Tab constructTab(final Fragment fragment) {
    ActionBar actionBar = this.getSupportActionBar();
    ActionBar.Tab tab   = actionBar.newTab();

    tab.setTabListener(new TabListener(){
      @Override
      public void onTabSelected(Tab tab, FragmentTransaction ignore) {
        FragmentManager manager = DialerActivity.this.getSupportFragmentManager();
        FragmentTransaction ft  = manager.beginTransaction();

        ft.replace(R.id.fragment_container, fragment);
        ft.commit();
      }

      @Override
      public void onTabUnselected(Tab tab, FragmentTransaction ignore) {
        FragmentManager manager = DialerActivity.this.getSupportFragmentManager();
        FragmentTransaction ft  = manager.beginTransaction();
        ft.remove(fragment);
        ft.commit();
      }
      @Override
      public void onTabReselected(Tab tab, FragmentTransaction ft) {}
    });

    return tab;
  }

  private void setupContactsTab() {
    final Fragment fragment   = Fragment.instantiate(DialerActivity.this,
                                                     ContactsListActivity.class.getName());
    ActionBar.Tab contactsTab = constructTab(fragment);
    contactsTab.setIcon(R.drawable.ic_tab_contacts);
    this.getSupportActionBar().addTab(contactsTab);
  }

  private void setupCallLogTab() {
    final Fragment fragment  = Fragment.instantiate(DialerActivity.this,
                                                    RecentCallListActivity.class.getName());
    ActionBar.Tab callLogTab = constructTab(fragment);
    callLogTab.setIcon(R.drawable.ic_tab_recent);
    this.getSupportActionBar().addTab(callLogTab);
  }

  private void setupFavoritesTab() {
    Bundle arguments = new Bundle();
    arguments.putBoolean("favorites", true);

    final Fragment fragment = Fragment.instantiate(DialerActivity.this,
                                                   ContactsListActivity.class.getName(), arguments);

    ActionBar.Tab favoritesTab = constructTab(fragment);
    favoritesTab.setIcon(R.drawable.ic_tab_favorites);
    this.getSupportActionBar().addTab(favoritesTab);
  }

  private void checkForFreshInstall() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

    if (preferences.getBoolean(Constants.VERIFYING_PREFERENCE, false)) {
      Log.w("DialerActivity", "Verification underway...");
      startActivity(new Intent(this, RegistrationProgressActivity.class));
      finish();
    }

    if (!preferences.getBoolean(Constants.REGISTERED_PREFERENCE, false)) {
      Log.w("DialerActivity", "Not registered and not verifying...");
      startActivity(new Intent(this, CreateAccountActivity.class));
      finish();
    }

    PeriodicActionUtils.scheduleUpdate(this, DirectoryUpdateReceiver.class);
    PeriodicActionUtils.scheduleUpdate(this, MonitorConfigUpdateReceiver.class);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getSupportMenuInflater();
    inflater.inflate(R.menu.dialer_options_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.resetPasswordItem: launchResetPasswordActivity();  return true;
    case R.id.aboutItem:         launchAboutActivity();          return true;
    case R.id.settingsItem:      launchPreferencesActivity();    return true;
    }
    return false;
  }

  private void launchPreferencesActivity() {
    startActivity(new Intent(this, ApplicationPreferencesActivity.class));
  }

  private void launchResetPasswordActivity() {
    startActivity(new Intent(this, CreateAccountActivity.class));
    finish();
  }

  private void launchAboutActivity() {
    startActivity(new Intent(this, AboutActivity.class));
  }

}
