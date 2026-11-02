/* Copyright 2026 Haoke Tools. Licensed under GPL-3.0-only; see LICENSE. */
package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ImeNavigationSpaceTest
{
  @Test public void edgeToEdgeReservesWholeNavigationFrame()
  {
    assertEquals(132, ImeNavigationSpace.overlap(132, 2376, 2376));
  }
  @Test public void ancestorInsetsAreNotCountedTwice()
  {
    assertEquals(0, ImeNavigationSpace.overlap(132, 2376, 2244));
    assertEquals(0, ImeNavigationSpace.overlap(132, 2376, 2000));
    assertEquals(84, ImeNavigationSpace.overlap(132, 2376, 2328));
  }
  @Test public void hiddenNavigationAndChangingBoundsRemainBounded()
  {
    assertEquals(0, ImeNavigationSpace.overlap(0, 2376, 2376));
    assertEquals(132, ImeNavigationSpace.overlap(132, 2376, 2450));
    assertEquals(48, ImeNavigationSpace.overlap(48, 1080, 1080));
  }
}
