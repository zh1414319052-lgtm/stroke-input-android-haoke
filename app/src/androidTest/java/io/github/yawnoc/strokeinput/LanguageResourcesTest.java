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
import android.content.res.Configuration;
import android.os.LocaleList;
import android.util.Log;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.util.Map;

/*
  Resource resolution with the real Android resource system, using isolated configuration contexts
  (the device language is never changed by these tests).
*/
@RunWith(AndroidJUnit4.class)
public class LanguageResourcesTest
{
  private static final String[] CHINESE_FIRST_LISTS =
          {"zh-CN,en-US", "zh-TW,en-US", "zh-Hant-HK,en-US", "zh-HK", "zh-MO", "zh-SG", "zh-Hans-CN", "zh-Hant"};
  private static final String[] OTHER_FIRST_LISTS =
          {"en-US,zh-CN", "en-GB,zh-TW", "ja-JP,zh-TW", "fr-FR,zh-CN", "ar-SA,zh-Hant", "tr-TR,zh-CN", "ko-KR"};

  private static Context targetContext()
  {
    return InstrumentationRegistry.getInstrumentation().getTargetContext();
  }

  private static Configuration systemConfiguration(final String languageTags)
  {
    final Configuration configuration = new Configuration(targetContext().getResources().getConfiguration());
    configuration.setLocales(LocaleList.forLanguageTags(languageTags));
    return configuration;
  }

  private static Context localised(final String languageTags)
  {
    return AppLanguage.wrap(targetContext(), LocaleList.forLanguageTags(languageTags));
  }

  @Test
  public void chineseFirst_usesSimplifiedChineseEverywhere() throws Exception
  {
    for (final String languageTags : CHINESE_FIRST_LISTS)
    {
      final Context context = localised(languageTags);
      assertEquals(languageTags, "笔画输入法", context.getString(R.string.app_name));
      assertEquals(languageTags, "欢迎使用笔画输入法！", context.getString(R.string.label__main_activity__welcome));
      assertEquals(languageTags, "简体字优先", context.getString(R.string.label__main_activity__simplified_first));
      assertEquals(languageTags, "浩科工具站", context.getString(R.string.label__main_activity__haoke_tools_button));
      assertEquals(languageTags, "◀ 中文 ▶", context.getString(R.string.display_text__strokes_space_bar));
      assertEquals(languageTags, "通配", context.getString(R.string.display_text__stroke_wildcard));
      assertEquals(languageTags, "常用语", context.getString(R.string.label__toolbar__quick_phrases));
      assertEquals(languageTags, "键盘区域手写", context.getString(R.string.label__settings__handwriting_on_keyboard));
      assertEquals(languageTags, "收起键盘", context.getString(R.string.label__toolbar__hide_keyboard));
      assertEquals(languageTags, "好的", context.getResources().getStringArray(R.array.default_quick_phrases)[0]);
      assertEquals(
        languageTags,
        "已保存 3 条常用语。",
        context.getResources().getQuantityString(R.plurals.message__main_activity__quick_phrases_saved, 3, 3)
      );
      assertEquals(
        languageTags,
        "无法打开浏览器访问<https://haoke.org>。",
        context.getString(R.string.message__error__browser, "https://haoke.org")
      );
      assertEquals(languageTags, "help-zh-Hans-CN.html", context.getString(R.string.file_name__help_html));
      assertEquals(languageTags, "about-zh-Hans-CN.html", context.getString(R.string.file_name__about_html));
      assertTrue(languageTags, context.getString(R.string.uri__privacy_policy).contains("PRIVACY-zh.md"));
      assertAssetExists(context, context.getString(R.string.file_name__help_html));
      assertAssetExists(context, context.getString(R.string.file_name__about_html));
    }
  }

  @Test
  public void otherFirst_usesEnglishAndIgnoresLaterChinese() throws Exception
  {
    for (final String languageTags : OTHER_FIRST_LISTS)
    {
      final Context context = localised(languageTags);
      assertEquals(languageTags, "Stroke Input Method", context.getString(R.string.app_name));
      assertEquals(languageTags, "Simplified first", context.getString(R.string.label__main_activity__simplified_first));
      assertEquals(languageTags, "Haoke Tools", context.getString(R.string.label__main_activity__haoke_tools_button));
      assertEquals(languageTags, "◀ Chinese ▶", context.getString(R.string.display_text__strokes_space_bar));
      assertEquals(languageTags, "＊", context.getString(R.string.display_text__stroke_wildcard));
      assertEquals(languageTags, "Phrases", context.getString(R.string.label__toolbar__quick_phrases));
      assertEquals(languageTags, "Handwrite on the keyboard", context.getString(R.string.label__settings__handwriting_on_keyboard));
      assertEquals(languageTags, "OK", context.getResources().getStringArray(R.array.default_quick_phrases)[0]);
      assertEquals(
        languageTags,
        "Saved 1 quick phrase.",
        context.getResources().getQuantityString(R.plurals.message__main_activity__quick_phrases_saved, 1, 1)
      );
      assertEquals(languageTags, "help.html", context.getString(R.string.file_name__help_html));
      assertTrue(languageTags, context.getString(R.string.uri__privacy_policy).contains("PRIVACY.md"));
      assertEquals(
        languageTags,
        View.LAYOUT_DIRECTION_LTR,
        context.getResources().getConfiguration().getLayoutDirection()
      );
      assertFalse(languageTags, context.getResources().getConfiguration().getLocales().toLanguageTags().contains("zh"));
    }
  }

  @Test
  public void keyboardSpaceBar_followsSelectedLanguage()
  {
    final Keyboard chineseKeyboard = new Keyboard(localised("zh-TW,en-US"), R.xml.keyboard_strokes);
    final Keyboard englishKeyboard = new Keyboard(localised("ja-JP,zh-CN"), R.xml.keyboard_strokes);
    assertTrue(hasKeyDisplayText(chineseKeyboard, "◀ 中文 ▶"));
    assertTrue(hasKeyDisplayText(englishKeyboard, "◀ Chinese ▶"));
  }

  @Test
  public void voiceStatesAndPermissionMessages_followTheFirstLanguage()
  {
    final int[] voiceStringIds =
      {
        R.string.label__toolbar__voice,
        R.string.status__voice_loading,
        R.string.status__voice_ready,
        R.string.status__voice_listening,
        R.string.status__voice_listening_hold,
        R.string.status__voice_recognising,
        R.string.status__voice_no_permission,
        R.string.status__voice_unavailable,
        R.string.status__voice_microphone_busy,
        R.string.status__voice_nothing_heard,
        R.string.message__voice_permission_granted,
        R.string.message__voice_permission_denied,
      };
    for (final String tags : CHINESE_FIRST_LISTS)
    {
      final Context context = localised(tags);
      assertEquals(tags, "语音", context.getString(R.string.label__toolbar__voice));
      assertEquals(tags, "点一下开始说话，或按住说话", context.getString(R.string.status__voice_ready));
      assertEquals(tags, "点这里允许使用麦克风", context.getString(R.string.status__voice_no_permission));
      assertEquals(tags, "语音输入需要麦克风权限", context.getString(R.string.message__voice_permission_denied));
      for (final int stringId : voiceStringIds)
      {
        final String text = context.getString(stringId);
        assertTrue(tags + ": untranslated voice string " + text, text.matches("(?s).*[\\u4e00-\\u9fff].*"));
      }
    }
    for (final String tags : OTHER_FIRST_LISTS)
    {
      final Context context = localised(tags);
      assertEquals(tags, "Voice", context.getString(R.string.label__toolbar__voice));
      assertEquals(tags, "Tap to talk, or hold while speaking", context.getString(R.string.status__voice_ready));
      assertEquals(tags, "Tap here to allow the microphone", context.getString(R.string.status__voice_no_permission));
      assertEquals(tags, "Voice input needs the microphone.", context.getString(R.string.message__voice_permission_denied));
      for (final int stringId : voiceStringIds)
      {
        final String text = context.getString(stringId);
        assertTrue(tags + ": empty voice string", !text.trim().isEmpty());
        assertFalse(tags + ": later Chinese locale leaked into " + text, text.matches("(?s).*[\\u4e00-\\u9fff].*"));
      }
    }
  }

  @Test
  public void voiceKeyboard_hasPadAndFunctionRowThatFitInBothLanguages()
  {
    for (final String tags : new String[]{"zh-TW,en-US", "en-US,zh-CN", "ja-JP,zh-CN"})
    {
      final Keyboard keyboard = new Keyboard(localised(tags), R.xml.keyboard_voice);
      assertRowsFitWidth(keyboard);
      final Key pad = findKey(keyboard, "VOICE_PAD");
      assertTrue(tags, pad.width >= keyboard.getWidth() - 2);
      assertTrue(tags, pad.height > findKey(keyboard, "SPACE").height);
      assertTrue(tags, findKey(keyboard, "BACKSPACE").width > 0);
      assertTrue(tags, findKey(keyboard, "ENTER").width > 0);
      assertTrue(tags, findKey(keyboard, "SWITCH_TO_STROKES").width > 0);
      final String expectedSpace = tags.startsWith("zh") ? "◀ 中文 ▶" : "◀ Chinese ▶";
      assertTrue(tags, hasKeyDisplayText(keyboard, expectedSpace));
    }
  }

  @Test
  public void strokeKeyboards_haveWildcardAndLargeLayoutHasPhoneHints()
  {
    final Keyboard standardKeyboard = new Keyboard(localised("zh-CN"), R.xml.keyboard_strokes);
    final Keyboard largeKeyboard = new Keyboard(localised("zh-CN"), R.xml.keyboard_strokes_large);

    assertTrue(hasKeyDisplayText(standardKeyboard, "通配"));
    assertTrue(hasKeyDisplayText(largeKeyboard, "通配"));

    for (int digit = 1; digit <= 6; digit++)
    {
      final Key strokeKey = findKey(largeKeyboard, "STROKE_" + digit);
      assertEquals(String.valueOf(digit), strokeKey.hintText);
    }
    // large keys really are larger, and every row spans the keyboard without overlap
    assertTrue(findKey(largeKeyboard, "STROKE_1").width > findKey(standardKeyboard, "STROKE_1").width);
    assertTrue(findKey(largeKeyboard, "STROKE_1").height > findKey(standardKeyboard, "STROKE_1").height);
    assertRowsFitWidth(largeKeyboard);
    assertRowsFitWidth(standardKeyboard);
  }

  private static Key findKey(final Keyboard keyboard, final String valueText)
  {
    for (final Key key : keyboard.getKeys())
    {
      if (key.valueText.equals(valueText))
      {
        return key;
      }
    }
    throw new AssertionError("No key " + valueText);
  }

  private static void assertRowsFitWidth(final Keyboard keyboard)
  {
    for (final Key key : keyboard.getKeys())
    {
      assertTrue(key.valueText, key.x >= 0);
      assertTrue(key.valueText + " ends at " + (key.x + key.width), key.x + key.width <= keyboard.getWidth() + 2);
    }
  }

  @Test
  public void localeOverride_keepsOtherConfiguration()
  {
    final Configuration base = targetContext().getResources().getConfiguration();
    final Configuration localisedConfiguration = localised("zh-TW,en-US").getResources().getConfiguration();
    assertEquals(base.orientation, localisedConfiguration.orientation);
    assertEquals(base.densityDpi, localisedConfiguration.densityDpi);
    assertEquals(base.screenWidthDp, localisedConfiguration.screenWidthDp);
    assertEquals(base.fontScale, localisedConfiguration.fontScale, 0f);
    assertEquals(base.uiMode, localisedConfiguration.uiMode);
  }

  /*
    Launchers and the input method picker resolve the name before the app runs, with the full system list.
    Only the combinations Android can honour without per-language resources are asserted;
    the result for a non-Chinese, non-English first language followed by Chinese is logged for the report.
  */
  @Test
  public void hostResolvedName_forChineseAndEnglishFirst()
  {
    for (final String languageTags : new String[]{"zh-TW,en-US", "zh-HK", "zh-CN,en-US", "zh-SG"})
    {
      final Context hostView = targetContext().createConfigurationContext(systemConfiguration(languageTags));
      assertEquals(languageTags, "笔画输入法", hostView.getString(R.string.app_name));
    }
    for (final String languageTags : new String[]{"en-US,zh-CN", "en-GB,zh-TW", "en-US"})
    {
      final Context hostView = targetContext().createConfigurationContext(systemConfiguration(languageTags));
      assertEquals(languageTags, "Stroke Input Method", hostView.getString(R.string.app_name));
    }
    for (final String languageTags : new String[]{"ja-JP,zh-TW", "fr-FR,zh-CN", "ja-JP"})
    {
      final Context hostView = targetContext().createConfigurationContext(systemConfiguration(languageTags));
      Log.i("LanguageResourcesTest", "host name for [" + languageTags + "] = " + hostView.getString(R.string.app_name));
    }
  }

  @Test
  public void systemLocales_isTheRawSystemListNotAResourceMatch()
  {
    // Resources.getSystem() moves the first locale supported by framework resources to the front,
    // so it must not be used; the raw list has the user's actual first language.
    final LocaleList systemLocales = AppLanguage.systemLocales(targetContext());
    Log.i("LanguageResourcesTest", "raw system locales = " + systemLocales.toLanguageTags());
    Log.i(
      "LanguageResourcesTest",
      "framework-resolved locales = " + android.content.res.Resources.getSystem().getConfiguration().getLocales()
    );
    assertFalse(systemLocales.isEmpty());
    assertEquals(
      AppLanguage.selectLanguage(systemLocales.get(0).toLanguageTag()),
      AppLanguage.systemLanguage(targetContext())
    );
    final String expectedLanguage =
            (systemLocales.get(0).getLanguage().equals("zh")) ? "笔画输入法" : "Stroke Input Method";
    assertEquals(expectedLanguage, AppLanguage.wrap(targetContext()).getString(R.string.app_name));
  }

  @Test
  public void optionalCandidatePreferences_availableWithoutMainActivity()
  {
    // In this test process MainActivity has never been created (formerly the map was then empty,
    // so the input method service let disallowed optional characters through after a restart).
    final Map<String, Boolean> isAllowedFromCharacters =
            MainActivity.loadSavedOptionalCandidatesPreferences(targetContext());
    assertEquals(OptionalCandidates.charactersList().size(), isAllowedFromCharacters.size());
    assertTrue(isAllowedFromCharacters.keySet().containsAll(OptionalCandidates.charactersList()));
  }

  private static boolean hasKeyDisplayText(final Keyboard keyboard, final String displayText)
  {
    for (final Key key : keyboard.getKeys())
    {
      if (displayText.equals(key.displayText))
      {
        return true;
      }
    }
    return false;
  }

  private static void assertAssetExists(final Context context, final String fileName) throws Exception
  {
    try (final InputStream inputStream = context.getAssets().open(fileName))
    {
      assertTrue(fileName, inputStream.read() >= 0);
    }
  }
}
