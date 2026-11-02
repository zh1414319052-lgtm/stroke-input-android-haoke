/*
  Copyright 2026 Haoke Tools (浩科工具站), modified build of Stroke Input Method by Conway
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class KeyboardDirectHandwritingTest
{
  private static final String STROKE_1 = "STROKE_1";

  private interface MainCheck { void run() throws Exception; }

  private static void onMain(final MainCheck check) throws Exception
  {
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
    {
      try { check.run(); }
      catch (Throwable thrown) { failure.set(thrown); }
    });
    if (failure.get() instanceof Exception exception) throw exception;
    if (failure.get() != null) throw new AssertionError(failure.get());
  }

  private static final class Events implements KeyboardView.KeyboardListener
  {
    final List<String> keys = new ArrayList<>();
    final List<String> swipes = new ArrayList<>();
    int strokeStarts;
    int strokeEnds;

    public void onKey(final String value) { keys.add(value); }
    public void onLongPress(final String value) { }
    public void onSwipe(final String value) { swipes.add(value); }
    public void saveKeyboard(final Keyboard keyboard) { }
    public void onHandwritingStrokeStart() { strokeStarts++; }
    public void onHandwritingStrokeEnd() { strokeEnds++; }
    public void onVoicePadDown() { }
    public void onVoicePadUp() { }
    public void onVoicePadCancel() { }
  }

  private static KeyboardView keyboardView(final Context context, final Events events)
  {
    final InputContainer container = (InputContainer) LayoutInflater.from(context)
      .inflate(R.layout.input_container, null);
    container.initialisePopupRecess();
    container.initialiseStrokeSequenceBar(context);
    container.initialiseCandidatesView(new CandidatesViewAdapter.CandidateListener()
    {
      public void onCandidate(final String candidate) { }
      public void onCandidateLongPress(final String candidate) { }
    });
    container.initialiseToolbar(context, new InputContainer.ToolbarListener()
    {
      public void onShowQuickPhrases() { }
      public void onShowHandwriting() { }
      public void onShowVoice() { }
      public void onHideKeyboard() { }
    });
    final Keyboard keyboard = new Keyboard(context, R.xml.keyboard_strokes);
    container.initialiseKeyboardView(events, keyboard);
    container.initialiseBottomSpacer();
    container.setHandwritingEnabled(true);
    container.measure(
      View.MeasureSpec.makeMeasureSpec(keyboard.getWidth(), View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.AT_MOST)
    );
    container.layout(0, 0, container.getMeasuredWidth(), container.getMeasuredHeight());
    return container.findViewById(R.id.keyboard_view);
  }

  private static Key key(final KeyboardView view, final String value)
  {
    for (final Key key : view.getKeyboard().getKeys())
    {
      if (value.equals(key.valueText)) return key;
    }
    throw new AssertionError("Missing key " + value);
  }

  private static void touch(final KeyboardView view, final long downTime, final long time,
                            final int action, final float x, final float y)
  {
    final MotionEvent event = MotionEvent.obtain(downTime, time, action, x, y, 0);
    try { view.onTouchEvent(event); }
    finally { event.recycle(); }
  }

  @Test
  public void tapTypes_slideWrites_andSpaceSwipeKeepsSwitchGesture() throws Exception
  {
    onMain(() ->
    {
      final Context context = new ContextThemeWrapper(
        InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_StrokeInput
      );

      Events events = new Events();
      KeyboardView view = keyboardView(context, events);
      Key stroke = key(view, STROKE_1);
      float x = stroke.x + stroke.width / 2f;
      float y = stroke.y + stroke.height / 2f;
      touch(view, 10, 10, MotionEvent.ACTION_DOWN, x, y);
      touch(view, 10, 40, MotionEvent.ACTION_UP, x + 1, y + 1);
      assertEquals(List.of(STROKE_1), events.keys);
      assertEquals(0, events.strokeStarts);

      events = new Events();
      view = keyboardView(context, events);
      stroke = key(view, STROKE_1);
      x = stroke.x + stroke.width / 2f;
      y = stroke.y + stroke.height / 2f;
      touch(view, 100, 100, MotionEvent.ACTION_DOWN, x, y);
      touch(view, 100, 130, MotionEvent.ACTION_MOVE, x + 120, y + 80);
      touch(view, 100, 160, MotionEvent.ACTION_UP, x + 160, y + 100);
      assertEquals(List.of(), events.keys);
      assertEquals(1, events.strokeStarts);
      assertEquals(1, events.strokeEnds);

      events = new Events();
      view = keyboardView(context, events);
      final Key space = key(view, StrokeInputService.SPACE_BAR_VALUE_TEXT);
      x = space.x + space.width / 2f;
      y = space.y + space.height / 2f;
      touch(view, 200, 200, MotionEvent.ACTION_DOWN, x, y);
      touch(view, 200, 230, MotionEvent.ACTION_MOVE, x + 120, y);
      touch(view, 200, 260, MotionEvent.ACTION_UP, x + 120, y);
      assertEquals(List.of(StrokeInputService.SPACE_BAR_VALUE_TEXT), events.swipes);
      assertEquals(0, events.strokeStarts);
    });
  }
}
