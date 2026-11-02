/*
  Copyright 2021--2025 Conway
  Modified 2026-10-28 by Haoke Tools (浩科工具站): platform WindowInsets.Type constant;
  candidates toolbar (quick phrases, handwriting, hide keyboard), candidate size, key feedback,
  stroke order display, themes, handwriting pad (half screen or full screen), voice input tool.
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.widget.TextViewCompat;

import java.util.List;

/*
  A container that holds:
    1. Main input block (fills the screen for full-screen handwriting):
       (a) Main input plane:
           - Popup recess
           - Stroke sequence bar
           - Candidates view (overlaid by a toolbar while there are no candidates)
           - Keyboard view
       (b) Key preview plane (overlaid)
       (c) Handwriting overlay (overlaid; draws the ink, and takes full-screen strokes)
    2. Bottom spacer (needed in API level 35+ for edge-to-edge bottom padding)
*/
public class InputContainer
  extends LinearLayout
{
  // Container properties
  private View mainFrame;
  private LinearLayout mainInputPlane;
  private View popupRecess;
  private StrokeSequenceBar strokeSequenceBar;
  private View candidatesFrame;
  private CandidatesView candidatesView;
  private CandidatesViewAdapter candidatesViewAdapter;
  private View candidatesToolbar;
  private Button quickPhrasesTool;
  private Button handwritingTool;
  private Button voiceTool;
  private Button hideKeyboardTool;
  private float toolbarBaseTextSizePx;
  private KeyboardView keyboardView;
  private View bottomSpacer;
  private WindowInsets lastWindowInsets;

  // Handwriting
  private HandwritingOverlayView handwritingOverlay;
  private boolean handwritingIsEnabled = false;
  private boolean handwritingFullScreenIsPreferred = false;
  private boolean extractModeIsActive = false; // landscape fullscreen editing: handwriting stays half screen
  private boolean handwritingIsFullScreen = false;
  private String handwritingToolLabel = "";
  private String handwritingFullScreenToolLabel = "";
  private String handwritingHalfScreenToolLabel = "";

  /*
    A listener for the toolbar shown in place of the candidates while there are none.
  */
  public interface ToolbarListener
  {
    void onShowQuickPhrases();
    void onShowHandwriting(); // also switches half/full screen while handwriting
    void onShowVoice();
    void onHideKeyboard();
  }

  @Override
  public WindowInsets onApplyWindowInsets(WindowInsets insets)
  {
    super.onApplyWindowInsets(insets);
    lastWindowInsets = insets;
    updateNavigationSpace();
    return insets;
  }

  @Override
  protected void onLayout(final boolean changed, final int left, final int top, final int right, final int bottom)
  {
    super.onLayout(changed, left, top, right, bottom);
    updateNavigationSpace();
  }

  private void updateNavigationSpace()
  {
    if (bottomSpacer == null) return;
    // The framework IME navigation frame is a DECOR sibling, so child-dispatched insets can
    // already be consumed. Read the same root insets as NavigationBarController instead.
    final View root = getRootView();
    final WindowInsets rootInsets = root.getRootWindowInsets();
    final WindowInsets insets = rootInsets != null ? rootInsets : lastWindowInsets;
    if (insets == null) return;
    final int navigationHeight;
    if (Build.VERSION.SDK_INT >= 30)
    {
      final Insets stable = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
      final Insets current = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
      navigationHeight = Math.min(stable.bottom, current.bottom);
    }
    else
    {
      navigationHeight = Math.min(insets.getStableInsetBottom(), insets.getSystemWindowInsetBottom());
    }
    final int space;
    if (root.getHeight() > 0 && getHeight() > 0)
    {
      final int[] rootLocation = new int[2];
      final int[] containerLocation = new int[2];
      root.getLocationInWindow(rootLocation);
      getLocationInWindow(containerLocation);
      space = ImeNavigationSpace.overlap(navigationHeight,
        rootLocation[1] + root.getHeight(), containerLocation[1] + getHeight());
    }
    else
    {
      space = navigationHeight;
    }
    final ViewGroup.LayoutParams parameters = bottomSpacer.getLayoutParams();
    if (parameters.height != space)
    {
      parameters.height = space;
      bottomSpacer.setLayoutParams(parameters);
    }
  }

  public InputContainer(final Context context, final AttributeSet attributes)
  {
    super(context, attributes);
  }

  public void initialisePopupRecess()
  {
    popupRecess = findViewById(R.id.popup_recess);
  }

  public void initialiseStrokeSequenceBar(final Context context)
  {
    strokeSequenceBar = findViewById(R.id.stroke_sequence_bar);
    strokeSequenceBar.setTypeface(Typeface.createFromAsset(context.getAssets(), KeyboardView.KEYBOARD_FONT_FILE_NAME));
  }

  public void initialiseCandidatesView(final CandidatesViewAdapter.CandidateListener candidateListener)
  {
    candidatesFrame = findViewById(R.id.candidates_frame);
    candidatesView = findViewById(R.id.candidates_view);
    candidatesView.setCandidateListener(candidateListener);
    candidatesViewAdapter = candidatesView.getCandidatesViewAdapter();
  }

  /*
    The localised context supplies the button labels in the UI language (see AppLanguage).
  */
  public void initialiseToolbar(final Context localisedContext, final ToolbarListener toolbarListener)
  {
    candidatesToolbar = findViewById(R.id.candidates_toolbar);
    quickPhrasesTool = findViewById(R.id.quick_phrases_tool);
    handwritingTool = findViewById(R.id.handwriting_tool);
    voiceTool = findViewById(R.id.voice_tool);
    hideKeyboardTool = findViewById(R.id.hide_keyboard_tool);
    toolbarBaseTextSizePx = quickPhrasesTool.getTextSize();

    quickPhrasesTool.setText(localisedContext.getString(R.string.label__toolbar__quick_phrases));
    handwritingToolLabel = localisedContext.getString(R.string.label__toolbar__handwriting);
    handwritingFullScreenToolLabel = localisedContext.getString(R.string.label__toolbar__handwriting_full_screen);
    handwritingHalfScreenToolLabel = localisedContext.getString(R.string.label__toolbar__handwriting_half_screen);
    handwritingTool.setText(handwritingToolLabel);
    voiceTool.setText(localisedContext.getString(R.string.label__toolbar__voice));
    // icon only, so that three tools fit in English and with large candidates
    hideKeyboardTool.setContentDescription(localisedContext.getString(R.string.label__toolbar__hide_keyboard));
    quickPhrasesTool.setOnClickListener(view -> toolbarListener.onShowQuickPhrases());
    handwritingTool.setOnClickListener(view -> toolbarListener.onShowHandwriting());
    voiceTool.setOnClickListener(view -> toolbarListener.onShowVoice());
    hideKeyboardTool.setOnClickListener(view -> toolbarListener.onHideKeyboard());

    candidatesToolbar.setVisibility(VISIBLE); // there are no candidates initially
  }

  /*
    Theme colours for everything above and around the keys (the keys are coloured by Keyboard.applyTheme).
  */
  public void setKeyboardTheme(final KeyboardTheme theme)
  {
    candidatesView.setBackgroundColor(theme.candidatesFill);
    candidatesViewAdapter.setTheme(theme);

    candidatesToolbar.setBackgroundColor(theme.candidatesFill);
    final ColorStateList toolColours =
            new ColorStateList(new int[][]{{android.R.attr.state_pressed}, {}}, new int[]{theme.onAccent, theme.accent});
    for (final Button tool : new Button[]{quickPhrasesTool, handwritingTool, voiceTool, hideKeyboardTool})
    {
      tool.setTextColor(toolColours);
      TextViewCompat.setCompoundDrawableTintList(tool, toolColours);
      final StateListDrawable toolBackground = new StateListDrawable();
      toolBackground.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(theme.accent));
      toolBackground.addState(new int[]{}, new ColorDrawable(theme.candidatesFill));
      tool.setBackground(toolBackground);
    }

    strokeSequenceBar.setBackgroundTintList(ColorStateList.valueOf(theme.strokeSequenceFill));
    strokeSequenceBar.setTextColor(theme.strokeSequenceText);
    bottomSpacer.setBackgroundColor(theme.keyboardFill);
    keyboardView.setKeyboardTheme(theme);
    if (handwritingOverlay != null)
    {
      handwritingOverlay.invalidate(); // ink colour comes from the pad key
    }
  }

  public void setKeyFeedback(final boolean vibrationIsEnabled, final boolean soundIsEnabled)
  {
    keyboardView.setKeyFeedback(vibrationIsEnabled, soundIsEnabled);
  }

  /*
    Scale the candidates (and the bars sharing their row) for the large text mode.
  */
  public void setCandidateSizeScale(final float sizeScale)
  {
    candidatesViewAdapter.setSizeScale(sizeScale);
    final int scaledHeightPx = candidatesViewAdapter.getScaledHeightPx();

    for (
      final View view
        : new View[]{candidatesView, candidatesToolbar, quickPhrasesTool, handwritingTool, voiceTool, hideKeyboardTool}
    )
    {
      final ViewGroup.LayoutParams layoutParameters = view.getLayoutParams();
      if (layoutParameters.height != scaledHeightPx)
      {
        layoutParameters.height = scaledHeightPx;
        view.setLayoutParams(layoutParameters);
      }
    }
    quickPhrasesTool.setTextSize(TypedValue.COMPLEX_UNIT_PX, toolbarBaseTextSizePx * sizeScale);
    handwritingTool.setTextSize(TypedValue.COMPLEX_UNIT_PX, toolbarBaseTextSizePx * sizeScale);
    voiceTool.setTextSize(TypedValue.COMPLEX_UNIT_PX, toolbarBaseTextSizePx * sizeScale);
    strokeSequenceBar.setSizeScale(sizeScale);
  }

  public void showStrokeOrder(final String strokeOrderText)
  {
    strokeSequenceBar.showStrokeOrder(strokeOrderText);
  }

  public void initialiseKeyboardView(
    final KeyboardView.KeyboardListener keyboardListener,
    final Keyboard keyboard
  )
  {
    mainFrame = findViewById(R.id.main_frame);
    mainInputPlane = findViewById(R.id.main_input_plane);
    keyboardView = findViewById(R.id.keyboard_view);
    keyboardView.setKeyboardListener(keyboardListener);
    keyboardView.setMainInputPlane(mainInputPlane);
    keyboardView.setKeyPreviewPlane(findViewById(R.id.key_preview_plane));
    handwritingOverlay = findViewById(R.id.handwriting_overlay);
    handwritingOverlay.initialise(keyboardView, candidatesFrame);
    keyboardView.setHandwritingOverlay(handwritingOverlay);
    keyboardView.setKeyboard(keyboard);
    updateHandwritingLayout();
  }

  /*
    Full-screen handwriting: the panel takes all the height it is offered (see onMeasure),
    the main block fills it above the bottom spacer, and the overlay takes strokes above the candidates.
  */
  public void setHandwritingFullScreenPreferred(final boolean isPreferred)
  {
    handwritingFullScreenIsPreferred = isPreferred;
    updateHandwritingLayout();
  }

  public void setHandwritingEnabled(final boolean isEnabled)
  {
    handwritingIsEnabled = isEnabled;
    keyboardView.setHandwritingEnabled(isEnabled);
    updateHandwritingLayout();
  }

  public boolean isHandwritingEnabled()
  {
    return handwritingIsEnabled;
  }

  public boolean isHandwritingFullScreen()
  {
    return handwritingIsFullScreen;
  }

  private void updateHandwritingLayout()
  {
    if (handwritingOverlay == null)
    {
      return;
    }
    final boolean hasPad = keyboardView.getHandwritingPadKey() != null;
    final boolean hasWritingSurface = keyboardView.isHandwritingAvailable();
    final boolean isFullScreen = hasWritingSurface && handwritingFullScreenIsPreferred && !extractModeIsActive;

    if (hasPad)
    {
      // Full-screen keeps the original bottom keyboard size. Half-screen is fitted in onMeasure.
      keyboardView.getKeyboard().adjustKeyboardHeight();
      keyboardView.requestLayout();
    }

    handwritingOverlay.setVisibility(hasWritingSurface ? VISIBLE : GONE);
    handwritingOverlay.setFullScreen(isFullScreen);
    handwritingTool.setVisibility(handwritingIsEnabled ? VISIBLE : GONE);
    handwritingTool.setText(
      (!hasWritingSurface)
        ? handwritingToolLabel
        : (handwritingFullScreenIsPreferred) ? handwritingHalfScreenToolLabel : handwritingFullScreenToolLabel
    );

    final LayoutParams mainFrameParameters = (LayoutParams) mainFrame.getLayoutParams();
    final int mainFrameHeight = (isFullScreen) ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
    final float mainFrameWeight = (isFullScreen) ? 1 : 0;
    if (isFullScreen != handwritingIsFullScreen
          || mainFrameParameters.height != mainFrameHeight
          || mainFrameParameters.weight != mainFrameWeight)
    {
      handwritingIsFullScreen = isFullScreen;
      mainFrameParameters.height = mainFrameHeight;
      mainFrameParameters.weight = mainFrameWeight;
      mainFrame.setLayoutParams(mainFrameParameters);
      requestLayout();
    }
  }

  @Override
  protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec)
  {
    int heightSpec = heightMeasureSpec;
    if (handwritingIsFullScreen && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST)
    {
      heightSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY);
    }
    super.onMeasure(widthMeasureSpec, heightSpec);
    if (keyboardView != null && keyboardView.getHandwritingPadKey() != null
          && keyboardView.isHandwritingAvailable() && !handwritingIsFullScreen)
    {
      final int windowHeight = getResources().getDisplayMetrics().heightPixels;
      final int parentLimit = (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
                                ? windowHeight : MeasureSpec.getSize(heightMeasureSpec);
      final int panelLimit = HandwritingHeight.panelLimitPx(windowHeight, parentLimit);
      final int fixedHeight = getPaddingTop() + getPaddingBottom()
        + Math.max(occupiedHeight(bottomSpacer), bottomSpacer.getLayoutParams().height)
        + mainInputPlane.getPaddingTop() + mainInputPlane.getPaddingBottom()
        + occupiedHeight(popupRecess) + occupiedHeight(strokeSequenceBar) + occupiedHeight(candidatesFrame)
        + keyboardView.getPaddingTop() + keyboardView.getPaddingBottom();
      if (keyboardView.getKeyboard().adjustHalfScreenHandwritingHeight(Math.max(0, panelLimit - fixedHeight)))
      {
        // The first pass measures real chrome, including scaled candidates and system navigation.
        keyboardView.forceLayout();
        mainInputPlane.forceLayout();
        mainFrame.forceLayout();
        super.onMeasure(widthMeasureSpec, heightSpec);
        keyboardView.invalidate();
        handwritingOverlay.invalidate();
      }
    }
  }

  private static int occupiedHeight(final View view)
  {
    if (view == null || view.getVisibility() == GONE)
    {
      return 0;
    }
    final ViewGroup.LayoutParams parameters = view.getLayoutParams();
    final int margins = (parameters instanceof ViewGroup.MarginLayoutParams)
                          ? ((ViewGroup.MarginLayoutParams) parameters).topMargin
                            + ((ViewGroup.MarginLayoutParams) parameters).bottomMargin : 0;
    return view.getMeasuredHeight() + margins;
  }

  public void initialiseBottomSpacer()
  {
    bottomSpacer = findViewById(R.id.bottom_spacer);
  }

  public void setPopupRecessLayout(final boolean isFullscreen)
  {
    extractModeIsActive = isFullscreen;
    updateHandwritingLayout();
    if (isFullscreen)
    {
      popupRecess.setVisibility(GONE);
    }
    else
    {
      popupRecess.setVisibility(INVISIBLE);
    }
  }

  public void setBackground(final boolean isFullscreen)
  {
    final int backgroundResourceId =
            (isFullscreen)
              ? R.color.stroke_sequence_bar_fill_fullscreen
              : 0; // none
    setBackgroundResource(backgroundResourceId);
  }

  public void updateHeight()
  {
    keyboardView.requestLayout();
    getViewTreeObserver().addOnPreDrawListener( // otherwise downsized keyboard won't fall to the bottom
      new ViewTreeObserver.OnPreDrawListener()
      {
        @Override
        public boolean onPreDraw()
        {
          getViewTreeObserver().removeOnPreDrawListener(this);
          requestLayout();
          return false;
        }
      }
    );
  }

  public void setStrokeDigitSequence(final String strokeDigitSequence)
  {
    strokeSequenceBar.setStrokeDigitSequence(strokeDigitSequence);
  }

  public void setCandidates(final List<String> candidates, final boolean firstCandidateIsEmphasised)
  {
    candidatesViewAdapter.updateCandidates(candidates, firstCandidateIsEmphasised);
    candidatesView.scrollToPosition(0);
    if (candidatesToolbar != null)
    {
      candidatesToolbar.setVisibility((candidates.isEmpty()) ? VISIBLE : GONE);
    }
  }

  public int getCandidatesViewTop()
  {
    // the frame (not the view inside it) is a child of the main input plane,
    // which is lower down in the main block for full-screen handwriting
    return mainInputPlane.getTop() + candidatesFrame.getTop();
  }

  public Keyboard getKeyboard()
  {
    return keyboardView.getKeyboard();
  }

  public void setKeyboard(final Keyboard keyboard)
  {
    keyboardView.setKeyboard(keyboard);
    updateHandwritingLayout();
  }

  public void setKeyRepeatIntervalMilliseconds(final int milliseconds)
  {
    keyboardView.setKeyRepeatIntervalMilliseconds(milliseconds);
  }

  public HandwritingInk getHandwritingInk()
  {
    return keyboardView.getHandwritingInk();
  }

  public int getHandwritingPadHeight()
  {
    return keyboardView.getHandwritingPadHeight();
  }

  public void setHandwritingHintText(final String hintText)
  {
    keyboardView.setHandwritingHintText(hintText);
  }

  /*
    Redraw the keys (e.g. handwriting ink) without a new layout.
  */
  public boolean hasVoicePad()
  {
    return keyboardView.hasVoicePad();
  }

  public void setVoiceStatus(final String statusText, final boolean isRecording, final float level)
  {
    keyboardView.setVoiceStatus(statusText, isRecording, level);
  }

  public void refreshKeyboard()
  {
    keyboardView.invalidate();
    handwritingOverlay.invalidate();
  }

  public void redrawKeyboard()
  {
    keyboardView.requestLayout();
    keyboardView.invalidate();
  }
}
