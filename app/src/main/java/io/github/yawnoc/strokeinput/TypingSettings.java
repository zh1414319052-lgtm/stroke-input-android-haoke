/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import android.content.Context;

import java.util.Arrays;
import java.util.List;

import io.github.yawnoc.utilities.Contexty;

/*
  Haoke typing settings, stored in the same local preferences file as the upstream settings.
*/
public final class TypingSettings
{
  public static final String KEYBOARD_LAYOUT_PREFERENCE_KEY = "keyboardLayoutPreference";
  public static final String KEYBOARD_LAYOUT_STANDARD = "STANDARD";
  public static final String KEYBOARD_LAYOUT_LARGE = "LARGE";

  public static final String CANDIDATE_SIZE_PREFERENCE_KEY = "candidateSizePreference";
  public static final float[] CANDIDATE_SIZE_SCALES = {1f, 1.3f, 1.6f};

  public static final String KEY_VIBRATION_PREFERENCE_KEY = "keyVibrationPreference";
  public static final boolean KEY_VIBRATION_DEFAULT = true;

  public static final String KEY_SOUND_PREFERENCE_KEY = "keySoundPreference";
  public static final boolean KEY_SOUND_DEFAULT = false;

  public static final String STROKE_TOLERANCE_PREFERENCE_KEY = "strokeTolerancePreference";
  public static final boolean STROKE_TOLERANCE_DEFAULT = true;

  public static final String QUICK_PHRASES_PREFERENCE_KEY = "quickPhrasesPreference";

  public static final String THEME_PREFERENCE_KEY = "themePreference";

  public static final String HANDWRITING_ON_KEYBOARD_PREFERENCE_KEY = "handwritingOnKeyboardPreference";
  public static final boolean HANDWRITING_ON_KEYBOARD_DEFAULT = true;

  public static final String HANDWRITING_FULL_SCREEN_PREFERENCE_KEY = "handwritingFullScreenPreference";
  public static final boolean HANDWRITING_FULL_SCREEN_DEFAULT = false; // half screen: the pad on the keyboard
  public static final String HANDWRITING_HEIGHT_DP_KEY = "handwritingHeightDp";

  private TypingSettings()
  {
  }

  public static int loadHandwritingHeightDp(final Context context)
  {
    return HandwritingHeight.clampDp(Contexty.loadPreferenceInt(
      context, StrokeInputService.PREFERENCES_FILE_NAME, HANDWRITING_HEIGHT_DP_KEY, HandwritingHeight.DEFAULT_DP
    ));
  }

  public static void saveHandwritingHeightDp(final Context context, final int heightDp)
  {
    Contexty.savePreferenceInt(
      context, StrokeInputService.PREFERENCES_FILE_NAME, HANDWRITING_HEIGHT_DP_KEY, HandwritingHeight.clampDp(heightDp)
    );
  }

  public static String loadThemeName(final Context context)
  {
    return KeyboardTheme.forName(
      Contexty.loadPreferenceString(context, StrokeInputService.PREFERENCES_FILE_NAME, THEME_PREFERENCE_KEY)
    ).name;
  }

  public static void saveThemeName(final Context context, final String themeName)
  {
    Contexty.savePreferenceString(context, StrokeInputService.PREFERENCES_FILE_NAME, THEME_PREFERENCE_KEY, themeName);
  }

  public static KeyboardTheme loadKeyboardTheme(final Context context)
  {
    return KeyboardTheme.forName(loadThemeName(context));
  }

  public static boolean isLargeKeyboardLayout(final Context context)
  {
    final String layout =
            Contexty.loadPreferenceString(context, StrokeInputService.PREFERENCES_FILE_NAME, KEYBOARD_LAYOUT_PREFERENCE_KEY);
    return KEYBOARD_LAYOUT_LARGE.equals(layout);
  }

  public static void saveKeyboardLayout(final Context context, final String layout)
  {
    Contexty.savePreferenceString(context, StrokeInputService.PREFERENCES_FILE_NAME, KEYBOARD_LAYOUT_PREFERENCE_KEY, layout);
  }

  public static int loadCandidateSizeIndex(final Context context)
  {
    final int index =
            Contexty.loadPreferenceInt(context, StrokeInputService.PREFERENCES_FILE_NAME, CANDIDATE_SIZE_PREFERENCE_KEY, 0);
    return (index >= 0 && index < CANDIDATE_SIZE_SCALES.length) ? index : 0;
  }

  public static void saveCandidateSizeIndex(final Context context, final int index)
  {
    Contexty.savePreferenceInt(context, StrokeInputService.PREFERENCES_FILE_NAME, CANDIDATE_SIZE_PREFERENCE_KEY, index);
  }

  public static float loadCandidateSizeScale(final Context context)
  {
    return CANDIDATE_SIZE_SCALES[loadCandidateSizeIndex(context)];
  }

  public static boolean loadBoolean(final Context context, final String preferenceKey, final boolean defaultValue)
  {
    return Contexty.loadPreferenceBoolean(context, StrokeInputService.PREFERENCES_FILE_NAME, preferenceKey, defaultValue);
  }

  public static void saveBoolean(final Context context, final String preferenceKey, final boolean value)
  {
    Contexty.savePreferenceBoolean(context, StrokeInputService.PREFERENCES_FILE_NAME, preferenceKey, value);
  }

  /*
    The saved quick phrases, or the default ones (in the UI language of the given context) if never saved.
  */
  public static List<String> loadQuickPhrases(final Context context)
  {
    final String savedText =
            Contexty.loadPreferenceString(context, StrokeInputService.PREFERENCES_FILE_NAME, QUICK_PHRASES_PREFERENCE_KEY);
    if (savedText == null)
    {
      return Arrays.asList(context.getResources().getStringArray(R.array.default_quick_phrases));
    }
    return QuickPhrases.parse(savedText);
  }

  public static List<String> saveQuickPhrases(final Context context, final String text)
  {
    final List<String> phrases = QuickPhrases.parse(text);
    Contexty.savePreferenceString(
      context,
      StrokeInputService.PREFERENCES_FILE_NAME,
      QUICK_PHRASES_PREFERENCE_KEY,
      QuickPhrases.serialise(phrases)
    );
    return phrases;
  }
}
