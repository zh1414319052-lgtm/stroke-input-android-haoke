/*
  Copyright 2021, 2023, 2025 Conway
  Modified 2026-10-28 by Haoke Tools (浩科工具站): wildcard stroke, stroke order display, text size.
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.appcompat.widget.AppCompatTextView;

/*
  A bar showing the current stroke sequence.
*/
public class StrokeSequenceBar
  extends AppCompatTextView
{
  private final float baseTextSizePx;

  public StrokeSequenceBar(Context context, AttributeSet attributes)
  {
    super(context, attributes);
    baseTextSizePx = getTextSize();
  }

  /*
    Convert stroke digits (including the wildcard) to the stroke characters shown to the user.
  */
  public static String toStrokeText(final Context context, final String strokeDigitSequence)
  {
    return
      strokeDigitSequence
        .replace(StrokeInputService.STROKE_DIGIT_1, context.getString(R.string.stroke_1))
        .replace(StrokeInputService.STROKE_DIGIT_2, context.getString(R.string.stroke_2))
        .replace(StrokeInputService.STROKE_DIGIT_3, context.getString(R.string.stroke_3))
        .replace(StrokeInputService.STROKE_DIGIT_4, context.getString(R.string.stroke_4))
        .replace(StrokeInputService.STROKE_DIGIT_5, context.getString(R.string.stroke_5))
        .replace(StrokeInputService.STROKE_DIGIT_WILDCARD, context.getString(R.string.stroke_wildcard));
  }

  public void setSizeScale(final float sizeScale)
  {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSizePx * sizeScale);
  }

  public void setStrokeDigitSequence(final String strokeDigitSequence)
  {
    if (!strokeDigitSequence.isEmpty())
    {
      setText(toStrokeText(getContext(), strokeDigitSequence));
      requestLayout();
      setVisibility(VISIBLE);
    }
    else
    {
      setVisibility(INVISIBLE);
    }
  }

  public void showStrokeOrder(final String strokeOrderText)
  {
    setText(strokeOrderText);
    requestLayout();
    setVisibility(VISIBLE);
  }
}
