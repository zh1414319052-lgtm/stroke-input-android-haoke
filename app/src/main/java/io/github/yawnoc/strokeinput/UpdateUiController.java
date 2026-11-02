/*
  Copyright 2026 Haoke Tools (浩科工具站)
  Licensed under GPL-3.0-only; see LICENSE.
*/
package io.github.yawnoc.strokeinput;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings-only update UI. The input service never owns or calls this controller.
 * All UI/state transitions run on the main thread; only bounded feed I/O runs in the worker.
 */
final class UpdateUiController implements AutoCloseable
{
  /** Keeps UI acceptance isolated from the network and production feed in instrumentation tests. */
  interface Backend
  {
    void setAllowed(boolean allowed);
    HaokeUpdateClient.Result check(boolean manual);
    boolean present(Runnable show, boolean manual);
    boolean later();
    boolean ignore();
    String pageUrlFromUserGesture();
    void dispose();
  }

  private static final class LiveBackend implements Backend
  {
    private final HaokeUpdateClient client;
    LiveBackend(final Context context) { client = new HaokeUpdateClient(context); }
    public void setAllowed(final boolean allowed) { client.setAllowed(allowed); }
    public HaokeUpdateClient.Result check(final boolean manual) { return client.check(manual); }
    public boolean present(final Runnable show, final boolean manual) { return client.present(show, manual); }
    public boolean later() { return client.later(); }
    public boolean ignore() { return client.ignore(); }
    public String pageUrlFromUserGesture() { return client.pageUrlFromUserGesture(); }
    public void dispose() { client.dispose(); }
  }

  private static final long SETTLE_MS = 500;
  private final MainActivity activity;
  private final View root;
  private final TextView status;
  private final Backend client;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::updateEligibility;
  private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener =
      (oldFocus, newFocus) -> updateEligibility();
  private final Runnable scheduledCheck = () -> startCheck(false);
  private boolean resumed;
  private boolean disposed;
  private boolean safe;
  private boolean busy;
  private boolean manualRequested;
  private boolean manualInFlight;
  private boolean ownDialog;
  private boolean retryScheduled;
  private boolean checkWhenIdle;
  private int retries;
  private long generation;
  private long flightGeneration;
  private AlertDialog dialog;

  UpdateUiController(final MainActivity activity)
  {
    this(activity, new LiveBackend(activity.getApplicationContext()));
  }

  UpdateUiController(final MainActivity activity, final Backend backend)
  {
    this.activity = activity;
    root = activity.findViewById(R.id.main_activity_content);
    status = activity.findViewById(R.id.update_status);
    status.setText(activity.getString(R.string.text__update__version_privacy, BuildConfig.VERSION_NAME));
    client = backend;
    root.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    root.getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
  }

  void onResume()
  {
    resumed = true;
    retries = 0;
    updateEligibility();
  }

  void onPause()
  {
    resumed = false;
    manualRequested = false;
    becomeUnsafe();
    dismissDialog();
  }

  void onWindowFocusChanged(final boolean hasFocus)
  {
    if (!hasFocus && !ownDialog) manualRequested = false;
    updateEligibility();
  }

  /** A tap may promote an automatic in-flight request, but never starts a second one. */
  void checkManually()
  {
    if (disposed || !resumed || ownDialog) return;
    manualRequested = true;
    final View focused = activity.getCurrentFocus();
    if (focused != null) focused.clearFocus();
    root.requestFocus();
    final InputMethodManager input =
        (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
    input.hideSoftInputFromWindow(root.getWindowToken(), 0);
    updateEligibility();
    if (safe) startCheck(true);
    // If the IME is still animating away, the next layout starts the manual check.
  }

  private boolean pageIsSafe()
  {
    if (!resumed || disposed || activity.isFinishing() || activity.isDestroyed()) return false;
    if (!activity.hasWindowFocus() && !ownDialog) return false;
    final View focused = activity.getCurrentFocus();
    if (focused != null && focused.onCheckIsTextEditor()) return false;
    final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
    return insets != null && !insets.isVisible(WindowInsetsCompat.Type.ime());
  }

  private void updateEligibility()
  {
    if (disposed) return;
    if (!pageIsSafe())
    {
      becomeUnsafe();
      return;
    }
    client.setAllowed(true);
    final boolean entering = !safe;
    safe = true;
    if (ownDialog) return;
    if (manualRequested)
    {
      startCheck(true);
    }
    else if (entering && busy)
    {
      checkWhenIdle = true;
    }
    else if (entering && !retryScheduled)
    {
      main.removeCallbacks(scheduledCheck);
      main.postDelayed(scheduledCheck, SETTLE_MS);
    }
  }

  private void becomeUnsafe()
  {
    if (safe || retryScheduled)
    {
      generation++;
      client.setAllowed(false);
    }
    safe = false;
    checkWhenIdle = false;
    main.removeCallbacks(scheduledCheck);
    retryScheduled = false;
    if (manualInFlight) status.setText(R.string.text__update__cancelled);
    if (ownDialog && !resumed) dismissDialog();
  }

  private void startCheck(final boolean manual)
  {
    main.removeCallbacks(scheduledCheck);
    retryScheduled = false;
    if (!pageIsSafe() || ownDialog)
    {
      updateEligibility();
      return;
    }
    if (busy)
    {
      if (flightGeneration != generation)
      {
        // An older request is still unwinding after cancellation. Keep this user tap for its successor.
        if (manual) manualRequested = true;
        else checkWhenIdle = true;
        return;
      }
      if (manual) manualInFlight = true;
      manualRequested = false;
      return;
    }
    safe = true;
    checkWhenIdle = false;
    client.setAllowed(true);
    manualRequested = false;
    manualInFlight = manual;
    busy = true;
    final long ticket = generation;
    flightGeneration = ticket;
    if (manual) status.setText(R.string.text__update__checking);
    worker.execute(() ->
    {
      final HaokeUpdateClient.Result result = client.check(manual);
      main.post(() -> finishCheck(ticket, result));
    });
  }

  private void finishCheck(final long ticket, final HaokeUpdateClient.Result result)
  {
    final boolean wasManual = manualInFlight;
    busy = false;
    manualInFlight = false;
    if (disposed) return;
    if (ticket != generation || !pageIsSafe() || ownDialog)
    {
      // The core discards the offer on cancellation; never retain a result across editing/background.
      if (manualRequested && pageIsSafe()) startCheck(true);
      else if (checkWhenIdle && pageIsSafe())
      {
        checkWhenIdle = false;
        main.postDelayed(scheduledCheck, SETTLE_MS);
      }
      else updateEligibility();
      return;
    }
    if ("throttled".equals(result.kind) && wasManual)
    {
      startCheck(true); // promotion arrived after the automatic debounce decision
      return;
    }
    if (("available".equals(result.kind) || "suppressed".equals(result.kind)) && result.release != null)
    {
      if ("available".equals(result.kind) || wasManual)
      {
        showRelease(result.release, wasManual);
      }
      return;
    }
    if ("error".equals(result.kind) || "silent-error".equals(result.kind)
        || "throttled".equals(result.kind))
    {
      if (wasManual) status.setText(R.string.text__update__failed);
      scheduleRetry(result.retryAfterMs);
      return;
    }
    if (wasManual)
    {
      if ("cancelled".equals(result.kind)) status.setText(R.string.text__update__cancelled);
      else status.setText(activity.getString(R.string.text__update__current, BuildConfig.VERSION_NAME));
    }
  }

  private void scheduleRetry(final long requestedDelayMs)
  {
    if (retries >= 2 || !pageIsSafe() || ownDialog) return;
    final long delay = Math.max(requestedDelayMs, retries == 0 ? 60_000 : 300_000);
    retries++;
    retryScheduled = true;
    main.postDelayed(scheduledCheck, delay);
  }

  private void showRelease(final HaokeUpdateClient.Release release, final boolean manual)
  {
    if (!pageIsSafe() || ownDialog) return;
    final boolean chinese = "zh".equals(activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
    String notes = chinese ? release.notesZh : release.notesEn;
    if (notes == null || notes.trim().isEmpty()) notes = chinese ? release.notesEn : release.notesZh;
    if (notes == null || notes.trim().isEmpty()) notes = activity.getString(R.string.text__update__empty_notes);
    final AlertDialog prepared = new AlertDialog.Builder(activity, R.style.StrokeInputAlert)
        .setTitle(activity.getString(R.string.text__update__title, release.versionName))
        .setMessage(activity.getString(R.string.text__update__notes, notes))
        .setPositiveButton(R.string.label__update__view, (ignored, which) -> openVersionPage())
        .setNegativeButton(R.string.label__update__later, (ignored, which) ->
        {
          if (!client.later()) choiceFailed();
        })
        .setNeutralButton(R.string.label__update__ignore, (ignored, which) ->
        {
          if (!client.ignore()) choiceFailed();
        })
        .create();
    prepared.setOnDismissListener(ignored ->
    {
      ownDialog = false;
      dialog = null;
      // Back/Close/View already acknowledge at the presentation boundary. No new poll on dismiss.
      main.post(this::updateEligibility);
    });
    final boolean shown = client.present(() ->
    {
      if (!pageIsSafe()) throw new IllegalStateException("Settings page no longer safe");
      ownDialog = true;
      dialog = prepared;
      prepared.show();
      final TextView message = prepared.findViewById(android.R.id.message);
      if (message != null) message.setTextIsSelectable(true);
    }, manual);
    if (!shown && manual) status.setText(R.string.text__update__failed);
  }

  private void openVersionPage()
  {
    final String page = client.pageUrlFromUserGesture();
    if (page == null)
    {
      choiceFailed();
      return;
    }
    try
    {
      activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(page)));
    }
    catch (ActivityNotFoundException exception)
    {
      Toast.makeText(activity, R.string.text__update__browser_failed, Toast.LENGTH_LONG).show();
    }
  }

  private void choiceFailed()
  {
    Toast.makeText(activity, R.string.text__update__storage_failed, Toast.LENGTH_LONG).show();
  }

  private void dismissDialog()
  {
    if (dialog != null) dialog.dismiss();
    dialog = null;
    ownDialog = false;
  }

  @Override
  public void close()
  {
    disposed = true;
    resumed = false;
    generation++;
    main.removeCallbacksAndMessages(null);
    client.dispose();
    worker.shutdownNow();
    dismissDialog();
    if (root.getViewTreeObserver().isAlive())
    {
      root.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
      root.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
    }
  }
}
