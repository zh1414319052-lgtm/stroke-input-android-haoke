/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
  Offline speech recognition with sherpa-onnx (Apache-2.0); the model is read straight from the APK assets
  (see VoiceModel for which model). Loading and recognition run on one background thread;
  results come back on the main thread. Nothing is recorded to storage or sent anywhere.
*/
public final class VoiceRecogniser
{
  private static final String LOG_TAG = "VoiceRecogniser";
  private static final int THREADS = 4;

  public interface StateListener
  {
    void onVoiceRecogniserStateChanged();
  }

  public interface ResultListener
  {
    void onVoiceRecognised(String text);
  }

  private enum State { LOADING, READY, FAILED }

  private final AssetManager assetManager;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final Object submissionLock = new Object();
  private final Object nativeLock = new Object();
  private volatile boolean closed = false;
  private volatile State state = State.LOADING;
  private OfflineRecognizer recognizer;

  public VoiceRecogniser(final Context context, final StateListener stateListener)
  {
    assetManager = context.getApplicationContext().getAssets();
    executor.execute(
      () ->
      {
        synchronized (nativeLock)
        {
          if (closed)
          {
            return;
          }
          state = load() ? State.READY : State.FAILED;
        }
        mainHandler.post(() ->
        {
          if (!closed)
          {
            stateListener.onVoiceRecogniserStateChanged();
          }
        });
      }
    );
  }

  public boolean isReady()
  {
    return !closed && state == State.READY;
  }

  public boolean hasFailed()
  {
    return closed || state == State.FAILED;
  }

  private boolean load()
  {
    final long startMilliseconds = System.currentTimeMillis();
    try
    {
      final OfflineModelConfig modelConfig = new OfflineModelConfig();
      VoiceModel.configure(modelConfig);
      modelConfig.setNumThreads(THREADS);
      modelConfig.setProvider("cpu");
      modelConfig.setDebug(false);

      final FeatureConfig featureConfig = new FeatureConfig();
      featureConfig.setSampleRate(VoiceRecorder.SAMPLE_RATE);
      featureConfig.setFeatureDim(VoiceModel.FEATURE_DIM);

      final OfflineRecognizerConfig config = new OfflineRecognizerConfig();
      config.setFeatConfig(featureConfig);
      config.setModelConfig(modelConfig);
      config.setDecodingMethod("greedy_search");

      recognizer = new OfflineRecognizer(assetManager, config);
      Log.i(LOG_TAG, "Loaded " + VoiceModel.NAME + " in " + (System.currentTimeMillis() - startMilliseconds) + " ms");
      return true;
    }
    catch (RuntimeException | UnsatisfiedLinkError exception)
    {
      Log.e(LOG_TAG, "Loading the voice model failed", exception);
      return false;
    }
  }

  public void recognise(final float[] samples, final ResultListener resultListener)
  {
    synchronized (submissionLock)
    {
      if (closed)
      {
        return;
      }
      executor.execute(
        () ->
        {
          final String text = recogniseNow(samples);
          mainHandler.post(() ->
          {
            if (!closed)
            {
              resultListener.onVoiceRecognised(text);
            }
          });
        }
      );
    }
  }

  /*
    Recognition on the calling thread (the background thread, or a test); empty if not ready or on error.
  */
  String recogniseNow(final float[] samples)
  {
    synchronized (nativeLock)
    {
      return recogniseLocked(samples);
    }
  }

  private String recogniseLocked(final float[] samples)
  {
    if (!isReady() || !VoiceAudio.hasSignal(samples))
    {
      return "";
    }
    final long startMilliseconds = System.currentTimeMillis();
    OfflineStream stream = null;
    try
    {
      stream = recognizer.createStream();
      stream.acceptWaveform(samples, VoiceRecorder.SAMPLE_RATE);
      recognizer.decode(stream);
      final String text = recognizer.getResult(stream).getText();
      Log.i(
        LOG_TAG,
        "Recognised " + samples.length / VoiceRecorder.SAMPLE_RATE + " s in "
          + (System.currentTimeMillis() - startMilliseconds) + " ms"
      );
      return VoiceModel.tidy(text);
    }
    catch (RuntimeException | UnsatisfiedLinkError exception)
    {
      Log.e(LOG_TAG, "Voice recognition failed", exception);
      return "";
    }
    finally
    {
      if (stream != null)
      {
        stream.release();
      }
    }
  }

  /*
    Frees the model's memory (on the background thread, after any recognition in progress).
  */
  public void close()
  {
    synchronized (submissionLock)
    {
      if (closed)
      {
        return;
      }
      closed = true; // suppress already-posted callbacks immediately
      executor.execute(
        () ->
        {
          synchronized (nativeLock)
          {
            if (recognizer != null)
            {
              recognizer.release();
              recognizer = null;
            }
            state = State.FAILED;
          }
        }
      );
      executor.shutdown();
    }
  }
}
