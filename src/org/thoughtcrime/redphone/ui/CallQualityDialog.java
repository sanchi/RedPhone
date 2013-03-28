package org.thoughtcrime.redphone.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.util.Map;

import org.thoughtcrime.redphone.R;
import org.thoughtcrime.redphone.RedPhoneService;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.ArrayAdapter;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListActivity;
import com.google.thoughtcrimegson.stream.JsonWriter;





public class CallQualityDialog extends SherlockListActivity  {
	
	  private RatingBar callRatingBar;
	  private Button sendButton;
	  private Button doneDialogButton;
	  private CheckBox optInCheckBox;
	  private CheckBox enableDialogCheckBox;

	  private RedPhoneService redPhoneService;
	  
	  private String callId;
	  
	  private Context context;
	 
	  public void onCreate(Bundle icicle) {
		    super.onCreate(icicle);
		    context = this.context;
		    this.callId = icicle.getString("callId");
		    
		    //setFeedbackQuestions();
		    setView();
		    initializeResources();
		   
		   
	  }
	  
	  
	  protected List<Map<String, Object>> getData() {
		  
		  if(!hasUserBeenAskedToOptIn() ){return null;}
		  
		  
	        List<Map<String, Object>> myData = new ArrayList<Map<String, Object>>();
	        
	        Map<String,Object> m = new HashMap<String,Object>();
		   	 
	        Map<String,Object> m2 = new HashMap<String,Object>();
	        m2.put("title", "Echo");
	        myData.add(m2);
	        
	        m.put("title", "Dropped Call");
	        myData.add(m);

	        Map<String,Object> m1 = new HashMap<String,Object>();
	        m1.put("title", "Jitter");
	        myData.add(m1);
	      
	        return myData;
	    }

	  private void setView(){
		  if(!hasUserBeenAskedToOptIn())
		    {
		    	 setContentView(R.layout.call_quality_initial_dialog);
		    }else if(getDialogSetting()){
		    	 setContentView(R.layout.call_quality_dialog);

			     setListAdapter(new SimpleAdapter(this, getData(),
			                android.R.layout.simple_list_item_1 , new String[] { "title" },
			                new int[] { android.R.id.text1 }));
			     getListView().setTextFilterEnabled(true);
		    }else{
//		    	finish();
		    }
	  }
	  
	  private void initializeInitialResources()
	  {
		  this.doneDialogButton        		= (Button)findViewById(R.id.doneDialogButton);
		  this.optInCheckBox				= (CheckBox)findViewById(R.id.optInCheckBox);
		  this.enableDialogCheckBox			= (CheckBox)findViewById(R.id.enableDialogCheckBox);
		  
		  this.doneDialogButton.setOnClickListener(new doneDialogListener());
	  }
	  
	  private void initializeStandardResources()
	  {
		  this.sendButton        		= (Button)findViewById(R.id.sendButton);
		  this.callRatingBar		= (RatingBar)findViewById(R.id.callRatingBar);

		  this.sendButton.setOnClickListener(new sendButtonListener());
//		  this.disableDialogButton.setOnClickListener(new disableDialogListener());
//		  this.sometimesEnableDialogButton.setOnClickListener(new sometimesEnableDialogListener());
	  }
	  
	  private void initializeResources()
	  {
		  if(!hasUserBeenAskedToOptIn()){
			  initializeInitialResources();
		  }
		  initializeStandardResources();
	  }
	  
	  private  UserFeedback buildUserFeedbackObject()
	  {
		  UserFeedback userFeedback = new UserFeedback(callId);
		  userFeedback.rating = ((RatingBar)findViewById(R.id.callRatingBar)).getRating();
		  userFeedback.issueTags = new ArrayList<String>();
		  
		  for(String s:)
	  }
	  
	  private void sendData() throws IOException{
		  

		  
		  
		  
		  
		  
		  File cacheSubdir = new File(this.getCacheDir(), "/calldata");
		  cacheSubdir.mkdir();
		  File jsonFile = File.createTempFile("userfeedback", ".json", cacheSubdir);
		  OutputStream outputStream = new FileOutputStream(jsonFile);
		  JsonWriter writer = new JsonWriter(new OutputStreamWriter(outputStream));
		  writer.beginArray();
		 
	  }

	  private class sendButtonListener implements View.OnClickListener {
		  @Override
		  public void onClick(View v) {
			  sendData();
			  finish();
		  }
	  }


	  private class doneDialogListener implements View.OnClickListener {
		  @Override
		  public void onClick(View v) {
			  Log.d("BUTTON", "Enable");
			  boolean optIn = ((CheckBox)findViewById(R.id.optInCheckBox)).isChecked();
			  boolean enableDialog = ((CheckBox)findViewById(R.id.enableDialogCheckBox)).isChecked();

			  setOptInFlag(optIn);
			  setDialogSetting(enableDialog);
			  setHasUserBeenAskedToOptIn(true);
			  setView();
		  }
	  }

	  
	  private boolean hasUserBeenAskedToOptIn(){
		  return PreferenceManager
		           .getDefaultSharedPreferences(this).getBoolean("USER_ASKED_TO_OPT_IN", false);
	  }
	  
	  private void setHasUserBeenAskedToOptIn(boolean value){
		  PreferenceManager.getDefaultSharedPreferences(this).edit()
	        .putBoolean("USER_ASKED_TO_OPT_IN", value)
	        .commit();
	  }
	  
	  private boolean getDialogSetting(){
		  return PreferenceManager
		           .getDefaultSharedPreferences(this).getBoolean("ENABLE_CALL_QUALITY_DIALOG", true);
	  }
	  
	  private void setDialogSetting(boolean value){
		  PreferenceManager.getDefaultSharedPreferences(this).edit()
	        .putBoolean("ENABLE_CALL_QUALITY_DIALOG", value)
	        .commit();
		 // callQualityPrefSetting = value;
	  }
	  
	  private boolean getOptInFlag(){
		  return PreferenceManager
		           .getDefaultSharedPreferences(this).getBoolean("ENABLE_CALL_METRIC_UPLOAD", true);
	  }
	  
	  private void setOptInFlag(boolean value){
		  PreferenceManager.getDefaultSharedPreferences(this).edit()
	        .putBoolean("ENABLE_CALL_METRIC_UPLOAD", value)
	        .commit();
	  }

	  private class UserFeedback {
			private static final int VERSION = 0;
			
			private float rating = -1;
			private String callId = "";
			private ArrayList<String> issueTags = new ArrayList<String>();

			public UserFeedback(String callId){
				this.callId = callId;
			}
			
		}
}








