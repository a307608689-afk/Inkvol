package com.inkvol.app.ui.library

import android.content.Intent
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.inkvol.app.data.import.SingleFileImporter
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.model.CategoryRecord
import com.inkvol.app.data.model.SystemCategory
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.data.settings.SettingsRepository
import com.inkvol.app.ui.components.LocalGlassNotice
import com.inkvol.app.ui.components.LocalPageOverlayController
import com.inkvol.app.ui.components.LocalReaderOpenController
import com.inkvol.app.ui.components.LibraryShelfListScroll
import com.inkvol.app.ui.components.LibraryDetailScroll
import com.inkvol.app.ui.components.LibraryGridScroll
import com.inkvol.app.ui.components.LibraryPullRefresh
import com.inkvol.app.ui.components.LocalScrollToTop
import com.inkvol.app.ui.components.ReaderOpenPhase
import com.inkvol.app.ui.components.ReaderOpenRect
import com.inkvol.app.ui.components.glass.GlassDimens
import com.inkvol.app.ui.components.glass.GlassEdge
import com.inkvol.app.ui.components.glass.GlassHeaderChip
import com.inkvol.app.ui.components.glass.GlassHeaderChipsRow
import com.inkvol.app.ui.components.glass.GlassHeaderTitleBlock
import com.inkvol.app.ui.components.glass.GlassMotion
import com.inkvol.app.ui.components.glass.GlassOverlay
import com.inkvol.app.ui.components.glass.GlassOverlayMotion
import com.inkvol.app.ui.components.glass.GlassOverlayPresence
import com.inkvol.app.ui.components.glass.PlusMenuOverlay
import com.inkvol.app.ui.components.glass.SelectionMenuOverlay
import com.inkvol.app.ui.components.glass.FolderSortMenuOverlay
import com.inkvol.app.ui.components.glass.SortMenuOverlay
import com.inkvol.app.ui.components.glass.GlassRounded
import com.inkvol.app.ui.components.glass.GlassTier
import com.inkvol.app.ui.components.glass.LocalGlassChrome
import com.inkvol.app.ui.components.glass.rememberGlassHeaderState
import com.inkvol.app.ui.components.glass.rememberGlassOverlayPresence
import com.inkvol.app.ui.search.SectionHeader
import com.inkvol.app.ui.theme.BookmarkPurple
import com.inkvol.app.ui.theme.InfoBlue
import com.inkvol.app.ui.theme.InkvolRadius
import com.inkvol.app.ui.theme.LocalFixedFolderColors
import com.inkvol.app.ui.theme.SuccessGreen
import com.inkvol.app.ui.theme.WarningAmber
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private val CATEGORY_COLORS = listOf(
    "#C8462F", "#4CAF7D", "#6C8CFF", "#E0A33E", "#9B59B6", "#5F6368",
)

internal sealed interface LibraryTarget {
    data class User(val id: Long, val name: String, val color: String) : LibraryTarget
    data class System(val kind: SystemCategory) : LibraryTarget

    /**
     * 系列（文件夹）。
     * - [path] = 相对书库根的目录路径（"" = 库根伪系列「未归档」，
     *   "海贼王/单行本" = 二级目录）；
     * - [path] 以 "@name:" 开头 = 搜索页按系列名进入（没有路径，按名字匹配）。
     */
    data class Series(val path: String, val name: String) : LibraryTarget {
        companion object {
            const val NAME_PREFIX = "@name:"
            fun byName(name: String) = Series(NAME_PREFIX + name, name)
        }
    }
}

/** 库根直接存放的书在系列视图里的伪系列名（与「未分类」书夹区分）。 */
private const val ROOT_SERIES_LABEL = "未归档"

/** 旧数据（v12 前 / 单文件导入）没有 folderPath，按旧 series 名退化。 */
private const val LEGACY_SERIES_PREFIX = "@legacy:"

/**
 * 书所属的叶子系列（直接父目录）——系列视图的唯一归属真源。
 * - folderPath 非空（含 ""）且书库名非空 → 系列路径 = 书库名 + 相对目录路径
 *   （书库根本身算一层系列：库根直接放的书归到「书库名」这一级，二级文件夹归到
 *   「书库名/文件夹」，从而支持「库根 → 二级系列 + 散书」的分区）
 * - 无书库名（单文件导入 / 旧数据）→ folderPath 自身，或按旧 series 名退化
 */
private fun leafSeriesOf(book: BookRecord): Pair<String, String> {
    val path = book.folderPath
    val lib = book.libraryName
    if (path != null && lib != null) {
        val full = if (path.isEmpty()) lib else "$lib/$path"
        return full to full.substringAfterLast('/')
    }
    if (path != null) {
        val name = if (path.isEmpty()) ROOT_SERIES_LABEL else path.substringAfterLast('/')
        return path to name
    }
    val legacy = book.series?.takeIf { it.isNotBlank() } ?: book.title
    return LEGACY_SERIES_PREFIX + legacy to legacy
}

/** 系列路径的显示名（末段；根 / 旧数据 / 按名入口特殊处理）。 */
private fun seriesNameOf(path: String): String = when {
    path.isEmpty() -> ROOT_SERIES_LABEL
    path.startsWith(LEGACY_SERIES_PREFIX) -> path.removePrefix(LEGACY_SERIES_PREFIX)
    path.startsWith(LibraryTarget.Series.NAME_PREFIX) ->
        path.removePrefix(LibraryTarget.Series.NAME_PREFIX)

    else -> path.substringAfterLast('/')
}

/** 系列卡节点：path 唯一；books = 该节点子树内的全部书（拼贴/计数/排序同源）。 */
private data class SeriesNode(
    val path: String,
    val name: String,
    val books: List<BookRecord>,
)

/** 层级模式一级卡：库根下的第一层文件夹（根层直接书归「未归档」）。 */
private fun topSeriesNodes(books: List<BookRecord>): List<SeriesNode> {
    val groups = LinkedHashMap<String, MutableList<BookRecord>>()
    books.forEach { book ->
        val leafPath = leafSeriesOf(book).first
        val topPath = when {
            leafPath.isEmpty() || leafPath.startsWith("@") -> leafPath
            else -> leafPath.substringBefore('/')
        }
        groups.getOrPut(topPath) { mutableListOf() } += book
    }
    return groups.map { (path, list) -> SeriesNode(path, seriesNameOf(path), list) }
}

/** 层级模式二级及以下：某节点下的子系列（子节点聚合其子树全部书）。 */
private fun childSeriesNodes(books: List<BookRecord>, path: String): List<SeriesNode> {
    // 根伪系列「未归档」只代表直接放在库根的书：第一层文件夹已经是一级卡，
    // 在它的页里再列一遍会重复；@legacy/@name 入口也没有层级语义。
    if (path.isEmpty() || path.startsWith("@")) return emptyList()
    val prefix = if (path.isEmpty()) "" else "$path/"
    val groups = LinkedHashMap<String, MutableList<BookRecord>>()
    books.forEach { book ->
        val leafPath = leafSeriesOf(book).first
        if (leafPath.startsWith("@") || leafPath == path) return@forEach
        if (!leafPath.startsWith(prefix)) return@forEach
        val rest = leafPath.removePrefix(prefix)
        if (rest.isEmpty()) return@forEach
        val segment = rest.substringBefore('/')
        val childPath = if (path.isEmpty()) segment else "$path/$segment"
        groups.getOrPut(childPath) { mutableListOf() } += book
    }
    return groups.map { (p, list) -> SeriesNode(p, seriesNameOf(p), list) }
}

/** 扁平模式：直接包含书的那一层目录各成一系列（同名不同父加父目录名后缀）。 */
private fun flatSeriesNodes(books: List<BookRecord>): List<SeriesNode> {
    val groups = LinkedHashMap<String, MutableList<BookRecord>>()
    books.forEach { book ->
        groups.getOrPut(leafSeriesOf(book).first) { mutableListOf() } += book
    }
    val nameCounts = groups.keys.groupingBy { seriesNameOf(it) }.eachCount()
    return groups.map { (path, list) ->
        val raw = seriesNameOf(path)
        val name = if ((nameCounts[raw] ?: 0) > 1) {
            val parent = when {
                path.startsWith("@") || !path.contains('/') -> "库根"
                else -> path.substringBeforeLast('/').substringAfterLast('/')
            }
            "$raw · $parent"
        } else {
            raw
        }
        SeriesNode(path, name, list)
    }
}

private fun seriesNodesFor(books: List<BookRecord>, mode: String): List<SeriesNode> =
    if (mode == SettingsKeys.SERIES_MODE_FLAT) flatSeriesNodes(books) else topSeriesNodes(books)

/** 书是否属于某系列节点：层级模式 = 子树；扁平 / 按名入口 = 精确 / 按名。 */
private fun bookInSeries(book: BookRecord, path: String, mode: String): Boolean {
    val (leafPath, leafName) = leafSeriesOf(book)
    if (path.startsWith(LibraryTarget.Series.NAME_PREFIX)) {
        val name = path.removePrefix(LibraryTarget.Series.NAME_PREFIX)
        // 旧数据 leafName 已按 series ?: title 兜底，这里不再单独匹配书名（防多收）
        return leafName == name || book.series == name
    }
    if (path.isEmpty()) return leafPath.isEmpty()
    return if (mode == SettingsKeys.SERIES_MODE_FLAT) {
        leafPath == path
    } else {
        leafPath == path || leafPath.startsWith("$path/")
    }
}

/** 层级模式的返回父级：只在真实文件夹路径上有父节点（@旧数据/@按名 没有父级）。 */
private fun parentSeriesTarget(target: LibraryTarget.Series): LibraryTarget.Series? {
    val path = target.path
    if (path.startsWith("@") || !path.contains('/')) return null
    val parentPath = path.substringBeforeLast('/')
    return LibraryTarget.Series(parentPath, seriesNameOf(parentPath))
}

/**
 * 进程级书架 UI 状态：开书/切 Tab 会让书架页被 NavHost 卸载，普通 remember 全部归零，
 * 表现为「二级页/漫画小说筛选被重置回一级/全部」。这里与 LibraryGridScroll 同范式：
 * 离页时在 onDispose 落值，进书架首帧直读恢复；显式返回（回一级/点回「全部」）
 * 自然落 null，进程被杀后重开也归零回一级，不做磁盘持久化。
 * target 是 mutableStateOf：宿主（InkvolApp）在组合内读它做侧栏排序菜单分流
 * （书夹一级 vs 二级页弹不同菜单），必须可观察才能即时跟随二级页打开/关闭。
 */
internal object LibraryUiState {
    var target: LibraryTarget? by mutableStateOf(null)
    var typeFilter: String? = null
}

/**
 * 分类档位顺序（与头部 chips 的 全部→漫画→小说 排列一致）。
 * ① 三档网格状态 map 的键序；② 分类切换滑动动画的方向判定：
 * 目标 index 更大 = 向前 = 新页从右滑入（与 Tab / 二级页转场同语言）。
 */
private val libraryTypeOrder = listOf<String?>(null, "COMIC", "NOVEL")

/**
 * 视图档位顺序（与 viewHeader chips 的 书夹→系列→平铺 排列一致）。
 * ① 视图×分类状态 map 的键序；② 视图切换滑动动画的方向判定：
 * 目标 index 更大 = 向前 = 新页从右滑入（与分类切换同语言）。
 */
private val libraryViewOrder = listOf(
    SettingsKeys.VIEW_FOLDER,
    SettingsKeys.VIEW_SERIES,
    SettingsKeys.VIEW_FLAT,
)

/**
 * 二级页（书夹 / 系列 / 智能书夹）的稳定键：
 * ① 进程级滚动位置的保存/命中判据（LibraryDetailScroll.key）；
 * ② 「当前活跃网格」与头部重置键的组成部分。
 */
private fun targetKeyOf(target: LibraryTarget?): String? = when (target) {
    null -> null
    is LibraryTarget.User -> "user:${target.id}"
    is LibraryTarget.System -> "system:${target.kind.name}"
    is LibraryTarget.Series -> "series:${target.path}"
}

/** 二级页标题行高度（48 的返回/排序按钮行 + 8 与网格的间距）：计入头部内容高。 */
private val DETAIL_TITLE_ROW_HEIGHT = 56.dp

/**
 * 一镜到底返回：把网格落回保存位置。
 *
 * 两段式（与 §9.13/§9.14 的定案一致）：
 * ① 立即派发 + 逐帧重申（≤40 帧）——新组装的 LazyGrid 最初几个测量帧会把锚点重置回
 *    (0,0)，单次 scrollToItem 会被下一次测量冲掉；
 * ② 等 totalItemsCount 连续两帧稳定后再落位一次——返回后数据会二次生长（5→16），
 *    早于数据稳定的落位会被重构顶回顶部。
 *
 * 落位判定用「目标 item 的纵向位置 ≈ 目标 offset」而不是「目标 item 在视口内」：
 * 后者在目标靠近顶部（返回时数据从顶部开始渲染）时常真，导致重申被立即 break、
 * 首测量帧的 (0,0) 不被纠正——chips/视图切换后的组合踩这条最明显。
 */
private suspend fun restoreGridPosition(
    state: LazyGridState,
    index: Int,
    offset: Int,
    tag: String,
) {
    if (index <= 0 && offset <= 0) return
    // 兜底：网格尚未 attach（如二级页内容还没测量）时 scrollToItem 会挂起等布局，
    // 不能让恢复流程卡死——settled 不置位会让状态栏控制被永久挂起、卡片回报被忽略。
    // 正常路径两段各 ≤40 帧（约 1.3s），3s 只兜底异常。
    withTimeoutOrNull(3000L) { restoreGridPositionImpl(state, index, offset, tag) }
        ?: Log.d("InkvolOpen", "diag restore $tag timeout")
}

private suspend fun restoreGridPositionImpl(
    state: LazyGridState,
    index: Int,
    offset: Int,
    tag: String,
) {
    fun landed(): Boolean {
        val info = state.layoutInfo
        if (info.totalItemsCount <= index) return false
        if (state.firstVisibleItemIndex == index &&
            state.firstVisibleItemScrollOffset == offset
        ) {
            return true
        }
        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
        // LazyGridItemInfo.offset 是 IntOffset（本版本 API），取纵向分量比较
        return kotlin.math.abs(item.offset.y + offset) <= 2
    }
    Log.d("InkvolOpen", "diag restore $tag start target=($index,$offset)")
    runCatching { state.scrollToItem(index, offset) }
    var reassertFrames = 0
    while (reassertFrames < 40) {
        if (landed()) break
        runCatching { state.scrollToItem(index, offset) }
        delay(16)
        reassertFrames++
    }
    var prevCount = -1
    var stable = 0
    var attempts = 0
    while (attempts < 40) {
        val count = state.layoutInfo.totalItemsCount
        if (count == prevCount) {
            stable++
        } else {
            stable = 0
            prevCount = count
        }
        if (stable >= 2 && count > index) break
        delay(16)
        attempts++
    }
    if (state.layoutInfo.totalItemsCount > index) {
        runCatching { state.scrollToItem(index, offset) }
    }
    Log.d(
        "InkvolOpen",
        "diag restore $tag done frames=$reassertFrames attempts=$attempts " +
            "grid=(${state.firstVisibleItemIndex},${state.firstVisibleItemScrollOffset}) " +
            "count=${state.layoutInfo.totalItemsCount}",
    )
}

/**
 * 列表容器版滚动落位（书架列表样式跨方向恢复用）：与 [restoreGridPosition] 同思路，
 * 但目标行定高、无逐帧重申需求——等 item 数稳定后 scrollToItem 落位一次即可。
 */
private suspend fun restoreListPosition(
    state: LazyListState,
    index: Int,
    offset: Int,
    tag: String,
) {
    var prevCount = -1
    var stable = 0
    var attempts = 0
    while (attempts < 40) {
        val count = state.layoutInfo.totalItemsCount
        if (count == prevCount) {
            stable++
        } else {
            stable = 0
            prevCount = count
        }
        if (stable >= 2 && count > index) break
        delay(16)
        attempts++
    }
    if (state.layoutInfo.totalItemsCount > index) {
        runCatching { state.scrollToItem(index, offset) }
    }
    Log.d(
        "InkvolOpen",
        "diag restore $tag done attempts=$attempts " +
            "list=(${state.firstVisibleItemIndex},${state.firstVisibleItemScrollOffset}) " +
            "count=${state.layoutInfo.totalItemsCount}",
    )
}

/**
 * 视图行在网格滚动中的纵向位移（px，正数 = 向上移出）。
 * 网格 item 0 是行高占位：index 0 时按滚动偏移跟手，滚过 item 0 即完全移出。
 * 只应在 graphicsLayer 的 draw 阶段调用（读网格状态不触发重组）。
 */
private fun viewHeaderScrollPx(state: LazyGridState, headerHeightPx: Float): Float {
    if (headerHeightPx <= 0f) return 0f
    return if (state.firstVisibleItemIndex > 0) {
        headerHeightPx
    } else {
        state.firstVisibleItemScrollOffset.toFloat().coerceIn(0f, headerHeightPx)
    }
}

/** 列表容器版（书夹列表样式）：语义与网格版一致（item 0 = 行高占位 Spacer）。 */
private fun viewHeaderScrollPx(state: LazyListState, headerHeightPx: Float): Float {
    if (headerHeightPx <= 0f) return 0f
    return if (state.firstVisibleItemIndex > 0) {
        headerHeightPx
    } else {
        state.firstVisibleItemScrollOffset.toFloat().coerceIn(0f, headerHeightPx)
    }
}

/** 卡片拖拽排序的瞬时状态；baseBounds 在拖拽开始时快照，避免移动中的卡片反馈干扰目标计算。 */
private data class DragSortState(
    val id: Long,
    val offset: Offset,
    val targetId: Long?,
    val baseBounds: Map<Long, Rect>,
)

private fun DragSortState.withDrag(amount: Offset): DragSortState {
    val nextOffset = offset + amount
    val draggedCenter = baseBounds[id]?.center?.plus(nextOffset) ?: return copy(
        offset = nextOffset,
        targetId = id,
    )
    val targetId = baseBounds
        .filterKeys { it != id }
        .minByOrNull { (_, bounds) ->
            val center = bounds.center
            val dx = center.x - draggedCenter.x
            val dy = center.y - draggedCenter.y
            dx * dx + dy * dy
        }?.key ?: id
    return copy(offset = nextOffset, targetId = targetId)
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun LibraryScreen(
    settingsRepository: SettingsRepository,
    libraryRepository: LibraryRepository,
    // M4.5 同步（删除确认卡的同步范围提示）
    syncRepository: com.inkvol.app.data.sync.SyncRepository? = null,
    /** 平板完整侧栏已有导入入口时，标题行不再重复加号 */
    showHeaderImport: Boolean = true,
    onOpenBook: (BookRecord, ReaderOpenRect?) -> Unit = { _, _ -> },
    /** 加号菜单 → 书库管理（路由 library-manager，与设置页入口同款） */
    onOpenLibraryManager: () -> Unit = {},
) {
    val context = LocalContext.current
    val chrome = LocalGlassChrome.current
    val scope = rememberCoroutineScope()
    val scrollToTop = LocalScrollToTop.current
    val glassNotice = LocalGlassNotice.current
    // 网格滚动状态不走 rememberSaveable（NavHost 的恢复快照不可靠——会被首帧数据
    // 生长覆盖成 (0,0)，且折叠状态机会被这个假位置翻成展开再折叠）。改用进程级
    // LibraryGridScroll 直接构造：每次进入书架（含一镜到底返回）首帧就持有保存的
    // 滚动位置，折叠状态机首采样即正确；进程被杀后重开自然回顶。
    // 进入书架时的视图档（prefs 同步快照，与 map 键生成 / 一镜到底锚点恢复同源；
    // "booklist" 旧值回落书夹视图）。remember 钉死一次，避免 prefs 异步加载后漂移。
    val initialViewMode = remember {
        settingsRepository.cachedPreferences()?.let {
            val stored = settingsRepository.stringOf(it, SettingsKeys.LIBRARY_VIEW, SettingsKeys.VIEW_FOLDER)
            if (stored == "booklist") SettingsKeys.VIEW_FOLDER else stored
        } ?: SettingsKeys.VIEW_FOLDER
    }
    // 视图（书夹/系列/平铺）× 分类（全部/漫画/小说）共 9 个组合各自持有独立
    // LazyGridState：
    // ① 视图/分类切换动画期间新旧两页并存（AnimatedContent），共用一个状态会触发
    //   已知的「滚动位置串味」（二级页 targetGridState 同款教训）；
    // ② 每个组合记住自己的滚动位置，切回时原位恢复（导航 Tab 同语义）。
    // 恢复目标组合 = 离开书架时记录的「事实」（LibraryGridScroll.viewMode/typeFilter），
    // 进程刚启动（holder 为 null）才回落到 prefs 快照 + LibraryUiState.typeFilter。
    // 之前用「进书架时的 prefs 快照」反推：只要与离开时的实时值不同源（prefs 首帧为
    // null / 书架内切过视图），带保存值的就是另一个组合，显示的那页从 (0,0) 开始、
    // 恢复 effect 还作用在未组合的网格上（settled 卡住）——chips/视图切换后的回顶源头。
    val restoreViewMode = remember { LibraryGridScroll.viewMode ?: initialViewMode }
    val restoreTypeFilter = remember {
        if (LibraryGridScroll.viewMode != null) {
            LibraryGridScroll.typeFilter
        } else {
            LibraryUiState.typeFilter
        }
    }
    val gridStates = remember {
        buildMap<Pair<String, String?>, LazyGridState> {
            for (vm in libraryViewOrder) {
                for (tf in libraryTypeOrder) {
                    put(
                        vm to tf,
                        if (vm == restoreViewMode && tf == restoreTypeFilter) {
                            LazyGridState(
                                firstVisibleItemIndex = LibraryGridScroll.index,
                                firstVisibleItemScrollOffset = LibraryGridScroll.offset,
                            )
                        } else {
                            LazyGridState()
                        },
                    )
                }
            }
        }
    }
    // 书架列表样式（library_shelf_view=list）一级页的滚动状态：视图 × 分类 9 个组合
    // 各持一份 LazyListState（书夹/系列/平铺列表共用此 map——AnimatedContent 转场期间
    // 新旧两页并存，共用状态会串味，网格 gridStates 同款教训）。离开书架时若列表样式
    // 活跃，位置落进 LibraryShelfListScroll（进程级），再次进入书架首帧恢复——与
    // LibraryGridScroll 同范式；网格/列表两套状态互不覆盖，样式来回切换各自原位恢复。
    val shelfListStates = remember {
        buildMap<Pair<String, String?>, LazyListState> {
            for (vm in libraryViewOrder) {
                for (tf in libraryTypeOrder) {
                    put(
                        vm to tf,
                        if (LibraryShelfListScroll.active &&
                            LibraryShelfListScroll.viewMode == vm &&
                            LibraryShelfListScroll.typeFilter == tf
                        ) {
                            LazyListState(
                                firstVisibleItemIndex = LibraryShelfListScroll.index,
                                firstVisibleItemScrollOffset = LibraryShelfListScroll.offset,
                            )
                        } else {
                            LazyListState()
                        },
                    )
                }
            }
        }
    }
    // 二级页网格：每个 target 一个实例（普通 HashMap，非响应式——实例不需要触发重组）。
    // 放在顶层按需创建而不是内容 lambda 里的 remember(t)：① 头部状态机与恢复逻辑要按
    // 「当前显示的网格」取状态，必须能在顶层拿到；② AnimatedContent 退场页按旧 t 仍取到
    // 旧实例，不会当场回顶；③ 初值直接吃 LibraryDetailScroll（一镜到底返回恢复位置）。
    val detailStates = remember { HashMap<String, LazyGridState>() }
    fun detailStateOf(t: LibraryTarget): LazyGridState =
        detailStates.getOrPut(targetKeyOf(t).orEmpty()) {
            val saved = LibraryDetailScroll.takeIf {
                it.key == targetKeyOf(t) && !it.isList
            }
            if (saved != null) {
                LazyGridState(saved.index, saved.offset)
            } else {
                LazyGridState()
            }
        }
    // 二级页列表样式（library_shelf_view=list）的滚动状态：与网格 detailStates 平行、
    // 各自独立（同一 target 同一时刻只显示一种样式，但样式来回切换时各自原位恢复，
    // 与一级 gridStates / shelfListStates 同款「网格/列表互不覆盖」语义）。
    val detailListStates = remember { HashMap<String, LazyListState>() }
    fun detailListStateOf(t: LibraryTarget): LazyListState =
        detailListStates.getOrPut(targetKeyOf(t).orEmpty()) {
            val saved = LibraryDetailScroll.takeIf {
                it.key == targetKeyOf(t) && it.isList
            }
            if (saved != null) {
                LazyListState(
                    firstVisibleItemIndex = saved.index,
                    firstVisibleItemScrollOffset = saved.offset,
                )
            } else {
                LazyListState()
            }
        }
    // 二级页 target 提前声明：头部折叠/标题行/恢复都要按当前页面取网格。
    var target by remember { mutableStateOf(LibraryUiState.target) }
    // 分类筛选：与 LibraryUiState.typeFilter 同源的本地快照态（chips 写入）。
    // 声明位置必须在 gridStates 之后、各消费点之前——头部状态机 / 恢复 /
    // 回顶 / 离页保存都按「当前组合」取状态。
    var typeFilter by remember { mutableStateOf(LibraryUiState.typeFilter) }
    val configuration = LocalConfiguration.current
    // 网格回报稳定信号（B 层）：Closing 期间卡片回报 sourceRect 只有在网格落位
    // 稳定后才可信——恢复期的数据生长与逐帧重申会让卡片上报瞬态坐标（§9.16 的
    // 污染源）。settled=false 期间 controller 忽略回报；首个 settled 回报同时
    // 解除「旋转后返回」的起飞闸。时间门限（250ms）由本信号替代。
    var gridSettled by remember { mutableStateOf(false) }
    val pageBackground = MaterialTheme.colorScheme.background
    val pageBackdrop = rememberLayerBackdrop(onDraw = {
        drawRect(pageBackground)
        drawContent()
    })

    // 一镜到底返回：网格滚动位置恢复（一级 / 二级同一套两段式落位）。
    // 返回后 item 数会先少后多（诊断见 5→16 生长），首帧/异步补滚都会被这次数据重构
    // 顶回 index 0：所以等 item 数连续两帧稳定、且足以容纳目标 index 之后再落位。
    // 恢复目标是「当前真正显示的网格」：二级页返回时一级网格根本没组合，对它
    // scrollToItem 既无效、又会把 settled 拖到 ~1.28s 之后（宿主起飞闸 800ms）。
    LaunchedEffect(Unit) {
        val savedLandscape = LibraryGridScroll.landscape
        val landscapeNow = configuration.screenWidthDp > configuration.screenHeightDp
        val crossOrientation = savedLandscape != null && savedLandscape != landscapeNow
        val detailTarget = LibraryUiState.target
        if (detailTarget != null) {
            // ---- 二级页（书夹 / 系列 / 智能书夹）----
            if (LibraryDetailScroll.isList) {
                // 列表样式（library_shelf_view=list）：初值已由 detailListStateOf 携带。
                // 非跨方向无需再滚；跨方向 offset 是旧方向像素语义 → 归零 + 锚点优先
                //（书行会开书，返回动画的飞回目标行要在视口内能回报坐标）。
                if (crossOrientation) {
                    val snapshot = settingsRepository.cachedPreferences()
                    val restoreSortMode = snapshot?.let {
                        settingsRepository.stringOf(it, SettingsKeys.LIBRARY_SORT, SettingsKeys.SORT_RECENT)
                    } ?: SettingsKeys.SORT_RECENT
                    val restoreSortDescending = snapshot?.let {
                        settingsRepository.stringOf(it, SettingsKeys.LIBRARY_SORT_DESC, "1")
                    } != "0"
                    val restoreSeriesMode = snapshot?.let {
                        settingsRepository.stringOf(
                            it,
                            SettingsKeys.LIBRARY_SERIES_MODE,
                            SettingsKeys.SERIES_MODE_HIERARCHY,
                        )
                    } ?: SettingsKeys.SERIES_MODE_HIERARCHY
                    // 列表态：子系列区前置「系列」标题 + 子系列行 +（有直属书时的「书籍」标题），
                    // 书序号偏移 = 1 + 子系列行数 +（直属书 > 0 时再 +1）。
                    val detailListSubOffset = if (
                        restoreSeriesMode == SettingsKeys.SERIES_MODE_HIERARCHY &&
                        detailTarget is LibraryTarget.Series
                    ) {
                        val all = libraryRepository.books.value
                        val tf = LibraryUiState.typeFilter
                        val source = if (tf == null) all else all.filter { it.type == tf }
                        val subCount = childSeriesNodes(source, detailTarget.path).size
                        if (subCount > 0) {
                            val direct = source.count {
                                leafSeriesOf(it).first == detailTarget.path
                            }
                            1 + subCount + if (direct > 0) 1 else 0
                        } else {
                            0
                        }
                    } else {
                        0
                    }
                    val listIndex = LibraryGridScroll.anchorBookId?.let { anchorId ->
                        sortBooks(
                            booksOf(
                                detailTarget,
                                libraryRepository.books.value,
                                libraryRepository.categoryBookIds.value,
                                restoreSeriesMode,
                            ),
                            restoreSortMode,
                            restoreSortDescending,
                        ).indexOfFirst { it.id == anchorId }.takeIf { it >= 0 }
                            ?.plus(detailListSubOffset)
                    } ?: LibraryDetailScroll.index
                    restoreListPosition(
                        detailListStateOf(detailTarget),
                        listIndex,
                        0,
                        "detail-list",
                    )
                }
                gridSettled = true
                Log.d("InkvolOpen", "diag restore gridSettled=true (detail-list)")
                return@LaunchedEffect
            }
            // 网格样式：跨方向时锚点重算（层级系列页网格前有子系列标题/卡/书籍标题条目）。
            var index = LibraryDetailScroll.index
            var offset = LibraryDetailScroll.offset
            if (crossOrientation) {
                // 跨方向：index 是线性序号仍有效；offset 是旧方向像素语义 → 归零；
                // 锚点优先，保证返回动画的飞回目标卡在新方向视口内、能回报坐标。
                val snapshot = settingsRepository.cachedPreferences()
                // 二级页排序与一级书架共用全局档（全 App 一份排序，SSMH 同款）
                val restoreSortMode = snapshot?.let {
                    settingsRepository.stringOf(it, SettingsKeys.LIBRARY_SORT, SettingsKeys.SORT_RECENT)
                } ?: SettingsKeys.SORT_RECENT
                val restoreSortDescending = snapshot?.let {
                    settingsRepository.stringOf(it, SettingsKeys.LIBRARY_SORT_DESC, "1")
                } != "0"
                val restoreSeriesMode = snapshot?.let {
                    settingsRepository.stringOf(
                        it,
                        SettingsKeys.LIBRARY_SERIES_MODE,
                        SettingsKeys.SERIES_MODE_HIERARCHY,
                    )
                } ?: SettingsKeys.SERIES_MODE_HIERARCHY
                // 层级系列页的网格前面多了「子系列标题 + 系列卡 + 书籍标题」条目：
                // 锚点算出的书本序号要加这段偏移，否则旋转返回会落偏到子系列区。
                val detailSubOffset = if (
                    restoreSeriesMode == SettingsKeys.SERIES_MODE_HIERARCHY &&
                    detailTarget is LibraryTarget.Series
                ) {
                    val all = libraryRepository.books.value
                    val tf = LibraryUiState.typeFilter
                    val source = if (tf == null) all else all.filter { it.type == tf }
                    val subCount = childSeriesNodes(source, detailTarget.path).size
                    // 子系列标题 + 系列卡 +（有直属书时的「书籍」标题）
                    if (subCount > 0) {
                        val direct = source.count { leafSeriesOf(it).first == detailTarget.path }
                        subCount + 1 + if (direct > 0) 1 else 0
                    } else {
                        0
                    }
                } else {
                    0
                }
                index = LibraryGridScroll.anchorBookId?.let { anchorId ->
                    sortBooks(
                        booksOf(
                            detailTarget,
                            libraryRepository.books.value,
                            libraryRepository.categoryBookIds.value,
                            restoreSeriesMode,
                        ),
                        restoreSortMode,
                        restoreSortDescending,
                    ).indexOfFirst { it.id == anchorId }.takeIf { it >= 0 }
                        ?.plus(detailSubOffset)
                } ?: LibraryDetailScroll.index
                offset = 0
            }
            restoreGridPosition(detailStateOf(detailTarget), index, offset, "detail")
            // 恢复完成：放行卡片回报（onGloballyPositioned 只在位置变化时回调，
            // 落位后不再有回调，卡片会在 settled 翻转时用最后已知位置补发一次）。
            gridSettled = true
            Log.d("InkvolOpen", "diag restore gridSettled=true (detail)")
            return@LaunchedEffect
        }
        // ---- 一级书架（视图 × 分类组合）----
        // 列表样式（library_shelf_view=list，三视图联动）回来时显示页是 LazyColumn：
        // 跳过网格恢复——restoreGridPosition 会去滚一个没组合的 LazyGridState（settling
        // 等不到 totalItemsCount），拖住本协程、gridSettled 放不出来（状态栏显隐被挂起）。
        // 列表位置已由 shelfListStates 初值携带（holder 恢复）。
        val restoreShelfView = settingsRepository.cachedPreferences()?.let {
            settingsRepository.stringOf(
                it,
                SettingsKeys.LIBRARY_SHELF_VIEW,
                SettingsKeys.SHELF_VIEW_GRID,
            )
        } ?: SettingsKeys.SHELF_VIEW_GRID
        if (restoreShelfView == SettingsKeys.SHELF_VIEW_LIST) {
            if (crossOrientation) {
                // 跨方向：列表行定高，index（线性序号）仍有效、offset 是旧方向像素语义
                // → 归零。平铺列表的书行会开书，优先锚点到最近打开的书（返回动画的
                // 飞回目标行要在视口内能回报坐标）；书夹/系列行不开书退回原 index。
                // 排序档走 cachedPreferences 同步快照（网格跨方向恢复同款模式）。
                // item 0 = 视图行等高占位，书序号 +1 补偿。
                var listIndex = LibraryShelfListScroll.index
                if (restoreViewMode == SettingsKeys.VIEW_FLAT) {
                    val anchorId = LibraryGridScroll.anchorBookId
                    if (anchorId != null) {
                        val snapshot = settingsRepository.cachedPreferences()
                        val flatBooks = sortBooks(
                            when (restoreTypeFilter) {
                                null -> libraryRepository.books.value
                                else -> libraryRepository.books.value.filter {
                                    it.type == restoreTypeFilter
                                }
                            },
                            snapshot?.let {
                                settingsRepository.stringOf(
                                    it,
                                    SettingsKeys.LIBRARY_SORT,
                                    SettingsKeys.SORT_RECENT,
                                )
                            } ?: SettingsKeys.SORT_RECENT,
                            snapshot?.let {
                                settingsRepository.stringOf(it, SettingsKeys.LIBRARY_SORT_DESC, "1")
                            } != "0",
                        )
                        flatBooks.indexOfFirst { it.id == anchorId }
                            .takeIf { it >= 0 }
                            ?.plus(1)
                            ?.let { listIndex = it }
                    }
                }
                restoreListPosition(
                    shelfListStates.getValue(restoreViewMode to restoreTypeFilter),
                    listIndex,
                    0,
                    "shelf-list",
                )
            }
            gridSettled = true
            Log.d("InkvolOpen", "diag restore gridSettled=true (shelf-list)")
            return@LaunchedEffect
        }
        val restoreState = gridStates.getValue(restoreViewMode to restoreTypeFilter)
        var index = LibraryGridScroll.index
        var offset = LibraryGridScroll.offset
        if (crossOrientation) {
            // 跨方向恢复（阅读期间旋转过再返回）：index 是线性序号仍然有效，
            // offset 是旧方向的像素语义、直接用会落错位置。锚点优先——定位到
            // 最近打开的书（返回动画的飞回目标），保证目标卡在视口内、能回报
            // 新方向下的正确坐标；flat 视图网格 = 视图行占位(item 0) + books。
            // 查不到（书夹/系列视图、书已删、数据未到）退回原 index。
            // 视图档用 restoreViewMode（离开书架时记录的组合，与网格初值同源）。
            index = LibraryGridScroll.anchorBookId?.let { anchorId ->
                if (restoreViewMode == SettingsKeys.VIEW_FLAT) {
                    // 分类档下网格是过滤+排序后的列表，index 必须在同一序列上求值，
                    // 否则会定位到错误的书（排序改动后网格顺序 ≠ SQL 顺序）。
                    // flat 网格 item 0 = 视图行等高占位，books 从 item 1 起，
                    // 需 +1 补偿（与网格结构同步）。
                    // 排序档走 cachedPreferences 同步快照（initialViewMode 同款模式）：
                    // 本 effect 只在进入书架首帧跑，快照值与网格首帧渲染档位同源，
                    // 且不依赖下方才声明的 prefs 响应值。
                    val snapshot = settingsRepository.cachedPreferences()
                    val restoreSortMode = snapshot?.let {
                        settingsRepository.stringOf(it, SettingsKeys.LIBRARY_SORT, SettingsKeys.SORT_RECENT)
                    } ?: SettingsKeys.SORT_RECENT
                    val restoreSortDescending = snapshot?.let {
                        settingsRepository.stringOf(it, SettingsKeys.LIBRARY_SORT_DESC, "1")
                    } != "0"
                    val sourceBooks = sortBooks(
                        when (restoreTypeFilter) {
                            null -> libraryRepository.books.value
                            else -> libraryRepository.books.value.filter {
                                it.type == restoreTypeFilter
                            }
                        },
                        restoreSortMode,
                        restoreSortDescending,
                    )
                    sourceBooks
                        .indexOfFirst { it.id == anchorId }
                        .takeIf { it >= 0 }
                        ?.plus(1)
                } else {
                    null
                }
            } ?: LibraryGridScroll.index
            offset = 0
        }
        restoreGridPosition(restoreState, index, offset, "grid")
        // 恢复完成：放行卡片回报（onGloballyPositioned 只在位置变化时回调，
        // 落位后不再有回调，卡片会在 settled 翻转时用最后已知位置补发一次，
        // 保证「旋转后返回」的起飞闸能收到首条可信坐标）。
        gridSettled = true
        Log.d("InkvolOpen", "diag restore gridSettled=true")
    }

    val books by libraryRepository.books.collectAsStateWithLifecycle()
    val categories by libraryRepository.categories.collectAsStateWithLifecycle()
    val categoryBookIds by libraryRepository.categoryBookIds.collectAsStateWithLifecycle()
    val scan by libraryRepository.scan.collectAsStateWithLifecycle()
    val notice by libraryRepository.notice.collectAsStateWithLifecycle()
    // initialValue 用内存缓存快照：首帧 viewMode 即为实际值（如平铺），避免 prefs
    // 异步加载完成时网格结构翻转、锚点被夹回 (0,0)——返回动画下的书架瞬态闪烁。
    val prefs by settingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = settingsRepository.cachedPreferences(),
    )

    // 历史版本持久化过 "booklist"（书单视图已合并进书夹），旧值回落书夹视图
    val viewMode = prefs?.let {
        val stored = settingsRepository.stringOf(it, SettingsKeys.LIBRARY_VIEW, SettingsKeys.VIEW_FOLDER)
        if (stored == "booklist") SettingsKeys.VIEW_FOLDER else stored
    } ?: SettingsKeys.VIEW_FOLDER
    val plusAction = prefs?.let {
        settingsRepository.stringOf(it, SettingsKeys.PLUS_ACTION, SettingsKeys.PLUS_ASK)
    } ?: SettingsKeys.PLUS_ASK
    // 一级书架全局排序（与平板侧栏排序菜单同一持久化真源）：缺省 recent + 降序
    // = 旧版 SQL ORDER BY added_at DESC 的原样行为，升级/全新安装零感知。
    val sortMode = prefs?.let {
        settingsRepository.stringOf(it, SettingsKeys.LIBRARY_SORT, SettingsKeys.SORT_RECENT)
    } ?: SettingsKeys.SORT_RECENT
    val sortDescending = prefs?.let {
        settingsRepository.stringOf(it, SettingsKeys.LIBRARY_SORT_DESC, "1")
    } != "0"
    // 系列视图结构（设置→书架）：层级（默认）/ 扁平。共用同一份 folder_path，
    // 切换只改渲染、不重扫；未知值回落层级。
    val seriesMode = prefs?.let {
        settingsRepository.stringOf(
            it,
            SettingsKeys.LIBRARY_SERIES_MODE,
            SettingsKeys.SERIES_MODE_HIERARCHY,
        )
    } ?: SettingsKeys.SERIES_MODE_HIERARCHY
    // 书夹视图（一级）书夹卡排序：单独记忆（library_folder_sort / _desc），缺省
    // custom + 升序 = 现状拖拽序 + 名称自然方向，与全局书排序互不影响。
    val folderSortMode = prefs?.let {
        settingsRepository.stringOf(it, SettingsKeys.LIBRARY_FOLDER_SORT, SettingsKeys.FOLDER_SORT_CUSTOM)
    } ?: SettingsKeys.FOLDER_SORT_CUSTOM
    val folderSortDescending = prefs?.let {
        settingsRepository.stringOf(it, SettingsKeys.LIBRARY_FOLDER_SORT_DESC, "0")
    } == "1"
    // 书架展示样式：grid 网格（缺省=现状卡）/ list 列表（仿历史页行卡）。作用于
    // 书夹/系列/平铺三视图的一级页（全局一份联动）；二级页不受影响。
    // 设置页与两个排序菜单入口写同一键、此处派生即时生效。冷启动 prefs 缓存未热时
    // 会短暂显示默认网格再淡入实际样式（viewMode 同款已知行为）。
    val folderView = prefs?.let {
        settingsRepository.stringOf(it, SettingsKeys.LIBRARY_SHELF_VIEW, SettingsKeys.SHELF_VIEW_GRID)
    } ?: SettingsKeys.SHELF_VIEW_GRID

    // 闭包/effect 内读「实时视图档」：viewMode 是普通 val（重组时重建），LaunchedEffect /
    // onDispose 这类不随重组重启的闭包捕获的是首帧值（切过视图后会陈旧），必须经
    // rememberUpdatedState 中转；typeFilter 是快照态委托，读到的天然是最新值。
    val currentViewMode by rememberUpdatedState(viewMode)
    val currentFolderView by rememberUpdatedState(folderView)

    // 离开书架（进阅读器）时记下滚动位置，供下次进入恢复。
    // 取「当前组合」的状态：currentViewMode/typeFilter 经委托读取，onDispose 时刻
    // 拿到的是离开前的最新值——与 LibraryUiState.typeFilter 落盘同源，下次进入
    // 恢复到同一组合。
    DisposableEffect(Unit) {
        onDispose {
            // 一级：位置 + 「离开时的组合」（恢复用事实，不再靠 prefs 快照反推）
            val leavingState = gridStates.getValue(currentViewMode to typeFilter)
            LibraryGridScroll.index = leavingState.firstVisibleItemIndex
            LibraryGridScroll.offset = leavingState.firstVisibleItemScrollOffset
            LibraryGridScroll.viewMode = currentViewMode
            LibraryGridScroll.typeFilter = typeFilter
            // 二级页：同一时刻只有一个存活，位置同样落进进程级 holder
            //（key 不匹配时不会被采用，保留「退出再进自然回顶」语义）。
            // 列表样式与网格各自独立记录（isList 标记），样式来回切换各自原位恢复。
            val leavingTarget = target
            if (leavingTarget != null) {
                val targetKey = targetKeyOf(leavingTarget).orEmpty()
                LibraryDetailScroll.key = targetKey
                LibraryDetailScroll.isList = currentFolderView == SettingsKeys.SHELF_VIEW_LIST
                if (LibraryDetailScroll.isList) {
                    detailListStates[targetKey]?.let {
                        LibraryDetailScroll.index = it.firstVisibleItemIndex
                        LibraryDetailScroll.offset = it.firstVisibleItemScrollOffset
                    }
                } else {
                    detailStates[targetKey]?.let {
                        LibraryDetailScroll.index = it.firstVisibleItemIndex
                        LibraryDetailScroll.offset = it.firstVisibleItemScrollOffset
                    }
                }
            }
            // 记录离开时的方向：下次进入若方向不同（阅读期间旋转过再返回），
            // 恢复逻辑走跨方向路径（index 保留、offset 归零 + 锚点校准）。
            // 方向用网格视口的实际宽高判断，不能读 onDispose 闭包捕获的
            // configuration——DisposableEffect(Unit) 只在首次组合安装，旋转
            // 重组不重装，闭包里的方向永远是首帧值（实测横屏打开存了 false）。
            // M6.3：此处曾打 Log.d，实测主线程卡在 logd 写入 21.5s 引发 ANR，
            // 关键路径的日志已全部移除。
            val viewport = leavingState.layoutInfo.viewportSize
            LibraryGridScroll.landscape =
                viewport.width > viewport.height && viewport.width > 0
            // 书架列表样式：离开时若列表活跃，位置落进独立 holder（与网格互不覆盖，
            // 网格/列表各自记住自己的位置）。非活跃时只翻 active 位——保留列表自己的
            // 记忆值，但恢复链据此知道「离开时显示的不是列表」。
            val leavingShelfList = currentFolderView == SettingsKeys.SHELF_VIEW_LIST
            LibraryShelfListScroll.active = leavingShelfList
            if (leavingShelfList) {
                val leavingListState = shelfListStates.getValue(currentViewMode to typeFilter)
                LibraryShelfListScroll.viewMode = currentViewMode
                LibraryShelfListScroll.typeFilter = typeFilter
                LibraryShelfListScroll.index = leavingListState.firstVisibleItemIndex
                LibraryShelfListScroll.offset = leavingListState.firstVisibleItemScrollOffset
                val listViewport = leavingListState.layoutInfo.viewportSize
                LibraryShelfListScroll.landscape =
                    listViewport.width > listViewport.height && listViewport.width > 0
            }
        }
    }

    // ---- 一镜到底返回：冻结状态栏让位 ----
    // Reader 会隐藏系统栏，返回时 statusBars inset 有 ~250ms 回归动画，实时值会让
    // 标题/chips/内容整体先高后落（「内容叠一下标题才回位」）。冻结值 = 进阅读器前
    // 系统栏显示时的高度（rememberSaveable 跨路由保存，等于回归完成后的终值），
    // 仅在 Closing 期间使用，其它场景一律实时值，滚动自动隐藏状态栏等既有行为零变化。
    val readerOpenCtl = LocalReaderOpenController.current
    // 冻结值必须取「回归完成后的终值」——用 statusBarsIgnoringVisibility（与显隐无关、
    // 恒等于显示态高度），不能用实时 statusBars：小说打开时宿主/阅读器会 hide 系统栏，
    // 0→40 的过渡中间值（实测 0.38 / 0.76dp）会在 Library 卸载前被写进 rememberSaveable，
    // 下次返回时被当成冻结让位 → 标题先上移一个状态栏高度再回落。
    val stableStatusBarTop = WindowInsets.statusBarsIgnoringVisibility
        .asPaddingValues()
        .calculateTopPadding()
    var savedStatusBarTop by rememberSaveable { mutableStateOf(0f) }
    LaunchedEffect(stableStatusBarTop.value, readerOpenCtl?.phase) {
        if (readerOpenCtl?.phase != ReaderOpenPhase.Closing && stableStatusBarTop.value > 0f) {
            savedStatusBarTop = stableStatusBarTop.value
        }
    }
    // 阈值 1dp：历史（旧版本/旧会话）可能已经把 0.38 这类过渡值存进 saveable，
    // 直接当冻结值就是那一次「标题跳到状态栏位置」。
    val frozenStatusBarTop = if (
        readerOpenCtl?.phase == ReaderOpenPhase.Closing && savedStatusBarTop > 1f
    ) {
        savedStatusBarTop.dp
    } else {
        null
    }
    // 「当前活跃网格」= 当前真正在显示、在滚动的那个网格（一级组合 / 二级页）。
    // 头部状态机必须绑定它：
    // ① 二级页上滑才能折叠头部——绑定一级网格时 canScrollForward/Backward 恒 false，
    //    命中「短内容不折叠」守卫直接放行滚动，头部永远不折；
    // ② 返回二级页时 atTop 不再读「未组合的一级网格」（恒在顶部 → 状态栏被强制唤出，
    //    与进书前的隐藏态打架 = 用户看到的「状态栏消失又出现」）。
    val activeDetailState = target?.let { detailStateOf(it) }
    val activeDetailListState = target?.let { detailListStateOf(it) }
    val activeGridState = activeDetailState ?: gridStates.getValue(viewMode to typeFilter)
    // 列表样式（三视图联动，一级/二级页统一）时头部绑定列表容器：LazyListState 版重载
    // 与网格版同返回类型、语义一致（折叠驱动 / 短内容守卫）。切样式时实例变化，core 内
    // remember(stateKey) 重建滚动连接；resetKey 拼上 folderView 保证头部滑回展开。
    // 列表容器按「二级页 / 一级」分流：二级页用 detailListStateOf(target)、一级用
    // shelfListStates（与渲染侧 DetailList / FlatList/BookcaseList/SeriesList 同源）。
    val listHeaderActive = folderView == SettingsKeys.SHELF_VIEW_LIST
    val activeListState = activeDetailListState
        ?: shelfListStates.getValue(viewMode to typeFilter)
    val headerState = if (listHeaderActive) {
        rememberGlassHeaderState(
            listState = activeListState,
            // resetKey：样式/视图/分类切换、二级页进出都是离散时机，头部滑回展开。
            resetKey = viewMode to typeFilter to targetKeyOf(target) to folderView,
            frozenStatusBarTop = frozenStatusBarTop,
            closingHold = readerOpenCtl?.phase == ReaderOpenPhase.Closing,
            statusBarHold = !gridSettled,
            // 与网格版同一几何（书夹排上移 10dp 的 108dp）；书架页接进程级 holder。
            // 二级页头部多一行标题行（随折叠一起上移、折满整块出屏）。
            headerContentHeight = 108.dp +
                if (activeDetailListState != null) DETAIL_TITLE_ROW_HEIGHT else 0.dp,
            useLibraryHolder = true,
        )
    } else {
        rememberGlassHeaderState(
            // 切档/进出二级页时实例变化，core 内 remember(stateKey) 会重建滚动连接 / 方向监听。
            gridState = activeGridState,
            // resetKey：视图/分类切换、二级页进出都是离散时机，头部滑回展开
            //（二级页标题行必须在状态栏下方，进页先展开一次）。
            resetKey = viewMode to typeFilter to targetKeyOf(target),
            frozenStatusBarTop = frozenStatusBarTop,
            closingHold = readerOpenCtl?.phase == ReaderOpenPhase.Closing,
            // 恢复落位完成前不动系统栏：Closing 只覆盖反向动画那一段，逐帧重申 + 数据
            // 稳定会延续到 Idle 之后，这期间显隐判定读到的是瞬态位置（先 show 后 hide）。
            statusBarHold = !gridSettled,
            // 书夹排上移 10dp：默认 118 的呼吸留白砍 10，历史/设置页用列表版重载不受影响。
            // 二级页头部多一行标题行（随折叠一起上移、折满整块出屏）。
            headerContentHeight = 108.dp +
                if (activeDetailState != null) DETAIL_TITLE_ROW_HEIGHT else 0.dp,
        )
    }
    // 让位量由折叠量单点导出：折叠量与「前置滚动消费」同源，
    // 折叠期间列表不滚、让位收缩多少内容就上移多少（与头部 1:1 同行）。
    val shelfGridTop = headerState.gridTopPadding

    // 回顶（底栏重复点书架）：滚回顶部后把头部滑回展开态，并轻量刷新书库（仅重读数据库）。
    // collect 闭包读 currentViewMode/typeFilter 最新值：事件落在哪个组合就回哪个组合的顶部。
    // 回顶事件要落在「当前显示网格」上：LaunchedEffect(Unit) 不随重组重启，
    // 直接读 activeGridState 会捕获首帧实例（二级页切进来后仍是旧的一级网格）。
    val currentActiveGrid by rememberUpdatedState(activeGridState)
    // 列表样式回顶：显示 LazyColumn（一级/二级）时滚列表（否则滚网格）。事件闭包读
    // rememberUpdatedState 中转的实时值，不随重组重启、不捕获首帧实例。
    val currentListHeaderActive by rememberUpdatedState(listHeaderActive)
    val currentActiveList by rememberUpdatedState(activeListState)
    LaunchedEffect(Unit) {
        scrollToTop.events.collect {
            if (currentListHeaderActive) {
                val listTopState = currentActiveList
                if (listTopState.layoutInfo.totalItemsCount > 0) {
                    listTopState.animateScrollToItem(0)
                }
            } else {
                // 回顶落在当前真正显示的网格上（二级页时是二级页自己的列表）
                val topState = currentActiveGrid
                if (topState.layoutInfo.totalItemsCount > 0) {
                    topState.animateScrollToItem(0)
                }
            }
            headerState.expand()
            libraryRepository.refresh()
        }
    }

    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var plusMenuOpen by remember { mutableStateOf(false) }
    // 手机端排序菜单（标题行排序钮弹出；与加号菜单互斥，开一个关另一个）
    var sortMenuOpen by remember { mutableStateOf(false) }
    var moveSheetOpen by remember { mutableStateOf(false) }
    var deleteSheetOpen by remember { mutableStateOf(false) }
    var metadataSheetOpen by remember { mutableStateOf(false) }
    // 多选条「⋯」溢出菜单（编辑 / 移出 / 导出 / 分享 / 删除）
    var selectionMenuOpen by remember { mutableStateOf(false) }
    // 阅读类型互转确认（EPUB 小说 ↔ 漫画）
    var retypeSheetOpen by remember { mutableStateOf(false) }
    var retypeTargets by remember { mutableStateOf<List<String>>(emptyList()) }
    var retypeToComic by remember { mutableStateOf(false) }
    // 菜单关闭到退场结束之间保持多选条可见、宿主 chrome 不隐藏：
    // 菜单锚在底部条上，chrome 一收起条就会跟着位移，锚点会漂走
    var selectionMenuHeld by remember { mutableStateOf(false) }
    // 导出 launcher 的待处理数据（单本走 CreateDocument，多本走 OpenDocumentTree）
    var exportSingleTarget by remember { mutableStateOf<BookRecord?>(null) }
    var exportBatchTargets by remember { mutableStateOf<List<BookRecord>>(emptyList()) }
    // 离开书架（开书/切 Tab 被 NavHost 卸载）时保留二级页与筛选；显式清除（回一级、
    // 点回「全部」）会先写 null，离页时自然落 null。与上面的滚动落值互不干扰。
    DisposableEffect(Unit) {
        onDispose {
            LibraryUiState.target = target
            LibraryUiState.typeFilter = typeFilter
        }
    }
    // target 变化实时同步到进程级状态：宿主侧栏排序钮显隐（书夹/系列二级页打开时
    // 也要显示排序钮，与一级平铺/系列视图同规则）读的是 LibraryUiState.target——
    // 原先只在 onDispose 落值，二级页打开过程中宿主读到的是离开书架时的陈旧值，
    // 书夹视图进二级页排序钮恒不出现。实时同步后打开/关闭即时反映，一级书夹视图
    // （target == null）依旧隐藏；一镜到底恢复链读同一值，离开书架时序不变。
    LaunchedEffect(target) { LibraryUiState.target = target }
    var editor by remember { mutableStateOf<CategoryEditorState?>(null) }
    val overlayController = LocalPageOverlayController.current

    // 覆盖层退场期间面板仍在组合里，chrome 必须等它退完再回来（否则两件玻璃同框）
    val overlayPresence = rememberGlassOverlayPresence()
    // 编辑型面板要保留最后一帧数据，否则退场时内容先消失、只剩空玻璃卡缩回去
    var editorHeld by remember { mutableStateOf<CategoryEditorState?>(null) }
    LaunchedEffect(editor) { editor?.let { editorHeld = it } }

    val overlayActive = plusMenuOpen || sortMenuOpen || editor != null ||
        moveSheetOpen || deleteSheetOpen || metadataSheetOpen
    // 用 present（含退场）而不是 target：多选条/底栏都要等面板退完再出现
    val overlayShowing = overlayActive || overlayPresence.present
    // 「⋯」菜单锚在多选条正上方：菜单打开/退场期间不能让宿主底栏淡出，
    // 否则条随 chrome 位移、锚点一起漂走。菜单关闭到退场结束之间保持「占位」。
    LaunchedEffect(selectionMenuOpen, overlayPresence.present) {
        selectionMenuHeld = selectionMenuOpen || (selectionMenuHeld && overlayPresence.present)
    }
    val chromeObscured = overlayActive || (overlayPresence.present && !selectionMenuHeld)
    LaunchedEffect(chromeObscured) { overlayController.setActive(chromeObscured) }
    DisposableEffect(Unit) {
        onDispose { overlayController.setActive(false) }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) libraryRepository.importTree(uri)
    }

    // 单本导出：SAF 保存对话框（用户可改名/选位置），零存储权限
    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val book = exportSingleTarget
        exportSingleTarget = null
        if (uri != null && book != null) {
            scope.launch {
                BookFileTransfer.exportTo(context, book, uri)
                    .onSuccess { glassNotice.show("已导出《${book.title}》") }
                    .onFailure { glassNotice.show("导出失败：${it.message ?: "未知"}") }
            }
        }
    }

    // 多本导出：选一个目录，逐本 createDocument 写入
    val exportTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        val targets = exportBatchTargets
        exportBatchTargets = emptyList()
        if (treeUri != null && targets.isNotEmpty()) {
            scope.launch {
                val resolver = context.contentResolver
                runCatching {
                    resolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                val parent = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri),
                )
                var ok = 0
                var failed = 0
                targets.forEach { book ->
                    val result = runCatching {
                        val mime = BookFileTransfer.mimeOf(BookFileTransfer.extOf(book))
                        val doc = DocumentsContract.createDocument(
                            resolver,
                            parent,
                            mime,
                            BookFileTransfer.fileNameOf(book),
                        ) ?: error("无法创建文件")
                        BookFileTransfer.exportTo(context, book, doc).getOrThrow()
                    }
                    if (result.isSuccess) ok++ else failed++
                }
                glassNotice.show(
                    if (failed == 0) "已导出 $ok 本" else "导出完成 · 成功 $ok · 失败 $failed",
                )
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) libraryRepository.importDocument(uri)
    }

    LaunchedEffect(notice) {
        notice?.let {
            glassNotice.show(it)
            libraryRepository.clearNotice()
        }
    }
    LaunchedEffect(target) {
        selected = emptySet()
    }
    // 返回书架时校验恢复的二级页仍有效（书夹可能在阅读期间被删除或重扫清空），
    // 失效静默落回一级，不弹提示；System 档按 readStatus 过滤，恒有效。
    LaunchedEffect(target, books, categories, seriesMode) {
        val t = target ?: return@LaunchedEffect
        val valid = when (t) {
            is LibraryTarget.User -> categories.any { it.id == t.id }
            is LibraryTarget.System -> true
            is LibraryTarget.Series -> books.any { bookInSeries(it, t.path, seriesMode) }
        }
        if (!valid) target = null
    }

    // 二级页返回：层级系列回父系列（面包屑逐级退出），扁平系列 / 按名入口 / 一级回书架。
    fun closeDetailTarget() {
        val t = target
        val parent = if (t is LibraryTarget.Series &&
            seriesMode != SettingsKeys.SERIES_MODE_FLAT
        ) {
            parentSeriesTarget(t)
        } else {
            null
        }
        if (t != null) {
            detailStates.remove(targetKeyOf(t).orEmpty())
            LibraryDetailScroll.key = null
        }
        target = parent
    }

    // 「⋯」菜单数据源：选中集 → 书本；文件夹型漫画没有单文件，导出/分享置灰
    val selectionTargetUser = target as? LibraryTarget.User
    val selectedBooks = remember(selected, books) { books.filter { it.id in selected } }
    val transferableSelected = remember(selectedBooks) {
        selectedBooks.filter { BookFileTransfer.isTransferable(it) }
    }
    // EPUB/MOBI 小说 / 漫画互转：选中项必须全是 EPUB/MOBI 且类型一致（混合或其它格式置灰）
    val retypeUniformType = selectedBooks.map { it.type }.distinct().size == 1
    val retypeAllConvertible = selectedBooks.isNotEmpty() && selectedBooks.all {
        it.format.equals("EPUB", ignoreCase = true) || it.format.equals("MOBI", ignoreCase = true)
    }
    val retypeSupported = retypeUniformType && retypeAllConvertible
    val retypeTargetIsNovel = retypeSupported && selectedBooks.firstOrNull()?.type == "NOVEL"
    LaunchedEffect(selected.isEmpty()) {
        if (selected.isEmpty()) selectionMenuOpen = false
    }

    BackHandler(
        enabled = selected.isNotEmpty() || target != null || metadataSheetOpen || selectionMenuOpen,
    ) {
        when {
            selectionMenuOpen -> selectionMenuOpen = false
            metadataSheetOpen -> metadataSheetOpen = false
            selected.isNotEmpty() -> selected = emptySet()
            else -> closeDetailTarget()
        }
    }

    fun runPlusAction() {
        when (plusAction) {
            SettingsKeys.PLUS_FOLDER -> folderPicker.launch(null)
            SettingsKeys.PLUS_FILE -> filePicker.launch(arrayOf("*/*"))
            SettingsKeys.PLUS_RESCAN -> libraryRepository.rescanAll()
            else -> {
                sortMenuOpen = false
                plusMenuOpen = true
            }
        }
    }

    val typeChips = listOf(
        GlassHeaderChip(
            key = "all",
            label = "全部",
            selected = typeFilter == null,
            loading = false,
            onClick = { typeFilter = null },
        ),
        GlassHeaderChip(
            key = "comic",
            label = "漫画",
            selected = typeFilter == "COMIC",
            loading = false,
            onClick = { typeFilter = "COMIC" },
        ),
        GlassHeaderChip(
            key = "novel",
            label = "小说",
            selected = typeFilter == "NOVEL",
            loading = false,
            onClick = { typeFilter = "NOVEL" },
        ),
    )

    val filteredBooks = when (typeFilter) {
        null -> books
        else -> books.filter { it.type == typeFilter }
    }

    // 视图 chips 行：横向固定（不再是网格 item，转场时不随页左右滑动），纵向由
    // 当前网格的滚动驱动（覆盖层 translationY，滚过即隐藏——与原 item 0 行为
    // 逐像素一致）。网格 item 0 保留等高空白占位（headerSpacerHeight）维持滚动
    // 空间与索引语义（一镜到底锚点 +1 依赖它）。只渲染一份实例，直接读全局
    // viewMode。行高由 onSizeChanged 测量回填：首帧占位为 0、行会与内容重叠
    // 一帧（屏幕条目进场时不可见，可接受）。
    var viewHeaderHeightPx by remember { mutableStateOf(0f) }
    val viewHeader: @Composable (Modifier) -> Unit = { headerModifier ->
        Row(
            modifier = headerModifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ViewChip("书夹", viewMode == SettingsKeys.VIEW_FOLDER) {
                scope.launch {
                    settingsRepository.putString(SettingsKeys.LIBRARY_VIEW, SettingsKeys.VIEW_FOLDER)
                }
            }
            ViewChip("系列", viewMode == SettingsKeys.VIEW_SERIES) {
                scope.launch {
                    settingsRepository.putString(SettingsKeys.LIBRARY_VIEW, SettingsKeys.VIEW_SERIES)
                }
            }
            ViewChip("平铺", viewMode == SettingsKeys.VIEW_FLAT) {
                scope.launch {
                    settingsRepository.putString(SettingsKeys.LIBRARY_VIEW, SettingsKeys.VIEW_FLAT)
                }
            }
            Spacer(Modifier.weight(1f))
            if (scan.running) {
                Text(
                    text = "扫描中 · ${scan.scanned}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ---- 下拉重扫（chrome 形变式，无浮动指示器）----
    // PullToRefreshBox 只管手势/阈值/触发；视觉反馈在 chrome 上：
    // 手机 = 本文件玻璃头加号钮「+ ↔ 重扫箭头」形变；平板 = 侧栏刷新钮经
    // LibraryPullRefresh 共享拉距跟手转。外壳包在折叠连接的外层——手势链
    // （网格 → headerState.scrollConnection → PullToRefresh → 宿主）保证
    // 「先展开头部，后蓄力刷新」，两段手势 1:1 跟手互不抢。
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(Unit) {
        snapshotFlow { pullState.distanceFraction }
            .collect { LibraryPullRefresh.pullFraction = it }
    }
    DisposableEffect(Unit) {
        onDispose { LibraryPullRefresh.pullFraction = 0f }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    PullToRefreshBox(
        isRefreshing = scan.running,
        onRefresh = { if (!scan.running) libraryRepository.rescanAll() },
        state = pullState,
        // chrome 就是指示器，不画浮动转圈
        indicator = {},
        modifier = Modifier.fillMaxSize(),
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layerBackdrop(pageBackdrop)
            // 头部折叠的手势消费：必须挂在网格的祖先上
            .nestedScroll(headerState.scrollConnection),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp + chrome.railEndInset)
                .padding(top = shelfGridTop),
        ) {
            // 书架 ↔ 书夹/系列二级页左右滑动转场：前进（null → 二级）新页从右
            // 滑入、旧页向左滑出；返回反向。参数与 SettingsScreen 组间导航、
            // InkvolApp Tab 切换逐字同款（280ms / FastOutSlowInEasing），三条链路
            // 动画语言统一。首帧（含一镜到底返回恢复二级页）不触发过渡。
            // 注意：内容 lambda 必须用 AnimatedContent 的参数 t——outgoing 内容
            // 以旧值调用，误读外层 target 会把退场页渲染成新页。
            AnimatedContent(
                targetState = target,
                transitionSpec = {
                    // 一级→二级 = 前进；层级系列下钻 = 前进，上钻 = 后退。
                    val from = initialState
                    val to = targetState
                    val forward = from == null ||
                        (from is LibraryTarget.Series && to is LibraryTarget.Series &&
                            (from.path.isEmpty() || to.path.startsWith("${from.path}/")))
                    val spec = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                    slideInHorizontally(spec) { full -> if (forward) full else -full } togetherWith
                        slideOutHorizontally(spec) { full -> if (forward) -full else full }
                },
                label = "libraryTargetNav",
                modifier = Modifier.fillMaxSize(),
            ) { t ->
                when (t) {
                    null -> {
                        // 视图行横向固定（转场不随页滑动）+ 纵向跟随网格滚动：
                        // 行做成 Box 覆盖层（不占布局空间），translationY = -网格已滚
                        // 距离（graphicsLayer 的 draw 阶段读状态，不触发重组）；网格
                        // item 0 是等高空占位（headerSpacerHeight）保留滚动空间 →
                        // 上滑隐藏与原「行作为 item 0」逐像素一致。
                        // 视图（书夹/系列/平铺）× 分类（全部/漫画/小说）切换：与 InkvolApp
                        // Tab 转场、本文件二级页转场同语言的左右滑动（280ms /
                        // FastOutSlowInEasing）。方向判定：视图档变了按 书夹→系列→平铺，
                        // 否则（分类切换）按 全部→漫画→小说（见 libraryViewOrder /
                        // libraryTypeOrder）。首帧（含二级页返回）不触发过渡。
                        // 注意：内容 lambda 必须用 AnimatedContent 的参数 (vm, tf)——
                        // outgoing 内容以旧值调用，误读外层 viewMode/typeFilter 会把
                        // 退场页渲染成新页。九个组合各自持有独立 LazyGridState
                        //（gridStates）：转场期间新旧两页并存，共用状态会串味，且每个
                        // 组合记住自己的滚动位置、切回原位恢复。
                        val headerSpacerDp = with(LocalDensity.current) {
                            viewHeaderHeightPx.toDp()
                        }
                        // 行位移的驱动源 = 当前组合的网格（转场期间跟进入页）。
                        val headerOverlayState by rememberUpdatedState(
                            gridStates.getValue(viewMode to typeFilter),
                        )
                        // 列表样式（三视图联动）：行位移驱动源切列表容器（视图行在两种
                        // 样式下都是 item 0 占位）。驱动状态按当前视图 × 分类组合取。
                        val headerOverlayListState by rememberUpdatedState(
                            shelfListStates.getValue(viewMode to typeFilter),
                        )
                        val headerOverlayListActive by rememberUpdatedState(
                            folderView == SettingsKeys.SHELF_VIEW_LIST,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                // 左右滑动切换视图：与点击 chips 同语义（触发同一段
                                // 280ms 方向转场，不跟手平移）。左滑=下一视图、右滑=上一
                                // 视图，两端不回绕。仲裁：被跟踪指针一旦被消费（书夹视图
                                // LazyRow 横滚 / 分类卡拖拽排序 / 网格竖向滚动）立即放弃；
                                // 方向占优 |dx| > |dy| × 1.5 且过 48dp 才触发，斜滑/竖滑
                                // 不误触。currentViewMode 经 rememberUpdatedState 中转，
                                // 手势协程不随视图切换重启。
                                .pointerInput(Unit) {
                                    val thresholdPx = 48.dp.toPx()
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes
                                                .firstOrNull { it.id == down.id }
                                                ?: return@awaitEachGesture
                                            if (change.isConsumed) return@awaitEachGesture
                                            if (!change.pressed) return@awaitEachGesture
                                            val dx = change.position.x - down.position.x
                                            val dy = change.position.y - down.position.y
                                            if (kotlin.math.abs(dx) <= thresholdPx ||
                                                kotlin.math.abs(dx) <= kotlin.math.abs(dy) * 1.5f
                                            ) {
                                                continue
                                            }
                                            val current = libraryViewOrder.indexOf(currentViewMode)
                                            if (current < 0) return@awaitEachGesture
                                            val target = if (dx < 0f) current + 1 else current - 1
                                            if (target !in libraryViewOrder.indices) {
                                                return@awaitEachGesture
                                            }
                                            change.consume()
                                            val nextMode = libraryViewOrder[target]
                                            scope.launch {
                                                settingsRepository.putString(
                                                    SettingsKeys.LIBRARY_VIEW,
                                                    nextMode,
                                                )
                                            }
                                            return@awaitEachGesture
                                        }
                                    }
                                },
                        ) {
                            AnimatedContent(
                                targetState = viewMode to typeFilter,
                                transitionSpec = {
                                    val (fromVm, fromTf) = initialState
                                    val (toVm, toTf) = targetState
                                    val forward = if (fromVm != toVm) {
                                        libraryViewOrder.indexOf(toVm) > libraryViewOrder.indexOf(fromVm)
                                    } else {
                                        libraryTypeOrder.indexOf(toTf) > libraryTypeOrder.indexOf(fromTf)
                                    }
                                    val spec = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                                    slideInHorizontally(spec) { full -> if (forward) full else -full } togetherWith
                                        slideOutHorizontally(spec) { full -> if (forward) -full else full }
                                },
                                label = "libraryGridNav",
                                modifier = Modifier.fillMaxSize(),
                            ) { (vm, tf) ->
                                // 三个视图共用同一排序出口（一级全局档 library_sort /
                                // library_sort_desc）：平铺直接是书列表；系列视图在
                                // SeriesGrid 内按成员书聚合键再排；书夹视图页面无书
                                // 排序语义（拼贴封面取前 4 跟随排序，行为无害）。
                                val pageBooks = sortBooks(
                                    when (tf) {
                                        null -> books
                                        else -> books.filter { it.type == tf }
                                    },
                                    sortMode,
                                    sortDescending,
                                )
                                val pageGridState = gridStates.getValue(vm to tf)
                                when (vm) {
                                    SettingsKeys.VIEW_SERIES -> ShelfStyleSwitch(
                                        style = folderView,
                                        list = {
                                            SeriesList(
                                                books = pageBooks,
                                                state = shelfListStates.getValue(vm to tf),
                                                headerSpacerHeight = headerSpacerDp,
                                                chromeBottom = chrome.contentBottomForPage,
                                                seriesMode = seriesMode,
                                                sortMode = sortMode,
                                                descending = sortDescending,
                                                onOpenSeries = { target = it },
                                            )
                                        },
                                        grid = {
                                            SeriesGrid(
                                                books = pageBooks,
                                                state = pageGridState,
                                                sortMode = sortMode,
                                                descending = sortDescending,
                                                headerSpacerHeight = headerSpacerDp,
                                                chromeBottom = chrome.contentBottomForPage,
                                                seriesMode = seriesMode,
                                                onOpenSeries = { target = it },
                                            )
                                        },
                                    )

                                    SettingsKeys.VIEW_FLAT -> ShelfStyleSwitch(
                                        style = folderView,
                                        list = {
                                            FlatList(
                                                books = pageBooks,
                                                state = shelfListStates.getValue(vm to tf),
                                                headerSpacerHeight = headerSpacerDp,
                                                selected = selected,
                                                reportSettled = gridSettled,
                                                chromeBottom = chrome.contentBottomForPage,
                                                onClick = { book, rect ->
                                                    if (selected.isEmpty()) {
                                                        onOpenBook(book, rect)
                                                    } else {
                                                        selected = if (book.id in selected) selected - book.id else selected + book.id
                                                    }
                                                },
                                                onLongClick = { book ->
                                                    selected = if (book.id in selected) selected - book.id else selected + book.id
                                                },
                                            )
                                        },
                                        grid = {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                BookGrid(
                                                    books = pageBooks,
                                                    state = pageGridState,
                                                    headerSpacerHeight = headerSpacerDp,
                                                    selected = selected,
                                                    reportSettled = gridSettled,
                                                    chromeBottom = chrome.contentBottomForPage,
                                                    onClick = { book, rect ->
                                                        if (selected.isEmpty()) {
                                                            onOpenBook(book, rect)
                                                        } else {
                                                            selected = if (book.id in selected) selected - book.id else selected + book.id
                                                        }
                                                    },
                                                    onLongClick = { book ->
                                                        selected = if (book.id in selected) selected - book.id else selected + book.id
                                                    },
                                                )
                                            }
                                        },
                                    )

                                    else -> ShelfStyleSwitch(
                                        style = folderView,
                                        list = {
                                            BookcaseList(
                                                books = pageBooks,
                                                state = shelfListStates.getValue(vm to tf),
                                                headerSpacerHeight = headerSpacerDp,
                                                categories = categories,
                                                categoryBookIds = categoryBookIds,
                                                folderSortMode = folderSortMode,
                                                folderSortDescending = folderSortDescending,
                                                chromeBottom = chrome.contentBottomForPage,
                                                onOpenSystem = { kind ->
                                                    target = LibraryTarget.System(kind)
                                                },
                                                onOpenCategory = { category ->
                                                    target = LibraryTarget.User(
                                                        category.id,
                                                        category.name,
                                                        category.color,
                                                    )
                                                },
                                                onEditCategory = { category ->
                                                    editor = CategoryEditorState(
                                                        id = category.id,
                                                        name = category.name,
                                                        color = category.color,
                                                    )
                                                },
                                                onNewCategory = { editor = CategoryEditorState() },
                                            )
                                        },
                                        grid = {
                                            BookcaseGrid(
                                                books = pageBooks,
                                                state = pageGridState,
                                                headerSpacerHeight = headerSpacerDp,
                                                categories = categories,
                                                categoryBookIds = categoryBookIds,
                                                chromeBottom = chrome.contentBottomForPage,
                                                onOpenSystem = { kind ->
                                                    target = LibraryTarget.System(kind)
                                                },
                                                onOpenCategory = { category ->
                                                    target = LibraryTarget.User(
                                                        category.id,
                                                        category.name,
                                                        category.color,
                                                    )
                                                },
                                                onEditCategory = { category ->
                                                    editor = CategoryEditorState(
                                                        id = category.id,
                                                        name = category.name,
                                                        color = category.color,
                                                    )
                                                },
                                                onNewCategory = { editor = CategoryEditorState() },
                                                folderSortMode = folderSortMode,
                                                folderSortDescending = folderSortDescending,
                                                onReorderCategory = { orderedIds ->
                                                    libraryRepository.reorderCategories(orderedIds)
                                                    // 拖完自动切回自定义顺序档（文件管理器同款行为）
                                                    scope.launch {
                                                        settingsRepository.putString(
                                                            SettingsKeys.LIBRARY_FOLDER_SORT,
                                                            SettingsKeys.FOLDER_SORT_CUSTOM,
                                                        )
                                                    }
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                            // 行覆盖层：组合序在网格之后 = 画在网格之上；
                            // clipToBounds 负责滚出内容区即裁掉。onSizeChanged 回填
                            // 行高供网格占位与消失阈值使用（字体缩放下自适应）。
                            viewHeader(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .onSizeChanged { viewHeaderHeightPx = it.height.toFloat() }
                                    .graphicsLayer {
                                        translationY = -if (headerOverlayListActive) {
                                            viewHeaderScrollPx(
                                                headerOverlayListState,
                                                viewHeaderHeightPx,
                                            )
                                        } else {
                                            viewHeaderScrollPx(
                                                headerOverlayState,
                                                viewHeaderHeightPx,
                                            )
                                        }
                                    },
                            )
                        }
                    }

                    else -> {
                        // 网格实例来自顶层 detailStates（普通 HashMap）：
                        // ① AnimatedContent 退场页按旧 t 仍取旧实例，不会当场回顶；
                        // ② 初值吃 LibraryDetailScroll（一镜到底返回恢复位置）；
                        // ③ 顶层可访问 → 头部状态机/恢复逻辑都能按「当前显示的网格」取。
                        // 标题行已上提到头部层（随折叠一起上移），这里只画网格/列表/空态。
                        val targetGridState = detailStateOf(t)
                        val targetListState = detailListStateOf(t)
                        // 排序与一级书架共用全局档（library_sort / library_sort_desc，
                        // 全 App 一份排序，SSMH 同款）；方向也共用全局键。
                        val targetBooks = sortBooks(
                            // 分类过滤贯穿二级页：智能/我的/系列都只显示当前漫画/小说筛选下的成员
                            booksOf(t, filteredBooks, categoryBookIds, seriesMode),
                            sortMode,
                            sortDescending,
                        )
                        // 层级系列：本节点下的子系列卡（分类筛选同源，子节点聚合其子树书数）
                        val subSeries = if (
                            seriesMode == SettingsKeys.SERIES_MODE_HIERARCHY &&
                            t is LibraryTarget.Series
                        ) {
                            sortSeriesNodes(
                                childSeriesNodes(filteredBooks, t.path),
                                sortMode,
                                sortDescending,
                            )
                        } else {
                            emptyList()
                        }
                        // 层级系列页的书区只放「本目录直属书」：子目录的书已由上面的
                        // 子系列卡代表，父页再铺一遍会重复（计数/排序仍用子树集合）。
                        val gridBooks = if (
                            seriesMode == SettingsKeys.SERIES_MODE_HIERARCHY &&
                            t is LibraryTarget.Series
                        ) {
                            targetBooks.filter { leafSeriesOf(it).first == t.path }
                        } else {
                            targetBooks
                        }
                        val emptyBecauseFiltered = typeFilter != null &&
                            gridBooks.isEmpty() &&
                            subSeries.isEmpty() &&
                            booksOf(t, books, categoryBookIds, seriesMode).isNotEmpty()
                        val emptyTitle = if (t is LibraryTarget.Series) "这个系列还没有书" else null
                        val emptyHint = if (t is LibraryTarget.Series) {
                            "换一个文件夹系列，或回到书架下拉重新扫描。"
                        } else {
                            null
                        }
                        // 二级页同样跟随全局展示样式（library_shelf_view）：网格/列表共用
                        // 同一套数据与空态，仅容器不同（与一级三视图的 ShelfStyleSwitch 同源）。
                        ShelfStyleSwitch(
                            style = folderView,
                            grid = {
                                CategoryContentView(
                                    books = gridBooks,
                                    subSeries = subSeries,
                                    emptyBecauseFiltered = emptyBecauseFiltered,
                                    emptyTitle = emptyTitle,
                                    emptyHint = emptyHint,
                                    state = targetGridState,
                                    selected = selected,
                                    reportSettled = gridSettled,
                                    onToggleSelect = { id ->
                                        selected = if (id in selected) selected - id else selected + id
                                    },
                                    onOpenBook = onOpenBook,
                                    onOpenSeries = { target = it },
                                    chromeBottom = chrome.contentBottomForPage,
                                )
                            },
                            list = {
                                DetailList(
                                    books = gridBooks,
                                    subSeries = subSeries,
                                    emptyBecauseFiltered = emptyBecauseFiltered,
                                    emptyTitle = emptyTitle,
                                    emptyHint = emptyHint,
                                    state = targetListState,
                                    selected = selected,
                                    reportSettled = gridSettled,
                                    onToggleSelect = { id ->
                                        selected = if (id in selected) selected - id else selected + id
                                    },
                                    onOpenBook = onOpenBook,
                                    onOpenSeries = { target = it },
                                    chromeBottom = chrome.contentBottomForPage,
                                )
                            },
                        )
                    }
                }
            }

        }
    }
    }

        GlassHeaderTitleBlock(
            state = headerState,
            title = "书架",
            subtitle = if (books.isEmpty()) {
                "本地漫画与小说"
            } else {
                "共 ${books.size} 本 · ${viewLabel(viewMode)}"
            },
            endInset = chrome.railEndInset,
            modifier = Modifier.align(Alignment.TopCenter),
            actions = {
                // 排序钮（加号左边）：仅平铺/系列视图显示（书夹视图页面无书排序语义，
                // 与平板侧栏排序钮同规则）。玻璃圆钮与 primary 实底加号形成主次区分，
                // 材质与侧栏 RailFab 同语言；与加号菜单互斥（开一个关另一个）。
                // 书架页恒显示：菜单按视图分流（书夹视图一级 = 书夹卡排序菜单，
                // 其余 = 全局书排序菜单），按钮只有一颗不增加数量。
                if (showHeaderImport) {
                    GlassRounded(
                        backdrop = pageBackdrop,
                        tier = GlassTier.Full,
                        radius = 22.dp,
                        blurRadius = GlassDimens.LightBlur,
                        edge = GlassEdge.Outline,
                        modifier = Modifier
                            .size(44.dp)
                            // 点击反馈（ripple）裁成圆形：clip 必须在 clickable 之前，
                            // 否则波纹按矩形 bounds 展开（设计规范 §点击反馈几何一致性）
                            .clip(CircleShape)
                            .clickable {
                                plusMenuOpen = false
                                sortMenuOpen = true
                            },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "排序",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(19.dp),
                        )
                    }
                    // 与加号的间距由 actions Row 的 spacedBy(8.dp) 提供，不再额外加
                    // Spacer（此前 8+8+8=24dp 导致两钮离得过远）
                }
                if (showHeaderImport) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .combinedClickable(
                                onClick = { runPlusAction() },
                                onLongClick = { plusMenuOpen = true },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (scan.running) {
                            // 重扫进行中：重扫箭头自旋（与下拉形变同一视觉语言，
                            // 替代原加载小圈）
                            val spin = rememberInfiniteTransition(label = "rescanSpin")
                            val spinAngle by spin.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    tween(900, easing = LinearEasing),
                                ),
                                label = "rescanAngle",
                            )
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "重新扫描中",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.graphicsLayer { rotationZ = spinAngle },
                            )
                        } else {
                            // 下拉形变：+ 淡出旋出、重扫箭头旋入（拉距驱动，graphicsLayer
                            // 读 LibraryPullRefresh，不进重组）；未拉动时恒为 +，零视觉变化
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "导入",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.graphicsLayer {
                                    val f = LibraryPullRefresh.pullFraction
                                    alpha = (1f - f).coerceIn(0f, 1f)
                                    rotationZ = -f * 90f
                                },
                            )
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.graphicsLayer {
                                    val f = LibraryPullRefresh.pullFraction
                                    alpha = f.coerceIn(0f, 1f)
                                    rotationZ = f * 360f
                                },
                            )
                        }
                    }
                }
            },
        )

        GlassHeaderChipsRow(
            state = headerState,
            chips = typeChips,
            backdrop = pageBackdrop,
            chrome = chrome,
        )

        // ---- 二级页标题行（属于头部）----
        // 它必须画在 header 层而不是 CategoryContentView 内：内容区的顶部让位由
        // expandedTop - extent 派生（折满归 0），放在内容里折到头也只能停在屏幕最上、
        // 不会离屏。放在这里并吃同一个 headerOffsetPx，就与书架标题/chips 刚性同移：
        // 位置 = statusBarTop + 108 - extent，折满（extent = H，H 含本行 56dp）时整行
        // 出屏，网格顶到屏幕最上。
        val detailTitleTarget = target
        if (detailTitleTarget != null) {
            // 计数与二级页网格同源（排序不再由本行承载——全 App 一份全局排序，
            // 入口只有侧栏/一级标题行的排序钮）
            val detailBooks = booksOf(detailTitleTarget, filteredBooks, categoryBookIds, seriesMode)
            val detailTypeLabel = when (typeFilter) {
                "COMIC" -> "漫画"
                "NOVEL" -> "小说"
                else -> null
            }
            // 层级系列页：子系列数量单独提示（标题行空间足够时才有意义，短文案兜底）
            val detailSubCount = if (
                seriesMode == SettingsKeys.SERIES_MODE_HIERARCHY &&
                detailTitleTarget is LibraryTarget.Series
            ) {
                childSeriesNodes(filteredBooks, detailTitleTarget.path).size
            } else {
                0
            }
            // 直属书数：本目录直接放的书，与子系列分开计数（构成一眼看清）
            val detailDirectCount = if (
                detailSubCount > 0 && detailTitleTarget is LibraryTarget.Series
            ) {
                filteredBooks.count { leafSeriesOf(it).first == detailTitleTarget.path }
            } else {
                0
            }
            DetailTitleRow(
                title = detailTypeLabel?.let { "${targetTitle(detailTitleTarget)} · $it" }
                    ?: targetTitle(detailTitleTarget),
                countText = if (selected.isEmpty()) {
                    if (detailSubCount > 0 && detailDirectCount > 0) {
                        "$detailSubCount 个系列 · $detailDirectCount 本直属"
                    } else if (detailSubCount > 0) {
                        "${detailBooks.size} 本 · $detailSubCount 个系列"
                    } else {
                        "${detailBooks.size} 本"
                    }
                } else {
                    "已选 ${selected.size}"
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer { translationY = headerState.headerOffsetPx }
                    .padding(top = headerState.statusBarTop + 108.dp)
                    // start 8dp：IconButton 自带 12dp 内边距，箭头视觉左缘 = 8+12 = 20dp，
                    // 与书架大标题 / 内容区（均 20dp）对齐；否则箭头缩进到 32dp 看着错位。
                    .padding(start = 8.dp, end = 20.dp + chrome.railEndInset),
                onBack = { closeDetailTarget() },
            )
        }

    AnimatedVisibility(
        visible = selected.isNotEmpty() && (!overlayShowing || selectionMenuHeld),
        enter = fadeIn(tween(GlassMotion.OVERLAY_ENTER_MS)) +
            slideInVertically(tween(GlassMotion.OVERLAY_ENTER_MS)) { it / 3 },
        exit = fadeOut(tween(GlassMotion.OVERLAY_EXIT_MS)) +
            slideOutVertically(tween(GlassMotion.OVERLAY_EXIT_MS)) { it / 3 },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        SelectionBar(
            backdrop = pageBackdrop,
            count = selected.size,
            modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp + chrome.railEndInset,
                bottom = chrome.contentBottomForPage + 8.dp,
            ),
            actions = {
                TextButton(onClick = { deleteSheetOpen = true }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = { moveSheetOpen = true }) { Text("书夹") }
                Spacer(Modifier.size(8.dp))
                // 溢出菜单入口：编辑 / 移出 / 导出 / 分享 / 删除 的二级收口。
                // 与「书夹」同一套 M3 Button 主题色 token（primary / onPrimary），
                // 只收窄成 52×40 短胶囊，形状与高度都和书夹齐平。
                Button(
                    onClick = { selectionMenuOpen = true },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(width = 52.dp, height = 40.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            },
        )
    }
    }

    PlusMenuOverlay(
        visible = plusMenuOpen,
        presence = overlayPresence,
        backdrop = pageBackdrop,
        onDismiss = { plusMenuOpen = false },
        onFolder = {
            plusMenuOpen = false
            folderPicker.launch(null)
        },
        onFile = {
            plusMenuOpen = false
            filePicker.launch(arrayOf("*/*"))
        },
        onRescan = {
            plusMenuOpen = false
            libraryRepository.rescanAll()
        },
        onManager = {
            plusMenuOpen = false
            onOpenLibraryManager()
        },
        // 锚点 = 右上角导入钮：菜单从那个角放大出来，不从画面中心胀开。
        // 纵锚跟标题块用同一状态栏真源（挖孔屏等大 inset 设备按钮底边更靠下，
        // 写死的 84dp 会被菜单压住）：状态栏 + 6(标题上留白) + 3(44 按钮在 50 内容里居中)
        // + 44(按钮) + 8(间隙)，与入场动效的 -8dp 起手回落正好衔接。
        alignment = Alignment.TopEnd,
        origin = TransformOrigin(1f, 0f),
        modifier = Modifier
            .padding(top = headerState.statusBarTop + 61.dp, end = 20.dp)
            .widthIn(max = 260.dp)
            .fillMaxWidth(),
    )

    // 多选「⋯」菜单：锚在多选条右上角（⋯ 按钮正上方），从右下角放大出来。
    // 菜单打开期间不让 chrome 让位（chromeObscured 已排除），锚点不会随底栏淡出漂移。
    SelectionMenuOverlay(
        visible = selectionMenuOpen,
        presence = overlayPresence,
        backdrop = pageBackdrop,
        onDismiss = { selectionMenuOpen = false },
        onEdit = {
            selectionMenuOpen = false
            metadataSheetOpen = true
        },
        onRemoveFromCategory = selectionTargetUser?.let { user ->
            {
                selectionMenuOpen = false
                libraryRepository.removeBooksFromCategory(selected, user.id)
                selected = emptySet()
            }
        },
        onRetype = {
            selectionMenuOpen = false
            retypeTargets = selectedBooks.map { it.id }
            retypeToComic = retypeTargetIsNovel
            retypeSheetOpen = true
        },
        retypeTitle = when {
            !retypeSupported -> "切换阅读类型"
            retypeTargetIsNovel -> "按漫画打开（转为漫画）"
            else -> "按小说打开（转为小说）"
        },
        retypeHint = when {
            retypeSupported && retypeTargetIsNovel -> "按图片页重建，小说进度会清除"
            retypeSupported -> "重新解析章节，漫画进度会清除"
            selectedBooks.isEmpty() -> ""
            else -> "仅支持 EPUB / MOBI 小说与漫画互转"
        },
        retypeEnabled = retypeSupported,
        onExport = {
            val targets = transferableSelected
            selectionMenuOpen = false
            if (targets.size == 1) {
                val book = targets.first()
                exportSingleTarget = book
                exportFileLauncher.launch(BookFileTransfer.fileNameOf(book))
            } else if (targets.size > 1) {
                exportBatchTargets = targets
                exportTreeLauncher.launch(null)
            }
        },
        onShare = {
            val targets = transferableSelected
            selectionMenuOpen = false
            when {
                targets.isEmpty() -> glassNotice.show("选中项里没有可分享的单文件书籍")
                targets.size > BookFileTransfer.MAX_SHARE_COUNT ->
                    glassNotice.show("一次最多分享 ${BookFileTransfer.MAX_SHARE_COUNT} 本")
                else -> scope.launch {
                    BookFileTransfer.share(context, targets)
                        .onFailure { glassNotice.show("分享失败：${it.message ?: "未知"}") }
                }
            }
        },
        onDelete = {
            selectionMenuOpen = false
            deleteSheetOpen = true
        },
        canTransfer = transferableSelected.isNotEmpty(),
        alignment = Alignment.BottomEnd,
        origin = TransformOrigin(1f, 1f),
        modifier = Modifier
            .padding(
                start = 20.dp,
                end = 20.dp + chrome.railEndInset + 8.dp,
                // 底 = 多选条底距 + 条高 56 + 间隙 8
                bottom = chrome.contentBottomForPage + 8.dp + 56.dp + 8.dp,
            )
            .widthIn(max = 300.dp)
            .fillMaxWidth(),
    )

    // 阅读类型互转确认：进度会清零，先讲清楚再执行（原文件不受影响）
    if (retypeSheetOpen) {
        AlertDialog(
            onDismissRequest = { retypeSheetOpen = false },
            title = { Text(if (retypeToComic) "转为漫画" else "转为小说") },
            text = {
                Text(
                    if (retypeToComic) {
                        "将以图片页重新建立阅读内容，当前小说进度与章节会清除；原文件不受影响。"
                    } else {
                        "将重新解析章节文本，当前漫画进度与页书签会清除；原文件不受影响。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        retypeSheetOpen = false
                        val ids = retypeTargets
                        val toComic = retypeToComic
                        scope.launch {
                            libraryRepository.retypeBooks(ids, toComic)
                            selected = emptySet()
                        }
                    },
                ) { Text("转换") }
            },
            dismissButton = {
                TextButton(onClick = { retypeSheetOpen = false }) { Text("取消") }
            },
        )
    }

    // 手机端排序菜单：与加号菜单完全同位（同一 top / end / origin，菜单右缘贴屏
    // 幕右 20dp）——贴排序钮左缘会把 260dp 宽的菜单整体拽向屏幕中部，与标题行
    // 视觉脱节。互斥已有：打开本菜单先收加号菜单（点排序钮时），反之亦然。
    // 内容按视图分流：书夹视图一级（无二级页）= 书夹卡排序菜单（独立记忆
    // library_folder_sort）；平铺/系列/二级页 = 全局书排序菜单（library_sort）。
    val folderSortMenuActive = viewMode == SettingsKeys.VIEW_FOLDER && target == null
    if (folderSortMenuActive) {
        FolderSortMenuOverlay(
            visible = sortMenuOpen,
            presence = overlayPresence,
            backdrop = pageBackdrop,
            mode = folderSortMode,
            descending = folderSortDescending,
            onDismiss = { sortMenuOpen = false },
            onPick = { mode ->
                sortMenuOpen = false
                scope.launch {
                    settingsRepository.putString(SettingsKeys.LIBRARY_FOLDER_SORT, mode)
                }
                scrollToTop.request()
            },
            onToggleOrder = {
                scope.launch {
                    settingsRepository.putString(
                        SettingsKeys.LIBRARY_FOLDER_SORT_DESC,
                        if (folderSortDescending) "0" else "1",
                    )
                }
                scrollToTop.request()
            },
            // 展示样式切换：写全局键即生效（prefs flow 回流派生值），不回顶——
            // 网格/列表各自记住自己的滚动位置。
            viewStyle = folderView,
            onViewStyleToggle = {
                sortMenuOpen = false
                scope.launch {
                    settingsRepository.putString(
                        SettingsKeys.LIBRARY_SHELF_VIEW,
                        if (folderView == SettingsKeys.SHELF_VIEW_LIST) {
                            SettingsKeys.SHELF_VIEW_GRID
                        } else {
                            SettingsKeys.SHELF_VIEW_LIST
                        },
                    )
                }
            },
            alignment = Alignment.TopEnd,
            origin = TransformOrigin(1f, 0f),
            modifier = Modifier
                .padding(top = headerState.statusBarTop + 61.dp, end = 20.dp)
                .widthIn(max = 260.dp)
                .fillMaxWidth(),
        )
    } else {
        SortMenuOverlay(
            visible = sortMenuOpen,
            presence = overlayPresence,
            backdrop = pageBackdrop,
            mode = sortMode,
            descending = sortDescending,
            onDismiss = { sortMenuOpen = false },
            onPick = { mode ->
                sortMenuOpen = false
                scope.launch {
                    settingsRepository.putString(SettingsKeys.LIBRARY_SORT, mode)
                }
                // 切档后列表按新顺序重排：复用回顶总线（当前显示网格带动画滚回顶部
                // + 头部展开），二级页打开时同样生效；滚动记忆随之归 0。
                scrollToTop.request()
            },
        onToggleOrder = {
            scope.launch {
                settingsRepository.putString(
                    SettingsKeys.LIBRARY_SORT_DESC,
                    if (sortDescending) "0" else "1",
                )
            }
            scrollToTop.request()
        },
        // 展示样式切换：仅一级平铺/系列渲染（二级页打开时不传，样式只作用一级）。
        // 与书夹菜单的切换行写同一全局键（library_shelf_view，三视图联动）。
        viewStyle = if (target == null) folderView else null,
        onViewStyleToggle = if (target == null) {
            {
                sortMenuOpen = false
                scope.launch {
                    settingsRepository.putString(
                        SettingsKeys.LIBRARY_SHELF_VIEW,
                        if (folderView == SettingsKeys.SHELF_VIEW_LIST) {
                            SettingsKeys.SHELF_VIEW_GRID
                        } else {
                            SettingsKeys.SHELF_VIEW_LIST
                        },
                    )
                }
            }
        } else {
            null
        },
        alignment = Alignment.TopEnd,
        origin = TransformOrigin(1f, 0f),
        modifier = Modifier
            .padding(top = headerState.statusBarTop + 61.dp, end = 20.dp)
            .widthIn(max = 260.dp)
            .fillMaxWidth(),
        )
    }

    editorHeld?.let { state ->
        CategoryEditorOverlay(
            visible = editor != null,
            presence = overlayPresence,
            backdrop = pageBackdrop,
            state = state,
            onDismiss = { editor = null },
            onSave = { name, color ->
                val id = state.id
                if (id == null) {
                    libraryRepository.createCategory(name, color)
                } else {
                    libraryRepository.renameCategory(id, name)
                    libraryRepository.setCategoryColor(id, color)
                }
                editor = null
            },
            onDelete = {
                state.id?.let(libraryRepository::deleteCategory)
                editor = null
            },
            onMove = { delta ->
                state.id?.let { libraryRepository.moveCategory(it, delta) }
                editor = null
            },
        )
    }

    MoveToCategoryOverlay(
        visible = moveSheetOpen,
        presence = overlayPresence,
        backdrop = pageBackdrop,
        categories = categories,
        selectedCount = selected.size,
        onDismiss = { moveSheetOpen = false },
        onPick = { category ->
            libraryRepository.addBooksToCategory(selected, category.id)
            moveSheetOpen = false
            selected = emptySet()
        },
        onCreate = {
            moveSheetOpen = false
            editor = CategoryEditorState()
        },
    )

    BulkMetadataOverlay(
        visible = metadataSheetOpen,
        presence = overlayPresence,
        backdrop = pageBackdrop,
        count = selected.size,
        onDismiss = { metadataSheetOpen = false },
        onApply = { series, writer, tags, readStatus ->
            val ids = selected
            metadataSheetOpen = false
            selected = emptySet()
            libraryRepository.updateBooksMetadata(
                ids = ids,
                series = series.ifBlank { null },
                writer = writer.ifBlank { null },
                tags = tags.ifBlank { null },
                readStatus = readStatus,
            )
        },
    )

    DeleteConfirmOverlay(
        visible = deleteSheetOpen,
        presence = overlayPresence,
        backdrop = pageBackdrop,
        count = selected.size,
        singleFileCount = selected.count { id ->
            books.firstOrNull { it.id == id }?.treeUri == SingleFileImporter.SINGLE_LIBRARY_URI
        },
        syncEnabled = syncRepository?.state?.value?.enabled == true,
        onDismiss = { deleteSheetOpen = false },
        onRemoveOnly = {
            val ids = selected
            deleteSheetOpen = false
            selected = emptySet()
            libraryRepository.moveBooksToTrash(ids)
        },
        onDeleteFiles = {
            val ids = selected
            deleteSheetOpen = false
            selected = emptySet()
            scope.launch { libraryRepository.deleteBooks(ids, deleteFiles = true) }
        },
    )
}

// ---------------- 书夹视图 ----------------

@Composable
private fun BookcaseGrid(
    books: List<BookRecord>,
    state: LazyGridState,
    headerSpacerHeight: androidx.compose.ui.unit.Dp,
    categories: List<CategoryRecord>,
    categoryBookIds: Map<Long, Set<String>>,
    folderSortMode: String,
    folderSortDescending: Boolean,
    chromeBottom: androidx.compose.ui.unit.Dp,
    onOpenSystem: (SystemCategory) -> Unit,
    onOpenCategory: (CategoryRecord) -> Unit,
    onEditCategory: (CategoryRecord) -> Unit,
    onNewCategory: () -> Unit,
    /** 拖拽结束：把拖拽后的完整卡片顺序原样提交（固化为 sort_key）。 */
    onReorderCategory: (List<Long>) -> Unit,
) {
    // 未分类 = 不在任何书夹里的书（虚拟卡，无书可归时整卡隐藏）
    val unclassifiedBooks = books.filter { book ->
        categoryBookIds.values.none { book.id in it }
    }
    val unclassifiedCount = unclassifiedBooks.size
    var dragState by remember(categories) { mutableStateOf<DragSortState?>(null) }
    val itemBounds = remember { mutableStateMapOf<Long, Rect>() }
    var orderedCategories by remember(categories) { mutableStateOf(categories) }
    LaunchedEffect(categories, dragState) {
        if (dragState == null) orderedCategories = categories
    }

    // 卡片数量键：与卡片角标同源（当前漫画/小说过滤后的成员数），不是 SQL 全量 bookCount
    fun categoryCount(id: Long): Int = books.count { it.id in categoryBookIds[id].orEmpty() }

    // 显示顺序 = 档位排序 × 方向；custom 档 = 拖拽序（orderedCategories）原样、
    // 不受方向影响。拖拽中 orderedCategories 就是实时拖拽序，档位排序不参与。
    val displayCategories = remember(orderedCategories, folderSortMode, folderSortDescending, books, categoryBookIds) {
        val sorted = when (folderSortMode) {
            SettingsKeys.FOLDER_SORT_NAME -> orderedCategories.sortedBy { it.name.lowercase() }
            SettingsKeys.FOLDER_SORT_COUNT -> orderedCategories.sortedBy { categoryCount(it.id) }
            else -> orderedCategories
        }
        if (folderSortMode != SettingsKeys.FOLDER_SORT_CUSTOM && folderSortDescending) {
            sorted.reversed()
        } else {
            sorted
        }
    }

    fun startDrag(id: Long) {
        // 拖拽基于当前显示顺序快照：从「按名称/按数量」档起手时先落到实际显示序，
        // 避免列表跳回 sort_key 序的起手跳变；松手后提交该序并自动切回自定义档。
        orderedCategories = displayCategories
        dragState = DragSortState(id, Offset.Zero, id, itemBounds.toMap())
    }

    fun updateDrag(id: Long, amount: Offset) {
        val current = dragState ?: return
        if (current.id != id) return
        val next = current.withDrag(amount)
        val targetId = next.targetId ?: id
        if (targetId != current.targetId) {
            val from = orderedCategories.indexOfFirst { it.id == id }
            val to = orderedCategories.indexOfFirst { it.id == targetId }
            if (from >= 0 && to >= 0 && from != to) {
                orderedCategories = orderedCategories.toMutableList().apply {
                    add(to, removeAt(from))
                }
            }
        }
        dragState = next.copy(targetId = targetId)
    }

    fun endDrag(id: Long) {
        val current = dragState
        val targetId = current?.targetId
        if (current?.id == id && targetId != null && targetId != id) {
            // 显示顺序原样固化（非 custom 档下显示序 ≠ sort_key 序，必须传完整序）
            onReorderCategory(orderedCategories.map { it.id })
        }
        dragState = null
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = state,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = chromeBottom + GlassDimens.ListBottomExtraScroll),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(headerSpacerHeight))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionLabel("智能书夹")
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SystemFoldersRow(
                books = books,
                unclassifiedCount = unclassifiedCount,
                onOpenSystem = onOpenSystem,
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionLabel("我的书夹")
        }
        items(displayCategories, key = { "cat_${it.id}" }) { category ->
            val dragging = dragState?.id == category.id
            val dropTarget = dragState?.targetId == category.id && !dragging
            Box(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        itemBounds[category.id] = coords.boundsInRoot()
                    }
                    .graphicsLayer {
                        shape = InkvolRadius.collection
                        clip = true
                        if (dragging) {
                            translationX = dragState?.offset?.x ?: 0f
                            translationY = dragState?.offset?.y ?: 0f
                            scaleX = 1.04f
                            scaleY = 1.04f
                            shadowElevation = 28f
                            alpha = 0.98f
                        }
                    },
            ) {
                FolderCard(
                    title = category.name,
                    // 与拼贴封面同源：按当前漫画/小说过滤计数，点进二级页数量一致
                    count = books.count { it.id in categoryBookIds[category.id].orEmpty() },
                    color = category.color,
                    covers = books.filter { it.id in categoryBookIds[category.id].orEmpty() }
                        .take(4)
                        .map { it.coverPath },
                    onClick = { onOpenCategory(category) },
                    onLongClick = { onEditCategory(category) },
                    dropTarget = dropTarget,
                    onDragStart = { startDrag(category.id) },
                    onDrag = { updateDrag(category.id, it) },
                    onDragEnd = { endDrag(category.id) },
                )
            }
        }
        // 虚拟书夹「未分类」：固定钉在最后一格，不参与排序档与拖拽排序；
        // 全部书都已归入书夹时（数量为 0）自动隐藏。
        if (unclassifiedBooks.isNotEmpty()) {
            item(key = "cat_unclassified") {
                FolderCard(
                    title = SystemCategory.UNCLASSIFIED.label,
                    count = unclassifiedBooks.size,
                    covers = unclassifiedBooks.take(4).map { it.coverPath },
                    accent = LocalFixedFolderColors.current.onCardMuted,
                    onClick = { onOpenSystem(SystemCategory.UNCLASSIFIED) },
                    onLongClick = {},
                    dragEnabled = false,
                )
            }
        }
        item {
            NewFolderCard(onClick = onNewCategory)
        }
    }
}

/**
 * 智能书夹横滚行：网格/列表两种书夹展示样式共用（视觉与行为完全一致）。
 * 左右双缘渐隐取代旧的行级圆角硬裁切：offscreen 层内 DstIn 只淡出本行内容；
 * 左缘渐隐仅在本行滚动过后显示（draw 作用域读 state，滚动失效重绘不重组）。
 */
@Composable
private fun SystemFoldersRow(
    books: List<BookRecord>,
    unclassifiedCount: Int,
    onOpenSystem: (SystemCategory) -> Unit,
) {
    val rowState = rememberLazyListState()
    LazyRow(
        modifier = Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val pastStart = rowState.firstVisibleItemIndex > 0 ||
                    rowState.firstVisibleItemScrollOffset > 0
                val brush = if (pastStart) {
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.08f to Color.Black,
                        0.92f to Color.Black,
                        1f to Color.Transparent,
                    )
                } else {
                    Brush.horizontalGradient(
                        0f to Color.Black,
                        0.92f to Color.Black,
                        1f to Color.Transparent,
                    )
                }
                drawRect(brush = brush, blendMode = BlendMode.DstIn)
            },
        state = rowState,
        contentPadding = PaddingValues(end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(systemFolders()) { folder ->
            SystemFolderCard(
                label = folder.label,
                count = when (folder) {
                    SystemCategory.UNREAD -> books.count { it.readStatus == BookRecord.READ_UNREAD }
                    SystemCategory.READING -> books.count { it.readStatus == BookRecord.READ_READING }
                    SystemCategory.FINISHED -> books.count { it.readStatus == BookRecord.READ_FINISHED }
                    SystemCategory.BOOKMARKED -> 0
                    SystemCategory.UNCLASSIFIED -> unclassifiedCount
                },
                accent = when (folder) {
                    SystemCategory.UNREAD -> InfoBlue
                    SystemCategory.READING -> WarningAmber
                    SystemCategory.FINISHED -> SuccessGreen
                    SystemCategory.BOOKMARKED -> BookmarkPurple
                    SystemCategory.UNCLASSIFIED -> LocalFixedFolderColors.current.onCardMuted
                },
                onClick = { onOpenSystem(folder) },
                modifier = Modifier.width(104.dp),
            )
        }
    }
}

// ---------------- 书架列表样式 ----------------

/**
 * 书架展示样式（网格/列表）切换的交叉淡化包装：书夹/系列/平铺三视图共用同一动效
 * （~200ms fade，不与外层视图/分类的左右滑动转场打架）。
 */
@Composable
private fun ShelfStyleSwitch(
    style: String,
    grid: @Composable () -> Unit,
    list: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = style,
        transitionSpec = {
            fadeIn(tween(200, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(200, easing = FastOutSlowInEasing))
        },
        label = "shelfViewStyle",
    ) { current ->
        if (current == SettingsKeys.SHELF_VIEW_LIST) list() else grid()
    }
}

// ---------------- 书夹视图 · 列表样式 ----------------

/**
 * 书夹一级的列表样式（仿历史页行卡）：整宽行卡 + 左侧迷你拼贴 + 名称/数量。
 * 智能书夹区保留横滚行（[SystemFoldersRow] 两种样式共用）；拖拽排序仅网格样式提供，
 * 列表样式 custom 档仍按 sort_key 显示序生效（排序菜单里提示回网格拖动）。
 */
@Composable
private fun BookcaseList(
    books: List<BookRecord>,
    state: LazyListState,
    headerSpacerHeight: androidx.compose.ui.unit.Dp,
    categories: List<CategoryRecord>,
    categoryBookIds: Map<Long, Set<String>>,
    folderSortMode: String,
    folderSortDescending: Boolean,
    chromeBottom: androidx.compose.ui.unit.Dp,
    onOpenSystem: (SystemCategory) -> Unit,
    onOpenCategory: (CategoryRecord) -> Unit,
    onEditCategory: (CategoryRecord) -> Unit,
    onNewCategory: () -> Unit,
) {
    // 未分类 = 不在任何书夹里的书（虚拟行，无书可归时整行隐藏）
    val unclassifiedBooks = books.filter { book ->
        categoryBookIds.values.none { book.id in it }
    }
    // 显示顺序与网格卡同源：档位排序 × 方向（custom = sort_key 序，不受方向影响）。
    // 列表不支持拖拽，无 orderedCategories 覆盖，直接以 categories（sort_key 序）为基准。
    val displayCategories = remember(
        categories,
        folderSortMode,
        folderSortDescending,
        books,
        categoryBookIds,
    ) {
        val sorted = when (folderSortMode) {
            SettingsKeys.FOLDER_SORT_NAME -> categories.sortedBy { it.name.lowercase() }
            SettingsKeys.FOLDER_SORT_COUNT -> categories.sortedBy { category ->
                books.count { it.id in categoryBookIds[category.id].orEmpty() }
            }
            else -> categories
        }
        if (folderSortMode != SettingsKeys.FOLDER_SORT_CUSTOM && folderSortDescending) {
            sorted.reversed()
        } else {
            sorted
        }
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = chromeBottom + GlassDimens.ListBottomExtraScroll),
    ) {
        // item 0 = 视图行等高占位（与网格样式同结构，行覆盖层位移语义一致）
        item(key = "header-spacer") {
            Spacer(Modifier.height(headerSpacerHeight))
        }
        item(key = "system-label") {
            SectionLabel("智能书夹", topPadding = 16.dp, bottomPadding = 8.dp)
        }
        item(key = "system-row") {
            SystemFoldersRow(
                books = books,
                unclassifiedCount = unclassifiedBooks.size,
                onOpenSystem = onOpenSystem,
            )
        }
        item(key = "mine-label") {
            SectionLabel("我的书夹", topPadding = 16.dp, bottomPadding = 8.dp)
        }
        items(displayCategories, key = { "cat_${it.id}" }) { category ->
            FolderListRow(
                title = category.name,
                // 与网格卡角标同源：按当前漫画/小说过滤计数，点进二级页数量一致
                count = books.count { it.id in categoryBookIds[category.id].orEmpty() },
                color = category.color,
                covers = books.filter { it.id in categoryBookIds[category.id].orEmpty() }
                    .take(4)
                    .map { it.coverPath },
                onClick = { onOpenCategory(category) },
                onLongClick = { onEditCategory(category) },
            )
            Spacer(Modifier.height(10.dp))
        }
        // 虚拟书夹「未分类」：固定钉在末尾，不参与排序档；无书自动隐藏。
        if (unclassifiedBooks.isNotEmpty()) {
            item(key = "cat_unclassified") {
                FolderListRow(
                    title = SystemCategory.UNCLASSIFIED.label,
                    count = unclassifiedBooks.size,
                    color = "",
                    covers = unclassifiedBooks.take(4).map { it.coverPath },
                    accent = LocalFixedFolderColors.current.onCardMuted,
                    onClick = { onOpenSystem(SystemCategory.UNCLASSIFIED) },
                    onLongClick = {},
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        item(key = "new-folder") {
            NewFolderRow(onClick = onNewCategory)
        }
    }
}

/** 书夹列表行：迷你拼贴 + 名称/数量（视觉语言仿历史页行卡）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderListRow(
    title: String,
    count: Int,
    color: String,
    covers: List<String?>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** 非空时覆盖 color 解析出的拼贴底色（虚拟书夹「未分类」无 color 字段）。 */
    accent: Color? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InkvolRadius.card)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = InkvolRadius.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(1.12f)
                    .clip(InkvolRadius.collection),
                contentAlignment = Alignment.Center,
            ) {
                Collage(
                    covers = covers,
                    accent = accent ?: runCatching {
                        Color(android.graphics.Color.parseColor(color))
                    }.getOrDefault(MaterialTheme.colorScheme.primary),
                    shape = InkvolRadius.collection,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "$count 本",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 「新建书夹」列表行：网格样式 NewFolderCard 的行版。 */
@Composable
private fun NewFolderRow(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "新建书夹",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 区块标题（智能书夹 / 我的书夹）。缺省间距给网格样式：上下由
 * LazyVerticalGrid 的 spacedBy(14.dp) 补足（实际 上18/下14），零回归。
 * 列表样式（LazyColumn 默认行距 0）调用时显式传 (16, 8)——历史页同款节奏。
 */
@Composable
private fun SectionLabel(
    text: String,
    topPadding: androidx.compose.ui.unit.Dp = 4.dp,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding),
    )
}

@Composable
private fun SystemFolderCard(
    label: String,
    count: Int,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 固定色板：不随动态取色（含浅色动态档），恒为默认色板同名 token 值。
    val fixed = LocalFixedFolderColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = fixed.card,
        border = BorderStroke(1.dp, fixed.outline),
        modifier = modifier.height(44.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fixed.onCard,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = if (count > 0) {
                    fixed.onCardMuted
                } else {
                    fixed.onCardMuted.copy(alpha = 0.45f)
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(fixed.badge.copy(alpha = if (count > 0) 1f else 0.45f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    title: String,
    count: Int,
    color: String = "#8A8F98",
    covers: List<String?>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** 非空时覆盖 color 解析出的拼贴底色（虚拟书夹无 color 字段）。 */
    accent: Color? = null,
    /** false 时不渲染右上角拖拽手柄（虚拟书夹不可排序）。 */
    dragEnabled: Boolean = true,
    dropTarget: Boolean = false,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InkvolRadius.collection)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(
                if (dropTarget) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = InkvolRadius.collection,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Box {
            Collage(
                covers = covers,
                accent = accent ?: runCatching {
                    Color(android.graphics.Color.parseColor(color))
                }.getOrDefault(MaterialTheme.colorScheme.primary),
                shape = InkvolRadius.collection,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.12f),
            )
            if (dragEnabled) {
                DragHandle(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(
            modifier = Modifier.padding(
                start = 8.dp,
                end = 8.dp,
                bottom = 8.dp,
            ),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$count 本",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DragHandle(
    modifier: Modifier = Modifier,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.86f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .pointerInput(Unit) {
                // 吃掉普通 tap，避免把手当卡片入口。
                detectTapGestures { }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = onDragStart,
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = "拖拽排序",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun Collage(
    covers: List<String?>,
    accent: Color,
    modifier: Modifier = Modifier,
    shape: Shape = InkvolRadius.cover,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.16f)),
    ) {
        repeat(2) { row ->
            Row(modifier = Modifier.weight(1f)) {
                repeat(2) { column ->
                    val index = row * 2 + column
                    val cover = covers.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cover != null) {
                            AsyncImage(
                                model = File(cover),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewFolderCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.05f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "新建书夹",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ---------------- 系列 / 平铺 ----------------

@Composable
private fun SeriesGrid(
    books: List<BookRecord>,
    state: LazyGridState,
    sortMode: String,
    descending: Boolean,
    headerSpacerHeight: androidx.compose.ui.unit.Dp,
    chromeBottom: androidx.compose.ui.unit.Dp,
    seriesMode: String,
    onOpenSeries: (LibraryTarget.Series) -> Unit,
) {
    // 层级模式：一级卡 = 库根下第一层文件夹（卡数量/封面 = 子树聚合）；
    // 扁平模式：直接含书的那层目录各一张卡（同名不同父加后缀）。
    // 排序 = 成员书聚合键（与一级书排序档同源），标题档即卡名。
    val nodes = remember(books, seriesMode, sortMode, descending) {
        sortSeriesNodes(seriesNodesFor(books, seriesMode), sortMode, descending)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = state,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = chromeBottom + GlassDimens.ListBottomExtraScroll),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(headerSpacerHeight))
        }
        items(nodes, key = { it.path }) { node ->
            // 层级模式：算子系列数给副标题标构成（「N 个系列 · M 本直属」）
            val sub = if (
                seriesMode == SettingsKeys.SERIES_MODE_HIERARCHY &&
                node.path.isNotEmpty() && !node.path.startsWith("@")
            ) {
                childSeriesNodes(books, node.path).size
            } else {
                null
            }
            SeriesCard(
                node = node,
                subCount = sub,
                onClick = { onOpenSeries(LibraryTarget.Series(node.path, node.name)) },
            )
        }
    }
}

/** 系列卡（一级网格与二级页「子系列」区共用）：拼贴封面 + 名称 + 构成（卷数）。 */
@Composable
private fun SeriesCard(
    node: SeriesNode,
    /** 层级模式下该节点的子系列数（非层级 / 伪路径 = null，副标题退回「N 卷」）。 */
    subCount: Int? = null,
    onClick: () -> Unit,
) {
    // 拼贴封面直属书优先：让「文件夹里直接放的书」在一级卡上可见（层级模式才区分，
    // 扁平/伪路径下 leafPath == node.path 恒直属，排序为无操作）。
    val covers = node.books
        .sortedBy { if (leafSeriesOf(it).first == node.path) 0 else 1 }
        .take(4)
        .map { it.coverPath }
    val directCount = node.books.count { leafSeriesOf(it).first == node.path }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InkvolRadius.collection)
            .clickable(onClick = onClick),
    ) {
        Collage(
            covers = covers,
            accent = MaterialTheme.colorScheme.primary,
            shape = InkvolRadius.collection,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.12f),
        )
        Spacer(Modifier.height(4.dp))
        Column(
            modifier = Modifier.padding(
                start = 8.dp,
                end = 8.dp,
                bottom = 8.dp,
            ),
        ) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = seriesSubLabel(node.books.size, subCount, directCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 系列卡副标题：有子系列时标明构成（直属书数 > 0 才带「本直属」尾巴）。 */
private fun seriesSubLabel(total: Int, subCount: Int?, directCount: Int): String = when {
    subCount != null && subCount > 0 ->
        if (directCount > 0) "$subCount 个系列 · $directCount 本直属" else "$subCount 个系列"
    else -> "$total 卷"
}

@Composable
private fun BookGrid(
    books: List<BookRecord>,
    state: LazyGridState,
    headerSpacerHeight: androidx.compose.ui.unit.Dp,
    selected: Set<String>,
    reportSettled: Boolean,
    chromeBottom: androidx.compose.ui.unit.Dp,
    onClick: (BookRecord, ReaderOpenRect?) -> Unit,
    onLongClick: (BookRecord) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        state = state,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = chromeBottom + GlassDimens.ListBottomExtraScroll),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(headerSpacerHeight))
        }
        items(books, key = { it.id }) { book ->
            BookCell(
                book = book,
                selected = book.id in selected,
                reportSettled = reportSettled,
                onClick = { rect -> onClick(book, rect) },
                onLongClick = { onLongClick(book) },
            )
        }
    }
}

// ---------------- 系列 / 平铺 · 列表样式 ----------------

/** 系列一级列表样式：系列行卡（迷你拼贴 + 名称 + 卷数），点击进系列二级页。 */
@Composable
private fun SeriesList(
    books: List<BookRecord>,
    state: LazyListState,
    headerSpacerHeight: androidx.compose.ui.unit.Dp,
    chromeBottom: androidx.compose.ui.unit.Dp,
    seriesMode: String,
    sortMode: String,
    descending: Boolean,
    onOpenSeries: (LibraryTarget.Series) -> Unit,
) {
    // 层级/扁平结构与排序档与网格版完全同源（seriesNodesFor + sortSeriesNodes）。
    val nodes = remember(books, seriesMode, sortMode, descending) {
        sortSeriesNodes(seriesNodesFor(books, seriesMode), sortMode, descending)
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = chromeBottom + GlassDimens.ListBottomExtraScroll),
    ) {
        item(key = "header-spacer") {
            Spacer(Modifier.height(headerSpacerHeight))
        }
        items(nodes, key = { it.path }) { node ->
            // 层级模式：算子系列数给副标题标构成（与网格卡同源）
            val sub = if (
                seriesMode == SettingsKeys.SERIES_MODE_HIERARCHY &&
                node.path.isNotEmpty() && !node.path.startsWith("@")
            ) {
                childSeriesNodes(books, node.path).size
            } else {
                null
            }
            SeriesListRow(
                node = node,
                subCount = sub,
                onClick = { onOpenSeries(LibraryTarget.Series(node.path, node.name)) },
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 系列列表行：迷你拼贴 + 系列名 + 构成（卷数，与网格卡同语言）。 */
@Composable
private fun SeriesListRow(
    node: SeriesNode,
    subCount: Int? = null,
    onClick: () -> Unit,
) {
    // 拼贴封面直属书优先（与 SeriesCard 同源，见彼处注释）
    val covers = node.books
        .sortedBy { if (leafSeriesOf(it).first == node.path) 0 else 1 }
        .take(4)
        .map { it.coverPath }
    val directCount = node.books.count { leafSeriesOf(it).first == node.path }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InkvolRadius.card)
            .clickable(onClick = onClick),
        shape = InkvolRadius.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(1.12f)
                    .clip(InkvolRadius.collection),
                contentAlignment = Alignment.Center,
            ) {
                Collage(
                    covers = covers,
                    accent = MaterialTheme.colorScheme.primary,
                    shape = InkvolRadius.collection,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = seriesSubLabel(node.books.size, subCount, directCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 平铺一级列表样式：书行卡（封面 + 书名 + 页数/格式/进度），开书 + 长按多选。 */
@Composable
private fun FlatList(
    books: List<BookRecord>,
    state: LazyListState,
    headerSpacerHeight: androidx.compose.ui.unit.Dp,
    selected: Set<String>,
    reportSettled: Boolean,
    chromeBottom: androidx.compose.ui.unit.Dp,
    onClick: (BookRecord, ReaderOpenRect?) -> Unit,
    onLongClick: (BookRecord) -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = chromeBottom + GlassDimens.ListBottomExtraScroll),
    ) {
        item(key = "header-spacer") {
            Spacer(Modifier.height(headerSpacerHeight))
        }
        items(books, key = { it.id }) { book ->
            BookListRow(
                book = book,
                selected = book.id in selected,
                reportSettled = reportSettled,
                onClick = { rect -> onClick(book, rect) },
                onLongClick = { onLongClick(book) },
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * 书行卡（平铺列表样式）：视觉仿历史页行卡；封面矩形经
 * [LocalReaderOpenController] 回报（一镜到底飞入/飞回），语义与网格版
 * [BookCell] 逐字一致；多选遮罩/勾选同网格样式。副标题与书网格/搜索结果卡
 * 同口径：读完=「已读完」，在读追加百分比，未读不加。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListRow(
    book: BookRecord,
    selected: Boolean,
    reportSettled: Boolean,
    onClick: (ReaderOpenRect?) -> Unit,
    onLongClick: () -> Unit,
) {
    var coverBounds by remember { mutableStateOf<ReaderOpenRect?>(null) }
    val openController = LocalReaderOpenController.current
    // settled 翻 true 时用最后已知位置补发一次（恢复落位后卡片不再移动、
    // onGloballyPositioned 不再回调），闭环「恢复完成 → 可信回报 → 起飞」。
    LaunchedEffect(reportSettled) {
        if (reportSettled) {
            coverBounds?.let { openController?.updateSourceRect(book.id, it, true) }
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InkvolRadius.card)
            .combinedClickable(
                onClick = { onClick(coverBounds) },
                onLongClick = onLongClick,
            ),
        shape = InkvolRadius.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(0.72f)
                    .clip(InkvolRadius.cover)
                    .onGloballyPositioned { coords ->
                        val bounds = coords.boundsInRoot()
                        val rect = ReaderOpenRect(
                            left = bounds.left,
                            top = bounds.top,
                            width = bounds.width,
                            height = bounds.height,
                        )
                        coverBounds = rect
                        // reportSettled=false（列表还在恢复）时坐标不可信，controller 忽略。
                        openController?.updateSourceRect(book.id, rect, reportSettled)
                    }
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                val cover = book.coverPath
                if (cover != null) {
                    AsyncImage(
                        model = File(cover),
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = book.title.take(1),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                val base = if (book.type == "NOVEL") {
                    "${book.pageCount} 章 · ${book.format}"
                } else {
                    "${book.pageCount} 页 · ${book.format}"
                }
                val progress = when {
                    book.readStatus == BookRecord.READ_FINISHED -> " · 已读完"
                    book.percent > 0.0 -> " · ${(book.percent * 100).toInt()}%"
                    else -> ""
                }
                Text(
                    text = base + progress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------- 分类页 ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryContentView(
    books: List<BookRecord>,
    /** 层级系列页的子系列卡（书夹 / 智能书夹恒为空）。 */
    subSeries: List<SeriesNode> = emptyList(),
    emptyBecauseFiltered: Boolean = false,
    /** 系列页专用空态文案；null = 书夹空态（长按加入书夹）。 */
    emptyTitle: String? = null,
    emptyHint: String? = null,
    state: LazyGridState,
    selected: Set<String>,
    reportSettled: Boolean,
    onToggleSelect: (String) -> Unit,
    onOpenBook: (BookRecord, ReaderOpenRect?) -> Unit,
    onOpenSeries: (LibraryTarget.Series) -> Unit = {},
    chromeBottom: androidx.compose.ui.unit.Dp,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 标题行（返回 / 名称 / 数量 / 排序）已上提到头部层（DetailTitleRow）：
        // 它要随折叠一起上移出屏，放在内容区只能停在屏幕顶部。
        // 标题行高 + 间距已由头部让位（DETAIL_TITLE_ROW_HEIGHT）承担。

        if (books.isEmpty() && subSeries.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = InkvolRadius.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = when {
                            emptyBecauseFiltered -> "当前分类下没有书"
                            else -> emptyTitle ?: "这个书夹还是空的"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            emptyBecauseFiltered ->
                                "书夹里有书，但都不属于当前选中的分类。返回书架切换到「全部」查看。"

                            else -> emptyHint ?: "回到书架长按书本，选择「加入书夹」。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                state = state,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = chromeBottom + 16.dp),
            ) {
                // 子系列区（层级系列页）：小标题 + 系列卡，书卡排在后面。
                // 系列卡与书卡同列宽（自适应），实现最稳、不动滚动/锚点容器结构。
                if (subSeries.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "subSeriesHeader") {
                        SectionHeader(title = "系列 · ${subSeries.size}")
                    }
                    items(subSeries, key = { "series:${it.path}" }) { node ->
                        SeriesCard(
                            node = node,
                            onClick = {
                                onOpenSeries(LibraryTarget.Series(node.path, node.name))
                            },
                        )
                    }
                    if (books.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "subBooksHeader") {
                            SectionHeader(title = "书 · ${books.size} 本")
                        }
                    }
                }
                items(books, key = { it.id }) { book ->
                    BookCell(
                        book = book,
                        selected = book.id in selected,
                        reportSettled = reportSettled,
                        onClick = { rect ->
                            if (selected.isEmpty()) {
                                onOpenBook(book, rect)
                            } else {
                                onToggleSelect(book.id)
                            }
                        },
                        onLongClick = { onToggleSelect(book.id) },
                    )
                }
            }
        }

    }
}

/**
 * 二级页列表样式（library_shelf_view=list）：书夹 / 系列 / 智能书夹的书行列表。
 * 与网格版 [CategoryContentView] 共用同一套数据与空态，仅容器换成 LazyColumn：
 * 子系列行（层级系列页）+ 书行卡 [BookListRow]，点击/多选/一镜到底语义与一级
 * [FlatList] 完全一致。层级系列页带分区标题（「系列」区 + 「书籍」区），
 * 跨方向恢复的锚点偏移 = 标题行 + 子系列行数（+ 直属书区标题）。
 */
@Composable
private fun DetailList(
    books: List<BookRecord>,
    subSeries: List<SeriesNode> = emptyList(),
    emptyBecauseFiltered: Boolean = false,
    emptyTitle: String? = null,
    emptyHint: String? = null,
    state: LazyListState,
    selected: Set<String>,
    reportSettled: Boolean,
    onToggleSelect: (String) -> Unit,
    onOpenBook: (BookRecord, ReaderOpenRect?) -> Unit,
    onOpenSeries: (LibraryTarget.Series) -> Unit = {},
    chromeBottom: androidx.compose.ui.unit.Dp,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (books.isEmpty() && subSeries.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = InkvolRadius.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = when {
                            emptyBecauseFiltered -> "当前分类下没有书"
                            else -> emptyTitle ?: "这个书夹还是空的"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            emptyBecauseFiltered ->
                                "书夹里有书，但都不属于当前选中的分类。返回书架切换到「全部」查看。"

                            else -> emptyHint ?: "回到书架长按书本，选择「加入书夹」。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                state = state,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = chromeBottom + GlassDimens.ListBottomExtraScroll,
                ),
            ) {
                // 层级系列页分区（与网格版 CategoryContentView 同构）：子系列区 + 直属书区。
                // 关键：直属书不并入任何二级系列，标题隔开让「一级散书」清晰可见。
                if (subSeries.isNotEmpty()) {
                    item(key = "subSeriesHeader") {
                        SectionHeader(title = "系列 · ${subSeries.size}")
                    }
                }
                items(subSeries, key = { "series:${it.path}" }) { node ->
                    SeriesListRow(
                        node = node,
                        onClick = {
                            onOpenSeries(LibraryTarget.Series(node.path, node.name))
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (subSeries.isNotEmpty() && books.isNotEmpty()) {
                    item(key = "subBooksHeader") {
                        SectionHeader(title = "书 · ${books.size} 本")
                    }
                }
                items(books, key = { it.id }) { book ->
                    BookListRow(
                        book = book,
                        selected = book.id in selected,
                        reportSettled = reportSettled,
                        onClick = { rect ->
                            if (selected.isEmpty()) {
                                onOpenBook(book, rect)
                            } else {
                                onToggleSelect(book.id)
                            }
                        },
                        onLongClick = { onToggleSelect(book.id) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/**
 * 二级页标题行（属于头部）：返回钮 + 书夹/系列名 + 数量。
 * 由 LibraryScreen 在 header 层渲染并吃同一个 headerOffsetPx，与书架标题/chips
 * 刚性同移——折叠时整行随头部上移出屏，而不是停在内容区顶部。
 * 高度 = IconButton 48 + 底部 8 = DETAIL_TITLE_ROW_HEIGHT（56dp）。
 * 排序不在本行承载：全 App 一份全局排序，入口只有侧栏/一级标题行的排序钮。
 */
@Composable
private fun DetailTitleRow(
    title: String,
    countText: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 底部 8dp 与网格 contentPadding(4dp) 合计 12dp，等于原「Spacer 8 + 4」间距
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = countText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectionBar(
    backdrop: Backdrop,
    count: Int,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    GlassRounded(
        backdrop = backdrop,
        tier = GlassTier.Full,
        // 同心圆：内部按钮 40dp 高 → 圆角 20dp，垂直/水平内边距均 8dp
        // ⇒ 28 = 20 + 8（同时 28 = 56/2，条本身即完整胶囊）
        radius = 28.dp,
        blurRadius = GlassDimens.StrongBlur,
        edge = GlassEdge.Outline,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已选 $count 本",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            actions()
        }
    }
}

// ---------------- 书本卡 ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCell(
    book: BookRecord,
    selected: Boolean,
    reportSettled: Boolean,
    onClick: (ReaderOpenRect?) -> Unit,
    onLongClick: () -> Unit,
) {
    var coverBounds by remember { mutableStateOf<ReaderOpenRect?>(null) }
    val openController = LocalReaderOpenController.current
    // onGloballyPositioned 只在位置变化时回调——网格恢复落位后卡片不再移动，
    // 不会再触发回报；若 settled 在最后一次位置回调之后才置位，controller 将
    // 永远收不到可信坐标（旋转后返回的起飞闸只能等到超时）。settled 翻 true
    // 时用最后已知位置补发一次，闭环「恢复完成 → 可信回报 → 起飞」链路。
    LaunchedEffect(reportSettled) {
        if (reportSettled) {
            coverBounds?.let { openController?.updateSourceRect(book.id, it, true) }
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                // 先 clip 再 clickable，按压涟漪跟随封面圆角，而不是整张直角卡。
                .clip(InkvolRadius.cover)
                .combinedClickable(
                    onClick = { onClick(coverBounds) },
                    onLongClick = onLongClick,
                )
                .onGloballyPositioned { coords ->
                    val bounds = coords.boundsInRoot()
                    val rect = ReaderOpenRect(
                        left = bounds.left,
                        top = bounds.top,
                        width = bounds.width,
                        height = bounds.height,
                    )
                    coverBounds = rect
                    // Closing/旋转后书架重新布局时，封面必须飞回当前位置。
                    // reportSettled=false（网格还在恢复）时坐标不可信，controller 忽略。
                    openController?.updateSourceRect(book.id, rect, reportSettled)
                }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val cover = book.coverPath
            if (cover != null) {
                AsyncImage(
                    model = File(cover),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = book.title.take(1),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val base = if (book.type == "NOVEL") {
            "${book.pageCount} 章 · ${book.format}"
        } else {
            "${book.pageCount} 页 · ${book.format}"
        }
        // 与搜索结果卡（SearchComponents）同口径：读完=「已读完」，在读追加百分比，未读不加。
        val progress = when {
            book.readStatus == BookRecord.READ_FINISHED -> " · 已读完"
            book.percent > 0.0 -> " · ${(book.percent * 100).toInt()}%"
            else -> ""
        }
        Text(
            text = base + progress,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class CategoryEditorState(
    val id: Long? = null,
    val name: String = "",
    val color: String = CATEGORY_COLORS.first(),
)

@Composable
private fun CategoryEditorOverlay(
    visible: Boolean,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
    state: CategoryEditorState,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var name by remember { mutableStateOf(state.name) }
    var color by remember { mutableStateOf(state.color) }
    // 面板改为常驻组合后，每次打开都要把草稿重置回当前数据
    LaunchedEffect(visible) {
        if (visible) {
            name = state.name
            color = state.color
        }
    }

    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.Card,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 340.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (state.id == null) "新建书夹" else "编辑书夹",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("名称") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CATEGORY_COLORS.forEach { hex ->
                    val value = runCatching { Color(android.graphics.Color.parseColor(hex)) }
                        .getOrDefault(Color.Gray)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(value)
                            .clickable { color = hex },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (color == hex) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            if (state.id != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onMove(-1) }) { Text("前移") }
                    TextButton(onClick = { onMove(1) }) { Text("后移") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.id != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = { if (name.isNotBlank()) onSave(name.trim(), color) },
                    enabled = name.isNotBlank(),
                ) { Text("保存") }
            }
        }
    }
}

@Composable
private fun MoveToCategoryOverlay(
    visible: Boolean,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
    categories: List<CategoryRecord>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onPick: (CategoryRecord) -> Unit,
    onCreate: () -> Unit,
) {
    val chrome = LocalGlassChrome.current
    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.BottomPanel,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = Alignment.BottomCenter,
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp + chrome.railEndInset,
                bottom = 16.dp,
            )
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(max = 420.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "把 $selectedCount 本加入…",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(10.dp))
            if (categories.isEmpty()) {
                Text(
                    text = "还没有书夹",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(categories, key = { it.id }) { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(category) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        runCatching {
                                            Color(android.graphics.Color.parseColor(category.color))
                                        }.getOrDefault(MaterialTheme.colorScheme.primary),
                                    ),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(category.name, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${category.bookCount} 本",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text("新建书夹")
            }
        }
    }
}

@Composable
private fun BulkMetadataOverlay(
    visible: Boolean,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
    count: Int,
    onDismiss: () -> Unit,
    onApply: (series: String, writer: String, tags: String, readStatus: String) -> Unit,
) {
    var series by remember { mutableStateOf("") }
    var writer by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    // 面板改为常驻组合后，每次打开都要清空上一次的输入
    LaunchedEffect(visible) {
        if (visible) {
            series = ""
            writer = ""
            tags = ""
            status = ""
        }
    }

    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.BottomPanel,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = Alignment.BottomCenter,
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp,
            )
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "批量编辑 $count 本",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "留空的字段不会修改",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = series,
                onValueChange = { series = it },
                singleLine = true,
                label = { Text("系列") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = writer,
                onValueChange = { writer = it },
                singleLine = true,
                label = { Text("作者") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                singleLine = true,
                label = { Text("标签（逗号分隔）") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("不改状态", status.isBlank()) { status = "" }
                TypeChip("未读", status == BookRecord.READ_UNREAD) {
                    status = BookRecord.READ_UNREAD
                }
                TypeChip("在读", status == BookRecord.READ_READING) {
                    status = BookRecord.READ_READING
                }
                TypeChip("读完", status == BookRecord.READ_FINISHED) {
                    status = BookRecord.READ_FINISHED
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onApply(series, writer, tags, status) }) {
                    Text("应用")
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmOverlay(
    visible: Boolean,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
    count: Int,
    singleFileCount: Int,
    syncEnabled: Boolean = false,
    onDismiss: () -> Unit,
    onRemoveOnly: () -> Unit,
    onDeleteFiles: () -> Unit,
) {
    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.Card,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 340.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "删除 $count 本？",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    singleFileCount == count && count > 0 ->
                        "这 $count 本为单文件导入：删除会同时删掉手机里选中的原文件，不可撤销；" +
                            "只想从书架移除请选「移入回收站」（保留 30 天，不删文件）。"
                    singleFileCount > 0 ->
                        "其中 $singleFileCount 本为单文件导入，删除会同时删掉这些原文件；" +
                            "移入回收站则保留 30 天可恢复。"
                    else ->
                        "放入回收站保留 30 天，可随时恢复；删源文件会永久删除手机里的文件，不可撤销。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (syncEnabled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "已开启同步：「连文件删除」将同步从所有设备的书架移除这些书；移入回收站仅影响本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(16.dp))
            // 单文件导入同样给硬删入口：源文件按 doc_id 直删（原先这里按 singleFileCount 隐藏，
            // 纯单文件选中时只剩「移入回收站」，等于没有立即删除的办法）
            Button(
                onClick = onDeleteFiles,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (singleFileCount == count) "删除（含原文件）" else "连文件删除")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRemoveOnly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("移入回收站")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("取消")
            }
        }
    }
}

// ---------------- 小组件 ----------------

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
            },
        )
    }
}

@Composable
private fun ViewChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        border = BorderStroke(
            1.dp,
            if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun viewLabel(mode: String): String = when (mode) {
    SettingsKeys.VIEW_SERIES -> "系列"
    SettingsKeys.VIEW_FLAT -> "平铺"
    else -> "书夹"
}

private fun targetTitle(target: LibraryTarget): String = when (target) {
    is LibraryTarget.User -> target.name
    is LibraryTarget.System -> target.kind.label
    is LibraryTarget.Series -> target.name
}

/**
 * 书架排序（6 档 × 升降序，功能逻辑对齐 SSMH 书夹排序）：
 * 各档统一升序键后按 [descending] 反转；缺省（recent + 降序）= 旧版 SQL
 * ORDER BY added_at DESC 的原样行为。title 用小写比较保证大小写稳定序。
 */
private fun sortBooks(books: List<BookRecord>, mode: String, descending: Boolean): List<BookRecord> {
    val sorted = when (mode) {
        SettingsKeys.SORT_TITLE -> books.sortedBy { it.title.lowercase() }
        SettingsKeys.SORT_LAST_READ -> books.sortedBy { it.lastReadAt }
        SettingsKeys.SORT_PAGES -> books.sortedBy { it.pageCount }
        SettingsKeys.SORT_PROGRESS -> books.sortedBy { it.percent }
        SettingsKeys.SORT_SIZE -> books.sortedBy { it.fileSize }
        else -> books.sortedBy { it.addedAt } // SORT_RECENT 收藏时间
    }
    return if (descending) sorted.reversed() else sorted
}

/**
 * 系列卡排序：成员书聚合键。收藏时间/最近阅读/阅读进度取成员最大值（一个系列
 * 只要有一卷新/被读过就前移），页数/文件大小取求和（系列体量），标题档即卡名。
 */
private fun sortSeriesNodes(
    nodes: List<SeriesNode>,
    mode: String,
    descending: Boolean,
): List<SeriesNode> {
    val sorted = when (mode) {
        SettingsKeys.SORT_TITLE -> nodes.sortedBy { it.name.lowercase() }
        SettingsKeys.SORT_LAST_READ -> nodes.sortedBy { node -> node.books.maxOf { it.lastReadAt } }
        SettingsKeys.SORT_PAGES -> nodes.sortedBy { node -> node.books.sumOf { it.pageCount } }
        SettingsKeys.SORT_PROGRESS -> nodes.sortedBy { node -> node.books.maxOf { it.percent } }
        SettingsKeys.SORT_SIZE -> nodes.sortedBy { node -> node.books.sumOf { it.fileSize } }
        else -> nodes.sortedBy { node -> node.books.maxOf { it.addedAt } }
    }
    return if (descending) sorted.reversed() else sorted
}

private fun booksOf(
    target: LibraryTarget,
    books: List<BookRecord>,
    categoryBookIds: Map<Long, Set<String>>,
    seriesMode: String = SettingsKeys.SERIES_MODE_HIERARCHY,
): List<BookRecord> = when (target) {
    is LibraryTarget.User -> {
        val ids = categoryBookIds[target.id].orEmpty()
        books.filter { it.id in ids }
    }

    is LibraryTarget.System -> when (target.kind) {
        SystemCategory.UNREAD -> books.filter { it.readStatus == BookRecord.READ_UNREAD }
        SystemCategory.READING -> books.filter { it.readStatus == BookRecord.READ_READING }
        SystemCategory.FINISHED -> books.filter { it.readStatus == BookRecord.READ_FINISHED }
        SystemCategory.BOOKMARKED -> emptyList()
        SystemCategory.UNCLASSIFIED -> {
            val assigned = categoryBookIds.values.flatten().toSet()
            books.filter { it.id !in assigned }
        }
    }

    is LibraryTarget.Series -> books.filter { bookInSeries(it, target.path, seriesMode) }
}

/**
 * 智能书夹行。未分类已迁到「我的书夹」网格末尾（数量为 0 自动隐藏），此处不再列出。
 */
private fun systemFolders(): List<SystemCategory> =
    SystemCategory.entries.filter { it != SystemCategory.UNCLASSIFIED }


