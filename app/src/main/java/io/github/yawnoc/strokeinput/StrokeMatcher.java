/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

/*
  Stroke sequence matching beyond plain lookups:
    - wildcard stroke (any one stroke), like the 通配符 key of classic phone stroke input
    - adjacent transpositions (two neighbouring strokes typed in the wrong order)
    - reverse lookup of the stroke sequences of a character
  Sequences are strings of stroke digits 1 to 5; the wildcard is WILDCARD_DIGIT.
*/
public final class StrokeMatcher
{
  public static final char WILDCARD_DIGIT = '6';
  public static final int MIN_TRANSPOSITION_LENGTH = 3;

  private StrokeMatcher()
  {
  }

  public static boolean hasWildcard(final String strokeDigitSequence)
  {
    return strokeDigitSequence.indexOf(WILDCARD_DIGIT) >= 0;
  }

  /*
    Whether the start of a data sequence matches a typed pattern (which may contain wildcards).
  */
  public static boolean startsWithPattern(final String sequence, final String pattern)
  {
    if (sequence.length() < pattern.length())
    {
      return false;
    }

    for (int index = 0; index < pattern.length(); index++)
    {
      final char patternDigit = pattern.charAt(index);
      if (patternDigit != WILDCARD_DIGIT && patternDigit != sequence.charAt(index))
      {
        return false;
      }
    }

    return true;
  }

  /*
    The part of the data map which could possibly match the pattern,
    i.e. the sequences starting with the literal digits before the first wildcard.
  */
  private static NavigableMap<String, String> candidateRegion(
    final NavigableMap<String, String> charactersFromSequence,
    final String pattern
  )
  {
    final int wildcardIndex = pattern.indexOf(WILDCARD_DIGIT);
    final String literalPrefix = (wildcardIndex < 0) ? pattern : pattern.substring(0, wildcardIndex);

    if (literalPrefix.isEmpty())
    {
      return charactersFromSequence;
    }

    return charactersFromSequence.subMap(literalPrefix, true, literalPrefix + Character.MAX_VALUE, false);
  }

  /*
    Characters (as data value strings) of sequences with exactly the pattern's length that match it.
  */
  public static List<String> exactMatchValues(
    final NavigableMap<String, String> charactersFromSequence,
    final String pattern
  )
  {
    final List<String> values = new ArrayList<>();

    if (!hasWildcard(pattern))
    {
      final String characters = charactersFromSequence.get(pattern);
      if (characters != null)
      {
        values.add(characters);
      }
      return values;
    }

    for (final Map.Entry<String, String> entry : candidateRegion(charactersFromSequence, pattern).entrySet())
    {
      final String sequence = entry.getKey();
      if (sequence.length() == pattern.length() && startsWithPattern(sequence, pattern))
      {
        values.add(entry.getValue());
      }
    }

    return values;
  }

  /*
    Characters (as data value strings) of sequences strictly longer than the pattern that start with it.
  */
  public static Collection<String> prefixMatchValues(
    final NavigableMap<String, String> charactersFromSequence,
    final String pattern
  )
  {
    if (!hasWildcard(pattern))
    {
      return charactersFromSequence.subMap(pattern, false, pattern + Character.MAX_VALUE, false).values();
    }

    final List<String> values = new ArrayList<>();
    for (final Map.Entry<String, String> entry : candidateRegion(charactersFromSequence, pattern).entrySet())
    {
      final String sequence = entry.getKey();
      if (sequence.length() > pattern.length() && startsWithPattern(sequence, pattern))
      {
        values.add(entry.getValue());
      }
    }

    return values;
  }

  /*
    The distinct sequences obtained by swapping one pair of neighbouring strokes,
    for sequences long enough that this is a plausible slip (and without wildcards).
  */
  public static List<String> adjacentTranspositions(final String strokeDigitSequence)
  {
    final Set<String> transpositions = new LinkedHashSet<>();

    if (strokeDigitSequence.length() < MIN_TRANSPOSITION_LENGTH || hasWildcard(strokeDigitSequence))
    {
      return new ArrayList<>(transpositions);
    }

    final char[] digits = strokeDigitSequence.toCharArray();
    for (int index = 0; index + 1 < digits.length; index++)
    {
      if (digits[index] == digits[index + 1])
      {
        continue;
      }
      final char[] swapped = digits.clone();
      swapped[index] = digits[index + 1];
      swapped[index + 1] = digits[index];
      transpositions.add(new String(swapped));
    }

    return new ArrayList<>(transpositions);
  }

  /*
    All stroke sequences recorded for a character, in data order.
  */
  public static List<String> sequencesForCharacter(
    final NavigableMap<String, String> charactersFromSequence,
    final String character
  )
  {
    final List<String> sequences = new ArrayList<>();
    if (character.isEmpty())
    {
      return sequences;
    }

    for (final Map.Entry<String, String> entry : charactersFromSequence.entrySet())
    {
      if (entry.getValue().contains(character))
      {
        sequences.add(entry.getKey());
      }
    }

    return sequences;
  }
}
