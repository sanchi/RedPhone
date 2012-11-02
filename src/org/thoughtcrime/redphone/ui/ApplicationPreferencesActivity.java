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

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.redphone.R;
import org.thoughtcrime.redphone.Release;
import org.thoughtcrime.redphone.audio.DeviceAudioSettings;
import org.thoughtcrime.redphone.c2dm.C2DMRegistrationService;

/**
 * Preferences menu Activity.
 *
 * Also provides methods for setting and getting application preferences.
 *
 * @author Stuart O. Anderson
 */
//TODO(Stuart Anderson): Consider splitting this into an Activity and a utility class
public class ApplicationPreferencesActivity extends SherlockPreferenceActivity {

  public static final String UI_DEBUG_PREF              = "pref_debug_ui";
  public static final String AUDIO_COMPAT_PREF          = "pref_audio_compat";
  public static final String AUDIO_SPEAKER_INCALL       = "pref_speaker_incall";
  public static final String LOOPBACK_MODE_PREF         = "pref_loopback";
  public static final String DEBUG_VIEW_PREF            = "pref_debugview";
  public static final String SIMULATE_PACKET_DROPS      = "pref_simulate_packet_loss";
  public static final String MINIMIZE_LATENCY           = "pref_min_latency";
  public static final String SINGLE_THREAD		          = "pref_singlethread";
  public static final String USE_C2DM                   = "pref_use_c2dm";
  public static final String AUDIO_TRACK_DES_LEVEL      = "pref_audio_track_des_buffer_level";
  public static final String CALL_STREAM_DES_LEVEL      = "pref_call_stream_des_buffer_level";
  public static final String ASK_DIAGNOSTIC_REPORTING   = "pref_ask_diagnostic_reporting";
  public static final String OPPORTUNISTIC_UPGRADE_PREF = "pref_prompt_upgrade";

  private ProgressDialog progressDialog;
  private C2DMCompleteReceiver completeReceiver;

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    addPreferencesFromResource(R.xml.preferences);

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle(R.string.ApplicationPreferencesActivity_redphone_settings);

    if(Release.DEBUG) {
      addPreferencesFromResource(R.xml.debug);
    }

    initializeIntentFilters();
    initializePreferenceDisplay();
    initializeListeners();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    if (progressDialog != null)
      progressDialog.dismiss();

    if (completeReceiver != null)
      unregisterReceiver(completeReceiver);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
    }

    return false;
  }

  private void initializeListeners() {
    CheckBoxPreference c2dmPreference = (CheckBoxPreference)this.findPreference(USE_C2DM);
    c2dmPreference.setOnPreferenceClickListener(new C2DMToggleListener());
  }

  private void initializeIntentFilters() {
    completeReceiver = new C2DMCompleteReceiver();
    registerReceiver(completeReceiver,
                     new IntentFilter(C2DMRegistrationService.C2DM_ACTION_COMPLETE));
  }

  private void initializePreferenceDisplay() {
    if (Build.VERSION.SDK_INT < 8)
      ((CheckBoxPreference)findPreference(USE_C2DM)).setEnabled(false);
  }

  public static boolean getPromptUpgradePreference(Context context) {
    return PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(OPPORTUNISTIC_UPGRADE_PREF, true);
  }

  public static void setC2dm(Context context, boolean enabled) {
    PreferenceManager
    .getDefaultSharedPreferences(context).edit().putBoolean(USE_C2DM, enabled).commit();
  }

  public static void setAudioTrackDesBufferLevel( Context context, int level ) {
    PreferenceManager
    .getDefaultSharedPreferences(context).edit().putInt(AUDIO_TRACK_DES_LEVEL, level).commit();
  }

  public static void setCallStreamDesBufferLevel( Context context, float dynDesFrameDelay ) {
    PreferenceManager
    .getDefaultSharedPreferences(context).edit().putFloat(CALL_STREAM_DES_LEVEL,
                                                          dynDesFrameDelay).commit();
  }

  public static int getAudioTrackDesBufferLevel( Context context ) {
    return PreferenceManager
           .getDefaultSharedPreferences(context).getInt(AUDIO_TRACK_DES_LEVEL, 900);
  }

  public static float getCallStreamDesBufferLevel( Context context ) {
    return PreferenceManager
           .getDefaultSharedPreferences(context).getFloat(CALL_STREAM_DES_LEVEL, 2.5f);
  }

  public static boolean getAudioModeIncall(Context context) {
    return PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(AUDIO_SPEAKER_INCALL,
                                                            DeviceAudioSettings.useInCallMode() );
  }

  public static boolean getAudioCompatibilityMode(Context context) {
    return PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(AUDIO_COMPAT_PREF, false);
  }

  public static boolean getDebugViewEnabled(Context context) {
    return Release.DEBUG &&
           PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(DEBUG_VIEW_PREF, false);
  }

  public static boolean getLoopbackEnabled(Context context) {
    return Release.DEBUG &&
           PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(LOOPBACK_MODE_PREF, false);
  }

  public static boolean isSimulateDroppedPackets(Context context) {
    return Release.DEBUG &&
           PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(SIMULATE_PACKET_DROPS, false);
  }

  public static boolean isMinimizeLatency(Context context) {
    return PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(MINIMIZE_LATENCY, false);
  }

  public static boolean isSingleThread(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SINGLE_THREAD, false);
  }

  private class C2DMToggleListener implements Preference.OnPreferenceClickListener {
    public boolean onPreferenceClick(Preference preference) {
      boolean enabled = ((CheckBoxPreference)preference).isChecked();
      ((CheckBoxPreference)preference).setChecked(!enabled);

      progressDialog  = ProgressDialog.show(ApplicationPreferencesActivity.this,
                                            getString(R.string.ApplicationPreferencesActivity_updating_signaling_method),
                                            getString(R.string.ApplicationPreferencesActivity_changing_signaling_method_this_could_take_a_second),
                                            true, false);

      Intent intent = new Intent(ApplicationPreferencesActivity.this,
                                 C2DMRegistrationService.class);

      if (enabled) intent.setAction(C2DMRegistrationService.REGISTER_ACTION);
      else         intent.setAction(C2DMRegistrationService.UNREGISTER_ACTION);

      startService(intent);
      return true;
    }
  }

  private class C2DMCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (progressDialog != null)
        progressDialog.dismiss();

      if (intent.getStringExtra("type").equals(C2DMRegistrationService.REGISTER_ACTION)) {
        if (!intent.getBooleanExtra("success", false)) {
          Toast.makeText(ApplicationPreferencesActivity.this,
                         R.string.ApplicationPreferencesActivity_error_registering_with_c2dm_server,
                         Toast.LENGTH_LONG).show();
        } else {
          ((CheckBoxPreference)findPreference(USE_C2DM)).setChecked(true);
        }
      }

      if (intent.getStringExtra("type").equals(C2DMRegistrationService.UNREGISTER_ACTION)) {
        if (!intent.getBooleanExtra("success", false)) {
          Toast.makeText(ApplicationPreferencesActivity.this,
                         R.string.ApplicationPreferencesActivity_error_unregistering_with_c2dm_server,
                         Toast.LENGTH_LONG).show();
        } else {
          ((CheckBoxPreference)findPreference(USE_C2DM)).setChecked(false);
        }
      }

      if (intent.getStringExtra("type").equals(C2DMRegistrationService.ERROR_ACTION)) {
        Toast.makeText(ApplicationPreferencesActivity.this,
                       R.string.ApplicationPreferencesActivity_error_communicating_with_c2dm_server,
                       Toast.LENGTH_LONG).show();
      }
    }
  }

  public static void setAskUserToSendDiagnosticData(Context context, boolean enabled) {
    PreferenceManager
    .getDefaultSharedPreferences(context).edit()
    .putBoolean(ASK_DIAGNOSTIC_REPORTING, enabled).commit();
  }

  public static boolean getAskUserToSendDiagnosticData(Context context ) {
    return PreferenceManager
           .getDefaultSharedPreferences(context)
           .getBoolean(ASK_DIAGNOSTIC_REPORTING, true);
  }
}
