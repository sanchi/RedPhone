package org.thoughtcrime.redphone.ui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.thoughtcrime.redphone.monitor.CallQualityConfig;
import org.thoughtcrime.redphone.monitor.UploadService;
import org.thoughtcrime.redphone.monitor.UserFeedback;
import org.thoughtcrime.redphone.R;

import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.RatingBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.google.thoughtcrimegson.Gson;





public class CallQualityDialog extends SherlockActivity  {

	  private Button sendButton;
	  private Button doneDialogButton;

	  private long callId;
	  private List<String> feedbackQuestions;
	  
	  private ArrayAdapter<String> adapter;
	  
	  private String typeOfData = "user-feedback";
	  //private ArrayAdapter adapter;
	 
	  public void onCreate(Bundle icicle) {
		    super.onCreate(icicle);
		    this.callId = getIntent().getLongExtra("callId", -1);

		    feedbackQuestions = getFeedbackQuestions();
		   
		    setupInterface();   
	  }
	  
	  private List<String> getFeedbackQuestions()
	  {
		  CallQualityConfig config = ApplicationPreferencesActivity.getCallQualityConfig(this);
		  return config.getCallQualityQuestions();
	  }
	  
	  
	  protected List<Map<String, Object>> getData() {
		  if(!ApplicationPreferencesActivity.wasUserNotifedOfCallQaulitySettings(this) ){return null;}

		  List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
		  for(String s: feedbackQuestions)
		  {
			  Map<String,Object> m = new HashMap<String,Object>();
			  m.put("title", s);
			  data.add(m);
		  }
		  return data;
	  }

	  private void setViewToInitialDialog(){
		  setContentView(R.layout.call_quality_initial_dialog);
	  }
	  
	  private void setViewToStandardDialog()
	  {
		  setContentView(R.layout.call_quality_dialog);
		  ListView list = (ListView)findViewById(android.R.id.list);
		  adapter = new ArrayAdapter<String>(this,android.R.layout.select_dialog_multichoice, feedbackQuestions);
		  list.setAdapter(adapter);
		  list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
	  }
	  
	  private void initializeInitialDialogResources()
	  {
		  this.doneDialogButton        		= (Button)findViewById(R.id.doneDialogButton);
		  this.doneDialogButton.setOnClickListener(new doneDialogListener());
	  }
	  
	  private void initializeStandardDialogResources()
	  {
		  this.sendButton        	= (Button)findViewById(R.id.sendButton);
		  this.sendButton.setOnClickListener(new sendButtonListener());
	  }
	  
	  private void setupInterface()
	  {
		  if(!ApplicationPreferencesActivity.wasUserNotifedOfCallQaulitySettings(this)){
			  setViewToInitialDialog();
			  initializeInitialDialogResources();
		  }else if(ApplicationPreferencesActivity.getDisplayDialogPreference(this)){
			  setViewToStandardDialog();
			  initializeStandardDialogResources();
		  }else{
			  finish();
		  }
	  }
	  
	  private  UserFeedback buildUserFeedbackObject()
	  {  
		  UserFeedback userFeedback = new UserFeedback();
		  userFeedback.setRating(((RatingBar)findViewById(R.id.callRatingBar)).getRating());
		 
		  ListView list = (ListView)findViewById(android.R.id.list);
		 
		  SparseBooleanArray checkedItems = list.getCheckedItemPositions(); 
		  for (int i = 0; i < feedbackQuestions.size(); i++){
			  userFeedback.addQuestionResponse(feedbackQuestions.get(i), checkedItems.indexOfKey(i) >= 0);
		  }
		  return userFeedback;
	  }
	  
	  private void sendData(){
		  FileWriter fileWriter = null;
		  BufferedWriter writer = null;
		  try{
			  File cacheSubdir = new File(this.getCacheDir(), "/calldata");
			  cacheSubdir.mkdir();
			  File jsonFile = File.createTempFile("userfeedback", ".json", cacheSubdir);
			  fileWriter = new FileWriter(jsonFile);
			  writer = new BufferedWriter(fileWriter);
			 
			  UserFeedback feedbackObject = buildUserFeedbackObject();
			  writer.write(new Gson().toJson(feedbackObject));

			  UploadService.beginUpload(this,String.valueOf(callId), typeOfData, jsonFile );
		  }catch(IOException e){
//			 e.printStackTrace();
		  }finally{
			  if(null != writer){
				  try{ writer.close(); } catch (Exception e){}
			  }
			  if(null != fileWriter){
				  try{ fileWriter.close(); } catch(Exception e){}
			  }
		  }
		  finish();
	  }

	  private class doneDialogListener implements View.OnClickListener {
		  @Override
		  public void onClick(View v) {
			  boolean optIn = ((CheckBox)findViewById(R.id.optInCheckBox)).isChecked();
			  boolean enableDialog = ((CheckBox)findViewById(R.id.enableDialogCheckBox)).isChecked();

			  ApplicationPreferencesActivity.setMetricsOptInFlag(getApplicationContext(), optIn);
			  ApplicationPreferencesActivity.setDisplayDialogPreference(getApplicationContext(), enableDialog);
			  ApplicationPreferencesActivity.setUserNotfiedOfCallQualitySettings(getApplicationContext(),true);
			  setupInterface();
		  }
	  }	
	  private class sendButtonListener implements View.OnClickListener {
		  @Override
		  public void onClick(View v) {
			 sendData();
		  }
	  }	  
}








