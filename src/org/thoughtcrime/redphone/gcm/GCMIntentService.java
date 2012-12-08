package org.thoughtcrime.redphone.gcm;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;
import com.google.protobuf.InvalidProtocolBufferException;

import org.thoughtcrime.redphone.Constants;
import org.thoughtcrime.redphone.RedPhoneService;
import org.thoughtcrime.redphone.crypto.EncryptedSignalMessage;
import org.thoughtcrime.redphone.crypto.InvalidEncryptedSignalException;
import org.thoughtcrime.redphone.signaling.SessionDescriptor;
import org.thoughtcrime.redphone.signaling.signals.CompressedInitiateSignalProtocol.CompressedInitiateSignal;
import org.thoughtcrime.redphone.sms.IncomingCallDetails;

public class GCMIntentService extends GCMBaseIntentService {

  public GCMIntentService() {
    super(GCMRegistrationService.GCM_SENDER_ID);
  }

  @Override
  protected void onRegistered(Context context, String registrationId) {
    Log.w("GCMIntentService", "GCM Registered!");
    GCMRegistrarHelper.setRegistrationIdOnServer(context, registrationId);
  }

  @Override
  protected void onUnregistered(Context context, String registrationId) {
    Log.w("GCMIntentService", "GCM Unregistered!");
    GCMRegistrarHelper.unsetRegistrationIdOnServer(context, registrationId);
  }

  @Override
  protected void onError(Context context, String error) {
    Log.w("GCMIntentService", "GCM Registration failed with hard error: " + error);
  }

  @Override
  protected void onMessage(Context context, Intent intent) {
    String data                     = intent.getStringExtra("signal");
    IncomingCallDetails callDetails = getIncomingCallDetails(context, data);

    if (callDetails != null) {
      intent.setClass(context, RedPhoneService.class);
      intent.setAction(RedPhoneService.ACTION_INCOMING_CALL);
      intent.putExtra(Constants.REMOTE_NUMBER, callDetails.getInitiator());
      intent.putExtra(Constants.SESSION, new SessionDescriptor(callDetails.getHost(),
                                                               callDetails.getPort(),
                                                               callDetails.getSessionId()));
      context.startService(intent);
    }
  }

  private IncomingCallDetails getIncomingCallDetails(Context context, String signalString) {
    try {
      Log.w("GCMIntentService", "Got GCM Signal: " + signalString);
      EncryptedSignalMessage encryptedSignalMessage = new EncryptedSignalMessage(context,
                                                                                 signalString);
      CompressedInitiateSignal signal = CompressedInitiateSignal.parseFrom(encryptedSignalMessage
                                                                           .getPlaintext());

      return new IncomingCallDetails(signal.getInitiator(), signal.getPort(),
                                     signal.getSessionId(), signal.getServerName());
    } catch (InvalidEncryptedSignalException e) {
      Log.w("GCMIntentService", e);
      return null;
    } catch (InvalidProtocolBufferException e) {
      Log.w("GCMIntentService", e);
      return null;
    }
  }
}
