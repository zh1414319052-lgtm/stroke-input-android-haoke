/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class KeyboardThemeTest
{
  @Test
  public void forName_returnsEachThemeAndFallsBackToDefault()
  {
    assertEquals(KeyboardTheme.PAPER, KeyboardTheme.forName(KeyboardTheme.PAPER).name);
    assertEquals(KeyboardTheme.SKY, KeyboardTheme.forName(KeyboardTheme.SKY).name);
    assertEquals(KeyboardTheme.NIGHT, KeyboardTheme.forName(KeyboardTheme.NIGHT).name);
    assertSame(KeyboardTheme.forName(KeyboardTheme.DEFAULT), KeyboardTheme.forName(null));
    assertSame(KeyboardTheme.forName(KeyboardTheme.DEFAULT), KeyboardTheme.forName("UNKNOWN"));
  }

  @Test
  public void keyRoles_pickFillAndTextColours()
  {
    final KeyboardTheme theme = KeyboardTheme.forName(KeyboardTheme.PAPER);

    for (final String strokeKey : new String[]{"STROKE_1", "STROKE_5", "STROKE_6"})
    {
      assertEquals(theme.keyStrokeFill, theme.fillForKey(strokeKey));
      assertEquals(theme.strokeText, theme.textForKey(strokeKey));
    }

    assertEquals(theme.keyEnterFill, theme.fillForKey(StrokeInputService.ENTER_KEY_VALUE_TEXT));
    assertEquals(theme.enterText, theme.textForKey(StrokeInputService.ENTER_KEY_VALUE_TEXT));

    for (final String functionKey : new String[]{"BACKSPACE", "SHIFT", "SWITCH_TO_STROKES", "SWITCH_TO_QWERTY"})
    {
      assertEquals(theme.keyFunctionFill, theme.fillForKey(functionKey));
      assertEquals(theme.keyText, theme.textForKey(functionKey));
    }

    assertEquals(theme.keyCharacterFill, theme.fillForKey(StrokeInputService.SPACE_BAR_VALUE_TEXT));
    assertEquals(theme.spaceBarText, theme.textForKey(StrokeInputService.SPACE_BAR_VALUE_TEXT));
    assertEquals(theme.keyCharacterFill, theme.fillForKey("，"));
    assertEquals(theme.keyText, theme.textForKey("a"));

    // the handwriting and voice pads look like stroke keys, and their ink and microphone take the stroke colour
    for (final String padKey : new String[]{StrokeInputService.HANDWRITING_PAD_VALUE_TEXT, StrokeInputService.VOICE_PAD_VALUE_TEXT})
    {
      assertEquals(theme.keyStrokeFill, theme.fillForKey(padKey));
      assertEquals(theme.strokeText, theme.textForKey(padKey));
    }
  }

  @Test
  public void themes_haveDistinctKeyboards()
  {
    final KeyboardTheme paper = KeyboardTheme.forName(KeyboardTheme.PAPER);
    final KeyboardTheme sky = KeyboardTheme.forName(KeyboardTheme.SKY);
    final KeyboardTheme night = KeyboardTheme.forName(KeyboardTheme.NIGHT);
    assertNotEquals(paper.keyboardFill, sky.keyboardFill);
    assertNotEquals(sky.keyboardFill, night.keyboardFill);
    assertNotEquals(paper.keyEnterFill, night.keyEnterFill);
    assertEquals(0, night.keyShadow); // the dark theme draws no key shadow
  }
}
