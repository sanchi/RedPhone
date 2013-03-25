package org.thoughtcrime.redphone.monitor;

import android.content.Context;
import android.util.Log;
import com.google.thoughtcrimegson.Gson;
import com.google.thoughtcrimegson.GsonBuilder;
import com.google.thoughtcrimegson.stream.JsonWriter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPOutputStream;

/**
 * Data collected over the course of a call.  Streamed to the temp cache directory
 * as gzipped json.
 */
public class CallDataImpl implements CallData {
  private final Gson gson = new GsonBuilder().serializeNulls().create();
  private final JsonWriter writer;
  private final File jsonFile;
  private boolean finished = false;

  public CallDataImpl(Context context) throws IOException {
    File cacheSubdir = new File(context.getCacheDir(), "/calldata");
    cacheSubdir.mkdir();
    jsonFile = File.createTempFile("calldata", ".json.gz", cacheSubdir);
    Log.d("CallDataImpl", "Writing output to " + jsonFile.getAbsolutePath());
    OutputStream outputStream = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(jsonFile)));
    writer = new JsonWriter(new OutputStreamWriter(outputStream));
    writer.beginArray();
  }

  @Override
  public void putNominal(String name, Object value) {
    addEvent(new MonitoredEvent(name, value));
  }

  @Override
  public synchronized void addEvent(MonitoredEvent event) {
    if (finished) {
      Log.d("CallDataImpl", "Not logging event, already finished");
      return;
    }
    gson.toJson(event, event.getClass(), writer);
  }

  /**
   * Finish writing the call data to disk and return the file where it is stored.
   * @return
   */
  @Override
  public synchronized File finish() throws IOException {
    finished = true;
    writer.endArray();
    writer.close();
    return jsonFile;
  }

  public static void clearCache(Context context) {
    File cacheDir = new File(context.getCacheDir(), "/calldata");
    File[] cacheFiles = cacheDir.listFiles();
    if (cacheFiles == null) {
      return;
    }
    for(File cacheFile : cacheFiles) {
      cacheFile.delete();
    }
  }
}
