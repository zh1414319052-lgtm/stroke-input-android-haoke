/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class HandwritingDecoderTest
{
  // label 0 is the CTC blank, the last label is the space
  private static final List<String> LABELS = Arrays.asList("", "十", "干", "千", "中", "国", " ");

  /*
    One row per time step; every row gets its listed probabilities, the rest of the row is spread on the blank.
  */
  private static float[] probabilities(final float[][] rows)
  {
    final float[] values = new float[rows.length * LABELS.size()];
    for (int timeStep = 0; timeStep < rows.length; timeStep++)
    {
      System.arraycopy(rows[timeStep], 0, values, timeStep * LABELS.size(), rows[timeStep].length);
    }
    return values;
  }

  private static List<String> decode(final float[][] rows)
  {
    return HandwritingDecoder.candidates(probabilities(rows), rows.length, LABELS, 3, 10);
  }

  @Test
  public void oneCharacter_bestThenRunnersUp()
  {
    final List<String> candidates =
            decode(
              new float[][]{
                {0.9f},
                {0.1f, 0.6f, 0.25f, 0.05f},
                {0.2f, 0.7f, 0.05f, 0.05f},
                {0.95f},
              }
            );
    assertEquals(Arrays.asList("十", "干", "千"), candidates);
  }

  @Test
  public void repeatsCollapse_andBlanksSeparateRepeatedCharacters()
  {
    // 十 十 (blank) 十 reads 十十
    final List<String> candidates =
            decode(
              new float[][]{
                {0.1f, 0.9f},
                {0.1f, 0.9f},
                {0.9f, 0.1f},
                {0.1f, 0.9f},
              }
            );
    assertEquals("十十", candidates.get(0));
  }

  @Test
  public void twoCharacters_variantsSwapOneCharacterByProbability()
  {
    final List<String> candidates =
            decode(
              new float[][]{
                {0.2f, 0f, 0f, 0f, 0.7f, 0.1f}, // 中, runner-up 国 (0.1)
                {0.9f},
                {0.1f, 0.3f, 0.6f}, // 干, runner-up 十 (0.3)
              }
            );
    assertEquals("中干", candidates.get(0));
    assertEquals("中十", candidates.get(1)); // the more likely swap comes first
    assertTrue(candidates.contains("国干"));
  }

  @Test
  public void spacesAndBlankOnlyOutput_giveNothing()
  {
    assertTrue(decode(new float[][]{{1f}, {1f}}).isEmpty());
    final List<String> spaced =
            decode(
              new float[][]{
                {0.1f, 0.9f},
                {0.1f, 0f, 0f, 0f, 0f, 0f, 0.9f}, // space between characters
                {0.1f, 0f, 0f, 0f, 0.9f},
              }
            );
    assertEquals("十中", spaced.get(0));
  }
}
