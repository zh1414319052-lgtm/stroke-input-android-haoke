/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HandwritingGesturePolicyTest
{
  @Test
  public void movementMustPassSystemSlop_beforeAKeyTouchBecomesInk()
  {
    assertFalse(HandwritingGesturePolicy.hasMovedBeyondSlop(10, 20, 16, 28, 10));
    assertFalse(HandwritingGesturePolicy.hasMovedBeyondSlop(10, 20, 20, 20, 10));
    assertTrue(HandwritingGesturePolicy.hasMovedBeyondSlop(10, 20, 21, 20, 10));
    assertTrue(HandwritingGesturePolicy.hasMovedBeyondSlop(10, 20, 18, 28, 10));
  }

  @Test
  public void directInkPreservesKeysWithTheirOwnHoldOrSwipeGestures()
  {
    assertTrue(HandwritingGesturePolicy.canStartDirectInk(true, false, false, false, false, false));
    assertFalse(HandwritingGesturePolicy.canStartDirectInk(false, false, false, false, false, false));
    assertFalse(HandwritingGesturePolicy.canStartDirectInk(true, true, false, false, false, false));
    assertFalse(HandwritingGesturePolicy.canStartDirectInk(true, false, true, false, false, false));
    assertFalse(HandwritingGesturePolicy.canStartDirectInk(true, false, false, true, false, false));
    assertFalse(HandwritingGesturePolicy.canStartDirectInk(true, false, false, false, true, false));
    assertFalse(HandwritingGesturePolicy.canStartDirectInk(true, false, false, false, false, true));
  }
}
