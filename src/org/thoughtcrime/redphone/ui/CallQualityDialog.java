package org.thoughtcrime.redphone.ui;


import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.TextView;

import org.thoughtcrime.redphone.monitor.CallQualityConfig;
import org.thoughtcrime.redphone.monitor.UploadService;
import org.thoughtcrime.redphone.monitor.UserFeedback;
import org.thoughtcrime.redphone.R;

import com.actionbarsherlock.app.SherlockActivity;
import com.google.thoughtcrimegson.Gson;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CallQualityDialog extends SherlockActivity  {

	  private long callId;
	  
	  private Button sendButton;
	  private Button doneDialogButton;

	  private List<String> feedbackQuestions;
	  private ArrayAdapter<String> adapter;
	  
	  private String typeOfData = "user-feedback";
	  private int numQuestionsToDisplay = 3;
	  private float defaultRating = 1.5f; 
	  
	  public static final String LIST_ITEM_TITLE = "title";	
	  public void onCreate(Bundle icicle) {
		    super.onCreate(icicle);
		    this.callId = getIntent().getLongExtra("callId", -1);
		    feedbackQuestions = getFeedbackQuestions();
		    setupInterface();   
	  }
	  
	  private List<String> getFeedbackQuestions()
	  {
		  CallQualityConfig config = ApplicationPreferencesActivity.getCallQualityConfig(this);
		  List<String> questions = config.getCallQualityQuestions();
		  if(questions.size() > numQuestionsToDisplay){
			  questions = questions.subList(0, numQuestionsToDisplay);
		  }
		  return questions;
	  }
	  
	  
	  protected List<Map<String, Object>> getData() {
		  if(!ApplicationPreferencesActivity.wasUserNotifedOfCallQaulitySettings(this) ){return null;}
		  feedbackQuestions = getFeedbackQuestions();
		  List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
		  for(String s: feedbackQuestions)
		  {
			  Map<String,Object> m = new HashMap<String,Object>();
			  m.put(LIST_ITEM_TITLE, s);
			  data.add(m);
		  }
		  return data;
	  }

	  private void setViewToInitialDialog(){
		  setContentView(R.layout.call_quality_initial_dialog);
		  setTitle(R.string.CallQualityDialog__we_re_making_changes);
	  }
	  
	  private void setViewToStandardDialog()
	  {
		  setContentView(R.layout.call_quality_dialog);
		  setTitle(R.string.CallQualityDialog_call_feedback);
		  TextView listLabel = (TextView)findViewById(R.id.listLabel);
		  ListView list = (ListView)findViewById(android.R.id.list);

		  adapter = new QuestionListAdapter(this,android.R.layout.select_dialog_multichoice, feedbackQuestions);

		  list.setAdapter(adapter);
		  list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		  if(0 >= feedbackQuestions.size() ){
			  listLabel.setVisibility(View.GONE);
		  }
	  }
	  
	  private void initializeInitialDialogResources()
	  {
		  this.doneDialogButton        		= (Button)findViewById(R.id.doneDialogButton);
		  this.doneDialogButton.setOnClickListener(new doneDialogListener());
	  }
	  
	  private void initializeStandardDialogResources()
	  {
		  RatingBar ratingBar = (RatingBar)findViewById(R.id.callRatingBar);
		  ratingBar.setRating(defaultRating);
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
			 e.printStackTrace();
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
	  
class QuestionListAdapter extends ArrayAdapter<String> {

	private Context context;
	private int id;
	private List <String>items ;

	public QuestionListAdapter(Context context, int textViewResourceId , List<String> list ) 
	{
		super(context, textViewResourceId, list);           
		this.context = context;
		id = textViewResourceId;
		items = list ;
	}

	@Override
	public View getView(int position, View v, ViewGroup parent)
	{
		View mView = v ;
		if(mView == null){
			LayoutInflater vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			mView = vi.inflate(id, null);
		}
		
		TextView text = (TextView) mView.findViewById(android.R.id.text1);

		if(items.get(position) != null )
		{
			text.setTextColor(Color.WHITE);
			text.setText(items.get(position));
		}
		return mView;
	}

}









