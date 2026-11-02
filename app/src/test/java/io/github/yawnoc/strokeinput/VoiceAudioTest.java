/* Copyright 2026 Haoke Tools. Licensed under GPL-3.0-only; see LICENSE. */
package io.github.yawnoc.strokeinput;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceAudioTest
{
  @Test
  public void mutedOrConstantInput_doesNotReachRecognition()
  {
    assertFalse(VoiceAudio.hasSignal(new float[32000]));
    assertFalse(VoiceAudio.hasSignal(new float[]{0.1f, 0.1f, 0.1f}));
    assertFalse(VoiceAudio.hasSignal(new float[]{-1f / 32768, 1f / 32768}));
    assertFalse(VoiceAudio.hasSignal(new float[0]));
  }

  @Test
  public void quietVaryingInput_isNotMistakenForDigitalSilence()
  {
    assertTrue(VoiceAudio.hasSignal(new float[]{0, .0002f, -.0002f, 0}));
    assertTrue(VoiceAudio.hasSignal(new float[]{-.8f, .5f, -.3f, .6f}));
  }

  @Test
  public void invalidPcm_isRejected()
  {
    assertFalse(VoiceAudio.hasSignal(null));
    assertFalse(VoiceAudio.hasSignal(new float[]{Float.NaN, .1f}));
    assertFalse(VoiceAudio.hasSignal(new float[]{Float.POSITIVE_INFINITY, .1f}));
    assertFalse(VoiceAudio.hasSignal(new float[]{-2, .1f}));
  }
}
