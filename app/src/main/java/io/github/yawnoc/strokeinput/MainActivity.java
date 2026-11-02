/*
  Copyright 2021--2026 Conway
  Modified 2026-10-28 by Haoke Tools (浩科工具站): system-primary-language UI,
  optional candidates independent of this activity, Haoke Tools link,
  keyboard layout, candidate size, typing aids and quick phrases settings,
  grouped settings page with setup status (design/stroke-input.haoke-ui.json),
  theme picker and themed settings page (design/stroke-input-theme-pages.haoke-ui.json).
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.yawnoc.utilities.Contexty;

/*
  The main activity of the application.
*/
public class MainActivity
  extends AppCompatActivity
  implements View.OnClickListener
{
  public static final String CANDIDATE_ORDER_PREFERENCE_KEY = "candidateOrderPreference";
  public static final String CANDIDATE_ORDER_PREFER_TRADITIONAL_FIRST = "TRADITIONAL_FIRST";
  public static final String CANDIDATE_ORDER_PREFER_SIMPLIFIED_FIRST = "SIMPLIFIED_FIRST";

  public static final String KEYBOARD_HEIGHT_ADJUSTMENT_PROGRESS_KEY = "keyboardHeightAdjustmentProgress";
  public static final int KEYBOARD_HEIGHT_ADJUSTMENT_DEFAULT_PROGRESS = 10;
  public static final int KEYBOARD_HEIGHT_ADJUSTMENT_MAX_PROGRESS = 20;
  public static final float KEYBOARD_HEIGHT_ADJUSTMENT_FACTOR_MIN = 0.5f;
  public static final float KEYBOARD_HEIGHT_ADJUSTMENT_FACTOR_MAX = 1.5f;
  public static final float KEYBOARD_HEIGHT_ADJUSTMENT_FACTOR_RANGE =
          KEYBOARD_HEIGHT_ADJUSTMENT_FACTOR_MAX - KEYBOARD_HEIGHT_ADJUSTMENT_FACTOR_MIN;

  public static final String OPTIONAL_CANDIDATES_PREFERENCE_KEY_PREFIX = "optionalCandidatesPreference_";
  private static final List<Integer> OPTIONAL_CANDIDATES_SWITCH_IDS =
          Arrays.asList(
            R.id.optional_main_tail_1_switch,
            R.id.optional_main_tail_2_switch,
            R.id.optional_main_tail_3_switch,
            R.id.optional_main_tail_4_switch,
            R.id.optional_extension_a_tail_switch,
            R.id.optional_kangxi_bengx_switch,
            R.id.optional_simplified_livermorium_switch,
            R.id.optional_biangbiang_switch
          );

  private static final int[] CANDIDATE_SIZE_LABEL_IDS =
          {
            R.string.label__main_activity__size_standard,
            R.string.label__main_activity__size_large,
            R.string.label__main_activity__size_extra_large,
          };

  // Themes, in picker order (design/stroke-input-theme-pages.haoke-ui.json)
  private static final List<String> THEME_NAMES =
          Arrays.asList(KeyboardTheme.PAPER, KeyboardTheme.SKY, KeyboardTheme.NIGHT);
  private static final int[] THEME_STYLE_IDS =
          {R.style.Theme_StrokeInput_Paper, R.style.Theme_StrokeInput_Sky, R.style.Theme_StrokeInput_Night};
  private static final int[] THEME_TILE_IDS = {R.id.theme_paper_tile, R.id.theme_sky_tile, R.id.theme_night_tile};
  private static final int[] THEME_PREVIEW_IDS =
          {R.drawable.theme_preview_paper, R.drawable.theme_preview_sky, R.drawable.theme_preview_night};
  private static final int[] THEME_LABEL_IDS =
          {R.string.label__theme__paper, R.string.label__theme__sky, R.string.label__theme__night};

  private static final String LOG_TAG = "MainActivity";
  private static final String ASSETS_DIRECTORY = "file:///android_asset/";
  private static final String WEBVIEW_CSS_FILE_NAME = "webview.css";
  private static final String SOURCE_CODE_URI = "https://github.com/zh1414319052-lgtm/stroke-input-android-haoke";
  private static final String HAOKE_TOOLS_URI = "https://haoke.org";

  AlertDialog.Builder candidateOrderDialogBuilder;
  Dialog candidateOrderDialog;
  AlertDialog.Builder htmlWebViewContainer;
  WebView htmlWebView;
  private Dialog htmlDialog;
  private UpdateUiController updateUi;

  @Override
  protected void attachBaseContext(final Context baseContext)
  {
    super.attachBaseContext(AppLanguage.wrap(baseContext));
  }

  @Override
  protected void onCreate(final Bundle savedInstanceState)
  {
    final String themeName = TypingSettings.loadThemeName(this);
    setTheme(THEME_STYLE_IDS[THEME_NAMES.indexOf(themeName)]);
    super.onCreate(savedInstanceState);
    setTitle(R.string.label__main_activity__welcome);
    setContentView(R.layout.main_activity);
    // ScrollView's constructor resets its descendant-focus policy after XML attributes are read.
    // Set it after inflation so an explicit container focus cannot fall through to the test editor.
    ((ViewGroup) findViewById(R.id.main_activity_content))
      .setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
    setSystemBarAppearance(!KeyboardTheme.NIGHT.equals(themeName));
    AppLanguage.applyTextLocale(
      findViewById(R.id.main_activity_content),
      getResources().getConfiguration().getLocales().get(0)
    );
    findViewById(R.id.app_icon).setClipToOutline(true); // rounded corners from its background shape
    initialiseThemePicker(themeName);

    findViewById(R.id.optional_candidates_header).setOnClickListener(this);
    findViewById(R.id.source_code_button).setOnClickListener(this);
    findViewById(R.id.help_button).setOnClickListener(this);
    findViewById(R.id.about_button).setOnClickListener(this);
    findViewById(R.id.privacy_button).setOnClickListener(this);
    findViewById(R.id.haoke_tools_button).setOnClickListener(this);
    findViewById(R.id.check_update_button).setOnClickListener(this);
    findViewById(R.id.input_method_settings_button).setOnClickListener(this);
    findViewById(R.id.change_keyboard_button).setOnClickListener(this);
    findViewById(R.id.candidate_order_button).setOnClickListener(this);
    findViewById(R.id.keyboard_layout_button).setOnClickListener(this);
    findViewById(R.id.candidate_size_button).setOnClickListener(this);
    findViewById(R.id.save_quick_phrases_button).setOnClickListener(this);

    initialisePreferenceSwitch(
      R.id.key_vibration_row,
      R.id.key_vibration_switch,
      TypingSettings.KEY_VIBRATION_PREFERENCE_KEY,
      TypingSettings.KEY_VIBRATION_DEFAULT
    );
    initialisePreferenceSwitch(
      R.id.key_sound_row,
      R.id.key_sound_switch,
      TypingSettings.KEY_SOUND_PREFERENCE_KEY,
      TypingSettings.KEY_SOUND_DEFAULT
    );
    initialisePreferenceSwitch(
      R.id.stroke_tolerance_row,
      R.id.stroke_tolerance_switch,
      TypingSettings.STROKE_TOLERANCE_PREFERENCE_KEY,
      TypingSettings.STROKE_TOLERANCE_DEFAULT
    );
    initialisePreferenceSwitch(
      R.id.handwriting_on_keyboard_row,
      R.id.handwriting_on_keyboard_switch,
      TypingSettings.HANDWRITING_ON_KEYBOARD_PREFERENCE_KEY,
      TypingSettings.HANDWRITING_ON_KEYBOARD_DEFAULT
    );

    final TextView quickPhrasesInput = findViewById(R.id.quick_phrases_input);
    quickPhrasesInput.setText(QuickPhrases.serialise(TypingSettings.loadQuickPhrases(this)));

    final SeekBar keyboardHeightAdjustmentSeekBar = findViewById(R.id.keyboard_height_adjustment_seek_bar);
    keyboardHeightAdjustmentSeekBar.setOnSeekBarChangeListener(
      new SeekBar.OnSeekBarChangeListener()
      {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean isUserChange)
        {
          saveKeyboardHeightAdjustmentProgress(progress);
          final float adjustmentFactor = keyboardHeightAdjustmentProgressToFactor(progress);
          setKeyboardHeightAdjustmentDisplayText(adjustmentFactor);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar)
        {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar)
        {
          // Trigger height readjustment and ensure keyboard shown
          restartInputOnFocusedView(true);
        }
      }
    );

    for (final int optionalCandidateSwitchId : OPTIONAL_CANDIDATES_SWITCH_IDS)
    {
      final OptionalCandidatesSwitch optionalCandidatesSwitch = findViewById(optionalCandidateSwitchId);
      final String characters = optionalCandidatesSwitch.characters;
      final boolean isAllowed = loadSavedOptionalCandidatesPreference(this, characters);

      optionalCandidatesSwitch.setChecked(isAllowed);
      optionalCandidatesSwitch.setOnCheckedChangeListener(
        (buttonView, isChecked) ->
        {
          saveOptionalCandidatesPreference(characters, isChecked);
          setOptionalCandidatesSummaryText();
        }
      );
    }
    setOptionalCandidatesSummaryText();

    setCandidateOrderButtonText(loadSavedCandidateOrderPreference());
    setKeyboardLayoutButtonText();
    setCandidateSizeButtonText();

    final int adjustmentProgress = loadSavedKeyboardHeightAdjustmentProgress(getApplicationContext());
    final float adjustmentFactor = keyboardHeightAdjustmentProgressToFactor(adjustmentProgress);
    keyboardHeightAdjustmentSeekBar.setMax(KEYBOARD_HEIGHT_ADJUSTMENT_MAX_PROGRESS);
    keyboardHeightAdjustmentSeekBar.setProgress(adjustmentProgress);
    setKeyboardHeightAdjustmentDisplayText(adjustmentFactor);

    // Keep startup settled; editing is entered only when the user selects a text field.
    findViewById(R.id.main_activity_content).requestFocus();
    updateUi = new UpdateUiController(this);
  }

  @Override
  protected void onResume()
  {
    super.onResume();
    updateSetupSteps();
    if (updateUi != null) updateUi.onResume();
  }

  @Override
  protected void onPause()
  {
    if (updateUi != null) updateUi.onPause();
    super.onPause();
  }

  @Override
  protected void onDestroy()
  {
    if (updateUi != null) updateUi.close();
    if (htmlDialog != null) htmlDialog.dismiss();
    if (candidateOrderDialog != null) candidateOrderDialog.dismiss();
    if (htmlWebView != null) htmlWebView.destroy();
    super.onDestroy();
  }

  @Override
  public void onWindowFocusChanged(final boolean hasFocus)
  {
    super.onWindowFocusChanged(hasFocus);
    if (updateUi != null) updateUi.onWindowFocusChanged(hasFocus);
    if (hasFocus)
    {
      updateSetupSteps(); // e.g. after the system keyboard picker (a window over this one) closes
    }
  }

  /*
    Dark icons on the light themes' system bars, light icons on the dark theme's.
  */
  private void setSystemBarAppearance(final boolean isLightTheme)
  {
    final WindowInsetsControllerCompat insetsController =
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    insetsController.setAppearanceLightStatusBars(isLightTheme);
    insetsController.setAppearanceLightNavigationBars(isLightTheme);
  }

  private void initialiseThemePicker(final String selectedThemeName)
  {
    for (int index = 0; index < THEME_NAMES.size(); index++)
    {
      final String themeName = THEME_NAMES.get(index);
      final boolean isSelected = themeName.equals(selectedThemeName);

      final View tile = findViewById(THEME_TILE_IDS[index]);
      final ImageView preview = tile.findViewById(R.id.theme_tile_preview);
      final TextView label = tile.findViewById(R.id.theme_tile_label);
      preview.setImageResource(THEME_PREVIEW_IDS[index]);
      label.setText(THEME_LABEL_IDS[index]);
      label.setTypeface(label.getTypeface(), (isSelected) ? Typeface.BOLD : Typeface.NORMAL);
      tile.setSelected(isSelected); // outline, check badge and label colour follow the selected state
      tile.setOnClickListener(view -> selectTheme(themeName));
    }
  }

  /*
    The settings page takes a new theme by recreating; the keyboard takes it when input next starts.
  */
  private void selectTheme(final String themeName)
  {
    if (themeName.equals(TypingSettings.loadThemeName(this)))
    {
      return;
    }
    TypingSettings.saveThemeName(getApplicationContext(), themeName);
    recreate();
  }

  private int resolveThemeColour(final int attributeId)
  {
    final TypedValue typedValue = new TypedValue();
    getTheme().resolveAttribute(attributeId, typedValue, true);
    return typedValue.data;
  }

  /*
    Show whether this input method is enabled and currently selected (read-only system state).
  */
  private void updateSetupSteps()
  {
    final InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    boolean isEnabled = false;
    for (final InputMethodInfo inputMethodInfo : inputMethodManager.getEnabledInputMethodList())
    {
      if (inputMethodInfo.getPackageName().equals(getPackageName()))
      {
        isEnabled = true;
        break;
      }
    }
    final String defaultInputMethod =
            Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
    final boolean isSelected = defaultInputMethod != null && defaultInputMethod.startsWith(getPackageName() + "/");

    setSetupStep(
      R.id.step_enable_badge, R.id.step_enable_badge_number, R.id.step_enable_badge_check,
      R.id.step_enable_status, R.id.input_method_settings_button,
      isEnabled,
      (isEnabled) ? R.string.text__setup__enable_done : R.string.text__setup__enable_pending,
      (isEnabled) ? R.string.label__main_activity__input_method_settings_button : R.string.label__setup__enable_button_pending
    );
    final int switchStatus =
            (isSelected)
              ? R.string.text__setup__switch_done
              : (isEnabled) ? R.string.text__setup__switch_pending : R.string.text__setup__switch_after_enable;
    setSetupStep(
      R.id.step_switch_badge, R.id.step_switch_badge_number, R.id.step_switch_badge_check,
      R.id.step_switch_status, R.id.change_keyboard_button,
      isSelected,
      switchStatus,
      (isSelected) ? R.string.label__main_activity__change_keyboard_button : R.string.label__setup__switch_button_pending
    );

    // The next step to do gets the filled (primary) button
    styleSetupButton(R.id.input_method_settings_button, !isEnabled);
    styleSetupButton(R.id.change_keyboard_button, isEnabled && !isSelected);
  }

  private void setSetupStep(
    final int badgeId,
    final int badgeNumberId,
    final int badgeCheckId,
    final int statusId,
    final int buttonId,
    final boolean isDone,
    final int statusTextId,
    final int buttonTextId
  )
  {
    findViewById(badgeId).setBackgroundResource((isDone) ? R.drawable.step_badge_done : R.drawable.step_badge_pending);
    findViewById(badgeNumberId).setVisibility((isDone) ? View.GONE : View.VISIBLE);
    findViewById(badgeCheckId).setVisibility((isDone) ? View.VISIBLE : View.GONE);

    final TextView status = findViewById(statusId);
    status.setText(statusTextId);
    status.setTextColor(resolveThemeColour((isDone) ? R.attr.settingsStatusDone : R.attr.settingsAccent));

    final TextView button = findViewById(buttonId);
    button.setText(buttonTextId);
  }

  private void styleSetupButton(final int buttonId, final boolean isPrimary)
  {
    final MaterialButton button = findViewById(buttonId);
    final int accent = resolveThemeColour(R.attr.settingsAccent);
    button.setBackgroundTintList(ColorStateList.valueOf((isPrimary) ? accent : Color.TRANSPARENT));
    button.setTextColor((isPrimary) ? resolveThemeColour(R.attr.settingsOnAccent) : accent);
  }

  private void setOptionalCandidatesSummaryText()
  {
    int allowedGroupCount = 0;
    for (final boolean isAllowed : loadSavedOptionalCandidatesPreferences(getApplicationContext()).values())
    {
      if (isAllowed)
      {
        allowedGroupCount++;
      }
    }
    final TextView summary = findViewById(R.id.optional_candidates_summary);
    summary.setText(
      getResources().getQuantityString(
        R.plurals.text__settings__optional_candidates_summary,
        allowedGroupCount,
        allowedGroupCount
      )
    );
  }

  private void toggleOptionalCandidates()
  {
    final View container = findViewById(R.id.optional_candidates_container);
    final boolean willShow = container.getVisibility() != View.VISIBLE;
    container.setVisibility((willShow) ? View.VISIBLE : View.GONE);
    findViewById(R.id.optional_candidates_chevron).setRotation((willShow) ? 180 : 0);
  }

  /*
    Restart input so that the input method service applies changed settings (in onStartInputView).
  */
  private void restartInputOnFocusedView(final boolean shouldShowKeyboard)
  {
    View focusView = getCurrentFocus();
    if (shouldShowKeyboard && (focusView == null || !focusView.onCheckIsTextEditor()))
    {
      focusView = findViewById(R.id.test_input);
      focusView.requestFocus();
    }
    if (focusView != null && focusView.onCheckIsTextEditor())
    {
      final InputMethodManager inputMethodManager =
              (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      inputMethodManager.restartInput(focusView);
      if (shouldShowKeyboard)
      {
        inputMethodManager.showSoftInput(focusView, 0);
      }
    }
  }

  private void initialisePreferenceSwitch(
    final int rowId,
    final int switchId,
    final String preferenceKey,
    final boolean defaultValue
  )
  {
    final CompoundButton preferenceSwitch = findViewById(switchId);
    preferenceSwitch.setChecked(TypingSettings.loadBoolean(getApplicationContext(), preferenceKey, defaultValue));
    preferenceSwitch.setOnCheckedChangeListener(
      (buttonView, isChecked) ->
      {
        TypingSettings.saveBoolean(getApplicationContext(), preferenceKey, isChecked);
        restartInputOnFocusedView(false);
      }
    );
    findViewById(rowId).setOnClickListener(view -> preferenceSwitch.toggle()); // the whole row is the touch target
  }

  private void setKeyboardLayoutButtonText()
  {
    final TextView keyboardLayoutValue = findViewById(R.id.keyboard_layout_value);
    keyboardLayoutValue.setText(
      (TypingSettings.isLargeKeyboardLayout(getApplicationContext()))
        ? R.string.label__main_activity__layout_large
        : R.string.label__main_activity__layout_standard
    );
  }

  private void setCandidateSizeButtonText()
  {
    final TextView candidateSizeValue = findViewById(R.id.candidate_size_value);
    candidateSizeValue.setText(CANDIDATE_SIZE_LABEL_IDS[TypingSettings.loadCandidateSizeIndex(getApplicationContext())]);
  }

  // A secondary line explains the layout without relying on a theme thumbnail.
  private CharSequence describeChoice(final int labelId, final int summaryId)
  {
    final String label = getString(labelId);
    final SpannableString text = new SpannableString(label + "\n" + getString(summaryId));
    text.setSpan(new RelativeSizeSpan(0.8f), label.length() + 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    text.setSpan(new ForegroundColorSpan(resolveThemeColour(R.attr.settingsText2)), label.length() + 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return text;
  }

  private void showKeyboardLayoutDialog()
  {
    final CharSequence[] layoutLabels =
            {
              describeChoice(R.string.label__main_activity__layout_standard, R.string.text__layout__standard_summary),
              describeChoice(R.string.label__main_activity__layout_large, R.string.text__layout__large_summary),
            };
    final int checkedIndex = (TypingSettings.isLargeKeyboardLayout(getApplicationContext())) ? 1 : 0;

    new AlertDialog.Builder(this, R.style.StrokeInputAlert)
      .setTitle(R.string.label__main_activity__keyboard_layout)
      .setSingleChoiceItems(
        new ArrayAdapter<>(this, R.layout.settings_choice_item, layoutLabels),
        checkedIndex,
        (dialog, index) ->
        {
          TypingSettings.saveKeyboardLayout(
            getApplicationContext(),
            (index == 1) ? TypingSettings.KEYBOARD_LAYOUT_LARGE : TypingSettings.KEYBOARD_LAYOUT_STANDARD
          );
          setKeyboardLayoutButtonText();
          dialog.dismiss();
          findViewById(R.id.test_input).requestFocus();
          restartInputOnFocusedView(true);
        }
      )
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private void showCandidateSizeDialog()
  {
    final String[] sizeLabels = new String[CANDIDATE_SIZE_LABEL_IDS.length];
    for (int index = 0; index < sizeLabels.length; index++)
    {
      sizeLabels[index] = getString(CANDIDATE_SIZE_LABEL_IDS[index]);
    }

    new AlertDialog.Builder(this, R.style.StrokeInputAlert)
      .setTitle(R.string.label__main_activity__candidate_size)
      .setSingleChoiceItems(
        new ArrayAdapter<>(this, R.layout.settings_choice_item, sizeLabels),
        TypingSettings.loadCandidateSizeIndex(getApplicationContext()),
        (dialog, index) ->
        {
          TypingSettings.saveCandidateSizeIndex(getApplicationContext(), index);
          setCandidateSizeButtonText();
          dialog.dismiss();
          findViewById(R.id.test_input).requestFocus();
          restartInputOnFocusedView(true);
        }
      )
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private void saveQuickPhrases()
  {
    final TextView quickPhrasesInput = findViewById(R.id.quick_phrases_input);
    final List<String> savedPhrases =
            TypingSettings.saveQuickPhrases(getApplicationContext(), quickPhrasesInput.getText().toString());
    quickPhrasesInput.setText(QuickPhrases.serialise(savedPhrases)); // show exactly what was kept
    Toast.makeText(
      this,
      getResources().getQuantityString(
        R.plurals.message__main_activity__quick_phrases_saved,
        savedPhrases.size(),
        savedPhrases.size()
      ),
      Toast.LENGTH_SHORT
    ).show();
  }

  public static boolean isTraditionalPreferred(final String candidateOrderPreference)
  {
    if (candidateOrderPreference == null)
    {
      return true;
    }

    return !candidateOrderPreference.equals(CANDIDATE_ORDER_PREFER_SIMPLIFIED_FIRST);
  }

  private String loadSavedCandidateOrderPreference()
  {
    return loadSavedCandidateOrderPreference(getApplicationContext());
  }

  public static String loadSavedCandidateOrderPreference(final Context context)
  {
    return
      Contexty.loadPreferenceString(context, StrokeInputService.PREFERENCES_FILE_NAME, CANDIDATE_ORDER_PREFERENCE_KEY);
  }

  private void saveCandidateOrderPreference(final String candidateOrderPreference)
  {
    Contexty.savePreferenceString(
      getApplicationContext(),
      StrokeInputService.PREFERENCES_FILE_NAME,
      CANDIDATE_ORDER_PREFERENCE_KEY,
      candidateOrderPreference
    );
  }

  private void setCandidateOrderButtonText(final String candidateOrderPreference)
  {
    final TextView candidateOrderValue = findViewById(R.id.candidate_order_value);
    final String candidateOrderButtonText =
            (isTraditionalPreferred(candidateOrderPreference))
              ? getString(R.string.label__main_activity__traditional_first)
              : getString(R.string.label__main_activity__simplified_first);
    candidateOrderValue.setText(candidateOrderButtonText);
  }

  public static int loadSavedKeyboardHeightAdjustmentProgress(final Context context)
  {
    return
      Contexty.loadPreferenceInt(
        context,
        StrokeInputService.PREFERENCES_FILE_NAME,
        KEYBOARD_HEIGHT_ADJUSTMENT_PROGRESS_KEY,
        KEYBOARD_HEIGHT_ADJUSTMENT_DEFAULT_PROGRESS
      );
  }

  private void saveKeyboardHeightAdjustmentProgress(final int keyboardHeightAdjustmentProgress)
  {
    Contexty.savePreferenceInt(
      getApplicationContext(),
      StrokeInputService.PREFERENCES_FILE_NAME,
      KEYBOARD_HEIGHT_ADJUSTMENT_PROGRESS_KEY,
      keyboardHeightAdjustmentProgress
    );
  }

  public static float keyboardHeightAdjustmentProgressToFactor(final int progress)
  {
    final float progressFraction = ((float) progress) / KEYBOARD_HEIGHT_ADJUSTMENT_MAX_PROGRESS;
    return KEYBOARD_HEIGHT_ADJUSTMENT_FACTOR_MIN + progressFraction * KEYBOARD_HEIGHT_ADJUSTMENT_FACTOR_RANGE;
  }

  private void setKeyboardHeightAdjustmentDisplayText(final float adjustmentFactor)
  {
    final TextView keyboardHeightAdjustmentDisplay = findViewById(R.id.keyboard_height_adjustment_display);
    final NumberFormat numberFormat = NumberFormat.getInstance();
    numberFormat.setMinimumFractionDigits(1); // "× 1.0", as in the design
    final String adjustmentDisplayText = "×\u00A0" + numberFormat.format(adjustmentFactor);
    keyboardHeightAdjustmentDisplay.setText(adjustmentDisplayText);
  }

  private static boolean loadSavedOptionalCandidatesPreference(final Context context, final String characters)
  {
    return
      Contexty.loadPreferenceBoolean(
        context,
        StrokeInputService.PREFERENCES_FILE_NAME,
        OPTIONAL_CANDIDATES_PREFERENCE_KEY_PREFIX + characters,
        false
      );
  }

  private void saveOptionalCandidatesPreference(final String characters, final boolean isAllowed)
  {
    Contexty.savePreferenceBoolean(
      getApplicationContext(),
      StrokeInputService.PREFERENCES_FILE_NAME,
      OPTIONAL_CANDIDATES_PREFERENCE_KEY_PREFIX + characters,
      isAllowed
    );
  }

  public static Map<String, Boolean> loadSavedOptionalCandidatesPreferences(final Context context)
  {
    final Map<String, Boolean> isAllowedFromCharacters = new HashMap<>();

    for (final String characters : OptionalCandidates.charactersList())
    {
      isAllowedFromCharacters.put(characters, loadSavedOptionalCandidatesPreference(context, characters));
    }

    return isAllowedFromCharacters;
  }

  @Override
  public void onClick(final View view)
  {
    final int viewId = view.getId();
    if (viewId == R.id.source_code_button)
    {
      Contexty.openInBrowser(this, SOURCE_CODE_URI);
    }
    else if (viewId == R.id.help_button)
    {
      showHtmlWebView(R.string.file_name__help_html);
    }
    else if (viewId == R.id.about_button)
    {
      showHtmlWebView(R.string.file_name__about_html);
    }
    else if (viewId == R.id.privacy_button)
    {
      showHtmlWebView(R.string.file_name__privacy_html);
    }
    else if (viewId == R.id.check_update_button)
    {
      updateUi.checkManually();
    }
    else if (viewId == R.id.haoke_tools_button)
    {
      Contexty.openInBrowser(this, HAOKE_TOOLS_URI);
    }
    else if (viewId == R.id.input_method_settings_button)
    {
      Contexty.showSystemInputMethodSettings(this);
    }
    else if (viewId == R.id.change_keyboard_button)
    {
      Contexty.showSystemKeyboardChanger(this);
    }
    else if (viewId == R.id.candidate_order_button)
    {
      showCandidateOrderDialog();
    }
    else if (viewId == R.id.keyboard_layout_button)
    {
      showKeyboardLayoutDialog();
    }
    else if (viewId == R.id.candidate_size_button)
    {
      showCandidateSizeDialog();
    }
    else if (viewId == R.id.save_quick_phrases_button)
    {
      saveQuickPhrases();
    }
    else if (viewId == R.id.optional_candidates_header)
    {
      toggleOptionalCandidates();
    }
    else if (viewId == R.id.traditional_first_button)
    {
      saveCandidateOrderPreference(CANDIDATE_ORDER_PREFER_TRADITIONAL_FIRST);
      setCandidateOrderButtonText(CANDIDATE_ORDER_PREFER_TRADITIONAL_FIRST);
      candidateOrderDialog.dismiss();
    }
    else if (viewId == R.id.simplified_first_button)
    {
      saveCandidateOrderPreference(CANDIDATE_ORDER_PREFER_SIMPLIFIED_FIRST);
      setCandidateOrderButtonText(CANDIDATE_ORDER_PREFER_SIMPLIFIED_FIRST);
      candidateOrderDialog.dismiss();
    }
  }

  private void showHtmlWebView(final String uri)
  {
    if (htmlWebViewContainer == null)
    {
      htmlWebViewContainer = new AlertDialog.Builder(this, R.style.StrokeInputAlert);
      htmlWebViewContainer.setPositiveButton(R.string.label__main_activity__return, null);
    }

    try
    {
      if (htmlWebView == null)
      {
        htmlWebView = new WebView(this);
        htmlWebView.setBackgroundColor(Color.TRANSPARENT);
        htmlWebView.getSettings().setBuiltInZoomControls(true);
        htmlWebView.getSettings().setDisplayZoomControls(false);
        // INTERNET is for the signed updater only; local help/privacy pages cannot load remote resources.
        htmlWebView.getSettings().setBlockNetworkLoads(true);
        htmlWebView.setWebViewClient(new ThemedWebViewClient(themedWebViewCss()));
      }

      htmlWebView.loadUrl(uri);
      htmlDialog = htmlWebViewContainer
        .setView(htmlWebView)
        .setOnDismissListener(dialog ->
        {
          if (htmlWebView.getParent() instanceof ViewGroup)
            ((ViewGroup) htmlWebView.getParent()).removeView(htmlWebView);
          htmlDialog = null;
        })
        .show();
    }
    catch (Exception exception)
    {
      Contexty.showErrorMessage(this, getString(R.string.message__error__webview));
    }
  }

  private void showHtmlWebView(final int fileNameResourceId)
  {
    showHtmlWebView(ASSETS_DIRECTORY + getString(fileNameResourceId));
  }

  /*
    The help and about pages' stylesheet with the theme's colours appended (the assets stay unchanged).
  */
  private String themedWebViewCss()
  {
    String css = "";
    try (final InputStream inputStream = getAssets().open(WEBVIEW_CSS_FILE_NAME))
    {
      final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      final byte[] buffer = new byte[4096];
      int length;
      while ((length = inputStream.read(buffer)) != -1)
      {
        outputStream.write(buffer, 0, length);
      }
      css = outputStream.toString(StandardCharsets.UTF_8.name());
    }
    catch (IOException exception)
    {
      Log.e(LOG_TAG, "themedWebViewCss failed", exception);
    }

    final KeyboardTheme keyboardTheme = TypingSettings.loadKeyboardTheme(this);
    final String border = cssColour(resolveThemeColour(R.attr.settingsText3));
    return css
      + "\n/* Haoke theme */\n"
      + "html{background:transparent}"
      + "body{background:" + cssColour(resolveThemeColour(R.attr.settingsDialog))
        + ";color:" + cssColour(resolveThemeColour(R.attr.settingsText)) + "}"
      + "a{color:" + cssColour(resolveThemeColour(R.attr.settingsAccent)) + "}"
      + "footer,td,th,.notice,kbd{border-color:" + border + "}"
      + "kbd{background:" + cssColour(keyboardTheme.keyCharacterFill) + "}"
      + ".stroke-key{color:" + cssColour(keyboardTheme.strokeText) + "}"
      + ".enter-key{background:" + cssColour(keyboardTheme.keyEnterFill)
        + ";color:" + cssColour(keyboardTheme.enterText) + "}";
  }

  private static String cssColour(final int colour)
  {
    return String.format("#%06X", colour & 0xFFFFFF);
  }

  /*
    Serves the themed stylesheet, and opens outside links in the browser (as without a client).
  */
  private class ThemedWebViewClient
    extends WebViewClient
  {
    private final byte[] cssBytes;

    ThemedWebViewClient(final String css)
    {
      cssBytes = css.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(final WebView view, final WebResourceRequest request)
    {
      if (request.getUrl().toString().equals(ASSETS_DIRECTORY + WEBVIEW_CSS_FILE_NAME))
      {
        return new WebResourceResponse("text/css", "UTF-8", new ByteArrayInputStream(cssBytes));
      }
      return null;
    }

    @Override
    public boolean shouldOverrideUrlLoading(final WebView view, final WebResourceRequest request)
    {
      final String url = request.getUrl().toString();
      if (url.startsWith(ASSETS_DIRECTORY))
      {
        return false;
      }
      Contexty.openInBrowser(MainActivity.this, url);
      return true;
    }
  }

  private void showCandidateOrderDialog()
  {
    candidateOrderDialogBuilder = new AlertDialog.Builder(this, R.style.StrokeInputAlert);
    candidateOrderDialogBuilder
      .setTitle(R.string.label__main_activity__candidate_order)
      .setView(R.layout.candidate_order_dialog)
      .setCancelable(true);

    candidateOrderDialog = candidateOrderDialogBuilder.create();
    final int dialog_size = ViewGroup.LayoutParams.WRAP_CONTENT;
    candidateOrderDialog.show();
    Objects.requireNonNull(candidateOrderDialog.getWindow()).setLayout(dialog_size, dialog_size);

    final RadioGroup candidateOrderRadioGroup = candidateOrderDialog.findViewById(R.id.candidate_order_radio_group);
    final Button traditionalFirstButton = candidateOrderDialog.findViewById(R.id.traditional_first_button);
    final Button simplifiedFirstButton = candidateOrderDialog.findViewById(R.id.simplified_first_button);

    final boolean traditionalIsPreferred = isTraditionalPreferred(loadSavedCandidateOrderPreference());
    final int savedCandidateOrderButtonId =
            (traditionalIsPreferred)
              ? R.id.traditional_first_button
              : R.id.simplified_first_button;
    candidateOrderRadioGroup.check(savedCandidateOrderButtonId);

    traditionalFirstButton.setOnClickListener(this);
    simplifiedFirstButton.setOnClickListener(this);
  }
}
