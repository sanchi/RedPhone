/*
 * Copyright (C) 2013 Open Whisper Systems
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

package org.thoughtcrime.redphone.monitor;

import java.util.HashMap;
import java.util.Map;

/**
 * Feedback about call quality collected from the user.  This object is serialized into
 * JSON and sent to the call metrics server
 *
 * @author Jazz Alyxzander
 * @author Stuart O. Anderson
 */

public class UserFeedback {
  private float rating = -1;
  private Map<String,Object> questionResponses = new HashMap<String,Object>();

  public void addQuestionResponse(String question, Object response) {
    questionResponses.put(question, response);
  }

  public void setRating(float value) {
    rating = value;
  }
}
