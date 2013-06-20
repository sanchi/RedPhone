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

package org.thoughtcrime.redphone.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import org.thoughtcrime.redphone.ApplicationContext;
import org.thoughtcrime.redphone.R;
import org.thoughtcrime.redphone.codec.AudioCodec;
import org.thoughtcrime.redphone.monitor.CallMonitor;
import org.thoughtcrime.redphone.monitor.CountMetric;
import org.thoughtcrime.redphone.monitor.HistogramMetric;
import org.thoughtcrime.redphone.network.RtpAudioSender;
import org.thoughtcrime.redphone.profiling.PacketLogger;
import org.thoughtcrime.redphone.profiling.PeriodicTimer;
import org.thoughtcrime.redphone.profiling.ProfilingTimer;
import org.thoughtcrime.redphone.ui.ApplicationPreferencesActivity;
import org.thoughtcrime.redphone.util.Factory;
import org.thoughtcrime.redphone.util.Pool;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MicrophoneReader provides robust async access to the device microphone.  A reader thread
 * performs blocking reads on the microphone device and writes the results to an async queue.
 *
 * The microphone reader provides non-blocking access to any data in this queue and handles
 * clearing and restarting the reader in the event of buffer overflows or hardware issues.
 *
 * @author Stuart O. Anderson
 */
public class MicrophoneReader {
  public static final String TAG = "MicrophoneReader";
  private static final int AUDIO_SOURCE =
    Build.VERSION.SDK_INT >= 11 ? MediaRecorder.AudioSource.VOICE_COMMUNICATION
      : MediaRecorder.AudioSource.DEFAULT;

  public static final int AUDIO_BUFFER_SIZE = 8000 + AudioRecord
      .getMinBufferSize(AudioCodec.SAMPLE_RATE,
          AudioFormat.CHANNEL_CONFIGURATION_MONO,
          AudioFormat.ENCODING_PCM_16BIT);
  private AudioRecord audioSource = new AudioRecord(
      AUDIO_SOURCE, AudioCodec.SAMPLE_RATE,
      AudioFormat.CHANNEL_CONFIGURATION_MONO,
      AudioFormat.ENCODING_PCM_16BIT, AUDIO_BUFFER_SIZE * 10);
  private byte encodedData[] = new byte[512];
  private LinkedList<EncodedAudioData> audioQueue;
  private AudioCodec codec;
  private boolean micStarted = false;
  private long sequenceNumber = 0;
  PeriodicTimer debugTimer = new PeriodicTimer(5000);
  private int totalSamplesRead;
  private MicReadThread micThread = new MicReadThread();
  private final boolean singleThread = ApplicationPreferencesActivity.isSingleThread(ApplicationContext.getInstance().getContext());

  private ProfilingTimer readTime = new ProfilingTimer("Mic Read Time");
  private PeriodicTimer debugTextUpdateTimer = new PeriodicTimer(1000);
  private DecimalFormat loadFormat = new DecimalFormat("0.00");
  private PacketLogger packetLogger;

  private final AtomicReference<AudioException> micThreadException;
  private final AtomicReference<Boolean> enableMute;
  private final CountMetric counter;
  private final HistogramMetric waveformStats = new HistogramMetric(Short.MIN_VALUE, Short.MAX_VALUE, 16);

  private List<AudioChunk> micAudioList =
    Collections.synchronizedList(new ArrayList<AudioChunk>());
  private Pool<AudioChunk> chunkPool = new Pool<AudioChunk>( new Factory<AudioChunk>() {
    public AudioChunk getInstance() {
      return new AudioChunk( new short[AudioCodec.SAMPLES_PER_FRAME], 0);
    }
  });

  public MicrophoneReader(LinkedList<EncodedAudioData> outgoingAudio,
      AudioCodec codec, PacketLogger packetLogger, CallMonitor monitor) {
    this.codec = codec;
    this.packetLogger = packetLogger;
    audioQueue = outgoingAudio;
    micThreadException = new AtomicReference<AudioException>();
    enableMute = new AtomicReference<Boolean>(false);

    counter = new CountMetric();
    monitor.addSampledMetrics("mic-reader", counter);
    monitor.addSampledMetrics("mic-reader", waveformStats);
  }

  private void waitForMicReady() throws AudioException {
    int waitCount = 0;
    while (audioSource.getState() != AudioRecord.STATE_INITIALIZED) {
      if (waitCount > 50) {
        micThread.terminate();
        throw new AudioException(R.string.MicrophoneReader_microphone_failed_to_initialize_try_changing_audio_call_mode_in_settings);
      }

      waitCount++;
      audioSource.release();
      audioSource = new AudioRecord(
          MediaRecorder.AudioSource.MIC, AudioCodec.SAMPLE_RATE,
          AudioFormat.CHANNEL_CONFIGURATION_MONO,
          AudioFormat.ENCODING_PCM_16BIT, AUDIO_BUFFER_SIZE * 10);
      try {
        Log.d( TAG, "Waiting for Microphone to initialize...["+waitCount+"]" );
        Thread.sleep(100);
      } catch (InterruptedException e) {
      }
    }
  }

  public void go() throws AudioException {
    short audioData[];
    AudioChunk chunk;

    AudioException exception = micThreadException.get();
    if(exception != null) {
      throw new AudioException(exception);
    }

    if( singleThread ) {
      micThread.readFromMic();
    } else if( !micThread.isAlive() ) {
      //TODO(Stuart Anderson): This will throw an unhelpful error if the micThread dies...
      micThread.start();
    }

    if( micAudioList.size() > 50 ) {
      micAudioList.clear();
      Log.d( TAG, "cleared mic queue, too much backlog");
    }
    while( !micAudioList.isEmpty() &&
        audioQueue.size() < RtpAudioSender.audioChunksPerPacket) {
      try {
        chunk = micAudioList.remove(0);
        audioData = chunk.getChunk();
      } catch( IndexOutOfBoundsException e ) {
        return; //no data
      }

      int encodedDataLen = codec.encode(audioData, encodedData,
          AudioCodec.SAMPLES_PER_FRAME);
      chunkPool.returnItem(chunk);
      byte encodedBuffer[] = new byte[encodedDataLen];

      System.arraycopy(encodedData, 0, encodedBuffer, 0, encodedDataLen);
      packetLogger.logPacket( chunk.sequenceNumber, PacketLogger.PACKET_ENCODED );
      audioQueue.add(new EncodedAudioData(encodedBuffer, chunk.sequenceNumber, chunk.sequenceNumber ));
    }
  }

  public void terminate() {
    if( !singleThread ) {
        micThread.terminate();
    }
    if (audioSource.getState() == AudioRecord.STATE_INITIALIZED) {
      audioSource.stop();
      audioSource.release();
    }
  }

  private class AudioChunk {
    public long sequenceNumber;
    private short chunk[];
    public AudioChunk( short audio[], long seqNum ) {
      chunk = audio;
      sequenceNumber = seqNum;
    }

    public short[] getChunk() {
      return chunk;
    }
  }

  private class MicReadThread extends Thread {
    private boolean terminate;
    private boolean outOfLoop;
    public MicReadThread() {
      super( "Microphone Reader" );
    }

    @Override
    public void run() {
      try {
        while( !terminate ) {
          readFromMic();
        }
      } catch(AudioException e) {
        micThreadException.set(e);
      }
      outOfLoop = true;
      synchronized( this ) {
        notify();
      }
    }

    public void terminate() {
      terminate = true;
    }

    AudioChunk staticChunk = new AudioChunk( new short[AudioCodec.SAMPLES_PER_FRAME], 0 );

    public void readFromMic() throws AudioException {
      /*if (debugTextUpdateTimer.periodically()) {
        double loadEst = 1 - readTime.getAccumTime()
            / (debugTextUpdateTimer.getActualLastPeriod() / 1000.0);
        CallStateListener csl = ApplicationContext.getInstance()
            .getCallStateListener();
        csl.notifyDebugInfo("Load: " + loadFormat.format(loadEst));
        readTime.reset();
      }*/
      if (!micStarted) {
        Log.d("MicrophoneReader", "Starting audio recording");
        micStarted = true;
        waitForMicReady();
        audioSource.startRecording();
      }
      if (audioSource.getState() != AudioRecord.STATE_INITIALIZED) {
        Log.d("MicrophoneReader", "AudioRecord is not initialized");
      }
      if (audioSource.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
        Log.d("MicrophoneReader", "not in record state, restarting...");
        micStarted = false;
      }

      long readStartTime = SystemClock.uptimeMillis();
      AudioChunk chunk = chunkPool.getItem();
      readTime.start();
      int samplesRead = audioSource.read(chunk.getChunk(), 0,
          AudioCodec.SAMPLES_PER_FRAME);
      readTime.stop();
      chunk.sequenceNumber = sequenceNumber++;

      packetLogger.logPacket( chunk.sequenceNumber, PacketLogger.PACKET_IN_MIC_QUEUE );

      checkClipping(chunk);

      if(enableMute.get()) {
        muteAudio(chunk);
      }

      micAudioList.add( chunk );

      if (samplesRead != AudioCodec.SAMPLES_PER_FRAME) {
        Log.w("RedPhone", "VoiceSender read only "
            + Integer.toString(samplesRead) + " samples");
      }

      totalSamplesRead += samplesRead;
      /*if (debugTimer.periodically()) {
        Log.d("MicrophoneReader", "Total read: " + totalSamplesRead);
      }*/
      long readStopTime = SystemClock.uptimeMillis();
      if (readStopTime - readStartTime > 300) {
        Log.e("MicrophoneReader", "STRANGE LONG MIC READ TIME: "
            + (readStopTime - readStartTime));
      }
    }
  }

  private void checkClipping(AudioChunk chunk) {
    int clips = 0;
    for (short sample : chunk.getChunk()) {
      waveformStats.addEvent(sample);
      if(sample == Short.MAX_VALUE || sample == Short.MIN_VALUE) {
        clips++;
      }
    }
    counter.increment("clipped", clips);
    counter.increment("samples", chunk.getChunk().length);
  }

  public void flush() {
    micAudioList.clear();
  }

  public void setMute(boolean updatedMuteSetting) {
    enableMute.set(updatedMuteSetting);
  }

  public void muteAudio(AudioChunk chunk) {
    Arrays.fill(chunk.getChunk(), (short) 0);
  }
}
