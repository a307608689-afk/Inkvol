package com.inkvol.app.ui.reader

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkvol.app.data.model.BookReadingPrefs
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.model.decodeBookPrefs
import com.inkvol.app.data.model.encodeBookPrefs
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.ui.components.LocalReaderOpenController
import com.inkvol.app.ui.reader.panel.PanelDetector
import com.inkvol.app.ui.reader.panel.PanelLayoutCache
import com.inkvol.app.ui.reader.panel.ReaderPanelEditorSheet
import com.inkvol.app.util.Fingerprint
import com.inkvol.app.ui.components.ReaderOpenRect
import com.inkvol.app.ui.components.ReaderOpenSession
import com.inkvol.app.ui.components.ReaderSurfaceAlive
import com.inkvol.app.ui.components.glass.GlassDefaults
import com.inkvol.app.ui.components.glass.GlassDimens
import com.inkvol.app.ui.components.glass.GlassEdge
import com.inkvol.app.ui.components.glass.GlassOverlay
import com.inkvol.app.ui.components.glass.GlassOverlayMotion
import com.inkvol.app.ui.components.glass.GlassOverlayPresence
import com.inkvol.app.ui.components.glass.GlassRounded
import com.inkvol.app.ui.components.glass.GlassTier
import com.inkvol.app.ui.components.glass.rememberGlassOverlayPresence
import com.inkvol.app.ui.theme.InkvolTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * M3 漫画阅读器（基础版）：
 * - 分页（RTL/LTR）/ 纵向滚动两种模式；
 * - 悬浮玻璃顶/底栏 + 玻璃快捷面板；
 * - Bitmap LRU + ±2 预取 + 进度落库；
 * - 点击左/中/右三区，3 秒自动收起 chrome。
 *
 * M3.5 会补：双页/跨页切割、捏合与双击缩放、缩略图列表、亮度色温手势。
 */
@Composable
fun ReaderScreen(
    bookId: String,
    repository: LibraryRepository,
    settingsRepository: com.inkvol.app.data.settings.SettingsRepository,
    session: ReaderOpenSession?,
    sessionFailed: Boolean,
    contentAspect: Float?,
    openActive: Boolean,
    /**
     * 返回键兜底接管开关：openActive 只在 Opening 为 true（Visible 阶段为 false），
     * 若 ReaderContent 因 session/错误态短暂卸载，Visible 阶段会没有任何 enabled 的
     * BackHandler——系统默认返回静默 popBackStack，控制器卡死在 Visible，后续打开
     * 全部被点击入口的 Idle 守卫丢弃。宿主传「非 Closing 即接管」。
     */
    backEnabled: Boolean = true,
    onTargetRect: (ReaderOpenRect?) -> Unit,
    onReaderReady: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activeSession = session?.takeIf { it.book.id == bookId }
    val density = androidx.compose.ui.platform.LocalDensity.current

    // 进程死亡 / 无宿主 session 时兜底普通打开；正常一镜到底不会走这里。
    var fallbackSession by remember { mutableStateOf<ReaderOpenSession?>(null) }
    var fallbackError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bookId, session, sessionFailed, openActive) {
        if (activeSession != null || sessionFailed || openActive) return@LaunchedEffect
        var loader: PageLoader? = null
        try {
            val record = repository.bookById(bookId) ?: error("书不见了")
            val startPage = repository.progressPage(bookId)
                .coerceIn(0, (record.pageCount - 1).coerceAtLeast(0))
            val source = repository.openSource(record)
            loader = PageLoader(
                source = source,
                targetWidth = view.width.coerceAtLeast(1),
                targetHeight = view.height.coerceAtLeast(1),
            )
            val bitmap = loader.load(startPage)
            fallbackSession = ReaderOpenSession(
                book = record,
                source = source,
                loader = loader,
                startPage = startPage,
                firstPage = bitmap?.asImageBitmap(),
            )
        } catch (error: CancellationException) {
            loader?.close()
            throw error
        } catch (error: Throwable) {
            loader?.close()
            fallbackError = error.message ?: "无法打开这本书"
        }
    }
    DisposableEffect(fallbackSession) {
        onDispose {
            // fallback session 的所有权在当前 ReaderScreen；宿主 session 由状态机关闭。
            if (session == null) fallbackSession?.close()
        }
    }

    DisposableEffect(Unit) {
        // 一镜到底返回垫层的唯一判据：阅读器组件是否还挂着（路由状态滞后 2~3 帧不可用）
        ReaderSurfaceAlive.enter()
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val hideBars = Runnable {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // 只在路由挂载时隐藏一次；重复 hide 会在打开动画中触发 insets/relayout。
        // 注意：不要在这里覆盖 Compose 根视图的 OnApplyWindowInsetsListener；
        // 那样 dispose 时即使置 null，也会把 Compose 自己的 insets 分发清掉，
        // 导致退出阅读器后书架拿不到“状态栏恢复”的 inset（标题压状态栏，需重启）。
        view.post(hideBars)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            ReaderSurfaceAlive.exit()
            view.removeCallbacks(hideBars)
            // 退出后只恢复导航栏（底栏 inset 需要）；状态栏由书架玻璃头按滚动状态
            // 单点控制——这里 show statusBars 会在滚动隐藏态下造成「闪现后又被隐藏」。
            // 延迟到下一帧，避免和路由转场/书架 header 抢系统栏控制权。
            view.post {
                controller?.show(WindowInsetsCompat.Type.navigationBars())
            }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Reader 已切入但书源还没加载完时，ReaderContent 尚未组合；
    // 这里兜底保证一镜到底仍能被返回立即取消。
    // backEnabled 覆盖 Visible 等阶段（openActive 仅 Opening），防止 ReaderContent
    // 短暂卸载时返回键落到系统默认——静默 popBackStack 会让 phase 卡死非 Idle。
    BackHandler(enabled = openActive || backEnabled) {
        onBack()
    }

    // 黑底从路由切入起就保持不透明；overlay 里的 cover/page 同层交叉，
    // Reader 内容在下面保持不透明，由 cover overlay 淡出后露出。
    // 阅读器固定深色：黑底页面上的玻璃必须走深色垫层，文字才够亮
    InkvolTheme(darkTheme = true) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val errorMessage = if (sessionFailed) {
                "书源预打开失败"
            } else {
                fallbackError
            }
            val effectiveSession = activeSession ?: fallbackSession
            val effectiveAspect = contentAspect ?: effectiveSession?.firstPageAspect
            when {
                errorMessage != null -> ReaderMessage(
                    title = "打不开",
                    message = errorMessage,
                    onBack = onBack,
                )

                effectiveSession == null -> ReaderLoading()

                else -> Box(modifier = Modifier.fillMaxSize()) {
                    ReaderContent(
                        book = effectiveSession.book,
                        loader = effectiveSession.loader,
                        startPage = effectiveSession.startPage,
                        repository = repository,
                        settingsRepository = settingsRepository,
                        openActive = openActive,
                        coverAspect = effectiveAspect,
                        onVisualRectChange = { rect ->
                            onTargetRect(
                                rect?.let {
                                    ReaderOpenRect(
                                        left = with(density) { it.left.toPx() },
                                        top = with(density) { it.top.toPx() },
                                        width = with(density) { it.width.toPx() },
                                        height = with(density) { it.height.toPx() },
                                    )
                                },
                            )
                        },
                        onBack = onBack,
                        onFirstPageReady = onReaderReady,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderContent(
    book: BookRecord,
    loader: PageLoader,
    startPage: Int,
    repository: LibraryRepository,
    settingsRepository: com.inkvol.app.data.settings.SettingsRepository,
    openActive: Boolean,
    coverAspect: Float?,
    onVisualRectChange: (DpRect?) -> Unit,
    onBack: () -> Unit,
    onFirstPageReady: () -> Unit,
) {
    val count = loader.source.pageCount.coerceAtLeast(1)
    val prefs by settingsRepository.preferences.collectAsStateWithLifecycle(initialValue = null)
    // 本书快照：仅「单独记忆」开启时读取/写入
    val savedBookPrefsRaw = prefs?.let {
        settingsRepository.stringOf(it, SettingsKeys.readerBookKey(book.id), "")
    }
    // 全局布局默认：所有未单独记忆的书读它、改它（编码与本书快照同构）
    val globalBookPrefsRaw = prefs?.let {
        settingsRepository.stringOf(it, SettingsKeys.READER_GLOBAL_PREFS, "")
    }
    // 单独记忆开关：null = 用户从未选择过（老书按已有快照自动迁移为开）
    val soloStored = prefs?.let {
        settingsRepository.booleanOrNull(it, SettingsKeys.readerBookSoloKey(book.id))
    }
    // 智能分格：全局快照未建立时硬默认「关闭」（不再从旧 panelDefault 键播种）
    val globalPanelMode = remember(globalBookPrefsRaw) {
        decodeBookPrefs(globalBookPrefsRaw)?.panelMode ?: SettingsKeys.PANEL_OFF
    }
    val effectiveSolo = soloStored ?: (savedBookPrefsRaw?.isNotBlank() == true)
    // 单独记忆开 → 本书快照（缺则回退全局作起点）；关 → 全局跟随（本书旧快照忽略）。
    // 未显式改过分格档的书，其分格档跟随全局，故 defaultPanelMode 传全局档。
    val savedBookPrefs = remember(
        savedBookPrefsRaw,
        globalBookPrefsRaw,
        globalPanelMode,
        effectiveSolo,
    ) {
        val perBook = decodeBookPrefs(savedBookPrefsRaw, globalPanelMode)
        val global = decodeBookPrefs(globalBookPrefsRaw, globalPanelMode)
        if (effectiveSolo) perBook ?: global else global ?: perBook
    }
    val gestures = remember(prefs) {
        buildGestureSettings(prefs, settingsRepository)
    }
    var bookPrefsLoaded by remember(book.id) { mutableStateOf(false) }

    // 全局双页档位（auto=横屏自动 / always=始终 / off=关闭）：纯派生值，不写每本书偏好
    val doublePageMode = prefs?.let {
        settingsRepository.stringOf(it, SettingsKeys.READER_DOUBLE_PAGE, SettingsKeys.DOUBLE_PAGE_AUTO)
    } ?: SettingsKeys.DOUBLE_PAGE_AUTO
    // 全局翻页动画档（instant / slide / cover）：仅影响点击 / 按键翻页，拖拽与跳页不变
    val pageTransitionMode = prefs?.let {
        settingsRepository.stringOf(it, SettingsKeys.READER_PAGE_TRANSITION, SettingsKeys.PAGE_TRANSITION_SLIDE)
    } ?: SettingsKeys.PAGE_TRANSITION_SLIDE
    // 页号 → 宽高比（宽/高）。ReaderPage 解码成功后回填（本地解码零额外开销），
    // 宽高比超阈值的页在双页下独占一个 spread，与 SSMH 同款。
    var pageAspects by remember(book.id) { mutableStateOf<Map<Int, Float>>(emptyMap()) }

    var vertical by rememberSaveable { mutableStateOf(false) }
    var rtl by rememberSaveable { mutableStateOf(true) }
    var trimMargins by remember(book.id) { mutableStateOf(false) }
    var splitSpreads by remember(book.id) { mutableStateOf(false) }
    var repair by remember(book.id) { mutableIntStateOf(0) }
    var pageOverlap by remember(book.id) { mutableIntStateOf(0) }
    var panelMode by remember(book.id) { mutableStateOf(globalPanelMode) }
    // 是否在阅读器内显式改过分格档位：未显式设置的书跟随全局分格档
    var panelModeTouched by remember(book.id) { mutableStateOf(false) }
    // 「此本书单独记忆」：关（默认）= 全局跟随；开 = 本书排版独立保存
    var prefsSolo by remember(book.id) { mutableStateOf(effectiveSolo) }
    var chromeVisible by remember { mutableStateOf(false) }
    var quickPanel by remember { mutableStateOf(false) }
    var thumbnailsOpen by remember { mutableStateOf(false) }
    var hudText by remember { mutableStateOf<String?>(null) }
    // HUD 是连续反馈（拖动亮度/色温时每帧改文案），动画只认"有无"，文案走保留变量
    var hudHeld by remember { mutableStateOf("") }
    var sharing by remember { mutableStateOf(false) }
    // 三块面板（快捷/缩略图/分格编辑）退场期间，顶底栏要等它退完再回来
    val readerOverlays = rememberGlassOverlayPresence()
    var brightness by rememberSaveable { mutableFloatStateOf(-1f) }
    var colorTemp by rememberSaveable { mutableFloatStateOf(0f) }
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val openController = LocalReaderOpenController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var readingSessionId by remember(book.id) { mutableLongStateOf(0L) }
    var readerForeground by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> readerForeground = false
                Lifecycle.Event.ON_RESUME -> readerForeground = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // M4-B：只在阅读器可见且前台时计时；退后台/熄屏结束当前会话，恢复时开新会话。
    LaunchedEffect(book.id, openActive, readerForeground) {
        if (readingSessionId != 0L) {
            repository.endReadingSession(readingSessionId)
            readingSessionId = 0L
        }
        if (openActive || !readerForeground) return@LaunchedEffect

        val sessionId = repository.startReadingSession(book.id)
        readingSessionId = sessionId
        val startedAt = SystemClock.elapsedRealtime()
        try {
            while (isActive) {
                delay(5_000)
                repository.updateReadingSessionDuration(
                    sessionId,
                    SystemClock.elapsedRealtime() - startedAt,
                )
            }
        } finally {
            repository.endReadingSession(sessionId)
            if (readingSessionId == sessionId) readingSessionId = 0L
        }
    }

    // 首次加载生效偏好（单独记忆 → 本书快照；否则 → 全局默认）；之后用户改动实时落库。
    // 关键：prefs 首帧为 null（DataStore 尚未发射），此时不能当作"本书无存档"——
    // 否则会提前把 bookPrefsLoaded 置真（存档被永久跳过），随后保存 effect 用初值覆盖存档，
    // 表现为「书内打开智能分格，退出重进又变回关闭」。
    LaunchedEffect(savedBookPrefs, savedBookPrefsRaw, book.id) {
        if (bookPrefsLoaded) return@LaunchedEffect
        if (prefs == null) return@LaunchedEffect
        savedBookPrefs?.let { saved ->
            vertical = saved.vertical
            rtl = saved.rtl
            trimMargins = saved.trimMargins
            splitSpreads = saved.splitSpreads
            repair = saved.repair
            pageOverlap = saved.pageOverlap
            panelMode = saved.panelMode
            panelModeTouched = saved.panelModeExplicit
        }
        // 无任何快照：吃旧的分格默认档
        if (savedBookPrefs == null) panelMode = globalPanelMode
        prefsSolo = effectiveSolo
        // 无损迁移：老书已有本书快照、但从未选择过「单独记忆」→ 记为开，保留原有独立排版
        if (soloStored == null && savedBookPrefsRaw?.isNotBlank() == true) {
            settingsRepository.putBoolean(SettingsKeys.readerBookSoloKey(book.id), true)
        }
        bookPrefsLoaded = true
    }
    LaunchedEffect(
        bookPrefsLoaded,
        vertical,
        rtl,
        trimMargins,
        splitSpreads,
        repair,
        pageOverlap,
        panelMode,
        panelModeTouched,
        prefsSolo,
    ) {
        if (!bookPrefsLoaded) return@LaunchedEffect
        val snapshot = BookReadingPrefs(
            vertical = vertical,
            rtl = rtl,
            trimMargins = trimMargins,
            splitSpreads = splitSpreads,
            repair = repair,
            pageOverlap = pageOverlap,
            panelMode = panelMode,
            panelModeExplicit = panelModeTouched,
        )
        if (prefsSolo) {
            // 单独记忆：只写本书快照（旧快照保留，关闭开关时忽略但不删除）
            settingsRepository.putString(
                SettingsKeys.readerBookKey(book.id),
                encodeBookPrefs(snapshot),
            )
            // M4.5：per-book 偏好变更时间戳（同步合并 LWW 依据）
            settingsRepository.putLong(
                SettingsKeys.bookPrefTsKey(book.id),
                System.currentTimeMillis(),
            )
        } else {
            // 全局跟随：写全局默认，所有未单独记忆的书下次打开自动生效。
            // 分格档在全局快照里恒为显式（它就是权威值，不再回落到旧 panelDefault）。
            val encoded = encodeBookPrefs(snapshot.copy(panelModeExplicit = true))
            if (encoded != globalBookPrefsRaw) {
                settingsRepository.putString(SettingsKeys.READER_GLOBAL_PREFS, encoded)
            }
        }
    }

    LaunchedEffect(book.id, vertical) {
        openController?.setReaderVertical(book.id, vertical)
    }

    val initial = startPage.coerceIn(0, count - 1)
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useDouble = !vertical &&
        doublePageMode != SettingsKeys.DOUBLE_PAGE_OFF &&
        (doublePageMode == SettingsKeys.DOUBLE_PAGE_ALWAYS || isLandscape)
    val effectiveDouble = useDouble && !splitSpreads
    val splitMode = useDouble && splitSpreads
    val widePages = remember(pageAspects) {
        pageAspects.filterValues { it > WIDE_PAGE_ASPECT }.keys
    }
    val spreads = remember(count, effectiveDouble, widePages) {
        buildSpreads(count, effectiveDouble, widePages)
    }

    // M5-D：智能分格扩展到纵向 + 双页；跨页切割仍禁用。
    val panelingEnabled = panelMode != SettingsKeys.PANEL_OFF && !splitMode
    val panelLayouts = remember(book.id) { mutableStateMapOf<Int, PanelDetector.Layout>() }
    var panelEditorOpen by remember(book.id) { mutableStateOf(false) }
    // 分格编辑面板常驻组合后，退场期间仍要拿到最后一帧 layout
    var editorLayoutHeld by remember(book.id) { mutableStateOf<PanelDetector.Layout?>(null) }
    var panelPrecomputeDone by remember(book.id) { mutableIntStateOf(0) }
    var panelPrecomputeTotal by remember(book.id) { mutableIntStateOf(0) }
    var panelPrecomputeRunning by remember(book.id) { mutableStateOf(false) }

    LaunchedEffect(panelMode, trimMargins, repair, rtl) {
        panelLayouts.clear()
    }

    val panelFrameCounts = IntArray(count) { page ->
        if (!panelingEnabled) {
            1
        } else {
            panelLayouts[page]
                ?.takeIf { it.status == PanelDetector.LayoutStatus.Panelized }
                ?.frames?.size ?: 1
        }
    }
    val frameStarts = IntArray(panelFrameCounts.size + 1).also { starts ->
        starts[0] = 0
        panelFrameCounts.forEachIndexed { index, frameCount ->
            starts[index + 1] = starts[index] + frameCount
        }
    }
    val framePagerCount = frameStarts.last()

    fun frameStartForPage(page: Int): Int = frameStarts[page.coerceIn(0, count)]

    fun pageForFrame(frameIndex: Int): Int {
        var low = 0
        var high = count - 1
        val target = frameIndex.coerceIn(0, (framePagerCount - 1).coerceAtLeast(0))
        while (low < high) {
            val middle = (low + high) / 2
            if (frameStarts[middle + 1] <= target) low = middle + 1 else high = middle
        }
        return low.coerceIn(0, count - 1)
    }

    val pagerCount = when {
        panelingEnabled -> framePagerCount
        effectiveDouble -> spreads.size
        else -> count
    }
    val initialPager = when {
        panelingEnabled -> frameStartForPage(initial)
        effectiveDouble -> spreadIndexFor(initial, spreads)
        else -> initial
    }
    val pagerState = rememberPagerState(initialPage = initialPager) { pagerCount }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (panelingEnabled) frameStartForPage(initial) else initial,
    )
    val currentPage = when {
        vertical -> pageForFrame(listState.firstVisibleItemIndex)
        panelingEnabled -> pageForFrame(pagerState.currentPage)
        effectiveDouble -> spreads.getOrElse(pagerState.currentPage) { listOf(0) }.first()
        else -> pagerState.currentPage
    }
    // 进度保存 / 统计的真源：动画翻页（animateScrollToPage）中 currentPage 要等越过页中点才翻转，
    // 用 targetPage 映射避免「翻页动画未落定就退出 → 进度退一页」；scrollToPage（无动画/跳页）下两者恒等。
    val savingPage = when {
        vertical -> pageForFrame(listState.firstVisibleItemIndex)
        panelingEnabled -> pageForFrame(pagerState.targetPage)
        effectiveDouble -> spreads.getOrElse(pagerState.targetPage) { listOf(0) }.first()
        else -> pagerState.targetPage
    }
    val currentContainerIndex = if (vertical) {
        listState.firstVisibleItemIndex
    } else {
        pagerState.currentPage
    }
    val currentFrameIndex = (currentContainerIndex - frameStartForPage(currentPage))
        .coerceIn(0, (panelFrameCounts.getOrElse(currentPage) { 1 } - 1).coerceAtLeast(0))
    val currentFrameCount = panelFrameCounts.getOrElse(currentPage) { 1 }
    val panelFrameVisible = panelingEnabled && currentFrameCount > 1
    val panelPrecomputeSummary = when {
        !panelingEnabled -> "未启用"
        panelPrecomputeRunning -> "${panelPrecomputeDone}/${panelPrecomputeTotal}"
        panelPrecomputeTotal > 0 -> "完成 ${panelPrecomputeDone}/${panelPrecomputeTotal}"
        else -> "未启动"
    }

    // M5-C：当前页优先；相邻页低优先级预检测，结果全部落盘。
    LaunchedEffect(panelingEnabled, currentPage, trimMargins, repair, rtl) {
        if (!panelingEnabled || currentPage !in 0 until count) return@LaunchedEffect
        listOf(currentPage, currentPage + 1, currentPage + 2)
            .filter { it in 0 until count }
            .forEach { page ->
                if (panelLayouts.containsKey(page)) return@forEach
                val direction = if (rtl) {
                    PanelDetector.ReadingDirection.RightToLeft
                } else {
                    PanelDetector.ReadingDirection.LeftToRight
                }
                val layout = detectPanelLayout(
                    context = context,
                    loader = loader,
                    bookId = book.id,
                    pageIndex = page,
                    trimMargins = trimMargins,
                    repair = repair,
                    panelMode = panelMode,
                    rtl = rtl,
                    direction = direction,
                )
                if (layout != null) panelLayouts[page] = layout
            }
    }

    // M5-D：全书后台预检，只写磁盘缓存，不改变当前 pager 结构，避免翻页跳位。
    LaunchedEffect(book.id, panelingEnabled, trimMargins, repair, rtl) {
        if (!panelingEnabled) {
            panelPrecomputeRunning = false
            panelPrecomputeDone = 0
            panelPrecomputeTotal = 0
            return@LaunchedEffect
        }
        panelPrecomputeRunning = true
        panelPrecomputeTotal = count
        panelPrecomputeDone = 0
        val direction = if (rtl) {
            PanelDetector.ReadingDirection.RightToLeft
        } else {
            PanelDetector.ReadingDirection.LeftToRight
        }
        for (page in 0 until count) {
            // 每页之间即时响应取消：阅读器销毁（返回书架）后预检立刻停止，
            // 不再有残余解码/分析抢主线程。
            if (!isActive) return@LaunchedEffect
            runCatching {
                detectPanelLayout(
                    context = context,
                    loader = loader,
                    bookId = book.id,
                    pageIndex = page,
                    trimMargins = trimMargins,
                    repair = repair,
                    panelMode = panelMode,
                    rtl = rtl,
                    direction = direction,
                )
            }
            panelPrecomputeDone = page + 1
        }
        panelPrecomputeRunning = false
    }

    // 解码成功即回填宽高比（放大重解码比例不变，按值去重避免无效重组）
    fun recordAspect(page: Int, ratio: Float) {
        if (ratio > 0f && pageAspects[page] != ratio) {
            pageAspects = pageAspects + (page to ratio)
        }
    }

    var statsLastPage by remember(book.id, initial) { mutableIntStateOf(initial) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val maxPanelHeight = maxHeight * 0.58f
        // handoff 完成后不再唤起 chrome；打开即为干净全屏，单击才显示。
        // loader 由 ReaderOpenSession 提供；生命周期由宿主状态机管理。

        val visualRect = readerCoverRect(
            aspect = coverAspect,
            screenW = configuration.screenWidthDp.dp,
            screenH = configuration.screenHeightDp.dp,
            doublePage = effectiveDouble,
            rtl = rtl,
            vertical = vertical,
        )
        LaunchedEffect(visualRect) {
            onVisualRectChange(visualRect)
        }

        val firstSpreadPages = if (effectiveDouble) {
            spreads.getOrElse(initialPager) { listOf(initial) }
        } else {
            listOf(initial)
        }
        LaunchedEffect(loader, firstSpreadPages, trimMargins, repair) {
            // 首个 spread 涉及的页面都解码完，才允许封面交接
            firstSpreadPages.forEach { loader.load(it, trimMargins, repair) }
            onFirstPageReady()
        }

        val pageBackdrop = rememberLayerBackdrop(onDraw = {
            drawRect(Color.Black)
            drawContent()
        })

        val tempAlpha = (abs(colorTemp) * 0.18f).coerceIn(0f, 0.18f)
        val tempColor = if (colorTemp >= 0f) Color(0xFFFF8A00) else Color(0xFF4A90FF)
        val edgeDrag: (Boolean, Float) -> Unit = { left, dy ->
            if (left) {
                val base = if (brightness < 0f) 0.5f else brightness
                brightness = (base - dy / heightPx).coerceIn(0.05f, 1f)
                hudText = "亮度 ${(brightness * 100).roundToInt()}%"
            } else {
                colorTemp = (colorTemp - dy / heightPx * 1.6f).coerceIn(-1f, 1f)
                hudText = when {
                    abs(colorTemp) < 0.05f -> "色温 中性"
                    colorTemp > 0f -> "色温 暖 ${(colorTemp * 100).roundToInt()}%"
                    else -> "色温 冷 ${(-colorTemp * 100).roundToInt()}%"
                }
            }
        }

        val onLongPressPanel: () -> Unit = { quickPanel = true }
        val onSharePage: () -> Unit = {
            if (!sharing) {
                sharing = true
                scope.launch {
                    val bitmap = loader.load(currentPage, trimMargins, repair)
                    val ok = ReaderShare.sharePage(
                        context = context,
                        bitmap = bitmap,
                        bookTitle = book.title,
                        pageNumber = currentPage + 1,
                    )
                    sharing = false
                    hudText = if (ok) "已交给系统分享" else "分享失败"
                }
            }
        }

        LaunchedEffect(brightness) {
            if (brightness >= 0f) {
                activity?.window?.let { window ->
                    val params = window.attributes
                    params.screenBrightness = brightness.coerceIn(0.05f, 1f)
                    window.attributes = params
                }
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                activity?.window?.let { window ->
                    val params = window.attributes
                    params.screenBrightness = -1f
                    window.attributes = params
                }
            }
        }
        LaunchedEffect(hudText) {
            val text = hudText
            if (text != null) {
                hudHeld = text
                delay(900)
                hudText = null
            }
        }

        LaunchedEffect(savingPage) {
            repository.saveProgress(
                book.id,
                savingPage,
                (savingPage + 1.0) / count,
            )
            if (savingPage != statsLastPage) {
                val pagesDelta = abs(savingPage - statsLastPage).coerceAtLeast(1)
                statsLastPage = savingPage
                repository.addReadingPages(readingSessionId, pagesDelta)
            }
            val prefetch = if (!vertical && effectiveDouble) {
                val spreadIndex = spreadIndexFor(savingPage, spreads)
                ((spreadIndex - 1)..(spreadIndex + 1))
                    .filter { it in spreads.indices }
                    .flatMap { spreads[it] }
            } else {
                listOf(savingPage, savingPage + 1, savingPage + 2, savingPage - 1, savingPage - 2)
            }
            loader.prefetch(prefetch, repair)
        }

        // 面板在场（含退场动画）期间不跑自动隐藏计时，否则底栏刚淡回来又自己藏
        LaunchedEffect(chromeVisible, readerOverlays.present) {
            if (chromeVisible && !readerOverlays.present) {
                delay(3000)
                chromeVisible = false
            }
        }

        LaunchedEffect(vertical, effectiveDouble, splitSpreads, panelingEnabled) {
            val page = if (vertical) {
                pageForFrame(listState.firstVisibleItemIndex)
            } else {
                pageForFrame(pagerState.currentPage)
            }
            if (vertical) {
                val target = if (panelingEnabled) frameStartForPage(page) else page
                listState.scrollToItem(target.coerceIn(0, (framePagerCount - 1).coerceAtLeast(0)))
            } else {
                val target = if (effectiveDouble) {
                    spreadIndexFor(page, spreads)
                } else if (panelingEnabled) {
                    frameStartForPage(page)
                } else {
                    page
                }
                pagerState.scrollToPage(target.coerceIn(0, pagerCount - 1))
            }
        }

        // 宽页补插会改变 spread 序号：按当前 spread 的真实首页重新锚定，防翻页跳位
        LaunchedEffect(spreads, effectiveDouble) {
            if (!vertical && effectiveDouble) {
                val container = pagerState.currentPage.coerceIn(0, spreads.lastIndex)
                val real = spreads.getOrNull(container)?.firstOrNull() ?: return@LaunchedEffect
                pagerState.scrollToPage(spreadIndexFor(real, spreads).coerceIn(0, spreads.lastIndex))
            }
        }

        // 配置档只承载「主题 + 方向」（白天/夜间/条漫），与布局（双页/跨页）正交：
        // 双页由全局双页模式 + 横竖屏派生，若抢占标签会导致「进得去出不来」的循环死锁
        // （横屏双页下 Day 的写动作集无法改变 effectiveDouble，标签恒为双页）。
        val currentProfile = when {
            vertical -> ReaderProfile.Webtoon
            brightness in 0.01f..0.55f || colorTemp < -0.25f -> ReaderProfile.Night
            else -> ReaderProfile.Day
        }

        // B2 快翻累加器：连点同向时每击精确 ±1 页——新击到来先把上一击未完成的翻页
        // scrollToPage 瞬移补完，再对本击播一页短动画（FLIP_SPEC），停手即落位无漂移。
        // 反向点击丢弃累加器，按方向回退 max/min(currentPage, targetPage)。
        var flipTarget by remember { mutableIntStateOf(-1) }
        var flipForward by remember { mutableStateOf(true) }
        // 翻页完全静止（动画落定 / 用户拖拽结束 / 跳页完成）后清零累加器
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.isScrollInProgress }
                .filter { !it }
                .collect { flipTarget = -1 }
        }
        // 用户拖拽起手即丢弃累加目标：拖拽已把页面带到别处，旧目标不可信
        LaunchedEffect(pagerState) {
            pagerState.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) flipTarget = -1
            }
        }
        // 切换翻页动画档时丢弃未完成累加，防旧档半截状态带进新档
        LaunchedEffect(pageTransitionMode) {
            flipTarget = -1
        }

        // 竖读恒瞬时（长条漫平滑滚动易晕）；无动画档保留 scrollToPage 与旧体验一致。
        val goNext: () -> Unit = {
            scope.launch {
                if (vertical) {
                    listState.scrollToItem(
                        (listState.firstVisibleItemIndex + 1).coerceAtMost(count - 1),
                    )
                } else {
                    val trusted = flipTarget >= 0 && flipForward
                    val base = (if (trusted) {
                        flipTarget
                    } else {
                        maxOf(pagerState.currentPage, pagerState.targetPage)
                    }).coerceIn(0, pagerCount - 1)
                    val target = (base + 1).coerceIn(0, pagerCount - 1)
                    flipTarget = target
                    flipForward = true
                    if (pageTransitionMode == SettingsKeys.PAGE_TRANSITION_INSTANT) {
                        pagerState.scrollToPage(target)
                        flipTarget = -1
                    } else {
                        // 上一击未完成的翻页瞬移补完，再对本击做一页短动画
                        if (pagerState.currentPage != base ||
                            pagerState.currentPageOffsetFraction != 0f
                        ) {
                            pagerState.scrollToPage(base)
                        }
                        pagerState.animateScrollToPage(target, animationSpec = FLIP_SPEC)
                    }
                }
            }
        }
        val goPrev: () -> Unit = {
            scope.launch {
                if (vertical) {
                    listState.scrollToItem(
                        (listState.firstVisibleItemIndex - 1).coerceAtLeast(0),
                    )
                } else {
                    val trusted = flipTarget >= 0 && !flipForward
                    val base = (if (trusted) {
                        flipTarget
                    } else {
                        minOf(pagerState.currentPage, pagerState.targetPage)
                    }).coerceIn(0, pagerCount - 1)
                    val target = (base - 1).coerceIn(0, pagerCount - 1)
                    flipTarget = target
                    flipForward = false
                    if (pageTransitionMode == SettingsKeys.PAGE_TRANSITION_INSTANT) {
                        pagerState.scrollToPage(target)
                        flipTarget = -1
                    } else {
                        if (pagerState.currentPage != base ||
                            pagerState.currentPageOffsetFraction != 0f
                        ) {
                            pagerState.scrollToPage(base)
                        }
                        pagerState.animateScrollToPage(target, animationSpec = FLIP_SPEC)
                    }
                }
            }
        }

        // 父级点击层回调中转：pointerInput 协程持有的是首组 lambda，快捷面板就地切
        // 翻页动画档后 goNext/goPrev 捕获的 pageTransitionMode 会变，不经此中转会用旧档位
        val currentTapFlip: (Boolean) -> Unit by rememberUpdatedState { forward ->
            if (forward) goNext() else goPrev()
        }
        val currentToggleChrome: () -> Unit by rememberUpdatedState {
            chromeVisible = !chromeVisible
        }
        // 覆盖档滑动翻页门条件：仅「覆盖」动画档启用（instant/slide 档保留原生拖拽）。
        // rememberUpdatedState 中转：快捷面板就地切翻页动画档后手势循环读到的恒是新档位
        // （pointerInput 协程持有首组 lambda，页面动画档位不能直接捕获）。
        val coverSwipeActive: Boolean by rememberUpdatedState(
            pageTransitionMode == SettingsKeys.PAGE_TRANSITION_COVER,
        )

        BackHandler {
            when {
                // 一镜到底打开中：返回立即反向打断，不先收 chrome/面板
                openActive -> onBack()
                thumbnailsOpen -> thumbnailsOpen = false
                quickPanel -> quickPanel = false
                // 面板正在退场：吞掉这次返回，避免把 chrome 一起收掉
                readerOverlays.present -> Unit
                chromeVisible -> chromeVisible = false
                else -> onBack()
            }
        }

        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        gestures.volumeKeys &&
                        (event.key == Key.VolumeUp || event.key == Key.VolumeDown)
                    ) {
                        if (event.key == Key.VolumeUp) goNext() else goPrev()
                        true
                    } else {
                        false
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(pageBackdrop)
                    // 屏幕级翻页点击判定：挂在本容器（Pager 的父节点）上，而不是独立的全屏
                    // 兄弟节点——兄弟会截断子层事件流（边缘亮度/色温、拖拽翻页、长按、捏合
                    // 全部失效）。父子穿透由手势协议保证（Main pass 子先父后）：子层手势一旦
                    // 消费（拖拽/边缘拖动/长按/捏合），本判定立即让位；无消费的按下-抬起才走
                    // 分区点击。分区用容器（屏幕）坐标，翻页动画中页面在指下移动也不会判错方向。
                    .pointerInput(
                        gestures.master,
                        gestures.tapPaging,
                        gestures.doubleTapZoom,
                        rtl,
                    ) {
                        if (!gestures.master || !gestures.tapPaging) {
                            return@pointerInput
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // 显式等待「无消费的完整抬起」：先查消费再查全抬，子层手势
                            // （边缘拖动等）消费过的 up 不会误判成点击
                            var up: PointerInputChange? = null
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                if (event.changes.any { it.isConsumed }) break
                                if (event.changes.all { !it.pressed }) {
                                    up = event.changes.firstOrNull { it.id == down.id }
                                    break
                                }
                            }
                            if (up == null) return@awaitEachGesture

                            val width = size.width.toFloat()
                            val x = up.position.x
                            val nextZone = if (rtl) x < width * 0.32f else x > width * 0.68f
                            val prevZone = if (rtl) x > width * 0.68f else x < width * 0.32f
                            when {
                                nextZone -> currentTapFlip(true)
                                prevZone -> currentTapFlip(false)
                                !gestures.doubleTapZoom -> currentToggleChrome()
                                else -> {
                                    // 中区：等双击缩放窗口，第二击交给页面缩放手势，超时才切
                                    // chrome。挂起等待只阻塞本判定迭代，子层手势不受影响。
                                    val secondDown =
                                        withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                            awaitFirstDown(requireUnconsumed = false)
                                        }
                                    if (secondDown == null) currentToggleChrome()
                                }
                            }
                        }
                    }
                    .pointerInput(
                        gestures.master,
                        gestures.tapPaging,
                        rtl,
                        vertical,
                    ) {
                        // 覆盖档滑动翻页：横向滑动超过阈值触发一次与单击相同的翻页动画
                        // （不跟手，触发前页面不移动）。与上面的点击判定并列成独立手势：
                        // 本层 consume 后，点击判定的「无消费完整抬起」检查立即让位，不会
                        // 误判成点击翻页。门条件（覆盖档）实时读 coverSwipeActive；方向
                        // 语义与 reverseLayout=rtl 的原生平移一致：LTR 向左滑=下一页、
                        // RTL 向右滑=下一页（与「rtl 左缘点击=下一页」同侧）。放大态平移/
                        // 长按放大镜/边缘亮度色温/捏合均为子层手势先消费，本层看到
                        // consumed 即放弃，不与它们竞争。
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!coverSwipeActive) return@awaitEachGesture
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                if (event.changes.any { it.isConsumed }) return@awaitEachGesture
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@awaitEachGesture
                                if (!change.pressed) return@awaitEachGesture
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (abs(dx) > 48.dp.toPx() && abs(dx) > abs(dy) * 1.5f) {
                                    event.changes.forEach { it.consume() }
                                    currentTapFlip(if (rtl) dx > 0f else dx < 0f)
                                    return@awaitEachGesture
                                }
                            }
                        }
                    },
            ) {
                if (vertical) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // R6 技术验证：负 spacing 让相邻条漫页产生 8~24px 重叠。
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(framePagerCount) { index ->
                            val framePage = pageForFrame(index)
                            val frameLayout = panelLayouts[framePage]
                                ?.takeIf { it.status == PanelDetector.LayoutStatus.Panelized }
                            val frameCount = panelFrameCounts.getOrElse(framePage) { 1 }
                            val frameIndexInPage = index - frameStartForPage(framePage)
                            val panelFrame = if (
                                panelingEnabled &&
                                frameLayout != null &&
                                frameCount > 1 &&
                                frameIndexInPage in frameLayout.frames.indices
                            ) {
                                frameLayout.frames.getOrNull(frameIndexInPage)
                            } else {
                                null
                            }

                            if (panelFrame != null) {
                                ReaderPanelFrame(
                                    pageIndex = framePage,
                                    frameIndex = frameIndexInPage,
                                    frameCount = frameCount,
                                    frame = panelFrame,
                                    loader = loader,
                                    repair = repair,
                                    trimMargins = trimMargins,
                                    gestures = gestures,
                                    onLongPressPanel = onLongPressPanel,
                                    onEdgeDrag = edgeDrag,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth(),
                                    showFrameBadge = false,
                                )
                            } else {
                                ReaderPage(
                                    index = framePage,
                                    loader = loader,
                                    repair = repair,
                                    trimMargins = trimMargins,
                                    gestures = gestures,
                                    onLongPressPanel = onLongPressPanel,
                                    contentScale = ContentScale.FillWidth,
                                    onAspect = { recordAspect(framePage, it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(
                                            y = if (
                                                !panelingEnabled &&
                                                framePage > 0 &&
                                                pageOverlap > 0
                                            ) {
                                                (-pageOverlap).dp
                                            } else {
                                                0.dp
                                            },
                                        ),
                                    onEdgeDrag = edgeDrag,
                                )
                            }
                        }
                    }
                } else {
                    val splitMode = useDouble && splitSpreads
                    val coverEnabled = pageTransitionMode == SettingsKeys.PAGE_TRANSITION_COVER
                    // 覆盖档共享派生：当前动画的「进入页」索引（静止 = -1）。derivedStateOf 让
                    // 动画帧内只有 currentPage 翻转瞬间才触发 placement 更新，动画期间零重组。
                    val coverEnteringPage by remember {
                        derivedStateOf {
                            val fraction = pagerState.currentPageOffsetFraction
                            if (fraction == 0f) {
                                -1
                            } else {
                                val current = pagerState.currentPage
                                val target = pagerState.targetPage
                                val forward = target > current ||
                                    (target == current && fraction < 0f)
                                val position = current + fraction
                                val leaving = (if (forward) floor(position) else ceil(position))
                                    .toInt()
                                if (forward) leaving + 1 else leaving - 1
                            }
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        reverseLayout = rtl,
                        beyondViewportPageCount = 2,
                        // 覆盖档关闭原生拖拽平移：滑动=触发一次翻页动画（不跟手），由根层
                        // 滑动手势接管；instant/slide 档拖拽不变。放大态平移由 ReaderPage
                        // 内部手势处理，不依赖 Pager 滚动，不受此开关影响。
                        userScrollEnabled = !coverEnabled,
                        modifier = Modifier.fillMaxSize(),
                    ) { index ->
                        val pageBody: @Composable () -> Unit = {
                        if (!panelingEnabled && splitMode) {
                            ReaderSplitSpread(
                                index = index,
                                loader = loader,
                                repair = repair,
                                trimMargins = trimMargins,
                                gestures = gestures,
                                onLongPressPanel = onLongPressPanel,
                                rtl = rtl,
                                onEdgeDrag = edgeDrag,
                            )
                        } else if (!panelingEnabled && effectiveDouble) {
                            val pages = spreads.getOrElse(index) { listOf(index) }
                            ReaderSpread(
                                pages = pages,
                                loader = loader,
                                repair = repair,
                                trimMargins = trimMargins,
                                gestures = gestures,
                                onLongPressPanel = onLongPressPanel,
                                rtl = rtl,
                                widePages = widePages,
                                onAspect = { page, ratio -> recordAspect(page, ratio) },
                                onEdgeDrag = edgeDrag,
                            )
                        } else {
                            val framePage = pageForFrame(index)
                            val frameLayout = panelLayouts[framePage]
                                ?.takeIf { it.status == PanelDetector.LayoutStatus.Panelized }
                            val frameCount = panelFrameCounts.getOrElse(framePage) { 1 }
                            val frameIndexInPage = index - frameStartForPage(framePage)
                            val usePanelFrame = panelingEnabled &&
                                frameLayout != null &&
                                frameCount > 1 &&
                                frameIndexInPage in frameLayout.frames.indices
                            val panelFrame = if (usePanelFrame) {
                                frameLayout.frames.getOrNull(frameIndexInPage)
                            } else {
                                null
                            }

                            if (panelFrame != null) {
                                ReaderPanelFrame(
                                    pageIndex = framePage,
                                    frameIndex = frameIndexInPage,
                                    frameCount = frameCount,
                                    frame = panelFrame,
                                    loader = loader,
                                    repair = repair,
                                    trimMargins = trimMargins,
                                    gestures = gestures,
                                    onLongPressPanel = onLongPressPanel,
                                    onEdgeDrag = edgeDrag,
                                )
                            } else {
                                ReaderPage(
                                    index = framePage,
                                    loader = loader,
                                    repair = repair,
                                    trimMargins = trimMargins,
                                    gestures = gestures,
                                    onLongPressPanel = onLongPressPanel,
                                    contentScale = ContentScale.Fit,
                                    onAspect = { recordAspect(framePage, it) },
                                    modifier = Modifier.fillMaxSize(),
                                    onEdgeDrag = edgeDrag,
                                )
                            }
                        }
                        }
                        if (coverEnabled) {
                            Box(
                                modifier = Modifier
                                    // 进入页提升绘制层级（倒退翻页时进入页 index 更小，
                                    // 必须翻 Z 才能产生「盖入」效果）
                                    .zIndex(if (index == coverEnteringPage) 1f else 0f)
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        // 覆盖档：离开页抵消 pager 位移视觉固定；
                                        // draw 阶段读 pager 状态，零重组。reverseLayout 下补偿符号取反。
                                        val fraction = pagerState.currentPageOffsetFraction
                                        if (fraction != 0f) {
                                            val current = pagerState.currentPage
                                            val target = pagerState.targetPage
                                            val forward = target > current ||
                                                (target == current && fraction < 0f)
                                            val position = current + fraction
                                            val leaving = (if (forward) floor(position) else ceil(position))
                                                .toInt()
                                            if (index == leaving) {
                                                translationX = (if (rtl) -1f else 1f) *
                                                    (position - leaving) * size.width
                                            }
                                        }
                                    }
                                    // 页容器补不透明黑底：页内容（ContentScale.Fit 位图）之外的
                                    // 留白此前透明，动画期间透出被补偿钉住的离开页画面造成叠印。
                                    // 黑底放在 graphicsLayer 内侧，随页位移走；静止时与阅读器根层
                                    // 黑底重合不可见，instant/slide 档走 pageBody() 直渲染不受影响。
                                    .background(Color.Black),
                            ) { pageBody() }
                        } else {
                            pageBody()
                        }
                    }
                }
            }

            if (tempAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(tempColor.copy(alpha = tempAlpha)),
                )
            }

            AnimatedVisibility(
                // 面板在场（含退场动画）时顶栏保持收起，等面板落定再一起回来
                visible = chromeVisible && !readerOverlays.present,
                enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { -it / 4 },
                exit = fadeOut(tween(120, easing = FastOutLinearInEasing)) +
                    slideOutVertically(tween(120)) { -it / 4 },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                ReaderTopBar(
                    title = book.title,
                    onBack = onBack,
                    onShare = onSharePage,
                    onSettings = { quickPanel = true },
                    backdrop = pageBackdrop,
                )
            }

            AnimatedVisibility(
                visible = chromeVisible && !readerOverlays.present,
                enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 4 },
                exit = fadeOut(tween(120, easing = FastOutLinearInEasing)) +
                    slideOutVertically(tween(120)) { it / 4 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ReaderBottomBar(
                    page = currentPage + 1,
                    total = count,
                    frameIndex = currentFrameIndex,
                    frameCount = currentFrameCount,
                    onThumbnails = {
                        quickPanel = false
                        thumbnailsOpen = true
                    },
                    onSeek = { target ->
                        scope.launch {
                            if (vertical) {
                                listState.scrollToItem(target)
                            } else {
                                val targetIndex = if (effectiveDouble) {
                                    spreadIndexFor(target, spreads)
                                } else if (panelingEnabled) {
                                    frameStartForPage(target)
                                } else {
                                    target
                                }
                                pagerState.scrollToPage(targetIndex.coerceIn(0, pagerCount - 1))
                            }
                        }
                    },
                    backdrop = pageBackdrop,
                )
            }

            ReaderQuickPanel(
                visible = quickPanel,
                presence = readerOverlays,
                prefsSolo = prefsSolo,
                vertical = vertical,
                    rtl = rtl,
                    doublePageMode = doublePageMode,
                    pageTransitionMode = pageTransitionMode,
                    trimMargins = trimMargins,
                    splitSpreads = splitSpreads,
                    repair = repair,
                    pageOverlap = pageOverlap,
                    panelMode = panelMode,
                    panelingEnabled = panelingEnabled,
                    panelPrecomputeSummary = panelPrecomputeSummary,
                    onOpenPanelEditor = { panelEditorOpen = true },
                    currentProfile = currentProfile,
                    onToggleMode = { vertical = !vertical },
                    onToggleDirection = { rtl = !rtl },
                    onSetDoublePageMode = { value ->
                        scope.launch {
                            settingsRepository.putString(SettingsKeys.READER_DOUBLE_PAGE, value)
                        }
                    },
                    onSetPageTransition = { value ->
                        scope.launch {
                            settingsRepository.putString(SettingsKeys.READER_PAGE_TRANSITION, value)
                        }
                    },
                    onToggleTrimMargins = { trimMargins = !trimMargins },
                    onToggleSplitSpreads = { splitSpreads = !splitSpreads },
                    onSetRepair = { repair = it },
                    onSetOverlap = { pageOverlap = it },
                    onSetPanelMode = {
                        panelModeTouched = true
                        panelMode = it
                    },
                    onToggleSolo = { enable ->
                        scope.launch {
                            val snapshot = withContext(Dispatchers.IO) {
                                settingsRepository.snapshotPreferences()
                            }
                            settingsRepository.putBoolean(
                                SettingsKeys.readerBookSoloKey(book.id),
                                enable,
                            )
                            if (enable) {
                                // 打开瞬间：沿用本书历史快照（保留此前单独调过的偏好），
                                // 无快照则以当前值（=全局值）为起点，随后落盘成为本书快照。
                                val perBook = settingsRepository.stringOf(
                                    snapshot,
                                    SettingsKeys.readerBookKey(book.id),
                                    "",
                                )
                                decodeBookPrefs(perBook, globalPanelMode)?.let { saved ->
                                    vertical = saved.vertical
                                    rtl = saved.rtl
                                    trimMargins = saved.trimMargins
                                    splitSpreads = saved.splitSpreads
                                    repair = saved.repair
                                    pageOverlap = saved.pageOverlap
                                    panelMode = saved.panelMode
                                    panelModeTouched = saved.panelModeExplicit
                                }
                            }
                            prefsSolo = enable
                        }
                    },
                    onApplyProfile = { profile ->
                        when (profile) {
                            ReaderProfile.Day -> {
                                vertical = false
                                splitSpreads = false
                                brightness = 1f
                                colorTemp = 0f
                            }

                            ReaderProfile.Night -> {
                                vertical = false
                                splitSpreads = false
                                brightness = 0.35f
                                colorTemp = -0.6f
                            }

                            ReaderProfile.Webtoon -> {
                                vertical = true
                                splitSpreads = false
                                brightness = 1f
                                colorTemp = 0f
                            }
                        }
                    },
                    onDismiss = { quickPanel = false },
                    backdrop = pageBackdrop,
                )

            // 分格编辑：面板改为常驻组合（退场动画需要），layout 用保留值
            val editorLayoutNow = panelLayouts[currentPage]
                ?.takeIf { it.status == PanelDetector.LayoutStatus.Panelized && it.frames.size > 1 }
            LaunchedEffect(editorLayoutNow) { editorLayoutNow?.let { editorLayoutHeld = it } }
            LaunchedEffect(panelEditorOpen) {
                if (panelEditorOpen && editorLayoutNow == null) {
                    panelEditorOpen = false
                    hudText = "当前页没有可编辑分格"
                }
            }
            if (panelEditorOpen) {
                BackHandler { panelEditorOpen = false }
            }
            editorLayoutHeld?.let { heldLayout ->
                ReaderPanelEditorSheet(
                    visible = panelEditorOpen,
                    presence = readerOverlays,
                    backdrop = pageBackdrop,
                    loader = loader,
                    pageIndex = currentPage,
                    layout = heldLayout,
                    initialFrameIndex = currentFrameIndex,
                    repair = repair,
                    trimMargins = trimMargins,
                    onDismiss = { panelEditorOpen = false },
                    onSave = { updated ->
                        panelEditorOpen = false
                        panelLayouts[currentPage] = updated
                        scope.launch {
                            val key = panelCacheKey(
                                loader = loader,
                                bookId = book.id,
                                pageIndex = currentPage,
                                trimMargins = trimMargins,
                                repair = repair,
                                panelMode = panelMode,
                                rtl = rtl,
                            )
                            if (key != null) {
                                PanelLayoutCache.save(context, key, updated)
                            }
                            hudText = "手动分格已保存"
                        }
                    },
                )
            }

            AnimatedVisibility(
                visible = hudText != null,
                enter = fadeIn(tween(100)) + scaleIn(tween(120), initialScale = 0.96f),
                exit = fadeOut(tween(80)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                GlassRounded(
                    backdrop = pageBackdrop,
                    tier = GlassTier.Full,
                    radius = 22.dp,
                    blurRadius = GlassDimens.StrongBlur,
                    edge = GlassEdge.Outline,
                ) {
                    Text(
                        // 显示中直接读实时文案（保留变量晚一帧，会闪一下空胶囊）；
                        // 退场时 hudText 已置空，用保留值撑完淡出
                        text = hudText ?: hudHeld,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            val thumbRows = (count + 4) / 5
            val thumbHeight = (90.dp + (150 * thumbRows).dp)
                .coerceAtMost(maxPanelHeight)
            ReaderThumbnailPanel(
                visible = thumbnailsOpen,
                presence = readerOverlays,
                count = count,
                    currentPage = currentPage,
                    loader = loader,
                    panelHeight = thumbHeight,
                    onPick = { page ->
                        thumbnailsOpen = false
                        scope.launch {
                            if (vertical) {
                                listState.scrollToItem(page)
                            } else {
                                val target = when {
                                    effectiveDouble -> spreadIndexFor(page, spreads)
                                    panelingEnabled -> frameStartForPage(page)
                                    else -> page
                                }
                                pagerState.scrollToPage(target.coerceIn(0, pagerCount - 1))
                            }
                        }
                    },
                    onDismiss = { thumbnailsOpen = false },
                    backdrop = pageBackdrop,
                )
        }
    }
}

@Composable
private fun ReaderSpread(
    pages: List<Int>,
    loader: PageLoader,
    repair: Int,
    trimMargins: Boolean,
    gestures: ReaderGestureSettings,
    onLongPressPanel: () -> Unit,
    rtl: Boolean,
    widePages: Set<Int>,
    onAspect: (Int, Float) -> Unit,
    onEdgeDrag: (Boolean, Float) -> Unit,
) {
    val ordered = if (rtl) pages.reversed() else pages
    // 宽页独占时铺满整屏；普通页落单（封面/末尾/邻宽页）保持半宽书页感
    val soloFill = ordered.size == 1 && ordered.first() in widePages
    Row(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        if (ordered.size == 1 && rtl && !soloFill) {
            Spacer(Modifier.weight(1f))
        }
        ordered.forEach { page ->
            ReaderPage(
                index = page,
                loader = loader,
                repair = repair,
                trimMargins = trimMargins,
                gestures = gestures,
                onLongPressPanel = onLongPressPanel,
                tapGestureEnabled = false,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onEdgeDrag = onEdgeDrag,
                onAspect = { ratio -> onAspect(page, ratio) },
            )
        }
        if (ordered.size == 1 && !rtl && !soloFill) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReaderSplitSpread(
    index: Int,
    loader: PageLoader,
    repair: Int,
    trimMargins: Boolean,
    gestures: ReaderGestureSettings,
    onLongPressPanel: () -> Unit,
    rtl: Boolean,
    onEdgeDrag: (Boolean, Float) -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, index, loader, trimMargins, repair) {
        value = loader.load(index, trimMargins, repair)
    }
    val source = bitmap
    if (source == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White.copy(alpha = 0.7f),
                strokeWidth = 2.dp,
            )
        }
        return
    }

    val wide = source.width.toFloat() / source.height > WIDE_PAGE_ASPECT
    if (!wide) {
        ReaderPage(
            index = index,
            loader = loader,
            repair = repair,
            trimMargins = trimMargins,
            gestures = gestures,
            onLongPressPanel = onLongPressPanel,
            bitmapOverride = source,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onEdgeDrag = onEdgeDrag,
        )
        return
    }

    val halves = remember(source) { splitBitmap(source) }
    val ordered = if (rtl) halves.reversed() else halves
    Row(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        ordered.forEach { half ->
            ReaderPage(
                index = index,
                loader = loader,
                repair = 0,
                trimMargins = false,
                gestures = gestures,
                onLongPressPanel = onLongPressPanel,
                tapGestureEnabled = false,
                bitmapOverride = half,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onEdgeDrag = onEdgeDrag,
            )
        }
    }
}

private suspend fun panelCacheKey(
    loader: PageLoader,
    bookId: String,
    pageIndex: Int,
    trimMargins: Boolean,
    repair: Int,
    panelMode: String,
    rtl: Boolean,
): PanelLayoutCache.Key? {
    val pageRef = loader.source.listPages().getOrNull(pageIndex) ?: return null
    val contentFingerprint = runCatching {
        Fingerprint.ofArchive(pageRef.size) { loader.source.openPage(pageRef.index) }
    }.getOrNull() ?: return null
    val pageFingerprint = Fingerprint.ofBytes(
        listOf(
            "page",
            bookId,
            pageIndex.toString(),
            pageRef.name,
            pageRef.size.toString(),
            contentFingerprint,
        ).joinToString("|").toByteArray(),
    )
    return PanelLayoutCache.Key(
        bookId = bookId,
        pageIndex = pageIndex,
        pageFingerprint = pageFingerprint,
        trimMargins = trimMargins,
        repair = repair,
        panelMode = panelMode,
        rtl = rtl,
    )
}

private suspend fun detectPanelLayout(
    context: android.content.Context,
    loader: PageLoader,
    bookId: String,
    pageIndex: Int,
    trimMargins: Boolean,
    repair: Int,
    panelMode: String,
    rtl: Boolean,
    direction: PanelDetector.ReadingDirection,
): PanelDetector.Layout? {
    val cacheKey = panelCacheKey(
        loader = loader,
        bookId = bookId,
        pageIndex = pageIndex,
        trimMargins = trimMargins,
        repair = repair,
        panelMode = panelMode,
        rtl = rtl,
    ) ?: return null
    PanelLayoutCache.load(context, cacheKey)?.let { return it }

    // 整页解码必须离开主线程：本函数被全书分格预检循环逐页调用（M5-D），解码留在
    // 调用方上下文（主线程）会把 UI 每页卡 50-100ms——返回书架的一镜到底动画被堵
    // 即掉帧（M5 回归根因）；withContext 同时是取消检查点，阅读器销毁后循环立即停止。
    val bitmap = withContext(Dispatchers.IO) {
        loader.load(pageIndex, trimMargins, repair)
    }
    val layout = withContext(Dispatchers.Default) {
        PanelDetector.analyze(bitmap, direction)
    }
    PanelLayoutCache.save(context, cacheKey, layout)
    return layout
}

@Composable
private fun ReaderPanelFrame(
    pageIndex: Int,
    frameIndex: Int,
    frameCount: Int,
    frame: PanelDetector.Frame,
    loader: PageLoader,
    repair: Int,
    trimMargins: Boolean,
    gestures: ReaderGestureSettings,
    onLongPressPanel: () -> Unit,
    onEdgeDrag: (Boolean, Float) -> Unit,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier.fillMaxSize(),
    showFrameBadge: Boolean = true,
) {
    val baseBitmap by produceState<Bitmap?>(
        initialValue = null,
        pageIndex,
        loader,
        trimMargins,
        repair,
    ) {
        value = loader.load(pageIndex, trimMargins, repair)
    }
    val croppedBitmap = remember(baseBitmap, frame) {
        baseBitmap?.let { cropPanelFrame(it, frame) }
    }

    Box(modifier = modifier) {
        if (croppedBitmap == null) {
            ReaderPage(
                index = pageIndex,
                loader = loader,
                repair = repair,
                trimMargins = trimMargins,
                gestures = gestures,
                onLongPressPanel = onLongPressPanel,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onEdgeDrag = onEdgeDrag,
            )
        } else {
            ReaderPage(
                index = pageIndex,
                loader = loader,
                repair = repair,
                trimMargins = trimMargins,
                gestures = gestures,
                onLongPressPanel = onLongPressPanel,
                bitmapOverride = croppedBitmap,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onEdgeDrag = onEdgeDrag,
            )
        }
        if (showFrameBadge) {
            Text(
                text = "格 ${frameIndex + 1}/$frameCount",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private fun cropPanelFrame(source: Bitmap, frame: PanelDetector.Frame): Bitmap? {
    if (source.width <= 0 || source.height <= 0) return null
    val left = (frame.left * source.width).roundToInt().coerceIn(0, source.width - 1)
    val top = (frame.top * source.height).roundToInt().coerceIn(0, source.height - 1)
    val right = (frame.right * source.width).roundToInt().coerceIn(left + 1, source.width)
    val bottom = (frame.bottom * source.height).roundToInt().coerceIn(top + 1, source.height)
    val width = right - left
    val height = bottom - top
    if (width <= 0 || height <= 0) return null
    if (left == 0 && top == 0 && width == source.width && height == source.height) return source
    return Bitmap.createBitmap(source, left, top, width, height)
}

@Composable
private fun ReaderPage(
    index: Int,
    loader: PageLoader,
    repair: Int,
    trimMargins: Boolean,
    gestures: ReaderGestureSettings,
    onLongPressPanel: () -> Unit,
    tapGestureEnabled: Boolean = true,
    contentScale: ContentScale,
    modifier: Modifier,
    onEdgeDrag: (Boolean, Float) -> Unit,
    bitmapOverride: Bitmap? = null,
    onAspect: ((Float) -> Unit)? = null,
) {
    val baseBitmap by produceState<Bitmap?>(
        initialValue = bitmapOverride,
        index,
        loader,
        trimMargins,
        repair,
        bitmapOverride,
    ) {
        value = bitmapOverride ?: loader.load(index, trimMargins, repair)
    }
    var highResBitmap by remember(index, trimMargins, repair) { mutableStateOf<Bitmap?>(null) }
    var highResRequested by remember(index, trimMargins, repair) { mutableStateOf(false) }
    val bitmap = bitmapOverride ?: highResBitmap ?: baseBitmap
    LaunchedEffect(bitmap) {
        val loaded = bitmap
        if (loaded != null && loaded.height > 0) {
            onAspect?.invoke(loaded.width.toFloat() / loaded.height)
        }
    }
    var scale by remember(index) { mutableFloatStateOf(1f) }
    var offset by remember(index) { mutableStateOf(Offset.Zero) }
    var magnifierVisible by remember(index) { mutableStateOf(false) }
    var magnifierSource by remember(index) { mutableStateOf(Offset.Zero) }
    // 本页在根容器中的位置：条漫长页必须靠它把窗口可视区换算到页面局部坐标
    var pageOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    val hostView = LocalView.current
    // 方向滞回：普通可变字段，只在 draw 阶段读写，避免拖动时逐帧重组
    val magnifierSide = remember(index) { MagnifierSideHolder() }
    val magnifierAlpha by animateFloatAsState(
        targetValue = if (magnifierVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 100),
        label = "magnifierAlpha",
    )

    // R3：实际放大超过 1.5x 时加载原始分辨率页，替换普通解码结果。
    LaunchedEffect(index, loader, trimMargins, repair, bitmapOverride) {
        highResRequested = false
        highResBitmap = null
    }
    LaunchedEffect(scale, index, loader, trimMargins, repair, bitmapOverride) {
        if (scale >= 1.5f && !highResRequested && bitmapOverride == null) {
            highResRequested = true
            highResBitmap = loader.loadHighRes(
                index = index,
                trimMargins = trimMargins,
                repair = repair,
                targetScale = 2f,
            )
        }
        if (scale <= 1.01f) {
            highResBitmap = null
            highResRequested = false
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { pageOriginInRoot = it.positionInRoot() }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .readerEdgeDrag(
                onDrag = onEdgeDrag,
                leftEnabled = gestures.master && gestures.edgeBrightness,
                rightEnabled = gestures.master && gestures.edgeColorTemp,
            )
            .pointerInput(
                index,
                tapGestureEnabled,
                gestures.master,
                gestures.tapPaging,
                gestures.doubleTapZoom,
            ) {
                // 双击缩放只认「两击都落在本页中区」；边缘翻页与中区切 chrome 已上移到
                // Pager 容器的屏幕级点击判定，本手势不再参与翻页。缩放焦点用页面局部
                // 坐标是正确的（放大对准指下的页面位置）。
                if (
                    !tapGestureEnabled ||
                    !gestures.master ||
                    !gestures.tapPaging ||
                    !gestures.doubleTapZoom
                ) {
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation()
                    if (up == null) return@awaitEachGesture

                    val width = size.width.toFloat()
                    if (up.position.x < width * 0.32f || up.position.x > width * 0.68f) {
                        return@awaitEachGesture
                    }

                    val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                        awaitFirstDown(requireUnconsumed = false)
                    }
                    if (secondDown == null) return@awaitEachGesture

                    val secondUp = waitForUpOrCancellation()
                    if (secondUp != null) {
                        val secondX = secondUp.position.x
                        if (secondX < width * 0.32f || secondX > width * 0.68f) {
                            return@awaitEachGesture
                        }
                        val point = secondUp.position
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                            offset = Offset(
                                x = (size.width / 2f - point.x) * 2f,
                                y = (size.height / 2f - point.y) * 2f,
                            )
                        }
                    }
                }
            }
            .pointerInput(index, gestures.master, gestures.longPress) {
                // 长按手势：放大镜 / 快捷面板 / 无。未激活前不消费，Pager 仍可翻页。
                if (!gestures.master || gestures.longPress == ReaderLongPressMode.None) {
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPress = awaitLongPressOrCancellation(down.id)
                    if (longPress == null) return@awaitEachGesture

                    val wantMagnifier = gestures.longPress == ReaderLongPressMode.Magnifier &&
                        scale <= 1.01f
                    if (gestures.longPress == ReaderLongPressMode.Magnifier) {
                        // 已处于缩放态时不叠加放大镜，交还给拖动平移
                        if (!wantMagnifier) return@awaitEachGesture
                        magnifierVisible = true
                        magnifierSource = longPress.position
                    } else {
                        onLongPressPanel()
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (wantMagnifier) magnifierSource = change.position
                        change.consume()
                        if (!change.pressed) break
                    }
                    if (wantMagnifier) magnifierVisible = false
                }
            }
            .pointerInput(index, gestures.master, gestures.pinchZoom) {
                if (!gestures.master || !gestures.pinchZoom) {
                    return@pointerInput
                }
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoomed = scale > 1f
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                zoomed = true
                                event.changes.forEach { it.consume() }
                            }
                        } else if (zoomed && scale > 1f) {
                            val pan = event.calculatePan()
                            if (pan != Offset.Zero) {
                                val maxX = (scale - 1f) * size.width / 2f
                                val maxY = (scale - 1f) * size.height / 2f
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY),
                                )
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale <= 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val page = bitmap
        if (page != null) {
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = "第 ${index + 1} 页",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White.copy(alpha = 0.7f),
                strokeWidth = 2.dp,
            )
        }

        if (magnifierVisible || magnifierAlpha > 0.01f) {
            val page = bitmap
            if (page != null) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = magnifierAlpha },
                ) {
                    // 画布局部坐标下的可视区：窗口尺寸减去本页在根容器的偏移（条漫长页必需）
                    val viewport = Rect(
                        left = -pageOriginInRoot.x,
                        top = -pageOriginInRoot.y,
                        right = hostView.width - pageOriginInRoot.x,
                        bottom = hostView.height - pageOriginInRoot.y,
                    )
                    drawMagnifierLens(
                        bitmap = page.asImageBitmap(),
                        sourcePoint = magnifierSource,
                        viewport = viewport,
                        spec = gestures.magnifier,
                        sideHolder = magnifierSide,
                    )
                }
            }
        }
    }
}

/** 放大镜朝向：横向覆盖一整行对白，竖向覆盖一段竖排文字 / 高瘦分格。 */
private enum class MagnifierOrientation { Landscape, Portrait }

/**
 * 放大镜档位：透镜长边 = 页宽 × ratio（横竖同口径，只是转 90°），
 * 短边 = 长边 × [MAGNIFIER_ASPECT]，镜内可见范围 = 边长 ÷ zoom。
 */
private data class MagnifierSpec(
    val ratio: Float = 0.86f,
    val zoom: Float = 2.0f,
    val orientation: MagnifierOrientation = MagnifierOrientation.Landscape,
)

private enum class MagnifierSide { Above, Below, Left, Right }

/** 方向滞回载体：只在绘制阶段读写，不进入 Snapshot。 */
private class MagnifierSideHolder {
    var side: MagnifierSide = MagnifierSide.Above
}

private const val MAGNIFIER_ASPECT = 0.58f
private const val MAGNIFIER_SIDE_STICKY = 0.70f
private val MAGNIFIER_KEEP_OUT_DP = 40.dp

private data class MagnifierLens(
    val rect: Rect,
    val side: MagnifierSide,
    val scale: Float,
)

/**
 * R5 放大镜（圆角矩形 + 绝不遮挡手指）：
 * - 透镜尺寸锚定当前页绘制矩形，双页 / 分格 / 条漫天然自适应；
 * - 四方向避让，任一侧空间不足时等比缩小透镜，绝不压住手指；
 * - 贴页边做源窗口对齐补偿，镜内内容与手指所指位置始终一致；
 * - 横 / 竖朝向只交换长边方向，短边比例与可见面积不变。
 */
private fun DrawScope.drawMagnifierLens(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    sourcePoint: Offset,
    viewport: Rect,
    spec: MagnifierSpec,
    sideHolder: MagnifierSideHolder,
) {
    if (size.width <= 1f || size.height <= 1f) return
    if (bitmap.width <= 0 || bitmap.height <= 0) return
    val page = magnifierContentRect(size, bitmap)
    if (page.width <= 1f || page.height <= 1f) return

    val margin = 8.dp.toPx()
    val keepOut = MAGNIFIER_KEEP_OUT_DP.toPx()
    val longSide = page.width * spec.ratio
    var lensWidth: Float
    var lensHeight: Float
    if (spec.orientation == MagnifierOrientation.Portrait) {
        // 竖向：长边是高度，只受单侧可容纳高度约束（宽度天然小于页宽）
        lensHeight = longSide.coerceAtMost((viewport.height - keepOut).coerceAtLeast(1f))
        lensWidth = lensHeight * MAGNIFIER_ASPECT
    } else {
        // 横向：长边是宽度，受页宽与视口宽约束
        val maxWidth = minOf(page.width, viewport.width - margin * 2f)
        lensWidth = longSide.coerceAtMost(maxWidth.coerceAtLeast(1f))
        lensHeight = lensWidth * MAGNIFIER_ASPECT
        val maxHeight = viewport.height - keepOut
        if (maxHeight > 1f && lensHeight > maxHeight) {
            lensHeight = maxHeight
            lensWidth = lensHeight / MAGNIFIER_ASPECT
        }
    }
    if (lensWidth <= 1f || lensHeight <= 1f) return

    val lens = computeMagnifierLens(
        source = sourcePoint,
        page = page,
        viewport = viewport,
        desiredWidth = lensWidth,
        desiredHeight = lensHeight,
        keepOut = keepOut,
        stickySide = sideHolder.side,
    )
    sideHolder.side = lens.side
    val rect = lens.rect
    if (rect.width <= 1f || rect.height <= 1f) return

    // 圆角按短边取：横向短边是高度，竖向短边是宽度
    val corner = (minOf(rect.height, rect.width) * 0.16f).coerceIn(12.dp.toPx(), 18.dp.toPx())
    // 屏幕 px → 位图 px（等比缩放，用宽度换算即可）
    val pxPerScreen = bitmap.width / page.width
    val srcWidth = (rect.width / spec.zoom * pxPerScreen).roundToInt().coerceIn(1, bitmap.width)
    val srcHeight = (rect.height / spec.zoom * pxPerScreen).roundToInt().coerceIn(1, bitmap.height)

    val desiredSrcLeft = (sourcePoint.x - page.left) * pxPerScreen - srcWidth / 2f
    val desiredSrcTop = (sourcePoint.y - page.top) * pxPerScreen - srcHeight / 2f
    val srcLeft = desiredSrcLeft.roundToInt()
        .coerceIn(0, (bitmap.width - srcWidth).coerceAtLeast(0))
    val srcTop = desiredSrcTop.roundToInt()
        .coerceIn(0, (bitmap.height - srcHeight).coerceAtLeast(0))
    // 源窗口被夹到图内后，绘制侧反向补偿，保证镜内内容与手指位置严格对齐
    val shiftX = (desiredSrcLeft - srcLeft) * (rect.width / srcWidth)
    val shiftY = (desiredSrcTop - srcTop) * (rect.height / srcHeight)

    val lensPath = Path().apply {
        addRoundRect(RoundRect(rect, CornerRadius(corner)))
    }
    clipPath(lensPath) {
        drawRect(
            color = Color.Black.copy(alpha = 0.86f),
            topLeft = rect.topLeft,
            size = rect.size,
        )
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(srcLeft, srcTop),
            srcSize = IntSize(srcWidth, srcHeight),
            dstOffset = IntOffset(
                (rect.left + shiftX).roundToInt(),
                (rect.top + shiftY).roundToInt(),
            ),
            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
        )
    }

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.28f),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(corner),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.9f),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(corner),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
    )

    val arm = 7.dp.toPx()
    val center = rect.center
    val crossColor = Color.White.copy(alpha = 0.55f)
    drawLine(
        color = crossColor,
        start = Offset(center.x - arm, center.y),
        end = Offset(center.x + arm, center.y),
        strokeWidth = 1.dp.toPx(),
    )
    drawLine(
        color = crossColor,
        start = Offset(center.x, center.y - arm),
        end = Offset(center.x, center.y + arm),
        strokeWidth = 1.dp.toPx(),
    )

    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = 2.dp.toPx(),
        center = sourcePoint,
    )
}

/** 当前内容在画布内的实际绘制矩形（与 Image 的 Fit / FillWidth 一致）。 */
private fun magnifierContentRect(
    size: Size,
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
): Rect {
    val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    if (size.width <= 1f || size.height <= 1f || bitmapAspect <= 0f) return Rect.Zero
    return if (bitmapAspect > size.width / size.height) {
        val height = size.width / bitmapAspect
        val top = (size.height - height) / 2f
        Rect(0f, top, size.width, top + height)
    } else {
        val width = size.height * bitmapAspect
        val left = (size.width - width) / 2f
        Rect(left, 0f, left + width, size.height)
    }
}

/**
 * 四方向避让：每侧算出「保持不遮挡可用的最大透镜」，取缩放比最大的一侧；
 * 当前方向仍够用（含滞回）时不切换，避免拖动时上下弹跳。
 */
private fun computeMagnifierLens(
    source: Offset,
    page: Rect,
    viewport: Rect,
    desiredWidth: Float,
    desiredHeight: Float,
    keepOut: Float,
    stickySide: MagnifierSide,
): MagnifierLens {
    val area = page.intersect(viewport).let {
        if (it.width > 1f && it.height > 1f) it else viewport
    }
    val aspect = desiredWidth / desiredHeight

    fun vertical(side: MagnifierSide): MagnifierLens {
        val available = if (side == MagnifierSide.Above) {
            source.y - keepOut - area.top
        } else {
            area.bottom - source.y - keepOut
        }
        if (available <= 1f) return MagnifierLens(Rect.Zero, side, 0f)
        var height = minOf(desiredHeight, available)
        var width = height * aspect
        if (width > area.width) {
            width = area.width
            height = width / aspect
        }
        if (height > available) {
            height = available
            width = height * aspect
        }
        val anchorY = if (side == MagnifierSide.Above) source.y - keepOut else source.y + keepOut
        val top = if (side == MagnifierSide.Above) anchorY - height else anchorY
        val left = (source.x - width / 2f)
            .coerceIn(area.left, (area.right - width).coerceAtLeast(area.left))
        return MagnifierLens(Rect(left, top, left + width, top + height), side, height / desiredHeight)
    }

    fun horizontal(side: MagnifierSide): MagnifierLens {
        val available = if (side == MagnifierSide.Left) {
            source.x - keepOut - area.left
        } else {
            area.right - source.x - keepOut
        }
        if (available <= 1f) return MagnifierLens(Rect.Zero, side, 0f)
        var width = minOf(desiredWidth, available)
        var height = width / aspect
        if (height > area.height) {
            height = area.height
            width = height * aspect
        }
        if (width > available) {
            width = available
            height = width / aspect
        }
        val anchorX = if (side == MagnifierSide.Left) source.x - keepOut else source.x + keepOut
        val left = if (side == MagnifierSide.Left) anchorX - width else anchorX
        val top = (source.y - height / 2f)
            .coerceIn(area.top, (area.bottom - height).coerceAtLeast(area.top))
        return MagnifierLens(Rect(left, top, left + width, top + height), side, width / desiredWidth)
    }

    val candidates = listOf(
        vertical(MagnifierSide.Above),
        vertical(MagnifierSide.Below),
        horizontal(MagnifierSide.Left),
        horizontal(MagnifierSide.Right),
    )
    val sticky = candidates.firstOrNull { it.side == stickySide && it.scale > 0f }
    val best = candidates.maxByOrNull { it.scale }?.takeIf { it.scale > 0f }
    return when {
        sticky == null -> best ?: MagnifierLens(Rect.Zero, stickySide, 0f)
        best == null -> sticky
        sticky.scale >= 0.98f -> sticky
        sticky.scale >= MAGNIFIER_SIDE_STICKY && best.scale <= sticky.scale + 0.15f -> sticky
        else -> best
    }
}

/** 左右边缘竖向拖动：只在真实屏幕边缘起手且竖向主导时触发，避免大面积误触。 */
private fun Modifier.readerEdgeDrag(
    onDrag: (Boolean, Float) -> Unit,
    leftEnabled: Boolean,
    rightEnabled: Boolean,
): Modifier = pointerInput(leftEnabled, rightEnabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val edgeWidth = 20.dp.toPx()
        val topGuard = 56.dp.toPx()
        val bottomGuard = 64.dp.toPx()

        // 必须从真实屏幕边缘起手；从中间滑到边缘不算。
        val left = leftEnabled && down.position.x <= edgeWidth
        val right = rightEnabled && down.position.x >= width - edgeWidth
        val verticalStart = down.position.y > topGuard &&
            down.position.y < height - bottomGuard
        if (height <= topGuard + bottomGuard || (!left && !right) || !verticalStart) {
            return@awaitEachGesture
        }

        var active = false
        var lastY = down.position.y
        while (true) {
            val event = awaitPointerEvent()

            // 双指出现时立刻让位给捏合缩放。
            if (event.changes.size > 1) {
                event.changes.forEach { change ->
                    if (change.id == down.id && active) change.consume()
                }
                break
            }

            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val total = change.position - down.position
            if (!active &&
                abs(total.y) > viewConfiguration.touchSlop &&
                abs(total.y) > abs(total.x) * 1.5f
            ) {
                active = true
            }
            if (active) {
                val dy = change.position.y - lastY
                if (dy != 0f) onDrag(left, dy)
                change.consume()
            }
            lastY = change.position.y
            if (!change.pressed) break
        }
    }
}

/** 点击翻页动画：短促一页过渡；连点时上一击被瞬移补完，只有最后一击的动画会播完。 */
private val FLIP_SPEC: AnimationSpec<Float> = tween(
    durationMillis = 160,
    easing = FastOutSlowInEasing,
)

private enum class ReaderProfile { Day, Night, Webtoon }

private enum class ReaderLongPressMode { Magnifier, QuickPanel, None }

private data class ReaderGestureSettings(
    val master: Boolean,
    val tapPaging: Boolean,
    val doubleTapZoom: Boolean,
    val pinchZoom: Boolean,
    val longPress: ReaderLongPressMode,
    val edgeBrightness: Boolean,
    val edgeColorTemp: Boolean,
    val volumeKeys: Boolean,
    val magnifier: MagnifierSpec = MagnifierSpec(),
)

private fun buildGestureSettings(
    prefs: androidx.datastore.preferences.core.Preferences?,
    repository: com.inkvol.app.data.settings.SettingsRepository,
): ReaderGestureSettings {
    fun booleanOf(key: String, default: Boolean) =
        prefs?.let { repository.booleanOf(it, key, default) } ?: default

    fun stringOf(key: String, default: String) =
        prefs?.let { repository.stringOf(it, key, default) } ?: default

    val master = booleanOf(SettingsKeys.READER_GESTURES_MASTER, true)
    val longPress = when (stringOf(SettingsKeys.READER_LONG_PRESS, SettingsKeys.LONG_PRESS_MAGNIFIER)) {
        SettingsKeys.LONG_PRESS_QUICK_PANEL -> ReaderLongPressMode.QuickPanel
        SettingsKeys.LONG_PRESS_NONE -> ReaderLongPressMode.None
        else -> ReaderLongPressMode.Magnifier
    }
    val magnifier = MagnifierSpec(
        ratio = when (stringOf(SettingsKeys.READER_MAGNIFIER_SIZE, SettingsKeys.MAGNIFIER_STANDARD)) {
            SettingsKeys.MAGNIFIER_COMPACT -> 0.72f
            SettingsKeys.MAGNIFIER_WIDE -> 0.95f
            else -> 0.86f
        },
        zoom = when (stringOf(SettingsKeys.READER_MAGNIFIER_ZOOM, "2.0")) {
            "1.8" -> 1.8f
            "2.5" -> 2.5f
            else -> 2.0f
        },
        orientation = when (stringOf(SettingsKeys.READER_MAGNIFIER_ORIENTATION, SettingsKeys.MAGNIFIER_LANDSCAPE)) {
            SettingsKeys.MAGNIFIER_PORTRAIT -> MagnifierOrientation.Portrait
            else -> MagnifierOrientation.Landscape
        },
    )
    return ReaderGestureSettings(
        master = master,
        tapPaging = master && booleanOf(SettingsKeys.READER_TAP_PAGING, true),
        doubleTapZoom = master && booleanOf(SettingsKeys.READER_DOUBLE_TAP_ZOOM, true),
        pinchZoom = master && booleanOf(SettingsKeys.READER_PINCH_ZOOM, true),
        longPress = if (master) longPress else ReaderLongPressMode.None,
        edgeBrightness = master && booleanOf(SettingsKeys.READER_EDGE_BRIGHTNESS, true),
        edgeColorTemp = master && booleanOf(SettingsKeys.READER_EDGE_COLOR_TEMP, true),
        volumeKeys = master && booleanOf(SettingsKeys.READER_VOLUME_KEYS, true),
        magnifier = magnifier,
    )
}

private fun splitBitmap(source: Bitmap): List<Bitmap> {
    val halfWidth = source.width / 2
    if (halfWidth <= 0) return listOf(source)
    return listOf(
        Bitmap.createBitmap(source, 0, 0, halfWidth, source.height),
        Bitmap.createBitmap(source, halfWidth, 0, source.width - halfWidth, source.height),
    )
}

/** 宽页判定阈值：宽高比超过该值的横图在双页下独占一个 spread（与跨页切割判断同源）。 */
private const val WIDE_PAGE_ASPECT = 1.15f

/**
 * spread 配对表：封面单独一页 → 顺序扫描，宽页（横图）独占、普通页两两配对；
 * 普通页与宽页相邻时普通页落单、宽页独占，不硬凑（SSMH 同款）。
 * 宽页宽高比在解码后才可知，表随回填动态补插，真实页号仍是唯一真源。
 */
private fun buildSpreads(count: Int, doublePage: Boolean, widePages: Set<Int> = emptySet()): List<List<Int>> {
    if (!doublePage || count <= 1) return (0 until count).map { listOf(it) }
    val spreads = ArrayList<List<Int>>()
    spreads += listOf(0)
    var index = 1
    while (index < count) {
        val next = index + 1
        if (index !in widePages && next < count && next !in widePages) {
            spreads += listOf(index, next)
            index += 2
        } else {
            spreads += listOf(index)
            index += 1
        }
    }
    return spreads
}

private data class DpRect(
    val left: Dp,
    val top: Dp,
    val width: Dp,
    val height: Dp,
)

/**
 * 封面共享元素的当前目标矩形：
 * - 单页横屏：首页 Fit 居中矩形；
 * - 双页横屏：首个 spread 的实际可视矩形（RTL 封面在右半屏）；
 * - 纵向滚动：整屏。
 * 打开与返回共用同一真源，避免单页/双页同框错位。
 */
private fun readerCoverRect(
    aspect: Float?,
    screenW: Dp,
    screenH: Dp,
    doublePage: Boolean,
    rtl: Boolean,
    vertical: Boolean,
): DpRect {
    if (aspect == null || vertical) return DpRect(0.dp, 0.dp, screenW, screenH)
    val pageW = minOf(screenW, screenH * aspect)
    val pageH = pageW / aspect
    if (!doublePage) {
        return DpRect(
            left = (screenW - pageW) / 2,
            top = (screenH - pageH) / 2,
            width = pageW,
            height = pageH,
        )
    }
    val spreadAspect = aspect * 2f
    val spreadW = minOf(screenW, screenH * spreadAspect)
    val spreadH = spreadW / spreadAspect
    val spreadLeft = (screenW - spreadW) / 2
    val halfW = spreadW / 2
    val pageLeft = if (rtl) spreadLeft + halfW else spreadLeft
    return DpRect(
        left = pageLeft,
        top = (screenH - spreadH) / 2,
        width = halfW,
        height = spreadH,
    )
}

private fun spreadIndexFor(page: Int, spreads: List<List<Int>>): Int =
    spreads.indexOfFirst { page in it }.coerceAtLeast(0)

@Composable
private fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSettings: () -> Unit,
    backdrop: Backdrop,
) {
    GlassRounded(
        backdrop = backdrop,
        tier = GlassTier.Full,
        radius = 28.dp,
        blurRadius = GlassDimens.StrongBlur,
        edge = GlassEdge.Outline,
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 12.dp, start = 12.dp, end = 12.dp)
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "分享页面",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "阅读设置",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    page: Int,
    total: Int,
    frameIndex: Int,
    frameCount: Int,
    onThumbnails: () -> Unit,
    onSeek: (Int) -> Unit,
    backdrop: Backdrop,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPage by remember { mutableIntStateOf(page - 1) }
    LaunchedEffect(page) {
        if (!scrubbing) scrubPage = (page - 1).coerceIn(0, total - 1)
    }
    val shownPage = if (scrubbing) scrubPage else (page - 1)
    val fraction = ((shownPage + 1).toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)

    GlassRounded(
        backdrop = backdrop,
        tier = GlassTier.Full,
        radius = 28.dp,
        blurRadius = GlassDimens.StrongBlur,
        edge = GlassEdge.Outline,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (frameCount > 1) {
                    "P${shownPage + 1} · 格 ${frameIndex + 1}/$frameCount"
                } else {
                    "${shownPage + 1} / $total"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .height(28.dp)
                    .pointerInput(total) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            scrubbing = true
                            fun update(position: Offset) {
                                val bounded = position.x.coerceIn(0f, size.width.toFloat())
                                scrubPage = ((bounded / size.width) * total)
                                    .toInt()
                                    .coerceIn(0, total - 1)
                            }
                            update(down.position)
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                update(change.position)
                                change.consume()
                            } while (change.pressed)
                            scrubbing = false
                            onSeek(scrubPage)
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                )
                val thumbWidth = 14.dp
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = with(androidx.compose.ui.platform.LocalDensity.current) {
                            ((maxWidth - thumbWidth) * fraction).coerceAtLeast(0.dp)
                        })
                        .size(thumbWidth)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            IconButton(onClick = onThumbnails) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = "缩略图",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ReaderThumbnailPanel(
    visible: Boolean,
    presence: GlassOverlayPresence,
    count: Int,
    currentPage: Int,
    loader: PageLoader,
    panelHeight: androidx.compose.ui.unit.Dp,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(currentPage) {
        if (currentPage in 0 until count) gridState.scrollToItem(currentPage)
    }

    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.ReaderPanel,
        onDismiss = onDismiss,
        backdrop = backdrop,
        tint = GlassDefaults.readerPanelTint(),
        presence = presence,
        alignment = Alignment.BottomCenter,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .height(panelHeight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "缩略图",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "关闭",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                state = gridState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(count) { index ->
                    ThumbCell(
                        index = index,
                        selected = index == currentPage,
                        loader = loader,
                        onClick = { onPick(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbCell(
    index: Int,
    selected: Boolean,
    loader: PageLoader,
    onClick: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, index, loader) {
        value = loader.loadThumb(index)
    }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    ) {
        bitmap?.let { page ->
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = "第 ${index + 1} 页",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun ReaderQuickPanel(
    visible: Boolean,
    presence: GlassOverlayPresence,
    prefsSolo: Boolean,
    vertical: Boolean,
    rtl: Boolean,
    doublePageMode: String,
    pageTransitionMode: String,
    trimMargins: Boolean,
    splitSpreads: Boolean,
    repair: Int,
    pageOverlap: Int,
    panelMode: String,
    panelingEnabled: Boolean,
    panelPrecomputeSummary: String,
    onOpenPanelEditor: () -> Unit,
    currentProfile: ReaderProfile,
    onToggleMode: () -> Unit,
    onToggleDirection: () -> Unit,
    onSetDoublePageMode: (String) -> Unit,
    onSetPageTransition: (String) -> Unit,
    onToggleTrimMargins: () -> Unit,
    onToggleSplitSpreads: () -> Unit,
    onSetRepair: (Int) -> Unit,
    onSetOverlap: (Int) -> Unit,
    onSetPanelMode: (String) -> Unit,
    onToggleSolo: (Boolean) -> Unit,
    onApplyProfile: (ReaderProfile) -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
) {
    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.ReaderPanel,
        onDismiss = onDismiss,
        backdrop = backdrop,
        tint = GlassDefaults.readerPanelTint(),
        presence = presence,
        alignment = Alignment.BottomCenter,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
    ) {
            // 展开中的选项行 id（互斥单开）：面板关闭重开自动重置，无需跨开关记忆
            var expandedRowId by remember { mutableStateOf<String?>(null) }
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "阅读设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(14.dp))
                PanelChoiceRow(
                    title = "配置档",
                    options = listOf(
                        ReaderProfile.Day.name to "白天",
                        ReaderProfile.Night.name to "夜间",
                        ReaderProfile.Webtoon.name to "条漫",
                    ),
                    selectedId = currentProfile.name,
                    expanded = expandedRowId == "profile",
                    onToggleExpand = {
                        expandedRowId = if (expandedRowId == "profile") null else "profile"
                    },
                    onSelect = { id ->
                        expandedRowId = null
                        onApplyProfile(ReaderProfile.valueOf(id))
                    },
                )
                Spacer(Modifier.height(8.dp))
                PanelRow("阅读模式", if (vertical) "纵向滚动" else "左右分页", onToggleMode)
                Spacer(Modifier.height(8.dp))
                PanelRow("阅读方向", if (rtl) "右到左" else "左到右", onToggleDirection)
                Spacer(Modifier.height(8.dp))
                PanelChoiceRow(
                    title = "双页模式",
                    options = listOf(
                        SettingsKeys.DOUBLE_PAGE_AUTO to "宽屏自动",
                        SettingsKeys.DOUBLE_PAGE_ALWAYS to "始终",
                        SettingsKeys.DOUBLE_PAGE_OFF to "关",
                    ),
                    selectedId = doublePageMode,
                    expanded = expandedRowId == "double_page",
                    onToggleExpand = {
                        expandedRowId = if (expandedRowId == "double_page") null else "double_page"
                    },
                    onSelect = { id ->
                        expandedRowId = null
                        onSetDoublePageMode(id)
                    },
                )
                Spacer(Modifier.height(8.dp))
                PanelChoiceRow(
                    title = "翻页动画",
                    options = listOf(
                        SettingsKeys.PAGE_TRANSITION_INSTANT to "无动画",
                        SettingsKeys.PAGE_TRANSITION_SLIDE to "平移",
                        SettingsKeys.PAGE_TRANSITION_COVER to "覆盖",
                    ),
                    selectedId = pageTransitionMode,
                    expanded = expandedRowId == "page_transition",
                    onToggleExpand = {
                        expandedRowId =
                            if (expandedRowId == "page_transition") null else "page_transition"
                    },
                    onSelect = { id ->
                        expandedRowId = null
                        onSetPageTransition(id)
                    },
                )
                Spacer(Modifier.height(8.dp))
                PanelRow("页面裁边", if (trimMargins) "开" else "关", onToggleTrimMargins)
                Spacer(Modifier.height(8.dp))
                PanelRow("跨页切割", if (splitSpreads) "开" else "关", onToggleSplitSpreads)
                Spacer(Modifier.height(8.dp))
                PanelChoiceRow(
                    title = "扫描件修复",
                    options = listOf("0" to "关", "1" to "轻", "2" to "强"),
                    selectedId = repair.toString(),
                    expanded = expandedRowId == "repair",
                    onToggleExpand = {
                        expandedRowId = if (expandedRowId == "repair") null else "repair"
                    },
                    onSelect = { id ->
                        expandedRowId = null
                        onSetRepair(id.toInt())
                    },
                )
                Spacer(Modifier.height(8.dp))
                PanelChoiceRow(
                    title = "页面重叠",
                    options = listOf("0" to "关", "8" to "8px", "16" to "16px", "24" to "24px"),
                    selectedId = pageOverlap.toString(),
                    expanded = expandedRowId == "overlap",
                    onToggleExpand = {
                        expandedRowId = if (expandedRowId == "overlap") null else "overlap"
                    },
                    onSelect = { id ->
                        expandedRowId = null
                        onSetOverlap(id.toInt())
                    },
                )
                Spacer(Modifier.height(8.dp))
                PanelChoiceRow(
                    title = "智能分格",
                    options = listOf(
                        SettingsKeys.PANEL_OFF to "关",
                        SettingsKeys.PANEL_AUTO to "自动",
                        SettingsKeys.PANEL_ON to "始终开启",
                    ),
                    selectedId = panelMode,
                    expanded = expandedRowId == "panel_mode",
                    onToggleExpand = {
                        expandedRowId = if (expandedRowId == "panel_mode") null else "panel_mode"
                    },
                    onSelect = { id ->
                        expandedRowId = null
                        onSetPanelMode(id)
                    },
                )
                if (panelingEnabled) {
                    Spacer(Modifier.height(8.dp))
                    PanelRow("手动分格编辑", "打开", onOpenPanelEditor)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "全书预检：$panelPrecomputeSummary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                // 单独记忆：开 = 本书排版独立保存（改动只影响本书）；
                // 关（默认）= 跟随全局默认，面板改动对所有书一起生效。整行单击切换。
                PanelRow(
                    "单独记忆",
                    if (prefsSolo) "开启" else "关闭",
                ) { onToggleSolo(!prefsSolo) }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "亮度：左缘上下滑 · 色温：右缘上下滑 · 设置/分享在顶栏 · 缩略图在底栏",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
}

@Composable
private fun PanelRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 三态及以上设置行：点击行内展开选项列表直接点选，选中即收起。
 * 与 2 态循环行（PanelRow）并存：两态翻面零成本不展开；展开互斥由面板层
 * expandedRowId 保证。选项子行 clickable 会消费点击，不会误触发展开切换。
 */
@Composable
private fun PanelChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val currentLabel = options.firstOrNull { it.first == selectedId }?.second.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(160))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .clickable(onClick = onToggleExpand),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (expanded) "▴" else "▾",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            options.forEach { (id, label) ->
                val selected = id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onSelect(id) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReaderLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassRounded(
            backdrop = null,
            tier = GlassTier.Lite,
            radius = 16.dp,
            blurRadius = GlassDimens.LightBlur,
            edge = GlassEdge.Outline,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                Text("正在打开…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ReaderMessage(title: String, message: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassRounded(
            backdrop = null,
            tier = GlassTier.Lite,
            radius = 16.dp,
            blurRadius = GlassDimens.LightBlur,
            edge = GlassEdge.Outline,
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 340.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(message, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "返回书架",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onBack)
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
        }
    }
}
