/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

/*
  Pure touch-policy helpers. Keeping the distance and key-conflict rules outside the View makes
  the tap-versus-ink contract testable without an Android window.
*/
public final class HandwritingGesturePolicy
{
  private HandwritingGesturePolicy()
  {
  }

  public static boolean hasMovedBeyondSlop(
    final float downX,
    final float downY,
    final float currentX,
    final float currentY,
    final float touchSlop
  )
  {
    final float deltaX = currentX - downX;
    final float deltaY = currentY - downY;
    return deltaX * deltaX + deltaY * deltaY > touchSlop * touchSlop;
  }

  public static boolean canStartDirectInk(
    final boolean handwritingIsEnabled,
    final boolean hasDedicatedHandwritingPad,
    final boolean hasVoicePad,
    final boolean isShiftKey,
    final boolean hasOwnSwipeGesture,
    final boolean hasOwnExtendedPressGesture
  )
  {
    return handwritingIsEnabled
      && !hasDedicatedHandwritingPad
      && !hasVoicePad
      && !isShiftKey
      && !hasOwnSwipeGesture
      && !hasOwnExtendedPressGesture;
  }
}
