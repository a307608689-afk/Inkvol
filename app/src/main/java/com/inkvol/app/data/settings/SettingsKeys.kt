package com.inkvol.app.data.settings

/** 设置键单一真源，UI 与仓储共用。 */
object SettingsKeys {
    const val APP_THEME = "app_theme"                // system / light / dark
    const val DYNAMIC_COLOR = "dynamic_color"
    // 自定义主题色（强调色）：值为 ThemeAccent.id，缺省 "vermilion" 与旧版一致。
    // 只接管 primary 一族，背景/卡片/语义色不受影响；动态取色开启时不生效。
    const val THEME_ACCENT = "theme_accent"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    const val LIBRARY_VIEW = "library_view"          // folder / series / flat
    // 系列视图结构（只作用于「系列」视图，书夹/平铺零影响）：
    // hierarchy = 层级（一级文件夹→二级→三级，默认）；flat = 扁平（直接含书的目录各成一系列）。
    // 两种模式共用同一份 folder_path 数据，切换不需要重扫。
    const val LIBRARY_SERIES_MODE = "library_series_mode"
    const val SERIES_MODE_HIERARCHY = "hierarchy"
    const val SERIES_MODE_FLAT = "flat"
    // 文件夹扫描深度：递归层数（缺省 3 = 最多下钻三级目录，与历史硬编码一致）。
    const val LIBRARY_SCAN_DEPTH = "library_scan_depth"
    // 系列路径回填：上次尝试时间（毫秒，字符串存）。失败 24h 后再试，
    // 防止权限失效时每次冷启动都白扫一遍。
    const val SERIES_PATH_BACKFILL_AT = "series_path_backfill_at"
    // 书架全局排序档位（一级平铺/系列、二级书夹/系列页共用一份，SSMH 同款语义）。
    // 缺省 recent + 降序 = 旧版 SQL ORDER BY added_at DESC 的原样行为，升级零感知。
    // 旧 per-target 键 library_sort_<target> 已废弃，残留值无人读取。
    const val LIBRARY_SORT = "library_sort"
    // 书夹视图（一级）书夹卡排序：单独记忆，与书排序（LIBRARY_SORT）互不影响。
    // 缺省 custom = 现状拖拽序（categories.sort_key），升级零感知；
    // 拖拽任何时候可用，拖完自动切回 custom 档（文件管理器同款行为）。
    const val LIBRARY_FOLDER_SORT = "library_folder_sort"
    // 书夹卡排序方向：缺省 "0" 升序（名称 A→Z 自然方向）；方向只作用于
    // name/count 两档，custom（拖拽序）与方向无关。
    const val LIBRARY_FOLDER_SORT_DESC = "library_folder_sort_desc"
    const val FOLDER_SORT_NAME = "name"      // 卡名 A → Z / Z → A
    const val FOLDER_SORT_COUNT = "count"    // 书数少/多在前（与卡片角标同源）
    const val FOLDER_SORT_CUSTOM = "custom"  // 拖拽序（sort_key），不受方向影响

    // 书架展示样式：grid 网格（缺省，现状卡）/ list 列表（仿历史页行卡）。
    // 作用于 书夹/系列/平铺 三视图的一级页（三视图联动，全局一份）；
    // 二级页不受影响。键值沿用历史字符串 "library_folder_view"（初版只作用书夹，
    // 扩展为全局后键值不变 = 已写过的 grid/list 值零迁移直接兼容）。
    const val LIBRARY_SHELF_VIEW = "library_folder_view"
    const val SHELF_VIEW_GRID = "grid"
    const val SHELF_VIEW_LIST = "list"
    // 排序方向（全局一份，一级与二级页共用）：缺省 "1" 降序（SSMH 同款语义）。
    const val LIBRARY_SORT_DESC = "library_sort_desc"
    const val PLUS_ACTION = "import_plus_action"     // ask / folder / file / rescan
    const val READER_OPEN_SPEED = "reader_open_speed" // faster / fast(默认) / default(适中) / slow / slower

    const val VIEW_FOLDER = "folder"
    const val VIEW_SERIES = "series"
    const val VIEW_FLAT = "flat"

    const val SORT_RECENT = "recent"      // 收藏时间（addedAt）
    const val SORT_TITLE = "title"
    const val SORT_SIZE = "size"
    const val SORT_LAST_READ = "last_read" // 最近阅读（lastReadAt）
    const val SORT_PAGES = "pages"         // 页数（pageCount）
    const val SORT_PROGRESS = "progress"   // 阅读进度（percent）

    const val PLUS_ASK = "ask"
    const val PLUS_FOLDER = "folder"
    const val PLUS_FILE = "file"
    const val PLUS_RESCAN = "rescan"

    const val OPEN_SPEED_DEFAULT = "default" // 适中 280ms
    const val OPEN_SPEED_SLOW = "slow"
    const val OPEN_SPEED_SLOWER = "slower"
    const val OPEN_SPEED_FAST = "fast"       // 快 260ms（默认档）
    const val OPEN_SPEED_FASTER = "faster"   // 极快 240ms

    const val READER_BOOK_PREFIX = "reader_book_"

    const val READER_DOUBLE_PAGE = "reader_double_page" // auto / always / off
    const val DOUBLE_PAGE_AUTO = "auto"
    const val DOUBLE_PAGE_ALWAYS = "always"
    const val DOUBLE_PAGE_OFF = "off"

    // 翻页动画：点击 / 按键翻页的过渡；拖拽与跳页不受影响，横读生效（竖读恒瞬时）
    const val READER_PAGE_TRANSITION = "reader_page_transition" // instant / slide / cover
    const val PAGE_TRANSITION_INSTANT = "instant"
    const val PAGE_TRANSITION_SLIDE = "slide" // 默认档：弹簧平移（可打断）
    const val PAGE_TRANSITION_COVER = "cover"

    const val READER_GESTURES_MASTER = "reader_gestures_master"
    const val READER_TAP_PAGING = "reader_tap_paging"
    const val READER_DOUBLE_TAP_ZOOM = "reader_double_tap_zoom"
    const val READER_PINCH_ZOOM = "reader_pinch_zoom"
    const val READER_LONG_PRESS = "reader_long_press" // magnifier / panel / none
    const val READER_EDGE_BRIGHTNESS = "reader_edge_brightness"
    const val READER_EDGE_COLOR_TEMP = "reader_edge_color_temp"
    const val READER_VOLUME_KEYS = "reader_volume_keys"

    const val LONG_PRESS_MAGNIFIER = "magnifier"
    const val LONG_PRESS_QUICK_PANEL = "panel"
    const val LONG_PRESS_NONE = "none"

    // 放大镜：透镜长边 = 当前页宽 × 尺寸比例；镜内可见范围 = 边长 ÷ 倍率
    const val READER_MAGNIFIER_SIZE = "reader_magnifier_size" // compact / standard / wide
    const val READER_MAGNIFIER_ZOOM = "reader_magnifier_zoom" // 1.8 / 2.0 / 2.5
    const val READER_MAGNIFIER_ORIENTATION = "reader_magnifier_orientation" // landscape / portrait
    const val MAGNIFIER_COMPACT = "compact"
    const val MAGNIFIER_STANDARD = "standard"
    const val MAGNIFIER_WIDE = "wide"
    const val MAGNIFIER_LANDSCAPE = "landscape"
    const val MAGNIFIER_PORTRAIT = "portrait"

    // M5-B 智能分格：per-book，off=关闭 / auto=自动 / on=始终开启
    const val PANEL_OFF = "off"
    const val PANEL_AUTO = "auto"
    const val PANEL_ON = "on"

    // 智能分格默认已并入全局阅读快照（READER_GLOBAL_PREFS.panelMode），
    // 旧键 reader_panel_default 不再读取：未设置过的书一律默认「关闭」（省内存）。

    fun readerBookKey(bookId: String): String = READER_BOOK_PREFIX + bookId

    // 漫画阅读全局布局默认：所有「未单独记忆」的书读取/写入它（快照编码同 per-book）
    const val READER_GLOBAL_PREFS = "reader_global_prefs"

    // 「此本书单独记忆」：每书独立开关（无值=未选择过，按旧快照自动迁移为开）。
    // 开 → 该书读取/写入本书快照；关 → 全局跟随（READER_GLOBAL_PREFS，本书旧快照忽略）。
    const val READER_BOOK_SOLO_PREFIX = "reader_book_solo_"

    fun readerBookSoloKey(bookId: String): String = READER_BOOK_SOLO_PREFIX + bookId

    // 小说双页：独立于漫画档位；auto=横屏自动 / always=始终 / off=关闭（仅左右翻页生效）
    const val NOVEL_DOUBLE_PAGE = "novel_double_page"

    // 小说翻页动画：独立全局键（独立于漫画档）；instant / slide / cover / curl。
    // 仅点击翻页生效，拖拽与跳页保持瞬时；上下翻页时 cover/curl 回退平移（无竖向语义）。
    const val NOVEL_PAGE_TRANSITION = "novel_page_transition"
    const val PAGE_TRANSITION_CURL = "curl" // 仿真：点击翻页的纸张卷曲（小说专属档）

    // 小说阅读器边缘手势（移植自漫画）：左缘竖滑调系统亮度、右缘竖滑调色温。
    // 纵向连续（scroll）档不支持（边缘竖滑让位给滚动）。
    const val NOVEL_EDGE_BRIGHTNESS = "novel_edge_brightness"
    const val NOVEL_EDGE_COLOR_TEMP = "novel_edge_color_temp"

    // M6.1-B 小说阅读
    const val NOVEL_FONTS = "novel_fonts"
    const val NOVEL_BOOK_PREFIX = "novel_book_"
    const val NOVEL_THEME_PAPER = "paper"
    const val NOVEL_THEME_SEPIA = "sepia"
    // 2026-11-26 背景扩档（4→8）：新增四档补齐「浓暖纸 / 冷调浅色 / 粉调 / 第二深色」四个空位。
    // 按 id 字符串持久化，追加新值对旧存档安全（旧版本读到未知值走 novelTheme 的纸白兜底）。
    const val NOVEL_THEME_KRAFT = "kraft" // 牛皮纸（浓暖，接近旧书页）
    const val NOVEL_THEME_GREEN = "green"
    const val NOVEL_THEME_MIST = "mist" // 雾蓝（唯一冷调浅色）
    const val NOVEL_THEME_SAKURA = "sakura" // 樱粉（唯一非中性色相）
    const val NOVEL_THEME_DARK = "dark"
    const val NOVEL_THEME_SLATE = "slate" // 深灰蓝（深色族第二档，低对比不刺眼）
    const val NOVEL_PAGE_HORIZONTAL = "horizontal"
    const val NOVEL_PAGE_VERTICAL = "vertical"
    // M6.5 纵向连续：单章信息流式连续滚动（LazyColumn）。
    // 与 horizontal/vertical 同字段第三档值，无新存储结构；旧版本读到未知值回落默认横向。
    const val NOVEL_PAGE_SCROLL = "scroll"

    fun novelBookKey(bookId: String): String = NOVEL_BOOK_PREFIX + bookId

    // M6.4-D「此本书单独记忆」：每书独立开关（无值=关）。
    // 开 → 该书读取/写入本书快照；关 → 全局跟随（NOVEL_GLOBAL_PREFS，本书旧快照忽略）。
    const val NOVEL_BOOK_SOLO_PREFIX = "novel_book_solo_"

    fun novelBookSoloKey(bookId: String): String = NOVEL_BOOK_SOLO_PREFIX + bookId

    // M6.1-D 设置页：小说阅读全局默认（per-book 未设置时回退到它）
    const val NOVEL_GLOBAL_PREFS = "novel_global_prefs"

    // 深色跟随：App 处于深色模式时，小说阅读背景自动切到墨黑（双向跟随：读取时派生，
    // 不写 prefs.theme，回浅色自动恢复手动档）；跟随期间手动选浅色档 = 用户接管，自动关闭。
    // 缺省开（booleanOf 默认 true）。
    const val NOVEL_DARK_FOLLOW = "novel_dark_follow"

    // 深色跟随首次提示：进入深色后首次打开小说阅读器弹一次居中卡提示，落盘后不再出现。
    const val NOVEL_DARK_FOLLOW_HINT = "novel_dark_follow_hint_shown"

    // 挖孔避让兜底：设备曾上报过的最大挖孔 inset（dp 字符串）。
    // 部分平板横屏时 displayCutout 全 0（挖孔被并入状态栏语义），用该值对左右补偿。
    const val NOVEL_CUTOUT_REMEMBER = "novel_cutout_remember"

    // M7 全局搜索：历史（JSON 数组）/ 筛选记忆（SearchFilter JSON）/ 筛选模板（JSON 数组）
    const val SEARCH_HISTORY = "search_history"
    const val SEARCH_FILTER = "search_filter_json"
    const val SEARCH_FILTER_TEMPLATES = "search_filter_templates"

    // ---- M4.5 WebDAV 同步 ----
    const val SYNC_ENABLED = "sync_enabled"               // 同步总开关（配置完整后由用户启停）
    const val SYNC_SERVER = "sync_server"                 // WebDAV 服务器 URL（强制 https）
    const val SYNC_ACCOUNT = "sync_account"
    const val SYNC_PASSWORD_ENC = "sync_password_enc"     // CryptoBox 密文（Base64 iv+ct）
    const val SYNC_BASE_PATH = "sync_base_path"           // 同步根目录，默认 /Inkvol/
    const val SYNC_AUTO = "sync_auto"                     // 自动同步（进度防抖 / 退后台）
    const val SYNC_WIFI_ONLY = "sync_wifi_only"           // 仅在 WiFi 下同步
    const val SYNC_DEVICE_NAME = "sync_device_name"       // 空串 = 默认（厂商+型号）
    const val SYNC_DEVICE_ID = "sync_device_id"           // 首次启用生成的 UUID
    const val SYNC_FILE_SCOPE = "sync_file_scope"         // 文件同步范围 JSON（SyncFileScope）
    @Deprecated("已被 SYNC_FILE_SCOPE 取代，仅用于旧值迁移")
    const val SYNC_FILES_SINGLE = "sync_files_single"     // 旧：单文件导入书籍文件同步（默认开）
    @Deprecated("已被 SYNC_FILE_SCOPE 取代，仅用于旧值迁移")
    const val SYNC_FILES_ALL = "sync_files_all"           // 旧：全部书籍文件同步（默认关）
    const val SYNC_ALLOW_INSECURE_TLS = "sync_allow_insecure_tls" // 允许自签证书（默认关）
    const val SYNC_DIRTY = "sync_dirty"                   // 持久脏标记（进程死亡后补同步）
    const val SYNC_UPLOAD_PENDING = "sync_upload_pending" // 待上传 bookId JSON 数组
    const val SYNC_DELETE_PENDING = "sync_delete_pending" // 待删除云端文件 bookId JSON 数组
    const val SYNC_LAST_MODIFIED = "sync_last_modified"   // 远端设备文件 Last-Modified 缓存 JSON
    const val SYNC_LOG = "sync_log"                       // 同步记录 JSON（最近 20 条）
    const val SYNC_LAST_OK_AT = "sync_last_ok_at"         // 上次成功同步时间
    const val SYNC_LAST_RESULT = "sync_last_result"       // 状态行文案（成功/失败摘要）
    const val SYNC_FAILURES = "sync_failures"             // 连续失败次数（冷却判定）
    const val SYNC_COOLDOWN_UNTIL = "sync_cooldown_until" // 冷却截止时间
    const val SYNC_BUDGET_WINDOW = "sync_budget_window"   // 请求预算滑窗起点（30min）
    const val SYNC_BUDGET_USED = "sync_budget_used"       // 滑窗内已用请求数

    // per-book 阅读偏好变更时间戳（合并 LWW 依据；ReaderScreen 落盘处同步写）
    const val BOOK_PREF_TS_PREFIX = "book_pref_ts_"

    // 应用锁：开关（系统锁屏凭据解锁；未设置锁屏密码时自动不生效）
    const val APP_LOCK_ENABLED = "app_lock_enabled"
    // 离开应用多久后重新上锁（毫秒）
    const val APP_LOCK_GRACE_MS = 30_000L

    // ---- 隐私政策同意 ----
    // 值为用户已同意的政策版本；与 PRIVACY_POLICY_VERSION 不等时首启会重新征求同意。
    const val PRIVACY_ACCEPTED_VERSION = "privacy_accepted_version"
    const val PRIVACY_POLICY_VERSION = "1"

    fun bookPrefTsKey(bookId: String): String = BOOK_PREF_TS_PREFIX + bookId
}
