/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
  Turns the recogniser's CTC output (per time step, a probability for every label; label 0 is the blank)
  into candidates: the best reading first, then readings with one character swapped for a runner-up,
  most likely first. Space labels separate characters and are not output.
*/
public final class HandwritingDecoder
{
  private HandwritingDecoder()
  {
  }

  private static final class Alternative
  {
    final String text;
    final float probability;

    Alternative(final String text, final float probability)
    {
      this.text = text;
      this.probability = probability;
    }
  }

  /*
    probabilities holds timeSteps rows of labels.size() values; labels.get(0) is the blank.
  */
  public static List<String> candidates(
    final float[] probabilities,
    final int timeSteps,
    final List<String> labels,
    final int alternativesPerCharacter,
    final int maxCandidates
  )
  {
    final int labelCount = labels.size();

    // Segments of consecutive time steps with the same non-blank best label
    final List<int[]> segments = new ArrayList<>(); // first and last time step
    int previousLabel = 0;
    for (int timeStep = 0; timeStep < timeSteps; timeStep++)
    {
      final int bestLabel = argMax(probabilities, timeStep * labelCount, labelCount);
      if (bestLabel != 0 && bestLabel == previousLabel)
      {
        segments.get(segments.size() - 1)[1] = timeStep;
      }
      else if (bestLabel != 0)
      {
        segments.add(new int[]{timeStep, timeStep});
      }
      previousLabel = bestLabel;
    }

    // For each segment, the labels ranked by their peak probability within it
    final List<List<Alternative>> readings = new ArrayList<>();
    for (final int[] segment : segments)
    {
      final float[] peak = new float[labelCount];
      for (int timeStep = segment[0]; timeStep <= segment[1]; timeStep++)
      {
        for (int label = 1; label < labelCount; label++)
        {
          peak[label] = Math.max(peak[label], probabilities[timeStep * labelCount + label]);
        }
      }
      final List<Alternative> ranked = new ArrayList<>();
      for (int rank = 0; rank < alternativesPerCharacter; rank++)
      {
        final int label = argMax(peak, 0, labelCount);
        if (peak[label] <= 0)
        {
          break;
        }
        ranked.add(new Alternative(labels.get(label), peak[label]));
        peak[label] = 0;
      }
      if (!ranked.isEmpty() && !ranked.get(0).text.trim().isEmpty())
      {
        readings.add(ranked);
      }
    }

    final List<String> candidates = new ArrayList<>();
    if (readings.isEmpty())
    {
      return candidates;
    }
    final StringBuilder best = new StringBuilder();
    for (final List<Alternative> reading : readings)
    {
      best.append(reading.get(0).text);
    }
    candidates.add(best.toString());

    final List<Alternative> variants = new ArrayList<>();
    for (int position = 0; position < readings.size(); position++)
    {
      final List<Alternative> reading = readings.get(position);
      for (final Alternative alternative : reading.subList(1, reading.size()))
      {
        if (alternative.text.trim().isEmpty())
        {
          continue;
        }
        final StringBuilder variant = new StringBuilder();
        for (int other = 0; other < readings.size(); other++)
        {
          variant.append((other == position) ? alternative.text : readings.get(other).get(0).text);
        }
        variants.add(new Alternative(variant.toString(), alternative.probability));
      }
    }
    Collections.sort(variants, (first, second) -> Float.compare(second.probability, first.probability));
    for (final Alternative variant : variants)
    {
      if (candidates.size() >= maxCandidates)
      {
        break;
      }
      if (!candidates.contains(variant.text))
      {
        candidates.add(variant.text);
      }
    }
    return candidates;
  }

  private static int argMax(final float[] values, final int start, final int count)
  {
    int best = 0;
    for (int index = 1; index < count; index++)
    {
      if (values[start + index] > values[start + best])
      {
        best = index;
      }
    }
    return best;
  }
}
