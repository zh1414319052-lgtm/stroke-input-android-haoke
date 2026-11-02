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

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public class StrokeMatcherTest
{
  private static final String SEQUENCE_CHARACTERS_FILE = "src/main/assets/sequence-characters.txt";

  private static final NavigableMap<String, String> charactersFromSequence = new TreeMap<>();

  @BeforeClass
  public static void loadRealStrokeData() throws Exception
  {
    try (
      final BufferedReader reader =
              new BufferedReader(
                new InputStreamReader(new FileInputStream(SEQUENCE_CHARACTERS_FILE), StandardCharsets.UTF_8)
              )
    )
    {
      String line;
      while ((line = reader.readLine()) != null)
      {
        if (line.startsWith("#") || line.isEmpty())
        {
          continue;
        }
        final String[] fields = line.split("\t");
        charactersFromSequence.put(fields[0], fields[1]);
      }
    }
  }

  private static boolean anyContains(final Collection<String> values, final String character)
  {
    for (final String value : values)
    {
      if (value.contains(character))
      {
        return true;
      }
    }
    return false;
  }

  @Test
  public void startsWithPattern_treatsWildcardAsAnyStroke()
  {
    assertTrue(StrokeMatcher.startsWithPattern("1134", "1134"));
    assertTrue(StrokeMatcher.startsWithPattern("1134", "1634"));
    assertTrue(StrokeMatcher.startsWithPattern("1134", "66"));
    assertTrue(StrokeMatcher.startsWithPattern("1134", "6666"));
    assertFalse(StrokeMatcher.startsWithPattern("1134", "1234"));
    assertFalse(StrokeMatcher.startsWithPattern("113", "1134"));
  }

  @Test
  public void exactMatchValues_withWildcardFindsCharacter()
  {
    // 天 is ㇐㇐㇒㇔ (1134); any single stroke may be replaced by the wildcard
    for (final String pattern : Arrays.asList("1134", "6134", "1634", "1164", "1136", "6666"))
    {
      assertTrue(pattern, anyContains(StrokeMatcher.exactMatchValues(charactersFromSequence, pattern), "天"));
    }
    // wrong length or wrong literal stroke does not match exactly
    assertFalse(anyContains(StrokeMatcher.exactMatchValues(charactersFromSequence, "113"), "天"));
    assertFalse(anyContains(StrokeMatcher.exactMatchValues(charactersFromSequence, "2634"), "天"));
  }

  @Test
  public void exactMatchValues_withoutWildcardIsThePlainLookup()
  {
    assertEquals(
      Collections.singletonList(charactersFromSequence.get("1134")),
      StrokeMatcher.exactMatchValues(charactersFromSequence, "1134")
    );
    assertTrue(StrokeMatcher.exactMatchValues(charactersFromSequence, "5555555555555555").isEmpty());
  }

  @Test
  public void prefixMatchValues_withWildcardFindsLongerCharacters()
  {
    // 為 is ㇔㇒㇖㇖㇖㇔㇔㇔㇔ (435554444), so 4?5 is a prefix of it
    assertTrue(anyContains(StrokeMatcher.prefixMatchValues(charactersFromSequence, "465"), "為"));
    assertFalse(anyContains(StrokeMatcher.prefixMatchValues(charactersFromSequence, "465"), "天"));
    // without wildcards it agrees with the plain sub-map lookup
    assertEquals(
      charactersFromSequence.subMap("435", false, "435" + Character.MAX_VALUE, false).values().size(),
      StrokeMatcher.prefixMatchValues(charactersFromSequence, "435").size()
    );
  }

  @Test
  public void adjacentTranspositions_isCorrect()
  {
    // 3434: swap 1-2 -> 4334, swap 2-3 -> 3344, swap 3-4 -> 3443
    assertEquals(Arrays.asList("4334", "3344", "3443"), StrokeMatcher.adjacentTranspositions("3434"));
    // 3433: swap 1-2 -> 4333, swap 2-3 -> 3343, the equal last pair gives nothing new
    assertEquals(Arrays.asList("4333", "3343"), StrokeMatcher.adjacentTranspositions("3433"));
    assertEquals(Collections.emptyList(), StrokeMatcher.adjacentTranspositions("12")); // too short
    assertEquals(Collections.emptyList(), StrokeMatcher.adjacentTranspositions("111")); // nothing to swap
    assertEquals(Collections.emptyList(), StrokeMatcher.adjacentTranspositions("1634")); // has wildcard
  }

  @Test
  public void adjacentTranspositions_recoverCommonSlips()
  {
    // 火 is ㇔㇒㇒㇔ (4334); typing the first two strokes the other way round (3434) still finds it
    assertTrue(StrokeMatcher.adjacentTranspositions("3434").contains("4334"));
    assertTrue(charactersFromSequence.get("4334").contains("火"));
    // 长 is ㇒㇐㇖㇔ (3154); 1354 is one swap away
    assertTrue(StrokeMatcher.adjacentTranspositions("1354").contains("3154"));
    assertTrue(charactersFromSequence.get("3154").contains("长"));
  }

  @Test
  public void sequencesForCharacter_listsAllRecordedOrders()
  {
    final List<String> sequencesOfBi = StrokeMatcher.sequencesForCharacter(charactersFromSequence, "必");
    assertEquals(Arrays.asList("45434", "45443"), sequencesOfBi);
    assertEquals(
      Collections.singletonList("1134"),
      StrokeMatcher.sequencesForCharacter(charactersFromSequence, "天")
    );
    assertTrue(StrokeMatcher.sequencesForCharacter(charactersFromSequence, "A").isEmpty());
    assertTrue(StrokeMatcher.sequencesForCharacter(charactersFromSequence, "").isEmpty());
  }
}
