/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import java.util.List;

/*
  A transparent layer over the whole input panel that draws the handwriting ink.
  Half screen: the ink stays on the pad key, or over the ordinary keyboard when direct handwriting
  is enabled; taps still go through to the keys below.
  Full screen: the panel fills the screen, and strokes may start anywhere above the candidates
  (over the app, which stays visible) as well as on the pad.
  The ink belongs to the keyboard view, in its coordinates.
*/
public class HandwritingOverlayView
  extends View
{
  private static final int HALO_ALPHA = 0xB0;

  private KeyboardView keyboardView;
  private View candidatesFrame;
  private boolean isFullScreen = false;
  private boolean strokeIsActive = false;
  private float keyboardOffsetX;
  private float keyboardOffsetY;

  private final Paint inkPaint;
  private final Paint haloPaint;
  private final Path inkPath = new Path();
  private final int[] ownLocation = new int[2];
  private final int[] otherLocation = new int[2];

  public HandwritingOverlayView(final Context context, final AttributeSet attributes)
  {
    super(context, attributes);

    final float inkWidth = context.getResources().getDimension(R.dimen.width__handwriting_ink);
    inkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    inkPaint.setStyle(Paint.Style.STROKE);
    inkPaint.setStrokeCap(Paint.Cap.ROUND);
    inkPaint.setStrokeJoin(Paint.Join.ROUND);
    inkPaint.setStrokeWidth(inkWidth);

    haloPaint = new Paint(inkPaint);
    haloPaint.setStrokeWidth(inkWidth + context.getResources().getDimension(R.dimen.width__handwriting_ink_halo));
  }

  public void initialise(final KeyboardView keyboardView, final View candidatesFrame)
  {
    this.keyboardView = keyboardView;
    this.candidatesFrame = candidatesFrame;
  }

  public void setFullScreen(final boolean isFullScreen)
  {
    this.isFullScreen = isFullScreen;
    strokeIsActive = false;
    invalidate();
  }

  @Override
  protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec)
  {
    // This transparent MATCH_PARENT layer must follow the content, never enlarge it.
    // FrameLayout's first AT_MOST pass otherwise gives it the entire IME window, squeezing
    // the navigation spacer to zero. Its second EXACTLY pass supplies the final frame size.
    final int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
      ? MeasureSpec.getSize(heightMeasureSpec) : 0;
    setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), height);
  }

  private void updateKeyboardOffset()
  {
    getLocationInWindow(ownLocation);
    keyboardView.getLocationInWindow(otherLocation);
    keyboardOffsetX = otherLocation[0] - ownLocation[0];
    keyboardOffsetY = otherLocation[1] - ownLocation[1];
  }

  @Override
  protected void onDraw(@NonNull final Canvas canvas)
  {
    if (keyboardView == null)
    {
      return;
    }
    final Key padKey = keyboardView.getHandwritingPadKey();
    final HandwritingInk ink = keyboardView.getHandwritingInk();
    if (!keyboardView.isHandwritingAvailable() || ink.isEmpty())
    {
      return;
    }

    updateKeyboardOffset();
    canvas.save();
    canvas.translate(keyboardOffsetX, keyboardOffsetY);
    if (!isFullScreen)
    {
      if (padKey == null)
      {
        canvas.clipRect(0, 0, keyboardView.getWidth(), keyboardView.getHeight());
      }
      else
      {
        final float inset = keyboardView.getKeyInsetPx();
        canvas.clipRect(
          padKey.x + inset,
          padKey.y + inset,
          padKey.x + padKey.width - inset,
          padKey.y + padKey.height - inset
        );
      }
    }

    final int inkColour = keyboardView.getHandwritingInkColour();
    inkPaint.setColor(inkColour);
    // Over the app (full screen), a halo of the opposite lightness keeps the ink readable on any background
    final boolean inkIsLight = ColorUtils.calculateLuminance(inkColour) > 0.5;
    haloPaint.setColor(ColorUtils.setAlphaComponent(inkIsLight ? Color.BLACK : Color.WHITE, HALO_ALPHA));

    final List<float[]> strokes = ink.getStrokes();
    if (isFullScreen)
    {
      drawStrokes(canvas, strokes, haloPaint);
    }
    drawStrokes(canvas, strokes, inkPaint);
    canvas.restore();
  }

  private void drawStrokes(final Canvas canvas, final List<float[]> strokes, final Paint paint)
  {
    for (final float[] stroke : strokes)
    {
      if (stroke.length == 2)
      {
        canvas.drawPoint(stroke[0], stroke[1], paint);
        continue;
      }
      inkPath.reset();
      inkPath.moveTo(stroke[0], stroke[1]);
      for (int index = 2; index + 1 < stroke.length; index += 2)
      {
        inkPath.lineTo(stroke[index], stroke[index + 1]);
      }
      canvas.drawPath(inkPath, paint);
    }
  }

  /*
    Full screen only: a stroke starting above the candidates is ink (one finger at a time);
    anything else goes through to the candidates and keys below.
  */
  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent(final MotionEvent event)
  {
    if (!isFullScreen || keyboardView == null || !keyboardView.isHandwritingAvailable())
    {
      return false;
    }

    final int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN)
    {
      getLocationInWindow(ownLocation);
      candidatesFrame.getLocationInWindow(otherLocation);
      final float candidatesTop = otherLocation[1] - ownLocation[1];
      strokeIsActive = event.getY() < candidatesTop;
      if (strokeIsActive)
      {
        updateKeyboardOffset();
        keyboardView.startHandwritingStroke(event.getX() - keyboardOffsetX, event.getY() - keyboardOffsetY);
      }
      return strokeIsActive;
    }
    if (!strokeIsActive)
    {
      return false;
    }

    switch (action)
    {
      case MotionEvent.ACTION_MOVE:
        for (int index = 0; index < event.getHistorySize(); index++)
        {
          keyboardView.addHandwritingPoint(
            event.getHistoricalX(0, index) - keyboardOffsetX,
            event.getHistoricalY(0, index) - keyboardOffsetY
          );
        }
        keyboardView.addHandwritingPoint(event.getX(0) - keyboardOffsetX, event.getY(0) - keyboardOffsetY);
        break;

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (action == MotionEvent.ACTION_UP)
        {
          keyboardView.addHandwritingPoint(event.getX(0) - keyboardOffsetX, event.getY(0) - keyboardOffsetY);
        }
        strokeIsActive = false;
        keyboardView.finishHandwritingStroke();
        break;
    }
    return true;
  }
}
