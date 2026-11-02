/*
  Copyright 2026 Haoke Tools (浩科工具站)
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
*/

package io.github.yawnoc.strokeinput;

/* Target pad height, independent of ordinary keyboard scaling. Android supplies pixel constraints. */
public final class HandwritingHeight
{
  public static final int MIN_DP = 180;
  public static final int MAX_DP = 400;
  public static final int DEFAULT_DP = 260;
  public static final int STEP_DP = 10;

  private HandwritingHeight()
  {
  }

  public static int clampDp(final int heightDp)
  {
    return Math.max(MIN_DP, Math.min(MAX_DP, heightDp));
  }

  static int panelLimitPx(final int windowHeightPx, final int parentLimitPx)
  {
    // Keep some application content visible, including in landscape and split-screen.
    return Math.max(0, Math.min(parentLimitPx, Math.round(windowHeightPx * 0.75f)));
  }

  static int fitPadPx(final int requestedPx, final int keyboardLimitPx, final int fixedKeysPx)
  {
    // Preserve the candidate/toolbar space (already excluded by the container) and function keys.
    return Math.max(0, Math.min(requestedPx, keyboardLimitPx - fixedKeysPx));
  }
}
