package org.thoughtcrime.redphone.monitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class UserFeedback {
  private float rating = -1;
  private Map<String,Object> questionResponses = new HashMap<String,Object>();

  public UserFeedback(){
  }

  public void addQuestionResponse(String question, Object response){
    questionResponses.put(question, response);
  }
  public void setRating(float value){
    rating = value;
  }

}
