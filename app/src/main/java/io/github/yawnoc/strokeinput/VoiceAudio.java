/*
  Copyright 2026 Haoke Tools (浩科工具站)
  Licensed under GPL-3.0-only; see LICENSE.
*/
package io.github.yawnoc.strokeinput;

final class VoiceAudio
{
  private VoiceAudio() {}

  // Reject digital silence, one-LSB dither and DC offsets before ASR can hallucinate text.
  // This deliberately low threshold is not speech detection and does not reject normal quiet speech.
  static boolean hasSignal(final float[] samples)
  {
    if (samples == null || samples.length == 0)
    {
      return false;
    }
    double sum = 0;
    double squares = 0;
    for (final float sample : samples)
    {
      if (!Float.isFinite(sample) || Math.abs(sample) > 1)
      {
        return false;
      }
      sum += sample;
      squares += (double) sample * sample;
    }
    final double mean = sum / samples.length;
    final double variance = Math.max(0, squares / samples.length - mean * mean);
    final double pcmFloor = 1.0 / 32768;
    return variance > pcmFloor * pcmFloor;
  }
}
