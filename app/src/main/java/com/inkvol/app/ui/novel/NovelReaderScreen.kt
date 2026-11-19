package com.inkvol.app.ui.novel

import android.app.Activity
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.inkvol.app.data.model.AnnotationRecord
import com.inkvol.app.data.model.BookmarkRecord
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.model.NovelChapterRecord
import com.inkvol.app.data.model.NovelReadingPrefs
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.data.settings.SettingsRepository
import com.inkvol.app.ui.components.ReaderOpenRect
import com.inkvol.app.ui.theme.InkvolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class NovelTheme(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val dark: Boolean,
)

internal fun novelTheme(id: String): NovelTheme = when (id) {
    SettingsKeys.NOVEL_THEME_SEPIA -> NovelTheme(
        background = Color(0xFFF7EFE1),
        text = Color(0xFF3A2F26),
        secondary = Color(0xFF8D7764),
        dark = false,
    )
    SettingsKeys.NOVEL_THEME_GREEN -> NovelTheme(
        background = Color(0xFFEAF3E5),
        text = Color(0xFF263A26),
        secondary = Color(0xFF5B7A5B),
        dark = false,
    )
    SettingsKeys.NOVEL_THEME_DARK -> NovelTheme(
        background = Color(0xFF121212),
        text = Color(0xFFDADADA),
        secondary = Color(0xFF9A9A9A),
        dark = true,
    )
    else -> NovelTheme(
        background = Color(0xFFFDFDFD),
        text = Color(0xFF1F1F1F),
        secondary = Color(0xFF6D6D6D),
        dark = false,
    )
}

private fun chromeColor(theme: NovelTheme): Color =
    if (theme.dark) Color(0xFF1E1E1E) else Color(0xFFF4F4F4)

private fun panelColor(theme: NovelTheme): Color =
    if (theme.dark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)

/** M6.3 优化：面板/菜单栏描边色——跟随软件全局主题色（App MaterialTheme 的 primary）。 */
@Composable
private fun novelOutline(theme: NovelTheme): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)

internal fun novelTextStyle(
    prefs: NovelReadingPrefs,
    theme: NovelTheme,
    fontFamily: FontFamily?,
): TextStyle = TextStyle(
    fontSize = prefs.fontSize.sp,
    lineHeight = (prefs.fontSize * prefs.lineHeightRatio).sp,
    letterSpacing = prefs.letterSpacing.sp,
    color = theme.text,
    fontFamily = fontFamily,
    textIndent = TextIndent(firstLine = (prefs.indentChars * prefs.fontSize).sp),
)

/** M6.3-A 划线颜色 token。 */
internal object NovelHighlightColors {
    val yellow = Color(0xFFFFD666)
    val green = Color(0xFFB7EB8F)
    val blue = Color(0xFF91D5FF)
    val pink = Color(0xFFFFB8C6)
    val all = listOf("yellow" to yellow, "green" to green, "blue" to blue, "pink" to pink)

    fun colorOf(id: String): Color = when (id) {
        "green" -> green
        "blue" -> blue
        "pink" -> pink
        else -> yellow
    }
}

/** M6.3 选区状态：章节内绝对字符区间（startAbs inclusive / endAbs exclusive）。
 *  绝对语义使渲染天然支持跨页（每页按交集渲染），翻页不清空；
 *  圆点锚定的 slice 相对值由所在页动态反查，不再存储。 */
internal data class NovelSelection(
    val startAbs: Int,
    val endAbs: Int,
)

/** M6.3-A 气泡：mode 0 = 新建选区；1 = 编辑已有标注。 */
internal data class NovelBubble(
    val mode: Int,
    val annotationId: Long? = null,
)

/** M6.3-A 笔记面板状态。 */
internal data class NovelNotePanelState(
    val snippet: String,
    val annotationId: Long?,
    val initial: String?,
    val pendingRange: IntRange?,
)

@Composable
fun NovelReaderScreen(
    bookId: String,
    repository: LibraryRepository,
    settingsRepository: SettingsRepository,
    session: NovelOpenSession?,
    openActive: Boolean,
    onTargetRect: (ReaderOpenRect?) -> Unit,
    onReaderReady: () -> Unit,
    onContentSnapshot: (androidx.compose.ui.graphics.ImageBitmap) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val activeSession = session?.takeIf { it.book.id == bookId }

    // M6.3 优化：阅读会话统计（历史页数据源）。
    // 分段计时用 elapsedRealtime（防时钟回拨）；ON_PAUSE 落一段、ON_RESUME 起新段；
    // 离开阅读器时落最后一段；单段超 24h 视为异常丢弃。
    var sessionStartWall by remember(bookId) { mutableLongStateOf(0L) }
    var sessionSegmentStart by remember(bookId) { mutableLongStateOf(0L) }
    var sessionPages by remember(bookId) { mutableIntStateOf(1) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    var book by remember(bookId) { mutableStateOf<BookRecord?>(activeSession?.book) }
    var chapters by remember(bookId) { mutableStateOf(activeSession?.chapters.orEmpty()) }
    var chapterIndex by remember(bookId) { mutableIntStateOf(activeSession?.chapterIndex ?: 0) }
    var chapterText by remember(bookId) { mutableStateOf(activeSession?.chapterText.orEmpty()) }
    var loading by remember(bookId) { mutableStateOf(activeSession == null) }
    var prefs by remember(bookId) { mutableStateOf(activeSession?.prefs ?: NovelReadingPrefs()) }
    var prefsLoaded by remember(bookId) { mutableStateOf(activeSession != null) }
    var importedFonts by remember(bookId) { mutableStateOf(activeSession?.fonts.orEmpty()) }
    var pendingRestoreOffset by remember(bookId) { mutableIntStateOf(activeSession?.pendingRestoreOffset ?: -1) }
    // 「上一章末页」跳转意图：首页左区点击上一页时置位；anchor 效果等整章分页覆盖到末块后落位并消费
    // （快速首排只出前 1-2 页、不含末页，期间不抢锚不消费）。
    var pendingJumpLastPage by remember(bookId) { mutableStateOf(false) }
    var chromeVisible by remember(bookId) { mutableStateOf(!openActive) }
    var quickPanel by remember { mutableStateOf(false) }
    var chapterListOpen by remember { mutableStateOf(false) }
    // M6.3-B：注释浏览器 / 书签列表 / 书内搜索面板 + 章内 char 定位页信号。
    var annotationListOpen by remember { mutableStateOf(false) }
    var bookmarkListOpen by remember { mutableStateOf(false) }
    var searchPanelOpen by remember { mutableStateOf(false) }
    var pendingRestoreChar by remember(bookId) { mutableIntStateOf(-1) }
    var pagination by remember(bookId) {
        mutableStateOf(activeSession?.firstPagination ?: NovelTextPager.empty())
    }
    var paginating by remember(bookId) { mutableStateOf(activeSession == null) }

    // M6.3-A 标注：当前章标注列表 + 选区 / 气泡 / 笔记面板状态。
    var annotations by remember(bookId) { mutableStateOf<List<AnnotationRecord>>(emptyList()) }
    var selection by remember(bookId) { mutableStateOf<NovelSelection?>(null) }
    var bubble by remember(bookId) { mutableStateOf<NovelBubble?>(null) }
    var bubbleAnnotation by remember(bookId) { mutableStateOf<AnnotationRecord?>(null) }
    var notePanel by remember(bookId) { mutableStateOf<NovelNotePanelState?>(null) }

    val refreshAnnotations: () -> Unit = {
        scope.launch { annotations = repository.annotations(bookId, chapterIndex) }
    }
    LaunchedEffect(bookId, chapterIndex) {
        annotations = repository.annotations(bookId, chapterIndex)
    }

    // M6.3-B：全书标注 / 书签列表数据（面板打开时刷新）。
    var allAnnotations by remember(bookId) { mutableStateOf<List<AnnotationRecord>>(emptyList()) }
    var bookmarks by remember(bookId) { mutableStateOf<List<BookmarkRecord>>(emptyList()) }
    val refreshAllAnnotations: () -> Unit = {
        scope.launch { allAnnotations = repository.annotations(bookId) }
    }
    val refreshBookmarks: () -> Unit = {
        scope.launch { bookmarks = repository.bookmarks(bookId) }
    }
    LaunchedEffect(annotationListOpen) {
        if (annotationListOpen) refreshAllAnnotations()
    }
    LaunchedEffect(bookmarkListOpen) {
        if (bookmarkListOpen) refreshBookmarks()
    }

    // M6.3-B：统一跳转——关全部面板 + chrome，跨章时清意图并重锚，章内用 char 定位页。
    val jumpTo: (Int, Int) -> Unit = { targetChapter, charStart ->
        annotationListOpen = false
        bookmarkListOpen = false
        searchPanelOpen = false
        chromeVisible = false
        selection = null
        bubble = null
        bubbleAnnotation = null
        pendingRestoreOffset = 0
        pendingJumpLastPage = false
        pendingRestoreChar = charStart
        if (targetChapter != chapterIndex) {
            chapterIndex = targetChapter
        }
    }

    // 小说双页全局档（独立键，独立于漫画）；先快照同步读一次，再 flow 监听即时生效。
    var doublePageMode by remember { mutableStateOf(SettingsKeys.DOUBLE_PAGE_AUTO) }
    LaunchedEffect(bookId) {
        doublePageMode = settingsRepository.stringOf(
            settingsRepository.snapshotPreferences(),
            SettingsKeys.NOVEL_DOUBLE_PAGE,
            SettingsKeys.DOUBLE_PAGE_AUTO,
        )
        settingsRepository.preferences.collect { snapshot ->
            doublePageMode = settingsRepository.stringOf(
                snapshot,
                SettingsKeys.NOVEL_DOUBLE_PAGE,
                SettingsKeys.DOUBLE_PAGE_AUTO,
            )
        }
    }
    // 当前阅读位置锚（字符偏移）：切档/旋转重排后按它重锚，进度保存时同步更新。
    var anchorOffset by remember(bookId) { mutableIntStateOf(-1) }
    var anchoredOnce by remember(bookId) { mutableStateOf(false) }

    // 挖孔兜底记忆：设备曾上报过的最大挖孔 inset（dp）。
    // 部分平板横屏 displayCutout 全 0（挖孔被并入状态栏语义），用该值对左右补偿。
    var rememberedCutoutDp by remember { mutableStateOf(0f) }

    val theme = novelTheme(prefs.theme)
    val dark = theme.dark
    val fontList = listOf(
        "system" to "系统默认",
        "serif" to "衬线",
        "sans" to "黑体",
        "mono" to "等宽",
    ) + importedFonts.map { it.id to it.name }
    val selectedFont = importedFonts.firstOrNull { it.id == prefs.fontId }
    val fontFamily = when (prefs.fontId) {
        "serif" -> FontFamily.Serif
        "sans" -> FontFamily.SansSerif
        "mono" -> FontFamily.Monospace
        else -> NovelFontRegistry.fontFamily(selectedFont)
    }

    val fontImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                NovelFontRegistry.import(context, uri, settingsRepository)?.let { record ->
                    importedFonts = NovelFontRegistry.fonts(settingsRepository)
                    prefs = prefs.copy(fontId = record.id)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        // 用 decorView 取 controller，兼容部分机型；post 到 attach 后再 hide。
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // 阅读期间不再 show，避免状态栏随菜单栏闪回。
        view.post { controller?.hide(WindowInsetsCompat.Type.systemBars()) }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            // 系统栏恢复交给宿主 closeReaderOpen 统一处理，避免双重 show 造成跳动。
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(dark) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
        onDispose { }
    }

    // M6.3 优化：阅读会话分段落库（历史页数据源）。
    DisposableEffect(bookId) {
        sessionStartWall = System.currentTimeMillis()
        sessionSegmentStart = android.os.SystemClock.elapsedRealtime()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    val duration = android.os.SystemClock.elapsedRealtime() - sessionSegmentStart
                    if (duration > 0 && duration <= 24 * 60 * 60 * 1000L) {
                        repository.saveReadingSession(bookId, sessionStartWall, duration, sessionPages)
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    sessionSegmentStart = android.os.SystemClock.elapsedRealtime()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val duration = android.os.SystemClock.elapsedRealtime() - sessionSegmentStart
            if (duration > 0 && duration <= 24 * 60 * 60 * 1000L) {
                repository.saveReadingSession(bookId, sessionStartWall, duration, sessionPages)
            }
        }
    }

    LaunchedEffect(bookId) {
        if (activeSession != null) return@LaunchedEffect
        val data = withContext(Dispatchers.IO) {
            val loadedBook = repository.bookById(bookId)
            val loadedChapters = repository.novelChapters(bookId)
            val loadedProgress = repository.novelProgress(bookId)
            Triple(loadedBook, loadedChapters, loadedProgress)
        }
        book = data.first
        chapters = data.second
        importedFonts = NovelFontRegistry.fonts(settingsRepository)
        val savedPrefs = withContext(Dispatchers.IO) {
            settingsRepository.snapshotPreferences().let { snapshot ->
                val perBook = settingsRepository.stringOf(snapshot, SettingsKeys.novelBookKey(bookId), "")
                // per-book 未设置时回退到设置页的全局默认。
                if (perBook.isNotBlank()) {
                    perBook
                } else {
                    settingsRepository.stringOf(snapshot, SettingsKeys.NOVEL_GLOBAL_PREFS, "")
                }
            }
        }
        if (savedPrefs.isNotBlank()) {
            runCatching {
                prefs = kotlinx.serialization.json.Json.decodeFromString(NovelReadingPrefs.serializer(), savedPrefs)
            }
        }
        prefsLoaded = true
        data.third?.let { progress ->
            chapterIndex = progress.chapterIndex.coerceIn(0, (data.second.lastIndex).coerceAtLeast(0))
            pendingRestoreOffset = progress.charOffset
        }
        loading = false
    }

    LaunchedEffect(prefsLoaded, chapterIndex) {
        if (!prefsLoaded) return@LaunchedEffect
        val chapter = chapters.getOrNull(chapterIndex) ?: return@LaunchedEffect
        if (activeSession?.chapterIndex == chapterIndex && chapterText == activeSession.chapterText) {
            return@LaunchedEffect
        }
        chapterText = repository.readNovelChapter(bookId, chapter)
    }

    LaunchedEffect(prefs, prefsLoaded) {
        if (!prefsLoaded) return@LaunchedEffect
        settingsRepository.putString(
            SettingsKeys.novelBookKey(bookId),
            kotlinx.serialization.json.Json.encodeToString(NovelReadingPrefs.serializer(), prefs),
        )
    }

    // 读取挖孔兜底记忆（跨会话持久化；设备从未上报过挖孔时为 0=不补偿）。
    LaunchedEffect(bookId, prefsLoaded) {
        if (!prefsLoaded) return@LaunchedEffect
        val snapshot = settingsRepository.snapshotPreferences()
        rememberedCutoutDp = settingsRepository
            .stringOf(snapshot, SettingsKeys.NOVEL_CUTOUT_REMEMBER, "")
            .toFloatOrNull() ?: 0f
    }
    LaunchedEffect(rememberedCutoutDp) {
        if (rememberedCutoutDp > 0f) {
            settingsRepository.putString(
                SettingsKeys.NOVEL_CUTOUT_REMEMBER,
                rememberedCutoutDp.toString(),
            )
        }
    }

    InkvolTheme(darkTheme = dark) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(theme.background)) {
            val availableWidth = maxWidth
            val availableHeight = maxHeight
            // 挖孔避让：正文渲染与分页预算共用同一套派生留白（基准与挖孔 insets 取大者）。
            val layoutDirection = LocalLayoutDirection.current
            val cutoutPad = WindowInsets.displayCutout.asPaddingValues()
            val rawStartDp = cutoutPad.calculateStartPadding(layoutDirection)
            val rawEndDp = cutoutPad.calculateEndPadding(layoutDirection)
            val rawTopDp = cutoutPad.calculateTopPadding()
            val rawBottomDp = cutoutPad.calculateBottomPadding()
            val rawMaxDp = rawStartDp.coerceAtLeast(rawEndDp).coerceAtLeast(rawTopDp).coerceAtLeast(rawBottomDp)
            val isLandscape = maxWidth > maxHeight
            // 小说双页：横向翻页 + (始终 or 横屏)；上下翻页模式不生效（同漫画条漫不双页）。
            val useDouble = prefs.pagingMode != SettingsKeys.NOVEL_PAGE_VERTICAL &&
                doublePageMode != SettingsKeys.DOUBLE_PAGE_OFF &&
                (doublePageMode == SettingsKeys.DOUBLE_PAGE_ALWAYS || isLandscape)
            SideEffect {
                if (rawMaxDp.value > rememberedCutoutDp) rememberedCutoutDp = rawMaxDp.value
            }
            val startPadDp: Dp
            val endPadDp: Dp
            if (rawMaxDp.value <= 0f && isLandscape && rememberedCutoutDp > 0f) {
                // OEM 兜底：横屏时挖孔 insets 全 0（部分平板把短边挖孔并入状态栏语义），
                // 用记忆化的挖孔值对左右同时补偿（同一物理孔，孔径不随旋转变化）。
                val compensation = rememberedCutoutDp.dp + 6.dp
                startPadDp = prefs.pagePadding.dp.coerceAtLeast(compensation)
                endPadDp = prefs.pagePadding.dp.coerceAtLeast(compensation)
            } else {
                startPadDp = cutoutSafeDp(prefs.pagePadding.dp, rawStartDp)
                endPadDp = cutoutSafeDp(prefs.pagePadding.dp, rawEndDp)
            }
            val topPadDp = cutoutSafeDp(24.dp, rawTopDp)
            val bottomPadDp = cutoutSafeDp(24.dp, rawBottomDp)
            val pageContentPadding = PaddingValues(
                start = startPadDp,
                end = endPadDp,
                top = topPadDp,
                bottom = bottomPadDp,
            )
            val horizontalInsetPx = with(density) { (startPadDp + endPadDp).toPx().roundToInt() }
            val verticalInsetPx = with(density) { (topPadDp + bottomPadDp).toPx().roundToInt() }
            val snapshotWidthPx = with(density) { availableWidth.toPx().roundToInt() }.coerceAtLeast(1)
            val snapshotHeightPx = with(density) { availableHeight.toPx().roundToInt() }.coerceAtLeast(1)
            val widthPx = (snapshotWidthPx - horizontalInsetPx).coerceAtLeast(1)
            val heightPx = (snapshotHeightPx - verticalInsetPx).coerceAtLeast(1)
            // 双页同源预算：半页宽 = (正文区宽 - 中缝)/2，分页/渲染/快照三者共用该值防错位。
            val gutterPx = with(density) { NOVEL_SPREAD_GUTTER.toPx().roundToInt() }
            val halfPageWidthPx = ((widthPx - gutterPx) / 2).coerceAtLeast(1)
            val paginateWidthPx = if (useDouble) halfPageWidthPx else widthPx

            LaunchedEffect(availableWidth, availableHeight) {
                onTargetRect(
                    ReaderOpenRect(
                        0f,
                        0f,
                        with(density) { availableWidth.toPx() },
                        with(density) { availableHeight.toPx() },
                    ),
                )
            }

            LaunchedEffect(prefsLoaded, chapterText, prefs, fontFamily, paginateWidthPx, useDouble, openActive) {
                if (!prefsLoaded || chapterText.isEmpty() || paginateWidthPx <= 0 || heightPx <= 0) {
                    pagination = NovelTextPager.empty()
                    paginating = false
                    return@LaunchedEffect
                }
                val prepared = activeSession?.takeIf {
                    it.chapterIndex == chapterIndex && it.chapterText == chapterText && it.prefs == prefs &&
                        it.paginationWidthPx == paginateWidthPx
                }?.firstPagination
                if (prepared != null) {
                    pagination = prepared
                    paginating = false
                } else {
                    paginating = true
                    var first = NovelTextPager.empty()
                    withContext(Dispatchers.Default) {
                        first = NovelTextPager.paginate(
                            chapterText = chapterText,
                            prefs = prefs,
                            fontFamily = fontFamily,
                            widthPx = paginateWidthPx,
                            heightPx = heightPx,
                            density = density,
                            textMeasurer = textMeasurer,
                            // 双页快速路径预排 2 页，保证首个 spread 两半齐整再交接。
                            maxPages = if (useDouble) 2 else 1,
                        )
                    }
                    pagination = first
                    paginating = false
                }
                // 打开交接期间只保留预排页；整章分页等 Reader 接管后再执行。
                if (openActive) return@LaunchedEffect
                var full = NovelTextPager.empty()
                withContext(Dispatchers.Default) {
                    full = NovelTextPager.paginate(
                        chapterText = chapterText,
                        prefs = prefs,
                        fontFamily = fontFamily,
                        widthPx = paginateWidthPx,
                        heightPx = heightPx,
                        density = density,
                        textMeasurer = textMeasurer,
                    )
                }
                pagination = full
            }

            val pages = pagination.pages
            // 双页 spread 组合：纯展示层两两配对（末页落单），进度锚定仍按字符偏移。
            val spreads = remember(pages, useDouble) {
                if (useDouble) pages.chunked(2) else emptyList()
            }
            val pagerState = rememberPagerState(initialPage = 0) {
                (if (useDouble) spreads.size else pages.size).coerceAtLeast(1)
            }

            // M6.3-A：翻页 / 换章 / 排版变化时清空选区与气泡。
            // M6.3 优化：翻页计数（阅读统计 pages_read）。
            LaunchedEffect(pagerState.currentPage) {
                sessionPages++
            }

            // M6.3 优化：滑动跨章——末页继续向左滑（上下翻页为向下滑）触发下一章；
            // 首页向反方向滑触发上一章（落上一章末页）。阈值 = 屏宽/3 累计 overscroll。
            var crossChapterAdvance by remember { mutableFloatStateOf(0f) }
            val crossChapterConnection = remember(pages.size, chapterIndex, chapters.size) {
                object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                    ): Offset {
                        // 划线 / 气泡 / 面板激活期间不跨章
                        if (selection != null || bubble != null || quickPanel ||
                            chapterListOpen || notePanel != null
                        ) {
                            crossChapterAdvance = 0f
                            return Offset.Zero
                        }
                        val threshold = with(density) { 240.dp.toPx() }
                        val atLast = pagerState.currentPage >= pages.lastIndex
                        val atFirst = pagerState.currentPage <= 0
                        val vertical = prefs.pagingMode == SettingsKeys.NOVEL_PAGE_VERTICAL
                        val avail = if (vertical) available.y else available.x
                        val canNext = chapterIndex < chapters.lastIndex
                        val canPrev = chapterIndex > 0
                        if (avail < 0 && atLast && canNext) {
                            crossChapterAdvance += -avail
                            if (crossChapterAdvance > threshold) {
                                crossChapterAdvance = 0f
                                pendingRestoreOffset = 0
                                pendingJumpLastPage = false
                                chapterIndex += 1
                            }
                        } else if (avail > 0 && atFirst && canPrev) {
                            crossChapterAdvance += avail
                            if (crossChapterAdvance > threshold) {
                                crossChapterAdvance = 0f
                                pendingRestoreOffset = Int.MAX_VALUE
                                pendingJumpLastPage = true
                                chapterIndex -= 1
                            }
                        } else {
                            crossChapterAdvance = 0f
                        }
                        return Offset.Zero
                    }
                }
            }

            // M6.3 跨页续拖：选区为章节绝对 char，翻页/重排不再清空（渲染按交集天然支持跨页）；
            // 仅章 / 排版 / 字体 / 双页切换才清空。dragging/dragSide 供气泡归属与隐藏。
            var dragging by remember(bookId) { mutableStateOf(false) }
            var dragSide by remember(bookId) { mutableStateOf("") }
            val converters = remember(bookId) {
                mutableStateMapOf<Int, (Offset) -> Int?>()
            }
            // M6.3 跨页续选：拦截层（restricted 手势上下文）不能直接调 suspend 的
            // scrollToPage，改设 pendingFlip 信号，由本协程执行真实翻页。
            var pendingFlip by remember(bookId) { mutableStateOf<Int?>(null) }
            LaunchedEffect(pendingFlip) {
                pendingFlip?.let { target ->
                    val size = if (useDouble) spreads.size else pages.size
                    if (size > 0) {
                        pagerState.scrollToPage(target.coerceIn(0, size - 1))
                    }
                    pendingFlip = null
                }
            }
            LaunchedEffect(
                chapterIndex,
                prefs,
                fontFamily,
                useDouble,
            ) {
                selection = null
                bubble = null
                bubbleAnnotation = null
                converters.clear()
            }

            // M6.3-A 标注回调（在 pagerState 之后定义；闭包读 var 最新值）。
            // M6.3 跨页续拖：选区为章节绝对 char；拦截层经 callbacks 读最新状态并翻页。
            val pageCallbacks = remember(bookId) {
                NovelPageCallbacks(
                    annotations = { annotations },
                    selection = { selection },
                    onSelectionStart = { startAbs ->
                        selection = NovelSelection(startAbs, startAbs + 1)
                        bubble = NovelBubble(mode = 0)
                        bubbleAnnotation = null
                    },
                    onDragStart = { startAbs ->
                        selection = selection?.copy(startAbs = startAbs)
                    },
                    onDragEnd = { endAbs ->
                        selection = selection?.copy(endAbs = endAbs)
                    },
                    onCommit = { cs0, ce0, color, style ->
                        scope.launch {
                            // 防御：clamp 并保证至少一个字符。
                            val cs = cs0.coerceIn(0, chapterText.length)
                            val ce = ce0.coerceIn(0, chapterText.length)
                            if (ce <= cs) return@launch
                            val snippet = chapterText.substring(cs, ce).replace('\n', ' ')
                            val now = System.currentTimeMillis()
                            // M6.3-B upsert：同章已有区间重叠的标注 → 更新区间/颜色，
                            // 保留其笔记，避免同段重复划线产生多行。
                            val overlap = repository.overlappingAnnotation(
                                bookId, chapterIndex, cs, ce,
                            )
                            if (overlap != null) {
                                val mergedStart = minOf(cs, overlap.charStart)
                                val mergedEnd = maxOf(ce, overlap.charEnd)
                                val mergedSnippet = chapterText
                                    .substring(mergedStart, mergedEnd).replace('\n', ' ')
                                repository.updateAnnotation(
                                    id = overlap.id,
                                    color = color,
                                    style = style,
                                    snippet = mergedSnippet,
                                    charStart = mergedStart,
                                    charEnd = mergedEnd,
                                )
                            } else {
                                repository.addAnnotation(
                                    AnnotationRecord(
                                        id = 0L,
                                        bookId = bookId,
                                        kind = "highlight",
                                        chapterIdx = chapterIndex,
                                        charStart = cs,
                                        charEnd = ce,
                                        snippet = snippet,
                                        note = null,
                                        color = color,
                                        style = style,
                                        createdAt = now,
                                        updatedAt = now,
                                    ),
                                )
                            }
                            annotations = repository.annotations(bookId, chapterIndex)
                            selection = null
                            bubble = null
                        }
                    },
                    onTapAnnotation = { id ->
                        bubbleAnnotation = annotations.firstOrNull { it.id == id }
                        bubble = NovelBubble(mode = 1, annotationId = id)
                        selection = null
                    },
                    onTapNoteBadge = { id ->
                        bubbleAnnotation = annotations.firstOrNull { it.id == id }
                        bubble = NovelBubble(mode = 1, annotationId = id)
                    },
                    onOpenNote = { snippet, id, pendingRange ->
                        val existing = annotations.firstOrNull { it.id == id }
                        notePanel = NovelNotePanelState(
                            snippet = snippet,
                            annotationId = id,
                            initial = existing?.note.orEmpty(),
                            pendingRange = pendingRange,
                        )
                        bubble = null
                        if (id != null) selection = null
                    },
                    onUpdateAnnotation = { id, color, style ->
                        scope.launch {
                            repository.updateAnnotation(id, color = color, style = style)
                            annotations = repository.annotations(bookId, chapterIndex)
                            bubbleAnnotation = annotations.firstOrNull { it.id == id }
                        }
                    },
                    onDeleteAnnotation = { id ->
                        scope.launch {
                            repository.deleteAnnotation(id)
                            annotations = repository.annotations(bookId, chapterIndex)
                            bubble = null
                            bubbleAnnotation = null
                        }
                    },
                    onDismissSelection = {
                        selection = null
                        bubble = null
                    },
                    onBeginAdjust = { sel ->
                        selection = sel
                        bubble = NovelBubble(mode = 0)
                    },
                    onCopy = { text ->
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(
                            android.content.ClipData.newPlainText("inkvol", text),
                        )
                    },
                    // M6.2-C：双页模式同样支持划线。
                    annotationsEnabled = { true },
                    chapterText = { chapterText },
                    chapterIndexOf = { chapterIndex },
                    // M6.3 跨页续拖：convertAt 按目标绝对 char 定位所在页，
                    // 调用该页注册的换算函数，返回拖动点对应的章节内绝对 char。
                    dragging = { dragging },
                    currentPageIndex = { pagerState.currentPage },
                    // M6.3 翻页请求走信号：pendingFlip 由 LaunchedEffect 执行 scrollToPage
                    flipPage = { target -> pendingFlip = target },
                    convertAt = { fingerScreen ->
                        // 各页 convert 自带范围判定：手指在哪页，那页返回非 null
                        converters.values.firstNotNullOfOrNull { it(fingerScreen) }
                    },
                    registerConverter = { key, fn -> converters[key] = fn },
                    unregisterConverter = { key -> converters.remove(key) },
                    onDragActive = { side ->
                        dragging = side != null
                        dragSide = side.orEmpty()
                    },
                )
            }
            var currentSnapshot by remember(bookId) { mutableStateOf(activeSession?.firstPageBitmap) }
            var snapshotChapter by remember(bookId) { mutableIntStateOf(-1) }
            var snapshotPage by remember(bookId) { mutableIntStateOf(-1) }

            // 翻页后在后台预备当前页快照（双页=左右两半渲染进同一张位图）；返回时无需主线程排版。
            LaunchedEffect(
                pages,
                pagerState.currentPage,
                chapterIndex,
                prefs,
                fontFamily,
                snapshotWidthPx,
                snapshotHeightPx,
                startPadDp,
                endPadDp,
                topPadDp,
                useDouble,
            ) {
                val targetPage = pagerState.currentPage
                val bitmap = withContext(Dispatchers.Default) {
                    if (useDouble) {
                        val spread = spreads.getOrNull(targetPage) ?: return@withContext null
                        NovelPageSnapshot.render(
                            page = spread.getOrNull(0),
                            prefs = prefs,
                            theme = theme,
                            fontFamily = fontFamily,
                            widthPx = snapshotWidthPx,
                            heightPx = snapshotHeightPx,
                            density = density,
                            textMeasurer = textMeasurer,
                            startPadPx = with(density) { startPadDp.toPx() },
                            endPadPx = with(density) { endPadDp.toPx() },
                            topPadPx = with(density) { topPadDp.toPx() },
                            secondPage = spread.getOrNull(1),
                            halfWidthPx = halfPageWidthPx,
                            gutterPx = with(density) { NOVEL_SPREAD_GUTTER.toPx() },
                        )
                    } else {
                        val page = pages.getOrNull(targetPage) ?: return@withContext null
                        NovelPageSnapshot.render(
                            page = page,
                            prefs = prefs,
                            theme = theme,
                            fontFamily = fontFamily,
                            widthPx = snapshotWidthPx,
                            heightPx = snapshotHeightPx,
                            density = density,
                            textMeasurer = textMeasurer,
                            startPadPx = with(density) { startPadDp.toPx() },
                            endPadPx = with(density) { endPadDp.toPx() },
                            topPadPx = with(density) { topPadDp.toPx() },
                        )
                    }
                } ?: return@LaunchedEffect
                if (pagerState.currentPage == targetPage) {
                    currentSnapshot = bitmap
                    snapshotChapter = chapterIndex
                    snapshotPage = targetPage
                }
            }

            // 恢复/切档/旋转统一重锚：按字符偏移定位页（双页定位 spread），
            // 重排后用 anchorOffset 重锚（不走页码，防跳页）；换章走 pendingRestoreOffset=0；
            // 「上一章末页」(pendingJumpLastPage) 等整章分页覆盖到末块后直接落末页/末 spread。
            LaunchedEffect(pages, spreads, useDouble) {
                if (pages.isEmpty()) return@LaunchedEffect
                val restore = pendingRestoreOffset
                val jumpLast = pendingJumpLastPage
                // 本章最后一个内容块的正文末位；半截快排页（前 1-2 页）够不到它 → jumpLast 先等待。
                val lastContentEnd = pagination.blocks.lastOrNull()
                    ?.let { it.charStart + it.text.length } ?: 0
                val jumpLastReady = jumpLast && pages.last().charEnd >= lastContentEnd
                if (jumpLast && !jumpLastReady) return@LaunchedEffect
                val target = when {
                    restore >= 0 -> restore
                    jumpLastReady -> 0 // 末页走 lastIdx 直落，不走偏移定位
                    anchoredOnce -> anchorOffset.coerceAtLeast(0)
                    else -> 0
                }
                val idx = if (jumpLastReady) {
                    if (useDouble) spreads.lastIndex else pages.lastIndex
                } else if (useDouble) {
                    spreads.indexOfFirst { spread ->
                        val start = spread.first().charStart
                        val end = spread.last().charEnd
                        start <= target && target < end
                    }
                } else {
                    pages.indexOfFirst { it.charStart <= target && target < it.charEnd }
                }
                // 恢复目标还没被当前分页结果覆盖（如偏移落在半截快排页之外）→ 不动、不消费，
                // 等整章分页完成后再落位，避免被误按到第 1 页。
                if (idx < 0 && restore >= 0) return@LaunchedEffect
                pagerState.scrollToPage(idx.coerceAtLeast(0))
                if (restore >= 0) pendingRestoreOffset = -1
                if (jumpLastReady) pendingJumpLastPage = false
                anchoredOnce = true
            }

            // M6.3-B：注释/书签/搜索跳转——章内 char 定位页（优先于进度重锚）。
            LaunchedEffect(pendingRestoreChar, pages, spreads, useDouble) {
                val char = pendingRestoreChar
                if (char < 0 || pages.isEmpty()) return@LaunchedEffect
                val idx = if (useDouble) {
                    spreads.indexOfFirst { spread ->
                        val start = spread.first().charStart
                        val end = spread.last().charEnd
                        start <= char && char < end
                    }
                } else {
                    pages.indexOfFirst { it.charStart <= char && char < it.charEnd }
                }
                if (idx >= 0) {
                    pagerState.scrollToPage(idx.coerceAtLeast(0))
                    pendingRestoreChar = -1
                    anchoredOnce = true
                }
                // char 超出当前分页覆盖（半截快排页）→ 等整章分页后再落位
            }

            LaunchedEffect(pagerState.currentPage, pages, spreads, useDouble, chapterIndex) {
                val currentCharStart = if (useDouble) {
                    spreads.getOrNull(pagerState.currentPage)?.firstOrNull()?.charStart
                } else {
                    pages.getOrNull(pagerState.currentPage)?.charStart
                } ?: return@LaunchedEffect
                val chapter = chapters.getOrNull(chapterIndex) ?: return@LaunchedEffect
                val localRatio = if (chapter.charCount > 0) {
                    (currentCharStart.toFloat() / chapter.charCount).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val overall = ((chapterIndex + localRatio) / chapters.size.coerceAtLeast(1))
                    .coerceIn(0f, 0.999f)
                    .toDouble()
                repository.saveNovelProgress(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                    charOffset = currentCharStart,
                    overallPercent = overall,
                )
                anchorOffset = currentCharStart
            }

            LaunchedEffect(loading, paginating, pages) {
                if (!loading && !paginating) onReaderReady()
            }

            val requestBack: () -> Unit = {
                val snapshot = currentSnapshot
                if (snapshot != null && snapshotChapter == chapterIndex && snapshotPage == pagerState.currentPage) {
                    onContentSnapshot(snapshot)
                }
                onBack()
            }
            BackHandler(enabled = chapterListOpen) {
                chapterListOpen = false
                chromeVisible = false
            }
            BackHandler(enabled = quickPanel) {
                quickPanel = false
                chromeVisible = false
            }
            // M6.3-B：注释 / 书签 / 搜索面板返回键关闭（同目录面板模式）
            BackHandler(enabled = annotationListOpen) {
                annotationListOpen = false
                chromeVisible = false
            }
            BackHandler(enabled = bookmarkListOpen) {
                bookmarkListOpen = false
                chromeVisible = false
            }
            BackHandler(enabled = searchPanelOpen) {
                searchPanelOpen = false
                chromeVisible = false
            }
            BackHandler(enabled = notePanel != null) { notePanel = null }
            BackHandler(enabled = selection != null || bubble != null) {
                selection = null
                bubble = null
            }
            BackHandler(
                enabled = !chapterListOpen && !quickPanel && notePanel == null &&
                    selection == null && bubble == null &&
                    !annotationListOpen && !bookmarkListOpen && !searchPanelOpen,
            ) {
                requestBack()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.background)
                    .nestedScroll(crossChapterConnection)
                    .pointerInput(
                        pagerState.currentPage,
                        selection,
                        bubble,
                        chapterIndex,
                        pages,
                        spreads,
                        useDouble,
                    ) {
                        detectTapGestures { offset ->
                            // 选区 / 气泡激活时，点击空白 = 收起，不翻页不呼 chrome。
                            if (selection != null || bubble != null) {
                                selection = null
                                bubble = null
                                return@detectTapGestures
                            }
                            when {
                                offset.x < size.width * 0.32f -> scope.launch {
                                    when {
                                        pagerState.currentPage > 0 ->
                                            pagerState.scrollToPage(pagerState.currentPage - 1)
                                        // 首页点上一页 → 上一章末页。
                                        chapterIndex > 0 -> {
                                            pendingJumpLastPage = true
                                            chapterIndex -= 1
                                        }
                                    }
                                }
                                offset.x > size.width * 0.68f -> scope.launch {
                                    // 双页模式 pager 页 = spread，末页判定必须用 spreads.lastIndex。
                                    val lastPagerIndex = if (useDouble) spreads.lastIndex else pages.lastIndex
                                    when {
                                        pagerState.currentPage < lastPagerIndex ->
                                            pagerState.scrollToPage(pagerState.currentPage + 1)
                                        // 末页点下一页 → 下一章首页。
                                        chapterIndex < chapters.lastIndex -> {
                                            pendingJumpLastPage = false
                                            pendingRestoreOffset = 0
                                            chapterIndex += 1
                                        }
                                    }
                                }
                                else -> chromeVisible = !chromeVisible
                            }
                        }
                    },
            ) {
                when {
                    loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("加载中…", color = theme.secondary)
                        }
                    }
                    pages.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (paginating) "排版中…" else "本章没有内容",
                                color = theme.secondary,
                            )
                        }
                    }
                    else -> {
                        if (prefs.pagingMode == SettingsKeys.NOVEL_PAGE_VERTICAL) {
                            VerticalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                // M6.3 跨页续选：翻页后旧页保持组合，拦截层手势协程不被取消
                                // （默认 0 会在 scrollToPage 时销毁旧页 → 拖动中断）
                                beyondViewportPageCount = 1,
                            ) { page ->
                                NovelPageContent(
                                    page = pages[page],
                                    prefs = prefs,
                                    theme = theme,
                                    fontFamily = fontFamily,
                                    contentPadding = pageContentPadding,
                                    annotations = annotations,
                                    selection = selection,
                                    bubble = bubble,
                                    bubbleAnnotation = bubbleAnnotation,
                                    callbacks = pageCallbacks,
                                    dragging = dragging,
                                    dragSide = dragSide,
                                )
                            }
                        } else if (useDouble) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
                            ) { index ->
                                val spread = spreads.getOrNull(index)
                                NovelSpreadContent(
                                    left = spread?.getOrNull(0),
                                    right = spread?.getOrNull(1),
                                    prefs = prefs,
                                    theme = theme,
                                    fontFamily = fontFamily,
                                    outerPadding = PaddingValues(start = startPadDp, end = endPadDp),
                                    contentPadding = PaddingValues(top = topPadDp, bottom = bottomPadDp),
                                    annotations = annotations,
                                    selection = selection,
                                    bubble = bubble,
                                    bubbleAnnotation = bubbleAnnotation,
                                    callbacks = pageCallbacks,
                                    dragging = dragging,
                                    dragSide = dragSide,
                                )
                            }
                        } else {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
                            ) { page ->
                                NovelPageContent(
                                    page = pages[page],
                                    prefs = prefs,
                                    theme = theme,
                                    fontFamily = fontFamily,
                                    contentPadding = pageContentPadding,
                                    annotations = annotations,
                                    selection = selection,
                                    bubble = bubble,
                                    bubbleAnnotation = bubbleAnnotation,
                                    callbacks = pageCallbacks,
                                    dragging = dragging,
                                    dragSide = dragSide,
                                )
                            }
                        }
                    }
                }

            }

            AnimatedVisibility(
                visible = chromeVisible && !quickPanel && !chapterListOpen &&
                    !annotationListOpen && !bookmarkListOpen && !searchPanelOpen,
                enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { -it / 4 },
                exit = fadeOut(tween(120, easing = FastOutLinearInEasing)) +
                    slideOutVertically(tween(120)) { -it / 4 },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                NovelTopBar(
                    title = book?.title ?: "小说",
                    chapterTitle = chapters.getOrNull(chapterIndex)?.title ?: "",
                    theme = theme,
                    onBack = requestBack,
                    onBookmarks = {
                        chapterListOpen = false
                        bookmarkListOpen = true
                    },
                    onAnnotations = {
                        chapterListOpen = false
                        annotationListOpen = true
                    },
                    onChapterList = {
                        quickPanel = false
                        chapterListOpen = true
                    },
                    onSettings = {
                        chapterListOpen = false
                        quickPanel = true
                    },
                )
            }

            AnimatedVisibility(
                visible = chromeVisible && !quickPanel && !chapterListOpen &&
                    !annotationListOpen && !bookmarkListOpen && !searchPanelOpen,
                enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 4 },
                exit = fadeOut(tween(120, easing = FastOutLinearInEasing)) +
                    slideOutVertically(tween(120)) { it / 4 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                NovelBottomBar(
                    chapterIndex = chapterIndex,
                    chapterCount = chapters.size,
                    pageLabel = if (useDouble) {
                        val first = pagerState.currentPage * 2 + 1
                        val second = first + 1
                        if (second <= pages.size) "P$first-$second/${pages.size}" else "P$first/${pages.size}"
                    } else {
                        "P${pagerState.currentPage + 1}/${pages.size}"
                    },
                    fraction = ((pagerState.currentPage + 1).toFloat() /
                        (if (useDouble) spreads.size else pages.size).coerceAtLeast(1))
                        .coerceIn(0f, 1f),
                    theme = theme,
                    onSearch = {
                        chapterListOpen = false
                        searchPanelOpen = true
                    },
                    onSettings = {
                        chapterListOpen = false
                        quickPanel = true
                    },
                )
            }

            NovelChapterList(
                visible = chapterListOpen,
                chapters = chapters,
                currentIndex = chapterIndex,
                theme = theme,
                onPick = { target ->
                    chapterListOpen = false
                    pendingJumpLastPage = false
                    if (target != chapterIndex) {
                        pendingRestoreOffset = 0
                        chapterIndex = target
                    } else {
                        scope.launch { pagerState.scrollToPage(0) }
                    }
                },
                onDismiss = {
                    chapterListOpen = false
                    chromeVisible = false
                },
            )

            // M6.3-B：注释浏览器 / 书签列表 / 书内搜索三个面板（视觉模式同 NovelChapterList）
            NovelAnnotationList(
                visible = annotationListOpen,
                annotations = allAnnotations,
                chapters = chapters,
                theme = theme,
                onPick = { a -> jumpTo(a.chapterIdx, a.charStart) },
                onDelete = { id ->
                    scope.launch {
                        repository.deleteAnnotation(id)
                        refreshAllAnnotations()
                        refreshAnnotations()
                    }
                },
                onDismiss = {
                    annotationListOpen = false
                    chromeVisible = false
                },
            )
            NovelBookmarkList(
                visible = bookmarkListOpen,
                bookmarks = bookmarks,
                chapters = chapters,
                theme = theme,
                onAdd = {
                    // 添加当前页首位置书签：页首 char + 页首 20 字做摘要，幂等（UNIQUE 约束）
                    val page = pages.getOrNull(pagerState.currentPage)
                    if (page != null) {
                        scope.launch {
                            repository.addBookmark(
                                bookId, chapterIndex, page.charStart,
                                chapterText.substring(
                                    page.charStart,
                                    (page.charStart + 20).coerceAtMost(chapterText.length),
                                ).replace('\n', ' '),
                            )
                            refreshBookmarks()
                        }
                    }
                },
                onPick = { b -> jumpTo(b.chapterIdx, b.charStart) },
                onDelete = { id ->
                    scope.launch {
                        repository.deleteBookmark(id)
                        refreshBookmarks()
                    }
                },
                onDismiss = {
                    bookmarkListOpen = false
                    chromeVisible = false
                },
            )
            NovelSearchPanel(
                visible = searchPanelOpen,
                bookId = bookId,
                repository = repository,
                chapters = chapters,
                theme = theme,
                onPick = { chapterIdx, charStart -> jumpTo(chapterIdx, charStart) },
                onDismiss = {
                    searchPanelOpen = false
                    chromeVisible = false
                },
            )

            NovelQuickPanel(
                visible = quickPanel,
                prefs = prefs,
                doublePageMode = doublePageMode,
                onSetDoublePageMode = { value ->
                    // 立即同步本地状态（面板即时生效），异步持久化。
                    doublePageMode = value
                    scope.launch { settingsRepository.putString(SettingsKeys.NOVEL_DOUBLE_PAGE, value) }
                },
                fonts = fontList,
                importedFonts = importedFonts,
                theme = theme,
                onImportFont = {
                    fontImporter.launch(
                        arrayOf(
                            "application/x-font-ttf",
                            "application/x-font-otf",
                            "font/ttf",
                            "font/otf",
                        ),
                    )
                },
                onPrefsChange = { prefs = it },
                onDeleteFont = { record ->
                    scope.launch {
                        NovelFontRegistry.delete(context, record, settingsRepository)
                        importedFonts = NovelFontRegistry.fonts(settingsRepository)
                        if (prefs.fontId == record.id) prefs = prefs.copy(fontId = "system")
                    }
                },
                onDismiss = {
                    quickPanel = false
                    chromeVisible = false
                },
            )

            notePanel?.let { state ->
                NovelNotePanel(
                    state = state,
                    theme = theme,
                    onSave = { text ->
                        scope.launch {
                            if (state.annotationId != null) {
                                repository.updateAnnotation(state.annotationId, note = text)
                            } else {
                                val range = state.pendingRange
                                if (range != null) {
                                    val snippet = chapterText
                                        .substring(range.first, range.last + 1)
                                        .replace('\n', ' ')
                                    val now = System.currentTimeMillis()
                                    repository.addAnnotation(
                                        AnnotationRecord(
                                            id = 0L,
                                            bookId = bookId,
                                            kind = "highlight",
                                            chapterIdx = chapterIndex,
                                            charStart = range.first,
                                            charEnd = range.last + 1,
                                            snippet = snippet,
                                            note = text,
                                            color = "yellow",
                                            style = "bg",
                                            createdAt = now,
                                            updatedAt = now,
                                        ),
                                    )
                                }
                            }
                            annotations = repository.annotations(bookId, chapterIndex)
                            notePanel = null
                            selection = null
                            bubble = null
                        }
                    },
                    onDelete = {
                        scope.launch {
                            state.annotationId?.let { repository.deleteAnnotation(it) }
                            annotations = repository.annotations(bookId, chapterIndex)
                            notePanel = null
                            selection = null
                            bubble = null
                        }
                    },
                    onDismiss = {
                        notePanel = null
                    },
                )
            }
        }
    }
}

/** M6.3-A 标注回调集；remember 一次，闭包读 screen 状态 var 最新值。
 *  M6.2-C 双页：onSelectionStart/onDragStart/onDragEnd 带 pageIdx（渲染页在 spread 中的页序），
 *  onOpenNote 的 pendingRange 由 NovelPageContent 用自身 page 计算后传入。 */
private class NovelPageCallbacks(
    val annotations: () -> List<AnnotationRecord>,
    val chapterText: () -> String,
    val chapterIndexOf: () -> Int,
    val selection: () -> NovelSelection?,
    // 选区回调统一传章节内绝对 char（startAbs inclusive / endAbs exclusive）
    val onSelectionStart: (Int) -> Unit,
    val onDragStart: (Int) -> Unit,
    val onDragEnd: (Int) -> Unit,
    val onCommit: (Int, Int, String, String) -> Unit,
    val onTapAnnotation: (Long) -> Unit,
    val onTapNoteBadge: (Long) -> Unit,
    val onOpenNote: (String, Long?, IntRange?) -> Unit,
    val onUpdateAnnotation: (Long, String?, String?) -> Unit,
    val onDeleteAnnotation: (Long) -> Unit,
    val onDismissSelection: () -> Unit,
    val onBeginAdjust: (NovelSelection) -> Unit,
    val onCopy: (String) -> Unit,
    val annotationsEnabled: () -> Boolean,
    // M6.3 跨页续拖支持
    val dragging: () -> Boolean,
    // 翻页请求：设 pendingFlip 信号，由 screen 级 LaunchedEffect 执行 scrollToPage
    // （手势协程是 AwaitPointerEventScope 的 restricted 上下文，不能直接调任意 suspend）
    val flipPage: (Int) -> Unit,
    val currentPageIndex: () -> Int,
    val convertAt: (Offset) -> Int?,
    val registerConverter: (Int, (Offset) -> Int?) -> Unit,
    val unregisterConverter: (Int) -> Unit,
    val onDragActive: (String?) -> Unit,
)

/** 词边界：CJK 字符逐字为选区，拉丁字母/数字连词，标点单字符。 */
private fun selectionBoundsAt(text: String, offset: Int): IntRange {
    if (text.isEmpty()) return 0..0
    val o = offset.coerceIn(0, text.length - 1)
    val c = text[o]
    val isCjk = c.code in 0x2E80..0x9FFF || c.code in 0x30000..0x313FFF
    if (isCjk || !c.isLetterOrDigit()) return o..o
    var s = o
    var e = o
    while (s > 0 && text[s - 1].isLetterOrDigit() && text[s - 1].code !in 0x2E80..0x9FFF) s--
    while (e < text.length - 1 && text[e + 1].isLetterOrDigit() && text[e + 1].code !in 0x2E80..0x9FFF) e++
    return s..e
}

@Composable
private fun NovelPageContent(
    page: NovelTextPage,
    prefs: NovelReadingPrefs,
    theme: NovelTheme,
    fontFamily: FontFamily?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    annotations: List<AnnotationRecord> = emptyList(),
    selection: NovelSelection? = null,
    bubble: NovelBubble? = null,
    bubbleAnnotation: AnnotationRecord? = null,
    callbacks: NovelPageCallbacks? = null,
    dragging: Boolean = false,
    dragSide: String = "",
) {
    val gesturesOn = callbacks != null && callbacks.annotationsEnabled()
    val layouts = remember(page) { arrayOfNulls<TextLayoutResult>(page.slices.size) }
    // slicePos = Text 相对 content root Box 内容区的坐标（手柄/气泡 offset 与其同坐标系，
    // 禁止混用 positionInRoot 窗口坐标，否则 padding 双计导致手柄整体漂移）。
    val slicePos = remember(page) { arrayOfNulls<Offset>(page.slices.size) }
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val rootPos = remember { mutableStateOf(Offset.Zero) } // 本页相对窗口（convert 屏幕换算用）

    // M6.3 选区为章节绝对 char：渲染按本页 slice 交集天然支持跨页；
    // 圆点按端点归属页各自渲染（startAbs / endAbs-1 落在本页才画）。
    val sel = selection
    val selLo = sel?.let { minOf(it.startAbs, it.endAbs) }
    val selHiEx = sel?.let { maxOf(it.startAbs, it.endAbs) }
    val selAbs: IntRange? = if (sel != null && selHiEx!! > selLo!!) selLo until selHiEx else null
    // 拖动端所在页渲染气泡（新建）；编辑气泡仍按标注所在页。
    val bubbleOnPage = when {
        bubble == null -> null
        bubble.mode == 1 && bubbleAnnotation != null -> {
            val inPage = bubbleAnnotation.chapterIdx == callbacks?.chapterIndexOf() &&
                bubbleAnnotation.charStart >= page.charStart &&
                bubbleAnnotation.charStart < page.charEnd
            if (inPage) bubble else null
        }
        else -> {
            val focusChar = if (dragSide == "end") selHiEx?.minus(1) ?: selLo else selLo
            val inPage = focusChar != null &&
                focusChar >= page.charStart && focusChar < page.charEnd
            if (inPage) bubble else null
        }
    }

    fun annotatedSlice(sliceIdx: Int, slice: NovelTextSlice): AnnotatedString {
        val cs = slice.charStart
        val ce = slice.charEnd
        val builder = AnnotatedString.Builder()
        builder.append(slice.text)
        // M6.3 防御：无障碍服务转换 AnnotatedString 时 span 越界会崩溃
        // （IndexOutOfBoundsException: setSpan ends beyond length），
        // 所有 addStyle 区间一律 clamp 到 builder 当前长度内。
        fun addStyleSafe(style: SpanStyle, start: Int, end: Int) {
            val s = start.coerceIn(0, builder.length)
            val e = end.coerceIn(s, builder.length)
            if (e > s) builder.addStyle(style, s, e)
        }
        annotations.forEach { a ->
            val s = max(a.charStart, cs)
            val e = min(a.charEnd, ce)
            if (e > s) {
                addStyleSafe(
                    if (a.style == "underline") {
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            color = NovelHighlightColors.colorOf(a.color),
                        )
                    } else {
                        SpanStyle(background = NovelHighlightColors.colorOf(a.color).copy(alpha = 0.45f))
                    },
                    s - cs,
                    e - cs,
                )
            }
        }
        selAbs?.let { range ->
            val s = max(range.first, cs)
            val e = min(range.last + 1, ce)
            if (e > s) {
                addStyleSafe(
                    SpanStyle(background = Color(0x6681D5FF)),
                    s - cs,
                    e - cs,
                )
            }
        }
        if (callbacks != null) {
            val badge = annotations.firstOrNull {
                it.note != null && it.charEnd > cs && it.charEnd <= ce
            }
            if (badge != null) {
                builder.append(" ❖")
                addStyleSafe(
                    SpanStyle(color = NovelHighlightColors.colorOf(badge.color)),
                    slice.text.length,
                    slice.text.length + 2,
                )
            }
        }
        return builder.toAnnotatedString()
    }

    // root 坐标 → (sliceIdx, slice 内字符 index)；先按行带找行、行内吸附。
    // M6.3 修复「拉长选区乱跑」：
    //   ① x 严格范围判定——双页布局另一页的 y 几乎相同，仅 x 可区分，
    //      旧逻辑 probe.x clamp 到 0 掩盖了越界，导致两页换算器竞争、选区随机跳页；
    //   ② y 收紧为 slice 自身范围——手指落在段间距/页边缘时不匹配任何段落，
    //      convertAt 返回 null → 选区保持上一帧，宁停顿不乱跳。
    fun convertRootToSliceOffset(posInRoot: Offset): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestDist = Float.MAX_VALUE
        for (i in page.slices.indices) {
            val l = layouts[i] ?: continue
            val p = slicePos[i] ?: continue
            val local = posInRoot - p
            if (local.x < 0f || local.x > l.size.width) continue
            if (local.y < 0f || local.y > l.size.height) continue
            val line = l.getLineForVerticalPosition(local.y)
            val lo = l.getLineStart(line)
            val hi = max(l.getLineEnd(line), lo) // getLineEnd 为 exclusive，最大合法字符 index = hi-1
            // 探针 y 用行中心，x 用拖动点；结果强制吸附在本行内。
            val probe = Offset(
                local.x.coerceIn(0f, l.size.width.toFloat()),
                ((l.getLineTop(line) + l.getLineBottom(line)) / 2f),
            )
            val off = l.getOffsetForPosition(probe).coerceIn(lo, (hi - 1).coerceAtLeast(lo))
            val d = abs(local.y - l.size.height / 2f)
            if (d < bestDist) {
                bestDist = d
                best = i to off
            }
        }
        return best
    }

    // 绝对 char → 本页 (sliceIdx, slice 内偏移)；不在本页返回 null。
    fun locateAbs(abs: Int): Pair<Int, Int>? {
        val idx = page.slices.indexOfFirst { abs >= it.charStart && abs < it.charEnd }
        if (idx >= 0) return idx to (abs - page.slices[idx].charStart)
        // abs = 本页末尾的 exclusive 插入位 → 从末尾向前找最后一个文本 slice
        // （最后一个 slice 可能是图片段，图片无字符锚点）
        if (abs == page.charEnd && page.slices.isNotEmpty()) {
            for (k in page.slices.indices.reversed()) {
                val sl = page.slices[k]
                if (sl.imagePath == null && sl.text.isNotEmpty()) return k to sl.text.length
            }
        }
        return null
    }

    fun handleAnchor(sliceIdx: Int, off: Int, isStart: Boolean): Offset? {
        val l = layouts.getOrNull(sliceIdx) ?: return null
        val p = slicePos.getOrNull(sliceIdx) ?: return null
        val text = page.slices.getOrNull(sliceIdx)?.text ?: return null
        if (text.isEmpty()) return null
        // startOff = 起始字符 index（inclusive）；endOff = exclusive，
        // 尾手柄必须锚在「最后选中字符」的右缘，即 off-1，而不是 off 指向的未选中字符。
        val o = if (isStart) {
            off.coerceIn(0, text.length - 1)
        } else {
            (off - 1).coerceIn(0, text.length - 1)
        }
        val box = l.getBoundingBox(o)
        // 微信读书样式：圆点无竖线，圆心在「所在行行底」下方少许，两圆点同线。
        val line = l.getLineForOffset(o)
        val lineBottom = l.getLineBottom(line)
        val embed = with(density) { 2.dp.toPx() }
        val x = if (isStart) box.left else box.right
        return Offset(p.x + x, p.y + lineBottom + embed)
    }

    fun handleAnchorAbs(abs: Int, isStart: Boolean): Offset? {
        val (idx, off) = locateAbs(abs) ?: return null
        return handleAnchor(idx, off, isStart)
    }

    // 拖动手柄期间隐藏气泡（dragging 由 screen 持有、拦截层经 callbacks.onDragActive 置位）。
    var bubbleMeasuredH by remember { mutableStateOf(0f) }
    val rootSize = remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    // M6.3 跨页续拖：向 screen 注册本页换算函数（手指屏幕坐标 → 章节 abs），
    // key = page.charStart（唯一），拦截层跨页后经 callbacks.convertAt 调用。
    DisposableEffect(page) {
        callbacks?.let { cb ->
            cb.registerConverter(page.charStart) { screenPos ->
                convertRootToSliceOffset(screenPos - rootPos.value)?.let { (si, off) ->
                    page.slices.getOrNull(si)?.let { it.charStart + off }
                }
            }
        }
        onDispose {
            callbacks?.unregisterConverter(page.charStart)
            // M6.3 兜底：极端情况下（快速连续翻页销毁本页）拖动协程可能被取消
            // 而来不及调 onDragActive(null)，dragging=true 残留会让气泡永久隐藏。
            if (callbacks?.dragging() == true) callbacks.onDragActive(null)
        }
    }

    // 气泡放置：以「整个选区的边界」为参照——
    // 上方：气泡 bottom = 选区首行 top - 8dp；下方：气泡 top = 选区末行 bottom + 8dp。
    // 永不压选区自身的任何行；水平 clamp 在本页内。
    // 高度用实测值（onSizeChanged 回写），估算值只做首帧兜底。
    fun bubbleTopLeft(selTop: Float, selBottom: Float, anchorX: Float, mode: Int): Offset {
        val bubbleW = with(density) { 200.dp.toPx() }
        val estimatedH = with(density) { if (mode == 1) 150.dp.toPx() else 118.dp.toPx() }
        val bubbleH = if (bubbleMeasuredH > 0f) bubbleMeasuredH else estimatedH
        val rootW = rootSize.value.width.toFloat().coerceAtLeast(bubbleW + 16f)
        val gap = with(density) { 8.dp.toPx() }
        val preferAbove = selTop - bubbleH - gap > 0f
        val y = if (preferAbove) {
            selTop - bubbleH - gap
        } else {
            selBottom + gap
        }
        val x = (anchorX - bubbleW / 2).coerceIn(8f, (rootW - bubbleW - 8f).coerceAtLeast(8f))
        return Offset(x, y)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            // M6.3 手柄拖动拦截层：down 落在任一圆点附近即接管（consume）。
            // 判定取「距离最近的圆」：单字选区两圆触控区重叠，按先命中顺序会误拉。
            // M6.3 修复「拖完再拉变成两圆一起动」：跨页选区只有一端在本页，
            // 旧逻辑要求两端都在本页（任一 null 整体放弃）→ 正文长按接管 → 选区被重建。
            // 现在允许单端接管：不在本页的端距离记为 MAX_VALUE，按够得着的端判定。
            // key 不含 selection：拖动中选区高频更新，key 变化会重启手势导致 dragging 残留。
            .pointerInput(page, callbacks) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cb = callbacks ?: return@awaitEachGesture
                    // 必须经 callbacks.selection() 读最新状态：闭包捕获的参数 val 是旧组合的值
                    val sel = cb.selection()
                    if (sel == null) return@awaitEachGesture
                    val sp = handleAnchorAbs(sel.startAbs, isStart = true)   // 端点不在本页时为 null
                    val ep = handleAnchorAbs(sel.endAbs, isStart = false)
                    val grabRadius = with(density) { 28.dp.toPx() }
                    val dStart = sp?.let { (down.position - it).getDistance() } ?: Float.MAX_VALUE
                    val dEnd = ep?.let { (down.position - it).getDistance() } ?: Float.MAX_VALUE
                    val grabStart = dStart <= dEnd && dStart <= grabRadius
                    val grabEnd = !grabStart && dEnd <= grabRadius
                    // M6.3 判定失败时放弃（不消费）→ 正文长按正常处理重建选区。
                    // 不做降级接管：40 字内的误接管会拦死「长按重新选区」。
                    // 跨页选区由单端接管覆盖（不在本页的端距离 = MAX_VALUE）。
                    if (!grabStart && !grabEnd) return@awaitEachGesture
                    down.consume()
                    cb.onDragActive(if (grabStart) "start" else "end")
                    // M6.3 另一端固定：down 时快照两端，拖动中每帧双写——
                    // 更新拖动端的同时把另一端显式写回（幂等），
                    // 无论任何路径（重组/翻转换向/清空）意外改动另一端，下一帧都会被钉回。
                    val startPin = sel.startAbs
                    val endPin = sel.endAbs
                    // 钳制「交界处微动导致的方向反复翻转」
                    val otherAbs = if (grabStart) sel.endAbs else sel.startAbs
                    var lastAbs = if (grabStart) sel.startAbs else sel.endAbs
                    var lastWasLo = lastAbs <= otherAbs
                    // 握把自动翻页（M6.3 可靠性改造）：
                    //   通道 a：手指贴本页边缘（拖 end → 底/右缘；拖 start → 顶/左缘）
                    //   通道 b：拖动端字符到达本页尾/头 2 字符内（内容触发，不依赖手指位置）
                    // 条件满足且手指静止 ≥400ms → 瞬间翻页（scrollToPage 避免动画抖动），
                    // 翻页后 300ms 冷却防抖，继续停住/继续拖可连续跨页标记大量内容。
                    // 循环为「事件/帧双路」：16ms 无触摸事件也醒一帧推进计时——
                    // 旧实现纯事件驱动，手指拖到页尾停住后永无事件，翻页计时永远不满。
                    val edgePx = with(density) { 72.dp.toPx() }
                    val holdMs = 400L
                    val cooldownMs = 300L
                    var stillSince = 0L       // 条件满足且手指静止的起始时刻（0 = 不满足或在动）
                    var lastFinger = Offset.Zero
                    var hasFinger = false
                    var cooldownUntil = 0L
                    // 翻页后置 true：拖动端钉到新页边界（grabEnd → 新页首字符；grabStart → 新页末字符），
                    // 手指移动超过 8px 才清除恢复跟随。否则帧路径会按手指位置（新页底部）跳选。
                    var justFlipped = false

                    fun applyDrag(fingerScreen: Offset) {
                        // 翻页后首帧：拖动端钉到新页边界（连续停住 → 连续跨页钉进）
                        if (justFlipped) {
                            val boundaryAbs = if (grabEnd) page.charEnd else page.charStart - 1
                            if (grabStart) {
                                cb.onDragStart(boundaryAbs)
                                cb.onDragEnd(endPin)
                            } else {
                                cb.onDragEnd(boundaryAbs + 1)
                                cb.onDragStart(startPin)
                            }
                            lastAbs = if (grabEnd) boundaryAbs + 1 else boundaryAbs
                            return
                        }
                        cb.convertAt(fingerScreen)?.let { raw ->
                            // start 端 inclusive、end 端 exclusive（含当前字符）
                            val abs = if (grabStart) raw else raw + 1
                            // 方向钳制：本帧方向与上帧相反、且位移 ≤1 字（交界处微动）→ 保持上帧值
                            val nowLo = abs <= otherAbs
                            val flipped = nowLo != lastWasLo
                            if (!flipped || kotlin.math.abs(abs - lastAbs) > 1) {
                                if (grabStart) {
                                    cb.onDragStart(abs)
                                    cb.onDragEnd(endPin)      // 另一端钉死
                                } else {
                                    cb.onDragEnd(abs)
                                    cb.onDragStart(startPin)  // 另一端钉死
                                }
                                lastAbs = abs
                                lastWasLo = nowLo
                            }
                        }
                    }

                    // 返回 true = 触发翻页（flipPage 由循环体调用，避免 local suspend fun
                    // 在 AwaitPointerEventScope（restricted）内的 suspend 调用限制）
                    fun tickEdgeHold(now: Long, atEdge: Boolean): Boolean {
                        // 内容触发（双条件，翻页后自动切换）：
                        //   未跨页：拖动端到达本页尾/头 2 字符内
                        //   已跨页（lastAbs 超出本页区间）：手指位于页缘
                        //   → 连续跨页时由边缘通道接管，不会一路连翻失控
                        val beyondPage = if (grabEnd) lastAbs > page.charEnd else lastAbs < page.charStart
                        val atTail = if (grabEnd) {
                            (lastAbs >= page.charEnd - 2 && lastAbs <= page.charEnd) ||
                                (beyondPage && atEdge)
                        } else {
                            (lastAbs <= page.charStart + 2 && lastAbs >= page.charStart) ||
                                (beyondPage && atEdge)
                        }
                        if ((!atEdge && !atTail) || now < cooldownUntil) {
                            stillSince = 0L
                            return false
                        }
                        if (stillSince == 0L) {
                            stillSince = now
                        }
                        if (now - stillSince > holdMs) {
                            stillSince = 0L
                            cooldownUntil = now + cooldownMs
                            justFlipped = true
                            return true
                        }
                        return false
                    }

                    while (true) {
                        // 事件/帧双路：16ms 无触摸事件也醒一帧（手指停住同样推进握把计时）
                        val event = withTimeoutOrNull(16) { awaitPointerEvent() }
                        if (event == null) {
                            // 帧路径：先核对 up（超时轮询可能错过 pressed 变化）
                            val change = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (change != null && !change.pressed) {
                                change.consume()
                                break
                            }
                            val now = System.currentTimeMillis()
                            if (hasFinger) {
                                val atEdge = if (grabEnd) {
                                    lastFinger.y > size.height - edgePx ||
                                        lastFinger.x > size.width - edgePx
                                } else {
                                    lastFinger.y < edgePx || lastFinger.x < edgePx
                                }
                                if (tickEdgeHold(now, atEdge)) {
                                    cb.flipPage(cb.currentPageIndex() + if (grabEnd) 1 else -1)
                                }
                                // 选区随手指停住处每帧延伸（翻页后新页 convert 就绪即生效）
                                applyDrag(lastFinger + rootPos.value)
                            }
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            justFlipped = false
                            break
                        }
                        hasFinger = true
                        // 静止判定加位移阈值：电容屏停住时的微小噪声 move 不重置 400ms 计时
                        if ((change.position - lastFinger).getDistance() > 8f) {
                            stillSince = 0L   // 手指真的在动
                            justFlipped = false   // 翻页钉边界结束，恢复跟随手指
                        }
                        lastFinger = change.position
                        change.consume()
                        // 手指屏幕位置：本页局部 + 本页 origin（rootPos 为本页 positionInRoot）
                        applyDrag(change.position + rootPos.value)
                        val atEdge = if (grabEnd) {
                            lastFinger.y > size.height - edgePx ||
                                lastFinger.x > size.width - edgePx
                        } else {
                            lastFinger.y < edgePx || lastFinger.x < edgePx
                        }
                        if (tickEdgeHold(System.currentTimeMillis(), atEdge)) {
                            cb.flipPage(cb.currentPageIndex() + if (grabEnd) 1 else -1)
                        }
                    }
                    cb.onDragActive(null)
                }
            }
            .onGloballyPositioned {
                rootPos.value = it.positionInRoot()
                rootSize.value = it.size
            },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(prefs.paragraphSpacing.dp),
        ) {
            page.slices.forEachIndexed { i, slice ->
                val imagePath = slice.imagePath
                if (imagePath != null) {
                    NovelPageImage(path = imagePath, heightPx = slice.heightPx, theme = theme)
                } else {
                    val badge = annotations.firstOrNull {
                        it.note != null && it.charEnd > slice.charStart && it.charEnd <= slice.charEnd
                    }
                    Text(
                        text = annotatedSlice(i, slice),
                        style = novelTextStyle(prefs, theme, fontFamily),
                        onTextLayout = { layouts[i] = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { slicePos[i] = it.positionInParent() }
                            .let { m ->
                                if (!gesturesOn) {
                                    m
                                } else {
                                    m.pointerInput(i, slice.text, annotations) {
                                        awaitEachGesture {
                                            // requireUnconsumed=true：手柄拦截层消费的 down 不进入正文手势
                                            val down = awaitFirstDown()
                                            var stolen = false
                                            var tap: PointerInputChange? = null
                                            val upOrTimeout = withTimeoutOrNull(
                                                viewConfiguration.longPressTimeoutMillis.toLong(),
                                            ) {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                    if (change == null) break
                                                    if (change.isConsumed) {
                                                        // 手柄拦截层接管，本次手势放弃
                                                        stolen = true
                                                        break
                                                    }
                                                    if (!change.pressed) {
                                                        tap = change
                                                        break
                                                    }
                                                }
                                                tap
                                            }
                                            if (stolen) return@awaitEachGesture
                                            if (upOrTimeout == null) {
                                                // M6.3 关键修复：超时路径检查「拦截层是否已接管」。
                                                // 不能用 down.isConsumed——pager 的 scrollable 在 down 时
                                                // 也会 consume（框架行为，防父级抢），会把正常长按一起拦死
                                                // （实测：加 isConsumed 检查后长按完全失效）。
                                                // dragging 只由手柄拦截层设置，语义精确：
                                                // 按住圆停顿超时 → dragging=true → 放弃，防选区被重建为单字。
                                                if (callbacks.dragging()) {
                                                    return@awaitEachGesture
                                                }
                                                // 长按：词边界选区 + 拖动扩展（长按后手指继续按住可拖）
                                                val layout = layouts[i]
                                                if (layout != null) {
                                                    val off = layout
                                                        .getOffsetForPosition(down.position)
                                                        .coerceIn(0, slice.text.length)
                                                    val bounds = selectionBoundsAt(slice.text, off)
                                                    callbacks.onSelectionStart(slice.charStart + bounds.first)
                                                    // endOff 语义 = exclusive 光标位；bounds.last 为含尾字符
                                                    callbacks.onDragEnd(slice.charStart + bounds.last + 1)
                                                    // 长按后按住拖动扩展（Text 长按路径，同页内）
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull {
                                                            it.id == down.id
                                                        } ?: break
                                                        if (!change.pressed) {
                                                            change.consume()
                                                            break
                                                        }
                                                        val rootPosHere =
                                                            change.position + (slicePos[i] ?: Offset.Zero)
                                                        convertRootToSliceOffset(rootPosHere)?.let { (si, off2) ->
                                                            callbacks.onDragEnd(
                                                                page.slices.getOrNull(si)
                                                                    ?.let { it.charStart + off2 }
                                                                    ?: slice.charStart,
                                                            )
                                                        }
                                                        change.consume()
                                                    }
                                                }
                                            } else {
                                                // tap：命中标注 / 笔记角标则消费，否则放行翻页
                                                val layout = layouts[i]
                                                val off = layout
                                                    ?.getOffsetForPosition(upOrTimeout.position)
                                                    ?.coerceIn(0, slice.text.length)
                                                if (off != null) {
                                                    if (badge != null && off >= slice.text.length) {
                                                        upOrTimeout.consume()
                                                        callbacks.onTapNoteBadge(badge.id)
                                                    } else {
                                                        // 命中容差：getOffsetForPosition 在字符右半部 / 行尾
                                                        // 返回下一格插入点，先试 off 再回退 off-1，
                                                        // 保证点字符任意半边都命中（对齐微信读书）。
                                                        val hit = annotations.firstOrNull {
                                                            val c = slice.charStart + off
                                                            c >= it.charStart && c < it.charEnd
                                                        } ?: if (off > 0) {
                                                            annotations.firstOrNull {
                                                                val c = slice.charStart + off - 1
                                                                c >= it.charStart && c < it.charEnd
                                                            }
                                                        } else {
                                                            null
                                                        }
                                                        if (hit != null) {
                                                            upOrTimeout.consume()
                                                            callbacks.onTapAnnotation(hit.id)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                    )
                }
            }
        }

        // 选区手柄 + 气泡 overlay：圆点按端点归属页渲染（跨页选区两页各画自己的圆点）
        if (gesturesOn && selection != null) {
            val sel = selection!!
            val selText = run {
                val text = callbacks.chapterText()
                val s = minOf(sel.startAbs, sel.endAbs).coerceIn(0, text.length)
                val e = maxOf(sel.startAbs, sel.endAbs).coerceIn(0, text.length)
                if (e > s) text.substring(s, e).replace('\n', ' ') else ""
            }
            // 端点归属本页才有锚点（跨页选区两页各自渲染自己的圆点）。
            // M6.3 修复「end 圆偏左一个字」：渲染统一按选区左右边缘锚定——
            // lo = inclusive 首字符 → 左圆锚 box(lo).left（isStart=true）；
            // hi = exclusive 插入位 → 右圆锚 box(hi-1).right（isStart=false）。
            // 旧实现先减一传尾字符 abs 再走 isStart=false 的 off-1，双计减一
            // 导致 end 圆压在倒数第二字右缘。选区方向（翻转）由 lo/hi 天然处理。
            val lo = minOf(sel.startAbs, sel.endAbs)
            val hi = maxOf(sel.startAbs, sel.endAbs)
            val startPos = lo
                .takeIf { it >= page.charStart && it < page.charEnd }
                ?.let { handleAnchorAbs(it, isStart = true) }
            val endPos = hi
                .takeIf { hi > page.charStart && hi <= page.charEnd }
                ?.let { handleAnchorAbs(it, isStart = false) }
            if (startPos != null || endPos != null) {
                // 新建笔记时选区对应的章节绝对区间
                val pendingRange = run {
                    val s = minOf(sel.startAbs, sel.endAbs)
                    val e = maxOf(sel.startAbs, sel.endAbs)
                    if (e > s) s..(e - 1) else null
                }
                startPos?.let { SelectionHandle(anchor = it) }
                endPos?.let { SelectionHandle(anchor = it) }
                if (!dragging) {
                    // 气泡以「本页可见端点的选区边界」放置：selTop = 首行顶、selBottom = 末行底。
                    val lineH = with(density) {
                        (prefs.fontSize * prefs.lineHeightRatio).dp.toPx()
                    }
                    val embed = with(density) { 2.dp.toPx() }
                    // 本页只有单端（跨页选区）时，另一端边界取本页顶部/底部
                    val selTop = startPos?.y?.minus(lineH + embed)
                        ?: with(density) { 0f }
                    val selBottom = endPos?.y ?: rootSize.value.height.toFloat()
                    val anchorX = (startPos ?: endPos)?.x ?: 0f
                    NovelAnnotationBubble(
                        topLeft = bubbleTopLeft(selTop, selBottom, anchorX, bubble?.mode ?: 0),
                        mode = bubble?.mode ?: 0,
                        active = bubbleAnnotation,
                        selText = selText,
                        theme = theme,
                        onMeasuredHeight = { bubbleMeasuredH = it },
                        onPickColor = { color ->
                            // 必须经 callbacks.selection() 读最新状态（拖动后的选区），
                            // 闭包捕获的组合时值在快速拖动后会过期。
                            val cur = callbacks.selection() ?: return@NovelAnnotationBubble
                            val a = minOf(cur.startAbs, cur.endAbs)
                            val b = maxOf(cur.startAbs, cur.endAbs)
                            callbacks.onCommit(a, b, color, "bg")
                        },
                        onNote = {
                            callbacks.onOpenNote(
                                selText,
                                bubbleAnnotation?.takeIf { bubble?.mode == 1 }?.id,
                                pendingRange,
                            )
                        },
                        onCopy = { clipboard.setText(AnnotatedString(selText)) },
                        onAdjust = {
                            bubbleAnnotation?.let { a ->
                                val sIdx = page.slices.indexOfFirst { a.charStart >= it.charStart && a.charStart < it.charEnd }
                                val eIdx = page.slices.indexOfFirst { a.charEnd > it.charStart && a.charEnd <= it.charEnd }
                                if (sIdx >= 0 && eIdx >= 0) {
                                    callbacks.onBeginAdjust(
                                        NovelSelection(
                                            startAbs = a.charStart,
                                            endAbs = a.charEnd,
                                        ),
                                    )
                                }
                            }
                        },
                        onDelete = {
                            bubbleAnnotation?.let { callbacks.onDeleteAnnotation(it.id) }
                        },
                    )
                }
            }
        } else if (gesturesOn && !dragging && bubbleOnPage != null && bubbleOnPage.mode == 1 && bubbleAnnotation != null) {
            // 已有划线的气泡（无选区）：气泡归属判定——标注必须落在本页 char 区间内。
            // 放置以「划线整体边界」为参照：首字符行顶 → 尾字符行底，不压划线自身。
            val a = bubbleAnnotation!!
            val sIdx = page.slices.indexOfFirst { a.charStart >= it.charStart && a.charStart < it.charEnd }
            val eIdx = page.slices.indexOfFirst { a.charEnd > it.charStart && a.charEnd <= it.charEnd }
            val startAnchor = sIdx.takeIf { it >= 0 }
                ?.let { handleAnchor(it, a.charStart - page.slices[it].charStart, true) }
            val endAnchor = eIdx.takeIf { it >= 0 }
                ?.let { handleAnchor(it, a.charEnd - page.slices[it].charStart, false) }
            if (startAnchor != null) {
                val lineH = with(density) {
                    (prefs.fontSize * prefs.lineHeightRatio).dp.toPx()
                }
                val embed = with(density) { 2.dp.toPx() }
                val selTop = startAnchor.y - lineH - embed
                val selBottom = endAnchor?.y ?: startAnchor.y
                NovelAnnotationBubble(
                    topLeft = bubbleTopLeft(selTop, selBottom, startAnchor.x, 1),
                    mode = 1,
                    active = a,
                    selText = a.snippet,
                    theme = theme,
                    onMeasuredHeight = { bubbleMeasuredH = it },
                    onPickColor = { color -> callbacks.onUpdateAnnotation(a.id, color, null) },
                    onNote = { callbacks.onOpenNote(a.snippet, a.id, null) },
                    onCopy = { clipboard.setText(AnnotatedString(a.snippet)) },
                    onAdjust = {
                        callbacks.onBeginAdjust(
                            NovelSelection(startAbs = a.charStart, endAbs = a.charEnd),
                        )
                    },
                    onDelete = { callbacks.onDeleteAnnotation(a.id) },
                )
            }
        }
    }
}

/** M6.3-A 选区手柄：竖线 + 圆点，拖动更新选区端点。 */
@Composable
private fun SelectionHandle(anchor: Offset) {
    val tint = Color(0xFF2196F3)
    // 微信读书样式：无竖线的圆点，圆心即 anchor（所在行行底下方，两圆点同线）。
    // 纯视觉组件：拖动手势由页面 root 的拦截层统一接管。
    Box(
        modifier = Modifier
            .offset { IntOffset((anchor.x - 7.dp.toPx()).roundToInt(), (anchor.y - 7.dp.toPx()).roundToInt()) }
            .size(14.dp)
            .background(tint, CircleShape),
    )
}

/** M6.3-A 标注气泡：新建 = 四色 + 笔记 / 复制；编辑 = 四色 / 笔记 / 调整 / 删除。
 *  M6.3 优化：去掉"背景"（样式切换）与"关闭"按钮（点空白 / 返回键关闭）。 */
@Composable
private fun NovelAnnotationBubble(
    topLeft: Offset,
    mode: Int,
    active: AnnotationRecord?,
    selText: String,
    theme: NovelTheme,
    onMeasuredHeight: (Float) -> Unit = {},
    onPickColor: (String) -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onAdjust: () -> Unit,
    onDelete: () -> Unit,
) {
    val editing = mode == 1
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = panelColor(theme),
        border = BorderStroke(1.dp, novelOutline(theme)),
        modifier = Modifier
            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
            .width(200.dp)
            .onSizeChanged { onMeasuredHeight(it.height.toFloat()) }
            .pointerInput(Unit) {
                // 气泡自身消费所有点击，避免透传翻页
                detectTapGestures {}
            },
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NovelHighlightColors.all.forEach { (id, color) ->
                    val selected = editing && active?.color == id
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(color, CircleShape)
                            .clip(CircleShape)
                            .clickable { onPickColor(id) }
                            .let {
                                if (selected) {
                                    it.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    it
                                }
                            },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "笔记",
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.text,
                    modifier = Modifier.clickable(onClick = onNote),
                )
                Text(
                    text = "复制",
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.text,
                    modifier = Modifier.clickable(onClick = onCopy),
                )
                if (editing) {
                    Text(
                        text = "调整",
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.text,
                        modifier = Modifier.clickable(onClick = onAdjust),
                    )
                    Text(
                        text = "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable(onClick = onDelete),
                    )
                }
            }
            if (editing) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = selText.take(40),
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** M6.3 优化：笔记编辑改为居中卡片弹窗——遮罩点击/返回键关闭，自动聚焦，键盘随 ime 上移。 */
@Composable
private fun NovelNotePanel(
    state: NovelNotePanelState,
    theme: NovelTheme,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(state) { mutableStateOf(state.initial ?: "") }
    val focusRequester = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .imePadding()
            .pointerInput(Unit) {
                // 点击遮罩关闭（无涟漪反馈）；滚动/拖动不透传正文
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = panelColor(theme),
            border = BorderStroke(1.dp, novelOutline(theme)),
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 480.dp)
                .clickable(enabled = false) {},
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (state.annotationId != null) "编辑笔记" else "写笔记",
                        style = MaterialTheme.typography.titleSmall,
                        color = theme.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "关闭",
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.secondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.snippet.take(60),
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = theme.text),
                    placeholder = { Text("写下想法…", style = MaterialTheme.typography.bodyMedium) },
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                ) {
                    if (state.annotationId != null) {
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onDelete)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.secondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Text(
                        text = "保存",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSave(text) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
    LaunchedEffect(state) {
        withTimeoutOrNull(600) { focusRequester.requestFocus() }
    }
}

/** M6.2-B：EPUB 图片切片渲染；解码失败显示占位。 */
@Composable
private fun NovelPageImage(
    path: String,
    heightPx: Int,
    theme: NovelTheme,
) {
    val density = LocalDensity.current
    val heightDp = with(density) { heightPx.coerceAtLeast(1).toDp() }
    // marker 存的是 filesDir 相对路径（epub_images/{bookId}/xxx），渲染时解析为绝对路径。
    val context = LocalContext.current
    val absolutePath = remember(path) { File(context.filesDir, path).absolutePath }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = absolutePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(absolutePath)
                if (!file.exists()) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1440) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(absolutePath, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = "[插图]",
                style = MaterialTheme.typography.labelMedium,
                color = theme.secondary,
            )
        }
    }
}

/** 双页 spread：挖孔避让挂外层整体 padding（两半页等宽对称），半页仅承担上下留白；落单侧补空保持书页感。 */
@Composable
private fun NovelSpreadContent(
    left: NovelTextPage?,
    right: NovelTextPage?,
    prefs: NovelReadingPrefs,
    theme: NovelTheme,
    fontFamily: FontFamily?,
    outerPadding: PaddingValues,
    contentPadding: PaddingValues,
    annotations: List<AnnotationRecord> = emptyList(),
    selection: NovelSelection? = null,
    bubble: NovelBubble? = null,
    bubbleAnnotation: AnnotationRecord? = null,
    callbacks: NovelPageCallbacks? = null,
    dragging: Boolean = false,
    dragSide: String = "",
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(outerPadding),
        horizontalArrangement = Arrangement.spacedBy(NOVEL_SPREAD_GUTTER),
    ) {
        if (left != null) {
            NovelPageContent(
                page = left,
                prefs = prefs,
                theme = theme,
                fontFamily = fontFamily,
                contentPadding = contentPadding,
                modifier = Modifier.weight(1f),
                annotations = annotations,
                selection = selection,
                bubble = bubble,
                bubbleAnnotation = bubbleAnnotation,
                callbacks = callbacks,
                dragging = dragging,
                dragSide = dragSide,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (right != null) {
            NovelPageContent(
                page = right,
                prefs = prefs,
                theme = theme,
                fontFamily = fontFamily,
                contentPadding = contentPadding,
                modifier = Modifier.weight(1f),
                annotations = annotations,
                selection = selection,
                bubble = bubble,
                bubbleAnnotation = bubbleAnnotation,
                callbacks = callbacks,
                dragging = dragging,
                dragSide = dragSide,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NovelTopBar(
    title: String,
    chapterTitle: String,
    theme: NovelTheme,
    onBack: () -> Unit,
    onBookmarks: () -> Unit,
    onAnnotations: () -> Unit,
    onChapterList: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(top = 12.dp, start = 12.dp, end = 12.dp)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(chromeColor(theme))
            .border(1.dp, novelOutline(theme), RoundedCornerShape(28.dp)),
            ) {
            Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            ) {
            IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = theme.text)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onBookmarks) {
                Icon(Icons.Filled.BookmarkBorder, contentDescription = "书签", tint = theme.text)
            }
            IconButton(onClick = onAnnotations) {
                Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "注释", tint = theme.text)
            }
            IconButton(onClick = onChapterList) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "目录", tint = theme.text)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "排版", tint = theme.text)
            }
        }
    }
}

@Composable
private fun NovelBottomBar(
    chapterIndex: Int,
    chapterCount: Int,
    pageLabel: String,
    fraction: Float,
    theme: NovelTheme,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(chromeColor(theme))
            .border(1.dp, novelOutline(theme), RoundedCornerShape(28.dp)),
            ) {
            Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "第 ${chapterIndex + 1}/$chapterCount 章",
                style = MaterialTheme.typography.labelMedium,
                color = theme.text,
            )
            BoxWithConstraints(
                modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(theme.text.copy(alpha = 0.15f)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(theme.text.copy(alpha = 0.6f)),
                )
            }
            Text(
                text = pageLabel,
                style = MaterialTheme.typography.labelMedium,
                color = theme.text,
            )
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "搜索", tint = theme.text)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "排版", tint = theme.text)
            }
        }
    }
}

@Composable
private fun NovelChapterList(
    visible: Boolean,
    chapters: List<NovelChapterRecord>,
    currentIndex: Int,
    theme: NovelTheme,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentIndex.coerceIn(0, (chapters.lastIndex).coerceAtLeast(0)),
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 } +
            scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(120, easing = FastOutLinearInEasing)) +
            slideOutVertically(tween(160)) { it / 3 } + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // 点击遮罩关闭（无涟漪反馈）
                        detectTapGestures { onDismiss() }
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(panelColor(theme))
                    .border(1.dp, novelOutline(theme), RoundedCornerShape(20.dp)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("目录", style = MaterialTheme.typography.titleMedium, color = theme.text)
                            Text(
                                text = "${chapters.size} 章 · 当前第 ${currentIndex + 1} 章",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.secondary,
                            )
                        }
                        Text(
                            text = "关闭",
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.secondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                    ) {
                        items(chapters, key = { it.index }) { chapter ->
                            val selected = chapter.index == currentIndex
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(chapter.index) }
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        else Color.Transparent,
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = "${chapter.index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.primary else theme.secondary,
                                    modifier = Modifier.width(32.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) MaterialTheme.colorScheme.primary else theme.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "约 ${chapter.charCount} 字符",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = theme.secondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class NovelSettingOption(val id: String, val label: String)

/** M6.3-B：面板底部卡通用头部（标题 + 副标题 + 关闭）。 */
@Composable
private fun NovelPanelHeader(
    title: String,
    subtitle: String,
    theme: NovelTheme,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = theme.text)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = theme.secondary,
            )
        }
        Text(
            text = "关闭",
            style = MaterialTheme.typography.labelMedium,
            color = theme.secondary,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** M6.3-B 面板底部卡通用容器（遮罩 + 底部玻璃卡，视觉模式同 NovelChapterList）。 */
@Composable
private fun NovelListPanelScaffold(
    visible: Boolean,
    theme: NovelTheme,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 } +
            scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(120, easing = FastOutLinearInEasing)) +
            slideOutVertically(tween(160)) { it / 3 } + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // 点击遮罩关闭（无涟漪反馈）
                        detectTapGestures { onDismiss() }
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(panelColor(theme))
                    .border(1.dp, novelOutline(theme), RoundedCornerShape(20.dp)),
            ) {
                content()
            }
        }
    }
}

/** M6.3-B 注释浏览器：本书全部划线/笔记，点击跳转，可删除。 */
@Composable
private fun NovelAnnotationList(
    visible: Boolean,
    annotations: List<AnnotationRecord>,
    chapters: List<NovelChapterRecord>,
    theme: NovelTheme,
    onPick: (AnnotationRecord) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    NovelListPanelScaffold(visible = visible, theme = theme, onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            NovelPanelHeader(
                title = "注释",
                subtitle = "${annotations.size} 条划线与笔记",
                theme = theme,
                onDismiss = onDismiss,
            )
            if (annotations.isEmpty()) {
                Text(
                    text = "还没有划线或笔记\n长按正文即可划选",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                ) {
                    items(annotations, key = { it.id }) { a ->
                        val chapterTitle = chapters.getOrNull(a.chapterIdx)?.title
                            ?: "第 ${a.chapterIdx + 1} 章"
                        val hasNote = a.note != null
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(a) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            // 色块：标注颜色
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        NovelHighlightColors.colorOf(a.color),
                                        CircleShape,
                                    ),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            ) {
                                Text(
                                    text = a.snippet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = theme.text,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = (if (hasNote) "❖ ${a.note!!.take(24)} · " else "") + chapterTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (hasNote) {
                                Icon(
                                    Icons.Filled.EditNote,
                                    contentDescription = "笔记",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(onClick = { onDelete(a.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "删除",
                                    tint = theme.secondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** M6.3-B 书签列表：点击跳转，可删除。 */
@Composable
private fun NovelBookmarkList(
    visible: Boolean,
    bookmarks: List<BookmarkRecord>,
    chapters: List<NovelChapterRecord>,
    theme: NovelTheme,
    onAdd: () -> Unit,
    onPick: (BookmarkRecord) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    NovelListPanelScaffold(visible = visible, theme = theme, onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    NovelPanelHeader(
                        title = "书签",
                        subtitle = "${bookmarks.size} 个书签",
                        theme = theme,
                        onDismiss = onDismiss,
                    )
                }
                Text(
                    text = "＋ 当前位置",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onAdd)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            if (bookmarks.isEmpty()) {
                Text(
                    text = "还没有书签\n顶栏书签按钮即可添加当前位置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                ) {
                    items(bookmarks, key = { it.id }) { b ->
                        val chapterTitle = chapters.getOrNull(b.chapterIdx)?.title
                            ?: "第 ${b.chapterIdx + 1} 章"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(b) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = "书签",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            ) {
                                Text(
                                    text = b.snippet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = theme.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = chapterTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onDelete(b.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "删除",
                                    tint = theme.secondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** M6.3-B 搜索结果条目。 */
private data class NovelSearchHit(
    val chapterIdx: Int,
    val charPos: Int,
    val context: String,   // 命中词前后文
    val matchLen: Int,
    val ctxOffset: Int,    // context 在原章节文本中的起始偏移（高亮定位用）
)

/** M6.3-B 书内全文搜索面板：输入关键字，逐章匹配，点击跳转。 */
@Composable
private fun NovelSearchPanel(
    visible: Boolean,
    bookId: String,
    repository: LibraryRepository,
    chapters: List<NovelChapterRecord>,
    theme: NovelTheme,
    onPick: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var keyword by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<NovelSearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchedFor by remember { mutableStateOf("") }

    // 面板打开且进入动画完成后自动聚焦输入框 → 输入法自动弹出
    LaunchedEffect(visible) {
        if (visible) {
            kotlinx.coroutines.delay(260)
            runCatching { focusRequester.requestFocus() }
        }
    }

    fun doSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        // 点击搜索 / 键盘搜索键后收起输入法，让结果列表完整可见
        focusManager.clearFocus()
        keyboard?.hide()
        searching = true
        scope.launch {
            val found = ArrayList<NovelSearchHit>()
            val chaptersLocal = repository.novelChapters(bookId)
            for (chapter in chaptersLocal) {
                if (found.size >= 100) break
                val text = runCatching { repository.readNovelChapter(bookId, chapter) }
                    .getOrDefault("")
                if (text.isEmpty()) continue
                var idx = text.indexOf(q, ignoreCase = true)
                while (idx >= 0 && found.size < 100) {
                    val ctxStart = (idx - 12).coerceAtLeast(0)
                    val ctxEnd = (idx + q.length + 24).coerceAtMost(text.length)
                    found += NovelSearchHit(
                        chapterIdx = chapter.index,
                        charPos = idx,
                        context = text.substring(ctxStart, ctxEnd).replace('\n', ' '),
                        matchLen = q.length,
                        ctxOffset = ctxStart,
                    )
                    idx = text.indexOf(q, idx + q.length, ignoreCase = true)
                }
            }
            hits = found
            searchedFor = q
            searching = false
        }
    }

    NovelListPanelScaffold(visible = visible, theme = theme, onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            NovelPanelHeader(
                title = "搜索",
                subtitle = if (searching) "搜索中…" else if (searchedFor.isEmpty()) "搜索本书全文" else
                    "“$searchedFor” 命中 ${hits.size} 处",
                theme = theme,
                onDismiss = onDismiss,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.text.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = theme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                BasicTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = theme.text),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch(keyword) }),
                )
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { doSearch(keyword) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            if (searching) {
                Text(
                    text = "正在搜索全书章节…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )
            } else if (hits.isEmpty()) {
                Text(
                    text = if (searchedFor.isEmpty()) "输入关键字搜索本书内容"
                    else "没有找到“$searchedFor”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                ) {
                    items(hits.size) { i ->
                        val hit = hits[i]
                        val chapterTitle = chapters.getOrNull(hit.chapterIdx)?.title
                            ?: "第 ${hit.chapterIdx + 1} 章"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(hit.chapterIdx, hit.charPos) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                // 命中词高亮的上下文：relStart = 命中位置 - 上下文起始偏移
                                val relStart = (hit.charPos - hit.ctxOffset).coerceIn(0, hit.context.length)
                                val relEnd = (relStart + hit.matchLen).coerceAtMost(hit.context.length)
                                val hlBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                Text(
                                    text = buildAnnotatedString {
                                        append(hit.context)
                                        if (relEnd > relStart) {
                                            addStyle(
                                                SpanStyle(background = hlBg),
                                                relStart,
                                                relEnd,
                                            )
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = theme.text,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = chapterTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelFontSettingRow(
    selectedId: String,
    fonts: List<Pair<String, String>>,
    importedFonts: List<com.inkvol.app.data.model.NovelFontRecord>,
    expandedKey: String?,
    onToggleExpand: (String?) -> Unit,
    onPick: (String) -> Unit,
    onImportFont: () -> Unit,
    onDeleteFont: (com.inkvol.app.data.model.NovelFontRecord) -> Unit,
) {
    val isExpanded = expandedKey == "font"
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggleExpand(if (isExpanded) null else "font") }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text("字体", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = fonts.firstOrNull { it.first == selectedId }?.second ?: "自定义",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (isExpanded) {
            Spacer(Modifier.height(6.dp))
            fonts.forEach { (id, label) ->
                val imported = importedFonts.firstOrNull { it.id == id }
                val selected = id == selectedId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent,
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onPick(id)
                                onToggleExpand(null)
                            },
                    )
                    if (selected) {
                        Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                    if (imported != null) {
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onDeleteFont(imported) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onImportFont)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("导入字体…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NovelSettingRow(
    title: String,
    selectedId: String,
    options: List<NovelSettingOption>,
    expandedKey: String?,
    key: String,
    onToggleExpand: (String?) -> Unit,
    onPick: (String) -> Unit,
) {
    val isExpanded = expandedKey == key
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    if (options.size > 2) {
                        onToggleExpand(if (isExpanded) null else key)
                    } else {
                        val idx = options.indexOfFirst { it.id == selectedId }
                        val next = if (idx < options.lastIndex) options[idx + 1] else options.first()
                        onPick(next.id)
                    }
                }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = options.firstOrNull { it.id == selectedId }?.label ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (isExpanded) {
            Spacer(Modifier.height(6.dp))
            options.forEach { option ->
                val selected = option.id == selectedId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onPick(option.id)
                            onToggleExpand(null)
                        }
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent,
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelQuickPanel(
    visible: Boolean,
    prefs: NovelReadingPrefs,
    doublePageMode: String,
    onSetDoublePageMode: (String) -> Unit,
    fonts: List<Pair<String, String>>,
    importedFonts: List<com.inkvol.app.data.model.NovelFontRecord>,
    theme: NovelTheme,
    onImportFont: () -> Unit,
    onDeleteFont: (com.inkvol.app.data.model.NovelFontRecord) -> Unit,
    onPrefsChange: (NovelReadingPrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    var expandedKey by remember { mutableStateOf<String?>(null) }
    val fontSizeOptions = listOf(16, 18, 20, 22, 24).map { NovelSettingOption("$it", "${it}sp") }
    val lineHeightOptions = listOf(1.2f, 1.35f, 1.5f, 1.65f, 1.8f)
        .map { NovelSettingOption("%.2f".format(it), "${"%.2f".format(it)}×") }
    val letterSpacingOptions = listOf(0, 1, 2, 4).map { NovelSettingOption("$it", "${it}sp") }
    val paragraphOptions = listOf(4, 8, 12, 16, 20).map { NovelSettingOption("$it", "${it}dp") }
    val indentOptions = listOf(0, 1, 2, 3, 4).map { NovelSettingOption("$it", "$it 字") }
    val paddingOptions = listOf(16, 24, 32, 40).map { NovelSettingOption("$it", "${it}dp") }
    val themeOptions = listOf(
        NovelSettingOption(SettingsKeys.NOVEL_THEME_PAPER, "纸白"),
        NovelSettingOption(SettingsKeys.NOVEL_THEME_SEPIA, "米黄"),
        NovelSettingOption(SettingsKeys.NOVEL_THEME_GREEN, "护眼绿"),
        NovelSettingOption(SettingsKeys.NOVEL_THEME_DARK, "墨黑"),
    )
    val pagingOptions = listOf(
        NovelSettingOption(SettingsKeys.NOVEL_PAGE_HORIZONTAL, "左右翻页"),
        NovelSettingOption(SettingsKeys.NOVEL_PAGE_VERTICAL, "上下翻页"),
    )
    val doublePageOptions = listOf(
        NovelSettingOption(SettingsKeys.DOUBLE_PAGE_AUTO, "宽屏自动"),
        NovelSettingOption(SettingsKeys.DOUBLE_PAGE_ALWAYS, "始终双页"),
        NovelSettingOption(SettingsKeys.DOUBLE_PAGE_OFF, "关闭"),
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 } +
            scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(120, easing = FastOutLinearInEasing)) +
            slideOutVertically(tween(160)) { it / 3 } + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // 点击遮罩关闭（无涟漪反馈）
                        detectTapGestures { onDismiss() }
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(panelColor(theme))
                    .border(1.dp, novelOutline(theme), RoundedCornerShape(20.dp)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("小说排版", style = MaterialTheme.typography.titleMedium, color = theme.text)
                            Text(
                                "字体 / 字号 / 行距 / 字距 / 段距 / 缩进 / 边距 / 主题 / 翻页 / 双页",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.secondary,
                            )
                        }
                        Text(
                            "关闭",
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.secondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    NovelFontSettingRow(
                        selectedId = prefs.fontId,
                        fonts = fonts,
                        importedFonts = importedFonts,
                        expandedKey = expandedKey,
                        onToggleExpand = { expandedKey = it },
                        onPick = { onPrefsChange(prefs.copy(fontId = it)) },
                        onImportFont = onImportFont,
                        onDeleteFont = onDeleteFont,
                    )
                    Spacer(Modifier.height(8.dp))
                    NovelSettingRow(
                        title = "字号",
                        selectedId = "${prefs.fontSize}",
                        options = fontSizeOptions,
                        expandedKey = expandedKey,
                        key = "fontSize",
                        onToggleExpand = { expandedKey = it },
                        onPick = { onPrefsChange(prefs.copy(fontSize = it.toIntOrNull() ?: prefs.fontSize)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    NovelSettingRow(
                        title = "行距",
                        selectedId = "%.2f".format(prefs.lineHeightRatio),
                        options = lineHeightOptions,
                        expandedKey = expandedKey,
                        key = "lineHeight",
                        onToggleExpand = { expandedKey = it },
                        onPick = { onPrefsChange(prefs.copy(lineHeightRatio = it.toFloatOrNull() ?: prefs.lineHeightRatio)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    NovelSettingRow(
                        title = "字距",
                        selectedId = "${prefs.letterSpacing}",
                        options = letterSpacingOptions,
                        expandedKey = expandedKey,
                        key = "letterSpacing",
                        onToggleExpand = { expandedKey = it },
                        onPick = { onPrefsChange(prefs.copy(letterSpacing = it.toIntOrNull() ?: prefs.letterSpacing)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    NovelSettingRow(
                        title = "段距",
                        selectedId = "${prefs.paragraphSpacing}",
                        options = paragraphOptions,
                        expandedKey = expandedKey,
                        key = "paragraph",
                        onToggleExpand = { expandedKey = it },
                        onPick = { onPrefsChange(prefs.copy(paragraphSpacing = it.toIntOrNull() ?: prefs.paragraphSpacing)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    NovelSettingRow(
                        title = "缩进",
                        selectedId = "${prefs.indentChars}",
                        options = indentOptions,
                        expandedKey = expandedKey,
                        key = "indent",
                        onToggleExpand = { expandedKey = it },
                        onPick = { onPrefsChange(prefs.copy(indentChars = it.toIntOrNull() ?: prefs.indentChars)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    NovelSettingRow(
                        title = "边距",
                        selectedId = "${prefs.pagePadding}",
                        options = paddingOptions,
                        expandedKey = expandedKey,
                        key = "padding",
                        onToggleExpand = { expandedKey = it },
                        onPick = { onPrefsChange(prefs.copy(pagePadding = it.toIntOrNull() ?: prefs.pagePadding)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    NovelSettingRow(
                        title = "主题",
                        selectedId = prefs.theme,
                        options = themeOptions,
                        expandedKey = expandedKey,
                        key = "theme",
                        onToggleExpand = { expandedKey = it },
                        onPick = { onPrefsChange(prefs.copy(theme = it)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    NovelSettingRow(
                        title = "翻页",
                        selectedId = prefs.pagingMode,
                        options = pagingOptions,
                        expandedKey = expandedKey,
                        key = "paging",
                        onToggleExpand = { expandedKey = it },
                        onPick = { onPrefsChange(prefs.copy(pagingMode = it)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    // 双页为全局档（独立键），不写本书偏好；上下翻页模式下档位保留但不生效。
                    NovelSettingRow(
                        title = "双页",
                        selectedId = doublePageMode,
                        options = doublePageOptions,
                        expandedKey = expandedKey,
                        key = "doublePage",
                        onToggleExpand = { expandedKey = it },
                        onPick = onSetDoublePageMode,
                    )
                }
            }
        }
    }
}
