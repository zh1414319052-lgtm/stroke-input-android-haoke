/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class QuickPhrasesTest
{
  @Test
  public void parse_trimsAndDropsBlankAndDuplicateLines()
  {
    assertEquals(
      Arrays.asList("好的", "我在开会，稍后回复", "OK"),
      QuickPhrases.parse("  好的 \n\n我在开会，稍后回复\r\n好的\r OK \n   ")
    );
  }

  @Test
  public void parse_handlesNullAndEmpty()
  {
    assertEquals(Collections.emptyList(), QuickPhrases.parse(null));
    assertEquals(Collections.emptyList(), QuickPhrases.parse(""));
    assertEquals(Collections.emptyList(), QuickPhrases.parse("\n \n"));
  }

  @Test
  public void parse_limitsCountAndLength()
  {
    final StringBuilder manyLines = new StringBuilder();
    for (int index = 0; index < QuickPhrases.MAX_PHRASE_COUNT + 20; index++)
    {
      manyLines.append("phrase ").append(index).append('\n');
    }
    assertEquals(QuickPhrases.MAX_PHRASE_COUNT, QuickPhrases.parse(manyLines.toString()).size());

    final String longPhrase = String.join("", Collections.nCopies(QuickPhrases.MAX_PHRASE_LENGTH + 30, "𠀤"));
    final String kept = QuickPhrases.parse(longPhrase).get(0);
    assertEquals(QuickPhrases.MAX_PHRASE_LENGTH, kept.codePointCount(0, kept.length())); // not split mid-character
  }

  @Test
  public void serialiseThenParse_roundTrips()
  {
    final List<String> phrases = Arrays.asList("广州市天河区某某路 1 号", "13800000000", "谢谢！");
    assertEquals(phrases, QuickPhrases.parse(QuickPhrases.serialise(phrases)));
    assertTrue(QuickPhrases.serialise(Collections.emptyList()).isEmpty());
  }
}
