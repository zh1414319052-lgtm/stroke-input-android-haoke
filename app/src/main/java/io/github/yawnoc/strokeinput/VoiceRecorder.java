/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Arrays;

/*
  Records 16 kHz mono speech into memory (never to storage) for offline recognition.
  The level callback (0..1) and the automatic stop at the time limit arrive on the main thread.
*/
public final class VoiceRecorder
{
  private static final String LOG_TAG = "VoiceRecorder";

  public static final int SAMPLE_RATE = 16000;
  static final int MAX_SECONDS = 30;

  public interface Listener
  {
    void onVoiceLevel(float level);
    void onVoiceTimeLimit();
    void onVoiceRecordingError();
  }

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private volatile Session activeSession;

  // A stopped reader must never see the next recording's flag or sample buffer.
  private static final class Session
  {
    final AudioRecord record;
    volatile boolean running = true;
    boolean released = false;
    Thread reader;
    float[] samples = new float[SAMPLE_RATE * 4];
    int sampleCount = 0;

    Session(final AudioRecord record)
    {
      this.record = record;
    }

    synchronized void stopAudio()
    {
      if (!released)
      {
        try
        {
          record.stop(); // unblock read before waiting for the reader
        }
        catch (IllegalStateException exception)
        {
          Log.w(LOG_TAG, "Recording already stopped", exception);
        }
      }
    }

    synchronized void releaseAudio()
    {
      stopAudio();
      record.release();
      released = true;
    }
  }

  public static boolean hasPermission(final Context context)
  {
    return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
  }

  public boolean isRecording()
  {
    return activeSession != null;
  }

  /*
    Returns false if recording could not start (no permission, or the microphone is busy).
  */
  public synchronized boolean start(final Context context, final Listener listener)
  {
    if (activeSession != null || !hasPermission(context))
    {
      return false;
    }

    final int minBufferBytes =
            AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    if (minBufferBytes <= 0)
    {
      return false;
    }
    AudioRecord audioRecord = null;
    try
    {
      audioRecord =
              new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBufferBytes, SAMPLE_RATE / 5 * 2)
              );
      if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED)
      {
        audioRecord.release();
        audioRecord = null;
        return false;
      }
      audioRecord.startRecording();
      if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
      {
        audioRecord.release();
        return false;
      }
    }
    catch (SecurityException | IllegalStateException | IllegalArgumentException exception)
    {
      Log.e(LOG_TAG, "Recording failed to start", exception);
      if (audioRecord != null)
      {
        audioRecord.release();
        audioRecord = null;
      }
      return false;
    }

    final Session session = new Session(audioRecord);
    activeSession = session;
    session.reader = new Thread(() -> readLoop(session, listener), "VoiceRecorder");
    session.reader.start();
    return true;
  }

  private void postForSession(final Session session, final Runnable callback)
  {
    mainHandler.post(() ->
    {
      if (activeSession == session)
      {
        callback.run();
      }
    });
  }

  private void readLoop(final Session session, final Listener listener)
  {
    final short[] chunk = new short[SAMPLE_RATE / 10]; // 100 ms
    try
    {
      while (session.running)
      {
        final int read = session.record.read(chunk, 0, chunk.length);
        if (!session.running)
        {
          break;
        }
        if (read < 0)
        {
          Log.w(LOG_TAG, "AudioRecord.read failed: " + read);
          postForSession(session, listener::onVoiceRecordingError);
          break;
        }
        if (read == 0)
        {
          Thread.sleep(10);
          continue;
        }
        double sumOfSquares = 0;
        final boolean atTimeLimit;
        synchronized (session)
        {
          if (!session.running)
          {
            break;
          }
          final int accepted = Math.min(read, MAX_SECONDS * SAMPLE_RATE - session.sampleCount);
          if (session.sampleCount + accepted > session.samples.length)
          {
            session.samples = Arrays.copyOf(
              session.samples, Math.min(MAX_SECONDS * SAMPLE_RATE, Math.max(session.samples.length * 2, session.sampleCount + accepted))
            );
          }
          for (int index = 0; index < accepted; index++)
          {
            final float value = chunk[index] / 32768f;
            session.samples[session.sampleCount++] = value;
            sumOfSquares += value * value;
          }
          atTimeLimit = session.sampleCount >= MAX_SECONDS * SAMPLE_RATE;
        }
        final float level = (float) Math.min(1, Math.sqrt(sumOfSquares / read) * 6);
        postForSession(session, () -> listener.onVoiceLevel(level));
        if (atTimeLimit)
        {
          postForSession(session, listener::onVoiceTimeLimit);
          break;
        }
      }
    }
    catch (RuntimeException exception)
    {
      Log.e(LOG_TAG, "Recording failed", exception);
      if (session.running)
      {
        postForSession(session, listener::onVoiceRecordingError);
      }
    }
    catch (InterruptedException exception)
    {
      Thread.currentThread().interrupt();
      if (session.running)
      {
        postForSession(session, listener::onVoiceRecordingError);
      }
    }
    finally
    {
      session.running = false;
      // Only the reader releases: a timed-out join cannot release a native handle still being read.
      session.releaseAudio();
    }
  }

  /*
    Stops recording and returns what was recorded (empty if nothing).
  */
  public synchronized float[] stop()
  {
    final Session session = activeSession;
    if (session == null)
    {
      return new float[0];
    }
    activeSession = null; // discard queued level/error/limit callbacks before another session starts
    session.running = false;
    session.stopAudio();
    if (session.reader != Thread.currentThread())
    {
      try
      {
        session.reader.join(250);
      }
      catch (InterruptedException exception)
      {
        Thread.currentThread().interrupt();
      }
    }
    synchronized (session)
    {
      final float[] recorded = Arrays.copyOf(session.samples, session.sampleCount);
      Arrays.fill(session.samples, 0);
      session.sampleCount = 0;
      return recorded;
    }
  }
}
