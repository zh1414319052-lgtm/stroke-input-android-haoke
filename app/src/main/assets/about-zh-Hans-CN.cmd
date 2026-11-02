< links.cmdr
< lang.cmdr
< typography.cmdr

OrdinaryDictionaryReplacement: #.boilerplate-properties-override
- queue_position: BEFORE #boilerplate-properties
- apply_mode: SIMULTANEOUS
* %lang --> zh-Hans-CN
* %title --> 关于

%%%

@LINK_DEFINITIONS

# 笔画输入法 (\stroke-input-version)

----
__[笔画输入法]__是自由开源软件，而恕无担保。
----
----{.notice}
©~\stroke-input-years {Conway} <br>
以 {GPL-3.0-only} 授权，见[gpl-3]。 <br>
----
----
当中含有从废类 [`Keyboard.java`]、[`KeyboardView.java`] 所抄的字节，即：
----
----{.notice}
©~2020 {Android} 开源项目 <br>
以 {Apache-2.0} 授权，见[apache-2]。 <br>
----


## 浩科优化版与上游来源

----
本软件为基于 [Conway 的 Stroke Input Method（笔画输入法）](https://github.com/stroke-input/stroke-input-android) v1.7.0 二次优化的浩科优化版 1.7.0-haoke.13。
原作者版权及 GPL-3.0-only 许可继续保留；浩科修改包括设置与键盘主题、字体与控件适配、离线手写/语音和签名版本检查。
[本优化版完整源码](https://github.com/zh1414319052-lgtm/stroke-input-android-haoke)已公开；[下载请前往浩科工具站](https://haoke.org/software/abc5c0fe8b0eab06?platform=android)。
界面字体使用 Noto Sans SC 静态 400/700 字重，遵循 [SIL OFL 1.1](licenses/NotoSansSC-OFL.txt)。
内置模型、运行库及原有键盘字体保留各自许可。
----


----
当前浩科版本号可在设置页“检查浩科版更新”处查看。
输入、手写和语音识别仍在本机完成，仅设置页处于前台时通过浩科网站检查签名更新，
不发送输入内容或录音，不自动安装。详见内置[隐私政策](privacy-zh.html)。
----

## 建件所倚靠的东西

### [Material Components For Android] (\material-version)
----{.notice}
©~\material-years {Android} 开源项目 <br>
以 {Apache-2.0} 授权，见[apache-2]。 <br>
----

### [ONNX Runtime] (\onnxruntime-version)
----{.notice}
©~{Microsoft Corporation} <br>
以 {MIT} 授权，见[mit]。 <br>
----


### Bouncy Castle (v1.86)
----{.notice}
©~The Legion of the Bouncy Castle，以 [MIT 许可证](licenses/BOUNCY-CASTLE-LICENSE.txt)授权，
仅用于验证版本更新签名。
----

## 手写识别模型

### [PP-OCRv6] 文字识别 small 模型 ({PaddleOCR} \paddleocr-version)
----{.notice}
©~{PaddlePaddle Authors} <br>
以 {Apache-2.0} 授权，见[apache-2]。 <br>
----
----
{ONNX} 格式由 [RapidOCR] (\rapidocr-version) 转换：
----
----{.notice}
©~2021 {RapidOCR Authors} <br>
以 {Apache-2.0} 授权，见[apache-2]。 <br>
----
----
手写识别完全在本机运行，笔迹和文字不会离开手机。
----


## 语音识别模型和运行库

### [SenseVoice-Small-Yue] (\sensevoice-yue-version)
----{.notice}
粤语微调：{ASLP-lab / WenetSpeech-Yue} 贡献者，以 {Apache-2.0} 授权。 <br>
基础模型：[SenseVoice-Small]，©~2023--2028 阿里巴巴集团，
采用 [funasr-model-license]。 <br>
模型保留各自的许可条款，不随本软件改授其他许可证。
----

### [sherpa-onnx] (\sherpa-onnx-version)
----{.notice}
©~{sherpa-onnx} 贡献者，以 [voice-apache-license] 授权。 <br>
随包提供的 {WSYue} 模型来自 2025-09-09 的 {ONNX/int8} 转换包，文件未再修改。
来源、校验值和许可详情见 [voice-notice]。
----
----
语音识别完全在本机运行，首次使用才申请麦克风权限。
录音只在内存中用于识别，不保存也不上传。
----


## 笔画输入资料

### [Conway Stroke Data] (\stroke-data-version)

#### `sequence-characters.txt`
----{.notice}
©~\sequence-characters-years {Conway} <br>
以 {CC-BY-4.0} 授权，见[cc-by-4]。 <br>
----

####
  `characters-traditional.txt` <br>
  `characters-simplified.txt` <br>
  `phrases-traditional.txt` <br>
  `phrases-simplified.txt` <br>
  `ranking-traditional.txt` <br>
  `ranking-simplified.txt` <br>

----{.notice}
献以为公，见[cc0]。
----


## 键盘字体

### [Stroke Input Font] (\stroke-font-version)
----{.notice}
©~\stroke-font-years {Conway} <br>
以 {GPL-3.0-only} 授权，见[gpl-3]。 <br>
----
----
修改自 [2015 版 {Noto Sans CJK TC}]，即：
----
----{.notice}
©~2015 谷歌、等 <br>
以 {Apache-2.0} 授权，见[apache-2]。 <br>
----


<footer>
  此页的 [CMD] 源码： [cmd-source]
</footer>
