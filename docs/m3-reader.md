# M3 漫画阅读器 · 交付记录

> 日期：2026-11-13 ｜ 状态：核心阅读闭环已通，强化能力分到 M3.5
> 验收：打开第1卷 → 翻到第2页 → 返回 → 重新打开，自动续读第2页

---

## 1. 已交付

### 书源解析

- `BookSourceFactory`：
  - FOLDER：按 `treeUri + docId` 用 `DocumentsContract` 批量 query 图片子项，构造 `FolderSource`；
  - CBZ / CBR / CB7：优先复用 `cacheDir/import/<fingerprint>.*`，缺失时从 SAF / 单文件 `content://` 复制；
  - PDF：明确报错「PDF 阅读将在 M3.5 接入」，不闪退。
- `BookRecord` 增 `treeUri`，书架查询带出 folder 的 tree URI；
- 单文件导入的 `docId` 为完整 `content://`，书源工厂可直接读取。

### 阅读器

- 路由 `reader/{bookId}`，从书架平铺/书夹页点击进入，宿主 chrome 自动隐藏；
- `ReaderScreen`：
  - **左右分页（RTL/LTR）** + **纵向滚动** 两种模式；
  - 点击左/中/右三区：上一页/显隐 chrome/下一页（RTL 自动镜像）；
  - chrome 3 秒自动收起；返回键先收 chrome，再退出；
  - **沉浸式**：隐藏系统栏、保持常亮，退出恢复；
  - 打开时按 SQLite 进度定位，翻页写回进度（百分比 + read_status）；
  - 失败态：玻璃卡片 + 返回书架。
- `PageLoader`：
  - 按屏幕尺寸计算 `inSampleSize`；
  - Bitmap LRU（最大内存 1/6，封顶 96MB）；
  - 当前页 ±2 预取；翻回已解码页零等待；
  - 关闭时释放 source 与缓存。
- 玻璃 chrome（按 §13）：
  - 悬浮顶栏/底栏：左右 12dp、圆角 28、高 56、StrongBlur + Outline；
  - 顶栏：返回 + 书名 + 页码；底栏：页码 + 进度条 + 模式/方向切换 + 设置；
  - 玻璃快捷面板：底部 16dp、宽 ≤560，阅读模式/方向两行，说明 M3.5 补高级项；
  - 阅读器强制 **深色主题**，黑底上玻璃与文字对比正确；
  - 页面 `pageBackdrop` 只包 pager/正文，chrome 在层外采样。

### 数据

- `progress` 表读写：`queryProgressPage` / `saveProgress`；
- 阅读后 `read_status` 自动置为 READING / FINISHED；
- 续读已实测（见下）。

---

## 2. 验证

| 项 | 结果 |
|---|---|
| 打开 5 本测试库中的第1卷 | 成功，Page 1/4 |
| RTL 左区翻页 | 第 1 → 2 页成功 |
| chrome 显隐 | 点中心显示，3 秒自动收起 |
| 快捷面板 | 齿轮打开，玻璃面板、深色对比正确 |
| 返回 | 顶栏返回回书架 |
| 续读 | 翻到 2 页 → 返回 → 重开，落 2/4（`reader-resume.png`） |
| 构建 | debug 22.49MB / release 1.92MB，R8 通过，lint 0 |

截图：`reader-dark-chrome.png`（玻璃顶/底栏）、`reader-page2.png`（翻页）、`reader-panel2.png`（玻璃面板）、`reader-resume.png`（续读）。

---

## 3. 已知限制（M3.5）

| 项 | 现状 |
|---|---|
| 双页 / 跨页横图 / 封面单页 | 未做，当前单页 |
| 捏合缩放 / 双击缩放 / 拖动 | 未做 |
| 缩略图 / scrubber | 缩略图玻璃面板已做；底部预览 scrubber 未做 |
| 亮度 / 色温边缘手势 | 已做：左缘亮度、右缘色温，玻璃 HUD |
| 音量键翻页 | 未做 |
| 每本阅读设置 / 双页阈值 / 裁白边 / 跨页切割 | 未做 |
| PDF 阅读 | 明确提示不支持 |
| 竖向模式 | 已实现，截图为分页模式 |
| 页面预取性能 | 模拟器验证，真机待测 |

---

## 4. M3.5 增量（2026-11-13）

| 能力 | 实现 |
|---|---|
| 双页模式 | `buildSpreads`：封面单页 + 之后两页一屏；横向 Pager 按 spread 翻页；RTL 时页序反转；横屏自动开启，快捷面板可关 |
| 捏合缩放 | 页面级双指缩放 1~4x，放大后单指拖动并 clamp 到边界；单指未放大时不消费事件，Pager 拖动不受影响 |
| 点击防误触 | 非消费式点击识别（短按 + 位移 < touchSlop），不再用会吞 down 的 `detectTapGestures`，滑动翻页正常 |
| 音量键 | 阅读器持焦，`onPreviewKeyEvent` 拦截 VolumeUp/Down 翻页 |
| 快捷面板 | 新增「双页模式」行 |

验证：双页截图 `m35-double-on.png`（页 2/3 同屏）、音量键 `m35-volume.png`（翻到 4/4）。
捏合缩放手势已编译，尚未用多指自动化实测。

---

## 5. 缩略图与亮度/色温（M3.5 收尾，2026-11-13）

| 能力 | 实现 |
|---|---|
| 缩略图面板 | 底栏列表图标打开；玻璃面板高度按页数自适应（90dp + 行数×150dp，封顶 58% 屏）；5 列网格、当前页高亮边框、点击跳页并关闭；`PageLoader.loadThumb` 单独 220×300 解码缓存（96 张） |
| 亮度手势 | 左缘 18% 竖向拖动调窗口亮度（0.05~1.0），首次以 0.5 为基准；离开阅读器恢复系统亮度 |
| 色温手势 | 右缘 18% 竖向拖动，-1~1 冷暖，叠 18% 上限的暖橙/冷蓝层 |
| 手势共存 | 未进入竖向拖动前不消费事件，横向 Pager 与点击翻页不受影响；HUD 玻璃胶囊 900ms 自动消失 |
| 快捷面板 | 提示改为「亮度左缘 / 色温右缘 / 缩略图在底栏」 |

验证截图：`thumbs3.png`（玻璃缩略图、当前页红框）、`thumb-jump.png`（点第3页跳转成功）、
`edge-bright.png`（亮度 67% HUD）、`edge-warm.png`（色温暖 19% HUD）。

---

## 5.1 M3.5 最终收尾（2026-11-14）

| 能力 | 实现 |
|---|---|
| R3 无损放大 | `BitmapRegionDecoder` 原始页重解码；放大 ≥1.5x 时启用 2x 目标分辨率；独立 zoom cache |
| R5 放大镜 | 长按后拖动，圆角矩形视口，松手关闭；形状（横向 / 竖向）、尺寸（长边 = 0.72/0.86/0.95 页宽）、倍率（1.8/2.0/2.5x）均可在设置页调；横竖同口径（只转 90°，可见面积不变）；四方向避让且绝不遮挡手指，空间不足时等比缩小透镜；贴页边做源窗口对齐补偿 |
| R6 页面重叠 | 条漫模式 0/8/16/24px，用 item offset 重叠；作为技术验证项保留开关 |
| R7 扫描件修复 | 关/轻/强；对比度、白点校正、去黄；按 per-book 偏好持久化 |
| 配置档 | 白天 / 夜间 / 条漫 / 双页；一键应用模式 + 亮度/色温，快捷面板循环 |
| 设置持久化 | `BookReadingPrefs` 增加 repair / pageOverlap，老格式向后兼容 |

模拟器验证：快捷面板新增配置档 / 扫描件修复 / 页面重叠行；PDF 翻页、CBZ 双击缩放、缩略图跳页均通过。  
遗留：R5/R3/R6 的真实多指/长按拖动手感需真机复核；RAR/CBR/7z 仍需真实样本回归。

---

## 6. 打开转场交接与系统栏（2026-11-13 修正）

| 问题 | 修复 |
|---|---|
| 封面→首页硬切 | 封面改为 `Animatable` 淡出 200ms，首页 `pageAlpha` 180ms 淡入 |
| 首页未就绪就撤封面 | `ReaderContent` 首屏 `loader.load(initial)` 完成后回调 `pageReady`，封面保持到首页首帧就绪 |
| 过早撤封面 | 首页就绪后再等 380ms，覆盖共享元素 spring 的主要形变 |
| 封面 Crop → 首页 Fit 跳变 | 用封面宽高算首页 `Fit` 矩形，封面目标改为该居中矩形；首页与该矩形同框 |
| 退出闪半页 | `exiting` 时封面 `snapTo(1f)`，64ms 后再 pop，逆向共享元素有完整源图 |
| 阅读器状态栏不隐藏 | 新增 `LocalReaderActive`：书架沉浸顶栏在阅读器期间不再 show 状态栏；阅读器进入后 0/240/800ms 重复 `hide(systemBars)`，压过转场晚一帧的系统栏改写 |
| 退出后标题压状态栏（需重启才恢复） | 阅读器曾把 `ViewCompat.setOnApplyWindowInsetsListener` 挂在 Compose 根视图上，dispose 置 null 会连带清掉 Compose 自己的 insets 分发；已改为不覆盖根视图监听，退出时下一帧 `show(systemBars)`。复现路径验证通过（`bugfix-back.png`） |

---

## 7. 一镜到底 B 方案（2026-11-14）

目标：让打开/返回动画真正可打断，摆脱 `sharedBounds` 在中断时的 reset/跳变。

| 项 | 实现 |
|---|---|
| 状态机 | `ReaderOpenController`：`Idle / Opening / Visible / Closing`，唯一真源 |
| 进度 | 宿主 `animateFloatAsState` 按 `targetProgress` retarget（Opening 未 ready 时保持 0，ready 后到 1；Closing 回 0） |
| 矩形 | `ReaderOpenRect` 根坐标；书架封面卡 `onGloballyPositioned` 上报 sourceRect，Reader 上报 targetRect |
| overlay | 宿主层 `ReaderOpenOverlay` 只在 `Opening / Closing` 渲染；`Visible` 隐藏，避免封面盖住阅读页 |
| 路由 | Reader 路由和 Library↔Reader 的 enter/exit/pop 全部 `None`，视觉由状态机接管 |
| 打断 | ReaderContent `BackHandler` 优先处理 `openActive`：打开中返回立即反向；切单页/双页 retarget 目标矩形 |
| 失败 | 书源打开失败时 `onOpenFailed -> beginClose("Error")`，overlay 回封面卡后显示错误页 |
| debug | `InkvolOpen` logcat 输出 phase/reason/rect/ready；debug 构建左上角显示 phase/progress/rect |

已知验收状态：打开路径与 `Opening -> Back -> Closing` 已在模拟器日志/抽帧验证；完整观感与真机表现由用户自测。

验证截图：`transition-open-mid.png`（封面从卡片位置放大中）、`transition-open-done.png`（首页与封面同框、无状态栏）。
`assembleRelease` 2.10MB，R8 通过，lint 0。
