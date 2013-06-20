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

import android.net.http.AndroidHttpClient;
import android.preference.Preference;
import android.util.Log;
import android.webkit.MimeTypeMap;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.thoughtcrime.redphone.Manifest;
import org.thoughtcrime.redphone.Release;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Uploads call quality data to the statistics server.
 *
 * Makes several attempts for each call, cleans up the stored data when finished.
 *
 * @author Stuart O. Anderson
 */
public class Uploader {
  public final static int MAX_ATTEMPTS = 3;

  private final String callId;
  private final String clientId;
  private final String dataSource;
  private final File datafile;

  private int attemptId;

  public Uploader(String clientId, String callId, String dataSource, String datafile) {
    this.clientId = clientId;
    this.callId = callId;
    this.dataSource = dataSource;
    this.datafile = new File(datafile);
  }

  public void upload() {
    while(attemptId < MAX_ATTEMPTS) {
      try {
        attemptUpload();
        datafile.delete();
        return;
      } catch (Exception e) {
        Log.e("Uploader", "Attempt failed", e);
      }
      attemptId++;
    }
    datafile.delete();
  }

  public void attemptUpload() throws IOException {
    AndroidHttpClient client = AndroidHttpClient.newInstance("RedPhone");
    try {
      String hostName = String.format("https://%s/collector/%s/%s/%s/%d",
                                      Release.DATA_COLLECTION_SERVER_HOST,
                                      callId,
                                      clientId,
                                      dataSource,
                                      attemptId);
      Log.d("Uploader", "Posting to RedPhone DCS: " + hostName + " clientId: " + clientId
        + " callId: " + callId);
      HttpPost post = new HttpPost(hostName);
      post.setEntity(new FileEntity(datafile, "application/json"));
      post.setHeader("Content-Encoding", "gzip");

      HttpResponse response = client.execute(post);
      if (response.getStatusLine().getStatusCode() != 200) {
        Log.d("Uploader", "Redphone DCS response: " + response.toString());
        response.getEntity().consumeContent();
        client.getConnectionManager().shutdown();
        throw new IllegalStateException("Upload failed");
      }
      response.getEntity().consumeContent();
      client.getConnectionManager().shutdown();
    } finally {
      client.close();
    }
  }
}
