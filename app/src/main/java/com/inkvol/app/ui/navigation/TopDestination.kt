package com.inkvol.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Inkvol 的三个顶层目的地。
 *
 * 与 SSMH 的五 Tab 相比砍掉了首页/搜索/排行（无在线源），
 * 书架内的 全部/漫画/小说 类型筛选由书架页自己承担，不占 Tab。
 */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Library("library", "书架", Icons.Filled.Bookmarks),
    History("history", "历史", Icons.Filled.History),
    Settings("settings", "设置", Icons.Filled.Settings),
}
