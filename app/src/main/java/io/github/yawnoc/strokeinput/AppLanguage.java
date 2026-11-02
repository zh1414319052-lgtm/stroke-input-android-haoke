/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;

import java.util.Locale;

/*
  Chooses the user interface language from the FIRST system language only:
    - any Chinese variant (zh, zh-CN, zh-TW, zh-Hant-HK, ...) --> Simplified Chinese
    - anything else (including English and unknown) --> English
  Later entries in the system language list never override the first,
  so e.g. [en-US, zh-CN] and [ja-JP, zh-TW] both give English.

  The raw system preference list is used. Resource configurations (including Resources.getSystem())
  are NOT used, because Android moves the first locale that the resources support to the front,
  e.g. [tr-TR, zh-CN] becomes [zh-CN, tr-TR] when the resources lack Turkish.

  Only the locale is overridden; all other configuration (orientation, size, density, night mode)
  keeps following the system, and neither the device locale nor Locale.getDefault() is changed.
*/
public final class AppLanguage
{
  public static final String CHINESE = "zh";
  public static final String ENGLISH = "en";

  private AppLanguage()
  {
  }

  public static String selectLanguage(final String firstLanguageTag)
  {
    if (firstLanguageTag == null)
    {
      return ENGLISH;
    }

    final String normalisedTag = firstLanguageTag.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    final int separatorIndex = normalisedTag.indexOf('-');
    final String languageSubtag = (separatorIndex < 0) ? normalisedTag : normalisedTag.substring(0, separatorIndex);

    return (languageSubtag.equals(CHINESE)) ? CHINESE : ENGLISH;
  }

  public static Locale localeForLanguage(final String language)
  {
    return (CHINESE.equals(language)) ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
  }

  public static String languageFor(final LocaleList systemLocales)
  {
    final String firstLanguageTag =
            (systemLocales == null || systemLocales.isEmpty()) ? null : systemLocales.get(0).toLanguageTag();
    return selectLanguage(firstLanguageTag);
  }

  /*
    The user's system language preference list, unaffected by resource matching.
  */
  public static LocaleList systemLocales(final Context context)
  {
    if (Build.VERSION.SDK_INT >= 33)
    {
      try
      {
        final LocaleManager localeManager = context.getSystemService(LocaleManager.class);
        if (localeManager != null)
        {
          return localeManager.getSystemLocales();
        }
      }
      catch (RuntimeException exception)
      {
        // fall through to the process default list, which Android sets from the same system list
      }
    }

    return LocaleList.getDefault();
  }

  public static String systemLanguage(final Context context)
  {
    return languageFor(systemLocales(context));
  }

  public static Context wrap(final Context context)
  {
    return wrap(context, systemLocales(context));
  }

  /*
    The explicit system list parameter also allows isolated tests of arbitrary language lists.
  */
  public static Context wrap(final Context context, final LocaleList systemLocales)
  {
    final Locale chosenLocale = localeForLanguage(languageFor(systemLocales));

    final Configuration localeOverride = new Configuration(); // all other fields undefined, hence not overridden
    localeOverride.setLocales(new LocaleList(chosenLocale));
    localeOverride.setLayoutDirection(chosenLocale);

    return context.createConfigurationContext(localeOverride);
  }

  /*
    Buttons default to the system text locale for their all-caps transformation,
    which would give Turkish "SETTİNGS" for the English UI on a Turkish system.
    Only plain buttons are changed (switches show CJK candidates whose glyph choice is left alone);
    the text is set again because the transformation is computed when the text is set.
  */
  public static void applyTextLocale(final View view, final Locale locale)
  {
    if (view instanceof Button && !(view instanceof CompoundButton))
    {
      final Button button = (Button) view;
      button.setTextLocale(locale);
      button.setText(button.getText());
    }
    if (view instanceof ViewGroup)
    {
      final ViewGroup viewGroup = (ViewGroup) view;
      for (int childIndex = 0; childIndex < viewGroup.getChildCount(); childIndex++)
      {
        applyTextLocale(viewGroup.getChildAt(childIndex), locale);
      }
    }
  }
}
