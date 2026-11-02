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
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/*
  Uses the real APK models and two independently transcribed FLEURS recordings.
  Test assets and attribution are under androidTest/assets/voice, not production assets.
  This checks native integration, not population-level ASR accuracy or microphone acoustics.
*/
@RunWith(AndroidJUnit4.class)
public class VoiceRecogniserTest
{
  private static final String LOG_TAG = "VoiceRecogniserTest";
  private static VoiceRecogniser recogniser;

  private static Context targetContext()
  {
    return InstrumentationRegistry.getInstrumentation().getTargetContext();
  }

  @BeforeClass
  public static void loadModel()
  {
    recogniser = new VoiceRecogniser(targetContext(), () -> {});
    final long deadline = SystemClock.uptimeMillis() + 60_000;
    while (!recogniser.isReady() && !recogniser.hasFailed() && SystemClock.uptimeMillis() < deadline)
    {
      SystemClock.sleep(50);
    }
    assertFalse("voice model failed to load from APK", recogniser.hasFailed());
    assertTrue("voice model was not ready within 60 seconds", recogniser.isReady());
  }

  @AfterClass
  public static void closeModel()
  {
    if (recogniser != null)
    {
      recogniser.close();
      recogniser.close();
      assertFalse("closed voice model must not stay ready", recogniser.isReady());
      assertEquals("", recogniser.recogniseNow(new float[VoiceRecorder.SAMPLE_RATE]));
    }
  }

  @Test
  public void mandarin_recordingContainsReferenceKeywords() throws Exception
  {
    final String text = recogniser.recogniseNow(waveform("mandarin.wav"));
    Log.i(LOG_TAG, "Mandarin fixture -> " + text);
    // Reference: 但鸟类还是有很多地方跟恐龙相似。
    assertTrue(text, text.contains("恐龙") || text.contains("恐龍"));
    assertTrue(text, text.contains("相似"));
  }

  @Test
  public void cantonese_recordingContainsReferenceKeywords() throws Exception
  {
    final String text = recogniser.recogniseNow(waveform("cantonese.wav"));
    Log.i(LOG_TAG, "Cantonese fixture -> " + text);
    // Reference: 甘藍汁會根據化學物質的酸鹼度變色。
    assertTrue(text, text.contains("化学") || text.contains("化學"));
    assertTrue(text, text.contains("变色") || text.contains("變色"));
  }

  @Test
  public void emptyAndSilentAudio_doNotProduceInput()
  {
    assertEquals("", recogniser.recogniseNow(new float[0]));
    assertEquals("silence must not insert hallucinated input", "",
      recogniser.recogniseNow(new float[VoiceRecorder.SAMPLE_RATE * 2]));
  }

  @Test
  public void asynchronousResult_isDeliveredOnMainThread() throws Exception
  {
    final CountDownLatch resultReady = new CountDownLatch(1);
    final AtomicReference<String> result = new AtomicReference<>();
    final AtomicBoolean onMainThread = new AtomicBoolean();
    recogniser.recognise(waveform("mandarin.wav"), text ->
    {
      result.set(text);
      onMainThread.set(Looper.myLooper() == Looper.getMainLooper());
      resultReady.countDown();
    });
    assertTrue("recognition callback timed out", resultReady.await(30, TimeUnit.SECONDS));
    assertTrue("callback must update UI on the main thread", onMainThread.get());
    assertTrue(result.get(), result.get().contains("相似"));
  }

  @Test
  public void voiceAndHandwriting_runInTheSameProcess() throws Exception
  {
    // Both native runtimes are resident here; exercise each after the other has loaded.
    final HandwritingRecogniser handwriting = new HandwritingRecogniser(targetContext(), () -> {});
    try
    {
      final long deadline = SystemClock.uptimeMillis() + 30_000;
      while (!handwriting.isReady() && !handwriting.hasFailed() && SystemClock.uptimeMillis() < deadline)
      {
        SystemClock.sleep(50);
      }
      assertFalse("handwriting failed alongside voice", handwriting.hasFailed());
      assertTrue("handwriting did not become ready", handwriting.isReady());
      final List<float[]> strokes = Arrays.asList(
        new float[]{70, 100, 150, 100}, new float[]{110, 60, 110, 140}
      );
      final List<String> candidates = handwriting.recogniseNow(strokes, 200);
      assertTrue(candidates.toString(), candidates.contains("十"));
      final String voice = recogniser.recogniseNow(waveform("mandarin.wav"));
      assertTrue(voice, voice.contains("相似"));
      assertTrue(handwriting.recogniseNow(strokes, 200).contains("十"));
    }
    finally
    {
      handwriting.close();
    }
  }

  /* Decode RIFF PCM rather than assuming a fixed WAV header length. */
  private static float[] waveform(final String fileName) throws Exception
  {
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext()
      .getAssets().open("voice/" + fileName))
    {
      final byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1)
      {
        output.write(buffer, 0, count);
      }
    }
    final byte[] bytes = output.toByteArray();
    final ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    assertTrue("WAV too short", bytes.length >= 12);
    assertEquals("RIFF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
    assertEquals("WAVE", new String(bytes, 8, 4, StandardCharsets.US_ASCII));
    boolean formatChecked = false;
    int offset = 12;
    while (offset + 8 <= bytes.length)
    {
      final String chunk = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
      final int size = data.getInt(offset + 4);
      final int start = offset + 8;
      assertTrue("invalid WAV chunk", size >= 0 && size <= bytes.length - start);
      if (chunk.equals("fmt "))
      {
        assertTrue("incomplete WAV format", size >= 16);
        assertEquals("PCM format", 1, data.getShort(start));
        assertEquals("mono", 1, data.getShort(start + 2));
        assertEquals("sample rate", VoiceRecorder.SAMPLE_RATE, data.getInt(start + 4));
        assertEquals("16 bit PCM", 16, data.getShort(start + 14));
        formatChecked = true;
      }
      else if (chunk.equals("data"))
      {
        assertTrue("WAV format must precede samples", formatChecked);
        assertTrue("PCM samples must be complete", size > 0 && size % 2 == 0);
        final float[] samples = new float[size / 2];
        for (int index = 0; index < samples.length; index++)
        {
          samples[index] = data.getShort(start + index * 2) / 32768f;
        }
        return samples;
      }
      offset = start + size + size % 2;
    }
    throw new AssertionError("WAV has no sample data: " + fileName);
  }
}
