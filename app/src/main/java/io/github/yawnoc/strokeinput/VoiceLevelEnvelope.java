/*
  Copyright 2026 Haoke Tools (浩科工具站)
  Licensed under GPL-3.0-only; see LICENSE.
*/
package io.github.yawnoc.strokeinput;

/* A small attack/release envelope for visual feedback only; it never alters recorded PCM. */
final class VoiceLevelEnvelope
{
  private float level;
  private long previousFrame;
  private boolean active;

  float update(final float input, final boolean recording, final boolean animate, final long frameTime)
  {
    if (!recording)
    {
      reset();
      return 0;
    }
    final float target = Float.isFinite(input) ? Math.max(0, Math.min(1, input)) : 0;
    final long elapsed = active ? Math.max(0, Math.min(100, frameTime - previousFrame)) : 33;
    if (animate)
    {
      final double responseMilliseconds = target > level ? 65 : 180;
      level += (target - level) * (float) (1 - Math.exp(-elapsed / responseMilliseconds));
      if (level < 0.0001f)
      {
        level = 0;
      }
    }
    else
    {
      level = target;
    }
    previousFrame = frameTime;
    active = true;
    return level;
  }

  void reset()
  {
    level = 0;
    previousFrame = 0;
    active = false;
  }
}
