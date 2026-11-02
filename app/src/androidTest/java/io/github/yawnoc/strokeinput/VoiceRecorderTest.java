/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

/*
  Run on the explicitly selected test device with RECORD_AUDIO temporarily granted externally.
  The test runner must record the original grant state and restore it after instrumentation exits:
  revoking the target app's permission inside this process can kill the instrumentation process.
  Audio stays in memory. A successful read with zero amplitude does NOT validate the host microphone.
*/
@RunWith(AndroidJUnit4.class)
public class VoiceRecorderTest
{
  @Test
  public void recording_canStopAndStartAgain_withoutSharingSessionSamples()
  {
    final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    assertTrue("precondition: temporarily grant RECORD_AUDIO externally and restore after this run",
      VoiceRecorder.hasPermission(context));
    final VoiceRecorder recorder = new VoiceRecorder();
    final AtomicBoolean recordingError = new AtomicBoolean();
    final AtomicBoolean unexpectedTimeLimit = new AtomicBoolean();
    final VoiceRecorder.Listener listener = new VoiceRecorder.Listener()
    {
      @Override
      public void onVoiceLevel(final float level)
      {
        // Levels are informative; samples below validate the numeric values directly.
      }

      @Override
      public void onVoiceTimeLimit()
      {
        unexpectedTimeLimit.set(true);
      }

      @Override
      public void onVoiceRecordingError()
      {
        recordingError.set(true);
      }
    };

    // AudioRecord needs a foreground app on modern Android, just like the visible input method.
    try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class))
    {
      for (int session = 1; session <= 2; session++)
      {
        assertTrue("microphone did not start, session " + session, recorder.start(context, listener));
        assertTrue(recorder.isRecording());
        SystemClock.sleep(1_000);
        final float[] samples = recorder.stop();
        assertFalse("recording flag stayed active after stop", recorder.isRecording());
        assertFalse("AudioRecord returned a read/start failure", recordingError.get());
        assertFalse("one second must not reach the thirty second limit", unexpectedTimeLimit.get());
        assertTrue("too few captured samples: " + samples.length,
          samples.length >= VoiceRecorder.SAMPLE_RATE / 2);
        assertTrue("session unexpectedly contains more than three seconds: " + samples.length,
          samples.length <= VoiceRecorder.SAMPLE_RATE * 3);

        float peak = 0;
        double squareSum = 0;
        for (final float sample : samples)
        {
          assertTrue("invalid PCM sample", Float.isFinite(sample) && Math.abs(sample) <= 1);
          peak = Math.max(peak, Math.abs(sample));
          squareSum += sample * sample;
        }
        final double rms = Math.sqrt(squareSum / samples.length);
        Log.i("VoiceRecorderTest", "session=" + session + " samples=" + samples.length
          + " peak=" + peak + " rms=" + rms
          + " acoustic_signal=" + (peak > 0.0001f ? "present_not_transcribed" : "SILENT_UNVERIFIED"));
        assertEquals("second stop must not return an earlier session", 0, recorder.stop().length);
      }
    }
    finally
    {
      recorder.stop();
    }
  }
}
