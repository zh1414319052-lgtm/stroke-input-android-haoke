/*
  Copyright 2021--2025 Conway
  Modified 2026-10-28 by Haoke Tools (浩科工具站): keyboards follow the system primary language;
  wildcard stroke, stroke-order tolerance, large-key layout, stroke order on candidate long press,
  key feedback, candidate size, quick phrases, hide-keyboard tool, themes, more symbol pages
  offline handwriting, half screen or full screen, and offline voice input (2026-10-29).
  Licensed under the GNU General Public License v3.0 (GPL-3.0-only).
  This is free software with NO WARRANTY etc. etc.,
  see LICENSE or <https://www.gnu.org/licenses/>.
*/

package io.github.yawnoc.strokeinput;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.github.yawnoc.utilities.Contexty;
import io.github.yawnoc.utilities.Mappy;
import io.github.yawnoc.utilities.Stringy;

/*
  An InputMethodService for the Stroke Input Method (筆畫輸入法).
*/
public class StrokeInputService
  extends InputMethodService
  implements
    CandidatesViewAdapter.CandidateListener,
    KeyboardView.KeyboardListener,
    InputContainer.ToolbarListener,
    HandwritingRecogniser.StateListener,
    HandwritingRecogniser.ResultListener,
    VoiceRecogniser.StateListener,
    VoiceRecorder.Listener
{
  private static final String LOG_TAG = "StrokeInputService";

  public static final String SHIFT_KEY_VALUE_TEXT = "SHIFT";
  public static final String ENTER_KEY_VALUE_TEXT = "ENTER";
  public static final String BACKSPACE_VALUE_TEXT = "BACKSPACE";
  public static final String SPACE_BAR_VALUE_TEXT = "SPACE";
  public static final String HANDWRITING_PAD_VALUE_TEXT = "HANDWRITING_PAD";
  public static final String VOICE_PAD_VALUE_TEXT = "VOICE_PAD";
  private static final int VOICE_HOLD_MILLISECONDS = 400; // a longer press is press-to-talk, a shorter one a toggle
  private static final int VOICE_MIN_SAMPLES = VoiceRecorder.SAMPLE_RATE * 3 / 10; // shorter is a slip
  private static final int VOICE_MODEL_RELEASE_DELAY_MILLISECONDS = 120_000; // frees the model's memory after use
  private static final int HANDWRITING_RECOGNITION_DELAY_MILLISECONDS = 500;
  private static final int HANDWRITING_FULL_SCREEN_RECOGNITION_DELAY_MILLISECONDS = 700; // longer moves between strokes

  public static final String STROKE_DIGIT_1 = "1";
  public static final String STROKE_DIGIT_2 = "2";
  public static final String STROKE_DIGIT_3 = "3";
  public static final String STROKE_DIGIT_4 = "4";
  public static final String STROKE_DIGIT_5 = "5";
  public static final String STROKE_DIGIT_WILDCARD = "" + StrokeMatcher.WILDCARD_DIGIT; // compile-time constant

  private static final String STROKE_KEY_VALUE_TEXT_PREFIX = "STROKE_";
  private static final String STROKE_1_VALUE_TEXT = STROKE_KEY_VALUE_TEXT_PREFIX + STROKE_DIGIT_1;
  private static final String STROKE_2_VALUE_TEXT = STROKE_KEY_VALUE_TEXT_PREFIX + STROKE_DIGIT_2;
  private static final String STROKE_3_VALUE_TEXT = STROKE_KEY_VALUE_TEXT_PREFIX + STROKE_DIGIT_3;
  private static final String STROKE_4_VALUE_TEXT = STROKE_KEY_VALUE_TEXT_PREFIX + STROKE_DIGIT_4;
  private static final String STROKE_5_VALUE_TEXT = STROKE_KEY_VALUE_TEXT_PREFIX + STROKE_DIGIT_5;
  private static final String STROKE_WILDCARD_VALUE_TEXT = STROKE_KEY_VALUE_TEXT_PREFIX + STROKE_DIGIT_WILDCARD;

  private static final String STROKES_KEYBOARD_NAME = "STROKES";
  private static final String STROKES_LARGE_KEYBOARD_NAME = "STROKES_LARGE";
  private static final String STROKES_SYMBOLS_1_KEYBOARD_NAME = "STROKES_SYMBOLS_1";
  private static final String STROKES_SYMBOLS_2_KEYBOARD_NAME = "STROKES_SYMBOLS_2";
  private static final String STROKES_SYMBOLS_3_KEYBOARD_NAME = "STROKES_SYMBOLS_3";
  // Haoke symbol pages, shown between upstream pages 1 and 2 (page order: 1, common, shapes, maths, 2, 3)
  private static final String STROKES_SYMBOLS_COMMON_KEYBOARD_NAME = "STROKES_SYMBOLS_COMMON";
  private static final String STROKES_SYMBOLS_SHAPES_KEYBOARD_NAME = "STROKES_SYMBOLS_SHAPES";
  private static final String STROKES_SYMBOLS_MATH_KEYBOARD_NAME = "STROKES_SYMBOLS_MATH";
  private static final String HANDWRITING_KEYBOARD_NAME = "HANDWRITING";
  private static final String VOICE_KEYBOARD_NAME = "VOICE";
  private static final String QWERTY_KEYBOARD_NAME = "QWERTY";
  private static final String QWERTY_SYMBOLS_KEYBOARD_NAME = "QWERTY_SYMBOLS";

  private static final String SWITCH_KEYBOARD_VALUE_TEXT_PREFIX = "SWITCH_TO_";

  private static final String SWITCH_TO_STROKES_VALUE_TEXT =
          SWITCH_KEYBOARD_VALUE_TEXT_PREFIX + STROKES_KEYBOARD_NAME;

  private static final String SWITCH_TO_STROKES_SYMBOLS_1_VALUE_TEXT =
          SWITCH_KEYBOARD_VALUE_TEXT_PREFIX + STROKES_SYMBOLS_1_KEYBOARD_NAME;

  private static final String SWITCH_TO_STROKES_SYMBOLS_2_VALUE_TEXT =
          SWITCH_KEYBOARD_VALUE_TEXT_PREFIX + STROKES_SYMBOLS_2_KEYBOARD_NAME;

  private static final String SWITCH_TO_STROKES_SYMBOLS_3_VALUE_TEXT =
          SWITCH_KEYBOARD_VALUE_TEXT_PREFIX + STROKES_SYMBOLS_3_KEYBOARD_NAME;
  private static final String SWITCH_TO_STROKES_SYMBOLS_COMMON_VALUE_TEXT =
          SWITCH_KEYBOARD_VALUE_TEXT_PREFIX + STROKES_SYMBOLS_COMMON_KEYBOARD_NAME;
  private static final String SWITCH_TO_STROKES_SYMBOLS_SHAPES_VALUE_TEXT =
          SWITCH_KEYBOARD_VALUE_TEXT_PREFIX + STROKES_SYMBOLS_SHAPES_KEYBOARD_NAME;
  private static final String SWITCH_TO_STROKES_SYMBOLS_MATH_VALUE_TEXT =
          SWITCH_KEYBOARD_VALUE_TEXT_PREFIX + STROKES_SYMBOLS_MATH_KEYBOARD_NAME;

  private static final String SWITCH_TO_QWERTY_VALUE_TEXT =
          SWITCH_KEYBOARD_VALUE_TEXT_PREFIX + QWERTY_KEYBOARD_NAME;

  private static final String SWITCH_TO_QWERTY_SYMBOLS_VALUE_TEXT =
          SWITCH_KEYBOARD_VALUE_TEXT_PREFIX + QWERTY_SYMBOLS_KEYBOARD_NAME;

  private static final int BACKSPACE_REPEAT_INTERVAL_MILLISECONDS_ASCII = 50;
  private static final int BACKSPACE_REPEAT_INTERVAL_MILLISECONDS_UTF_8 = 100;

  public static final String PREFERENCES_FILE_NAME = "preferences.txt";
  private static final String KEYBOARD_NAME_PREFERENCE_KEY = "keyboardName";

  private static final String SEQUENCE_CHARACTERS_FILE_NAME = "sequence-characters.txt";
  private static final String CHARACTERS_FILE_NAME_TRADITIONAL = "characters-traditional.txt";
  private static final String CHARACTERS_FILE_NAME_SIMPLIFIED = "characters-simplified.txt";
  private static final String RANKING_FILE_NAME_TRADITIONAL = "ranking-traditional.txt";
  private static final String RANKING_FILE_NAME_SIMPLIFIED = "ranking-simplified.txt";
  private static final String PHRASES_FILE_NAME_TRADITIONAL = "phrases-traditional.txt";
  private static final String PHRASES_FILE_NAME_SIMPLIFIED = "phrases-simplified.txt";

  private static final int LAG_PREVENTION_CODE_POINT_COUNT = 1400;
  private static final int CJK_MAIN_CODE_POINT_START = 0x4E00;
  private static final int CJK_MAIN_CODE_POINT_END = 0x9FFF;
  private static final int CJK_EXTENSION_CODE_POINT_MIN = 0x3400;
  private static final int CJK_EXTENSION_CODE_POINT_MAX = 0x2CEAF;
  private static final int RANKING_PENALTY_CJK_EXTENSION = CJK_MAIN_CODE_POINT_END - CJK_EXTENSION_CODE_POINT_MIN + 1;
  private static final int RANKING_PENALTY_PER_CHAR = 2 * CJK_EXTENSION_CODE_POINT_MAX;
  private static final int RANKING_PENALTY_UNPREFERRED = 10 * CJK_EXTENSION_CODE_POINT_MAX;
  private static final int MAX_PREFIX_MATCH_COUNT = 30;
  private static final int MAX_PHRASE_LENGTH = 6;
  private static final int MAX_TOLERANCE_MATCH_COUNT = 10;
  private static final int MAX_STROKE_ORDER_CHARACTERS = 3;
  private static final int MAX_STROKE_ORDER_VARIANTS = 3;

  Keyboard strokesKeyboard;
  Keyboard strokesLargeKeyboard;
  Keyboard strokesSymbols1Keyboard;
  Keyboard strokesSymbols2Keyboard;
  Keyboard strokesSymbols3Keyboard;
  Keyboard strokesSymbolsCommonKeyboard;
  Keyboard strokesSymbolsShapesKeyboard;
  Keyboard strokesSymbolsMathKeyboard;
  Keyboard handwritingKeyboard;
  Keyboard voiceKeyboard;
  Keyboard qwertyKeyboard;
  Keyboard qwertySymbolsKeyboard;

  private Map<Keyboard, String> nameFromKeyboard;
  private Map<String, Keyboard> keyboardFromName;
  private Set<Keyboard> keyboards;

  private InputContainer inputContainer;

  private final NavigableMap<String, String> charactersFromStrokeDigitSequence = new TreeMap<>();
  private final Set<Integer> codePointsTraditional = new HashSet<>();
  private final Set<Integer> codePointsSimplified = new HashSet<>();
  private final Map<Integer, Integer> sortingRankFromCodePointTraditional = new HashMap<>();
  private final Map<Integer, Integer> sortingRankFromCodePointSimplified = new HashMap<>();
  private final Set<Integer> commonCodePointsTraditional = new HashSet<>();
  private final Set<Integer> commonCodePointsSimplified = new HashSet<>();
  private final NavigableSet<String> phrasesTraditional = new TreeSet<>();
  private final NavigableSet<String> phrasesSimplified = new TreeSet<>();

  private Set<Integer> unpreferredCodePoints;
  private Map<Integer, Integer> sortingRankFromCodePoint;
  private Set<Integer> commonCodePoints;
  private NavigableSet<String> phrases;

  private String strokeDigitSequence = "";
  private List<String> candidates = new ArrayList<>();
  private final List<Integer> phraseCompletionFirstCodePoints = new ArrayList<>();

  private int inputActionsBits;
  private boolean enterKeyHasAction;
  private boolean inputIsPassword;

  private boolean strokeToleranceIsEnabled = TypingSettings.STROKE_TOLERANCE_DEFAULT;
  private boolean quickPhrasesAreShowing = false;
  private boolean strokeOrderIsShowing = false;

  // Handwriting: the recogniser is created when the handwriting keyboard is first used
  private HandwritingRecogniser handwritingRecogniser;
  private final Handler handwritingHandler = new Handler(Looper.getMainLooper());
  private final Runnable handwritingRecognitionRunnable = this::recogniseHandwriting;
  private boolean handwritingIsComposing = false; // the best reading is in the editor as composing text

  // Voice input: the recogniser is created when the voice keyboard is first shown, and freed a while after
  private VoiceRecogniser voiceRecogniser;
  private final VoiceRecorder voiceRecorder = new VoiceRecorder();
  private final Runnable voiceRecogniserRelease = this::releaseVoiceRecogniser;
  private boolean voicePressStartedRecording = false;
  private boolean voicePressStopsRecording = false;
  private long voicePressDownMilliseconds;
  private boolean voiceIsHeldToTalk = false;
  private boolean voiceIsRecognising = false;
  private long voiceSessionId = 0; // invalidated whenever the editor or voice interaction is left
  private boolean voiceServiceDestroyed = false;
  private int voiceMessageId = 0; // a one-off status (nothing heard, microphone busy) until the next action
  private float voiceLevel = 0;

  @Override
  public void onCreate()
  {
    super.onCreate();

    loadSequenceCharactersData(SEQUENCE_CHARACTERS_FILE_NAME, charactersFromStrokeDigitSequence);
    loadCharactersData(CHARACTERS_FILE_NAME_TRADITIONAL, codePointsTraditional);
    loadCharactersData(CHARACTERS_FILE_NAME_SIMPLIFIED, codePointsSimplified);
    loadRankingData(RANKING_FILE_NAME_TRADITIONAL, sortingRankFromCodePointTraditional, commonCodePointsTraditional);
    loadRankingData(RANKING_FILE_NAME_SIMPLIFIED, sortingRankFromCodePointSimplified, commonCodePointsSimplified);
    loadPhrasesData(PHRASES_FILE_NAME_TRADITIONAL, phrasesTraditional);
    loadPhrasesData(PHRASES_FILE_NAME_SIMPLIFIED, phrasesSimplified);

    updateCandidateOrderPreference();
  }

  @SuppressLint("InflateParams")
  @Override
  public View onCreateInputView()
  {
    // Re-evaluated on every (re)creation, so that a change of system language takes effect
    final Context keyboardContext = AppLanguage.wrap(this);
    strokesKeyboard = new Keyboard(keyboardContext, R.xml.keyboard_strokes);
    strokesLargeKeyboard = new Keyboard(keyboardContext, R.xml.keyboard_strokes_large);
    strokesSymbols1Keyboard = new Keyboard(keyboardContext, R.xml.keyboard_strokes_symbols_1);
    strokesSymbols2Keyboard = new Keyboard(keyboardContext, R.xml.keyboard_strokes_symbols_2);
    strokesSymbols3Keyboard = new Keyboard(keyboardContext, R.xml.keyboard_strokes_symbols_3);
    strokesSymbolsCommonKeyboard = new Keyboard(keyboardContext, R.xml.keyboard_strokes_symbols_common);
    strokesSymbolsShapesKeyboard = new Keyboard(keyboardContext, R.xml.keyboard_strokes_symbols_shapes);
    strokesSymbolsMathKeyboard = new Keyboard(keyboardContext, R.xml.keyboard_strokes_symbols_math);
    handwritingKeyboard = new Keyboard(keyboardContext, R.xml.keyboard_handwriting);
    voiceKeyboard = new Keyboard(keyboardContext, R.xml.keyboard_voice);
    qwertyKeyboard = new Keyboard(keyboardContext, R.xml.keyboard_qwerty);
    qwertySymbolsKeyboard = new Keyboard(keyboardContext, R.xml.keyboard_qwerty_symbols);

    nameFromKeyboard = new HashMap<>();
    nameFromKeyboard.put(strokesKeyboard, STROKES_KEYBOARD_NAME);
    nameFromKeyboard.put(strokesLargeKeyboard, STROKES_LARGE_KEYBOARD_NAME);
    nameFromKeyboard.put(strokesSymbols1Keyboard, STROKES_SYMBOLS_1_KEYBOARD_NAME);
    nameFromKeyboard.put(strokesSymbols2Keyboard, STROKES_SYMBOLS_2_KEYBOARD_NAME);
    nameFromKeyboard.put(strokesSymbols3Keyboard, STROKES_SYMBOLS_3_KEYBOARD_NAME);
    nameFromKeyboard.put(strokesSymbolsCommonKeyboard, STROKES_SYMBOLS_COMMON_KEYBOARD_NAME);
    nameFromKeyboard.put(strokesSymbolsShapesKeyboard, STROKES_SYMBOLS_SHAPES_KEYBOARD_NAME);
    nameFromKeyboard.put(strokesSymbolsMathKeyboard, STROKES_SYMBOLS_MATH_KEYBOARD_NAME);
    nameFromKeyboard.put(handwritingKeyboard, HANDWRITING_KEYBOARD_NAME);
    nameFromKeyboard.put(voiceKeyboard, VOICE_KEYBOARD_NAME);
    nameFromKeyboard.put(qwertyKeyboard, QWERTY_KEYBOARD_NAME);
    nameFromKeyboard.put(qwertySymbolsKeyboard, QWERTY_SYMBOLS_KEYBOARD_NAME);
    keyboardFromName = Mappy.invertMap(nameFromKeyboard);
    keyboards = nameFromKeyboard.keySet();

    inputContainer = (InputContainer) getLayoutInflater().inflate(R.layout.input_container, null);
    inputContainer.initialisePopupRecess();
    inputContainer.initialiseStrokeSequenceBar(this);
    inputContainer.initialiseCandidatesView(this);
    inputContainer.initialiseToolbar(keyboardContext, this);
    inputContainer.initialiseKeyboardView(this, loadSavedKeyboard());
    inputContainer.initialiseBottomSpacer();

    return inputContainer;
  }

  private Keyboard loadSavedKeyboard()
  {
    final String savedKeyboardName =
            Contexty.loadPreferenceString(getApplicationContext(), PREFERENCES_FILE_NAME, KEYBOARD_NAME_PREFERENCE_KEY);
    final Keyboard savedKeyboard = keyboardFromName.get(savedKeyboardName);
    if (savedKeyboard == handwritingKeyboard)
    {
      // v1.7.0-haoke.12 writes directly on the ordinary keyboard; migrate the retired pad mode.
      return preferredStrokesKeyboard();
    }
    else if (savedKeyboard != null && !isMainStrokesKeyboard(savedKeyboard))
    {
      return savedKeyboard;
    }
    else
    {
      return preferredStrokesKeyboard();
    }
  }

  /*
    The main strokes keyboard in the layout chosen in the settings (standard or large keys).
  */
  private Keyboard preferredStrokesKeyboard()
  {
    return (TypingSettings.isLargeKeyboardLayout(getApplicationContext())) ? strokesLargeKeyboard : strokesKeyboard;
  }

  private boolean isMainStrokesKeyboard(final Keyboard keyboard)
  {
    return keyboard == strokesKeyboard || keyboard == strokesLargeKeyboard;
  }

  /*
    Apply settings that may have been changed in the main activity since the keyboard was last shown.
  */
  private void applyTypingSettings()
  {
    final Context applicationContext = getApplicationContext();

    inputContainer.setKeyFeedback(
      TypingSettings.loadBoolean(
        applicationContext,
        TypingSettings.KEY_VIBRATION_PREFERENCE_KEY,
        TypingSettings.KEY_VIBRATION_DEFAULT
      ),
      TypingSettings.loadBoolean(
        applicationContext,
        TypingSettings.KEY_SOUND_PREFERENCE_KEY,
        TypingSettings.KEY_SOUND_DEFAULT
      )
    );
    inputContainer.setCandidateSizeScale(TypingSettings.loadCandidateSizeScale(applicationContext));

    final KeyboardTheme keyboardTheme = TypingSettings.loadKeyboardTheme(applicationContext);
    for (final Keyboard keyboard : keyboards)
    {
      keyboard.applyTheme(keyboardTheme);
    }
    inputContainer.setKeyboardTheme(keyboardTheme);
    // The IME owns the system switcher/gesture-bar appearance while it is visible.
    // Match their contrast to the same footer palette on every input/theme refresh.
    if (getWindow() != null && getWindow().getWindow() != null)
    {
      final android.view.Window imeWindow = getWindow().getWindow();
      androidx.core.view.WindowCompat.getInsetsController(imeWindow, imeWindow.getDecorView())
        .setAppearanceLightNavigationBars(!KeyboardTheme.NIGHT.equals(keyboardTheme.name));
    }
    final boolean handwritingIsEnabled = TypingSettings.loadBoolean(
      applicationContext,
      TypingSettings.HANDWRITING_ON_KEYBOARD_PREFERENCE_KEY,
      TypingSettings.HANDWRITING_ON_KEYBOARD_DEFAULT
    );
    if (!handwritingIsEnabled && inputContainer.isHandwritingEnabled())
    {
      flushHandwriting(getCurrentInputConnection());
    }
    inputContainer.setHandwritingEnabled(handwritingIsEnabled);
    inputContainer.setHandwritingFullScreenPreferred(
      TypingSettings.loadBoolean(
        applicationContext,
        TypingSettings.HANDWRITING_FULL_SCREEN_PREFERENCE_KEY,
        TypingSettings.HANDWRITING_FULL_SCREEN_DEFAULT
      )
    );
    strokeToleranceIsEnabled =
            TypingSettings.loadBoolean(
              applicationContext,
              TypingSettings.STROKE_TOLERANCE_PREFERENCE_KEY,
              TypingSettings.STROKE_TOLERANCE_DEFAULT
            );

    final Keyboard currentKeyboard = inputContainer.getKeyboard();
    final Keyboard preferredStrokesKeyboard = preferredStrokesKeyboard();
    if (currentKeyboard == handwritingKeyboard
          || isMainStrokesKeyboard(currentKeyboard) && currentKeyboard != preferredStrokesKeyboard)
    {
      inputContainer.setKeyboard(preferredStrokesKeyboard);
    }

    if (quickPhrasesAreShowing)
    {
      quickPhrasesAreShowing = false;
      setCandidates(Collections.emptyList());
    }
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private static boolean isCommentLine(final String line)
  {
    return line.startsWith("#") || line.isEmpty();
  }

  @SuppressWarnings("SameParameterValue")
  private void loadSequenceCharactersData(
    final String sequenceCharactersFileName,
    final Map<String, String> charactersFromStrokeDigitSequence
  )
  {
    final long startMilliseconds = System.currentTimeMillis();

    try
    {
      final InputStream inputStream = getAssets().open(sequenceCharactersFileName);
      final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

      String line;
      while ((line = bufferedReader.readLine()) != null)
      {
        if (!isCommentLine(line))
        {
          final String[] sunderedLineArray = Stringy.sunder(line, "\t");
          final String strokeDigitSequence = sunderedLineArray[0];
          final String characters = sunderedLineArray[1];
          charactersFromStrokeDigitSequence.put(strokeDigitSequence, characters);
        }
      }
    }
    catch (IOException exception)
    {
      Log.e(LOG_TAG, "loadSequenceCharactersData failed", exception);
    }

    final long endMilliseconds = System.currentTimeMillis();
    sendLoadingTimeLog(sequenceCharactersFileName, startMilliseconds, endMilliseconds);
  }

  private void loadCharactersData(final String charactersFileName, final Set<Integer> codePoints)
  {
    final long startMilliseconds = System.currentTimeMillis();

    try
    {
      final InputStream inputStream = getAssets().open(charactersFileName);
      final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

      String line;
      while ((line = bufferedReader.readLine()) != null)
      {
        if (!isCommentLine(line))
        {
          codePoints.add(Stringy.getFirstCodePoint(line));
        }
      }
    }
    catch (IOException exception)
    {
      Log.e(LOG_TAG, "loadCharactersData failed", exception);
    }

    final long endMilliseconds = System.currentTimeMillis();
    sendLoadingTimeLog(charactersFileName, startMilliseconds, endMilliseconds);
  }

  private void loadRankingData(
    final String rankingFileName,
    final Map<Integer, Integer> sortingRankFromCodePoint,
    final Set<Integer> commonCodePoints
  )
  {
    final long startMilliseconds = System.currentTimeMillis();

    try
    {
      final InputStream inputStream = getAssets().open(rankingFileName);
      final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

      int currentRank = 0;
      String line;
      while ((line = bufferedReader.readLine()) != null)
      {
        if (!isCommentLine(line))
        {
          for (final int codePoint : Stringy.toCodePointList(line))
          {
            currentRank++;
            if (!sortingRankFromCodePoint.containsKey(codePoint))
            {
              sortingRankFromCodePoint.put(codePoint, currentRank);
            }
            if (currentRank < LAG_PREVENTION_CODE_POINT_COUNT)
            {
              commonCodePoints.add(codePoint);
            }
          }
        }
      }
    }
    catch (IOException exception)
    {
      Log.e(LOG_TAG, "loadRankingData failed", exception);
    }

    final long endMilliseconds = System.currentTimeMillis();
    sendLoadingTimeLog(rankingFileName, startMilliseconds, endMilliseconds);
  }

  private void loadPhrasesData(final String phrasesFileName, final Set<String> phrases)
  {
    final long startMilliseconds = System.currentTimeMillis();

    try
    {
      final InputStream inputStream = getAssets().open(phrasesFileName);
      final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

      String line;
      while ((line = bufferedReader.readLine()) != null)
      {
        if (!isCommentLine(line))
        {
          phrases.add(line);
        }
      }
    }
    catch (IOException exception)
    {
      Log.e(LOG_TAG, "loadPhrasesData failed", exception);
    }

    final long endMilliseconds = System.currentTimeMillis();
    sendLoadingTimeLog(phrasesFileName, startMilliseconds, endMilliseconds);
  }

  private void sendLoadingTimeLog(final String fileName, final long startMilliseconds, final long endMilliseconds)
  {
    if (BuildConfig.DEBUG)
    {
      final long durationMilliseconds = endMilliseconds - startMilliseconds;
      Log.d(LOG_TAG, String.format("Loaded %s in %d ms", fileName, durationMilliseconds));
    }
  }

  @Override
  public void onStartInput(final EditorInfo editorInfo, final boolean isRestarting)
  {
    stopVoiceInput(); // even a restart must not receive an earlier editor's asynchronous result
    super.onStartInput(editorInfo, isRestarting);

    final int inputOptionsBits = editorInfo.imeOptions;
    inputActionsBits = inputOptionsBits & EditorInfo.IME_MASK_ACTION;
    enterKeyHasAction = (inputOptionsBits & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0;

    final int inputTypeBits = editorInfo.inputType;
    final int inputClassBits = inputTypeBits & InputType.TYPE_MASK_CLASS;
    final int inputVariationBits = inputTypeBits & InputType.TYPE_MASK_VARIATION;

    switch (inputClassBits)
    {
      case InputType.TYPE_CLASS_NUMBER:
        inputIsPassword = inputVariationBits == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        break;

      case InputType.TYPE_CLASS_TEXT:
        switch (inputVariationBits)
        {
          case InputType.TYPE_TEXT_VARIATION_PASSWORD:
          case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
          case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
            inputIsPassword = true;
            break;

          default:
            inputIsPassword = false;
        }
        break;

      default:
        inputIsPassword = false;
    }
  }

  @Override
  public void onStartInputView(final EditorInfo editorInfo, final boolean isRestarting)
  {
    super.onStartInputView(editorInfo, isRestarting);

    applyTypingSettings();

    inputContainer.post( // await layout so that width is available
      () ->
      {
        final int inputContainerWidth = inputContainer.getWidth();
        for (final Keyboard keyboard : keyboards)
        {
          keyboard.correctKeyboardWidth(inputContainerWidth); // needed in API level 35+ due to edge-to-edge breakage
        }
        inputContainer.redrawKeyboard(); // key preview plane initialisation is buggy in landscape mode
      }
    );

    for (final Keyboard keyboard : keyboards)
    {
      keyboard.adjustKeyboardHeight();
    }
    inputContainer.updateHeight();

    updateFullscreenMode(); // needed in API level 31+ so that fullscreen works after rotate whilst keyboard showing
    final boolean isFullscreen = isFullscreenMode();
    inputContainer.setPopupRecessLayout(isFullscreen);
    inputContainer.setBackground(isFullscreen);

    inputContainer.setStrokeDigitSequence(strokeDigitSequence);
    inputContainer.setCandidates(candidates, firstCandidateIsCommittedBySpace());

    setEnterKeyDisplayText();

    if (inputContainer.getKeyboard() == handwritingKeyboard)
    {
      ensureHandwritingRecogniser();
    }
    updateHandwritingHint();
    if (inputContainer.getKeyboard() == voiceKeyboard)
    {
      ensureVoiceRecogniser();
    }
    updateVoiceStatus();
  }

  @Override
  public void onFinishInput()
  {
    stopVoiceInput();
    super.onFinishInput();
  }

  @Override
  public void onFinishInputView(final boolean finishingInput)
  {
    stopVoiceInput();
    if (inputContainer != null)
    {
      final InputConnection inputConnection = getCurrentInputConnection();
      if (inputConnection != null)
      {
        flushHandwriting(inputConnection);
      }
      else
      {
        handwritingHandler.removeCallbacks(handwritingRecognitionRunnable);
        inputContainer.getHandwritingInk().clear();
        handwritingIsComposing = false;
      }
    }
    super.onFinishInputView(finishingInput);
  }

  @Override
  public void onUpdateSelection(
    final int oldSelectionStart,
    final int oldSelectionEnd,
    final int newSelectionStart,
    final int newSelectionEnd,
    final int composingStart,
    final int composingEnd
  )
  {
    super.onUpdateSelection(
      oldSelectionStart, oldSelectionEnd, newSelectionStart, newSelectionEnd, composingStart, composingEnd
    );
    if (handwritingIsComposing && composingStart < 0 && composingEnd < 0) // the editor ended the composing text
    {
      handwritingIsComposing = false;
      setCandidates(Collections.emptyList());
    }
  }

  @Override
  public void onDestroy()
  {
    voiceServiceDestroyed = true;
    stopVoiceInput();
    handwritingHandler.removeCallbacks(voiceRecogniserRelease);
    releaseVoiceRecogniser();
    handwritingHandler.removeCallbacks(handwritingRecognitionRunnable);
    if (handwritingRecogniser != null)
    {
      handwritingRecogniser.close();
      handwritingRecogniser = null;
    }
    super.onDestroy();
  }

  private void setEnterKeyDisplayText()
  {
    final EnterKeyIcon enterIcon = EnterKeyIcon.forEditorAction(inputActionsBits, enterKeyHasAction);
    final String enterKeyDisplayText = getString(enterIcon.displayTextResource);

    for (final Keyboard keyboard : keyboards)
    {
      for (final Key key : keyboard.getKeys())
      {
        if (key.valueText.equals(ENTER_KEY_VALUE_TEXT))
        {
          key.displayText = enterKeyDisplayText;
          key.enterIcon = enterIcon;
        }
      }
    }
    inputContainer.redrawKeyboard();
  }

  @Override
  public void onComputeInsets(final Insets insets)
  {
    super.onComputeInsets(insets);
    if (inputContainer != null) // check needed in API level 30
    {
      final int candidatesViewTop = inputContainer.getCandidatesViewTop();
      insets.visibleTopInsets = candidatesViewTop;
      insets.contentTopInsets = candidatesViewTop;
      if (inputContainer.isHandwritingFullScreen())
      {
        insets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME;
      }
    }
  }

  @Override
  public boolean onEvaluateInputViewShown()
  {
    super.onEvaluateInputViewShown();
    return true; // override needed in API level 36
  }

  @Override
  public void onCandidate(final String candidate)
  {
    final InputConnection inputConnection = getCurrentInputConnection();
    if (inputConnection == null)
    {
      return;
    }

    quickPhrasesAreShowing = false;
    handwritingIsComposing = false;
    inputConnection.commitText(candidate, 1); // also replaces handwriting composing text
    setStrokeDigitSequence("");
    setPhraseCompletionCandidates(inputConnection);
  }

  /*
    Show the recorded stroke order(s) of a candidate in the stroke sequence bar, until the next key.
  */
  @Override
  public void onCandidateLongPress(final String candidate)
  {
    final Context localisedContext = AppLanguage.wrap(this);
    final List<String> parts = new ArrayList<>();

    final List<String> characters = Stringy.toCharacterList(candidate);
    for (final String character : characters.subList(0, Math.min(characters.size(), MAX_STROKE_ORDER_CHARACTERS)))
    {
      final List<String> sequences = StrokeMatcher.sequencesForCharacter(charactersFromStrokeDigitSequence, character);
      if (sequences.isEmpty())
      {
        continue;
      }

      final List<String> strokeSequences = new ArrayList<>();
      for (final String sequence : sequences.subList(0, Math.min(sequences.size(), MAX_STROKE_ORDER_VARIANTS)))
      {
        strokeSequences.add(StrokeSequenceBar.toStrokeText(localisedContext, sequence));
      }
      parts.add(
        localisedContext.getString(R.string.format__stroke_order, character, String.join(" / ", strokeSequences))
      );
    }

    if (parts.isEmpty())
    {
      return;
    }

    strokeOrderIsShowing = true;
    inputContainer.showStrokeOrder(String.join("　", parts));
  }

  @Override
  public void onHideKeyboard()
  {
    requestHideSelf(0);
  }

  @Override
  public void onShowHandwriting()
  {
    final Context applicationContext = getApplicationContext();
    if (!TypingSettings.loadBoolean(
          applicationContext,
          TypingSettings.HANDWRITING_ON_KEYBOARD_PREFERENCE_KEY,
          TypingSettings.HANDWRITING_ON_KEYBOARD_DEFAULT
        ))
    {
      return;
    }
    stopVoiceInput();
    flushHandwriting(getCurrentInputConnection());
    if (inputContainer.getKeyboard() == handwritingKeyboard || inputContainer.getKeyboard() == voiceKeyboard)
    {
      inputContainer.setKeyboard(preferredStrokesKeyboard());
    }
    ensureHandwritingRecogniser();
    final boolean fullScreenIsPreferred =
            !TypingSettings.loadBoolean(
              applicationContext,
              TypingSettings.HANDWRITING_FULL_SCREEN_PREFERENCE_KEY,
              TypingSettings.HANDWRITING_FULL_SCREEN_DEFAULT
            );
    TypingSettings.saveBoolean(
      applicationContext,
      TypingSettings.HANDWRITING_FULL_SCREEN_PREFERENCE_KEY,
      fullScreenIsPreferred
    );
    inputContainer.setHandwritingFullScreenPreferred(fullScreenIsPreferred);
    updateHandwritingHint();
  }

  @Override
  public void onShowVoice()
  {
    flushHandwriting(getCurrentInputConnection());
    ensureVoiceRecogniser();
    inputContainer.setKeyboard(voiceKeyboard);
    voiceMessageId = 0;
    updateVoiceStatus();
  }

  @Override
  public void onShowQuickPhrases()
  {
    final Context localisedContext = AppLanguage.wrap(this);
    final List<String> quickPhrases = TypingSettings.loadQuickPhrases(localisedContext);
    if (quickPhrases.isEmpty())
    {
      Toast.makeText(localisedContext, R.string.message__quick_phrases_empty, Toast.LENGTH_LONG).show();
      return;
    }

    quickPhrasesAreShowing = true;
    setCandidates(new ArrayList<>(quickPhrases));
  }

  @Override
  public void onKey(final String valueText)
  {
    final InputConnection inputConnection = getCurrentInputConnection();
    if (inputConnection == null)
    {
      return;
    }

    if (strokeOrderIsShowing)
    {
      strokeOrderIsShowing = false;
      inputContainer.setStrokeDigitSequence(strokeDigitSequence);
    }

    if (quickPhrasesAreShowing)
    {
      quickPhrasesAreShowing = false;
      setCandidates(Collections.emptyList());
      if (valueText.equals(BACKSPACE_VALUE_TEXT))
      {
        return; // backspace just closes the quick phrases
      }
    }

    switch (valueText)
    {
      case STROKE_1_VALUE_TEXT:
      case STROKE_2_VALUE_TEXT:
      case STROKE_3_VALUE_TEXT:
      case STROKE_4_VALUE_TEXT:
      case STROKE_5_VALUE_TEXT:
      case STROKE_WILDCARD_VALUE_TEXT:
        final String strokeDigit = Stringy.removePrefix(STROKE_KEY_VALUE_TEXT_PREFIX, valueText);
        effectStrokeAppend(strokeDigit);
        break;

      case HANDWRITING_PAD_VALUE_TEXT:
      case VOICE_PAD_VALUE_TEXT:
        break;

      case BACKSPACE_VALUE_TEXT:
        effectBackspace(inputConnection);
        break;

      case SWITCH_TO_STROKES_VALUE_TEXT:
      case SWITCH_TO_STROKES_SYMBOLS_1_VALUE_TEXT:
      case SWITCH_TO_STROKES_SYMBOLS_2_VALUE_TEXT:
      case SWITCH_TO_STROKES_SYMBOLS_3_VALUE_TEXT:
      case SWITCH_TO_STROKES_SYMBOLS_COMMON_VALUE_TEXT:
      case SWITCH_TO_STROKES_SYMBOLS_SHAPES_VALUE_TEXT:
      case SWITCH_TO_STROKES_SYMBOLS_MATH_VALUE_TEXT:
      case SWITCH_TO_QWERTY_VALUE_TEXT:
      case SWITCH_TO_QWERTY_SYMBOLS_VALUE_TEXT:
        final String keyboardName = Stringy.removePrefix(SWITCH_KEYBOARD_VALUE_TEXT_PREFIX, valueText);
        effectKeyboardSwitch(keyboardName);
        break;

      case SPACE_BAR_VALUE_TEXT:
        effectSpaceKey(inputConnection);
        break;

      case ENTER_KEY_VALUE_TEXT:
        effectEnterKey(inputConnection);
        break;

      default:
        effectOrdinaryKey(inputConnection, valueText);
    }
  }

  private void effectStrokeAppend(final String strokeDigit)
  {
    if (handwritingIsPending())
    {
      flushHandwriting(getCurrentInputConnection());
    }
    final String newStrokeDigitSequence = strokeDigitSequence + strokeDigit;
    final List<String> newCandidates = computeCandidates(newStrokeDigitSequence);
    if (!newCandidates.isEmpty())
    {
      setStrokeDigitSequence(newStrokeDigitSequence);
      setCandidates(newCandidates);
    }
  }

  private void effectBackspace(final InputConnection inputConnection)
  {
    if (effectHandwritingBackspace(inputConnection))
    {
      return;
    }

    if (!strokeDigitSequence.isEmpty())
    {
      final String newStrokeDigitSequence = Stringy.removeSuffixRegex("(?s).", strokeDigitSequence);
      final List<String> newCandidates = computeCandidates(newStrokeDigitSequence);

      setStrokeDigitSequence(newStrokeDigitSequence);
      setCandidates(newCandidates);

      if (newStrokeDigitSequence.isEmpty())
      {
        setPhraseCompletionCandidates(inputConnection);
      }

      inputContainer.setKeyRepeatIntervalMilliseconds(BACKSPACE_REPEAT_INTERVAL_MILLISECONDS_UTF_8);
    }
    else
    {
      final String upToOneCharacterBeforeCursor = getTextBeforeCursor(inputConnection, 1);

      if (!upToOneCharacterBeforeCursor.isEmpty())
      {
        final CharSequence selection = inputConnection.getSelectedText(0);
        if (TextUtils.isEmpty(selection))
        {
          inputConnection.deleteSurroundingTextInCodePoints(1, 0);
        }
        else
        {
          inputConnection.commitText("", 1);
        }
      }
      else // for apps like Termux
      {
        inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
      }

      setPhraseCompletionCandidates(inputConnection);

      final int nextBackspaceIntervalMilliseconds =
              (Stringy.isAscii(upToOneCharacterBeforeCursor))
                ? BACKSPACE_REPEAT_INTERVAL_MILLISECONDS_ASCII
                : BACKSPACE_REPEAT_INTERVAL_MILLISECONDS_UTF_8;
      inputContainer.setKeyRepeatIntervalMilliseconds(nextBackspaceIntervalMilliseconds);
    }
  }

  private void effectKeyboardSwitch(final String keyboardName)
  {
    flushHandwriting(getCurrentInputConnection());
    stopVoiceInput();
    final Keyboard keyboard =
            (keyboardName.equals(STROKES_KEYBOARD_NAME))
              ? preferredStrokesKeyboard()
              : keyboardFromName.get(keyboardName);
    inputContainer.setKeyboard(keyboard);
  }

  private void effectSpaceKey(final InputConnection inputConnection)
  {
    if (handwritingIsPending())
    {
      flushHandwriting(inputConnection);
      setPhraseCompletionCandidates(inputConnection);
    }
    else if (!strokeDigitSequence.isEmpty())
    {
      onCandidate(getFirstCandidate());
    }
    else
    {
      inputConnection.commitText(" ", 1);
    }
  }

  private void effectEnterKey(final InputConnection inputConnection)
  {
    if (handwritingIsPending())
    {
      flushHandwriting(inputConnection);
      setPhraseCompletionCandidates(inputConnection);
    }
    else if (!strokeDigitSequence.isEmpty())
    {
      onCandidate(getFirstCandidate());
    }
    else if (enterKeyHasAction)
    {
      inputConnection.performEditorAction(inputActionsBits);
    }
    else
    {
      inputConnection.commitText("\n", 1);
    }
  }

  private void effectOrdinaryKey(final InputConnection inputConnection, final String valueText)
  {
    if (!strokeDigitSequence.isEmpty())
    {
      return;
    }
    flushHandwriting(inputConnection); // e.g. a full stop goes after the character just written
    inputConnection.commitText(valueText, 1);
  }

  /*
    Handwriting: start the recogniser (once), and show what the pad can do in its hint.
  */
  private void ensureHandwritingRecogniser()
  {
    if (handwritingRecogniser == null)
    {
      handwritingRecogniser = new HandwritingRecogniser(this, this);
    }
  }

  private void updateHandwritingHint()
  {
    if (inputContainer == null)
    {
      return;
    }
    final int hintId;
    if (handwritingRecogniser == null || !handwritingRecogniser.isReady() && !handwritingRecogniser.hasFailed())
    {
      hintId = R.string.hint__handwriting_loading;
    }
    else if (handwritingRecogniser.hasFailed())
    {
      hintId = R.string.hint__handwriting_unavailable;
    }
    else
    {
      hintId = (inputContainer.isHandwritingFullScreen())
                 ? R.string.hint__handwriting_full_screen
                 : R.string.hint__handwriting_pad;
    }
    inputContainer.setHandwritingHintText(AppLanguage.wrap(this).getString(hintId));
  }

  @Override
  public void onRecogniserStateChanged()
  {
    updateHandwritingHint();
    if (inputContainer != null && !inputContainer.getHandwritingInk().isEmpty())
    {
      recogniseHandwriting(); // ink written while the model was loading
    }
  }

  @Override
  public void onHandwritingStrokeStart()
  {
    ensureHandwritingRecogniser();
    handwritingHandler.removeCallbacks(handwritingRecognitionRunnable);
    quickPhrasesAreShowing = false;
    if (handwritingIsComposing)
    {
      final InputConnection inputConnection = getCurrentInputConnection();
      if (inputConnection != null)
      {
        inputConnection.finishComposingText(); // writing on accepts the previous reading
      }
      handwritingIsComposing = false;
    }
    if (!candidates.isEmpty())
    {
      setCandidates(Collections.emptyList());
    }
  }

  @Override
  public void onHandwritingStrokeEnd()
  {
    handwritingHandler.removeCallbacks(handwritingRecognitionRunnable);
    handwritingHandler.postDelayed(handwritingRecognitionRunnable, handwritingRecognitionDelayMilliseconds());
  }

  private int handwritingRecognitionDelayMilliseconds()
  {
    return (inputContainer != null && inputContainer.isHandwritingFullScreen())
      ? HANDWRITING_FULL_SCREEN_RECOGNITION_DELAY_MILLISECONDS
      : HANDWRITING_RECOGNITION_DELAY_MILLISECONDS;
  }

  private void recogniseHandwriting()
  {
    if (inputContainer == null || handwritingRecogniser == null || !handwritingRecogniser.isReady())
    {
      return; // retried in onRecogniserStateChanged
    }
    final HandwritingInk handwritingInk = inputContainer.getHandwritingInk();
    if (handwritingInk.isEmpty())
    {
      return;
    }
    if (handwritingInk.isTap(getResources().getDimension(R.dimen.size__handwriting_tap)))
    {
      handwritingInk.clear(); // a lone tap (e.g. meant for the app under full-screen handwriting) is not writing
      inputContainer.refreshKeyboard();
      return;
    }
    handwritingRecogniser.recognise(
      handwritingInk.getStrokes(),
      inputContainer.getHandwritingPadHeight(),
      handwritingInk.getVersion(),
      this
    );
  }

  @Override
  public void onHandwritingRecognised(final int inkVersion, final List<String> readings)
  {
    if (inputContainer == null)
    {
      return;
    }
    final HandwritingInk handwritingInk = inputContainer.getHandwritingInk();
    if (handwritingInk.getVersion() != inkVersion)
    {
      return; // more ink since: a newer recognition follows
    }
    handwritingInk.clear();
    inputContainer.refreshKeyboard();

    final InputConnection inputConnection = getCurrentInputConnection();
    if (readings.isEmpty() || inputConnection == null)
    {
      return;
    }
    inputConnection.setComposingText(readings.get(0), 1);
    handwritingIsComposing = true;
    setCandidates(readings);
  }

  private boolean handwritingIsPending()
  {
    return handwritingIsComposing || inputContainer != null && !inputContainer.getHandwritingInk().isEmpty();
  }

  /*
    Accept what is pending on the handwriting pad, in writing order:
    the composing reading, then any ink not yet recognised (recognised at once, best reading).
  */
  private void flushHandwriting(final InputConnection inputConnection)
  {
    handwritingHandler.removeCallbacks(handwritingRecognitionRunnable);
    if (inputContainer == null)
    {
      return;
    }

    if (handwritingIsComposing)
    {
      if (inputConnection != null)
      {
        inputConnection.finishComposingText();
      }
      handwritingIsComposing = false;
      setCandidates(Collections.emptyList());
    }

    final HandwritingInk handwritingInk = inputContainer.getHandwritingInk();
    if (!handwritingInk.isEmpty())
    {
      final List<String> readings =
              (handwritingRecogniser != null)
                ? handwritingRecogniser.recogniseNow(handwritingInk.getStrokes(), inputContainer.getHandwritingPadHeight())
                : Collections.emptyList();
      handwritingInk.clear();
      inputContainer.refreshKeyboard();
      if (!readings.isEmpty() && inputConnection != null)
      {
        inputConnection.commitText(readings.get(0), 1);
      }
    }
  }

  /*
    Voice input: the recogniser loads when the voice keyboard is shown, and is freed a while after it is left.
  */
  private void ensureVoiceRecogniser()
  {
    handwritingHandler.removeCallbacks(voiceRecogniserRelease);
    if (voiceServiceDestroyed)
    {
      return;
    }
    if (voiceRecogniser != null && voiceRecogniser.hasFailed())
    {
      releaseVoiceRecogniser();
    }
    if (voiceRecogniser == null)
    {
      voiceRecogniser = new VoiceRecogniser(this, this);
    }
  }

  private void releaseVoiceRecogniser()
  {
    if (voiceRecogniser != null)
    {
      voiceSessionId++;
      voiceRecogniser.close();
      voiceRecogniser = null;
      voiceIsRecognising = false;
    }
  }

  /*
    Leaving the voice keyboard (or the input): a recording in progress is discarded.
  */
  private void stopVoiceInput()
  {
    voiceSessionId++;
    if (voiceRecorder.isRecording())
    {
      voiceRecorder.stop();
    }
    voicePressStartedRecording = false;
    voicePressStopsRecording = false;
    voiceIsHeldToTalk = false;
    voiceLevel = 0;
    voiceMessageId = 0;
    if (voiceRecogniser != null)
    {
      handwritingHandler.removeCallbacks(voiceRecogniserRelease);
      handwritingHandler.postDelayed(voiceRecogniserRelease, VOICE_MODEL_RELEASE_DELAY_MILLISECONDS);
    }
  }

  private void updateVoiceStatus()
  {
    if (voiceServiceDestroyed || inputContainer == null || !inputContainer.hasVoicePad())
    {
      return;
    }
    final int statusId;
    if (!VoiceRecorder.hasPermission(this))
    {
      statusId = R.string.status__voice_no_permission;
    }
    else if (voiceRecorder.isRecording())
    {
      statusId = (voiceIsHeldToTalk) ? R.string.status__voice_listening_hold : R.string.status__voice_listening;
    }
    else if (voiceIsRecognising)
    {
      statusId = R.string.status__voice_recognising;
    }
    else if (voiceRecogniser == null || !voiceRecogniser.isReady() && !voiceRecogniser.hasFailed())
    {
      statusId = R.string.status__voice_loading;
    }
    else if (voiceRecogniser.hasFailed())
    {
      statusId = R.string.status__voice_unavailable;
    }
    else if (voiceMessageId != 0)
    {
      statusId = voiceMessageId;
    }
    else
    {
      statusId = R.string.status__voice_ready;
    }
    inputContainer.setVoiceStatus(
      AppLanguage.wrap(this).getString(statusId),
      voiceRecorder.isRecording(),
      voiceLevel
    );
  }

  @Override
  public void onVoiceRecogniserStateChanged()
  {
    updateVoiceStatus();
  }

  @Override
  public void onVoicePadDown()
  {
    voiceMessageId = 0;
    if (!VoiceRecorder.hasPermission(this))
    {
      final Intent permissionIntent = new Intent(this, VoicePermissionActivity.class);
      permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(permissionIntent);
      return;
    }
    ensureVoiceRecogniser(); // also cancels idle release and permits a failed model load to retry
    if (voiceIsRecognising || voiceRecogniser == null || !voiceRecogniser.isReady())
    {
      updateVoiceStatus();
      return;
    }
    if (voiceRecorder.isRecording()) // second tap of tap-to-talk
    {
      voicePressStopsRecording = true; // commit on release so ACTION_CANCEL can still discard
      return;
    }
    if (!voiceRecorder.start(this, this))
    {
      voiceMessageId = R.string.status__voice_microphone_busy;
      updateVoiceStatus();
      return;
    }
    voicePressStartedRecording = true;
    voicePressDownMilliseconds = SystemClock.uptimeMillis();
    voiceIsHeldToTalk = true;
    voiceLevel = 0;
    updateVoiceStatus();
  }

  @Override
  public void onVoicePadUp()
  {
    if (voicePressStopsRecording)
    {
      voicePressStopsRecording = false;
      finishVoiceRecording();
      return;
    }
    if (!voicePressStartedRecording)
    {
      return;
    }
    voicePressStartedRecording = false;
    if (SystemClock.uptimeMillis() - voicePressDownMilliseconds >= VOICE_HOLD_MILLISECONDS)
    {
      finishVoiceRecording(); // press-to-talk: letting go finishes
    }
    else
    {
      voiceIsHeldToTalk = false; // a tap: keep listening until the next tap
      updateVoiceStatus();
    }
  }

  @Override
  public void onVoicePadCancel()
  {
    stopVoiceInput(); // Android cancels a gesture; neither toggle nor submit its audio
    updateVoiceStatus();
  }

  @Override
  public void onVoiceLevel(final float level)
  {
    voiceLevel = level;
    if (voiceRecorder.isRecording())
    {
      updateVoiceStatus();
    }
  }

  @Override
  public void onVoiceRecordingError()
  {
    stopVoiceInput();
    voiceMessageId = R.string.status__voice_microphone_busy;
    updateVoiceStatus();
  }

  @Override
  public void onVoiceTimeLimit()
  {
    if (voiceRecorder.isRecording())
    {
      finishVoiceRecording();
    }
  }

  private void finishVoiceRecording()
  {
    final float[] samples = voiceRecorder.stop();
    voicePressStartedRecording = false;
    voicePressStopsRecording = false;
    voiceIsHeldToTalk = false;
    voiceLevel = 0;
    if (samples.length < VOICE_MIN_SAMPLES || voiceRecogniser == null || !voiceRecogniser.isReady())
    {
      voiceMessageId = R.string.status__voice_nothing_heard;
      updateVoiceStatus();
      return;
    }
    final InputConnection inputConnection = getCurrentInputConnection();
    if (inputConnection == null || voiceServiceDestroyed)
    {
      updateVoiceStatus();
      return;
    }
    final long sessionId = voiceSessionId;
    final VoiceRecogniser source = voiceRecogniser;
    voiceIsRecognising = true;
    updateVoiceStatus();
    source.recognise(samples, text -> onVoiceRecognised(text, source, sessionId, inputConnection));
  }

  private void onVoiceRecognised(
    final String text, final VoiceRecogniser source, final long sessionId, final InputConnection inputConnection
  )
  {
    if (voiceServiceDestroyed || source != voiceRecogniser)
    {
      return;
    }
    voiceIsRecognising = false;
    if (sessionId != voiceSessionId || inputConnection != getCurrentInputConnection())
    {
      updateVoiceStatus();
      return;
    }
    if (text.isEmpty())
    {
      voiceMessageId = R.string.status__voice_nothing_heard;
    }
    else
    {
      voiceMessageId = 0;
      inputConnection.commitText(text, 1);
      setPhraseCompletionCandidates(inputConnection);
    }
    updateVoiceStatus();
  }

  /*
    Backspace on the handwriting pad: undo the last stroke, else withdraw the composing reading.
  */
  private boolean effectHandwritingBackspace(final InputConnection inputConnection)
  {
    final HandwritingInk handwritingInk = inputContainer.getHandwritingInk();
    if (!handwritingInk.isEmpty())
    {
      handwritingHandler.removeCallbacks(handwritingRecognitionRunnable);
      handwritingInk.removeLastStroke();
      inputContainer.refreshKeyboard();
      if (!handwritingInk.isEmpty())
      {
        handwritingHandler.postDelayed(handwritingRecognitionRunnable, handwritingRecognitionDelayMilliseconds());
      }
      return true;
    }
    if (handwritingIsComposing)
    {
      inputConnection.setComposingText("", 1);
      inputConnection.finishComposingText();
      handwritingIsComposing = false;
      setPhraseCompletionCandidates(inputConnection);
      return true;
    }
    return false;
  }

  @Override
  public void onLongPress(final String valueText)
  {
    if (valueText.equals(SPACE_BAR_VALUE_TEXT))
    {
      Contexty.showSystemKeyboardChanger(this);
    }
  }

  @Override
  public void onSwipe(final String valueText)
  {
    if (valueText.equals(SPACE_BAR_VALUE_TEXT))
    {
      final Keyboard keyboard = inputContainer.getKeyboard();
      final String keyboardName = nameFromKeyboard.get(keyboard);

      if (keyboardName == null)
      {
        return;
      }
      switch (keyboardName)
      {
        case STROKES_KEYBOARD_NAME:
        case STROKES_LARGE_KEYBOARD_NAME:
        case STROKES_SYMBOLS_1_KEYBOARD_NAME:
        case STROKES_SYMBOLS_2_KEYBOARD_NAME:
        case STROKES_SYMBOLS_3_KEYBOARD_NAME:
        case STROKES_SYMBOLS_COMMON_KEYBOARD_NAME:
        case STROKES_SYMBOLS_SHAPES_KEYBOARD_NAME:
        case STROKES_SYMBOLS_MATH_KEYBOARD_NAME:
        case HANDWRITING_KEYBOARD_NAME:
        case VOICE_KEYBOARD_NAME:
          flushHandwriting(getCurrentInputConnection());
          stopVoiceInput();
          inputContainer.setKeyboard(qwertyKeyboard);
          break;

        case QWERTY_KEYBOARD_NAME:
        case QWERTY_SYMBOLS_KEYBOARD_NAME:
          inputContainer.setKeyboard(preferredStrokesKeyboard());
          break;
      }
    }
  }

  @Override
  public void saveKeyboard(final Keyboard keyboard)
  {
    final String keyboardName = nameFromKeyboard.get(keyboard);
    Contexty.savePreferenceString(
      getApplicationContext(),
      PREFERENCES_FILE_NAME,
      KEYBOARD_NAME_PREFERENCE_KEY,
      keyboardName
    );
  }

  private void setStrokeDigitSequence(final String strokeDigitSequence)
  {
    this.strokeDigitSequence = strokeDigitSequence;
    inputContainer.setStrokeDigitSequence(strokeDigitSequence);
  }

  private void setCandidates(final List<String> candidates)
  {
    this.candidates = candidates;
    inputContainer.setCandidates(candidates, firstCandidateIsCommittedBySpace());
  }

  /*
    Space and enter commit the first candidate only while a stroke sequence is being typed
    or a handwriting reading is composing (not for phrase completions or quick phrases),
    so only then is it emphasised.
  */
  private boolean firstCandidateIsCommittedBySpace()
  {
    return (!strokeDigitSequence.isEmpty() || handwritingIsComposing) && !quickPhrasesAreShowing;
  }

  private void setPhraseCompletionCandidates(final InputConnection inputConnection)
  {
    List<String> phraseCompletionCandidates = computePhraseCompletionCandidates(inputConnection);

    phraseCompletionFirstCodePoints.clear();
    for (final String phraseCompletionCandidate : phraseCompletionCandidates)
    {
      phraseCompletionFirstCodePoints.add(Stringy.getFirstCodePoint(phraseCompletionCandidate));
    }

    setCandidates(phraseCompletionCandidates);
  }

  /*
    Candidate comparator for a string.
  */
  private Comparator<String> candidateComparator(
    final Set<Integer> unpreferredCodePoints,
    final Map<Integer, Integer> sortingRankFromCodePoint,
    final List<Integer> phraseCompletionFirstCodePoints
  )
  {
    return
      Comparator.comparingInt(
        string ->
          computeCandidateRank(
            string,
            unpreferredCodePoints,
            sortingRankFromCodePoint,
            phraseCompletionFirstCodePoints
          )
      );
  }

  /*
    Candidate comparator for a code point.
  */
  private Comparator<Integer> candidateCodePointComparator(
    final Set<Integer> unpreferredCodePoints,
    final Map<Integer, Integer> sortingRankFromCodePoint,
    final List<Integer> phraseCompletionFirstCodePoints
  )
  {
    return
      Comparator.comparingInt(
        codePoint ->
          computeCandidateRank(
            codePoint,
            1,
            unpreferredCodePoints,
            sortingRankFromCodePoint,
            phraseCompletionFirstCodePoints
          )
      );
  }

  /*
    Compute the candidate rank for a string.
  */
  private int computeCandidateRank(
    final String string,
    final Set<Integer> unpreferredCodePoints,
    final Map<Integer, Integer> sortingRankFromCodePoint,
    final List<Integer> phraseCompletionFirstCodePoints
  )
  {
    final int firstCodePoint = Stringy.getFirstCodePoint(string);
    final int stringLength = string.length();

    return
      computeCandidateRank(
        firstCodePoint,
        stringLength,
        unpreferredCodePoints,
        sortingRankFromCodePoint,
        phraseCompletionFirstCodePoints
      );
  }

  /*
    Compute the candidate rank for a string with a given first code point and length.
  */
  private int computeCandidateRank(
    final int firstCodePoint,
    final int stringLength,
    final Set<Integer> unpreferredCodePoints,
    final Map<Integer, Integer> sortingRankFromCodePoint,
    final List<Integer> phraseCompletionFirstCodePoints
  )
  {
    final int coarseRank;
    final int fineRank;
    final int penalty;

    final boolean phraseCompletionsIsEmpty = phraseCompletionFirstCodePoints.isEmpty();
    final int phraseCompletionIndex = phraseCompletionFirstCodePoints.indexOf(firstCodePoint);
    final boolean firstCodePointMatchesPhraseCompletionCandidate = phraseCompletionIndex > 0;

    final Integer sortingRank = sortingRankFromCodePoint.get(firstCodePoint);
    final int cjkBlockPenalty =
            (firstCodePoint < CJK_MAIN_CODE_POINT_START || firstCodePoint > CJK_MAIN_CODE_POINT_END)
              ? RANKING_PENALTY_CJK_EXTENSION
              : 0;
    final int sortingRankNonNull =
            (sortingRank != null)
              ? sortingRank
              : firstCodePoint + cjkBlockPenalty;

    final int lengthPenalty = (stringLength - 1) * RANKING_PENALTY_PER_CHAR;
    final int unpreferredPenalty =
            (unpreferredCodePoints.contains(firstCodePoint))
              ? RANKING_PENALTY_UNPREFERRED
              : 0;

    if (phraseCompletionsIsEmpty)
    {
      coarseRank = Integer.MIN_VALUE;
      fineRank = sortingRankNonNull;
      penalty = lengthPenalty + unpreferredPenalty;
    }
    else if (firstCodePointMatchesPhraseCompletionCandidate)
    {
      coarseRank = Integer.MIN_VALUE;
      fineRank = phraseCompletionIndex;
      penalty = lengthPenalty;
    }
    else
    {
      coarseRank = 0;
      fineRank = sortingRankNonNull;
      penalty = lengthPenalty + unpreferredPenalty;
    }

    return coarseRank + fineRank + penalty;
  }

  private List<String> computeCandidates(final String strokeDigitSequence)
  {
    if (strokeDigitSequence.isEmpty())
    {
      return Collections.emptyList();
    }

    updateCandidateOrderPreference();

    final Set<Integer> prohibitedCodePoints = new HashSet<>();
    for (final Map.Entry<String, Boolean> entry : isAllowedFromCharacters().entrySet())
    {
      final String optionalCharacters = entry.getKey();
      final boolean isAllowed = entry.getValue();
      if (!isAllowed)
      {
        prohibitedCodePoints.addAll(Stringy.toCodePointSet(optionalCharacters));
      }
    }

    // Exact matches (a wildcard stroke matches any one stroke), kept in data order before ranking
    final Set<Integer> exactMatchCodePoints = new LinkedHashSet<>();
    for (final String exactMatchCharacters :
            StrokeMatcher.exactMatchValues(charactersFromStrokeDigitSequence, strokeDigitSequence))
    {
      exactMatchCodePoints.addAll(Stringy.toCodePointList(exactMatchCharacters));
    }
    exactMatchCodePoints.removeAll(prohibitedCodePoints);
    final List<String> exactMatchCandidates = toSortedCandidates(exactMatchCodePoints);

    // Tolerance matches: two neighbouring strokes typed in the wrong order
    final Set<Integer> toleranceMatchCodePoints = new HashSet<>();
    if (strokeToleranceIsEnabled)
    {
      for (final String transposition : StrokeMatcher.adjacentTranspositions(strokeDigitSequence))
      {
        final String transpositionCharacters = charactersFromStrokeDigitSequence.get(transposition);
        if (transpositionCharacters != null)
        {
          toleranceMatchCodePoints.addAll(Stringy.toCodePointSet(transpositionCharacters));
        }
      }
      toleranceMatchCodePoints.removeAll(exactMatchCodePoints);
      toleranceMatchCodePoints.removeAll(prohibitedCodePoints);
    }
    final List<String> toleranceMatchCandidates = toSortedCandidates(toleranceMatchCodePoints);
    if (toleranceMatchCandidates.size() > MAX_TOLERANCE_MATCH_COUNT)
    {
      toleranceMatchCandidates.subList(MAX_TOLERANCE_MATCH_COUNT, toleranceMatchCandidates.size()).clear();
    }

    final Collection<String> prefixMatchCharactersCollection =
            StrokeMatcher.prefixMatchValues(charactersFromStrokeDigitSequence, strokeDigitSequence);

    final Set<Integer> prefixMatchCodePoints = Stringy.toCodePointSet(prefixMatchCharactersCollection);

    prefixMatchCodePoints.removeAll(exactMatchCodePoints);
    prefixMatchCodePoints.removeAll(Stringy.toCodePointSet(toleranceMatchCandidates));
    prefixMatchCodePoints.removeAll(prohibitedCodePoints);
    if (prefixMatchCodePoints.size() > LAG_PREVENTION_CODE_POINT_COUNT)
    {
      prefixMatchCodePoints.retainAll(commonCodePoints);
    }

    final List<Integer> prefixMatchCandidateCodePoints = new ArrayList<>(prefixMatchCodePoints);
    prefixMatchCandidateCodePoints.sort(
      candidateCodePointComparator(unpreferredCodePoints, sortingRankFromCodePoint, phraseCompletionFirstCodePoints)
    );

    final int prefixMatchCount = Math.min(prefixMatchCandidateCodePoints.size(), MAX_PREFIX_MATCH_COUNT);
    final List<String> prefixMatchCandidates = new ArrayList<>();
    for (final int prefixMatchCodePoint : prefixMatchCandidateCodePoints.subList(0, prefixMatchCount))
    {
      prefixMatchCandidates.add(Stringy.toString(prefixMatchCodePoint));
    }

    final List<String> candidates = new ArrayList<>();
    candidates.addAll(exactMatchCandidates);
    candidates.addAll(toleranceMatchCandidates);
    candidates.addAll(prefixMatchCandidates);

    return candidates;
  }

  private List<String> toSortedCandidates(final Set<Integer> codePoints)
  {
    final List<Integer> sortedCodePoints = new ArrayList<>(codePoints);
    sortedCodePoints.sort(
      candidateCodePointComparator(unpreferredCodePoints, sortingRankFromCodePoint, phraseCompletionFirstCodePoints)
    );

    final List<String> candidates = new ArrayList<>();
    for (final int codePoint : sortedCodePoints)
    {
      candidates.add(Stringy.toString(codePoint));
    }

    return candidates;
  }

  private String getFirstCandidate()
  {
    try
    {
      return candidates.get(0);
    }
    catch (IndexOutOfBoundsException exception)
    {
      return "";
    }
  }

  /*
    Compute the phrase completion candidate list.
    Longer matches with the text before the cursor are ranked earlier.
  */
  private List<String> computePhraseCompletionCandidates(final InputConnection inputConnection)
  {
    updateCandidateOrderPreference();

    final List<String> phraseCompletionCandidates = new ArrayList<>();

    for (
      String phrasePrefix = getTextBeforeCursor(inputConnection, MAX_PHRASE_LENGTH - 1);
      !phrasePrefix.isEmpty();
      phrasePrefix = Stringy.removePrefixRegex("(?s).", phrasePrefix)
    )
    {
      final Set<String> prefixMatchPhraseCandidates =
              phrases.subSet(
                phrasePrefix, false,
                phrasePrefix + Character.MAX_VALUE, false
              );
      final List<String> prefixMatchPhraseCompletions = new ArrayList<>();

      for (final String phraseCandidate : prefixMatchPhraseCandidates)
      {
        final String phraseCompletion = Stringy.removePrefix(phrasePrefix, phraseCandidate);
        if (!phraseCompletionCandidates.contains(phraseCompletion))
        {
          prefixMatchPhraseCompletions.add(phraseCompletion);
        }
      }
      prefixMatchPhraseCompletions.sort(
        candidateComparator(unpreferredCodePoints, sortingRankFromCodePoint, Collections.emptyList())
      );
      phraseCompletionCandidates.addAll(prefixMatchPhraseCompletions);
    }

    return phraseCompletionCandidates;
  }

  private String getTextBeforeCursor(final InputConnection inputConnection, final int characterCount)
  {
    if (inputIsPassword)
    {
      return ""; // don't read passwords
    }

    final String textBeforeCursor = (String) inputConnection.getTextBeforeCursor(characterCount, 0);

    return Objects.requireNonNullElse(textBeforeCursor, "");
  }

  private void updateCandidateOrderPreference()
  {
    if (shouldPreferTraditional())
    {
      unpreferredCodePoints = codePointsSimplified;
      sortingRankFromCodePoint = sortingRankFromCodePointTraditional;
      commonCodePoints = commonCodePointsTraditional;
      phrases = phrasesTraditional;
    }
    else
    {
      unpreferredCodePoints = codePointsTraditional;
      sortingRankFromCodePoint = sortingRankFromCodePointSimplified;
      commonCodePoints = commonCodePointsSimplified;
      phrases = phrasesSimplified;
    }
  }

  private boolean shouldPreferTraditional()
  {
    final String savedCandidateOrderPreference =
            MainActivity.loadSavedCandidateOrderPreference(getApplicationContext());
    return MainActivity.isTraditionalPreferred(savedCandidateOrderPreference);
  }

  private Map<String, Boolean> isAllowedFromCharacters()
  {
    return MainActivity.loadSavedOptionalCandidatesPreferences(getApplicationContext());
  }
}
