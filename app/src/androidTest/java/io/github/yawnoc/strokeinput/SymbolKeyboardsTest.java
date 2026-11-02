/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/*
  The six strokes symbol pages: page keys lead through all of them in order, rows fill the width,
  and the first four pages repeat no symbol.
*/
@RunWith(AndroidJUnit4.class)
public class SymbolKeyboardsTest
{
  private static final int[] PAGE_LAYOUT_IDS =
          {
            R.xml.keyboard_strokes_symbols_1,
            R.xml.keyboard_strokes_symbols_common,
            R.xml.keyboard_strokes_symbols_shapes,
            R.xml.keyboard_strokes_symbols_math,
            R.xml.keyboard_strokes_symbols_2,
            R.xml.keyboard_strokes_symbols_3,
          };
  private static final String[] PAGE_SWITCH_VALUE_TEXTS =
          {
            "SWITCH_TO_STROKES_SYMBOLS_1",
            "SWITCH_TO_STROKES_SYMBOLS_COMMON",
            "SWITCH_TO_STROKES_SYMBOLS_SHAPES",
            "SWITCH_TO_STROKES_SYMBOLS_MATH",
            "SWITCH_TO_STROKES_SYMBOLS_2",
            "SWITCH_TO_STROKES_SYMBOLS_3",
          };

  private static Context targetContext()
  {
    return InstrumentationRegistry.getInstrumentation().getTargetContext();
  }

  @Test
  public void pageKeys_leadThroughAllSixPagesInOrder()
  {
    final int pageCount = PAGE_LAYOUT_IDS.length;
    for (int index = 0; index < pageCount; index++)
    {
      final Keyboard page = new Keyboard(targetContext(), PAGE_LAYOUT_IDS[index]);
      final String nextPageValueText = PAGE_SWITCH_VALUE_TEXTS[(index + 1) % pageCount];

      Key pageKey = null;
      for (final Key key : page.getKeys())
      {
        if (key.valueText.equals(nextPageValueText))
        {
          pageKey = key;
        }
      }
      assertTrue("page " + (index + 1) + " has no key to " + nextPageValueText, pageKey != null);
      assertEquals((index + 1) + "/" + pageCount, pageKey.displayText);
    }
  }

  @Test
  public void everyRow_fillsTheKeyboardWidth()
  {
    for (final int layoutId : PAGE_LAYOUT_IDS)
    {
      final Keyboard page = new Keyboard(targetContext(), layoutId);
      final int screenWidth = page.getScreenWidth();
      final Map<Integer, Integer> rowWidthFromY = new TreeMap<>();
      for (final Key key : page.getKeys())
      {
        assertTrue(key.valueText, key.x + key.width <= screenWidth + 2);
        rowWidthFromY.merge(key.naturalY, key.width, Integer::sum);
      }
      for (final int rowWidth : rowWidthFromY.values())
      {
        // upstream symbol pages 2 and 3 have short rows, so only the Haoke pages must be full
        if (layoutId != R.xml.keyboard_strokes_symbols_2 && layoutId != R.xml.keyboard_strokes_symbols_3)
        {
          assertTrue("row width " + rowWidth + " of " + screenWidth, Math.abs(rowWidth - screenWidth) <= 6);
        }
      }
    }
  }

  /*
    Pages 1 to 4 (upstream page 1 and the Haoke pages) repeat no symbol. The upstream tone page may repeat one
    on purpose: the middle dot · is also on the common page, where it is easy to find for names.
  */
  @Test
  public void symbols_appearOnOnePageOnly()
  {
    final Set<String> seen = new HashSet<>();
    for (int index = 0; index < 4; index++)
    {
      final int layoutId = PAGE_LAYOUT_IDS[index];
      for (final Key key : new Keyboard(targetContext(), layoutId).getKeys())
      {
        final String valueText = key.valueText;
        final boolean isSharedKey =
                valueText.startsWith("SWITCH_TO_")
                  || valueText.equals("BACKSPACE")
                  || valueText.equals("SPACE")
                  || valueText.equals("ENTER")
                  || valueText.equals(targetContext().getString(R.string.value_text__strokes_comma))
                  || valueText.equals(targetContext().getString(R.string.value_text__strokes_full_stop));
        if (!isSharedKey)
        {
          assertTrue("duplicate symbol " + valueText, seen.add(valueText));
        }
      }
    }
    assertTrue(seen.contains("——"));
    assertTrue(seen.contains("……"));
    assertTrue(seen.contains("①"));
    assertTrue(seen.contains("㎡"));
    assertTrue(seen.contains("\\"));
    assertTrue(seen.contains("@"));
  }
}
