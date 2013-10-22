package org.thoughtcrime.redphone.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import com.actionbarsherlock.app.SherlockActivity;
import org.thoughtcrime.redphone.R;

/**
 * Displays information about the Call Metrics program
 *
 * @author Stuart O. Anderson
 */
public class CallMetricsInfoActivity extends Activity {

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.call_metrics_info);
    setTitle(getString(R.string.CallMetricsInfo__title));

    findViewById(R.id.close_button).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });
  }
}
