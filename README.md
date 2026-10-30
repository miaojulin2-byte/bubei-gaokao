# 不背高考

仿「不背单词」的高考背诵应用：文言实词、古诗文默写、生物三科，离线运行，Android 8.0 以上。

v0.3.0 是重写版。旧版（v0.1.1）的代码在提交 `a8cf597`。

## 学什么

| 科目 | 卡片 | 对标高考 |
|---|---|---|
| 文言实词 | 统编版高中语文五册课内实词 1429 个；高考与广东模拟“词语解说”题 27 道；一词多义辨析 67 道 | 文言文阅读的词语解说题会拿课外词与课内例句比较；翻译题按关键实词给分 |
| 古诗文默写 | 理解性默写 114 道：2021–2026 年高考真题（新高考Ⅰ卷 17 道）、广东模拟 14 道、外省模拟 56 道 | 高考考情境式、开放式默写，不考上下句接龙 |
| 生物 | 人教版 2019 五本教材的填空与问答 1121 张；2022–2024 年广东卷选择题 25 道 | 选择题按广东卷真题练；大题要写教材原话，用填空卡记 |

真题来源都标在卡片左上角。依赖图表才能作答的题没有收。开放题列出参考答案给的全部示例。古诗文答案能在课文库里找到的，答案页附前后文。

## 怎么学

和不背单词一样：

- 首页三科各一页，左右滑动切换（背景视差）。文言是宣纸水墨动画，每天一个必修常考实词，画面按例句语境添景（月、雪、雨、江舟、飞鸟、军旗、城楼、柳、花）；古诗文是与诗句相配的风景照；生物是显微照片或旋转的三维 DNA，配讲同一内容的卡片。点文字看详情。
- 底部三个线条图标：书库、本子、我的，点开整页从底部滑上来，左上角 ⌄ 收起。
- 学习时先看题，选「认识 / 提示一下 / 不认识」（古诗文是「会写 / 写不出」，生物是「会 / 不会」），再看答案；看了答案还能说「记错了」。选择题直接点选项，自动判对错，答对了还能标「其实是蒙的」。
- 忘记的卡片在本组稍后再出现，直到答对。
- 首页往下拉打开搜索，三科一起搜字词、名句、知识点。学习页、详情页右上角的 ☆ 把卡片放进自己建的本子，可以建多个本子、重命名、删除。
- 书库页按教材分册选新学范围（封面取自国家中小学智慧教育平台），调每日新学量；我的页看本周学习日历（点开按月翻）、各科进度，设高考年份（默认 2028）、目标记忆率，导出、恢复或重置学习记录。

## 调度

FSRS-6，按天调度，学习日在凌晨 4 点切换。每张卡每天只有第一次作答进入记忆模型，当天重练只记日志。默认目标记忆率 90%，各科可在 80%–97% 之间调。「长期记忆」指稳定度达到 30 天，只是模型估计，不代表已掌握。

FSRS 数学移植自 [ts-fsrs](https://github.com/open-spaced-repetition/ts-fsrs)（MIT，提交 28410b2），单元测试用上游生成的 4592 组向量逐项核对，见 `third_party/fsrs`。

## 开发

需要 JDK 17 和 Android SDK（platform 35、build-tools 35）。

```sh
./gradlew testDebugUnitTest   # FSRS 向量测试
./gradlew assembleDebug       # 输出 app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

包名 `cn.bubei.gaokao`，与旧版 `cn.local.gaokao` 不同，可以同时安装，互不影响。

### 内容

`app/src/main/assets` 里的 JSON 由 `scripts/prepare_content.py` 生成：

- 教材内容取自旧版冻结的资源：`git archive a8cf597 android/assets | tar -x -C /tmp/legacy`，再运行 `python3 scripts/prepare_content.py /tmp/legacy/android/assets`。
- 真题在 `scripts/` 下手工整理：`dictation_source.json`（古诗文默写）、`wenyan_exam_source.json`（文言词语解说）、`biology_exam_source.json`（生物选择题）。改这几个文件后重新运行脚本即可。
- 首页风景图与三科内容的配对写在脚本的 `SCENES` 里。

代码结构：

- `data/Fsrs.kt` 记忆模型；`data/Store.kt` SQLite 记录、设置、备份；`data/Study.kt` 每日计划与学习会话；`data/Content.kt` 卡片模型与加载。
- `ui/` 下每个页面一个文件：`HomeScreen`、`StudyScreen`、`BooksScreen`、`MeScreen`；`CardViews` 是各类卡片的正反面。

## 还没做

- 2021、2025、2026 年广东卷生物真题：能找到的版本都是图片或付费文档，暂缺。
- 生物大题、文言翻译题。
- 旧版三个应用的学习记录没有导入，新应用从零开始。

## 图片来源

- 教材封面：国家中小学智慧教育平台。
- 生物首页照片均来自 Wikimedia Commons：
  - 藓类叶片细胞中的叶绿体：Des_Callaghan，CC BY-SA 4.0（Bryum capillare leaf cells showing chloroplasts）
  - 培养的神经元（GFP 荧光）：ManuelSchottdorf，CC BY-SA 4.0（GFP Neurons）
  - 血细胞扫描电镜照片：Bruce Wetzel、Harry Schaefer，美国国家癌症研究所，公有领域（SEM blood cells）
  - 红洋葱表皮细胞质壁分离：Trương Minh Khải，CC BY 4.0（Plasmolysis of red onion (Allium cepa) cells）
  - 酿酒酵母（DIC 显微）：Masur，公有领域（S cerevisiae under DIC microscopy）
  - 熊蜂为耧斗菜传粉：Roo72，CC BY-SA 3.0（Bee pollinating Aquilegia vulgaris）
- 文言水墨山水、生物 DNA 双螺旋为程序绘制。

