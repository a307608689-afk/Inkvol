package com.inkvol.app.ui.library

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.inkvol.app.ui.components.LibraryGridScroll
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
import com.inkvol.app.ui.components.glass.GlassRounded
import com.inkvol.app.ui.components.glass.GlassTier
import com.inkvol.app.ui.components.glass.LocalGlassChrome
import com.inkvol.app.ui.components.glass.rememberGlassHeaderState
import com.inkvol.app.ui.components.glass.rememberGlassOverlayPresence
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
import java.io.File

private val CATEGORY_COLORS = listOf(
    "#C8462F", "#4CAF7D", "#6C8CFF", "#E0A33E", "#9B59B6", "#5F6368",
)

private sealed interface LibraryTarget {
    data class User(val id: Long, val name: String, val color: String) : LibraryTarget
    data class System(val kind: SystemCategory) : LibraryTarget
    data class Series(val name: String) : LibraryTarget
}

/**
 * 进程级书架 UI 状态：开书/切 Tab 会让书架页被 NavHost 卸载，普通 remember 全部归零，
 * 表现为「二级页/漫画小说筛选被重置回一级/全部」。这里与 LibraryGridScroll 同范式：
 * 离页时在 onDispose 落值，进书架首帧直读恢复；显式返回（回一级/点回「全部」）
 * 自然落 null，进程被杀后重开也归零回一级，不做磁盘持久化。
 */
private object LibraryUiState {
    var target: LibraryTarget? = null
    var typeFilter: String? = null
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

private fun List<Long>.movedIds(fromId: Long, toId: Long): List<Long> {
    val from = indexOf(fromId)
    val to = indexOf(toId)
    if (from < 0 || to < 0 || from == to) return this
    return toMutableList().apply {
        add(to, removeAt(from))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    settingsRepository: SettingsRepository,
    libraryRepository: LibraryRepository,
    /** 平板完整侧栏已有导入入口时，标题行不再重复加号 */
    showHeaderImport: Boolean = true,
    onOpenBook: (BookRecord, ReaderOpenRect?) -> Unit = { _, _ -> },
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
    val gridState = remember {
        LazyGridState(
            firstVisibleItemIndex = LibraryGridScroll.index,
            firstVisibleItemScrollOffset = LibraryGridScroll.offset,
        )
    }
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

    // 一镜到底返回：网格滚动位置恢复。
    // 返回后网格 item 数会先少后多（诊断见 5→16 生长），首帧/异步补滚都会被这次
    // 数据重构顶回 index 0。所以这里等 item 数连续两帧稳定、且足以容纳目标 index
    // 之后再 scrollToItem，落在数据生长结束后，才不会被重构顶掉。
    LaunchedEffect(Unit) {
        val savedLandscape = LibraryGridScroll.landscape
        val landscapeNow = configuration.screenWidthDp > configuration.screenHeightDp
        val crossOrientation = savedLandscape != null && savedLandscape != landscapeNow
        var index = LibraryGridScroll.index
        var offset = LibraryGridScroll.offset
        if (crossOrientation) {
            // 跨方向恢复（阅读期间旋转过再返回）：index 是线性序号仍然有效，
            // offset 是旧方向的像素语义、直接用会落错位置。锚点优先——定位到
            // 最近打开的书（返回动画的飞回目标），保证目标卡在视口内、能回报
            // 新方向下的正确坐标；flat 视图网格 = viewHeader(item 0) + books。
            // 查不到（书夹/系列视图、书已删、数据未到）退回原 index。
            // viewMode 用 cachedPreferences 同步快照求值（组合状态在 effect 之后
            // 才声明，且首帧即实际值）；"booklist" 旧值回落书夹视图。
            val viewModeNow = settingsRepository.cachedPreferences()?.let {
                val stored = settingsRepository.stringOf(it, SettingsKeys.LIBRARY_VIEW, SettingsKeys.VIEW_FOLDER)
                if (stored == "booklist") SettingsKeys.VIEW_FOLDER else stored
            } ?: SettingsKeys.VIEW_FOLDER
            index = LibraryGridScroll.anchorBookId?.let { anchorId ->
                if (LibraryUiState.target == null && viewModeNow == SettingsKeys.VIEW_FLAT) {
                    libraryRepository.books.value
                        .indexOfFirst { it.id == anchorId }
                        .takeIf { it >= 0 }
                        ?.plus(1)
                } else {
                    null
                }
            } ?: LibraryGridScroll.index
            offset = 0
        }
        Log.d(
            "InkvolOpen",
            "diag restore start target=($index,$offset) cross=$crossOrientation " +
                "grid=(${gridState.firstVisibleItemIndex},${gridState.firstVisibleItemScrollOffset}) " +
                "count=${gridState.layoutInfo.totalItemsCount}",
        )
        if (index > 0 || offset > 0) {
            // 立即派发 + 逐帧重申：新组装的 LazyGrid 在最初几个测量帧（视口未定、
            // 数据未齐、条目高度未就绪）会把任何锚点重置回 (0,0)，内容先画在列表
            // 顶部、稳定后才拉回——可见的瞬间跳动。逐帧检查并重申，布局一旦可绘制
            // 就落在保存位置。落位判定按「目标 item 已在视口内」而非 firstVisible
            // 精确相等：Adaptive 列数下目标可能与行首 item 同行，scrollToItem 按行
            // 落位、firstVisibleItemIndex 停在行首（实测横屏一排 5 本时 scrollToItem(4)
            // 落 (1,0)，视觉已正确）——精确比较会白耗 40 帧、拖慢 settled 与
            // 「旋转后返回」的起飞闸。
            runCatching { gridState.scrollToItem(index, offset) }
            var reassertFrames = 0
            while (reassertFrames < 40) {
                val targetVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == index }
                if (targetVisible) break
                runCatching { gridState.scrollToItem(index, offset) }
                delay(16)
                reassertFrames++
            }
            Log.d(
                "InkvolOpen",
                "diag restore settled frames=$reassertFrames " +
                    "grid=(${gridState.firstVisibleItemIndex},${gridState.firstVisibleItemScrollOffset})",
            )
            var prevCount = -1
            var stable = 0
            var attempts = 0
            while (attempts < 40) {  // 最多约 40 帧（~640ms）等待稳定
                val count = gridState.layoutInfo.totalItemsCount
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
            Log.d(
                "InkvolOpen",
                "diag restore pre-scroll attempts=$attempts " +
                    "grid=(${gridState.firstVisibleItemIndex},${gridState.firstVisibleItemScrollOffset}) " +
                    "count=${gridState.layoutInfo.totalItemsCount}",
            )
            if (gridState.layoutInfo.totalItemsCount > index) {
                gridState.scrollToItem(index, offset)
                Log.d(
                    "InkvolOpen",
                    "diag restore post-scroll " +
                        "grid=(${gridState.firstVisibleItemIndex},${gridState.firstVisibleItemScrollOffset})",
                )
            }
        }
        // 恢复完成：放行卡片回报（onGloballyPositioned 只在位置变化时回调，
        // 落位后不再有回调，卡片会在 settled 翻转时用最后已知位置补发一次，
        // 保证「旋转后返回」的起飞闸能收到首条可信坐标）。
        gridSettled = true
        Log.d("InkvolOpen", "diag restore gridSettled=true")
    }

    // 离开书架（进阅读器）时记下滚动位置，供下次进入恢复。
    DisposableEffect(Unit) {
        onDispose {
            LibraryGridScroll.index = gridState.firstVisibleItemIndex
            LibraryGridScroll.offset = gridState.firstVisibleItemScrollOffset
            // 记录离开时的方向：下次进入若方向不同（阅读期间旋转过再返回），
            // 恢复逻辑走跨方向路径（index 保留、offset 归零 + 锚点校准）。
            // 方向用网格视口的实际宽高判断，不能读 onDispose 闭包捕获的
            // configuration——DisposableEffect(Unit) 只在首次组合安装，旋转
            // 重组不重装，闭包里的方向永远是首帧值（实测横屏打开存了 false）。
            // M6.3：此处曾打 Log.d，实测主线程卡在 logd 写入 21.5s 引发 ANR，
            // 关键路径的日志已全部移除。
            val viewport = gridState.layoutInfo.viewportSize
            LibraryGridScroll.landscape =
                viewport.width > viewport.height && viewport.width > 0
        }
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

    // ---- 一镜到底返回：冻结状态栏让位 ----
    // Reader 会隐藏系统栏，返回时 statusBars inset 有 ~250ms 回归动画，实时值会让
    // 标题/chips/内容整体先高后落（「内容叠一下标题才回位」）。冻结值 = 进阅读器前
    // 系统栏显示时的高度（rememberSaveable 跨路由保存，等于回归完成后的终值），
    // 仅在 Closing 期间使用，其它场景一律实时值，滚动自动隐藏状态栏等既有行为零变化。
    val readerOpenCtl = LocalReaderOpenController.current
    val liveStatusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var savedStatusBarTop by rememberSaveable { mutableStateOf(0f) }
    LaunchedEffect(liveStatusBarTop.value, readerOpenCtl?.phase) {
        if (readerOpenCtl?.phase != ReaderOpenPhase.Closing) {
            savedStatusBarTop = liveStatusBarTop.value
        }
    }
    val frozenStatusBarTop = if (
        readerOpenCtl?.phase == ReaderOpenPhase.Closing && savedStatusBarTop > 0f
    ) {
        savedStatusBarTop.dp
    } else {
        null
    }
    val headerState = rememberGlassHeaderState(
        gridState = gridState,
        resetKey = viewMode,
        frozenStatusBarTop = frozenStatusBarTop,
        closingHold = readerOpenCtl?.phase == ReaderOpenPhase.Closing,
        // 书夹排上移 10dp：默认 118 的呼吸留白砍 10，历史/设置页用列表版重载不受影响
        headerContentHeight = 108.dp,
    )
    // 让位量由折叠量单点导出：折叠量与「前置滚动消费」同源，
    // 折叠期间列表不滚、让位收缩多少内容就上移多少（与头部 1:1 同行）。
    val shelfGridTop = headerState.gridTopPadding

    // 回顶（底栏重复点书架）：滚回顶部后把头部滑回展开态，并轻量刷新书库（仅重读数据库，
    // 书夹二级页与一级网格共用 gridState，回顶自动覆盖）
    LaunchedEffect(Unit) {
        scrollToTop.events.collect {
            if (gridState.layoutInfo.totalItemsCount > 0) {
                gridState.animateScrollToItem(0)
            }
            headerState.expand()
            libraryRepository.refresh()
        }
    }

    var target by remember { mutableStateOf(LibraryUiState.target) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var plusMenuOpen by remember { mutableStateOf(false) }
    var moveSheetOpen by remember { mutableStateOf(false) }
    var deleteSheetOpen by remember { mutableStateOf(false) }
    var metadataSheetOpen by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf(LibraryUiState.typeFilter) }
    // 离开书架（开书/切 Tab 被 NavHost 卸载）时保留二级页与筛选；显式清除（回一级、
    // 点回「全部」）会先写 null，离页时自然落 null。与上面的滚动落值互不干扰。
    DisposableEffect(Unit) {
        onDispose {
            LibraryUiState.target = target
            LibraryUiState.typeFilter = typeFilter
        }
    }
    var editor by remember { mutableStateOf<CategoryEditorState?>(null) }
    val overlayController = LocalPageOverlayController.current

    // 覆盖层退场期间面板仍在组合里，chrome 必须等它退完再回来（否则两件玻璃同框）
    val overlayPresence = rememberGlassOverlayPresence()
    // 编辑型面板要保留最后一帧数据，否则退场时内容先消失、只剩空玻璃卡缩回去
    var editorHeld by remember { mutableStateOf<CategoryEditorState?>(null) }
    LaunchedEffect(editor) { editor?.let { editorHeld = it } }

    val overlayActive = plusMenuOpen || editor != null ||
        moveSheetOpen || deleteSheetOpen || metadataSheetOpen
    // 用 present（含退场）而不是 target：多选条/底栏都要等面板退完再出现
    val overlayShowing = overlayActive || overlayPresence.present
    LaunchedEffect(overlayShowing) { overlayController.setActive(overlayShowing) }
    DisposableEffect(Unit) {
        onDispose { overlayController.setActive(false) }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) libraryRepository.importTree(uri)
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
    LaunchedEffect(target) { selected = emptySet() }
    // 返回书架时校验恢复的二级页仍有效（书夹可能在阅读期间被删除或重扫清空），
    // 失效静默落回一级，不弹提示；System 档按 readStatus 过滤，恒有效。
    LaunchedEffect(target, books, categories) {
        val t = target ?: return@LaunchedEffect
        val valid = when (t) {
            is LibraryTarget.User -> categories.any { it.id == t.id }
            is LibraryTarget.System -> true
            is LibraryTarget.Series -> books.any {
                (it.series?.takeIf { s -> s.isNotBlank() } ?: it.title) == t.name
            }
        }
        if (!valid) target = null
    }

    BackHandler(
        enabled = selected.isNotEmpty() || target != null || metadataSheetOpen,
    ) {
        when {
            metadataSheetOpen -> metadataSheetOpen = false
            selected.isNotEmpty() -> selected = emptySet()
            else -> target = null
        }
    }

    fun runPlusAction() {
        when (plusAction) {
            SettingsKeys.PLUS_FOLDER -> folderPicker.launch(null)
            SettingsKeys.PLUS_FILE -> filePicker.launch(arrayOf("*/*"))
            SettingsKeys.PLUS_RESCAN -> libraryRepository.rescanAll()
            else -> plusMenuOpen = true
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

    val viewHeader: @Composable () -> Unit = {
        Row(
            modifier = Modifier
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

    Box(modifier = Modifier.fillMaxSize()) {
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
            val currentTarget = target
            if (currentTarget != null) {
                val sortKey = targetSortKey(currentTarget)
                val sortMode = prefs?.let {
                    settingsRepository.stringOf(it, SettingsKeys.LIBRARY_SORT_PREFIX + sortKey, SettingsKeys.SORT_RECENT)
                } ?: SettingsKeys.SORT_RECENT
                val targetBooks = sortBooks(
                    // 分类过滤贯穿二级页：智能/我的/系列都只显示当前漫画/小说筛选下的成员
                    booksOf(currentTarget, filteredBooks, categoryBookIds),
                    sortMode,
                )
                val emptyBecauseFiltered = typeFilter != null &&
                    targetBooks.isEmpty() &&
                    booksOf(currentTarget, books, categoryBookIds).isNotEmpty()
                val typeLabel = when (typeFilter) {
                    "COMIC" -> "漫画"
                    "NOVEL" -> "小说"
                    else -> null
                }
                CategoryContentView(
                    target = currentTarget,
                    books = targetBooks,
                    typeLabel = typeLabel,
                    emptyBecauseFiltered = emptyBecauseFiltered,
                    state = gridState,
                    selected = selected,
                    sortMode = sortMode,
                    reportSettled = gridSettled,
                    onSortChange = { mode ->
                        scope.launch {
                            settingsRepository.putString(
                                SettingsKeys.LIBRARY_SORT_PREFIX + sortKey,
                                mode,
                            )
                        }
                    },
                    onToggleSelect = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    },
                    onBack = { target = null },
                    onOpenBook = onOpenBook,
                    chromeBottom = chrome.contentBottomForPage,
                )
            } else {
                when (viewMode) {
                    SettingsKeys.VIEW_SERIES -> SeriesGrid(
                        books = filteredBooks,
                        state = gridState,
                        viewHeader = viewHeader,
                        chromeBottom = chrome.contentBottomForPage,
                        onOpenSeries = { name -> target = LibraryTarget.Series(name) },
                    )

                    SettingsKeys.VIEW_FLAT -> Box(modifier = Modifier.weight(1f)) {
                        BookGrid(
                            books = filteredBooks,
                            state = gridState,
                            viewHeader = viewHeader,
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

                    else -> BookcaseGrid(
                        books = filteredBooks,
                        state = gridState,
                        viewHeader = viewHeader,
                        categories = categories,
                        categoryBookIds = categoryBookIds,
                        chromeBottom = chrome.contentBottomForPage,
                        onOpenSystem = { kind -> target = LibraryTarget.System(kind) },
                        onOpenCategory = { category ->
                            target = LibraryTarget.User(category.id, category.name, category.color)
                        },
                        onEditCategory = { category ->
                            editor = CategoryEditorState(
                                id = category.id,
                                name = category.name,
                                color = category.color,
                            )
                        },
                        onNewCategory = { editor = CategoryEditorState() },
                        onReorderCategory = { fromId, toId ->
                            libraryRepository.reorderCategories(
                                categories.map { it.id }.movedIds(fromId, toId),
                            )
                        },
                    )
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "导入",
                                tint = MaterialTheme.colorScheme.onPrimary,
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

    AnimatedVisibility(
        visible = selected.isNotEmpty() && !overlayShowing,
        enter = fadeIn(tween(GlassMotion.OVERLAY_ENTER_MS)) +
            slideInVertically(tween(GlassMotion.OVERLAY_ENTER_MS)) { it / 3 },
        exit = fadeOut(tween(GlassMotion.OVERLAY_EXIT_MS)) +
            slideOutVertically(tween(GlassMotion.OVERLAY_EXIT_MS)) { it / 3 },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val userTarget = target as? LibraryTarget.User
        SelectionBar(
            backdrop = pageBackdrop,
            count = selected.size,
            modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp + chrome.railEndInset,
                bottom = chrome.contentBottomForPage + 8.dp,
            ),
            actions = {
                TextButton(onClick = { selected = emptySet() }) { Text("取消") }
                Spacer(Modifier.weight(1f))
                if (userTarget != null) {
                    TextButton(onClick = {
                        libraryRepository.removeBooksFromCategory(selected, userTarget.id)
                        selected = emptySet()
                    }) { Text("移出") }
                }
                TextButton(onClick = { metadataSheetOpen = true }) { Text("编辑") }
                TextButton(onClick = { deleteSheetOpen = true }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = { moveSheetOpen = true }) { Text("书夹") }
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
    viewHeader: @Composable () -> Unit,
    categories: List<CategoryRecord>,
    categoryBookIds: Map<Long, Set<String>>,
    chromeBottom: androidx.compose.ui.unit.Dp,
    onOpenSystem: (SystemCategory) -> Unit,
    onOpenCategory: (CategoryRecord) -> Unit,
    onEditCategory: (CategoryRecord) -> Unit,
    onNewCategory: () -> Unit,
    onReorderCategory: (Long, Long) -> Unit,
) {
    val unclassifiedCount = books.count { book ->
        categoryBookIds.values.none { book.id in it }
    }
    var dragState by remember(categories) { mutableStateOf<DragSortState?>(null) }
    val itemBounds = remember { mutableStateMapOf<Long, Rect>() }
    var orderedCategories by remember(categories) { mutableStateOf(categories) }
    LaunchedEffect(categories, dragState) {
        if (dragState == null) orderedCategories = categories
    }

    fun startDrag(id: Long) {
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
            onReorderCategory(id, targetId)
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
            viewHeader()
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionLabel("智能书夹")
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val rowState = rememberLazyListState()
            LazyRow(
                // 左右双缘渐隐取代旧的行级圆角硬裁切：offscreen 层内 DstIn 只淡出本行内容；
                // 左缘渐隐仅在本行滚动过后显示（draw 作用域读 state，滚动失效重绘不重组）。
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

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionLabel("我的书夹")
        }
        items(orderedCategories, key = { "cat_${it.id}" }) { category ->
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
        item {
            NewFolderCard(onClick = onNewCategory)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
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
    color: String,
    covers: List<String?>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
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
                accent = runCatching { Color(android.graphics.Color.parseColor(color)) }
                    .getOrDefault(MaterialTheme.colorScheme.primary),
                shape = InkvolRadius.collection,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.12f),
            )
            DragHandle(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
            )
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
    viewHeader: @Composable () -> Unit,
    chromeBottom: androidx.compose.ui.unit.Dp,
    onOpenSeries: (String) -> Unit,
) {
    val groups = remember(books) {
        books.groupBy { book -> book.series?.takeIf { it.isNotBlank() } ?: book.title }
            .toList()
            .sortedBy { it.first }
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
            viewHeader()
        }
        items(groups, key = { it.first }) { (name, group) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(InkvolRadius.collection)
                    .clickable { onOpenSeries(name) },
            ) {
                Collage(
                    covers = group.take(4).map { it.coverPath },
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
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${group.size} 卷",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookGrid(
    books: List<BookRecord>,
    state: LazyGridState,
    viewHeader: @Composable () -> Unit,
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
            viewHeader()
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

// ---------------- 分类页 ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryContentView(
    target: LibraryTarget,
    books: List<BookRecord>,
    typeLabel: String? = null,
    emptyBecauseFiltered: Boolean = false,
    state: LazyGridState,
    selected: Set<String>,
    sortMode: String,
    reportSettled: Boolean,
    onSortChange: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onBack: () -> Unit,
    onOpenBook: (BookRecord, ReaderOpenRect?) -> Unit,
    chromeBottom: androidx.compose.ui.unit.Dp,
) {
    val glassNotice = LocalGlassNotice.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = typeLabel?.let { "${targetTitle(target)} · $it" } ?: targetTitle(target),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (selected.isEmpty()) "${books.size} 本" else "已选 ${selected.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TypeChip("最近添加", sortMode == SettingsKeys.SORT_RECENT) {
                onSortChange(SettingsKeys.SORT_RECENT)
            }
            TypeChip("标题", sortMode == SettingsKeys.SORT_TITLE) {
                onSortChange(SettingsKeys.SORT_TITLE)
            }
            TypeChip("文件大小", sortMode == SettingsKeys.SORT_SIZE) {
                onSortChange(SettingsKeys.SORT_SIZE)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (books.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = InkvolRadius.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = if (emptyBecauseFiltered) "当前分类下没有书" else "这个书夹还是空的",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (emptyBecauseFiltered) {
                            "书夹里有书，但都不属于当前选中的分类。返回书架切换到「全部」查看。"
                        } else {
                            "回到书架长按书本，选择「加入书夹」。"
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
        radius = 18.dp,
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
        Text(
            text = if (book.type == "NOVEL") {
                "${book.pageCount} 章 · ${book.format}"
            } else {
                "${book.pageCount} 页 · ${book.format}"
            },
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
                text = "移除 $count 本漫画？",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (singleFileCount > 0) {
                    "其中 $singleFileCount 本为单文件导入；放入回收站保留 30 天，可随时恢复。"
                } else {
                    "放入回收站保留 30 天，可随时恢复；连文件删除会永久删除源文件，不可撤销。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (singleFileCount < count) {
                Button(
                    onClick = onDeleteFiles,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("连文件删除")
                }
                Spacer(Modifier.height(8.dp))
            }
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

private fun targetSortKey(target: LibraryTarget): String = when (target) {
    is LibraryTarget.User -> "user_${target.id}"
    is LibraryTarget.System -> "system_${target.kind.name}"
    is LibraryTarget.Series -> "series_${target.name}"
}

private fun sortBooks(books: List<BookRecord>, mode: String): List<BookRecord> = when (mode) {
    SettingsKeys.SORT_TITLE -> books.sortedBy { it.title.lowercase() }
    SettingsKeys.SORT_SIZE -> books.sortedByDescending { it.fileSize }
    else -> books.sortedByDescending { it.addedAt }
}

private fun booksOf(
    target: LibraryTarget,
    books: List<BookRecord>,
    categoryBookIds: Map<Long, Set<String>>,
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

    is LibraryTarget.Series -> books.filter {
        (it.series?.takeIf { value -> value.isNotBlank() } ?: it.title) == target.name
    }
}

private fun systemFolders(): List<SystemCategory> = SystemCategory.entries


