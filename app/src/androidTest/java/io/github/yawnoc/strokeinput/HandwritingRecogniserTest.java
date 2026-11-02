/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
  The bundled model on the device: simple characters drawn as straight strokes are recognised.
*/
@RunWith(AndroidJUnit4.class)
public class HandwritingRecogniserTest
{
  private static final String LOG_TAG = "HandwritingRecogniserTest";
  private static final float PAD_HEIGHT = 200;
  private static HandwritingRecogniser recogniser;

  @BeforeClass
  public static void loadModel()
  {
    recogniser =
            new HandwritingRecogniser(
              InstrumentationRegistry.getInstrumentation().getTargetContext(),
              () -> {}
            );
    final long deadline = SystemClock.uptimeMillis() + 30_000;
    while (!recogniser.isReady() && !recogniser.hasFailed() && SystemClock.uptimeMillis() < deadline)
    {
      SystemClock.sleep(50);
    }
  }

  @AfterClass
  public static void closeModel()
  {
    recogniser.close();
  }

  /*
    Strokes in a 100 x 100 box, as x0, y0, x1, y1, ...
  */
  private static final Map<String, float[][]> CHARACTERS = new LinkedHashMap<>();
  static
  {
    CHARACTERS.put("一", new float[][]{{10, 50, 90, 50}});
    CHARACTERS.put("十", new float[][]{{10, 50, 90, 50}, {50, 10, 50, 90}});
    CHARACTERS.put("二", new float[][]{{25, 35, 75, 35}, {10, 70, 90, 70}});
    CHARACTERS.put("三", new float[][]{{20, 25, 80, 25}, {25, 50, 75, 50}, {10, 80, 90, 80}});
    CHARACTERS.put("口", new float[][]{{15, 15, 15, 85}, {15, 15, 85, 15, 85, 85}, {15, 85, 85, 85}});
    CHARACTERS.put("人", new float[][]{{50, 10, 48, 40, 30, 70, 10, 90}, {50, 45, 70, 70, 90, 90}});
    CHARACTERS.put("大", new float[][]{{10, 40, 90, 40}, {50, 10, 48, 50, 30, 75, 10, 92}, {52, 52, 70, 75, 92, 92}});
    CHARACTERS.put("中", new float[][]{{20, 30, 20, 70}, {20, 30, 80, 30, 80, 70}, {20, 70, 80, 70}, {50, 5, 50, 95}});
    CHARACTERS.put("王", new float[][]{{20, 15, 80, 15}, {25, 50, 75, 50}, {10, 88, 90, 88}, {50, 15, 50, 88}});
    CHARACTERS.put("木", new float[][]{{10, 35, 90, 35}, {50, 5, 50, 95}, {48, 38, 30, 65, 10, 80}, {52, 38, 70, 65, 90, 80}});
  }

  private static List<float[]> placed(final float[][] strokes, final float left)
  {
    final List<float[]> placedStrokes = new ArrayList<>();
    for (final float[] stroke : strokes)
    {
      final float[] copy = stroke.clone();
      for (int index = 0; index < copy.length; index += 2)
      {
        copy[index] += left;
        copy[index + 1] += 50;
      }
      placedStrokes.add(copy);
    }
    return placedStrokes;
  }

  @Test
  public void model_loadsFromTheApk()
  {
    assertFalse("model failed to load", recogniser.hasFailed());
    assertTrue("model not ready", recogniser.isReady());
  }

  @Test
  public void simpleCharacters_areAmongTheFirstCandidates()
  {
    final List<String> misses = new ArrayList<>();
    for (final Map.Entry<String, float[][]> entry : CHARACTERS.entrySet())
    {
      final List<String> candidates = recogniser.recogniseNow(placed(entry.getValue(), 60), PAD_HEIGHT);
      Log.i(LOG_TAG, entry.getKey() + " -> " + candidates);
      if (!candidates.subList(0, Math.min(5, candidates.size())).contains(entry.getKey()))
      {
        misses.add(entry.getKey() + " -> " + candidates);
      }
    }
    assertTrue("not recognised: " + misses, misses.size() <= 1); // straight-line drawings, so allow one miss
  }

  @Test
  public void twoCharactersSideBySide_areReadTogether()
  {
    final List<float[]> strokes = new ArrayList<>(placed(CHARACTERS.get("十"), 20));
    strokes.addAll(placed(CHARACTERS.get("口"), 160));
    final List<String> candidates = recogniser.recogniseNow(strokes, PAD_HEIGHT);
    Log.i(LOG_TAG, "十口 -> " + candidates);
    assertTrue(candidates.toString(), candidates.contains("十口"));
  }
}
