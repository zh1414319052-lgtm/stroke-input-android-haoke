/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

/*
  Colour palettes of the three Haoke themes for the input method (keyboard, candidates, toolbar).
  Values are the tokens of design/stroke-input-styles.haoke-ui.json (boards style-a, style-b, style-c);
  the settings page uses the matching Android themes Theme.StrokeInput.Paper / .Sky / .Night.
*/
public final class KeyboardTheme
{
  public static final String PAPER = "PAPER"; // 宣纸朱印
  public static final String SKY = "SKY"; // 晴空大字
  public static final String NIGHT = "NIGHT"; // 黛青夜色
  public static final String DEFAULT = SKY;

  public final String name;

  // Keyboard
  public final int keyboardFill;
  public final int keyStrokeFill;
  public final int keyCharacterFill;
  public final int keyFunctionFill;
  public final int keyEnterFill;
  public final int keyShadow; // 0 for none
  public final int strokeText;
  public final int keyText;
  public final int spaceBarText;
  public final int enterText;
  public final float keyRadiusDp;
  public final float keyGapDp;

  // Candidates bar, toolbar and stroke sequence bar
  public final int candidatesFill;
  public final int candidateText;
  public final int firstCandidateText;
  public final int accent;
  public final int onAccent;
  public final int strokeSequenceFill;
  public final int strokeSequenceText;

  private KeyboardTheme(
    final String name,
    final int keyboardFill, final int keyStrokeFill, final int keyCharacterFill, final int keyFunctionFill,
    final int keyEnterFill, final int keyShadow,
    final int strokeText, final int keyText, final int spaceBarText, final int enterText,
    final float keyRadiusDp, final float keyGapDp,
    final int candidatesFill, final int candidateText, final int firstCandidateText,
    final int accent, final int onAccent, final int strokeSequenceFill, final int strokeSequenceText
  )
  {
    this.name = name;
    this.keyboardFill = keyboardFill;
    this.keyStrokeFill = keyStrokeFill;
    this.keyCharacterFill = keyCharacterFill;
    this.keyFunctionFill = keyFunctionFill;
    this.keyEnterFill = keyEnterFill;
    this.keyShadow = keyShadow;
    this.strokeText = strokeText;
    this.keyText = keyText;
    this.spaceBarText = spaceBarText;
    this.enterText = enterText;
    this.keyRadiusDp = keyRadiusDp;
    this.keyGapDp = keyGapDp;
    this.candidatesFill = candidatesFill;
    this.candidateText = candidateText;
    this.firstCandidateText = firstCandidateText;
    this.accent = accent;
    this.onAccent = onAccent;
    this.strokeSequenceFill = strokeSequenceFill;
    this.strokeSequenceText = strokeSequenceText;
  }

  private static final KeyboardTheme PAPER_THEME =
          new KeyboardTheme(
            PAPER,
            0xFFE6DDCB, 0xFFFFFDF8, 0xFFF7F1E5, 0xFFD8CDB8,
            0xFFC0392B, 0x18000000,
            0xFF1E1B17, 0xFF3A352E, 0xFF8A8274, 0xFFFFFFFF,
            8, 5,
            0xFFFBF7EE, 0xFF3A352E, 0xFFC0392B,
            0xFFC0392B, 0xFFFFFFFF, 0xFF2B2722, 0xFFFFF4DE
          );

  private static final KeyboardTheme SKY_THEME =
          new KeyboardTheme(
            SKY,
            0xFFDFE6F0, 0xFFFFFFFF, 0xFFF8FAFD, 0xFFE8EFFD,
            0xFF2457D6, 0x1F0F172A,
            0xFF2457D6, 0xFF17243B, 0xFF627089, 0xFFFFFFFF,
            10, 6,
            0xFFFFFFFF, 0xFF17243B, 0xFF2457D6,
            0xFF2457D6, 0xFFFFFFFF, 0xFF2457D6, 0xFFFFFFFF
          );

  private static final KeyboardTheme NIGHT_THEME =
          new KeyboardTheme(
            NIGHT,
            0xFF081012, 0xFF1C2A2F, 0xFF152226, 0xFF0F1A1D,
            0xFF14B8A6, 0,
            0xFF5EEAD4, 0xFFDCE7E5, 0xFF7F9994, 0xFF042F2B,
            8, 4,
            0xFF0F181B, 0xFFCFE3DF, 0xFF2DD4BF,
            0xFF2DD4BF, 0xFF042F2B, 0xFF13302D, 0xFF5EEAD4
          );

  /*
    The theme for a saved preference value; unknown or missing values give the default theme.
  */
  public static KeyboardTheme forName(final String name)
  {
    if (PAPER.equals(name))
    {
      return PAPER_THEME;
    }
    if (NIGHT.equals(name))
    {
      return NIGHT_THEME;
    }
    return SKY_THEME;
  }

  /*
    Key roles decide the colours: stroke keys, ordinary characters, function keys and the enter key.
    The handwriting and voice pads look like stroke keys, and their ink and microphone take the stroke colour.
  */
  public int fillForKey(final String valueText)
  {
    if (isStrokeKey(valueText) || isPadKey(valueText))
    {
      return keyStrokeFill;
    }
    if (isEnterKey(valueText))
    {
      return keyEnterFill;
    }
    if (isFunctionKey(valueText))
    {
      return keyFunctionFill;
    }
    return keyCharacterFill;
  }

  public int textForKey(final String valueText)
  {
    if (isStrokeKey(valueText) || isPadKey(valueText))
    {
      return strokeText;
    }
    if (isEnterKey(valueText))
    {
      return enterText;
    }
    if (StrokeInputService.SPACE_BAR_VALUE_TEXT.equals(valueText))
    {
      return spaceBarText;
    }
    return keyText;
  }

  public static boolean isStrokeKey(final String valueText)
  {
    return valueText.startsWith("STROKE_");
  }

  public static boolean isPadKey(final String valueText)
  {
    return StrokeInputService.HANDWRITING_PAD_VALUE_TEXT.equals(valueText)
      || StrokeInputService.VOICE_PAD_VALUE_TEXT.equals(valueText);
  }

  public static boolean isEnterKey(final String valueText)
  {
    return StrokeInputService.ENTER_KEY_VALUE_TEXT.equals(valueText);
  }

  public static boolean isFunctionKey(final String valueText)
  {
    return StrokeInputService.BACKSPACE_VALUE_TEXT.equals(valueText)
      || StrokeInputService.SHIFT_KEY_VALUE_TEXT.equals(valueText)
      || valueText.startsWith("SWITCH_TO_");
  }
}
