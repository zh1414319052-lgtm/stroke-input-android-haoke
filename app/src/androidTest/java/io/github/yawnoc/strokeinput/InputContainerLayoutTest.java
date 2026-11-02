/* Copyright 2026 Haoke Tools. Licensed under GPL-3.0-only; see LICENSE. */
package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Real inflated hierarchy measurement under the physical phone's observed parent constraint.
 * The 132px footer simulates the already-resolved system navigation reservation; this checks
 * that visible handwriting overlays cannot consume it during FrameLayout/LinearLayout measure.
 * This is a layout regression test, not proof of system navigation placement on a live device.
 */
@RunWith(AndroidJUnit4.class)
public class InputContainerLayoutTest
{
  private static final String PREFERENCES = "instrumentationInputContainerLayout";
  private static final int WIDTH = 1080;
  private static final int PARENT_HEIGHT = 2256;
  private static final int NAVIGATION_HEIGHT = 132;
  private Context context;

  @Before
  public void isolatedPreferences()
  {
    final Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context = new ContextThemeWrapper(target, R.style.Theme_StrokeInput)
    {
      @Override public Context getApplicationContext() { return this; }
      @Override public SharedPreferences getSharedPreferences(final String name, final int mode)
      {
        return target.getSharedPreferences(PREFERENCES, mode);
      }
    };
    assertTrue(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit());
  }

  @After
  public void clearOnlySyntheticPreferences()
  {
    assertTrue(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit());
  }

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

  private InputContainer container()
  {
    final InputContainer container = (InputContainer) LayoutInflater.from(context)
        .inflate(R.layout.input_container, null);
    container.initialisePopupRecess();
    container.initialiseStrokeSequenceBar(context);
    container.initialiseCandidatesView(new CandidatesViewAdapter.CandidateListener()
    {
      public void onCandidate(String candidate) { }
      public void onCandidateLongPress(String candidate) { }
    });
    container.initialiseToolbar(context, new InputContainer.ToolbarListener()
    {
      public void onShowQuickPhrases() { }
      public void onShowHandwriting() { }
      public void onShowVoice() { }
      public void onHideKeyboard() { }
    });
    container.initialiseKeyboardView(new KeyboardView.KeyboardListener()
    {
      public void onKey(String value) { }
      public void onLongPress(String value) { }
      public void onSwipe(String value) { }
      public void saveKeyboard(Keyboard keyboard) { }
      public void onHandwritingStrokeStart() { }
      public void onHandwritingStrokeEnd() { }
      public void onVoicePadDown() { }
      public void onVoicePadUp() { }
      public void onVoicePadCancel() { }
    }, new Keyboard(context, R.xml.keyboard_strokes));
    container.setHandwritingEnabled(true);
    container.initialiseBottomSpacer();
    container.setPopupRecessLayout(false);
    container.setKeyboardTheme(KeyboardTheme.forName(KeyboardTheme.PAPER));
    final View footer = container.findViewById(R.id.bottom_spacer);
    final ViewGroup.LayoutParams footerParameters = footer.getLayoutParams();
    footerParameters.height = NAVIGATION_HEIGHT;
    footer.setLayoutParams(footerParameters);
    return container;
  }

  private static Rect inContainer(final InputContainer container, final View child)
  {
    final Rect bounds = new Rect(0, 0, child.getWidth(), child.getHeight());
    container.offsetDescendantRectToMyCoords(child, bounds);
    return bounds;
  }

  private static void measureAndCheckFooter(final InputContainer container, final String mode)
  {
    container.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(PARENT_HEIGHT, View.MeasureSpec.AT_MOST));
    container.layout(0, 0, container.getMeasuredWidth(), container.getMeasuredHeight());
    final View footer = container.findViewById(R.id.bottom_spacer);
    final KeyboardView keyboard = container.findViewById(R.id.keyboard_view);
    final View frame = container.findViewById(R.id.main_frame);
    assertEquals(mode + ": navigation space must survive measurement", NAVIGATION_HEIGHT,
        footer.getMeasuredHeight());
    assertEquals(mode + ": navigation space stays at panel bottom", container.getHeight(),
        footer.getBottom());
    assertTrue(mode + ": measured panel fits parent", container.getHeight() <= PARENT_HEIGHT);
    assertEquals(mode + ": input block ends where navigation starts", footer.getTop(), frame.getBottom());
    final Rect keyboardBounds = inContainer(container, keyboard);
    assertTrue(mode + ": keyboard never enters navigation region", keyboardBounds.bottom <= footer.getTop());
    for (final Key key : container.getKeyboard().getKeys())
    {
      if (StrokeInputService.ENTER_KEY_VALUE_TEXT.equals(key.valueText))
      {
        assertTrue(mode + ": Enter touch area stays above the navigation region",
            keyboardBounds.top + key.y + key.height <= footer.getTop());
      }
    }
    final View overlay = container.findViewById(R.id.handwriting_overlay);
    if (overlay.getVisibility() == View.VISIBLE)
    {
      assertEquals(mode + ": overlay fills only main frame", frame.getHeight(), overlay.getHeight());
      assertEquals(mode + ": overlay uses the frame's width", frame.getWidth(), overlay.getWidth());
      assertTrue(mode + ": overlay cannot receive navigation touches",
          inContainer(container, overlay).bottom <= footer.getTop());
    }
  }

  @Test
  public void modeTransitions_preserveNavigationSpace_andConstrainHandwritingOverlay() throws Exception
  {
    onMain(() ->
    {
      final InputContainer container = container();
      measureAndCheckFooter(container, "ordinary strokes");
      assertFalse(container.isHandwritingFullScreen());
      assertEquals(View.VISIBLE, container.findViewById(R.id.handwriting_overlay).getVisibility());

      container.setHandwritingFullScreenPreferred(true);
      measureAndCheckFooter(container, "direct full-screen handwriting");
      assertTrue(container.isHandwritingFullScreen());
      container.setHandwritingFullScreenPreferred(false);
      measureAndCheckFooter(container, "direct keyboard-area handwriting");
      assertFalse(container.isHandwritingFullScreen());

      int smallerPad = 0;
      for (final int dp : new int[]{180, 400})
      {
        TypingSettings.saveHandwritingHeightDp(context, dp);
        container.setKeyboard(new Keyboard(context, R.xml.keyboard_handwriting));
        measureAndCheckFooter(container, "half handwriting " + dp);
        assertFalse(container.isHandwritingFullScreen());
        assertTrue("half handwriting leaves application content visible",
            container.getHeight() < PARENT_HEIGHT);
        assertTrue("half handwriting follows the panel limit",
            container.getHeight() <= HandwritingHeight.panelLimitPx(
                context.getResources().getDisplayMetrics().heightPixels, PARENT_HEIGHT));
        assertTrue("half handwriting has usable ink space", container.getHandwritingPadHeight() > 0);
        if (dp == 180) smallerPad = container.getHandwritingPadHeight();
        else assertTrue("increasing the saved height increases the actual ink area",
            container.getHandwritingPadHeight() > smallerPad);
      }

      container.setHandwritingFullScreenPreferred(true);
      measureAndCheckFooter(container, "full handwriting");
      assertTrue(container.isHandwritingFullScreen());
      assertEquals("full handwriting fills the offered height", PARENT_HEIGHT, container.getHeight());
      assertEquals("full handwriting reserves its navigation footer", PARENT_HEIGHT - NAVIGATION_HEIGHT,
          container.findViewById(R.id.main_frame).getHeight());

      container.setHandwritingFullScreenPreferred(false);
      measureAndCheckFooter(container, "return to half handwriting");
      assertTrue(container.getHeight() < PARENT_HEIGHT);

      container.setKeyboard(new Keyboard(context, R.xml.keyboard_voice));
      measureAndCheckFooter(container, "voice");
      assertEquals(View.GONE, container.findViewById(R.id.handwriting_overlay).getVisibility());
      assertFalse(container.isHandwritingFullScreen());

      container.setKeyboard(new Keyboard(context, R.xml.keyboard_strokes));
      measureAndCheckFooter(container, "return to ordinary strokes");
      assertEquals(View.VISIBLE, container.findViewById(R.id.handwriting_overlay).getVisibility());
      assertFalse(container.isHandwritingFullScreen());

      container.setHandwritingEnabled(false);
      measureAndCheckFooter(container, "handwriting disabled");
      assertEquals(View.GONE, container.findViewById(R.id.handwriting_overlay).getVisibility());
      assertFalse(container.isHandwritingFullScreen());
    });
  }
}
