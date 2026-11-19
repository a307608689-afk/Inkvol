# M1 导入引擎 · 交付记录

> 日期：2026-11-13 ｜ 状态：核心闭环已通，格式与后台任务分两批收尾
> 验收：模拟器端到端 3 本书（4 页 / 3 页 / 3 页 CBZ）全部入库、封面落地、书架渲染正确

---

## 1. 已交付

### 数据层

- `data/local/InkvolDatabase.kt`：SQLiteOpenHelper，四表
  `libraries / folders / books / book_locations`；书本以内容指纹为主键，`insertWithOnConflict(REPLACE)` 保留首次 `added_at`；`hasBook` 支持增量跳过；`clearAll` 供测试与清除数据。
- `data/model/BookRecord.kt`：书架列表所需字段 + `ScanState`。

### 指纹与排序

- `util/Fingerprint.kt`：`size + 头/中/尾各 64KB` 的 SHA-256（三采样）；文件夹书用排序后的 `(文件名, 大小)` 列表指纹。
- `util/NaturalOrder.kt`：数字段按数值比较，`page_2 < page_10`。

### 内容源

- `data/source/BookContentSource.kt`：
  - `FolderSource`（SAF 文档流，自然序）
  - `ZipSource`（`ZipFile` 随机访问，过滤图片条目）
  - `PdfSource`（`PdfRenderer` 探页数；正文渲染留给 M3）
  - `SourceRules`：图片/归档扩展名、8 字节魔数识别（ZIP/RAR/7Z/PDF）。

### 扫描器

- `data/import/LibraryScanner.kt`：
  - **DocumentsContract 批量 query**（一次 Cursor 取全量元数据），不使用 DocumentFile；
  - 目录语义：含图片目录 = 一本；归档 = 一本；子目录递归，深度上限 3；
  - 增量：指纹已存在则跳过；
  - 封面：首图按 256px 降采样写 `filesDir/covers/<fingerprint>.jpg`；
  - 归档先落 `cacheDir/import/<fingerprint>.<ext>`，再做魔数与页数探测；
  - 单本失败只计数，不中断整次扫描。
- `data/import/ComicInfoParser.kt`：ZIP 内 `ComicInfo.xml` 的 Title/Series/Number/Volume/Writer/Genre。

### 仓储与 UI

- `data/repository/LibraryRepository.kt`：`takePersistableUriPermission`、库/文件夹建档、IO 线程扫描、`books` 与 `scan` 两个 StateFlow。
- `ui/library/LibraryScreen.kt`：SAF 目录选择、扫描进度胶囊、错误条、封面网格（自适应列）、页数/格式角标、空态导入。

---

## 2. 验证结果

| 项 | 结果 |
|---|---|
| 端到端测试 `M1ScanTest` | 通过：3 本，页数 4/3/3，封面全部非空 |
| 目录语义 | `测试库/测试系列/第1卷`、`第2卷`、`单本.cbz` 正确归书 |
| 书架渲染 | `tools/shots/m1-shelf3.png`：3 张封面、标题、页数、格式正确 |
| R8 | `assembleRelease` 通过，release 1.41MB |
| 启动耗时 | 模拟器冷启动 ~11s；同模拟器 SSMH 同样 10.7s 超时，属 debug + swiftshader 环境问题 |

---

## 3. M1.1 格式补齐（已完成）

| 项 | 实现 |
|---|---|
| 7z / CB7 | `SevenZSource`（commons-compress 1.28.0 + XZ 1.12，随机访问 + LZMA2） |
| RAR4 / RAR5 | `RarSource`（junrar 8.1.2，**原生支持 RAR5**，无需 JNI） |
| PDF 封面 | `PdfRenderer` 渲首页 512px 宽，写入封面缓存 |
| 归档缓存 | `cacheDir/import` 封顶 200MB，按 lastModified 淘汰 |
| 扫描接入 | 魔数分支 ZIP/RAR/7Z/PDF 全通，`format` 输出 CBZ/CBR/CB7/PDF |
| 测试样本 | Provider 扩展 `附加.cb7`（2 页）与 `扫描件.pdf`（2 页），端到端 5 本全绿 |

**验证**：`M1ScanTest` 通过（第1卷 4 / 第2卷 3 / 单本 CBZ 3 / 附加 CB7 2 / 扫描件 PDF 2，封面全部落地）；
截图：`tools/shots/m1-shelf5.png`（5 本含 CB7/PDF）、`tools/shots/m1-scrim.png`（底部渐隐修复后）。

**RAR 运行时验证缺口**：本机无 rar 创建工具、WSL 无发行版、公网样本不可达，
RAR4/RAR5 仅完成编译与代码路径验证，待用户提供真实 `.cbr/.rar` 后补一次导入回归。
若 junrar 对某个 RAR5 变体解码失败，扫描器会按单本失败计数，不影响整次扫描。

---

## 4. 已知限制（下一批收尾）

| 项 | 现状 | 计划 |
|---|---|---|
| RAR 真机/样本验证 | 代码路径已通，缺真实归档 | 用户样本回归 |
| 后台扫描 | **已做**：冷启动 WorkManager 唯一任务自动增量扫描（KEEP、电量低延后、指数退避），回到前台刷新书架 | 断点续扫/大库分批仍待做 |
| 增量 diff | 仅按指纹跳过 | 消失/变更标记、离线角标 |
| 系列聚合/多书库 | 平铺展示 | M2 |
| 阅读器 | 点击仅提示 | M3 |
| PDF 阅读 | 仅页数/封面 | M3 接 PdfRenderer |
| 玻璃底栏可读性 | 模拟器无真实模糊，底栏后的封面会透出成色块 | 已加底部渐隐；真机模糊下为正常磨砂，待真机确认 |

---

## 5. 测试资产

- `app/src/androidTest/.../M1ScanTest.kt`：扫描端到端 + 自然序/指纹稳定性。
- `app/src/debug/.../TestLibraryProvider.kt`：模拟真实漫画目录树（4+3 图 + CBZ）。
- `tools/m1-verify.ps1`：安装、启动、截图、抓扫描日志。
