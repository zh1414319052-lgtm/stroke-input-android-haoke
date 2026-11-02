/* Copyright 2026 Haoke Tools. Licensed under GPL-3.0-only. */
package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class HandwritingHeightTest
{
  @Test
  public void targetIsBoundedAndDefaultEnlargesTheOldPad()
  {
    assertEquals(180, HandwritingHeight.clampDp(-1));
    assertEquals(260, HandwritingHeight.clampDp(HandwritingHeight.DEFAULT_DP));
    assertEquals(400, HandwritingHeight.clampDp(Integer.MAX_VALUE));
  }

  @Test
  public void landscapeAndSmallParentReserveChromeAndFunctionKeys()
  {
    assertEquals(600, HandwritingHeight.panelLimitPx(800, 800));
    assertEquals(240, HandwritingHeight.panelLimitPx(480, 240));
    // A 240 px IME budget with 80 px chrome and a 60 px function row leaves 100 px for ink.
    assertEquals(100, HandwritingHeight.fitPadPx(400, 240 - 80, 60));
    assertEquals(0, HandwritingHeight.fitPadPx(400, 20, 60));
    assertEquals(400, HandwritingHeight.fitPadPx(400, 600 - 80, 60));
  }
}
