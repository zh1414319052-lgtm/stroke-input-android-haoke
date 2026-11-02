/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.yawnoc.utilities.Stringy;

/*
  The fixed set of optional candidate ranges (disallowed unless the user switches them on).

  The input method service must know these ranges even when MainActivity has never been created
  in the current process (e.g. after a reboot or after the system kills the process),
  otherwise disallowed characters (often without font support) would wrongly appear as candidates.

  The ranges must match the OptionalCandidatesSwitch attributes in main_activity.xml,
  since the preference key is derived from the characters (checked by OptionalCandidatesTest).
*/
public final class OptionalCandidates
{
  private static final int[][] CODE_POINT_RANGES =
          {
            {0x9FD1, 0x9FD5}, // tail end of CJK Unified Ideographs (1/4)
            {0x9FD6, 0x9FE9}, // tail end of CJK Unified Ideographs (2/4)
            {0x9FEA, 0x9FEF}, // tail end of CJK Unified Ideographs (3/4)
            {0x9FF0, 0x9FFF}, // tail end of CJK Unified Ideographs (4/4)
            {0x4DB6, 0x4DBF}, // tail end of CJK Unified Ideographs Extension A
            {0x20024, 0x20024}, // Kangxi explanatory 並 (Extension B)
            {0x2B7F7, 0x2B7F7}, // simplified 鉝 "livermorium" (Extension B)
            {0x30EDD, 0x30EDE}, // biangbiang noodles (Extension G)
          };

  private static final List<String> CHARACTERS_LIST = makeCharactersList();

  private OptionalCandidates()
  {
  }

  public static String charactersForRange(final int minimumCodePoint, final int maximumCodePoint)
  {
    final List<String> charactersList = new ArrayList<>();
    for (int codePoint = minimumCodePoint; codePoint <= maximumCodePoint; codePoint++)
    {
      charactersList.add(Stringy.toString(codePoint));
    }

    return String.join("", charactersList);
  }

  private static List<String> makeCharactersList()
  {
    final List<String> charactersList = new ArrayList<>();
    for (final int[] codePointRange : CODE_POINT_RANGES)
    {
      charactersList.add(charactersForRange(codePointRange[0], codePointRange[1]));
    }

    return Collections.unmodifiableList(charactersList);
  }

  /*
    One string per switch, in the same order as the switches in the main activity.
  */
  public static List<String> charactersList()
  {
    return CHARACTERS_LIST;
  }
}
