/* Copyright 2026 Haoke Tools. Licensed under GPL-3.0-only; see LICENSE. */
package io.github.yawnoc.strokeinput;

/** Only reserve the system navigation area actually overlapping this input container. */
final class ImeNavigationSpace
{
  private ImeNavigationSpace() {}

  static int overlap(final int navigationHeight, final int rootBottom, final int containerBottom)
  {
    final int height = Math.max(0, navigationHeight);
    return Math.min(height, Math.max(0, containerBottom - (rootBottom - height)));
  }
}
