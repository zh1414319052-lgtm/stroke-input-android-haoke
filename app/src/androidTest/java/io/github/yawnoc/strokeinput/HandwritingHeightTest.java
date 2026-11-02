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
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/*
  Real Android dimensions and XML keyboards, with a separate preference file so the user's height,
  theme and keyboard choices remain untouched. Does not load voice or handwriting recognition models.
*/
@RunWith(AndroidJUnit4.class)
public class HandwritingHeightTest
{
  private static final String TEST_PREFERENCES = "instrumentationHandwritingHeight";
  private Context context;

  private static Context isolatedContext()
  {
    final Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
    return new ContextWrapper(target)
    {
      @Override
      public Context getApplicationContext()
      {
        return this;
      }

      @Override
      public SharedPreferences getSharedPreferences(final String name, final int mode)
      {
        return target.getSharedPreferences(TEST_PREFERENCES, mode);
      }
    };
  }

  private SharedPreferences preferences()
  {
    return context.getSharedPreferences(StrokeInputService.PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
  }

  @Before
  public void prepareIsolatedSettings()
  {
    context = isolatedContext();
    assertTrue(preferences().edit().clear().putInt(MainActivity.KEYBOARD_HEIGHT_ADJUSTMENT_PROGRESS_KEY, 7).commit());
  }

  @After
  public void clearOnlyTestSettings()
  {
    assertTrue(preferences().edit().clear().commit());
  }

  @Test
  public void lowAndHighSettings_resizeOnlyThePad_andRestoreNormalGeometry()
  {
    final Keyboard handwriting = new Keyboard(context, R.xml.keyboard_handwriting);
    final List<int[]> naturalGeometry = geometry(handwriting);
    final int naturalKeyboardHeight = handwriting.getHeight();
    final Key pad = findKey(handwriting, "HANDWRITING_PAD");

    TypingSettings.saveHandwritingHeightDp(context, 180);
    handwriting.adjustHalfScreenHandwritingHeight(px(1_000));
    assertEquals(px(180), pad.height);
    final List<int[]> lowGeometry = geometry(handwriting);
    final int lowKeyboardHeight = handwriting.getHeight();
    final int lowPadBottom = pad.y + pad.height;

    TypingSettings.saveHandwritingHeightDp(context, 400);
    assertTrue(handwriting.adjustHalfScreenHandwritingHeight(px(1_000)));
    assertEquals(px(400), pad.height);
    final int difference = px(400) - px(180);
    assertEquals(lowKeyboardHeight + difference, handwriting.getHeight());
    for (int index = 0; index < handwriting.getKeys().size(); index++)
    {
      final Key key = handwriting.getKeys().get(index);
      final int[] before = lowGeometry.get(index);
      assertEquals(key.valueText + " x", before[0], key.x);
      assertEquals(key.valueText + " width", before[2], key.width);
      assertEquals(key.valueText + " text offset", before[4], key.textOffsetY);
      assertEquals(key.valueText + " preview offset", before[5], key.previewMarginY);
      if (key == pad)
      {
        assertEquals(before[1], key.y);
      }
      else
      {
        assertEquals(key.valueText + " function-key height", before[3], key.height);
        assertEquals(key.valueText + " bottom-row position",
          before[1] + (before[1] >= lowPadBottom ? difference : 0), key.y);
      }
    }
    assertFalse("unchanged height must not trigger another resize",
      handwriting.adjustHalfScreenHandwritingHeight(px(1_000)));

    // InputContainer uses this exact restoration path when entering full-screen handwriting.
    handwriting.adjustKeyboardHeight();
    assertEquals(naturalKeyboardHeight, handwriting.getHeight());
    assertGeometryEquals(naturalGeometry, handwriting);
    handwriting.adjustHalfScreenHandwritingHeight(px(1_000));
    assertEquals("returning to half screen restores the chosen pad size", px(400), pad.height);

    for (final int layout : new int[]{R.xml.keyboard_strokes, R.xml.keyboard_voice})
    {
      final Keyboard other = new Keyboard(context, layout);
      final List<int[]> before = geometry(other);
      final int beforeHeight = other.getHeight();
      assertFalse(other.adjustHalfScreenHandwritingHeight(px(100)));
      assertEquals(beforeHeight, other.getHeight());
      assertGeometryEquals(before, other);
    }
    assertEquals("ordinary keyboard scaling preference must stay independent", 7,
      MainActivity.loadSavedKeyboardHeightAdjustmentProgress(context));
  }

  @Test
  public void savedHeight_survivesContextRecreation_andFitsTemporarySpaceLimits()
  {
    assertEquals(260, TypingSettings.loadHandwritingHeightDp(context));
    TypingSettings.saveHandwritingHeightDp(context, 400);
    final Context recreated = isolatedContext();
    assertEquals(400, TypingSettings.loadHandwritingHeightDp(recreated));
    final Keyboard handwriting = new Keyboard(recreated, R.xml.keyboard_handwriting);
    final Key pad = findKey(handwriting, "HANDWRITING_PAD");
    final int fixedHeight = handwriting.getHeight() - pad.height;
    final int functionKeyHeight = findKey(handwriting, "SPACE").height;
    final int availableHeight = fixedHeight + px(80);
    handwriting.adjustHalfScreenHandwritingHeight(availableHeight);
    assertEquals("short parent must limit the pad rather than the function row", px(80), pad.height);
    assertEquals(availableHeight, handwriting.getHeight());
    assertEquals(functionKeyHeight, findKey(handwriting, "SPACE").height);
    assertEquals("available space must not overwrite the chosen preference", 400,
      TypingSettings.loadHandwritingHeightDp(isolatedContext()));

    handwriting.adjustHalfScreenHandwritingHeight(px(1_000));
    assertEquals("growing the window restores the chosen size", px(400), pad.height);
    assertEquals(functionKeyHeight, findKey(handwriting, "SPACE").height);
    for (final Key key : handwriting.getKeys())
    {
      assertTrue(key.valueText, key.y >= 0 && key.y + key.height <= handwriting.getHeight() + 2);
    }
  }

  @Test
  public void keyboardAreaHandwriting_defaultsOn_andPersistsWhenTurnedOff()
  {
    assertTrue(TypingSettings.loadBoolean(
      context,
      TypingSettings.HANDWRITING_ON_KEYBOARD_PREFERENCE_KEY,
      TypingSettings.HANDWRITING_ON_KEYBOARD_DEFAULT
    ));
    TypingSettings.saveBoolean(context, TypingSettings.HANDWRITING_ON_KEYBOARD_PREFERENCE_KEY, false);
    assertFalse(TypingSettings.loadBoolean(
      isolatedContext(),
      TypingSettings.HANDWRITING_ON_KEYBOARD_PREFERENCE_KEY,
      TypingSettings.HANDWRITING_ON_KEYBOARD_DEFAULT
    ));
  }

  private int px(final int dp)
  {
    return Math.round(dp * context.getResources().getDisplayMetrics().density);
  }

  private static Key findKey(final Keyboard keyboard, final String value)
  {
    for (final Key key : keyboard.getKeys())
    {
      if (value.equals(key.valueText))
      {
        return key;
      }
    }
    throw new AssertionError("Missing key: " + value);
  }

  private static List<int[]> geometry(final Keyboard keyboard)
  {
    final List<int[]> result = new ArrayList<>();
    for (final Key key : keyboard.getKeys())
    {
      result.add(new int[]{key.x, key.y, key.width, key.height, key.textOffsetY, key.previewMarginY});
    }
    return result;
  }

  private static void assertGeometryEquals(final List<int[]> expected, final Keyboard keyboard)
  {
    final List<int[]> actual = geometry(keyboard);
    assertEquals(expected.size(), actual.size());
    for (int index = 0; index < actual.size(); index++)
    {
      assertArrayEquals(keyboard.getKeys().get(index).valueText, expected.get(index), actual.get(index));
    }
  }
}
