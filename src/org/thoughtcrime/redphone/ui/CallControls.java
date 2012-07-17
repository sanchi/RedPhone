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

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.redphone.R;
import org.thoughtcrime.redphone.util.com.android.internal.widget.multiwaveview.MultiWaveView;

/**
 * Displays the controls at the bottom of the in-call screen.
 *
 * @author Moxie Marlinspike
 *
 */

public class CallControls extends RelativeLayout {

  private ImageButton endCallButton;
  private TextView sasTextView;

  private View activeCallWidget;
  private MultiWaveView incomingCallWidget;

  private Handler handler = new Handler() {
    @Override
    public void handleMessage(Message message) {
      if (incomingCallWidget.getVisibility() == View.VISIBLE) {
        incomingCallWidget.ping();
        handler.sendEmptyMessageDelayed(0, 1200);
      }
    }
  };

  public CallControls(Context context) {
    super(context);
    initialize();
  }

  public CallControls(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public CallControls(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public void setIncomingCall() {
    activeCallWidget.setVisibility(View.GONE);

    Animation animation = incomingCallWidget.getAnimation();

    if (animation != null) {
      animation.reset();
      incomingCallWidget.clearAnimation();
    }

    incomingCallWidget.reset(false);
    incomingCallWidget.setVisibility(View.VISIBLE);

    handler.sendEmptyMessageDelayed(0, 500);
  }

  public void setActiveCall() {
    incomingCallWidget.setVisibility(View.GONE);
    activeCallWidget.setVisibility(View.VISIBLE);
    sasTextView.setVisibility(View.GONE);
  }

  public void setActiveCall(String sas) {
    setActiveCall();
    sasTextView.setText(sas);
    sasTextView.setVisibility(View.VISIBLE);
  }

  public void reset() {
    incomingCallWidget.setVisibility(View.GONE);
    activeCallWidget.setVisibility(View.GONE);
    sasTextView.setText("");
  }

  public void setHangupButtonListener(final HangupButtonListener listener) {
    endCallButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        listener.onClick();
      }
    });
  }

  public void setIncomingCallActionListener(final IncomingCallActionListener listener) {
    incomingCallWidget.setOnTriggerListener(new MultiWaveView.OnTriggerListener() {
      @Override
      public void onTrigger(View v, int target) {
        switch (target) {
        case 0: listener.onAcceptClick(); break;
        case 2: listener.onDenyClick();   break;
        }
      }
      @Override
      public void onReleased(View v, int handle) {}
      @Override
      public void onGrabbedStateChange(View v, int handle) {}
      @Override
      public void onGrabbed(View v, int handle) {}
    });
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext()
        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.call_controls, this, true);

    this.endCallButton      = (ImageButton)findViewById(R.id.endButton);
    this.incomingCallWidget = (MultiWaveView)findViewById(R.id.incomingCallWidget);
    this.activeCallWidget   = (View)findViewById(R.id.inCallControls);
    this.sasTextView        = (TextView)findViewById(R.id.sas);
  }

  public static interface HangupButtonListener {
    public void onClick();
  }

  public static interface IncomingCallActionListener {
    public void onAcceptClick();
    public void onDenyClick();
  }
}
