/*
  Copyright 2021--2023 Conway
  Modified 2026-10-28 by Haoke Tools (浩科工具站): key vibration and sound, key hint text,
  rounded keys with gaps (design/stroke-input.haoke-ui.json), handwriting pad, voice pad.
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/
/*
  This file contains bytes copied from the deprecated `KeyboardView` class,
  i.e. `core/java/android/inputmethodservice/KeyboardView.java`
  from <https://android.googlesource.com/platform/frameworks/base>,
  which is licensed under the Apache License 2.0,
  see <https://www.apache.org/licenses/LICENSE-2.0.html>.
  ---
  Take your pick from the following out-of-date notices:
  In `core/java/android/inputmethodservice/KeyboardView.java`:
    Copyright (C) 2008-2009 Google Inc.
  In `NOTICE`:
    Copyright 2005-2008 The Android Open Source Project
*/

package io.github.yawnoc.strokeinput;

import android.annotation.SuppressLint;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.ColorUtils;

import java.util.List;

/*
  A view that holds a keyboard.
  Touch logic is implemented here.
*/
public class KeyboardView
  extends View
  implements View.OnClickListener
{
  private static final int NONEXISTENT_POINTER_ID = -1;

  private static final int MESSAGE_KEY_REPEAT = 1;
  private static final int MESSAGE_LONG_PRESS = 2;
  private static final int DEFAULT_KEY_REPEAT_INTERVAL_MILLISECONDS = 75;
  private static final int KEY_REPEAT_START_MILLISECONDS = 500;
  private static final int KEY_LONG_PRESS_MILLISECONDS = 750;

  private static final int SWIPE_ACTIVATION_DISTANCE = 40;

  public static final int SHIFT_DISABLED = 0;
  private static final int SHIFT_SINGLE = 1;
  private static final int SHIFT_PERSISTENT = 2;
  private static final int SHIFT_INITIATED = 3;
  private static final int SHIFT_HELD = 4;

  public static final String KEYBOARD_FONT_FILE_NAME = "StrokeInputFont.ttf";
  private static final float COLOUR_LIGHTNESS_CUTOFF = 0.7f;
  private static final float KEY_HINT_TEXT_SIZE_FRACTION = 0.4f;
  private static final float KEY_HINT_MARGIN_FRACTION = 0.08f;
  private static final int KEY_HINT_ALPHA = 0xA0;
  private static final float KEY_SOUND_VOLUME = -1f; // default key click volume
  private static final int HANDWRITING_HINT_ALPHA = 0x80;

  // View properties
  private KeyboardListener keyboardListener;
  private LinearLayout mainInputPlane;
  private KeyPreviewPlane keyPreviewPlane;
  private Keyboard keyboard;
  private List<Key> keys;

  // Active key
  private Key activeKey;
  private int activePointerId = NONEXISTENT_POINTER_ID;

  // Long presses and key repeats
  private Handler extendedPressHandler;
  private int keyRepeatIntervalMilliseconds;

  // Horizontal swipes
  private int pointerDownX;
  private boolean swipeModeIsActivated = false;

  // Shift key
  private int shiftPointerId = NONEXISTENT_POINTER_ID;
  private int shiftMode;

  // Keyboard drawing
  private Rect keyboardRectangle;
  private Paint keyboardFillPaint;

  // Key drawing (Haoke design: rounded keys separated by a gap, no border)
  private Rect keyRectangle;
  private RectF keyRoundRectangle;
  private float keyInsetPx;
  private float keyRadiusPx;
  private float keyShadowOffsetPx;
  private Paint keyShadowPaint; // drawn only when the theme has a key shadow
  private boolean keyShadowIsShown = false;
  private Paint keyFillPaint;
  private Paint keyTextPaint;
  private Paint keyHintPaint;

  // Handwriting (Haoke): a dedicated pad writes immediately; ordinary keys become ink only after
  // movement passes Android's touch slop, so taps keep typing. The overlay draws both kinds of ink.
  private final HandwritingInk handwritingInk = new HandwritingInk();
  private Key handwritingPadKey; // on the current keyboard, if any
  private boolean handwritingStrokeIsActive = false;
  private boolean handwritingIsEnabled = false;
  private final float directHandwritingTouchSlopPx;
  private int directHandwritingPointerId = NONEXISTENT_POINTER_ID;
  private float directHandwritingDownX;
  private float directHandwritingDownY;
  private boolean directHandwritingStrokeIsActive = false;
  private String handwritingHintText = "";
  private Paint handwritingHintPaint;
  private HandwritingOverlayView handwritingOverlay;

  // Voice pad (Haoke): press to talk (or tap to start and tap again to stop); the service records and recognises
  private Key voicePadKey; // on the current keyboard, if any
  private boolean voicePadIsPressed = false;
  private String voiceStatusText = "";
  private boolean voiceIsRecording = false;
  private float voiceLevel = 0;
  private Drawable microphoneIcon;
  private Drawable returnIcon;
  private KeyboardTheme currentTheme = KeyboardTheme.forName(KeyboardTheme.DEFAULT);
  private VoiceFeedbackRenderer voiceFeedback;
  private final RectF voiceBounds = new RectF();
  private boolean voiceFrameQueued;
  private final Runnable voiceAnimationTick = () ->
  {
    voiceFrameQueued = false;
    if (shouldAnimateVoice())
    {
      invalidate(voicePadKey.x, voicePadKey.y,
        voicePadKey.x + voicePadKey.width, voicePadKey.y + voicePadKey.height);
    }
  };

  // Key feedback (no permission needed: haptics follow the system touch-vibration setting)
  private boolean keyVibrationIsEnabled = false;
  private boolean keySoundIsEnabled = false;
  private final AudioManager audioManager;

  public KeyboardView(final Context context, final AttributeSet attributes)
  {
    super(context, attributes);

    audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    directHandwritingTouchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
    initialiseExtendedPressHandler();
    initialiseDrawing(context);
  }

  /*
    Key shape and keyboard fill of the theme (key colours themselves are set on the keys, see Keyboard.applyTheme).
  */
  public void setKeyboardTheme(final KeyboardTheme theme)
  {
    currentTheme = theme;
    final float density = getResources().getDisplayMetrics().density;
    keyRadiusPx = theme.keyRadiusDp * density;
    keyInsetPx = theme.keyGapDp * density / 2;
    keyShadowIsShown = theme.keyShadow != 0;
    keyShadowPaint.setColor(theme.keyShadow);
    if (keyboard != null)
    {
      keyboardFillPaint.setColor(keyboard.fillColour);
    }
    invalidate();
  }

  public void setKeyFeedback(final boolean vibrationIsEnabled, final boolean soundIsEnabled)
  {
    keyVibrationIsEnabled = vibrationIsEnabled;
    keySoundIsEnabled = soundIsEnabled;
  }

  private void performKeyFeedback(final Key key)
  {
    if (key == null)
    {
      return;
    }

    if (keyVibrationIsEnabled)
    {
      performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    if (keySoundIsEnabled && audioManager != null)
    {
      final int soundEffect = switch (key.valueText)
      {
        case StrokeInputService.BACKSPACE_VALUE_TEXT -> AudioManager.FX_KEYPRESS_DELETE;
        case StrokeInputService.SPACE_BAR_VALUE_TEXT -> AudioManager.FX_KEYPRESS_SPACEBAR;
        case StrokeInputService.ENTER_KEY_VALUE_TEXT -> AudioManager.FX_KEYPRESS_RETURN;
        default -> AudioManager.FX_KEYPRESS_STANDARD;
      };
      audioManager.playSoundEffect(soundEffect, KEY_SOUND_VOLUME);
    }
  }

  private void initialiseExtendedPressHandler()
  {
    resetKeyRepeatIntervalMilliseconds();

    extendedPressHandler =
            new Handler(Looper.getMainLooper())
            {
              @Override
              public void handleMessage(@NonNull Message message)
              {
                if (activeKey != null)
                {
                  switch (message.what)
                  {
                    case MESSAGE_KEY_REPEAT:
                      keyboardListener.onKey(activeKey.valueText);
                      sendExtendedPressHandlerMessage(MESSAGE_KEY_REPEAT, keyRepeatIntervalMilliseconds);
                      break;

                    case MESSAGE_LONG_PRESS:
                      keyboardListener.onLongPress(activeKey.valueText);
                      activeKey = null;
                      activePointerId = NONEXISTENT_POINTER_ID;
                      keyPreviewPlane.dismissAllImmediately();
                      invalidate();
                      break;
                  }
                }
              }
            };

  }

  private void initialiseDrawing(final Context context)
  {
    this.setBackgroundColor(Color.TRANSPARENT);

    keyboardRectangle = new Rect();
    keyboardFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    keyRectangle = new Rect();
    keyRoundRectangle = new RectF();
    keyInsetPx = context.getResources().getDimension(R.dimen.inset__key);
    keyRadiusPx = context.getResources().getDimension(R.dimen.radius__key);
    keyShadowOffsetPx = context.getResources().getDimension(R.dimen.shadow_offset__key);
    keyShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    keyShadowPaint.setStyle(Paint.Style.FILL);

    keyFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    keyFillPaint.setStyle(Paint.Style.FILL);


    keyTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    keyTextPaint.setTypeface(Typeface.createFromAsset(context.getAssets(), KEYBOARD_FONT_FILE_NAME));
    keyTextPaint.setTextAlign(Paint.Align.CENTER);

    keyHintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    keyHintPaint.setTextAlign(Paint.Align.LEFT);

    voiceFeedback = new VoiceFeedbackRenderer(context.getResources());
    final Drawable icon = AppCompatResources.getDrawable(context, R.drawable.icon_microphone);
    microphoneIcon = (icon == null) ? null : icon.mutate();
    returnIcon = java.util.Objects.requireNonNull(
      AppCompatResources.getDrawable(context, R.drawable.icon_keyboard_return)).mutate();

    handwritingHintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    handwritingHintPaint.setTextAlign(Paint.Align.CENTER);
    handwritingHintPaint.setTextSize(context.getResources().getDimension(R.dimen.font_size__handwriting_hint));
  }

  /*
    A listener for keyboard events.
  */
  public interface KeyboardListener
  {
    void onKey(String valueText);
    void onLongPress(String valueText);
    void onSwipe(String valueText);
    void saveKeyboard(Keyboard keyboard);
    void onHandwritingStrokeStart();
    void onHandwritingStrokeEnd();
    void onVoicePadDown();
    void onVoicePadUp();
    void onVoicePadCancel();
  }

  public void setKeyboardListener(final KeyboardListener keyboardListener)
  {
    this.keyboardListener = keyboardListener;
  }

  public void setMainInputPlane(final LinearLayout mainInputPlane)
  {
    this.mainInputPlane = mainInputPlane;
  }

  public void setKeyPreviewPlane(final KeyPreviewPlane keyPreviewPlane)
  {
    this.keyPreviewPlane = keyPreviewPlane;
  }

  public Keyboard getKeyboard()
  {
    return keyboard;
  }

  public void setKeyboard(final Keyboard keyboard)
  {
    keyboardListener.saveKeyboard(keyboard);
    this.keyboard = keyboard;
    keys = keyboard.getKeys();
    keyboardFillPaint.setColor(keyboard.fillColour);
    handwritingPadKey = null;
    voicePadKey = null;
    for (final Key key : keys)
    {
      if (key.valueText.equals(StrokeInputService.HANDWRITING_PAD_VALUE_TEXT))
      {
        handwritingPadKey = key;
      }
      else if (key.valueText.equals(StrokeInputService.VOICE_PAD_VALUE_TEXT))
      {
        voicePadKey = key;
      }
    }
    voicePadIsPressed = false;
    if (voicePadKey == null)
    {
      voiceIsRecording = false;
      voiceLevel = 0;
      stopVoiceAnimation();
    }
    handwritingStrokeIsActive = false;
    resetDirectHandwritingGesture();
    handwritingInk.clear();
    if (shiftMode != SHIFT_PERSISTENT)
    {
      shiftMode = SHIFT_DISABLED;
      keyPreviewPlane.updateShiftMode(shiftMode);
    }
    requestLayout();
  }

  public HandwritingInk getHandwritingInk()
  {
    return handwritingInk;
  }

  public boolean hasVoicePad()
  {
    return voicePadKey != null;
  }

  public void setVoiceStatus(final String statusText, final boolean isRecording, final float level)
  {
    if (voiceIsRecording != isRecording)
    {
      voiceFeedback.reset();
    }
    voiceStatusText = statusText;
    voiceIsRecording = isRecording;
    voiceLevel = level;
    if (!isRecording)
    {
      stopVoiceAnimation();
    }
    invalidate();
  }

  private boolean voiceAnimationsEnabled()
  {
    return Build.VERSION.SDK_INT >= 26 ? ValueAnimator.areAnimatorsEnabled()
      : Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0;
  }

  private boolean shouldAnimateVoice()
  {
    return voiceIsRecording && voicePadKey != null && isAttachedToWindow()
      && getWindowVisibility() == VISIBLE && isShown() && voiceAnimationsEnabled();
  }

  private void stopVoiceAnimation()
  {
    if (voiceAnimationTick != null)
    {
      removeCallbacks(voiceAnimationTick);
    }
    voiceFrameQueued = false;
    if (voiceFeedback != null)
    {
      voiceFeedback.reset();
    }
  }

  @Override
  protected void onDetachedFromWindow()
  {
    stopVoiceAnimation();
    super.onDetachedFromWindow();
  }

  @Override
  protected void onWindowVisibilityChanged(final int visibility)
  {
    super.onWindowVisibilityChanged(visibility);
    if (visibility != VISIBLE)
    {
      stopVoiceAnimation();
    }
    else if (voiceIsRecording)
    {
      invalidate();
    }
  }

  public void setHandwritingOverlay(final HandwritingOverlayView handwritingOverlay)
  {
    this.handwritingOverlay = handwritingOverlay;
  }

  public void setHandwritingEnabled(final boolean isEnabled)
  {
    handwritingIsEnabled = isEnabled;
    if (!isEnabled)
    {
      resetDirectHandwritingGesture();
    }
    invalidate();
  }

  public boolean isHandwritingAvailable()
  {
    return handwritingIsEnabled && (handwritingPadKey != null || voicePadKey == null);
  }

  public Key getHandwritingPadKey()
  {
    return handwritingPadKey;
  }

  public float getKeyInsetPx()
  {
    return keyInsetPx;
  }

  /*
    Ink strokes in this view's coordinates, from the pad or (full-screen handwriting) from the overlay.
  */
  void startHandwritingStroke(final float x, final float y)
  {
    keyboardListener.onHandwritingStrokeStart();
    handwritingInk.startStroke(x, y);
    invalidate(); // the pad hint goes
    invalidateHandwritingOverlay();
  }

  void addHandwritingPoint(final float x, final float y)
  {
    handwritingInk.addPoint(x, y);
    invalidateHandwritingOverlay();
  }

  void finishHandwritingStroke()
  {
    handwritingInk.finishStroke();
    invalidateHandwritingOverlay();
    keyboardListener.onHandwritingStrokeEnd();
  }

  private void invalidateHandwritingOverlay()
  {
    if (handwritingOverlay != null)
    {
      handwritingOverlay.invalidate();
    }
  }

  public int getHandwritingPadHeight()
  {
    return (handwritingPadKey == null) ? Math.max(1, getHeight()) : handwritingPadKey.height;
  }

  public int getHandwritingInkColour()
  {
    return (handwritingPadKey == null) ? currentTheme.strokeText : handwritingPadKey.textColour;
  }

  public void setHandwritingHintText(final String hintText)
  {
    handwritingHintText = hintText;
    invalidate();
  }

  public void resetKeyRepeatIntervalMilliseconds()
  {
    keyRepeatIntervalMilliseconds = DEFAULT_KEY_REPEAT_INTERVAL_MILLISECONDS;
  }

  public void setKeyRepeatIntervalMilliseconds(final int milliseconds)
  {
    keyRepeatIntervalMilliseconds = milliseconds;
  }

  @Override
  public void onClick(final View view)
  {
    // Touch logic implemented in onTouchEvent
  }

  @Override
  public void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec)
  {
    final int keyboardWidth;
    final int keyboardHeight;
    if (keyboard != null)
    {
      keyboardWidth = keyboard.getWidth();
      keyboardHeight = keyboard.getHeight();
    }
    else
    {
      keyboardWidth = 0;
      keyboardHeight = 0;
    }
    keyboardRectangle.set(0, 0, keyboardWidth, keyboardHeight);

    final int mainInputPlaneWidth = mainInputPlane.getMeasuredWidth();
    final int mainInputPlaneHeight = mainInputPlane.getMeasuredHeight();
    keyPreviewPlane.updateDimensions(mainInputPlaneWidth, mainInputPlaneHeight, keyboardHeight);

    setMeasuredDimension(keyboardWidth, keyboardHeight);
  }

  @Override
  public void onDraw(@NonNull final Canvas canvas)
  {
    if (keyboard == null)
    {
      return;
    }

    canvas.drawRect(keyboardRectangle, keyboardFillPaint);

    for (final Key key : keys)
    {
      keyRectangle.set(0, 0, key.width, key.height);

      int keyFillColour = key.fillColour;
      if (
        key == activeKey
          ||
        key.valueText.equals(StrokeInputService.SHIFT_KEY_VALUE_TEXT) && (
          shiftPointerId != NONEXISTENT_POINTER_ID
            ||
          shiftMode == SHIFT_PERSISTENT
            ||
          shiftMode == SHIFT_INITIATED
            ||
          shiftMode == SHIFT_HELD
        )
      )
      {
        keyFillColour = toPressedColour(keyFillColour);
      }

      keyFillPaint.setColor(keyFillColour);

      final int keyTextColour;
      if (key == activeKey && swipeModeIsActivated)
      {
        keyTextColour = key.textSwipeColour;
      }
      else
      {
        keyTextColour = key.textColour;
      }
      keyTextPaint.setColor(keyTextColour);
      keyTextPaint.setTextSize(key.textSize);

      final String keyDisplayText =
              (key.valueText.equals(StrokeInputService.ENTER_KEY_VALUE_TEXT))
                ? key.displayText
                : key.shiftAwareDisplayText(shiftMode);

      final float keyTextX = key.width / 2f + key.textOffsetX;
      final float keyTextY = (key.height - keyTextPaint.ascent() - keyTextPaint.descent()) / 2f + key.textOffsetY;

      canvas.translate(key.x, key.y);
      keyRoundRectangle.set(keyRectangle);
      keyRoundRectangle.inset(keyInsetPx, keyInsetPx);
      if (keyShadowIsShown && !key.valueText.equals(StrokeInputService.ENTER_KEY_VALUE_TEXT))
      {
        keyRoundRectangle.offset(0, keyShadowOffsetPx);
        canvas.drawRoundRect(keyRoundRectangle, keyRadiusPx, keyRadiusPx, keyShadowPaint);
        keyRoundRectangle.offset(0, -keyShadowOffsetPx);
      }
      canvas.drawRoundRect(keyRoundRectangle, keyRadiusPx, keyRadiusPx, keyFillPaint);
      if (StrokeInputService.ENTER_KEY_VALUE_TEXT.equals(key.valueText))
      {
        // The requested filled return silhouette stays the same for every editor action.
        // Labels and dispatch remain semantic; neither font glyphs nor action state select a shape.
        final int size = Math.round(Math.min(key.width, key.height) * 0.48f);
        final int left = (key.width - size) / 2;
        final int top = (key.height - size) / 2;
        returnIcon.setBounds(left, top, left + size, top + size);
        returnIcon.setTint(keyTextColour);
        returnIcon.draw(canvas);
      }
      else
      {
        canvas.drawText(keyDisplayText, keyTextX, keyTextY, keyTextPaint);
      }
      if (key.hintText != null)
      {
        // Small corner label, e.g. the classic phone digit of a stroke key
        keyHintPaint.setColor(ColorUtils.setAlphaComponent(keyTextColour, KEY_HINT_ALPHA));
        keyHintPaint.setTextSize(key.textSize * KEY_HINT_TEXT_SIZE_FRACTION);
        final float keyHintMargin = Math.min(key.width, key.height) * KEY_HINT_MARGIN_FRACTION;
        canvas.drawText(key.hintText, keyHintMargin, keyHintMargin - keyHintPaint.ascent(), keyHintPaint);
      }
      canvas.translate(-key.x, -key.y);
    }

    if (handwritingPadKey != null && handwritingInk.isEmpty())
    {
      drawHandwritingHint(canvas, handwritingPadKey);
    }
    if (voicePadKey != null)
    {
      drawVoicePad(canvas, voicePadKey);
    }
  }

  /*
    A microphone in a circle (with a halo following the voice level while recording) and a status line.
  */
  private void drawVoicePad(final Canvas canvas, final Key padKey)
  {
    voiceBounds.set(padKey.x + keyInsetPx, padKey.y + keyInsetPx,
      padKey.x + padKey.width - keyInsetPx, padKey.y + padKey.height - keyInsetPx);
    voiceFeedback.draw(canvas, voiceBounds, currentTheme.accent, currentTheme.onAccent,
      currentTheme.keyText, voiceIsRecording, voiceLevel, voicePadIsPressed,
      voiceStatusText, handwritingHintPaint, microphoneIcon, SystemClock.uptimeMillis(), voiceAnimationsEnabled());
    if (!voiceFrameQueued && shouldAnimateVoice())
    {
      voiceFrameQueued = true;
      postOnAnimationDelayed(voiceAnimationTick, 33);
    }
  }

  /*
    Presses on the voice pad go to the service (it decides between press-to-talk and tap-to-toggle).
  */
  private boolean handleVoicePadTouch(final MotionEvent event)
  {
    final int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN)
    {
      voicePadIsPressed = activeKey == null && voicePadKey.containsPoint((int) event.getX(), (int) event.getY());
      if (voicePadIsPressed)
      {
        performKeyFeedback(voicePadKey);
        invalidate();
        keyboardListener.onVoicePadDown();
      }
      return voicePadIsPressed;
    }
    if (!voicePadIsPressed)
    {
      return false;
    }
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
    {
      voicePadIsPressed = false;
      invalidate();
      if (action == MotionEvent.ACTION_CANCEL)
      {
        keyboardListener.onVoicePadCancel();
      }
      else
      {
        keyboardListener.onVoicePadUp();
      }
    }
    return true;
  }

  /*
    A faint hint on the empty pad (the ink itself is drawn by the overlay).
  */
  private void drawHandwritingHint(final Canvas canvas, final Key padKey)
  {
    handwritingHintPaint.setColor(ColorUtils.setAlphaComponent(padKey.textColour, HANDWRITING_HINT_ALPHA));
    final float hintY = padKey.y + (padKey.height - handwritingHintPaint.ascent() - handwritingHintPaint.descent()) / 2f;
    canvas.drawText(handwritingHintText, padKey.x + padKey.width / 2f, hintY, handwritingHintPaint);
  }

  /*
    Route a touch that starts on the handwriting pad to the ink (one finger at a time).
    Returns whether the event was used by the pad.
  */
  private boolean handleHandwritingTouch(final MotionEvent event)
  {
    final Key padKey = handwritingPadKey;
    final int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN)
    {
      handwritingStrokeIsActive = activeKey == null && padKey.containsPoint((int) event.getX(), (int) event.getY());
      if (handwritingStrokeIsActive)
      {
        startHandwritingStroke(event.getX(), event.getY());
      }
    }
    if (!handwritingStrokeIsActive)
    {
      return false;
    }

    switch (action)
    {
      case MotionEvent.ACTION_MOVE:
        for (int index = 0; index < event.getHistorySize(); index++)
        {
          addHandwritingPoint(event.getHistoricalX(0, index), event.getHistoricalY(0, index));
        }
        addHandwritingPoint(event.getX(0), event.getY(0));
        break;

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (action == MotionEvent.ACTION_UP)
        {
          addHandwritingPoint(event.getX(0), event.getY(0));
        }
        handwritingStrokeIsActive = false;
        finishHandwritingStroke();
        break;
    }
    return true;
  }

  /*
    Ordinary keyboard: keep the normal key press until movement clearly passes touch slop, then
    cancel that uncommitted key and replay the whole path as ink. Keys with an established swipe,
    repeat or long-press action retain their original gesture to avoid regressions.
  */
  private boolean handleDirectHandwritingTouch(final MotionEvent event)
  {
    final int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN)
    {
      final int pointerIndex = event.getActionIndex();
      final Key downKey = getKeyAtPoint((int) event.getX(pointerIndex), (int) event.getY(pointerIndex));
      final boolean hasExtendedPress = downKey != null && (downKey.isRepeatable || downKey.isLongPressable);
      final boolean canStart = HandwritingGesturePolicy.canStartDirectInk(
        handwritingIsEnabled,
        handwritingPadKey != null,
        voicePadKey != null,
        isShiftKey(downKey),
        isSwipeableKey(downKey),
        hasExtendedPress
      );
      if (canStart)
      {
        directHandwritingPointerId = event.getPointerId(pointerIndex);
        directHandwritingDownX = event.getX(pointerIndex);
        directHandwritingDownY = event.getY(pointerIndex);
      }
      else
      {
        resetDirectHandwritingGesture();
      }
      return false; // the normal key path still owns a possible tap
    }

    if (directHandwritingStrokeIsActive)
    {
      final int pointerIndex = event.findPointerIndex(directHandwritingPointerId);
      if (action == MotionEvent.ACTION_CANCEL)
      {
        finishHandwritingStroke();
        resetDirectHandwritingGesture();
        return true;
      }
      if (pointerIndex < 0)
      {
        return true;
      }
      if (action == MotionEvent.ACTION_MOVE)
      {
        addHistoricalHandwritingPoints(event, pointerIndex);
        addHandwritingPoint(event.getX(pointerIndex), event.getY(pointerIndex));
      }
      else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
               && event.getPointerId(event.getActionIndex()) == directHandwritingPointerId)
      {
        addHandwritingPoint(event.getX(pointerIndex), event.getY(pointerIndex));
        finishHandwritingStroke();
        resetDirectHandwritingGesture();
      }
      return true;
    }

    if (directHandwritingPointerId == NONEXISTENT_POINTER_ID)
    {
      return false;
    }
    if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_CANCEL)
    {
      resetDirectHandwritingGesture(); // preserve the existing two-thumb key path
      return false;
    }
    if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1)
    {
      final int pointerIndex = event.findPointerIndex(directHandwritingPointerId);
      if (pointerIndex >= 0 && HandwritingGesturePolicy.hasMovedBeyondSlop(
            directHandwritingDownX,
            directHandwritingDownY,
            event.getX(pointerIndex),
            event.getY(pointerIndex),
            directHandwritingTouchSlopPx
          ))
      {
        cancelActiveKeyForHandwriting();
        directHandwritingStrokeIsActive = true;
        startHandwritingStroke(directHandwritingDownX, directHandwritingDownY);
        addHistoricalHandwritingPoints(event, pointerIndex);
        addHandwritingPoint(event.getX(pointerIndex), event.getY(pointerIndex));
        return true;
      }
    }
    if (action == MotionEvent.ACTION_UP)
    {
      resetDirectHandwritingGesture();
    }
    return false;
  }

  private void addHistoricalHandwritingPoints(final MotionEvent event, final int pointerIndex)
  {
    for (int historyIndex = 0; historyIndex < event.getHistorySize(); historyIndex++)
    {
      addHandwritingPoint(
        event.getHistoricalX(pointerIndex, historyIndex),
        event.getHistoricalY(pointerIndex, historyIndex)
      );
    }
  }

  private void cancelActiveKeyForHandwriting()
  {
    activeKey = null;
    activePointerId = NONEXISTENT_POINTER_ID;
    swipeModeIsActivated = false;
    removeAllExtendedPressHandlerMessages();
    keyPreviewPlane.dismissLatest();
    invalidate();
  }

  private void resetDirectHandwritingGesture()
  {
    directHandwritingPointerId = NONEXISTENT_POINTER_ID;
    directHandwritingStrokeIsActive = false;
  }

  /*
    Lighten a dark colour and darken a light colour.
    Used for key press colour changes.
  */
  public static int toPressedColour(final int colour)
  {
    final float[] colourHSLArray = new float[3];
    ColorUtils.colorToHSL(colour, colourHSLArray);

    float colourLightness = colourHSLArray[2];

    if (colourLightness < COLOUR_LIGHTNESS_CUTOFF)
    {
      colourLightness = (2 * colourLightness + 1) / 3;
    }
    else
    {
      colourLightness = (2 * colourLightness) / 3;
    }

    colourHSLArray[2] = colourLightness;

    return ColorUtils.HSLToColor(colourHSLArray);
  }

  /*
    Handle logic for multiple pointers (e.g. two-thumb typing).
    The correct handling of a pointer moving outside the keyboard
    is ensured by including a 1-pixel gutter at the top of the keyboard
    (so that the pointer must move through a key-free row of pixels).
    The correct handling of merging pointers has not been implemented.
  */
  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent(final MotionEvent event)
  {
    if (handwritingIsEnabled && handwritingPadKey != null && handleHandwritingTouch(event))
    {
      return true;
    }
    if (voicePadKey != null && handleVoicePadTouch(event))
    {
      return true;
    }
    if (handleDirectHandwritingTouch(event))
    {
      return true;
    }

    final int eventPointerCount = event.getPointerCount();

    if (eventPointerCount > 2)
    {
      sendCancelEvent();
      return true;
    }

    touchLogic:
    switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:

        final int downPointerIndex = event.getActionIndex();
        final int downPointerId = event.getPointerId(downPointerIndex);
        final int downPointerX = (int) event.getX(downPointerIndex);
        final int downPointerY = (int) event.getY(downPointerIndex);
        final Key downKey = getKeyAtPoint(downPointerX, downPointerY);

        if (isShiftKey(downKey))
        {
          performKeyFeedback(downKey);
          sendShiftDownEvent(downPointerId);
          break;
        }

        if (activePointerId != NONEXISTENT_POINTER_ID)
        {
          sendUpEvent(activeKey, false);
          keyPreviewPlane.dismissLatest();
        }
        sendDownEvent(downKey, downPointerId, downPointerX);
        break;

      case MotionEvent.ACTION_MOVE:

        for (int index = 0; index < eventPointerCount; index++)
        {
          final int movePointerId = event.getPointerId(index);
          final int movePointerX = (int) event.getX(index);
          final int movePointerY = (int) event.getY(index);
          final Key moveKey = getKeyAtPoint(movePointerX, movePointerY);

          if (movePointerId == activePointerId)
          {
            if (isShiftKey(moveKey) && !isSwipeableKey(activeKey))
            {
              sendShiftMoveToEvent(movePointerId);
              break touchLogic;
            }

            if (moveKey != activeKey || isSwipeableKey(activeKey))
            {
              sendMoveEvent(moveKey, movePointerId, movePointerX);
              break touchLogic;
            }

            break touchLogic;
          }

          if (movePointerId == shiftPointerId)
          {
            if (!isShiftKey(moveKey))
            {
              sendShiftMoveFromEvent(moveKey, movePointerId);
              break touchLogic;
            }
          }
        }
        break;

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:

        final int upPointerIndex = event.getActionIndex();
        final int upPointerId = event.getPointerId(upPointerIndex);
        final int upPointerX = (int) event.getX(upPointerIndex);
        final int upPointerY = (int) event.getY(upPointerIndex);
        final Key upKey = getKeyAtPoint(upPointerX, upPointerY);

        if ((upPointerId == shiftPointerId || isShiftKey(upKey)) && !isSwipeableKey(activeKey))
        {
          sendShiftUpEvent(true);
          break;
        }

        if (upPointerId == activePointerId)
        {
          sendUpEvent(upKey, true);
          break;
        }
        break;

      case MotionEvent.ACTION_CANCEL:
        sendCancelEvent();
        break;
    }

    return true;
  }

  private void sendCancelEvent()
  {
    shiftPointerId = NONEXISTENT_POINTER_ID;
    activeKey = null;
    activePointerId = NONEXISTENT_POINTER_ID;
    keyPreviewPlane.dismissAllImmediately();
    invalidate();
  }

  private void sendDownEvent(final Key key, final int pointerId, final int x)
  {
    if (isSwipeableKey(key))
    {
      pointerDownX = x;
    }
    swipeModeIsActivated = false;

    if (shiftPointerId != NONEXISTENT_POINTER_ID)
    {
      shiftMode = SHIFT_HELD;
      keyPreviewPlane.updateShiftMode(shiftMode);
    }

    activeKey = key;
    activePointerId = pointerId;

    performKeyFeedback(key);
    sendAppropriateExtendedPressHandlerMessage(key);
    keyPreviewPlane.showPreviewAt(key);
    invalidate();
  }

  private void sendMoveEvent(final Key key, final int pointerId, final int x)
  {
    boolean shouldRedrawKeyboard = false;

    if (swipeModeIsActivated)
    {
      if (Math.abs(x - pointerDownX) < SWIPE_ACTIVATION_DISTANCE)
      {
        swipeModeIsActivated = false;
        shouldRedrawKeyboard = true;
      }
    }
    else if (key == activeKey && isSwipeableKey(key))
    {
      if (Math.abs(x - pointerDownX) > SWIPE_ACTIVATION_DISTANCE)
      {
        swipeModeIsActivated = true;
        removeAllExtendedPressHandlerMessages();
        shouldRedrawKeyboard = true;
      }
    }
    else // move is a key change
    {
      activeKey = key;
      removeAllExtendedPressHandlerMessages();
      sendAppropriateExtendedPressHandlerMessage(key);
      resetKeyRepeatIntervalMilliseconds();
      keyPreviewPlane.movePreviewTo(key);
      shouldRedrawKeyboard = true;
    }

    activePointerId = pointerId;

    if (shouldRedrawKeyboard)
    {
      invalidate();
    }
  }

  private void sendUpEvent(final Key key, final boolean shouldRedrawKeyboard)
  {
    if (swipeModeIsActivated)
    {
      keyboardListener.onSwipe(activeKey.valueText);
    }
    else if (key != null)
    {
      if (shiftMode != SHIFT_DISABLED && key.isShiftable)
      {
        keyboardListener.onKey(key.valueTextShifted);
      }
      else
      {
        keyboardListener.onKey(key.valueText);
      }

      if (shiftMode == SHIFT_SINGLE)
      {
        shiftMode = SHIFT_DISABLED;
        keyPreviewPlane.updateShiftMode(shiftMode);
      }
    }

    activeKey = null;
    activePointerId = NONEXISTENT_POINTER_ID;

    removeAllExtendedPressHandlerMessages();
    resetKeyRepeatIntervalMilliseconds();
    if (shouldRedrawKeyboard)
    {
      keyPreviewPlane.dismissLatest();
      invalidate();
    }
  }

  private void sendShiftDownEvent(final int pointerId)
  {
    if (shiftMode == SHIFT_DISABLED)
    {
      shiftMode =
              (activeKey == null)
                ? SHIFT_INITIATED
                : SHIFT_HELD;
      keyPreviewPlane.updateShiftMode(shiftMode);
    }
    shiftPointerId = pointerId;
    invalidate();
  }

  private void sendShiftMoveToEvent(final int pointerId)
  {
    shiftMode = SHIFT_HELD;
    shiftPointerId = pointerId;
    keyPreviewPlane.updateShiftMode(shiftMode);

    activeKey = null;
    activePointerId = NONEXISTENT_POINTER_ID;

    removeAllExtendedPressHandlerMessages();
    keyPreviewPlane.dismissLatest();
    invalidate();
  }

  private void sendShiftMoveFromEvent(final Key key, final int pointerId)
  {
    sendShiftUpEvent(false);

    activeKey = key;
    activePointerId = pointerId;

    removeAllExtendedPressHandlerMessages();
    sendAppropriateExtendedPressHandlerMessage(key);
    resetKeyRepeatIntervalMilliseconds();
    keyPreviewPlane.movePreviewTo(key);
    invalidate();
  }

  private void sendShiftUpEvent(boolean shouldRedrawKeyboard)
  {
    switch (shiftMode)
    {
      case SHIFT_SINGLE:
        shiftMode = SHIFT_PERSISTENT;
        break;

      case SHIFT_INITIATED:
        shiftMode = SHIFT_SINGLE;
        break;

      case SHIFT_PERSISTENT:
      case SHIFT_HELD:
        shiftMode = SHIFT_DISABLED;
        break;
    }
    shiftPointerId = NONEXISTENT_POINTER_ID;
    keyPreviewPlane.updateShiftMode(shiftMode);

    if (shouldRedrawKeyboard)
    {
      invalidate();
    }
  }

  private Key getKeyAtPoint(final int x, final int y)
  {
    for (final Key key : keys)
    {
      if (key.containsPoint(x, y))
      {
        return key;
      }
    }

    return null;
  }

  private boolean isShiftKey(final Key key)
  {
    return key != null && key.valueText.equals(StrokeInputService.SHIFT_KEY_VALUE_TEXT);
  }

  private boolean isSwipeableKey(final Key key)
  {
    return key != null && key.isSwipeable;
  }

  private void sendAppropriateExtendedPressHandlerMessage(final Key key)
  {
    if (key == null)
    {
      return;
    }
    if (key.isRepeatable)
    {
      sendExtendedPressHandlerMessage(MESSAGE_KEY_REPEAT, KEY_REPEAT_START_MILLISECONDS);
    }
    else if (key.isLongPressable)
    {
      sendExtendedPressHandlerMessage(MESSAGE_LONG_PRESS, KEY_LONG_PRESS_MILLISECONDS);
    }
  }

  private void sendExtendedPressHandlerMessage(final int messageWhat, final long delayMilliseconds)
  {
    final Message message = extendedPressHandler.obtainMessage(messageWhat);
    extendedPressHandler.sendMessageDelayed(message, delayMilliseconds);
  }

  private void removeAllExtendedPressHandlerMessages()
  {
    extendedPressHandler.removeCallbacksAndMessages(null);
  }
}
