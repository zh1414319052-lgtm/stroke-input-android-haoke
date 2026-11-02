/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
  Ink written on the handwriting pad: strokes of points in pad pixels (origin at the pad's top left).
  Also works out where the ink goes in the line image given to the recogniser.
*/
public final class HandwritingInk
{
  // Line image geometry, tuned with PP-OCRv6 rec small on simulated finger writing
  static final float MIN_BOX_HEIGHT_FRACTION_OF_PAD = 0.3f; // a lone 一 is not blown up to full height
  static final float MIN_BOX_WIDTH_FRACTION_OF_HEIGHT = 0.6f; // nor a lone 丨 to full width
  static final float MARGIN_FRACTION_OF_HEIGHT = 0.12f;

  private final List<float[]> strokes = new ArrayList<>(); // x0, y0, x1, y1, ...
  private float[] currentStroke;
  private int currentStrokeSize;
  private int version;

  public void startStroke(final float x, final float y)
  {
    finishStroke();
    currentStroke = new float[64];
    currentStrokeSize = 0;
    addPoint(x, y);
  }

  public void addPoint(final float x, final float y)
  {
    if (currentStroke == null)
    {
      return;
    }
    if (currentStrokeSize + 2 > currentStroke.length)
    {
      currentStroke = Arrays.copyOf(currentStroke, currentStroke.length * 2);
    }
    currentStroke[currentStrokeSize++] = x;
    currentStroke[currentStrokeSize++] = y;
    version++;
  }

  public void finishStroke()
  {
    if (currentStroke != null)
    {
      strokes.add(Arrays.copyOf(currentStroke, currentStrokeSize));
      currentStroke = null;
      currentStrokeSize = 0;
      version++;
    }
  }

  public void removeLastStroke()
  {
    finishStroke();
    if (!strokes.isEmpty())
    {
      strokes.remove(strokes.size() - 1);
      version++;
    }
  }

  public void clear()
  {
    currentStroke = null;
    currentStrokeSize = 0;
    strokes.clear();
    version++;
  }

  public boolean isEmpty()
  {
    return strokes.isEmpty() && currentStroke == null;
  }

  /*
    Changes on every edit, so that a recognition result for older ink can be dropped.
  */
  public int getVersion()
  {
    return version;
  }

  /*
    Whether all the ink fits in a square of the given size (a tap rather than writing).
  */
  public boolean isTap(final float maxSize)
  {
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    for (final float[] stroke : getStrokes())
    {
      for (int index = 0; index + 1 < stroke.length; index += 2)
      {
        minX = Math.min(minX, stroke[index]);
        maxX = Math.max(maxX, stroke[index]);
        minY = Math.min(minY, stroke[index + 1]);
        maxY = Math.max(maxY, stroke[index + 1]);
      }
    }
    return minX <= maxX && maxX - minX <= maxSize && maxY - minY <= maxSize;
  }

  /*
    Copies of all strokes, including one still being drawn.
  */
  public List<float[]> getStrokes()
  {
    final List<float[]> copies = new ArrayList<>();
    for (final float[] stroke : strokes)
    {
      copies.add(stroke.clone());
    }
    if (currentStroke != null)
    {
      copies.add(Arrays.copyOf(currentStroke, currentStrokeSize));
    }
    return copies;
  }

  /*
    Placement of the ink in a line image of the given height: image = ink * scale + offset.
  */
  public static final class LineLayout
  {
    public final float scale;
    public final float offsetX;
    public final float offsetY;
    public final int width;

    LineLayout(final float scale, final float offsetX, final float offsetY, final int width)
    {
      this.scale = scale;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
      this.width = width;
    }
  }

  public static LineLayout lineLayout(final List<float[]> strokes, final float padHeight, final int lineHeight)
  {
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    for (final float[] stroke : strokes)
    {
      for (int index = 0; index + 1 < stroke.length; index += 2)
      {
        minX = Math.min(minX, stroke[index]);
        maxX = Math.max(maxX, stroke[index]);
        minY = Math.min(minY, stroke[index + 1]);
        maxY = Math.max(maxY, stroke[index + 1]);
      }
    }
    if (minX > maxX) // no points
    {
      return new LineLayout(1, 0, 0, lineHeight);
    }

    final float boxHeight = Math.max(maxY - minY, MIN_BOX_HEIGHT_FRACTION_OF_PAD * padHeight);
    final float boxWidth = Math.max(maxX - minX, MIN_BOX_WIDTH_FRACTION_OF_HEIGHT * boxHeight);
    final float fullHeight = boxHeight * (1 + 2 * MARGIN_FRACTION_OF_HEIGHT);
    final float fullWidth = boxWidth + 2 * MARGIN_FRACTION_OF_HEIGHT * boxHeight;

    final float scale = lineHeight / fullHeight;
    final int width = Math.max(lineHeight, (int) Math.ceil(fullWidth * scale));
    final float offsetX = width / 2f - (minX + maxX) / 2f * scale;
    final float offsetY = lineHeight / 2f - (minY + maxY) / 2f * scale;
    return new LineLayout(scale, offsetX, offsetY, width);
  }
}
