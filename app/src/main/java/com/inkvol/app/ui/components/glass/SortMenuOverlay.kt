package com.inkvol.app.ui.components.glass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.inkvol.app.data.settings.SettingsKeys
import com.kyant.backdrop.Backdrop

/**
 * 书架排序菜单（6 档 + 升降序切换 + 展示样式切换行）。
 *
 * 功能逻辑对齐 SSMH 的书夹排序：逐档点选即生效并收起，末行「改为升序/改为降序」
 * 切换全局方向。视觉与 [PlusMenuOverlay] 完全同语言（GlassOverlay Anchored 动效 +
 * MenuRow 双行 + HorizontalDivider），选中档主色高亮 + 行尾对勾。
 *
 * 展示样式切换行（library_shelf_view）：[viewStyle] / [onViewStyleToggle] 非空才渲染
 * （一级平铺/系列视图时由调用方传入；二级页打开时不传，样式只作用一级页）。
 *
 * 锚点几何由调用方经 [modifier]（padding / widthIn）与 [alignment] / [origin] 给出：
 * - 手机：标题行排序钮左上角（TopEnd + 右上 origin）；
 * - 平板：侧栏动作槽第 2 颗钮左侧（TopEnd + 右上 origin）；
 * - 二级页：标题行右缘（TopEnd + 右上 origin）。
 */
private data class SortOption(
    val value: String,
    val label: String,
    val icon: ImageVector,
    val ascSubtitle: String,
    val descSubtitle: String,
)

/** 六档固定序：收藏时间 → 最近阅读 → 标题 → 页数 → 阅读进度 → 文件大小。 */
private val sortOptions = listOf(
    SortOption(
        SettingsKeys.SORT_RECENT, "收藏时间", Icons.Outlined.Schedule,
        ascSubtitle = "最早添加在前", descSubtitle = "最新添加在前",
    ),
    SortOption(
        SettingsKeys.SORT_LAST_READ, "最近阅读", Icons.Outlined.History,
        ascSubtitle = "最久未读在前", descSubtitle = "最近读的在前",
    ),
    SortOption(
        SettingsKeys.SORT_TITLE, "标题", Icons.Outlined.SortByAlpha,
        ascSubtitle = "A → Z", descSubtitle = "Z → A",
    ),
    SortOption(
        SettingsKeys.SORT_PAGES, "页数", Icons.AutoMirrored.Outlined.MenuBook,
        ascSubtitle = "页数少在前", descSubtitle = "页数多在前",
    ),
    SortOption(
        SettingsKeys.SORT_PROGRESS, "阅读进度", Icons.Outlined.PieChart,
        ascSubtitle = "进度低在前", descSubtitle = "进度高在前",
    ),
    SortOption(
        SettingsKeys.SORT_SIZE, "文件大小", Icons.Outlined.DataUsage,
        ascSubtitle = "文件小在前", descSubtitle = "文件大在前",
    ),
)

@Composable
fun SortMenuOverlay(
    visible: Boolean,
    presence: GlassOverlayPresence?,
    backdrop: Backdrop,
    /** 当前档位（SettingsKeys.SORT_* 持久化值）。 */
    mode: String,
    /** 当前方向：true 降序（默认，最新/最大在前）。 */
    descending: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onToggleOrder: () -> Unit,
    /** 当前书架展示样式（SettingsKeys.SHELF_VIEW_*）；null = 不渲染切换行（二级页）。 */
    viewStyle: String? = null,
    /** 切换网格/列表展示；null = 不渲染切换行。 */
    onViewStyleToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopEnd,
    origin: TransformOrigin = TransformOrigin(1f, 0f),
) {
    val listStyleActive = viewStyle == SettingsKeys.SHELF_VIEW_LIST
    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.Anchored,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = alignment,
        origin = origin,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            sortOptions.forEachIndexed { index, option ->
                if (index > 0) HorizontalDivider()
                MenuRow(
                    icon = option.icon,
                    title = option.label,
                    subtitle = if (descending) option.descSubtitle else option.ascSubtitle,
                    onClick = { onPick(option.value) },
                    selected = mode == option.value,
                )
            }
            HorizontalDivider()
            MenuRow(
                icon = Icons.Outlined.SwapVert,
                title = if (descending) "改为升序" else "改为降序",
                subtitle = if (descending) "当前降序，点按切换" else "当前升序，点按切换",
                onClick = onToggleOrder,
            )
            if (onViewStyleToggle != null && viewStyle != null) {
                HorizontalDivider()
                MenuRow(
                    icon = if (listStyleActive) {
                        Icons.Outlined.GridView
                    } else {
                        Icons.AutoMirrored.Outlined.ViewList
                    },
                    title = if (listStyleActive) "切换为网格展示" else "切换为列表展示",
                    subtitle = if (listStyleActive) {
                        "当前列表展示，点按切换"
                    } else {
                        "当前网格展示，点按切换"
                    },
                    onClick = onViewStyleToggle,
                )
            }
        }
    }
}

/** 书夹卡排序的一档：持久化值 + 标题 + 图标 + 升/降序副标题（custom 档方向无关）。 */
private data class FolderSortOption(
    val value: String,
    val label: String,
    val icon: ImageVector,
    val ascSubtitle: String,
    val descSubtitle: String,
)

private val folderSortOptions = listOf(
    FolderSortOption(
        SettingsKeys.FOLDER_SORT_NAME, "按名称", Icons.Outlined.SortByAlpha,
        ascSubtitle = "卡名 A → Z", descSubtitle = "卡名 Z → A",
    ),
    FolderSortOption(
        SettingsKeys.FOLDER_SORT_COUNT, "按数量", Icons.Outlined.DataUsage,
        ascSubtitle = "书少的书夹在前", descSubtitle = "书多的书夹在前",
    ),
    FolderSortOption(
        SettingsKeys.FOLDER_SORT_CUSTOM, "自定义顺序", Icons.Outlined.DragHandle,
        ascSubtitle = "长按拖动卡片调整", descSubtitle = "长按拖动卡片调整",
    ),
)

/**
 * 书夹视图（一级）的书夹卡排序菜单（3 档 + 升降序行 + 展示样式切换行）——与书排序菜单
 * [SortMenuOverlay] 同语言但独立记忆（library_folder_sort / _desc），
 * 书夹卡片顺序不跟随全局书排序档。方向只作用于名称/数量两档，
 * 自定义（拖拽）档与方向无关。锚点几何同 [SortMenuOverlay] 由调用方给出。
 *
 * 展示样式切换行（library_shelf_view，三视图联动）：[viewStyle] / [onViewStyleToggle]
 * 非空才渲染（调用方按平板/手机各自传回调，写同一持久化键、prefs flow 自动回流两边）。
 * 列表样式不支持拖拽排序，custom 档副标题随之提示回网格操作。
 */
@Composable
fun FolderSortMenuOverlay(
    visible: Boolean,
    presence: GlassOverlayPresence?,
    backdrop: Backdrop,
    /** 当前档位（SettingsKeys.FOLDER_SORT_* 持久化值）。 */
    mode: String,
    /** 当前方向：true 降序；只影响名称/数量档，custom 档显示原拖拽序。 */
    descending: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onToggleOrder: () -> Unit,
    /** 当前书架展示样式（SettingsKeys.SHELF_VIEW_*）；null = 不渲染切换行。 */
    viewStyle: String? = null,
    /** 切换网格/列表展示；null = 不渲染切换行。 */
    onViewStyleToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopEnd,
    origin: TransformOrigin = TransformOrigin(1f, 0f),
) {
    val listStyleActive = viewStyle == SettingsKeys.SHELF_VIEW_LIST
    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.Anchored,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = alignment,
        origin = origin,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            folderSortOptions.forEachIndexed { index, option ->
                if (index > 0) HorizontalDivider()
                MenuRow(
                    icon = option.icon,
                    title = option.label,
                    subtitle = if (option.value == SettingsKeys.FOLDER_SORT_CUSTOM && listStyleActive) {
                        "切换到网格视图后长按拖动"
                    } else if (descending) {
                        option.descSubtitle
                    } else {
                        option.ascSubtitle
                    },
                    onClick = { onPick(option.value) },
                    selected = mode == option.value,
                )
            }
            HorizontalDivider()
            MenuRow(
                icon = Icons.Outlined.SwapVert,
                title = if (descending) "改为升序" else "改为降序",
                subtitle = if (descending) "当前降序，点按切换" else "当前升序，点按切换",
                onClick = onToggleOrder,
            )
            if (onViewStyleToggle != null && viewStyle != null) {
                HorizontalDivider()
                MenuRow(
                    icon = if (listStyleActive) {
                        Icons.Outlined.GridView
                    } else {
                        Icons.AutoMirrored.Outlined.ViewList
                    },
                    title = if (listStyleActive) "切换为网格展示" else "切换为列表展示",
                    subtitle = if (listStyleActive) {
                        "当前列表展示，点按切换"
                    } else {
                        "当前网格展示，点按切换"
                    },
                    onClick = onViewStyleToggle,
                )
            }
        }
    }
}
