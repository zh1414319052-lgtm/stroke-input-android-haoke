/* Copyright 2026 Haoke Tools. Licensed under GPL-3.0-only; see LICENSE. */
package io.github.yawnoc.strokeinput;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceLevelEnvelopeTest
{
  @Test
  public void recordingLevels_attackAndDecay_withoutInventingSound()
  {
    final VoiceLevelEnvelope envelope = new VoiceLevelEnvelope();
    assertEquals(0, envelope.update(0, true, true, 0), 0);
    final float rise = envelope.update(1, true, true, 33);
    assertTrue(rise > 0 && rise < 1);
    final float fall = envelope.update(0, true, true, 66);
    assertTrue(fall > 0 && fall < rise);
    for (int frame = 3; frame < 80; frame++)
    {
      envelope.update(0, true, true, frame * 33);
    }
    assertEquals(0, envelope.update(0, true, true, 80 * 33), 0);
  }

  @Test
  public void stopAndReducedMotion_areImmediateAndBounded()
  {
    final VoiceLevelEnvelope envelope = new VoiceLevelEnvelope();
    assertEquals(.75f, envelope.update(.75f, true, false, 1), 0);
    assertEquals(1, envelope.update(10, true, false, 2), 0);
    assertEquals(0, envelope.update(Float.NaN, true, false, 3), 0);
    envelope.update(1, true, false, 4);
    assertEquals(0, envelope.update(1, false, true, 5), 0);
    assertEquals(0, envelope.update(0, true, true, 6), 0);
  }
}
