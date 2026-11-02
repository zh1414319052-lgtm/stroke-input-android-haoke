/* Copyright 2026 Haoke Tools. Licensed under GPL-3.0-only; see LICENSE. */
package io.github.yawnoc.strokeinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Synthetic backend only: never publishes a notice or needs microphone/user input data. */
@RunWith(AndroidJUnit4.class)
public class UpdateUiLifecycleTest
{
  /**
   * Own only this MainActivity instance. ActivityScenario's external Empty/Bootstrap
   * activities trigger OEM cross-app launch prompts, which are unrelated to these
   * assertions. Direct launch/finish plus real Home tests the actual app lifecycle.
   */
  private static final class SettingsSession implements AutoCloseable
  {
    private final Application application = (Application) InstrumentationRegistry
        .getInstrumentation().getTargetContext().getApplicationContext();
    private final AtomicReference<MainActivity> owner = new AtomicReference<>();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final CountDownLatch destroyed = new CountDownLatch(1);
    private final Application.ActivityLifecycleCallbacks callbacks = new Application.ActivityLifecycleCallbacks()
    {
      public void onActivityCreated(Activity a, Bundle state)
      {
        if (a instanceof MainActivity main) owner.compareAndSet(null, main);
      }
      public void onActivityStarted(Activity a) { }
      public void onActivityResumed(Activity a) { }
      public void onActivityPaused(Activity a) { }
      public void onActivityStopped(Activity a) { if (a == owner.get()) stopped.countDown(); }
      public void onActivitySaveInstanceState(Activity a, Bundle state) { }
      public void onActivityDestroyed(Activity a) { if (a == owner.get()) destroyed.countDown(); }
    };

    SettingsSession()
    {
      application.registerActivityLifecycleCallbacks(callbacks);
      try
      {
        final Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final MainActivity launched = (MainActivity) InstrumentationRegistry.getInstrumentation()
            .startActivitySync(new Intent(target, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertEquals("The session must own the launched activity", launched, owner.get());
      }
      catch (RuntimeException | Error failure)
      {
        application.unregisterActivityLifecycleCallbacks(callbacks);
        throw failure;
      }
    }

    void onActivity(final Consumer<MainActivity> action)
    {
      final AtomicReference<Throwable> failure = new AtomicReference<>();
      InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
      {
        try { action.accept(owner.get()); }
        catch (Throwable thrown) { failure.set(thrown); }
      });
      if (failure.get() instanceof Error error) throw error;
      if (failure.get() instanceof RuntimeException error) throw error;
      if (failure.get() != null) throw new AssertionError(failure.get());
    }

    void goHome() throws Exception
    {
      try (ParcelFileDescriptor.AutoCloseInputStream output = new ParcelFileDescriptor.AutoCloseInputStream(
          InstrumentationRegistry.getInstrumentation().getUiAutomation()
              .executeShellCommand("input keyevent KEYCODE_HOME")))
      {
        while (output.read() != -1) { /* drain this bounded command, without changing settings */ }
      }
      assertTrue("A real Home action must stop the owned activity", stopped.await(5, TimeUnit.SECONDS));
    }

    public void close() throws Exception
    {
      try
      {
        if (destroyed.getCount() != 0) onActivity(Activity::finish);
        assertTrue("The owned activity must be destroyed", destroyed.await(5, TimeUnit.SECONDS));
      }
      finally { application.unregisterActivityLifecycleCallbacks(callbacks); }
    }
  }

  private static final class Backend implements UpdateUiController.Backend
  {
    final AtomicInteger checks = new AtomicInteger();
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    volatile boolean allowed;
    volatile boolean disposed;

    public void setAllowed(final boolean value) { allowed = value; }
    public HaokeUpdateClient.Result check(final boolean manual)
    {
      checks.incrementAndGet();
      entered.countDown();
      try { release.await(5, TimeUnit.SECONDS); }
      catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
      // Deliberately return success even after cancellation: the UI must discard late results.
      return HaokeUpdateClient.Result.kind("current");
    }
    public boolean present(final Runnable show, final boolean manual) { show.run(); return true; }
    public boolean later() { return true; }
    public boolean ignore() { return true; }
    public String pageUrlFromUserGesture() { return null; }
    public void dispose() { disposed = true; allowed = false; release.countDown(); }
  }

  private static void replaceController(final MainActivity activity, final Backend backend)
  {
    try
    {
      final Field field = MainActivity.class.getDeclaredField("updateUi");
      field.setAccessible(true);
      ((UpdateUiController) field.get(activity)).close();
      final UpdateUiController replacement = new UpdateUiController(activity, backend);
      field.set(activity, replacement);
      assertEquals("ScrollView must retain the post-inflation focus policy",
          ViewGroup.FOCUS_BEFORE_DESCENDANTS,
          ((ViewGroup) activity.findViewById(R.id.main_activity_content)).getDescendantFocusability());
      activity.findViewById(R.id.main_activity_content).requestFocus();
      assertEquals("Requesting the settings container must not focus its EditText descendant",
          activity.findViewById(R.id.main_activity_content), activity.getCurrentFocus());
      replacement.onResume();
    }
    catch (ReflectiveOperationException failure)
    {
      throw new AssertionError(failure);
    }
  }

  private static void prepare(final SettingsSession scenario, final Backend backend)
  {
    // MuMu can rotate from a landscape launcher into a portrait activity after RESUMED.
    // Pin only this test activity, then inject into its settled instance, never a dying one.
    scenario.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED));
    final long deadline = SystemClock.elapsedRealtime() + 5_000;
    final AtomicReference<String> state = new AtomicReference<>("no activity");
    final AtomicBoolean ready = new AtomicBoolean();
    String previous = "";
    int consecutive = 0;
    while (SystemClock.elapsedRealtime() < deadline)
    {
      scenario.onActivity(activity ->
      {
        final View root = activity.findViewById(R.id.main_activity_content);
        final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
        final boolean ime = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
        state.set("activity=" + System.identityHashCode(activity) + ", focus=" + activity.hasWindowFocus()
            + ", size=" + root.getWidth() + "x" + root.getHeight() + ", insets=" + (insets != null)
            + ", ime=" + ime);
        ready.set(activity.hasWindowFocus() && root.getWidth() > 0 && root.getHeight() > 0
            && insets != null && !ime);
      });
      if (ready.get() && state.get().equals(previous)) consecutive++;
      else consecutive = 0;
      previous = state.get();
      if (consecutive >= 3)
      {
        Log.i("UpdateUiLifecycleTest", "Settled test window: " + previous);
        scenario.onActivity(activity -> replaceController(activity, backend));
        return;
      }
      SystemClock.sleep(100);
    }
    throw new AssertionError("Test activity did not settle before backend injection: " + state.get());
  }

  private static void settle() throws Exception
  {
    final CountDownLatch mainBarrier = new CountDownLatch(1);
    new Handler(Looper.getMainLooper()).postDelayed(mainBarrier::countDown, 700);
    assertTrue("The main loop must deliver the completion barrier", mainBarrier.await(5, TimeUnit.SECONDS));
  }

  @Test
  public void editingAndPauseCancelChecksAndDiscardLateUi() throws Exception
  {
    final Backend backend = new Backend();
    try (SettingsSession scenario = new SettingsSession())
    {
      prepare(scenario, backend);
      assertTrue("Safe foreground starts a check; backend disposed=" + backend.disposed,
          backend.entered.await(5, TimeUnit.SECONDS));
      scenario.onActivity(activity -> activity.findViewById(R.id.test_input).requestFocus());
      assertFalse("Text editing immediately forbids feed access", backend.allowed);
      backend.release.countDown();
      settle();
      scenario.onActivity(activity ->
      {
        final TextView status = activity.findViewById(R.id.update_status);
        assertEquals("A cancelled result must not alter status", activity.getString(
            R.string.text__update__version_privacy, BuildConfig.VERSION_NAME), status.getText().toString());
      });
      scenario.goHome();
      assertFalse("Background forbids checks", backend.allowed);
    }
    assertTrue("Destroy closes the backend", backend.disposed);
  }

  @Test
  public void rapidManualTapsPromoteOneAutomaticCheck() throws Exception
  {
    final Backend backend = new Backend();
    try (SettingsSession scenario = new SettingsSession())
    {
      prepare(scenario, backend);
      assertTrue(backend.entered.await(5, TimeUnit.SECONDS));
      scenario.onActivity(activity ->
      {
        activity.findViewById(R.id.check_update_button).performClick();
        activity.findViewById(R.id.check_update_button).performClick();
      });
      assertEquals("Manual taps share the active request", 1, backend.checks.get());
      backend.release.countDown();
      settle();
      scenario.onActivity(activity ->
      {
        final TextView status = activity.findViewById(R.id.update_status);
        assertEquals(activity.getString(R.string.text__update__current, BuildConfig.VERSION_NAME), status.getText().toString());
      });
      assertEquals("Success does not continuously poll", 1, backend.checks.get());
    }
  }

  @Test
  public void manualTapAfterEditingSurvivesCancelledWorker() throws Exception
  {
    final Backend backend = new Backend();
    try (SettingsSession scenario = new SettingsSession())
    {
      prepare(scenario, backend);
      assertTrue(backend.entered.await(5, TimeUnit.SECONDS));
      scenario.onActivity(activity ->
      {
        activity.findViewById(R.id.test_input).requestFocus();
        assertFalse(backend.allowed);
        activity.findViewById(R.id.check_update_button).performClick();
      });
      backend.release.countDown();
      settle();
      assertEquals("The tap starts one fresh request after the cancelled one exits", 2, backend.checks.get());
      scenario.onActivity(activity ->
      {
        final TextView status = activity.findViewById(R.id.update_status);
        assertEquals(activity.getString(R.string.text__update__current, BuildConfig.VERSION_NAME), status.getText().toString());
      });
    }
  }

  @Test
  public void allThemesUseThemedUpdateEntryAndLocalPrivacy() throws Exception
  {
    final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    final String previous = TypingSettings.loadThemeName(context);
    try
    {
      for (final String theme : new String[]{KeyboardTheme.PAPER, KeyboardTheme.SKY, KeyboardTheme.NIGHT})
      {
        TypingSettings.saveThemeName(context, theme);
        final Backend backend = new Backend();
        backend.release.countDown();
        try (SettingsSession scenario = new SettingsSession())
        {
          prepare(scenario, backend);
          scenario.onActivity(activity ->
          {
            final TextView entry = activity.findViewById(R.id.check_update_button);
            final TypedValue expected = new TypedValue();
            activity.getTheme().resolveAttribute(R.attr.settingsText, expected, true);
            assertEquals(theme + " entry text follows the active theme", expected.data, entry.getCurrentTextColor());
            assertTrue(entry.isClickable());
            activity.findViewById(R.id.privacy_button).performClick();
            assertNotNull(activity.htmlWebView);
            assertTrue("Local pages cannot load network resources", activity.htmlWebView.getSettings().getBlockNetworkLoads());
          });
          settle();
          scenario.onActivity(activity ->
          {
            assertEquals("file:///android_asset/" + activity.getString(R.string.file_name__privacy_html),
                activity.htmlWebView.getUrl());
            assertFalse("A privacy dialog suspends checks", backend.allowed);
          });
        }
      }
    }
    finally
    {
      TypingSettings.saveThemeName(context, previous);
    }
  }
}
