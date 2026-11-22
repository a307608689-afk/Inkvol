package com.inkvol.app.data.model

import com.inkvol.app.data.settings.SettingsKeys

/**
 * 漫画阅读布局偏好：per-book 快照与全局默认（[SettingsKeys.READER_GLOBAL_PREFS]）共用同一编码。
 *
 * 编码是 "|" 连接的固定顺序字段，历史存档必须保持 index 对齐：
 * 0 vertical / 1 rtl / 2 doublePage（占位恒 0，已迁移为全局档）/
 * 3 trimMargins / 4 splitSpreads / 5 repair / 6 pageOverlap /
 * 7 panelMode / 8 panelModeExplicit
 */
data class BookReadingPrefs(
    val vertical: Boolean = false,
    val rtl: Boolean = true,
    val trimMargins: Boolean = false,
    val splitSpreads: Boolean = false,
    val repair: Int = 0,
    val pageOverlap: Int = 0,
    val panelMode: String = SettingsKeys.PANEL_OFF,
    // index 8：用户是否显式改过分格档位；false = 跟随全局分格档
    val panelModeExplicit: Boolean = false,
)

/** 解析快照；空串返回 null（=从未保存过）。 */
fun decodeBookPrefs(
    raw: String?,
    defaultPanelMode: String = SettingsKeys.PANEL_OFF,
): BookReadingPrefs? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.split('|')
    val explicit = parts.getOrNull(8) == "1"
    val storedPanelMode = when (parts.getOrNull(7)?.toIntOrNull()) {
        0 -> SettingsKeys.PANEL_OFF
        2 -> SettingsKeys.PANEL_ON
        else -> SettingsKeys.PANEL_AUTO
    }
    return BookReadingPrefs(
        vertical = parts.getOrNull(0) == "1",
        rtl = parts.getOrNull(1) != "0",
        // 旧格式 index 2 是 doublePage（已迁移为全局设置 reader_double_page），跳过不读
        trimMargins = parts.getOrNull(3) == "1",
        splitSpreads = parts.getOrNull(4) == "1",
        repair = parts.getOrNull(5)?.toIntOrNull()?.coerceIn(0, 2) ?: 0,
        pageOverlap = parts.getOrNull(6)?.toIntOrNull()?.coerceIn(0, 24) ?: 0,
        // 未显式设置过的书（含旧存档无 index 8）一律吃默认分格档
        panelMode = if (explicit) storedPanelMode else defaultPanelMode,
        panelModeExplicit = explicit,
    )
}

fun encodeBookPrefs(prefs: BookReadingPrefs): String = listOf(
    if (prefs.vertical) "1" else "0",
    if (prefs.rtl) "1" else "0",
    // doublePage 占位（恒 0），保持旧编码格式对齐，避免旧存档错位解析
    "0",
    if (prefs.trimMargins) "1" else "0",
    if (prefs.splitSpreads) "1" else "0",
    prefs.repair.toString(),
    prefs.pageOverlap.toString(),
    when (prefs.panelMode) {
        SettingsKeys.PANEL_AUTO -> "1"
        SettingsKeys.PANEL_ON -> "2"
        else -> "0"
    },
    // index 8：显式设置标记（旧存档无此段 → 视为跟随默认分格档）
    if (prefs.panelModeExplicit) "1" else "0",
).joinToString("|")
