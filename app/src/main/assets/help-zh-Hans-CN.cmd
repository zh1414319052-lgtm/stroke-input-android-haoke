< links.cmdr
< keys.cmdr
< typography.cmdr

OrdinaryDictionaryReplacement: #.boilerplate-properties-override
- queue_position: BEFORE #boilerplate-properties
- apply_mode: SIMULTANEOUS
* %lang --> zh-Hans-CN
* %title --> 说明

%%%

@LINK_DEFINITIONS

# %title


## 笔画

''''
|^
//
  ; 键
  ; 笔画
|:
//
  , <kbd class="stroke-key">\stroke-1</kbd>
  , 横、提
//
  , <kbd class="stroke-key">\stroke-2</kbd>
  , 竖、竖钩
//
  , <kbd class="stroke-key">\stroke-3</kbd>
  , 撇
//
  , <kbd class="stroke-key">\stroke-4</kbd>
  , 捺、点
//
  , <kbd class="stroke-key">\stroke-5</kbd>
  , 折、弯
''''


## 例子

''''
|^
//
  ; 字
  ; 笔顺
|:
//
  , 天
  ,{.stroke-key}
    \stroke-1\stroke-1\stroke-3\stroke-4
//
  , 下
  ,{.stroke-key}
    \stroke-1\stroke-2\stroke-4
//
  , 为
  ,{.stroke-key}
    \stroke-4\stroke-3\stroke-5\stroke-4
//
  , 公
  ,{.stroke-key}
    \stroke-3\stroke-4\stroke-5\stroke-4
''''


## 杂项

''''
|^
//
  ; 功能
  ; 动作
|:
//
  , 收回键盘
  , 用系统返回按钮（或返回手势）
//
  , 切换语言
  , 左右滑动 \space-bar-zh
//
  , 切换到其他键盘
  , 长按 \space-bar-zh
''''


## 浩科版新增

''''
|^
//
  ; 功能
  ; 动作
|:
//
  , 记不清某一笔
  , 用 <kbd class="stroke-key">通配</kbd> 代替这一笔
//
  , 相邻两笔顺序写反
  , 开启“笔顺容错”后照样出字
//
  , 查看某字的笔顺
  , 长按该候选字
//
  , 输入常用语
  , 没有候选字时，点键盘上方的“常用语”
//
  , 收起键盘
  , 点键盘上方的“收起键盘”
//
  , 大按键布局、候选字大小、按键振动和按键音
  , 在本软件的设置页调整
//
  , 换界面主题（宣纸朱印、晴空大字、黛青夜色）
  , 在本软件设置页的“界面主题”中选择，键盘同时换色
//
  , 输入更多符号（——、……、①、★、→、±、㎡ 等）
  , 点 <kbd>（）：</kbd> 进入符号键盘，点左下的 <kbd>1/6</kbd> 翻页
//
  , 手写输入（离线识别）
  , 在设置页打开“键盘区域手写”后，短按键位仍正常输入；手指在普通按键区连续滑动超过系统触摸阈值后才开始写字，可以连写几个字。空格横滑、退格连删和长按原功能保留。
//
  , 切换全屏手写
  , 没有候选字时点键盘上方的“全屏”，可在整个屏幕上写；再点“半屏”回到键盘区域。<kbd>⌫</kbd> 会先撤销最后一笔。关闭“键盘区域手写”会同时停用键盘区和全屏手写。
//
  , 语音输入（离线识别）
  , 点键盘上方的“语音”，首次使用时允许麦克风权限。点说话区开始，再点一次结束；也可按住说话，松开结束。每次最多 30 秒，离开语音键盘会取消本次录音。录音只放在内存中，不保存也不上传。自动识别语言，模型支持普通话、粤语、英语、日语和韩语。发送前请检查识别文字。
''''


<footer>
  此页的 [CMD] 源码： [cmd-source]
</footer>
