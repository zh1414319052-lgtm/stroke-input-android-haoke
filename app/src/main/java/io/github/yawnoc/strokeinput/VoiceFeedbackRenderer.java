/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under GPL-3.0-only; see LICENSE.
*/
package io.github.yawnoc.strokeinput;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
  Draws voice feedback only. The owning View controls frame scheduling (at most 30 fps),
  resets this renderer on hide/detach/keyboard changes, and passes the real recording state.
  Theme colours and the existing licensed text paint come from the keyboard; no font is substituted.
  The rings indicate recording. The bars reflect microphone level, never a fabricated idle waveform.
*/
public final class VoiceFeedbackRenderer
{
  private static final int BAR_COUNT = 5;
  private static final long HISTORY_STEP_MILLISECONDS = 60;
  private final float density;
  private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF barBounds = new RectF();
  private final VoiceLevelEnvelope envelope = new VoiceLevelEnvelope();
  private final float[] history = new float[BAR_COUNT];
  private final List<String> statusLines = new ArrayList<>(3);
  private String previousStatus;
  private float previousTextSize = -1;
  private float previousTextWidth = -1;
  private boolean wasRecording;
  private long recordingStartedAt;
  private long lastHistoryTime;

  public VoiceFeedbackRenderer(final Resources resources)
  {
    density = resources.getDisplayMetrics().density;
  }

  public void reset()
  {
    envelope.reset();
    Arrays.fill(history, 0);
    wasRecording = false;
    recordingStartedAt = 0;
    lastHistoryTime = 0;
  }

  public void draw(
    final Canvas canvas, final RectF padBounds,
    final int accent, final int onAccent, final int foreground,
    final boolean recording, final float level, final boolean pressed,
    final String status, final Paint textPaint, final Drawable microphoneIcon,
    final long frameTimeMilliseconds, final boolean animationsEnabled
  )
  {
    if (padBounds.width() <= 0 || padBounds.height() <= 0)
    {
      return;
    }
    if (!recording && wasRecording)
    {
      reset();
    }
    if (recording && !wasRecording)
    {
      recordingStartedAt = frameTimeMilliseconds;
      lastHistoryTime = frameTimeMilliseconds;
      Arrays.fill(history, 0);
    }
    wasRecording = recording;
    final float displayedLevel = envelope.update(level, recording, animationsEnabled, frameTimeMilliseconds);
    if (!animationsEnabled)
    {
      Arrays.fill(history, displayedLevel);
    }
    else if (recording && frameTimeMilliseconds - lastHistoryTime >= HISTORY_STEP_MILLISECONDS)
    {
      System.arraycopy(history, 0, history, 1, BAR_COUNT - 1);
      history[0] = displayedLevel;
      lastHistoryTime = frameTimeMilliseconds;
    }

    final float padding = Math.min(12 * density, padBounds.width() * 0.06f);
    final float textWidth = Math.max(1, padBounds.width() - 2 * padding);
    statusPaint.set(textPaint); // retain typeface, font scaling, letter spacing conventions and flags
    statusPaint.setTextAlign(Paint.Align.CENTER);
    statusPaint.setColor(ColorUtils.setAlphaComponent(foreground, 0xE6));
    final String currentStatus = status == null ? "" : status;
    if (!currentStatus.equals(previousStatus) || previousTextSize != statusPaint.getTextSize()
      || previousTextWidth != textWidth)
    {
      layoutStatus(currentStatus, textWidth);
      previousStatus = currentStatus;
      previousTextSize = statusPaint.getTextSize();
      previousTextWidth = textWidth;
    }
    final float lineHeight = statusPaint.getFontSpacing() + 2 * density;
    final float textHeight = statusLines.size() * lineHeight;
    // Give the full label priority over rings, bars and decorative space at large font scales.
    final float textPadding = Math.min(padding, Math.max(0, (padBounds.height() - textHeight) / 2));
    final float gap = 12 * density;
    final float visualHeight = Math.max(0, padBounds.height() - 2 * padding - textHeight - gap);
    final float centreX = padBounds.centerX();
    final float centreY = padBounds.top + padding + visualHeight / 2;
    final float radius = Math.min(30 * density, Math.min(visualHeight / 3.1f, padBounds.width() / 7.5f));

    final int save = canvas.save();
    canvas.clipRect(padBounds);
    if (radius >= 3 * density)
    {
      final double phase = (frameTimeMilliseconds - recordingStartedAt) / 1550.0 * 2 * Math.PI;
      final float breath = recording && animationsEnabled ? (float) ((Math.sin(phase) + 1) / 2) : 0.5f;
      shapePaint.setStyle(Paint.Style.FILL);
      if (recording)
      {
        // Small, slow rings show that the mic is open even when the input is digitally silent.
        final float reach = Math.min(7 * density, radius * 0.24f);
        shapePaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(17 + breath * 9)));
        canvas.drawCircle(centreX, centreY, radius + reach * (1.05f + breath * 0.45f), shapePaint);
        shapePaint.setColor(ColorUtils.setAlphaComponent(accent, 35));
        canvas.drawCircle(centreX, centreY, radius + reach * 0.48f, shapePaint);
      }

      shapePaint.setColor(recording ? accent : ColorUtils.setAlphaComponent(accent, pressed ? 54 : 25));
      canvas.drawCircle(centreX, centreY, radius * (pressed ? 0.97f : 1), shapePaint);
      if (!recording)
      {
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(1.2f * density);
        shapePaint.setColor(ColorUtils.setAlphaComponent(accent, 65));
        canvas.drawCircle(centreX, centreY, radius - density, shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);
      }
      if (microphoneIcon != null)
      {
        final int half = Math.max(1, Math.round(radius * 0.53f));
        microphoneIcon.setBounds(Math.round(centreX) - half, Math.round(centreY) - half,
          Math.round(centreX) + half, Math.round(centreY) + half);
        microphoneIcon.setTint(recording ? onAccent : accent);
        microphoneIcon.draw(canvas);
      }
      if (recording)
      {
        drawLevelBars(canvas, padBounds, centreX, centreY, radius, visualHeight, accent, displayedLevel);
      }
    }

    // If the text alone exceeds the entire host pad, keep the chosen accessible font size;
    // the host must offer a taller pad. Do not silently truncate or shrink the wording here.
    final float firstBaseline = Math.max(padBounds.top + textPadding,
      padBounds.bottom - textPadding - textHeight) - statusPaint.ascent();
    for (int line = 0; line < statusLines.size(); line++)
    {
      canvas.drawText(statusLines.get(line), centreX, firstBaseline + line * lineHeight, statusPaint);
    }
    canvas.restoreToCount(save);
  }

  private void drawLevelBars(
    final Canvas canvas, final RectF padBounds, final float centreX, final float centreY,
    final float radius, final float visualHeight, final int accent, final float currentLevel
  )
  {
    final float innerDistance = radius + 20 * density;
    final float sideSpace = padBounds.width() / 2 - 14 * density - innerDistance;
    if (sideSpace < 16 * density)
    {
      return; // constrained window: prioritise the mic and the complete status label
    }
    final float step = Math.min(8 * density, sideSpace / (BAR_COUNT - 1));
    final float width = Math.min(3.2f * density, step * 0.46f);
    final float minHeight = width;
    final float maxHeight = Math.min(40 * density, visualHeight * 0.62f);
    shapePaint.setStyle(Paint.Style.FILL);
    for (int index = 0; index < BAR_COUNT; index++)
    {
      // Nearest bar follows the latest level, outer bars hold a short real level history.
      final float sample = index == 0 ? currentLevel : history[index - 1];
      final float response = (float) Math.sqrt(sample);
      final float height = minHeight + Math.max(0, maxHeight - minHeight) * response;
      shapePaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(75 + 155 * response)));
      final float offset = innerDistance + index * step;
      for (int direction = -1; direction <= 1; direction += 2)
      {
        final float x = centreX + direction * offset;
        barBounds.set(x - width / 2, centreY - height / 2, x + width / 2, centreY + height / 2);
        canvas.drawRoundRect(barBounds, width / 2, width / 2, shapePaint);
      }
    }
  }

  /* Status is stable between audio frames; only allocate line strings when it or the width changes. */
  private void layoutStatus(final String text, final float width)
  {
    statusLines.clear();
    int start = 0;
    while (start < text.length())
    {
      int count = Math.max(1, statusPaint.breakText(text, start, text.length(), true, width, null));
      if (start + count < text.length())
      {
        final int space = text.lastIndexOf(' ', start + count - 1);
        if (space > start)
        {
          count = space - start;
        }
      }
      statusLines.add(text.substring(start, Math.min(text.length(), start + count)).trim());
      start += count;
      while (start < text.length() && text.charAt(start) == ' ')
      {
        start++;
      }
    }
  }
}
