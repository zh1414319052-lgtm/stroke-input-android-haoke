< links.cmdr
< typography.cmdr

OrdinaryDictionaryReplacement: #.boilerplate-properties-override
- queue_position: BEFORE #boilerplate-properties
- apply_mode: SIMULTANEOUS
* %title --> About

%%%

@LINK_DEFINITIONS

# Stroke Input Method (\stroke-input-version)

----
__[Stroke Input Method]__ is free and open-source software
with ABSOLUTELY NO WARRANTY.
----
----{.notice}
©~\stroke-input-years Conway <br>
Licensed under GPL-3.0-only, see [gpl-3]. <br>
----
----
It contains bytes copied from the deprecated classes
[`Keyboard.java`] and [`KeyboardView.java`],
which are:
----
----{.notice}
©~2020 The Android Open Source Project <br>
Licensed under Apache-2.0, see [apache-2]. <br>
----


## Haoke optimized edition and upstream attribution

----
This is the Haoke optimized edition 1.7.0-haoke.13, based on [Conway's Stroke Input Method](https://github.com/stroke-input/stroke-input-android) v1.7.0.
The original copyright and GPL-3.0-only license are retained.
Haoke changes cover themed settings/keyboards, font and control adaptation, offline handwriting/speech and signed version checks.
[Source for this optimized edition](https://github.com/zh1414319052-lgtm/stroke-input-android-haoke) is public. [Download from Haoke](https://haoke.org/software/abc5c0fe8b0eab06?platform=android).
The interface uses static Noto Sans SC weights 400/700 under [SIL OFL 1.1](licenses/NotoSansSC-OFL.txt).
Bundled models, runtimes and the original keyboard font retain their separate licenses.
----


----
The current Haoke version appears under "Check for Haoke updates" in settings.
Input, handwriting and speech recognition remain on this device.
Only signed version checks connect to the Haoke website, from the foreground settings screen.
No input or audio is sent. Updates are never installed automatically.
See the bundled [privacy policy](privacy.html).
----

## Build dependencies

### [Material Components For Android] (\material-version)
----{.notice}
©~\material-years The Android Open Source Project <br>
Licensed under Apache-2.0, see [apache-2]. <br>
----

### [ONNX Runtime] (\onnxruntime-version)
----{.notice}
©~Microsoft Corporation <br>
Licensed under MIT, see [mit]. <br>
----


### Bouncy Castle (v1.86)
----{.notice}
©~The Legion of the Bouncy Castle.
Licensed under the [MIT licence](licenses/BOUNCY-CASTLE-LICENSE.txt).
Used only to verify signed version updates.
----

## Handwriting recognition model

### [PP-OCRv6] text recognition, small (PaddleOCR \paddleocr-version)
----{.notice}
©~PaddlePaddle Authors <br>
Licensed under Apache-2.0, see [apache-2]. <br>
----
----
Converted to ONNX by [RapidOCR] (\rapidocr-version):
----
----{.notice}
©~2021 RapidOCR Authors <br>
Licensed under Apache-2.0, see [apache-2]. <br>
----
----
Handwriting is recognised entirely on the device; no ink or text leaves the phone.
----


## Voice recognition model and runtime

### [SenseVoice-Small-Yue] (\sensevoice-yue-version)
----{.notice}
Cantonese fine-tuning: ASLP-lab / WenetSpeech-Yue contributors, Apache-2.0. <br>
Base model: [SenseVoice-Small], ©~2023--2028 Alibaba Group,
under [funasr-model-license]. <br>
The model keeps its own licence terms; it is not relicensed as part of this app.
----

### [sherpa-onnx] (\sherpa-onnx-version)
----{.notice}
©~sherpa-onnx contributors; licensed under [voice-apache-license]. <br>
The 2025-09-09 ONNX/int8 conversion of the WSYue model is bundled unchanged.
Sources, checksums and licence details: [voice-notice].
----
----
Speech recognition runs entirely on the device. Microphone permission is requested on first use.
Audio stays in memory for recognition and is neither saved nor uploaded.
----


## Stroke input data

### [Conway Stroke Data] (\stroke-data-version)

#### `sequence-characters.txt`
----{.notice}
©~\sequence-characters-years Conway <br>
Licensed under CC-BY-4.0, see [cc-by-4]. <br>
----

####
  `characters-traditional.txt` <br>
  `characters-simplified.txt` <br>
  `phrases-traditional.txt` <br>
  `phrases-simplified.txt` <br>
  `ranking-traditional.txt` <br>
  `ranking-simplified.txt` <br>

----{.notice}
Released into the Public Domain, see [cc0].
----


## Keyboard font

### [Stroke Input Font] (\stroke-font-version)

----{.notice}
©~\stroke-font-years Conway <br>
Licensed under GPL-3.0-only, see [gpl-3]. <br>
----
----
Modified from a [2015 version of Noto Sans CJK TC],
which is:
----
----{.notice}
©~2015 Google and others <br>
Licensed under Apache-2.0, see [apache-2]. <br>
----


<footer>
  This page's [CMD] source: [cmd-source]
</footer>
