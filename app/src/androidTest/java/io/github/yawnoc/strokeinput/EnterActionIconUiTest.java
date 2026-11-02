/* Copyright 2026 Haoke Tools. Licensed under GPL-3.0-only; see LICENSE. */
package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.os.SystemClock;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Exercises the real KeyboardView branch and the service's cross-layout update, rather than
 * drawing a standalone return drawable. Uses synthetic state, an isolated preferences file and
 * a recording InputConnection; it never enables an IME, changes saved user settings or records audio.
 * Saved PNG evidence has no labels/text: rows PAPER/SKY/NIGHT, columns the EnterKeyIcon enum order.
 */
@RunWith(AndroidJUnit4.class)
public class EnterActionIconUiTest
{
  private static final String TEST_PREFERENCES = "instrumentationEnterActionIcons";
  private static final int[] LAYOUTS = {
    R.xml.keyboard_strokes, R.xml.keyboard_strokes_large,
    R.xml.keyboard_strokes_symbols_1, R.xml.keyboard_strokes_symbols_2,
    R.xml.keyboard_strokes_symbols_3, R.xml.keyboard_strokes_symbols_common,
    R.xml.keyboard_strokes_symbols_shapes, R.xml.keyboard_strokes_symbols_math,
    R.xml.keyboard_qwerty, R.xml.keyboard_qwerty_symbols,
    R.xml.keyboard_handwriting, R.xml.keyboard_voice
  };
  private static final String[] THEMES = {KeyboardTheme.PAPER, KeyboardTheme.SKY, KeyboardTheme.NIGHT};
  private static final int[] ACTIONS = {
    EditorInfo.IME_ACTION_NONE, EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_GO,
    EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS, EditorInfo.IME_ACTION_SEARCH,
    EditorInfo.IME_ACTION_SEND
  };
  private static final EnterKeyIcon[] ICONS = {
    EnterKeyIcon.RETURN, EnterKeyIcon.DONE, EnterKeyIcon.GO, EnterKeyIcon.NEXT,
    EnterKeyIcon.PREVIOUS, EnterKeyIcon.SEARCH, EnterKeyIcon.SEND
  };
  private Context context;

  @Before
  public void isolatedSettings()
  {
    final Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context = new ContextWrapper(target)
    {
      @Override public Context getApplicationContext() { return this; }
      @Override public SharedPreferences getSharedPreferences(final String name, final int mode)
      {
        return target.getSharedPreferences(TEST_PREFERENCES, mode);
      }
    };
    assertTrue(context.getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit());
  }

  @After
  public void clearOnlySyntheticSettings()
  {
    assertTrue(context.getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit());
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

  private static void set(final Object target, final Class<?> declaringClass,
      final String name, final Object value) throws Exception
  {
    final Field field = declaringClass.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Key enter(final Keyboard keyboard)
  {
    Key result = null;
    for (final Key key : keyboard.getKeys())
    {
      if (StrokeInputService.ENTER_KEY_VALUE_TEXT.equals(key.valueText))
      {
        assertEquals("one ENTER per layout", null, result);
        result = key;
      }
    }
    assertNotNull("layout must have ENTER", result);
    assertFalse("ENTER never uses the font-based key preview", result.isPreviewable);
    return result;
  }

  private KeyboardView view(final Keyboard keyboard, final KeyboardTheme theme)
  {
    final KeyboardView result = new KeyboardView(context, null);
    result.setKeyboardListener(new KeyboardView.KeyboardListener()
    {
      public void onKey(String value) { }
      public void onLongPress(String value) { }
      public void onSwipe(String value) { }
      public void saveKeyboard(Keyboard selected) { }
      public void onHandwritingStrokeStart() { }
      public void onHandwritingStrokeEnd() { }
      public void onVoicePadDown() { }
      public void onVoicePadUp() { }
      public void onVoicePadCancel() { }
    });
    result.setKeyPreviewPlane(new KeyPreviewPlane(context));
    keyboard.applyTheme(theme);
    result.setKeyboardTheme(theme);
    result.setKeyboard(keyboard);
    return result;
  }

  private Bitmap actual(final KeyboardView view, final Key key, final boolean pressed) throws Exception
  {
    set(view, KeyboardView.class, "activeKey", pressed ? key : null);
    final Bitmap result = Bitmap.createBitmap(key.width, key.height, Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(result);
    canvas.translate(-key.x, -key.y);
    view.onDraw(canvas);
    return result;
  }

  private Bitmap expected(final Key key, final KeyboardTheme theme, final boolean pressed)
  {
    final Bitmap result = Bitmap.createBitmap(key.width, key.height, Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(result);
    final float density = context.getResources().getDisplayMetrics().density;
    final float inset = theme.keyGapDp * density / 2;
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    fill.setColor(pressed ? KeyboardView.toPressedColour(key.fillColour) : key.fillColour);
    final RectF bounds = new RectF(0, 0, key.width, key.height);
    bounds.inset(inset, inset);
    canvas.drawRoundRect(bounds, theme.keyRadiusDp * density, theme.keyRadiusDp * density, fill);
    final Drawable icon = AppCompatResources.getDrawable(context, R.drawable.icon_keyboard_return);
    assertNotNull(icon);
    final Drawable unique = icon.mutate();
    final int size = Math.round(Math.min(key.width, key.height) * .48f);
    final int left = (key.width - size) / 2;
    final int top = (key.height - size) / 2;
    unique.setBounds(left, top, left + size, top + size);
    unique.setTint(key.textColour);
    unique.draw(canvas);
    return result;
  }

  private static void assertIconPixels(final String label, final Bitmap expected, final Bitmap actual,
      final Key key)
  {
    final int size = Math.round(Math.min(key.width, key.height) * .48f);
    final int left = (key.width - size) / 2;
    final int top = (key.height - size) / 2;
    // Read the full ROI once. Per-pixel JNI calls and eager success messages caused
    // allocation/GC pressure; every pixel and the independent silhouette remain checked.
    final int[] expectedPixels = new int[size * size];
    final int[] actualPixels = new int[size * size];
    expected.getPixels(expectedPixels, 0, size, left, top, size, size);
    actual.getPixels(actualPixels, 0, size, left, top, size, size);
    int fullyTintedPixels = 0;
    for (int index = 0; index < actualPixels.length; index++)
    {
      if (expectedPixels[index] != actualPixels[index])
      {
        assertEquals(label + " pixel " + (left + index % size) + "," + (top + index / size),
            expectedPixels[index], actualPixels[index]);
      }
      if (actualPixels[index] == key.textColour) fullyTintedPixels++;
    }
    assertTrue(label + ": visible icon with the theme's enter text colour", fullyTintedPixels > 5);
  }

  /** Independent silhouette checks: the reference is a filled left arrow, with a right upright. */
  private static void assertFilledReturnSilhouette(final Bitmap actual, final Key key,
      final boolean pressed)
  {
    final int size = Math.round(Math.min(key.width, key.height) * .48f);
    final int left = (key.width - size) / 2;
    final int top = (key.height - size) / 2;
    // Interior samples deliberately avoid antialiased boundaries. They detect a hollow/outlined
    // arrow, a right-facing arrow, a checkmark and the previous Next arrow-to-bar independently
    // of the vector resource used by expected().
    final float[][] filled = {{6, 12}, {6, 17}, {3.5f, 14.5f}, {10, 14}, {17, 14}, {21, 7}, {21, 11}};
    final float[][] empty = {{3, 7}, {12, 7}, {12, 18}, {20, 18}};
    for (final float[] point : filled)
    {
      assertEquals("filled left arrow / right upright at " + point[0] + "," + point[1],
          key.textColour, actual.getPixel(left + (int) (size * point[0] / 24),
              top + (int) (size * point[1] / 24)));
    }
    final int background = pressed ? KeyboardView.toPressedColour(key.fillColour) : key.fillColour;
    for (final float[] point : empty)
    {
      assertEquals("clear negative space at " + point[0] + "," + point[1],
          background, actual.getPixel(left + (int) (size * point[0] / 24),
              top + (int) (size * point[1] / 24)));
    }
  }

  @Test
  public void allLayoutsAndThemes_renderOneFilledReturn_forEveryActionAndPressedState() throws Exception
  {
    final long started = SystemClock.elapsedRealtime();
    final int[] checks = {0};
    for (final String themeName : THEMES)
    {
      final KeyboardTheme theme = KeyboardTheme.forName(themeName);
      for (final int layout : LAYOUTS)
      {
        // Yield between layouts so the real app main loop is never occupied by all 504 draws.
        onMain(() ->
        {
          final Keyboard keyboard = new Keyboard(context, layout);
          final Key key = enter(keyboard);
          final KeyboardView view = view(keyboard, theme);
          final Bitmap[] returnState = new Bitmap[2];
          try
          {
            for (final EnterKeyIcon icon : ICONS)
            {
              key.enterIcon = icon;
              key.displayText = "";
              for (final boolean pressed : new boolean[]{false, true})
              {
                final Bitmap actual = actual(view, key, pressed);
                final Bitmap expected = expected(key, theme, pressed);
                try
                {
                  assertIconPixels(themeName + "/" + layout + "/" + icon + "/pressed=" + pressed,
                      expected, actual, key);
                  assertFilledReturnSilhouette(actual, key, pressed);
                  final int state = pressed ? 1 : 0;
                  if (returnState[state] == null)
                    returnState[state] = actual.copy(Bitmap.Config.ARGB_8888, false);
                  else
                    assertTrue("action changes must retain the exact same return key pixels",
                        returnState[state].sameAs(actual));
                  checks[0]++;
                }
                finally
                {
                  expected.recycle();
                  actual.recycle();
                }
              }
            }
          }
          finally
          {
            for (final Bitmap baseline : returnState) if (baseline != null) baseline.recycle();
          }
        });
        Log.i("EnterActionIconUiTest", "Rendered " + checks[0] + "/504; theme=" + themeName
            + ", layout=" + layout + ", elapsedMs=" + (SystemClock.elapsedRealtime() - started));
      }
    }
    assertEquals(504, checks[0]);
    Log.i("EnterActionIconUiTest", checks[0] + " real KeyboardView action/theme/layout/pressed draws PASS");
  }

  @Test
  public void actionSelection_handlesNoEnterAction_unknownActions_andEditorFlags()
  {
    final Set<Integer> distinctLabels = new LinkedHashSet<>();
    for (int index = 0; index < ACTIONS.length; index++)
    {
      final int action = ACTIONS[index];
      assertEquals(ICONS[index], EnterKeyIcon.forEditorAction(action, true));
      assertEquals(ICONS[index], EnterKeyIcon.forEditorAction(action | EditorInfo.IME_FLAG_NO_FULLSCREEN, true));
      assertEquals(EnterKeyIcon.RETURN, EnterKeyIcon.forEditorAction(action, false));
      assertEquals(EnterKeyIcon.RETURN,
          EnterKeyIcon.forEditorAction(action | EditorInfo.IME_FLAG_NO_ENTER_ACTION, false));
      distinctLabels.add(ICONS[index].displayTextResource);
    }
    assertEquals("all seven actions retain their semantic labels", 7, distinctLabels.size());
    assertEquals(EnterKeyIcon.RETURN, EnterKeyIcon.forEditorAction(EditorInfo.IME_ACTION_UNSPECIFIED, true));
    assertEquals(EnterKeyIcon.RETURN, EnterKeyIcon.forEditorAction(0xff, true));
  }

  private static final class SyntheticContainer extends InputContainer
  {
    int redraws;
    private final HandwritingInk ink = new HandwritingInk();
    SyntheticContainer(final Context context) { super(context, null); }
    @Override public void redrawKeyboard() { redraws++; }
    @Override public HandwritingInk getHandwritingInk() { return ink; }
  }

  private static final class SyntheticService extends StrokeInputService
  {
    private final Context context;
    SyntheticService(final Context context) { this.context = context; }
    @Override public Resources getResources() { return context.getResources(); }
  }

  @Test
  public void serviceActionTransitions_refreshEveryLayout_andNoEnterActionRestoresReturn() throws Exception
  {
    onMain(() ->
    {
      final SyntheticService service = new SyntheticService(context);
      final SyntheticContainer container = new SyntheticContainer(context);
      final Set<Keyboard> keyboards = new LinkedHashSet<>();
      for (final int layout : LAYOUTS) keyboards.add(new Keyboard(context, layout));
      set(service, StrokeInputService.class, "inputContainer", container);
      set(service, StrokeInputService.class, "keyboards", keyboards);
      final Method update = StrokeInputService.class.getDeclaredMethod("setEnterKeyDisplayText");
      update.setAccessible(true);
      int updates = 0;
      for (int index = 0; index < ACTIONS.length; index++)
      {
        for (final boolean hasAction : new boolean[]{true, false})
        {
          set(service, StrokeInputService.class, "inputActionsBits", ACTIONS[index]);
          set(service, StrokeInputService.class, "enterKeyHasAction", hasAction);
          update.invoke(service);
          final EnterKeyIcon expected = hasAction ? ICONS[index] : EnterKeyIcon.RETURN;
          for (final Keyboard keyboard : keyboards)
          {
            final Key key = enter(keyboard);
            assertEquals(expected, key.enterIcon);
            assertEquals(context.getString(expected.displayTextResource), key.displayText);
          }
          assertEquals(++updates, container.redraws);
        }
      }
      Log.i("EnterActionIconUiTest", "14 service state transitions refreshed all 12 layouts PASS");
    });
  }

  private static final class RecordingConnection extends BaseInputConnection
  {
    final List<Integer> actions = new ArrayList<>();
    final List<String> committed = new ArrayList<>();
    RecordingConnection(final Context context) { super(new View(context), true); }
    @Override public boolean performEditorAction(final int action) { actions.add(action); return true; }
    @Override public boolean commitText(final CharSequence text, final int cursor)
    {
      committed.add(text.toString());
      return true;
    }
  }

  @Test
  public void visualRepair_preservesTheRealEditorAction_andPlainNewlineDispatch() throws Exception
  {
    onMain(() ->
    {
      final SyntheticService service = new SyntheticService(context);
      final Method dispatch = StrokeInputService.class.getDeclaredMethod("effectEnterKey", InputConnection.class);
      dispatch.setAccessible(true);
      for (final int action : ACTIONS)
      {
        set(service, StrokeInputService.class, "inputActionsBits", action);
        set(service, StrokeInputService.class, "enterKeyHasAction", true);
        final RecordingConnection withAction = new RecordingConnection(context);
        dispatch.invoke(service, withAction);
        assertEquals(List.of(action), withAction.actions);
        assertTrue(withAction.committed.isEmpty());

        set(service, StrokeInputService.class, "enterKeyHasAction", false);
        final RecordingConnection noAction = new RecordingConnection(context);
        dispatch.invoke(service, noAction);
        assertTrue(noAction.actions.isEmpty());
        assertEquals(List.of("\n"), noAction.committed);
      }
    });
  }

  @Test
  public void handwritingHeightChanges_keepActionIcons_andProduceNativeRendererEvidence() throws Exception
  {
    onMain(() ->
    {
      Bitmap grid = null;
      Canvas gridCanvas = null;
      for (int row = 0; row < THEMES.length; row++)
      {
        final KeyboardTheme theme = KeyboardTheme.forName(THEMES[row]);
        final Keyboard keyboard = new Keyboard(context, R.xml.keyboard_handwriting);
        final KeyboardView view = view(keyboard, theme);
        final Key key = enter(keyboard);
        if (grid == null)
        {
          grid = Bitmap.createBitmap(key.width * ICONS.length, key.height * THEMES.length,
              Bitmap.Config.ARGB_8888);
          gridCanvas = new Canvas(grid);
        }
        for (final int dp : new int[]{180, 400})
        {
          TypingSettings.saveHandwritingHeightDp(context, dp);
          keyboard.adjustHalfScreenHandwritingHeight(Math.round(
              1_000 * context.getResources().getDisplayMetrics().density));
          for (int column = 0; column < ICONS.length; column++)
          {
            key.enterIcon = ICONS[column];
            key.displayText = context.getString(ICONS[column].displayTextResource);
            final Bitmap actual = actual(view, key, false);
            final Bitmap expected = expected(key, theme, false);
            assertIconPixels(THEMES[row] + "/handwriting=" + dp + "/" + ICONS[column], expected, actual, key);
            if (dp == 400) gridCanvas.drawBitmap(actual, column * key.width, row * key.height, null);
            actual.recycle();
            expected.recycle();
          }
        }
      }
      final File file = new File(context.getCacheDir(), "enter-action-native-renderer-grid.png");
      try (FileOutputStream output = new FileOutputStream(file))
      {
        assertTrue(grid.compress(Bitmap.CompressFormat.PNG, 100, output));
      }
      grid.recycle();
      Log.i("EnterActionIconUiTest", "Synthetic native renderer evidence, not an app screenshot: " + file);
    });
  }
}
