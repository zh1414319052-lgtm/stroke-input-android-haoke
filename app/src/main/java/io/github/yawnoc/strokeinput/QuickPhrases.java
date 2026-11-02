/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/*
  User-defined quick phrases (常用语), stored locally as one phrase per line.
*/
public final class QuickPhrases
{
  public static final int MAX_PHRASE_COUNT = 50;
  public static final int MAX_PHRASE_LENGTH = 200;

  private QuickPhrases()
  {
  }

  /*
    Split text into phrases: one per line, trimmed, blank lines and duplicates dropped,
    over-long phrases cut, and at most MAX_PHRASE_COUNT kept.
  */
  public static List<String> parse(final String text)
  {
    final Set<String> phrases = new LinkedHashSet<>();
    if (text == null)
    {
      return new ArrayList<>(phrases);
    }

    for (final String line : text.split("\\r?\\n|\\r"))
    {
      String phrase = line.trim();
      if (phrase.isEmpty())
      {
        continue;
      }
      if (phrase.codePointCount(0, phrase.length()) > MAX_PHRASE_LENGTH)
      {
        phrase = phrase.substring(0, phrase.offsetByCodePoints(0, MAX_PHRASE_LENGTH));
      }
      phrases.add(phrase);
      if (phrases.size() >= MAX_PHRASE_COUNT)
      {
        break;
      }
    }

    return new ArrayList<>(phrases);
  }

  public static String serialise(final List<String> phrases)
  {
    return String.join("\n", phrases);
  }
}
