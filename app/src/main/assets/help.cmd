< links.cmdr
< keys.cmdr
< typography.cmdr

OrdinaryDictionaryReplacement: #.boilerplate-properties-override
- queue_position: BEFORE #boilerplate-properties
- apply_mode: SIMULTANEOUS
* %title --> Help

%%%

@LINK_DEFINITIONS

# %title


## Strokes

''''
|^
//
  ; Key
  ; Stroke
|:
//
  , <kbd class="stroke-key" lang="zh-Hant">\stroke-1</kbd>
  , horizontal; raise
//
  , <kbd class="stroke-key" lang="zh-Hant">\stroke-2</kbd>
  , vertical; vertical-with-hook
//
  , <kbd class="stroke-key" lang="zh-Hant">\stroke-3</kbd>
  , left-slash
//
  , <kbd class="stroke-key" lang="zh-Hant">\stroke-4</kbd>
  , right-press; dot
//
  , <kbd class="stroke-key" lang="zh-Hant">\stroke-5</kbd>
  , turn; bend
''''


## Examples

''''
|^
//
  ; Character
  ; Stroke sequence
|:{lang=zh-Hant}
//
  , 天
  ,{.stroke-key}
    \stroke-1\stroke-1\stroke-3\stroke-4
//
  , 下
  ,{.stroke-key}
    \stroke-1\stroke-2\stroke-4
//
  , 為
  ,{.stroke-key}
    \stroke-4\stroke-3\stroke-5\stroke-5\stroke-5\
    \stroke-4\stroke-4\stroke-4\stroke-4
//
  , 公
  ,{.stroke-key}
    \stroke-3\stroke-4\stroke-5\stroke-4
''''


## Miscellaneous

''''
|^
//
  ; Function
  ; Action
|:
//
  , Retract keyboard
  , Use system Back Button (or Back Gesture)
//
  , Change language
  , Horizontally swipe \space-bar-en
//
  , Change to other keyboard
  , Long press \space-bar-en
''''


## Haoke additions

''''
|^
//
  ; Function
  ; Action
|:
//
  , Unsure of a stroke
  , Tap <kbd class="stroke-key">＊</kbd> (wildcard) in its place
//
  , Two neighbouring strokes typed the wrong way round
  , Still found when “Stroke-order tolerance” is on
//
  , See a character's stroke order
  , Long press the candidate
//
  , Insert a quick phrase
  , Tap “Phrases” above the keys (shown when there are no candidates)
//
  , Hide the keyboard
  , Tap “Hide keyboard” above the keys
//
  , Large keys, candidate size, key vibration and sound
  , Change them in this app
//
  , Change the theme (Rice paper, Clear sky, Night teal)
  , Pick it under “Theme” in this app; the keyboard follows
//
  , More symbols (——, ……, ①, ★, →, ±, ㎡ and more)
  , Tap <kbd>（）：</kbd> for symbols, then <kbd>1/6</kbd> to turn the page
//
  , Handwriting (recognised offline)
  , Turn on “Handwrite on the keyboard” in settings. A short tap still types the key; sliding across ordinary keys starts ink only after Android's touch threshold. Space-bar swipes, Backspace repeat and long presses keep their original actions.
//
  , Switch to full-screen handwriting
  , With no candidates showing, tap “Full” above the keys to write across the screen; tap “Half” to return to the keyboard area. <kbd>⌫</kbd> first undoes the last stroke. Turning the setting off disables both keyboard-area and full-screen handwriting.
//
  , Voice input (recognised offline)
  , Tap “Voice” above the keys and allow the microphone on first use. Tap the microphone area to start, then tap again to finish; or hold it while speaking and release to finish. Each recording is limited to 30 seconds. Leaving the voice keyboard cancels the current recording. Audio is held only in memory and is neither saved nor uploaded. Language is detected automatically; the model supports Mandarin, Cantonese, English, Japanese and Korean. Check the recognised text before sending it.
''''


<footer>
  This page's [CMD] source: [cmd-source]
</footer>
