/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import io.github.yawnoc.utilities.Stringy;

public class OptionalCandidatesTest
{
  private static final String MAIN_ACTIVITY_LAYOUT = "src/main/res/layout/main_activity.xml";
  private static final String APP_NAMESPACE = "http://schemas.android.com/apk/res-auto";

  /*
    The preference key of each switch is derived from its characters,
    so the fixed table must give exactly the same strings, in the same order, as the layout.
  */
  @Test
  public void charactersList_matchesSwitchesInLayout() throws Exception
  {
    final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(true);
    final Document layout = documentBuilderFactory.newDocumentBuilder().parse(new File(MAIN_ACTIVITY_LAYOUT));
    final NodeList switches = layout.getElementsByTagName("io.github.yawnoc.strokeinput.OptionalCandidatesSwitch");

    final List<String> layoutCharactersList = new ArrayList<>();
    for (int index = 0; index < switches.getLength(); index++)
    {
      final Element optionalCandidatesSwitch = (Element) switches.item(index);
      final String minimumCandidate = optionalCandidatesSwitch.getAttributeNS(APP_NAMESPACE, "minimumCandidate");
      final String maximumCandidate = optionalCandidatesSwitch.getAttributeNS(APP_NAMESPACE, "maximumCandidate");
      layoutCharactersList.add(
        OptionalCandidates.charactersForRange(
          Stringy.getFirstCodePoint(minimumCandidate),
          Stringy.getFirstCodePoint(maximumCandidate)
        )
      );
    }

    assertEquals(8, layoutCharactersList.size());
    assertEquals(layoutCharactersList, OptionalCandidates.charactersList());
  }

  @Test
  public void charactersForRange_isCorrect()
  {
    assertEquals("鿑鿒鿓鿔鿕", OptionalCandidates.charactersForRange(0x9FD1, 0x9FD5));
    assertEquals("𠀤", OptionalCandidates.charactersForRange(0x20024, 0x20024));
    assertEquals("𰻝𰻞", OptionalCandidates.charactersForRange(0x30EDD, 0x30EDE));
  }

  @Test
  public void charactersList_isAvailableWithoutActivityAndUnmodifiable()
  {
    final List<String> charactersList = OptionalCandidates.charactersList();
    assertEquals(8, charactersList.size());
    assertThrows(UnsupportedOperationException.class, () -> charactersList.add("x"));
  }
}
