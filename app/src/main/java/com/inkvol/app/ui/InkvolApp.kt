package com.inkvol.app.ui

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import com.inkvol.app.BuildConfig
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.data.settings.SettingsRepository
import com.inkvol.app.ui.components.glass.GlassBottomBar
import com.inkvol.app.ui.components.glass.GlassChromeInsets
import com.inkvol.app.ui.components.glass.GlassDimens
import com.inkvol.app.ui.components.glass.GlassEdge
import com.inkvol.app.ui.components.glass.GlassMotion
import com.inkvol.app.ui.components.glass.GlassRail
import com.inkvol.app.ui.components.glass.PlusMenuOverlay
import com.inkvol.app.ui.components.glass.RailActions
import com.inkvol.app.ui.components.glass.RailVariant
import com.inkvol.app.ui.components.GlassNoticeBus
import com.inkvol.app.ui.components.GlassNoticeHost
import com.inkvol.app.ui.components.LocalGlassNotice
import com.inkvol.app.ui.components.LocalPageOverlayController
import com.inkvol.app.ui.components.LocalReaderActive
import com.inkvol.app.ui.components.LocalReaderOpenController
import com.inkvol.app.ui.components.LocalScrollToTop
import com.inkvol.app.ui.components.LibraryGridScroll
import com.inkvol.app.ui.components.LibraryStatusBar
import com.inkvol.app.ui.components.PageOverlayController
import com.inkvol.app.ui.components.ReaderOpenController
import com.inkvol.app.ui.components.ReaderOpenPhase
import com.inkvol.app.ui.components.ReaderOpenRect
import com.inkvol.app.ui.components.ReaderOpenViewModel
import com.inkvol.app.ui.components.ScrollToTopBus
import com.inkvol.app.ui.components.glass.LocalGlassBackdrop
import com.inkvol.app.ui.components.glass.LocalGlassChrome
import com.inkvol.app.ui.components.glass.LocalGlassChromeProgress
import com.inkvol.app.ui.components.glass.rememberAnimationsEnabled
import com.inkvol.app.ui.components.glass.rememberGlassBottomBarHeight
import com.inkvol.app.ui.history.HistoryScreen
import com.inkvol.app.ui.library.LibraryManagerScreen
import com.inkvol.app.ui.reader.panel.PanelSpikeScreen
import com.inkvol.app.ui.library.LibraryScreen
import com.inkvol.app.ui.navigation.TopDestination
import com.inkvol.app.ui.novel.NovelChapterPreviewScreen
import com.inkvol.app.ui.novel.NovelReaderScreen
import com.inkvol.app.ui.reader.ReaderScreen
import com.inkvol.app.ui.settings.SettingsScreen
import com.inkvol.app.ui.theme.Breakpoints
import com.inkvol.app.ui.theme.InkvolRadius
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt

/** 旋转后返回的起飞闸超时：书架恢复回报超过此时长仍未到达则按旧坐标放行（兜底，不卡死）。
 *  实测横屏恢复落位+settled 回报 ~700ms（数据生长+逐帧重申+补发一帧），600ms 会先于
 *  正常回报触发、动画前段按旧坐标飞再改道——放宽到 800ms 让正常路径免改道。 */
private const val CLOSING_SOURCE_WAIT_MS = 800L

/**
 * Inkvol 宿主壳：玻璃采集层 + chrome 显隐状态机 + 三 Tab 导航。
 *
 * 结构对齐 SSMH 的 SsmhApp，但砍掉了所有网络/站点依赖：
 * 内容区只有 书架 / 历史 / 设置 三个顶层目的地，
 * 手机走底部满宽玻璃底栏，平板（≥600dp）走右缘 GlassRail。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InkvolApp(
    settingsRepository: SettingsRepository,
    libraryRepository: LibraryRepository,
    /** Widget 冷启动 / singleTop 唤起时请求打开的书籍 ID。 */
    pendingReaderBookId: String? = null,
    onPendingReaderConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val topLevel = TopDestination.entries.firstOrNull { it.route == currentRoute }

    // 后台扫描完成后，回到前台刷新书架
    val lifecycleOwner = LocalLifecycleOwner.current
    val refreshScope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshScope.launch { libraryRepository.refresh() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val useRail = maxWidth >= Breakpoints.medium
        // 手机横屏（高 <480dp）走紧凑侧栏：只保留底部导航；平板完整侧栏才挂顶部动作槽
        val fullRail = useRail && maxHeight >= 480.dp
        val context = LocalContext.current
        val view = LocalView.current
        val scrollToTopBus = remember { ScrollToTopBus() }
        val noticeBus = remember { GlassNoticeBus() }
        val readerOpenViewModel: ReaderOpenViewModel = viewModel()
        val readerOpen = readerOpenViewModel.controller
        val prefs by settingsRepository.preferences.collectAsStateWithLifecycle(initialValue = null)
        val openSpeed = prefs?.let {
            settingsRepository.stringOf(it, SettingsKeys.READER_OPEN_SPEED, SettingsKeys.OPEN_SPEED_FAST)
        } ?: SettingsKeys.OPEN_SPEED_FAST
        val readerOpenDurationMs = settingsRepository.readerOpenDurationMs(openSpeed)
        // 只传 State 给 overlay/effects；宿主 composition 不读取 progress，
        // 避免主动画每帧重组整个 Scaffold/NavHost/chrome。
        val readerOpenTimeline = rememberReaderOpenTimeline(readerOpen, readerOpenDurationMs)
        val readerOpenActive = readerOpen.phase == ReaderOpenPhase.Opening
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) libraryRepository.importTree(uri)
        }
        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) libraryRepository.importDocument(uri)
        }
        // 侧栏加号三选菜单（询问档 / 长按强制弹出）
        var railPlusMenuOpen by remember { mutableStateOf(false) }
        // 与手机端加号同源：「加号默认动作」设置全局生效，未配置（ask）弹三选菜单
        val plusAction = prefs?.let {
            settingsRepository.stringOf(it, SettingsKeys.PLUS_ACTION, SettingsKeys.PLUS_ASK)
        } ?: SettingsKeys.PLUS_ASK

        fun runRailPlusAction() {
            when (plusAction) {
                SettingsKeys.PLUS_FOLDER -> folderPicker.launch(null)
                SettingsKeys.PLUS_FILE -> filePicker.launch(arrayOf("*/*"))
                SettingsKeys.PLUS_RESCAN -> libraryRepository.rescanAll()
                else -> railPlusMenuOpen = true
            }
        }
        val density = LocalDensity.current
        val backgroundColor = MaterialTheme.colorScheme.background

        // 玻璃采集层：录制内容子树供底栏/侧栏采样。
        // 回调必须 remember 成稳定引用，否则 LayerBackdrop 会反复重建（玻璃闪烁）。
        val backdropDraw: ContentDrawScope.() -> Unit = remember(backgroundColor) {
            {
                drawRect(backgroundColor)
                drawContent()
            }
        }
        val backdrop = rememberLayerBackdrop(onDraw = backdropDraw)

        // 页面覆盖层激活时隐藏 chrome（菜单/编辑器/选择面板要盖在底栏之上）
        var pageOverlayActive by remember { mutableStateOf(false) }
        val overlayController = remember {
            PageOverlayController { active -> pageOverlayActive = active }
        }

        // ---- chrome 显隐状态机 ----
        val animationsEnabled = rememberAnimationsEnabled()
        // 覆盖层让位：打开时底栏淡出下移；关闭后页面会先把「面板退场结束」（present=false）
        // 才把 active 置 false，所以底栏一定在面板落定之后才回来，不会两件玻璃同框。
        val overlayYield by animateFloatAsState(
            targetValue = if (pageOverlayActive) 1f else 0f,
            animationSpec = if (animationsEnabled) {
                tween(GlassMotion.CHROME_YIELD_MS)
            } else {
                snap()
            },
            label = "overlayChromeYield",
        )
        var barVisible by remember { mutableStateOf(true) }
        var scrollAccum by remember { mutableFloatStateOf(0f) }
        // 首帧置为未稳定：让底栏在内容层完成首次绘制后重新采样，
        // 避免玻璃把启动窗口的图标染成整块色斑（冷启动可见）。
        var lensSettled by remember { mutableStateOf(false) }
        val lastScrollAt = remember { AtomicLong(0L) }
        val scrollTriggerPx = with(density) { GlassMotion.SCROLL_TRIGGER.toPx() }
        // 底栏保留组合到让位动画跑完（overlayYield 归 0）才卸载，否则就是硬切消失
        val showBottomBar = !useRail && topLevel != null && (!pageOverlayActive || overlayYield > 0f)
        val showRail = useRail && topLevel != null && !pageOverlayActive
        val chromeScrollEnabled = topLevel != null && !pageOverlayActive

        val selectTop: (TopDestination) -> Unit = { destination ->
            if (destination == topLevel) {
                scrollToTopBus.request()
            } else {
                navController.switchTop(destination)
            }
        }

        val scrollHandler: (Float, Boolean) -> Unit = { dy, atEdge ->
            lastScrollAt.set(SystemClock.uptimeMillis())
            when {
                atEdge -> {
                    scrollAccum = 0f
                    if (!barVisible) barVisible = true
                }

                dy < 0f -> {
                    scrollAccum = (scrollAccum + dy).coerceAtMost(0f)
                    if (scrollAccum <= -scrollTriggerPx) {
                        scrollAccum = 0f
                        if (barVisible) barVisible = false
                    }
                }

                dy > 0f -> {
                    scrollAccum = (scrollAccum + dy).coerceAtLeast(0f)
                    if (scrollAccum >= scrollTriggerPx) {
                        scrollAccum = 0f
                        if (!barVisible) barVisible = true
                    }
                }
            }
            if (lensSettled) lensSettled = false
        }
        val latestScrollHandler = rememberUpdatedState(scrollHandler)

        val barScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val dy = consumed.y
                    if (abs(dy) > abs(consumed.x) && dy != 0f) {
                        latestScrollHandler.value(dy, false)
                        return Offset.Zero
                    }
                    val remain = available.y
                    if (abs(remain) > abs(available.x) && remain != 0f) {
                        latestScrollHandler.value(remain, true)
                    }
                    return Offset.Zero
                }
            }
        }

        // 停手 200ms 恢复折射；滚动中降到 Lite 档
        LaunchedEffect(Unit) {
            while (true) {
                delay(80)
                val idle = SystemClock.uptimeMillis() - lastScrollAt.get()
                if (!lensSettled && idle >= GlassMotion.LENS_SETTLE_MS) lensSettled = true
            }
        }

        val imeVisible = WindowInsets.isImeVisible
        LaunchedEffect(imeVisible) {
            if (imeVisible) {
                barVisible = false
            } else {
                delay(200)
                barVisible = true
            }
        }

        LaunchedEffect(currentRoute) {
            barVisible = true
            scrollAccum = 0f
        }

        LaunchedEffect(pendingReaderBookId) {
            val bookId = pendingReaderBookId ?: return@LaunchedEffect
            if (readerOpen.phase == ReaderOpenPhase.Idle) {
                navController.navigate("reader/$bookId") { launchSingleTop = true }
            }
            onPendingReaderConsumed()
        }

        // Opening：动画完整在 Library 上播完，内容稳定后再切 Reader。
        // 无整屏渐黑掩护后，导航推迟到 >=0.98：此时黑色容器已近乎铺满全屏
        // （overlay 不透明），底下 Library→Reader 的替换完全不可见。
        LaunchedEffect(
            readerOpen.phase,
            currentRoute,
            readerOpen.readerReady,
            readerOpen.contentBitmap,
            readerOpen.coverBitmap,
            readerOpen.isNovel,
        ) {
            val readyForNav = readerOpen.contentBitmap != null || readerOpen.readerReady
            if (readerOpen.phase == ReaderOpenPhase.Opening &&
                (currentRoute == TopDestination.Library.route ||
                    currentRoute == TopDestination.History.route) &&
                readyForNav
            ) {
                // 正文页已就绪时，等容器铺满到接近全屏再切路由，替换完全不可见。
                val navThreshold = 0.98f
                snapshotFlow { readerOpenTimeline.progress.value }
                    .first { it >= navThreshold }
                    .let {
                        if (readerOpen.phase == ReaderOpenPhase.Opening) {
                            val route = if (readerOpen.isNovel) {
                                "novel-reader/${readerOpen.bookId}"
                            } else {
                                "reader/${readerOpen.bookId}"
                            }
                            navController.navigate(route) {
                                // launchSingleTop：栈顶已是同名 reader 路由时 navigate 变 no-op。
                                // navigate 后 currentRoute 异步更新，窗口内本 effect 因
                                // readerReady/contentBitmap 翻转重启时，旧值 route=Library 仍满足
                                // 条件且 snapshotFlow 首采样即 progress=1.0，会再 navigate 一次
                                //（双重入栈）——reader 条目在回退栈累积，与 phase 状态机错位
                                //（闪现/失效/须重启的最终触发源）。
                                launchSingleTop = true
                            }
                        }
                    }
            }
        }
        // Visible 只依赖唯一主 progress；handoff 是 draw 阶段纯函数。
        // currentRoute 必须进 key：路由切到 Reader 后要重启等待，否则 markVisible 永不触发。
        LaunchedEffect(
            readerOpen.phase,
            currentRoute,
            readerOpen.readerReady,
            readerOpen.contentBitmap,
            readerOpen.isNovel,
        ) {
            val ready = if (readerOpen.isNovel) {
                readerOpen.readerReady
            } else {
                readerOpen.contentBitmap != null || readerOpen.readerReady
            }
            val onReaderRoute = currentRoute?.startsWith("reader/") == true ||
                currentRoute?.startsWith("novel-reader/") == true
            if (readerOpen.phase == ReaderOpenPhase.Opening && ready && onReaderRoute) {
                val markThreshold = 0.995f
                snapshotFlow { readerOpenTimeline.progress.value }
                    .first { it >= markThreshold }
                // 等 Reader 上报实际可视矩形，保证 overlay 终帧与 Reader 首帧逐像素一致。
                // 无渐黑掩护后这里是对齐硬保证；200ms 超时兜底防 Reader 未上报时挂起。
                if (!readerOpen.targetFromReader) {
                    withTimeoutOrNull(200) {
                        snapshotFlow { readerOpen.targetFromReader }.first { it }
                    }
                }
                // 等一帧让 Reader 路由先完成组合，再撤 overlay，避免同帧竞争。
                withFrameNanos { }
                readerOpen.markVisible()
            }
        }

        // Closing：立即回书架，让 overlay 在真实书架 UI 上反向播放。
        // Closing 在真实书架 UI 上反向播放；进度到 0 后回 Idle，
        // 否则下一本书点击会被 phase 卡住。
        LaunchedEffect(readerOpen.phase, readerOpen.reason) {
            Log.d(
                "InkvolOpen",
                "host phase=${readerOpen.phase} reason=${readerOpen.reason} progress=${readerOpenTimeline.progress.value}",
            )
            if (readerOpen.phase == ReaderOpenPhase.Closing) {
                snapshotFlow { readerOpenTimeline.progress.value }
                    .first { it <= 0.02f }
                    .let { readerOpen.markIdle() }
            }
        }

        // 小说返回：状态栏延迟到 Closing 完成后再恢复，避免返场动画期间标题跳动。
        var pendingNovelBarRestore by remember { mutableStateOf(false) }

        val closeReaderOpen: (String) -> Unit = { reason ->
        if (readerOpen.phase == ReaderOpenPhase.Preparing) {
            // 尚未起飞：直接取消，不播反向动画。
            readerOpenViewModel.cancelPreparing()
        } else {
            // 恢复系统栏：先把行为标志复位为非瞬态——阅读器设置的 TRANSIENT 退出后
            // 不复位，会让这里的 show 变成「几秒后自动缩回」的临时唤出（状态栏闪现）。
            // 状态栏是否显示交给书架玻璃头按滚动状态决定（Closing 期间挂起、Idle 落位）：
            // 滚动隐藏态下这里 show statusBars 会造成闪现，所以只恢复导航栏。
            runCatching {
                val window = (context as? android.app.Activity)?.window
                if (window != null) {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                    if (readerOpen.isNovel) {
                        // 小说：返回动画期间保持沉浸，Idle 后再恢复。
                        pendingNovelBarRestore = true
                    } else if (LibraryStatusBar.visible) {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.navigationBars())
                    }
                }
            }

            // 传当前根尺寸：与 beginOpen 记录值对比，判断阅读期间是否旋转/变尺寸过
            //（stale 时 Closing 拉起飞闸，等书架回报新布局下的稳定坐标再反向）。
            readerOpen.beginClose(
                reason,
                currentRootWidthPx = view.width.coerceAtLeast(1).toFloat(),
                currentRootHeightPx = view.height.coerceAtLeast(1).toFloat(),
            )
            if (currentRoute?.startsWith("reader/") == true ||
                currentRoute?.startsWith("novel-reader/") == true
            ) {
                navController.popBackStack()
            }
        }
    }

        // 小说一镜到底：真正起飞时才隐藏系统栏；等待正文快照期间不动 inset。
        LaunchedEffect(readerOpen.isNovel, readerOpen.phase) {
            if (readerOpen.isNovel && readerOpen.phase == ReaderOpenPhase.Opening) {
                val window = (context as? android.app.Activity)?.window
                val controller = window?.let {
                    WindowCompat.getInsetsController(it, it.decorView)
                }
                controller?.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        // 小说路由：进入后立即隐藏系统栏，并在 300ms 后重申一次（防路由转场恢复）。
        LaunchedEffect(currentRoute) {
            if (currentRoute?.startsWith("novel-reader/") == true) {
                val window = (context as? android.app.Activity)?.window
                val controller = window?.let {
                    WindowCompat.getInsetsController(it, it.decorView)
                }
                controller?.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller?.hide(WindowInsetsCompat.Type.systemBars())
                delay(300)
                // 路由已变时 effect 会被取消，这里的重申不会误伤书架。
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        // 小说返回：Closing 完成（Idle）后再恢复系统栏，返场动画期间保持沉浸。
        LaunchedEffect(readerOpen.phase) {
            if (readerOpen.phase == ReaderOpenPhase.Idle && pendingNovelBarRestore) {
                pendingNovelBarRestore = false
                runCatching {
                    val window = (context as? android.app.Activity)?.window
                    if (window != null) {
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                        if (LibraryStatusBar.visible) {
                            controller.show(WindowInsetsCompat.Type.systemBars())
                        } else {
                            controller.show(WindowInsetsCompat.Type.navigationBars())
                        }
                    }
                }
            }
        }

        // 预加载失败：若还没切路由则直接反向关闭；若已切 Reader 则回书架。
        LaunchedEffect(readerOpen.contentFailed) {
            if (readerOpen.contentFailed) {
                closeReaderOpen("Error")
            }
        }

        // 自愈：路由已回非 reader 页而 phase 仍在 Opening/Visible——系统层静默 pop
        // （预测性返回/OEM 手势在快速重组窗口的边沿态直接弹路由，绕过全部
        // BackHandler，无 beginClose 日志）或 0.98 处竞态跳过 navigate。恢复走
        // beginClose 而非 forceIdle：Closing → 时间轴从当前进度反向播放 → markIdle
        // 归位，静默 pop 的「阅读器消失、书架闪现在中间位置」变成一次正常的返回
        // 动画。600ms 宽限：navigate 后 currentBackStackEntryAsState 滞后真实回退栈
        // 2-3 帧，立即触发会误杀刚打开的阅读器；真卡死持续存在，复查已稳定的
        // currentRoute 后照样触发。
        //
        // Opening 分支追加「动画完成」前置：Opening 期间 route 本就停在
        // library/history（0.98 才 navigate），动画没播完时这不是卡死——帧循环
        // 冻结/慢机上 animateTo 的推进可能晚于 600ms 宽限期（模拟器实测 progress
        // 冻结 0 达 1.8s 后自愈误杀，正常打开被反向拉回，表现为容器飞一半突然
        // 改道）。先等 progress >= 0.98（navigate 应随之发生、effect 会因 route
        // 变化重启），超时 2s 仍不到才判定真死；等到后给 route 状态一个传播
        // 缓冲再复查。Visible（阅读中静默 pop）维持 600ms 复查。
        LaunchedEffect(currentRoute, readerOpen.phase) {
            val phase = readerOpen.phase
            val onReader = currentRoute?.startsWith("reader/") == true ||
                currentRoute?.startsWith("novel-reader/") == true
            if (onReader || phase != ReaderOpenPhase.Opening && phase != ReaderOpenPhase.Visible) {
                return@LaunchedEffect
            }
            delay(600)
            // 小说正文快照未就绪时不自愈：仍在等 preload，route 未切不算异常。
            if (readerOpen.isNovel && readerOpen.contentBitmap == null) {
                return@LaunchedEffect
            }
            if (phase == ReaderOpenPhase.Opening) {
                val reached = withTimeoutOrNull(2000L) {
                    snapshotFlow { readerOpenTimeline.progress.value }.first { it >= 0.98f }
                } != null
                if (!reached) {
                    Log.d("InkvolOpen", "self-heal beginClose from=Opening (animation stuck) route=$currentRoute")
                    readerOpen.beginClose(
                        "SelfHeal",
                        currentRootWidthPx = view.width.coerceAtLeast(1).toFloat(),
                        currentRootHeightPx = view.height.coerceAtLeast(1).toFloat(),
                    )
                    return@LaunchedEffect
                }
                // 动画已完成：给 navigate 的 route 状态一个传播缓冲。
                delay(150)
            }
            if (readerOpen.phase == phase &&
                currentRoute?.startsWith("reader/") != true &&
                currentRoute?.startsWith("novel-reader/") != true
            ) {
                Log.d("InkvolOpen", "self-heal beginClose from=$phase route=$currentRoute")
                readerOpen.beginClose(
                    "SelfHeal",
                    currentRootWidthPx = view.width.coerceAtLeast(1).toFloat(),
                    currentRootHeightPx = view.height.coerceAtLeast(1).toFloat(),
                )
            }
        }

        // 旋转后返回的起飞闸超时兜底：书架始终没回报（目标卡不在视口、恢复异常）
        // 时放行，按旧坐标飞完——宁可错不卡死。
        LaunchedEffect(readerOpen.phase, readerOpen.closingHold) {
            if (readerOpen.phase == ReaderOpenPhase.Closing && readerOpen.closingHold) {
                delay(CLOSING_SOURCE_WAIT_MS)
                readerOpen.releaseClosingHold()
            }
        }

        // Preparing 返回直接取消；Closing 返回快速结束；Opening 未路由前反向取消。
        BackHandler(enabled = readerOpen.phase == ReaderOpenPhase.Preparing) {
            readerOpenViewModel.cancelPreparing()
        }
        BackHandler(enabled = readerOpen.phase == ReaderOpenPhase.Closing) {
            readerOpen.markIdle()
        }
        BackHandler(
            enabled = readerOpen.phase == ReaderOpenPhase.Opening &&
                (currentRoute == TopDestination.Library.route ||
                    currentRoute == TopDestination.History.route),
        ) {
            closeReaderOpen("Back")
        }

        val navigationBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val chromeInsets = GlassChromeInsets(
            bottomBarHeight = if (useRail) 0.dp else GlassDimens.BarHeight,
            bottomGap = if (useRail) {
                0.dp
            } else {
                GlassDimens.BarBottomMargin + navigationBottomInset + GlassDimens.ContentBreathing
            },
            pageBarHeight = 0.dp,
            hasPageBar = false,
            chromeVisible = topLevel != null,
            railMode = useRail,
            railEndInset = if (useRail) GlassDimens.RailWidth + GlassDimens.RailMargin else 0.dp,
        )

        val chromeHideProgress by animateFloatAsState(
            targetValue = if (barVisible) 0f else 1f,
            animationSpec = if (animationsEnabled) {
                spring(dampingRatio = 0.85f, stiffness = 380f)
            } else {
                snap()
            },
            label = "chromeHideProgress",
        )

        CompositionLocalProvider(
            LocalGlassChrome provides chromeInsets,
            LocalGlassBackdrop provides backdrop,
            LocalGlassChromeProgress provides chromeHideProgress,
            LocalPageOverlayController provides overlayController,
            LocalScrollToTop provides scrollToTopBus,
            LocalGlassNotice provides noticeBus,
            LocalReaderOpenController provides readerOpen,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0),
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (chromeScrollEnabled) {
                                    Modifier.nestedScroll(barScrollConnection)
                                } else {
                                    Modifier
                                },
                            )
                            // layerBackdrop 常挂（勿按 phase 摘挂）：条件摘挂会在每个开关周期
                            // 的起止各触发一次全量重排（janky 10.87%、99th=150ms，与时长无关）。
                            // 采集层失步的主要放大器（同书快速开关双重入栈）已由 launchSingleTop
                            // 根治；若常挂后失步复现，再切 backdrop 实例方案（§9.19）。
                            .layerBackdrop(backdrop),
                    ) {
                        InkvolNavHost(
                            navController = navController,
                            settingsRepository = settingsRepository,
                            libraryRepository = libraryRepository,
                            showHeaderImport = !fullRail,
                            readerActive = currentRoute?.startsWith("reader/") == true ||
                                currentRoute?.startsWith("novel-reader/") == true,
                            readerOpenViewModel = readerOpenViewModel,
                            readerOpenActive = readerOpenActive,
                            onCloseReader = closeReaderOpen,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // 底部内容渐隐：玻璃底栏采样到稳定底色，同时提升图标可读性。
                        // 必须在 layerBackdrop 内，否则不会进入采集层。
                        if (showBottomBar) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(GlassDimens.BarHeight + chromeInsets.bottomGap + 16.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, backgroundColor),
                                        ),
                                    )
                                    // 让位期间同步淡出，避免保留组合到最后一帧才整条消失
                                    .graphicsLayer { alpha = 1f - overlayYield },
                            )
                        }
                    }
                }

                if (showBottomBar) {
                    val barHeight = rememberGlassBottomBarHeight()
                    // 滚动隐藏（chromeHideProgress）与覆盖层让位（overlayYield）正交合成
                    val barShift = (barHeight + GlassDimens.PageBarGap) * chromeHideProgress +
                        (GlassMotion.CHROME_YIELD_DROP_DP * overlayYield).dp
                    val barAlpha = (1f - (1f - GlassMotion.HIDDEN_ALPHA) * chromeHideProgress) *
                        (1f - overlayYield)
                    GlassBottomBar(
                        destinations = TopDestination.entries,
                        selected = topLevel,
                        onSelect = selectTop,
                        backdrop = backdrop,
                        lensEnabled = lensSettled,
                        edge = GlassEdge.Outline,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = barShift)
                            .graphicsLayer { alpha = barAlpha },
                    )
                }

                if (showRail) {
                    GlassRail(
                        destinations = TopDestination.entries,
                        selected = topLevel,
                        onSelect = selectTop,
                        backdrop = backdrop,
                        lensEnabled = lensSettled,
                        variant = if (fullRail) RailVariant.Full else RailVariant.Compact,
                        actions = if (fullRail) {
                            RailActions(
                                onImport = { runRailPlusAction() },
                                onRescan = { libraryRepository.rescanAll() },
                                onToTop = { scrollToTopBus.request() },
                                onImportLongClick = { railPlusMenuOpen = true },
                            )
                        } else {
                            null
                        },
                        hideProgress = 0f,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }

                // 侧栏加号的三选菜单：锚在侧栏动作槽（顶部加号）左侧，从右上角放大出来。
                // 与手机端共用 PlusMenuOverlay 与同一询问语义；页面覆盖层激活时侧栏整体
                // 隐藏（showRail=false），菜单随 visible 一并收起。
                if (showRail) {
                    PlusMenuOverlay(
                        visible = railPlusMenuOpen && showRail,
                        presence = null,
                        backdrop = backdrop,
                        onDismiss = { railPlusMenuOpen = false },
                        onFolder = {
                            railPlusMenuOpen = false
                            folderPicker.launch(null)
                        },
                        onFile = {
                            railPlusMenuOpen = false
                            filePicker.launch(arrayOf("*/*"))
                        },
                        onRescan = {
                            railPlusMenuOpen = false
                            libraryRepository.rescanAll()
                        },
                        alignment = Alignment.TopEnd,
                        origin = TransformOrigin(1f, 0f),
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(
                                top = 16.dp,
                                end = GlassDimens.RailWidth + GlassDimens.RailMargin + 12.dp,
                            )
                            .widthIn(max = 260.dp)
                            .fillMaxWidth(),
                    )
                }

                ReaderOpenOverlay(
                    controller = readerOpen,
                    timeline = readerOpenTimeline,
                    novelReaderMounted = readerOpen.isNovel &&
                        currentRoute?.startsWith("novel-reader/") == true,
                )

                GlassNoticeHost(
                    bus = noticeBus,
                    backdrop = backdrop,
                    modifier = Modifier
                        // 通知最顶层：overlay 容器加了 zIndex(1f) 抬层后，通知必须再抬一层
                        .zIndex(2f)
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 20.dp,
                            end = 20.dp + chromeInsets.railEndInset,
                            // 抬过悬浮多选条（56dp）再留 16dp 空隙
                            bottom = chromeInsets.contentBottomForPage + 72.dp,
                        ),
                )
            }
        }
    }
}

@Composable
private fun InkvolNavHost(
    navController: NavHostController,
    settingsRepository: SettingsRepository,
    libraryRepository: LibraryRepository,
    showHeaderImport: Boolean,
    readerActive: Boolean,
    readerOpenViewModel: ReaderOpenViewModel,
    readerOpenActive: Boolean,
    onCloseReader: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val readerOpen = readerOpenViewModel.controller
    val animationsEnabled = rememberAnimationsEnabled()
    val view = LocalView.current
    val density = LocalDensity.current
    val novelTextMeasurer = rememberTextMeasurer()
    NavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize(),
            startDestination = TopDestination.Library.route,
            enterTransition = {
                tabEnter(tabShift(initialState.destination.route, targetState.destination.route))
            },
            exitTransition = {
                tabExit(tabShift(initialState.destination.route, targetState.destination.route))
            },
            popEnterTransition = {
                tabEnter(tabShift(initialState.destination.route, targetState.destination.route))
            },
            popExitTransition = {
                tabExit(tabShift(initialState.destination.route, targetState.destination.route))
            },
            sizeTransform = null,
        ) {
            composable(
                route = TopDestination.Library.route,
                // 一镜到底由宿主 overlay 状态机接管，路由切换不做动画
                exitTransition = {
                    if (targetState.destination.route?.startsWith("reader/") == true ||
                        targetState.destination.route?.startsWith("novel-reader/") == true
                    ) {
                        ExitTransition.None
                    } else {
                        tabExit(tabShift(initialState.destination.route, targetState.destination.route))
                    }
                },
                popEnterTransition = {
                    if (initialState.destination.route?.startsWith("reader/") == true ||
                        initialState.destination.route?.startsWith("novel-reader/") == true
                    ) {
                        EnterTransition.None
                    } else {
                        tabEnter(tabShift(initialState.destination.route, targetState.destination.route))
                    }
                },
            ) {
                CompositionLocalProvider(
                    LocalReaderActive provides readerActive,
                ) {
                    // 一镜到底 Opening/Closing 在书架 UI 上播放时，
                    // 不能让「再按一次退出」抢走系统返回。
                    TabExitBackHandler(enabled = readerOpen.phase == ReaderOpenPhase.Idle)
                    LibraryScreen(
                        settingsRepository = settingsRepository,
                        libraryRepository = libraryRepository,
                        showHeaderImport = showHeaderImport,
                        onOpenBook = { book, rect ->
                            if (readerOpen.phase == ReaderOpenPhase.Idle) {
                                // C 层锚点：跨方向恢复时把书架视口定位到这本书，
                                // 保证返回动画的飞回目标卡在视口内能回报。
                                LibraryGridScroll.anchorBookId = book.id
                                if (book.type == "NOVEL") {
                                    val cutout = WindowInsetsCompat.toWindowInsetsCompat(view.rootWindowInsets)
                                        .getInsets(WindowInsetsCompat.Type.displayCutout())
                                    readerOpenViewModel.startNovelOpen(
                                        book = book,
                                        sourceRect = rect,
                                        rootWidthPx = view.width.coerceAtLeast(1),
                                        rootHeightPx = view.height.coerceAtLeast(1),
                                        repository = libraryRepository,
                                        settingsRepository = settingsRepository,
                                        textMeasurer = novelTextMeasurer,
                                        density = density,
                                        cutoutLeftPx = cutout.left,
                                        cutoutTopPx = cutout.top,
                                        cutoutRightPx = cutout.right,
                                        cutoutBottomPx = cutout.bottom,
                                    )
                                } else if (!animationsEnabled) {
                                    // 关闭动画时直接打开；Reader 会自行建立书源会话。
                                    navController.navigate("reader/${book.id}")
                                } else {
                                    readerOpenViewModel.startOpen(
                                        book = book,
                                        repository = libraryRepository,
                                        sourceRect = rect,
                                        rootWidthPx = view.width.coerceAtLeast(1),
                                        rootHeightPx = view.height.coerceAtLeast(1),
                                    )
                                }
                            }
                        },
                    )
                }
            }
            composable(
                TopDestination.History.route,
                // 一镜到底由宿主 overlay 状态机接管，进出 reader 路由不做动画
                exitTransition = {
                    if (targetState.destination.route?.startsWith("reader/") == true ||
                        targetState.destination.route?.startsWith("novel-reader/") == true
                    ) {
                        ExitTransition.None
                    } else {
                        tabExit(tabShift(initialState.destination.route, targetState.destination.route))
                    }
                },
                popEnterTransition = {
                    if (initialState.destination.route?.startsWith("reader/") == true ||
                        initialState.destination.route?.startsWith("novel-reader/") == true
                    ) {
                        EnterTransition.None
                    } else {
                        tabEnter(tabShift(initialState.destination.route, targetState.destination.route))
                    }
                },
            ) {
                // 一镜到底 Opening/Closing 在历史页 UI 上播放时，
                // 不能让「再按一次退出」抢走系统返回。
                TabExitBackHandler(enabled = readerOpen.phase == ReaderOpenPhase.Idle)
                HistoryScreen(
                    repository = libraryRepository,
                    onOpenBook = { entry, rect ->
                        if (readerOpen.phase == ReaderOpenPhase.Idle) {
                            // C 层锚点：同书架入口。
                            LibraryGridScroll.anchorBookId = entry.book.id
                            if (entry.book.type == "NOVEL") {
                                val cutout = WindowInsetsCompat.toWindowInsetsCompat(view.rootWindowInsets)
                                    .getInsets(WindowInsetsCompat.Type.displayCutout())
                                readerOpenViewModel.startNovelOpen(
                                    book = entry.book,
                                    sourceRect = rect,
                                    rootWidthPx = view.width.coerceAtLeast(1),
                                    rootHeightPx = view.height.coerceAtLeast(1),
                                    repository = libraryRepository,
                                    settingsRepository = settingsRepository,
                                    textMeasurer = novelTextMeasurer,
                                    density = density,
                                    cutoutLeftPx = cutout.left,
                                    cutoutTopPx = cutout.top,
                                    cutoutRightPx = cutout.right,
                                    cutoutBottomPx = cutout.bottom,
                                )
                            } else if (!animationsEnabled) {
                                navController.navigate("reader/${entry.book.id}")
                            } else {
                                readerOpenViewModel.startOpen(
                                    book = entry.book,
                                    repository = libraryRepository,
                                    sourceRect = rect,
                                    rootWidthPx = view.width.coerceAtLeast(1),
                                    rootHeightPx = view.height.coerceAtLeast(1),
                                )
                            }
                        }
                    },
                )
            }
            composable(TopDestination.Settings.route) {
                TabExitBackHandler()
                SettingsScreen(
                    settingsRepository = settingsRepository,
                    libraryRepository = libraryRepository,
                    onOpenLibraryManager = { navController.navigate("library-manager") },
                    onOpenPanelSpike = { navController.navigate("panel-spike") },
                )
            }
            composable(route = "library-manager") {
                LibraryManagerScreen(
                    repository = libraryRepository,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(route = "panel-spike") {
                PanelSpikeScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "novel-reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { entry ->
                val openBookId = entry.arguments?.getString("bookId").orEmpty()
                NovelReaderScreen(
                    bookId = openBookId,
                    repository = libraryRepository,
                    settingsRepository = settingsRepository,
                    session = readerOpen.novelSession?.takeIf { it.book.id == openBookId },
                    openActive = readerOpenActive,
                    onTargetRect = { rect -> readerOpen.setTargetRect(openBookId, rect) },
                    onReaderReady = { readerOpen.setReaderReady(openBookId) },
                    onContentSnapshot = { bitmap -> readerOpen.updateNovelContent(openBookId, bitmap) },
                    onBack = { onCloseReader("Back") },
                )
            }
            composable(
                route = "novel-preview/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
            ) { entry ->
                NovelChapterPreviewScreen(
                    repository = libraryRepository,
                    bookId = entry.arguments?.getString("bookId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { entry ->
                val openBookId = entry.arguments?.getString("bookId").orEmpty()
                ReaderScreen(
                    bookId = openBookId,
                    repository = libraryRepository,
                    settingsRepository = settingsRepository,
                    session = readerOpen.session?.takeIf { it.book.id == openBookId },
                    sessionFailed = readerOpen.contentFailed,
                    contentAspect = readerOpen.contentAspect,
                    openActive = readerOpenActive,
                    backEnabled = readerOpen.phase != ReaderOpenPhase.Closing,
                    onTargetRect = { rect -> readerOpen.setTargetRect(openBookId, rect) },
                    onReaderReady = { readerOpen.setReaderReady(openBookId) },
                    onBack = { onCloseReader("Back") },
                )
            }
        }
}

/** 一镜到底时间轴；只保留唯一 Animatable，避免每帧双重状态写入。 */
private class ReaderOpenTimelineState {
    val progress = Animatable(0f)
}

@Composable
private fun rememberReaderOpenTimeline(
    controller: ReaderOpenController,
    durationMs: Int,
): ReaderOpenTimelineState {
    val timeline = remember(controller) { ReaderOpenTimelineState() }

    LaunchedEffect(controller.phase, durationMs) {
        if (controller.phase == ReaderOpenPhase.Preparing) {
            timeline.progress.snapTo(0f)
        }
        if (controller.phase == ReaderOpenPhase.Closing && controller.closingHold) {
            // 旋转后返回：旧 sourceRect 是打开方向的坐标，当前布局下不可信。
            // progress 停在全屏（容器与 Reader 末帧同构、盖住书架以新方向重建的
            // 过程），等书架回报首个稳定坐标（updateSourceRect 释放闸）或宿主
            // 超时（releaseClosingHold）后，再从全屏缩回正确卡片。
            snapshotFlow { controller.closingHold }.first { !it }
        }
        timeline.progress.animateTo(
            targetValue = controller.targetProgress,
            animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
        )
    }

    return timeline
}

@Composable
private fun ReaderOpenOverlay(
    controller: ReaderOpenController,
    timeline: ReaderOpenTimelineState,
    novelReaderMounted: Boolean,
) {
    // Preparing 不起飞；Visible 后 overlay 已安全移除。
    if (controller.phase == ReaderOpenPhase.Idle ||
        controller.phase == ReaderOpenPhase.Preparing ||
        controller.phase == ReaderOpenPhase.Visible
    ) return
    val source = controller.sourceRect
    val target = controller.targetRect ?: source ?: return
    if (source == null && controller.phase == ReaderOpenPhase.Opening && !controller.readerReady) {
        return
    }
    val closing = controller.phase == ReaderOpenPhase.Closing
    val contentReady = controller.contentBitmap != null || controller.readerReady
    val coverBitmap = controller.coverBitmap
    val coverPath = controller.coverPath
    val novelFlight = controller.isNovel

    // 打开阶段不再使用封面位图：点击瞬间飞行物就是「迷你阅读器」（黑底 + 首页含黑边）。
    // 封面只参与返回交叉；封面预解码失败时返回阶段走 AsyncImage 兜底。
    val showCoverFallback = closing && coverBitmap == null && coverPath != null

    // 起飞时首页未就绪：记录就绪时刻，容器内 120ms 淡入；
    // 起飞即就绪（常态）则首帧直接显示，不能等 LaunchedEffect 下一帧才置位，
    // 否则会先空一帧再出现，形成「闪一下」。
    var pageFadeStartMs by remember {
        mutableStateOf(if (contentReady) Long.MIN_VALUE else Long.MAX_VALUE)
    }
    LaunchedEffect(controller.phase, contentReady) {
        if (controller.phase == ReaderOpenPhase.Opening) {
            if (contentReady) {
                pageFadeStartMs = Long.MIN_VALUE
            } else {
                snapshotFlow { controller.contentBitmap != null || controller.readerReady }
                    .first { it }
                if (controller.phase == ReaderOpenPhase.Opening) {
                    pageFadeStartMs = SystemClock.uptimeMillis()
                }
            }
        }
    }

    val density = LocalDensity.current

    // 所有 progress 相关内容都放进 draw 阶段：
    // 动画每帧只触发 redraw，不再触发 overlay 的 measure/layout/composition。
    // handoff 是纯函数，不是第二个 Animatable，避免每帧双重状态写入。
    // zIndex(1f)：overlay 与 Scaffold 同为宿主 Box 的兄弟，书架内容挂
    // layerBackdrop 的 GraphicsLayer 采集层——快速连续开关时其重放绘制偶尔
    // 合成到 overlay 之后（卡片盖住收缩中的容器，「跳到所有封面后面」）。
    // zIndex 抬层后整个 Scaffold 子树（含所有采集层重放）必定合成在 overlay 之下。
    Canvas(
        modifier = Modifier
            .zIndex(1f)
            .fillMaxSize()
            // 过渡层必须消费输入，避免用户在 Opening/Closing 中穿透到书架或 Reader。
            .pointerInput(Unit) {
                detectTapGestures { }
            },
    ) {
        val progress = timeline.progress.value
        val handoff = if (contentReady) readerHandoffAlpha(progress) else 0f
        val rootRect = ReaderOpenRect(0f, 0f, size.width, size.height)
        // 容器：封面卡矩形 → 全屏根矩形；首页矩形：封面卡矩形 → Reader 实际可视矩形。
        // 两者同起点插值，容器全程包住首页；黑边随放大自然出现，终点与 Reader 布局同构。
        val containerRect = source?.lerpTo(rootRect, progress) ?: rootRect
        val pageRect = source?.lerpTo(target, progress) ?: target
        // 圆角随容器等比放大（保持封面卡的圆角比例，观感全程是圆的），上限 28dp。
        // 打开方向全程保持圆角：切路由后 Reader 黑底垫在 overlay 下方，
        // 黑底上的黑圆角不可见，markVisible 撤除瞬间与 Reader 直角首帧无缝。
        // 返回方向保留极小闸：返回首帧（p=1）必须直角才能与 Reader 末帧同构
        // （此刻底下已是书架 UI，没有黑色兜底），14ms 内圆角长回，肉眼不可见。
        val cardRadiusPx = 14.dp.toPx()
        val maxRadiusPx = 28.dp.toPx()
        val radiusScale = source?.takeIf { it.width > 0f }
            ?.let { containerRect.width / it.width } ?: 1f
        val radiusGate = if (closing) 1f - readerStage(progress, 0.95f, 1f) else 1f
        val radius = (cardRadiusPx * radiusScale).coerceAtMost(maxRadiusPx) * radiusGate
        val fadeGate = when {
            closing -> 1f
            pageFadeStartMs == Long.MIN_VALUE -> 1f
            pageFadeStartMs == Long.MAX_VALUE -> 0f
            else -> ((SystemClock.uptimeMillis() - pageFadeStartMs) / 120f)
                .coerceIn(0f, 1f)
                .let { it * it * (3f - 2f * it) }
        }
        val pageAlpha = if (closing) handoff else fadeGate
        // 打开阶段不画封面：小说容器填充正文背景色，放大到全屏后与正文无缝。
        // 返回阶段封面随 handoff 淡入盖回正文。
        val coverAlpha = if (closing) {
            (1f - handoff).coerceIn(0f, 1f)
        } else {
            0f
        }
        // 漫画：迷你阅读器黑底；小说：正文背景色容器。
        val containerColor = if (novelFlight) {
            Color(controller.novelBackgroundColor)
        } else {
            Color.Black
        }
        // 返回首帧小说路由可能尚未真正退栈：先铺满不透明正文底色，避免容器外
        // 透出仍在挂载的真实阅读器正文（表现为「背景叠着小说内容」）。
        if (closing && novelFlight && novelReaderMounted) {
            drawRect(color = containerColor)
        }

        if (containerRect.width > 0f && containerRect.height > 0f) {
            val containerPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = containerRect.left,
                        top = containerRect.top,
                        right = containerRect.left + containerRect.width,
                        bottom = containerRect.top + containerRect.height,
                        cornerRadius = CornerRadius(radius),
                    ),
                )
            }
            clipPath(containerPath) {
                // 迷你阅读器容器：漫画黑底即阅读器黑边；小说填充正文背景色。
                drawRect(color = containerColor)
                // 纹理预热：封面位图只在返回交叉淡化时才首绘，首次绘制的 GPU 纹理上传
                //（~1.1MB）会卡掉 1-5 帧 = 「内容切换到封面那一瞬间」闪一下（gfxinfo
                // Slow bitmap uploads 与返回次数一致）。Opening 期间以近零 alpha 预绘，
                // 上传成本落在打开动画内，返回时纹理已就绪。
                if (!closing) {
                    coverBitmap?.let { bmp ->
                        drawReaderBitmap(
                            bitmap = bmp,
                            rect = pageRect,
                            alpha = 0.01f,
                            radiusPx = 0f,
                            fillWidthTop = false,
                        )
                    }
                }
                if (pageAlpha > 0.001f) {
                    controller.contentBitmap?.let { pageBitmap ->
                        drawReaderBitmap(
                            bitmap = pageBitmap,
                            rect = pageRect,
                            alpha = pageAlpha,
                            radiusPx = 0f,
                            fillWidthTop = controller.readerVertical,
                        )
                    }
                }
                if (coverAlpha > 0.001f) {
                    coverBitmap?.let { bmp ->
                        drawReaderBitmap(
                            bitmap = bmp,
                            rect = pageRect,
                            alpha = coverAlpha,
                            radiusPx = 0f,
                            fillWidthTop = false,
                        )
                    }
                }
            }
        }
    }

    // 兜底：返回阶段封面预解码失败时仍显示封面；打开阶段不再使用封面，正常路径不会走这里。
    if (showCoverFallback) {
        val progress = timeline.progress.value
        val handoff = if (contentReady) readerHandoffAlpha(progress) else 0f
        val rect = source?.lerpTo(target, progress) ?: target
        val coverAlpha = (1f - handoff).coerceIn(0f, 1f)
        val shape = RoundedCornerShape(lerp(14.dp, 0.dp, handoff))
        AsyncImage(
            model = File(coverPath),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                // 与容器 Canvas 同层（zIndex 1f），组合序保证封面画在容器之上
                .zIndex(1f)
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(
                    with(density) { rect.width.toDp() },
                    with(density) { rect.height.toDp() },
                )
                .clip(shape)
                .graphicsLayer { alpha = coverAlpha },
        )
    }
}

private fun DrawScope.drawReaderBitmap(
    bitmap: ImageBitmap,
    rect: ReaderOpenRect,
    alpha: Float,
    radiusPx: Float,
    fillWidthTop: Boolean,
) {
    if (alpha <= 0.001f || bitmap.width <= 0 || bitmap.height <= 0) return
    val sourceRect = if (fillWidthTop) {
        fillWidthTopSource(
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            dstLeft = rect.left,
            dstTop = rect.top,
            dstWidth = rect.width,
            dstHeight = rect.height,
        )
    } else {
        cropSource(
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            dstWidth = rect.width,
            dstHeight = rect.height,
        )
    }
    val dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
    val dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt())
    val draw: () -> Unit = {
        drawImage(
            image = bitmap,
            srcOffset = sourceRect.offset,
            srcSize = sourceRect.size,
            dstOffset = dstOffset,
            dstSize = dstSize,
            alpha = alpha.coerceIn(0f, 1f),
            filterQuality = FilterQuality.Medium,
        )
    }

    if (radiusPx > 0.5f) {
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = rect.left,
                    top = rect.top,
                    right = rect.left + rect.width,
                    bottom = rect.top + rect.height,
                    cornerRadius = CornerRadius(radiusPx),
                ),
            )
        }
        clipPath(path) { draw() }
    } else {
        draw()
    }
}

private data class ReaderDrawSource(
    val offset: IntOffset,
    val size: IntSize,
)

private fun cropSource(
    bitmapWidth: Int,
    bitmapHeight: Int,
    dstWidth: Float,
    dstHeight: Float,
): ReaderDrawSource {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || dstWidth <= 0f || dstHeight <= 0f) {
        return ReaderDrawSource(IntOffset.Zero, IntSize(bitmapWidth, bitmapHeight))
    }
    val bitmapAspect = bitmapWidth.toFloat() / bitmapHeight
    val dstAspect = dstWidth / dstHeight
    return if (bitmapAspect > dstAspect) {
        val srcWidth = (bitmapHeight * dstAspect).roundToInt()
        ReaderDrawSource(
            offset = IntOffset((bitmapWidth - srcWidth) / 2, 0),
            size = IntSize(srcWidth, bitmapHeight),
        )
    } else {
        val srcHeight = (bitmapWidth / dstAspect).roundToInt()
        ReaderDrawSource(
            offset = IntOffset(0, (bitmapHeight - srcHeight) / 2),
            size = IntSize(bitmapWidth, srcHeight),
        )
    }
}

private fun fillWidthTopSource(
    bitmapWidth: Int,
    bitmapHeight: Int,
    dstLeft: Float,
    dstTop: Float,
    dstWidth: Float,
    dstHeight: Float,
): ReaderDrawSource {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || dstWidth <= 0f || dstHeight <= 0f) {
        return ReaderDrawSource(IntOffset.Zero, IntSize(bitmapWidth, bitmapHeight))
    }
    val scaledHeight = dstWidth * bitmapHeight / bitmapWidth
    return if (scaledHeight >= dstHeight) {
        val srcHeight = (bitmapHeight * dstHeight / scaledHeight).roundToInt()
        ReaderDrawSource(
            offset = IntOffset(0, 0),
            size = IntSize(bitmapWidth, srcHeight),
        )
    } else {
        ReaderDrawSource(
            offset = IntOffset(0, 0),
            size = IntSize(bitmapWidth, bitmapHeight),
        )
    }
}

/**
 * 顶层 Tab 的返回处理：不回首页，改成「再按一次退出」。
 * 空实现会让用户以为卡住，这里给一次反馈并允许退出。
 */
@Composable
private fun TabExitBackHandler(enabled: Boolean = true) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val glassNotice = LocalGlassNotice.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var armed by remember { mutableStateOf(false) }

    BackHandler(enabled = enabled) {
        if (armed) {
            (context as? android.app.Activity)?.finish()
        } else {
            armed = true
            glassNotice.show("再按一次退出")
            scope.launch {
                delay(2000)
                armed = false
            }
        }
    }
}

/** smoothstep 区间：主 progress 为线性，视觉节奏由各曲线控制。 */
private fun readerStage(progress: Float, start: Float, end: Float): Float {
    val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 返回交叉（Closing 反向播放）：首页 → 封面；打开阶段不再使用封面。 */
private fun readerHandoffAlpha(progress: Float): Float = readerStage(progress, 0.54f, 0.90f)

/** 顶层 Tab 的顺序；阅读器不参与左右滑动方向计算。 */
private val tabOrder = listOf(
    TopDestination.Library.route,
    TopDestination.History.route,
    TopDestination.Settings.route,
)

/** 目标相对起点的位移方向：1=向前，-1=向后，null=非 Tab 路由。 */
private fun tabShift(initialRoute: String?, targetRoute: String?): Int? {
    val from = initialRoute?.let { tabOrder.indexOf(it) }?.takeIf { it >= 0 }
    val to = targetRoute?.let { tabOrder.indexOf(it) }?.takeIf { it >= 0 }
    return when {
        from != null && to != null -> to - from
        // 设置二级整页（书库管理 / 分格检测实验）：从设置进=向前，回设置=向后，
        // 让全局默认转场给出规范的左右滑动（280ms）而非 fade
        initialRoute == TopDestination.Settings.route &&
            targetRoute != null && targetRoute in settingsSubRoutes -> 1
        initialRoute != null && initialRoute in settingsSubRoutes &&
            targetRoute == TopDestination.Settings.route -> -1
        else -> null
    }
}

/** 设置页经 NavHost 跳出的二级整页路由，参与与 Tab 一致的左右滑动转场。 */
private val settingsSubRoutes = setOf("library-manager", "panel-spike")

/** Tab 进入转场：向前从右滑入，向后从左滑入。 */
private fun tabEnter(shift: Int?): EnterTransition = when {
    shift == null -> fadeIn(tween(220, easing = FastOutSlowInEasing))
    shift > 0 -> slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it }
    shift < 0 -> slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it }
    else -> fadeIn(tween(220, easing = FastOutSlowInEasing))
}

/** Tab 离开转场：向前向左滑出，向后向右滑出。 */
private fun tabExit(shift: Int?): ExitTransition = when {
    shift == null -> fadeOut(tween(220, easing = FastOutSlowInEasing))
    shift > 0 -> slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it }
    shift < 0 -> slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it }
    else -> fadeOut(tween(220, easing = FastOutSlowInEasing))
}

private fun NavHostController.switchTop(destination: TopDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
