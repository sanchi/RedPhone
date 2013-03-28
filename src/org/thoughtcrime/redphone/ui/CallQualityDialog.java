package org.thoughtcrime.redphone.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.thoughtcrime.redphone.R;

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





public class CallQualityDialog extends SherlockListActivity  {
	
	  private RatingBar callRatingBar;
	  private Button sendButton;
	  private Button doneDialogButton;
	  private CheckBox optInCheckBox;
	  private CheckBox enableDialogCheckBox;

	 
	  public void onCreate(Bundle icicle) {
		    super.onCreate(icicle);
		    
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
	  private class sendButtonListener implements View.OnClickListener {
		  @Override
		  public void onClick(View v) {
			  float rating = ((RatingBar)findViewById(R.id.callRatingBar)).getRating();
			  // SEND DATA
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


}



