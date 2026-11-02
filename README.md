# 笔画输入法 · 浩科优化版 / Stroke Input Method — Haoke optimized edition

本仓库是基于 [Conway 的 Stroke Input Method 上游项目](https://github.com/stroke-input/stroke-input-android) 的 **Android 浩科优化版**。
上游基线为 v1.7.0（提交 `14d2b368a6bfa64a57d96f059eacc8183631e337`），当前浩科正式版本为 **1.7.0-haoke.13 / 7513**。
保留原作者版权、GPL-3.0-only 许可、原有笔画数据及已确认的应用图标；浩科的修改与上游版本分别标明。

**下载地址：[浩科工具站（haoke.org）](https://haoke.org/software/abc5c0fe8b0eab06?platform=android)**。本仓库用于公开源码与对应版本标签。

This is a downstream **Haoke optimized Android edition** of [Conway's Stroke Input Method](https://github.com/stroke-input/stroke-input-android),
based on upstream v1.7.0 at the commit above. Original attribution and GPL-3.0-only are retained.
Download the Haoke build from [haoke.org](https://haoke.org/software/abc5c0fe8b0eab06?platform=android); this repository provides its corresponding source.

## 浩科修改 / Haoke changes

- 统一三套设置/键盘主题与控件排版，使用 Noto Sans SC 静态 400/700 字重（SIL OFL 1.1）。
- 保留标准/大按键笔画、候选字大小、简繁偏好、键盘高度、常用语与六页中文符号。
- 普通键盘区域直接手写，点按继续输入；支持全屏/半屏切换和独立开关。
- 手写与语音在设备本地识别，录音不保存、不上传；仅设置页的签名版本检查联网，不自动安装更新。
- 启动及键盘图标保持本轮已确认的资源；所有编辑动作保持原有分发语义。

The Haoke changes add consistent themed settings/keyboards, larger controls, local handwriting/speech,
quick phrases, configurable typing aids and signed update checks. Typing, handwriting and audio stay local.
See [privacy](PRIVACY.md), [中文隐私说明](PRIVACY-zh.md) and bundled notices.

## 获取源码与构建 / Source and build

The repository uses **Git LFS** for ONNX models, the bundled AAR and interface fonts. Fetch the actual files before building:

```sh
git clone https://github.com/zh1414319052-lgtm/stroke-input-android-haoke.git
cd stroke-input-android-haoke
git lfs install --local
git lfs pull
```

Use JDK 21, Android SDK/API 37 and build-tools 37.0.0; the Gradle wrapper is included.
Create a local `local.properties` with your own `sdk.dir`, or configure `ANDROID_HOME`.

```sh
./gradlew testDebugUnitTest lintRelease assembleRelease
# Isolated, installable development build:
./gradlew assembleDebug assembleDebugAndroidTest
```

`assembleRelease` creates an unsigned release. The maintainer signs the Haoke distribution with the established
external identity using `tools/sign_release.py`; private keys and passwords are never published.
Debug builds use a separate application ID and cannot overwrite the formal app.

## 许可与归属 / License and attribution

Application code and Stroke Input Font retain GPL-3.0-only. Conway remains the upstream author.
Original help, data attribution and copyright notices are retained below and in the app About page.
Third-party runtimes, Noto Sans SC and model weights retain their own licenses; the voice model includes
the separate FunASR Model License v1.1, not a relicensing under GPL. See `app/src/main/assets/voice/NOTICE.txt`,
`app/src/main/assets/handwriting/NOTICE.txt` and `app/src/main/assets/licenses/`.

![App logo.](app/src/main/res/mipmap-hdpi/icon_launcher.png)

A minimalist Chinese keyboard where you input characters
by typing stroke sequences (e.g. 天 is ㇐㇐㇒㇔).

![Screenshot of the keyboard during stroke input.](stroke-input-screenshot.png)

Uses [Conway Stroke Data].

[Conway Stroke Data]: https://github.com/stroke-input/stroke-input-data


## Features

* Good character support (over 28k characters) including vernacular Cantonese
* User preference for traditional or simplified characters
* No tracking. The Haoke edition requests microphone permission for offline voice
  and Internet permission only for signed Haoke version checks in settings.
* Deterministic candidate generation which doesn't learn user input
* Optional direct handwriting on ordinary keyboard keys: taps type normally,
  while movement beyond Android's touch slop becomes ink. The toolbar switches
  between keyboard-area and full-screen writing.


## 下载与版本 / Downloads and versions

- **浩科优化版 / Haoke optimized edition:** [1.7.0-haoke.13 — haoke.org](https://haoke.org/software/abc5c0fe8b0eab06?platform=android)
- **对应源码 / Corresponding source:** [1.7.0-haoke.13 tag](https://github.com/zh1414319052-lgtm/stroke-input-android-haoke/tree/v1.7.0-haoke.13)
- **上游原项目 / Upstream project:** [stroke-input/stroke-input-android](https://github.com/stroke-input/stroke-input-android)
- Upstream store builds: [F-Droid](https://f-droid.org/en/packages/io.github.yawnoc.strokeinput/) · [Google Play](https://play.google.com/store/apps/details?id=io.github.yawnoc.strokeinput)

- [Privacy Policy (English version)](PRIVACY.md#privacy-policy-english-version)
- [私隱政策（中文版）](PRIVACY-zh.md#私隱政策中文版)


## License

**Copyright 2021–2026 Conway** <br>
Licensed under the GNU General Public License v3.0 (GPL-3.0-only). <br>
This is free software with NO WARRANTY etc. etc., see [LICENSE]. <br>

For detailed copyright information in relation to dependencies,
see [app/src/main/assets/about.html].


## Help (for users)

### Strokes

| Key | Stroke |
| - | - |
| ㇐ | horizontal; raise |
| ㇑ | vertical; vertical-with-hook |
| ㇒ | left-slash |
| ㇔ | right-press; dot |
| ㇖ | turn; bend |

### Examples

| Character | Stroke sequence |
| - | - |
| 天 | ㇐㇐㇒㇔ |
| 下 | ㇐㇑㇔ |
| 為 | ㇔㇒㇖㇖㇖㇔㇔㇔㇔ |
| 公 | ㇒㇔㇖㇔ |

### Miscellaneous

| Function | Action |
| - | - |
| Retract keyboard | Use system Back Button (or Back Gesture) |
| Change language | Horizontally swipe space-bar |
| Change to other keyboard | Long press space-bar |


## Assets (for devs)

### `*.cmd` (CMD)

These are [Conway-Markdown (CMD)] files,
used to compile the HTML files by running `cmd --all`
whilst in the root directory of this repository.

### `*.cmdr` (CMD Rules)

These are files containing CMD Rules used by the CMD files.

### `*.html` (HTML)

These are the actual About and Help files that get served in the app.

### `*.txt` (Text)

These are data files used by the input method.
Taken from Conway Stroke Data ([CC-BY-4.0] / [Public Domain]),
see <<https://github.com/stroke-input/stroke-input-data>>.

### `StrokeInputFont.ttf`

This is the font used for the keyboard.
Taken from Stroke Input Font ([GPL-3.0-only]),
see <<https://github.com/stroke-input/stroke-input-font>>.

### `webview.css`

This is the stylesheet for the HTML files that get served in the app.


[LICENSE]: LICENSE
[GPL-3.0-only]: https://www.gnu.org/licenses/
[CC-BY-4.0]: https://creativecommons.org/licenses/by/4.0/
[Public Domain]: https://creativecommons.org/publicdomain/zero/1.0/
[app/src/main/assets/about.html]:
  https://htmlpreview.github.io/?https://github.com/stroke-input/stroke-input-android/blob/master/app/src/main/assets/about.html
[Conway-Markdown (CMD)]:
  https://github.com/conwaymd/conwaymd
