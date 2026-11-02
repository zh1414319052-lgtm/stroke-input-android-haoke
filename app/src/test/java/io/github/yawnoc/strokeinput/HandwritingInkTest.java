/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HandwritingInkTest
{
  private static final float DELTA = 1e-3f;

  @Test
  public void strokes_areRecordedUndoneAndCleared()
  {
    final HandwritingInk ink = new HandwritingInk();
    assertTrue(ink.isEmpty());

    ink.startStroke(1, 2);
    ink.addPoint(3, 4);
    assertFalse(ink.isEmpty());
    assertArrayEquals(new float[]{1, 2, 3, 4}, ink.getStrokes().get(0), DELTA); // includes the stroke in progress
    ink.finishStroke();

    for (int index = 0; index < 100; index++) // grows past the initial buffer
    {
      if (index == 0)
      {
        ink.startStroke(0, 0);
      }
      ink.addPoint(index, index);
    }
    ink.finishStroke();
    assertEquals(2, ink.getStrokes().size());
    assertEquals(202, ink.getStrokes().get(1).length);

    final int version = ink.getVersion();
    ink.removeLastStroke();
    assertNotEquals(version, ink.getVersion());
    assertEquals(1, ink.getStrokes().size());

    ink.clear();
    assertTrue(ink.isEmpty());
  }

  @Test
  public void isTap_onlyForInkInASmallSquare()
  {
    final HandwritingInk ink = new HandwritingInk();
    assertFalse(ink.isTap(10)); // no ink

    ink.startStroke(100, 100);
    ink.addPoint(104, 106);
    ink.finishStroke();
    assertTrue(ink.isTap(10));

    ink.startStroke(100, 100);
    ink.addPoint(130, 100); // a short horizontal stroke is writing (e.g. 一)
    ink.finishStroke();
    assertFalse(ink.isTap(10));
  }

  @Test
  public void lineLayout_fitsTheInkIntoTheLineHeightWithMargins()
  {
    // a 100 x 100 square of ink on a 400-pixel-high pad
    final List<float[]> square = Collections.singletonList(new float[]{100, 100, 200, 100, 200, 200, 100, 200});
    final HandwritingInk.LineLayout layout = HandwritingInk.lineLayout(square, 400, 48);

    // box height is max(100, 0.3 * 400) = 120, plus 12 % margins top and bottom
    final float expectedScale = 48 / (120 * 1.24f);
    assertEquals(expectedScale, layout.scale, DELTA);
    // the ink is centred
    assertEquals(24, 150 * layout.scale + layout.offsetY, DELTA);
    assertEquals(layout.width / 2f, 150 * layout.scale + layout.offsetX, DELTA);
    assertTrue(layout.width >= 48);
  }

  @Test
  public void lineLayout_keepsAWideLineWide()
  {
    // two characters side by side: 400 wide, 150 high
    final List<float[]> line = Arrays.asList(new float[]{0, 0, 150, 150}, new float[]{250, 0, 400, 150});
    final HandwritingInk.LineLayout layout = HandwritingInk.lineLayout(line, 300, 48);
    final float fullHeight = 150 * 1.24f;
    final float fullWidth = 400 + 2 * 0.12f * 150;
    assertEquals((int) Math.ceil(fullWidth * 48 / fullHeight), layout.width);
  }
}
