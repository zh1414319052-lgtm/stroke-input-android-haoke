/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

public class AppLanguageTest
{
  @Test
  public void selectLanguage_anyChineseVariantGivesChinese()
  {
    final String[] chineseTags =
            {"zh", "zh-CN", "zh-SG", "zh-Hans", "zh-Hans-CN", "zh-Hant", "zh-TW", "zh-HK", "zh-MO", "zh-Hant-HK",
              "ZH_tw", " zh-cn "};
    for (final String tag : chineseTags)
    {
      assertEquals(tag, AppLanguage.CHINESE, AppLanguage.selectLanguage(tag));
    }
  }

  @Test
  public void selectLanguage_everythingElseGivesEnglish()
  {
    final String[] otherTags =
            {"en", "en-US", "en-GB", "EN_au", "ja-JP", "ko-KR", "fr-FR", "ar-SA", "tr-TR", "yue-HK", "zho", "", "und"};
    for (final String tag : otherTags)
    {
      assertEquals(tag, AppLanguage.ENGLISH, AppLanguage.selectLanguage(tag));
    }
    assertEquals(AppLanguage.ENGLISH, AppLanguage.selectLanguage(null));
  }

  @Test
  public void localeForLanguage_isSimplifiedChineseOrEnglish()
  {
    assertEquals(Locale.SIMPLIFIED_CHINESE, AppLanguage.localeForLanguage(AppLanguage.CHINESE));
    assertEquals(Locale.ENGLISH, AppLanguage.localeForLanguage(AppLanguage.ENGLISH));
  }
}
