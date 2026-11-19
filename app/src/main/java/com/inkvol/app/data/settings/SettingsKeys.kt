package com.inkvol.app.data.settings

/** 设置键单一真源，UI 与仓储共用。 */
object SettingsKeys {
    const val APP_THEME = "app_theme"                // system / light / dark
    const val DYNAMIC_COLOR = "dynamic_color"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    const val LIBRARY_VIEW = "library_view"          // folder / series / flat
    const val LIBRARY_SORT_PREFIX = "library_sort_"
    const val PLUS_ACTION = "import_plus_action"     // ask / folder / file / rescan
    const val READER_OPEN_SPEED = "reader_open_speed" // faster / fast(默认) / default(适中) / slow / slower

    const val VIEW_FOLDER = "folder"
    const val VIEW_SERIES = "series"
    const val VIEW_FLAT = "flat"

    const val SORT_RECENT = "recent"
    const val SORT_TITLE = "title"
    const val SORT_SIZE = "size"

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

    // 全局智能分格初始默认：新书与未单独设置过的书使用（默认关闭）
    const val READER_PANEL_DEFAULT = "reader_panel_default"

    fun readerBookKey(bookId: String): String = READER_BOOK_PREFIX + bookId

    // 小说双页：独立于漫画档位；auto=横屏自动 / always=始终 / off=关闭（仅左右翻页生效）
    const val NOVEL_DOUBLE_PAGE = "novel_double_page"

    // M6.1-B 小说阅读
    const val NOVEL_FONTS = "novel_fonts"
    const val NOVEL_BOOK_PREFIX = "novel_book_"
    const val NOVEL_THEME_PAPER = "paper"
    const val NOVEL_THEME_SEPIA = "sepia"
    const val NOVEL_THEME_GREEN = "green"
    const val NOVEL_THEME_DARK = "dark"
    const val NOVEL_PAGE_HORIZONTAL = "horizontal"
    const val NOVEL_PAGE_VERTICAL = "vertical"

    fun novelBookKey(bookId: String): String = NOVEL_BOOK_PREFIX + bookId

    // M6.1-D 设置页：小说阅读全局默认（per-book 未设置时回退到它）
    const val NOVEL_GLOBAL_PREFS = "novel_global_prefs"

    // 挖孔避让兜底：设备曾上报过的最大挖孔 inset（dp 字符串）。
    // 部分平板横屏时 displayCutout 全 0（挖孔被并入状态栏语义），用该值对左右补偿。
    const val NOVEL_CUTOUT_REMEMBER = "novel_cutout_remember"
}
