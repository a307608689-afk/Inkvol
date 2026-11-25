@file:OptIn(ExperimentalPageCurlApi::class)

package com.inkvol.app.ui.novel

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusable
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.zIndex
import com.inkvol.app.data.model.AnnotationRecord
import com.inkvol.app.data.model.BookmarkRecord
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.model.NovelChapterRecord
import com.inkvol.app.data.model.NovelReadingPrefs
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.data.settings.SettingsRepository
import com.inkvol.app.ui.components.LibraryStatusBar
import com.inkvol.app.ui.components.ReaderOpenRect
import com.inkvol.app.ui.components.ReaderSurfaceAlive
import com.inkvol.app.ui.theme.InkvolTheme
import com.inkvol.pagecurl.ExperimentalPageCurlApi
import com.inkvol.pagecurl.config.PageCurlConfig
import com.inkvol.pagecurl.config.rememberPageCurlConfig
import com.inkvol.pagecurl.page.PageCurl
import com.inkvol.pagecurl.page.rememberPageCurlState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class NovelTheme(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val dark: Boolean,
)

/** 平移/覆盖档点击翻页动画：短促一页过渡；连点时上一击被瞬移补完，只有最后一击的动画会播完。 */
private val NOVEL_FLIP_SPEC: AnimationSpec<Float> = tween(160, easing = FastOutSlowInEasing)

/** 覆盖档专用：盖入语义比平移更重，稍长一点才有「纸张盖过来」的分量感。 */
private val NOVEL_COVER_SPEC: AnimationSpec<Float> = tween(240, easing = FastOutSlowInEasing)

/** 跨章过渡（快照层）：平移/覆盖档 —— 整章内容跨度，比章内一页动画略长。 */
private val NOVEL_CHAPTER_FLIP_SPEC: AnimationSpec<Float> = tween(320, easing = FastOutSlowInEasing)

/** 跨章过渡（快照层）：仿真档卷曲 —— 与库内 450ms 卷曲观感对齐但略快。 */
private val NOVEL_CHAPTER_CURL_SPEC: AnimationSpec<Float> = tween(380, easing = FastOutSlowInEasing)

/**
 * M6-G 跨章过渡（方案 A：快照过渡层）。
 *
 * 章末翻页瞬间把当前整屏画到一张位图（View.draw 一次性软快照，平时零开销），
 * 照旧切章；等新章排版并落位后，用快照按当前动画档退场（平移滑出 / 被覆盖 /
 * 仿真卷走），新内容在下方实时渲染露出。抓图失败时不启用过渡，退化为瞬时切换。
 *
 * 这样跨章动画完全不碰分页/页号/锚定/选区链，失败自动降级。
 */
private class NovelChapterFlip(
    /** true = 去下一章首页（向后翻）；false = 回上一章末页（向前翻回）。 */
    val forward: Boolean,
    /** 上下翻页模式：位移用纵向走向，cover/curl 回退为平移。 */
    val vertical: Boolean,
    /** 实际播放档位：instant / slide / cover / curl（vertical 时只会是 slide）。 */
    val mode: String,
    val snapshot: ImageBitmap,
) {
    var started = false
    val progress = Animatable(0f)
}

/**
 * 跨章仿真过渡：旧页快照按「纸张卷起」退场。
 *
 * 简化模型（垂直折痕 + 纸背带 + 折痕根部投影），0.38s 一闪而过：
 * 观感与库内卷曲一致，但不追求条带级弧面细节（跨章只播一次，新内容已在下方实时渲染）。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChapterCurl(
    snapshot: ImageBitmap,
    progress: Float,
    forward: Boolean,
    paper: Color,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val t = progress.coerceIn(0f, 1f)
    if (t <= 0.001f) {
        drawImage(snapshot)
        return
    }
    // 折痕位置：forward 从右缘推进到左缘；backward 从左缘推进到右缘。
    val foldX = if (forward) w * (1f - t) else w * t
    // 未翻起的静止部分照常显示内容
    if (forward) {
        if (foldX > 0f) {
            clipRect(left = 0f, top = 0f, right = foldX, bottom = h) { drawImage(snapshot) }
        }
    } else {
        if (foldX < w) {
            clipRect(left = foldX, top = 0f, right = w, bottom = h) { drawImage(snapshot) }
        }
    }
    // 卷起带（纸背）：宽度取翻起区的 72%，从折痕朝外
    val flipped = if (forward) w - foldX else foldX
    if (flipped <= 0.5f) return
    val backW = (flipped * 0.72f).coerceAtMost(w)
    val backLeft = if (forward) foldX - backW else foldX
    val backRight = backLeft + backW
    // 1) 折痕根部投影（落在静止页上）
    val shadowW = (flipped * 0.22f + 6.dp.toPx()).coerceAtMost(w * 0.5f)
    drawRect(
        brush = if (forward) {
            Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                startX = foldX - shadowW,
                endX = foldX,
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
                startX = foldX,
                endX = foldX + shadowW,
            )
        },
        topLeft = Offset(if (forward) foldX - shadowW else foldX, 0f),
        size = Size(shadowW, h),
    )
    // 2) 纸背带本体
    drawRect(
        color = paper,
        topLeft = Offset(backLeft, 0f),
        size = Size(backW, h),
    )
    // 3) 弧面明暗：靠折痕亮、外缘暗（纸背不透光）
    drawRect(
        brush = if (forward) {
            Brush.horizontalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.20f), Color.Transparent),
                startX = backLeft,
                endX = backRight,
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.20f)),
                startX = backLeft,
                endX = backRight,
            )
        },
        topLeft = Offset(backLeft, 0f),
        size = Size(backW, h),
    )
    // 4) 卷起带外缘投影（纸张离开静止页的浮起感）
    val edgeX = if (forward) backLeft else backRight
    val edgeShadowW = 10.dp.toPx()
    drawRect(
        brush = if (forward) {
            Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                startX = edgeX - edgeShadowW,
                endX = edgeX,
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent),
                startX = edgeX,
                endX = edgeX + edgeShadowW,
            )
        },
        topLeft = Offset(if (forward) edgeX - edgeShadowW else edgeX, 0f),
        size = Size(edgeShadowW, h),
    )
    // 5) 折痕高光（纸边受光）
    drawRect(
        color = Color.White.copy(alpha = 0.12f),
        topLeft = Offset(foldX - 1f, 0f),
        size = Size(2f, h),
    )
}

internal fun novelTheme(id: String): NovelTheme = when (id) {
    SettingsKeys.NOVEL_THEME_SEPIA -> NovelTheme(
        background = Color(0xFFF7EFE1),
        text = Color(0xFF3A2F26),
        secondary = Color(0xFF8D7764),
        dark = false,
    )
    SettingsKeys.NOVEL_THEME_KRAFT -> NovelTheme(
        background = Color(0xFFF0E4CB),
        text = Color(0xFF3B3025),
        secondary = Color(0xFF8A7A63),
        dark = false,
    )
    SettingsKeys.NOVEL_THEME_GREEN -> NovelTheme(
        background = Color(0xFFEAF3E5),
        text = Color(0xFF263A26),
        secondary = Color(0xFF5B7A5B),
        dark = false,
    )
    SettingsKeys.NOVEL_THEME_MIST -> NovelTheme(
        background = Color(0xFFE7EDF5),
        text = Color(0xFF242B34),
        secondary = Color(0xFF66707C),
        dark = false,
    )
    SettingsKeys.NOVEL_THEME_SAKURA -> NovelTheme(
        background = Color(0xFFFAEDEF),
        text = Color(0xFF3A2A2D),
        secondary = Color(0xFF8A7075),
        dark = false,
    )
    SettingsKeys.NOVEL_THEME_DARK -> NovelTheme(
        background = Color(0xFF121212),
        text = Color(0xFFDADADA),
        secondary = Color(0xFF9A9A9A),
        dark = true,
    )
    SettingsKeys.NOVEL_THEME_SLATE -> NovelTheme(
        background = Color(0xFF1B2027),
        text = Color(0xFFC8CDD6),
        secondary = Color(0xFF8A929E),
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

/**
 * 面板/菜单栏描边色——跟随软件全局主题色（App MaterialTheme 的 primary）。
 * alpha 0.45 在纸白/米黄上几乎看不出换色（用户 2026-11-25 反馈「阅读器颜色没跟着改」），
 * 提到 0.55 让主题色在 1dp 描边上可辨；底色仍保持中性（对比度优先）。
 */
@Composable
private fun novelOutline(theme: NovelTheme): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

/**
 * 左右边缘竖向拖动（移植自漫画 readerEdgeDrag）：左缘调亮度、右缘调色温。
 * 只在真实屏幕边缘起手且竖向主导时触发，避免大面积误触；双指出现立即让位捏合。
 * 与漫画版的唯一差异：激活前任何一帧发现事件被子层消费（LazyColumn 滚动 /
 * Pager 横向拖拽 / 手柄拦截层）即整手放弃——scroll 档边缘竖滑=正常滚动，
 * 斜滑翻页与选区手柄拖动零回归。本层声明在根 Box 手势链尾，能看到前序层消费。
 */
private fun Modifier.novelEdgeDrag(
    onDrag: (Boolean, Float, Float) -> Unit,
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
            // 子层/前序兄弟层已消费（滚动、翻页拖拽、手柄拖动）：整手放弃。
            if (change.isConsumed) {
                if (active) change.consume()
                return@awaitEachGesture
            }
            val total = change.position - down.position
            if (!active &&
                abs(total.y) > viewConfiguration.touchSlop &&
                abs(total.y) > abs(total.x) * 1.5f
            ) {
                active = true
            }
            if (active) {
                val dy = change.position.y - lastY
                if (dy != 0f) onDrag(left, dy, height)
                change.consume()
            }
            lastY = change.position.y
            if (!change.pressed) break
        }
    }
}

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

/** M6.3-E 跨章延伸选区：已冻结的完整段（章内绝对区间）。
 *  连续跨章时中间章收录为整章段，提交时每段一条标注。 */
internal data class CrossChapterSegment(
    val chapter: Int,
    val startAbs: Int,
    val endAbs: Int,
)

/** M6.3-E 跨章选区桥接：grabEnd = 拖 end 端向章末方向跨章（false = 拖 start 端向章首）。
 *  segments.first() = 跨章起点章的冻结段（pin 章），其余为途经整章。
 *  拖动端永远在当前章 selection 里动态更新，不冗余存储。 */
internal data class CrossDrag(
    val grabEnd: Boolean,
    val segments: List<CrossChapterSegment>,
)

/** M6.3-E 页锚点句柄：NovelPageContent 注册进 screen 级注册表，
 *  供全书级拦截层对「可见页」做手柄抓取判定与页边界查询。 */
internal class PageAnchorHandle(
    val charStart: Int,
    val charEnd: Int,
    /** 本页最后一个文本 slice 的 charEnd（图片段无字符锚点时 atTail 的可达上界）。 */
    val lastTextEnd: Int,
    /** (abs, isStart) -> 页内容区局部坐标；abs 不在本页返回 null。 */
    val anchor: (Int, Boolean) -> Offset?,
    /** 页 Box 的 positionInRoot（实时读取）。 */
    val origin: () -> Offset,
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
    // M6.4-D「此本书单独记忆」开关（每书独立键）：关 → 面板改动写全局默认、读取跟随全局；开 → 本书快照。
    var prefsSolo by remember(bookId) { mutableStateOf(false) }
    var importedFonts by remember(bookId) { mutableStateOf(activeSession?.fonts.orEmpty()) }
    var pendingRestoreOffset by remember(bookId) { mutableIntStateOf(activeSession?.pendingRestoreOffset ?: -1) }
    // M7 搜索定位：一次性临时高亮（章内绝对区间；打开 5s 后自动消失，章节匹配才渲染）。
    var locateHighlight by remember(bookId) { mutableStateOf<IntRange?>(null) }
    var locateHighlightChapter by remember(bookId) { mutableIntStateOf(-1) }
    LaunchedEffect(bookId) {
        val lc = activeSession?.locateChar ?: -1
        if (lc >= 0) {
            locateHighlight = lc until (lc + (activeSession?.locateLen ?: 0).coerceAtLeast(1))
            locateHighlightChapter = activeSession?.chapterIndex ?: -1
            kotlinx.coroutines.delay(5000)
            locateHighlight = null
        }
    }
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

    // M6.4 插图预览：章内插图序列 + 打开位置（charStart 定位——同一张图章内可能被多次
    // 引用，imagePath 不唯一）。previewCharStart >= 0 即预览层打开。
    var previewCharStart by remember(bookId) { mutableIntStateOf(-1) }
    var previewSaving by remember(bookId) { mutableStateOf(false) }
    var previewSavedCharStart by remember(bookId) { mutableIntStateOf(-1) }
    val chapterImages = remember(pagination) {
        pagination.blocks.mapNotNull { block ->
            block.imagePath?.let { NovelPreviewImage(it, block.charStart) }
        }
    }
    // 手势协程持有首组 lambda：预览开关经 rememberUpdatedState 中转（根 tap/拦截层都在
    // pointerInput 里，直接读 previewCharStart 会 stale）。
    val previewOpenRef by rememberUpdatedState(previewCharStart >= 0)

    // M6.3-A 标注：当前章标注列表 + 选区 / 气泡 / 笔记面板状态。
    var annotations by remember(bookId) { mutableStateOf<List<AnnotationRecord>>(emptyList()) }
    var selection by remember(bookId) { mutableStateOf<NovelSelection?>(null) }
    var bubble by remember(bookId) { mutableStateOf<NovelBubble?>(null) }
    var bubbleAnnotation by remember(bookId) { mutableStateOf<AnnotationRecord?>(null) }
    // M6-G 长按标记：拦截层长按建选区后，抬手时库 tapGesture 会把这次长按判定成点击并调
    // onCustomTap（其「选区激活 → 收起」分支会立刻收掉刚建的选区 → 长按表象失效）。
    // 置位后由 onCustomTap 消费一次点击但不收起；下个手势 down 时重置。
    var longPressFired by remember(bookId) { mutableStateOf(false) }
    var notePanel by remember(bookId) { mutableStateOf<NovelNotePanelState?>(null) }
    // M6.3-E 跨章延伸选区（声明在 jumpTo 之前：跳转清空点要引用）；
    // pendingCrossArrival 标记本次章切换由跨章拖动发起。
    var crossDrag by remember(bookId) { mutableStateOf<CrossDrag?>(null) }
    var pendingCrossArrival by remember(bookId) { mutableStateOf(false) }

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
        crossDrag = null
        pendingCrossArrival = false
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
    // 动态取色（Android 12+，全局档）：开启时透传给内层 InkvolTheme，阅读器强调色
    // （描边/图标/进度条/选区手柄/命中）跟壁纸 primary，与主界面同源；关闭时回落主题色档。
    // Android 11- 无动态分支，自动回落强调色（与顶层 InkvolTheme 的 SDK 门槛一致）。
    var dynamicColorOn by remember { mutableStateOf(false) }
    LaunchedEffect(bookId) {
        dynamicColorOn = settingsRepository.booleanOf(
            settingsRepository.snapshotPreferences(),
            SettingsKeys.DYNAMIC_COLOR,
            false,
        )
        settingsRepository.preferences.collect { snapshot ->
            dynamicColorOn = settingsRepository.booleanOf(snapshot, SettingsKeys.DYNAMIC_COLOR, false)
        }
    }
    // M6-G 小说翻页动画全局档（独立键，独立于漫画）：无动画/平移/覆盖/仿真。
    // 读取与双页档同款：先快照同步读一次，再 flow 监听即时生效（设置页改动无需重进）。
    // 缺省档 = 平移（用户 2026-11-25 定档；已存过档位的老用户不受影响）。
    var pageTransitionMode by remember { mutableStateOf(SettingsKeys.PAGE_TRANSITION_SLIDE) }
    LaunchedEffect(bookId) {
        pageTransitionMode = settingsRepository.stringOf(
            settingsRepository.snapshotPreferences(),
            SettingsKeys.NOVEL_PAGE_TRANSITION,
            SettingsKeys.PAGE_TRANSITION_SLIDE,
        )
        settingsRepository.preferences.collect { snapshot ->
            pageTransitionMode = settingsRepository.stringOf(
                snapshot,
                SettingsKeys.NOVEL_PAGE_TRANSITION,
                SettingsKeys.PAGE_TRANSITION_SLIDE,
            )
        }
    }
    // 深色跟随：App 生效深色态（APP_THEME 档 + 系统夜间模式）与跟随开关（独立全局键，缺省开）。
    // 读取与双页档同款：先快照同步读一次，再 flow 监听即时生效。
    val systemDark = isSystemInDarkTheme()
    var appThemeMode by remember { mutableStateOf("") }
    LaunchedEffect(bookId) {
        appThemeMode = settingsRepository.stringOf(
            settingsRepository.snapshotPreferences(),
            SettingsKeys.APP_THEME,
            SettingsKeys.THEME_SYSTEM,
        )
        settingsRepository.preferences.collect { snapshot ->
            appThemeMode = settingsRepository.stringOf(
                snapshot,
                SettingsKeys.APP_THEME,
                SettingsKeys.THEME_SYSTEM,
            )
        }
    }
    // "" = 快照未加载：首帧不参与跟随（用 prefs.theme 原样渲染，避免错误跟随闪变）。
    val appDark = when {
        appThemeMode.isEmpty() -> false
        appThemeMode == SettingsKeys.THEME_LIGHT -> false
        appThemeMode == SettingsKeys.THEME_DARK -> true
        else -> systemDark
    }
    var darkFollow by remember { mutableStateOf(true) }
    LaunchedEffect(bookId) {
        darkFollow = settingsRepository.booleanOf(
            settingsRepository.snapshotPreferences(),
            SettingsKeys.NOVEL_DARK_FOLLOW,
            true,
        )
        settingsRepository.preferences.collect { snapshot ->
            darkFollow = settingsRepository.booleanOf(snapshot, SettingsKeys.NOVEL_DARK_FOLLOW, true)
        }
    }
    // 边缘手势开关（移植自漫画）：左缘竖滑调系统亮度 / 右缘竖滑调色温。
    // 纵向连续（scroll）档不支持——边缘竖滑被 LazyColumn 消费，手势层自动让位。
    var novelEdgeBrightnessOn by remember { mutableStateOf(true) }
    var novelEdgeColorTempOn by remember { mutableStateOf(true) }
    LaunchedEffect(bookId) {
        val snapshot = settingsRepository.snapshotPreferences()
        novelEdgeBrightnessOn = settingsRepository.booleanOf(
            snapshot,
            SettingsKeys.NOVEL_EDGE_BRIGHTNESS,
            true,
        )
        novelEdgeColorTempOn = settingsRepository.booleanOf(
            snapshot,
            SettingsKeys.NOVEL_EDGE_COLOR_TEMP,
            true,
        )
        settingsRepository.preferences.collect { snap ->
            novelEdgeBrightnessOn = settingsRepository.booleanOf(
                snap,
                SettingsKeys.NOVEL_EDGE_BRIGHTNESS,
                true,
            )
            novelEdgeColorTempOn = settingsRepository.booleanOf(
                snap,
                SettingsKeys.NOVEL_EDGE_COLOR_TEMP,
                true,
            )
        }
    }
    // 派生不落盘：跟随开启且 App 深色 → 背景强制墨黑；prefs.theme（含 per-book 单独记忆）不被污染，
    // 回浅色自动恢复手动档。面板选浅色档会走 onPickTheme 关闭跟随（用户接管）。
    val darkFollowActive = darkFollow && appDark
    val effectiveThemeId = if (darkFollowActive) SettingsKeys.NOVEL_THEME_DARK else prefs.theme
    val theme = novelTheme(effectiveThemeId)
    val dark = theme.dark
    // 当前阅读位置锚（字符偏移）：切档/旋转重排后按它重锚，进度保存时同步更新。
    var anchorOffset by remember(bookId) { mutableIntStateOf(-1) }
    var anchoredOnce by remember(bookId) { mutableStateOf(false) }

    // 挖孔兜底记忆：设备曾上报过的最大挖孔 inset（dp）。
    // 部分平板横屏 displayCutout 全 0（挖孔被并入状态栏语义），用该值对左右补偿。
    var rememberedCutoutDp by remember { mutableStateOf(0f) }

    // 边缘手势的会话状态（不持久化，同漫画）：brightness -1 = 未接管过系统亮度。
    var novelBrightness by rememberSaveable { mutableFloatStateOf(-1f) }
    var novelColorTemp by rememberSaveable { mutableFloatStateOf(0f) }
    // HUD 是连续反馈（拖动时每帧改文案），动画只认"有无"，文案走保留变量（漫画同款）。
    var novelHudText by remember { mutableStateOf<String?>(null) }
    var novelHudHeld by remember { mutableStateOf("") }

    // 左缘拖动接管系统亮度；离开阅读器恢复系统档（-1f）。与漫画各自恢复，互不影响。
    LaunchedEffect(novelBrightness) {
        if (novelBrightness >= 0f) {
            (context as? Activity)?.window?.let { window ->
                val params = window.attributes
                params.screenBrightness = novelBrightness.coerceIn(0.05f, 1f)
                window.attributes = params
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.window?.let { window ->
                val params = window.attributes
                params.screenBrightness = -1f
                window.attributes = params
            }
        }
    }
    LaunchedEffect(novelHudText) {
        val text = novelHudText
        if (text != null) {
            novelHudHeld = text
            kotlinx.coroutines.delay(900)
            novelHudText = null
        }
    }
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
        // 一镜到底返回垫层的唯一判据：阅读器组件是否还挂着（路由状态滞后 2~3 帧不可用）
        ReaderSurfaceAlive.enter()
        val window = (context as? Activity)?.window
        // 用 decorView 取 controller，兼容部分机型；post 到 attach 后再 hide。
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // 进场先整组隐藏；之后状态栏显隐由下方「菜单栏联动」效果单点接管。
        view.post { controller?.hide(WindowInsetsCompat.Type.systemBars()) }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            ReaderSurfaceAlive.exit()
            // 插画位图缓存为会话级：离开阅读器即释放（跨书不保留）。
            NovelImageCache.clear()
            // 系统栏恢复交给宿主 closeReaderOpen 统一处理，避免双重 show 造成跳动。
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 菜单栏 ↔ 状态栏联动（沉浸）：上下菜单栏出现即唤出状态栏（透明底、图标色随 dark），
    // 菜单栏消失则连同导航栏一起收回，回到全沉浸。
    // 驱动信号用 chromeVisible（不含面板排除）：打开目录/排版/注释/搜索/书签等底部面板时
    // 状态栏保持出现，避免从菜单栏进面板时「灭一下再亮回来」。
    // 只 show 状态栏、不 show 导航栏——导航栏 inset 是底部留白的输入，唤出会触发重新分页。
    // openActive 并入 key：深链/重建进场时 chromeVisible 初值可能首帧即 true，
    // 开书动画结束那一刻重申一次当前状态（防转场把状态栏压掉）。
    LaunchedEffect(chromeVisible, openActive) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            ?: return@LaunchedEffect
        runCatching {
            if (chromeVisible) {
                // 先复位为非瞬态再 show：TRANSIENT 下的 show 会被系统当作临时唤出，
                // 状态栏几秒后自动缩回（与书架玻璃头同源的坑）。
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.hide(WindowInsetsCompat.Type.navigationBars())
            } else {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                // 整组隐藏：显示态切到 DEFAULT 后用户划出的导航栏不会自己缩回，
                // 只藏状态栏会让手势条赖在屏幕上并改掉底部留白。
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
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
        val (savedPrefs, solo) = withContext(Dispatchers.IO) {
            val snapshot = settingsRepository.snapshotPreferences()
            val soloNow = settingsRepository.booleanOf(snapshot, SettingsKeys.novelBookSoloKey(bookId), false)
            val perBook = settingsRepository.stringOf(snapshot, SettingsKeys.novelBookKey(bookId), "")
            val global = settingsRepository.stringOf(snapshot, SettingsKeys.NOVEL_GLOBAL_PREFS, "")
            // M6.4-D：单独记忆开 → 本书快照（缺则回退全局作起点）；关 → 全局跟随（本书旧快照忽略）。
            val effective = if (soloNow && perBook.isNotBlank()) perBook else global.ifBlank { perBook }
            effective to soloNow
        }
        prefsSolo = solo
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

    LaunchedEffect(prefs, prefsLoaded, prefsSolo) {
        if (!prefsLoaded) return@LaunchedEffect
        val json = kotlinx.serialization.json.Json.encodeToString(NovelReadingPrefs.serializer(), prefs)
        if (prefsSolo) {
            // 单独记忆：只写本书快照。
            settingsRepository.putString(SettingsKeys.novelBookKey(bookId), json)
        } else {
            // M6.4-D 全局跟随：面板改动写全局默认，所有未单独记忆的书下次打开自动生效。
            settingsRepository.putString(SettingsKeys.NOVEL_GLOBAL_PREFS, json)
        }
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

    // 深色跟随首次提示：进入深色（手动切换或跟随系统均算）且跟随开启时，首次打开阅读器
    // 弹一次居中卡提示；落盘标记后任何情况下不再出现。快照未加载/跟随未激活不弹。
    var darkFollowHintOpen by remember(bookId) { mutableStateOf(false) }
    LaunchedEffect(bookId, prefsLoaded, appThemeMode.isEmpty(), darkFollowActive) {
        if (!prefsLoaded || appThemeMode.isEmpty() || !darkFollowActive) return@LaunchedEffect
        val shown = withContext(Dispatchers.IO) {
            settingsRepository.booleanOf(
                settingsRepository.snapshotPreferences(),
                SettingsKeys.NOVEL_DARK_FOLLOW_HINT,
                false,
            )
        }
        if (!shown) {
            darkFollowHintOpen = true
            settingsRepository.putBoolean(SettingsKeys.NOVEL_DARK_FOLLOW_HINT, true)
        }
    }

    InkvolTheme(darkTheme = dark, dynamicColor = dynamicColorOn) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(theme.background)) {
            val availableWidth = maxWidth
            val availableHeight = maxHeight
            // 挖孔避让：正文渲染与分页预算共用同一套派生留白（基准与挖孔 insets 取大者）。
            val layoutDirection = LocalLayoutDirection.current
            val cutoutPad = WindowInsets.displayCutout.asPaddingValues()
            // M6.3-E-H 03 修复：底部避让并入系统手势条/三键导航栏——横屏双页手势条
            // 在侧边无感，切竖屏单页后手势条落到底部压住最后一行正文。
            val navBarsPad = WindowInsets.navigationBars.asPaddingValues()
            val rawStartDp = cutoutPad.calculateStartPadding(layoutDirection)
            val rawEndDp = cutoutPad.calculateEndPadding(layoutDirection)
            val rawTopDp = cutoutPad.calculateTopPadding()
            val rawBottomDp = cutoutPad.calculateBottomPadding()
                .coerceAtLeast(navBarsPad.calculateBottomPadding())
            val rawMaxDp = rawStartDp.coerceAtLeast(rawEndDp).coerceAtLeast(rawTopDp).coerceAtLeast(rawBottomDp)
            val isLandscape = maxWidth > maxHeight
            // 小说双页：横向翻页 + (始终 or 横屏)；上下翻页/纵向连续不生效（同漫画条漫不双页）。
            val useDouble = prefs.pagingMode != SettingsKeys.NOVEL_PAGE_VERTICAL &&
                prefs.pagingMode != SettingsKeys.NOVEL_PAGE_SCROLL &&
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
            // M6.3-E-H 03 兜底：全屏沉浸下 navigationBars insets 可能为 0，
            // 基准从 24dp 提到 32dp 覆盖手势条余量（仅竖屏需要——竖屏手势条落底部）。
            // 横屏手势条/挖孔在侧边，底部无遮挡风险，基准与 top 对齐（24dp），
            // 消除横屏下「下留空恒比上留空多 8dp」的不对称（EPUB/TXT 同链路受益）；
            // insets 非零（横屏三键导航等）仍按 cutoutSafeDp 取大，不遮挡。
            val bottomPadDp = if (isLandscape) {
                cutoutSafeDp(24.dp, rawBottomDp)
            } else {
                cutoutSafeDp(32.dp, rawBottomDp)
            }
            android.util.Log.d(
                "CROSSDRAG",
                "pad bottom=$bottomPadDp rawCutout=${cutoutPad.calculateBottomPadding()} " +
                    "rawNav=${navBarsPad.calculateBottomPadding()} landscape=$isLandscape",
            )
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

            // M6.3-E-H 05：keys 补 heightPx——旋转/切双页时屏幕高与 insets（手势条/挖孔）
            // 更新存在先后帧，宽键已变而高度后变时旧实现不重排，分页沿用旧高度导致
            // 竖屏渲染底部一行被裁。heightPx 作为分页真实输入入键（含 insets 变化传导），
            // 切屏立即按新尺寸重分页。
            LaunchedEffect(prefsLoaded, chapterText, prefs, fontFamily, paginateWidthPx, useDouble, heightPx, openActive) {
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
                            // 插图比例：marker 是相对路径，量尺寸必须挂 filesDir 根。
                            imageBaseDir = context.filesDir,
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
                        imageBaseDir = context.filesDir,
                    )
                }
                pagination = full
            }

            val pages = pagination.pages
            // 双页 spread 组合：纯展示层两两配对（末页落单），进度锚定仍按字符偏移。
            val spreads = remember(pages, useDouble) {
                if (useDouble) pages.chunked(2) else emptyList()
            }
            // 首帧就落在目标页：pagerState 若从第 0 页起步，锚定 effect 要等首帧之后才
            // settlePage，一镜到底交接瞬间会先露出章首页（第 0 页）、再跳到实际位置——
            // 用户感知为「先显示章节、再显示具体小说内容」。这里先用预打开会话里的目标
            // 偏移在 firstPagination 上求页号；求不到（restore < 0 / 半截快排页覆盖不到）
            // 回落 0，仍由下方锚定 effect 兜底纠正。
            val initialPageIndex = remember(bookId) {
                val restore = activeSession?.pendingRestoreOffset ?: -1
                val firstPages = activeSession?.firstPagination?.pages.orEmpty()
                val idx = when {
                    restore < 0 || firstPages.isEmpty() -> -1
                    useDouble -> firstPages.chunked(2).indexOfFirst { spread ->
                        spread.first().charStart <= restore && restore < spread.last().charEnd
                    }
                    else -> firstPages.indexOfFirst { page ->
                        page.charStart <= restore && restore < page.charEnd
                    }
                }
                idx.coerceAtLeast(0)
            }
            val pagerState = rememberPagerState(initialPage = initialPageIndex) {
                (if (useDouble) spreads.size else pages.size).coerceAtLeast(1)
            }

            // M6.3-A：翻页 / 换章 / 排版变化时清空选区与气泡。
            // M6.3 优化：翻页计数（阅读统计 pages_read）。
            LaunchedEffect(pagerState.currentPage) {
                sessionPages++
            }

            // M6-G 跨章过渡（方案 A：快照过渡层）：状态 + 统一入口。
            // 非空期间旧页快照冻结在屏上、新章内容整体位移（见页面容器的 graphicsLayer），
            // 新章排版并落位后播放退场动画，结束后清空。声明位置必须在根层滑动手势
            // 与各跨章调用点之前（同作用域局部变量只能先用后声明）。
            var chapterFlip by remember(bookId) { mutableStateOf<NovelChapterFlip?>(null) }
            // 切章前调用：抓图 = 当前整屏一次性软快照（View.draw），抓不到就不加过渡，
            // 退化为原来的瞬时切换（跳转类：目录/搜索/恢复进度不经过这里）。
            val beginChapterFlip: (Boolean) -> Unit = label@{ forward ->
                if (chapterFlip != null) return@label
                val mode = pageTransitionMode
                if (mode == SettingsKeys.PAGE_TRANSITION_INSTANT) return@label
                // M6.5 纵向连续档同上下翻页：跨章过渡走竖向平移（与滚动方向一致）。
                val vertical = prefs.pagingMode != SettingsKeys.NOVEL_PAGE_HORIZONTAL
                // 上下翻页没有横向卷曲/盖入语义 → 回退平移（与 flipTo 的档位回退一致）
                val effective = if (vertical) SettingsKeys.PAGE_TRANSITION_SLIDE else mode
                val snapshot = runCatching {
                    val w = view.width
                    val h = view.height
                    if (w <= 0 || h <= 0) return@runCatching null
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(bmp))
                    bmp.asImageBitmap()
                }.getOrNull() ?: return@label
                chapterFlip = NovelChapterFlip(
                    forward = forward,
                    vertical = vertical,
                    mode = effective,
                    snapshot = snapshot,
                )
            }
            // 手势协程持有首组 lambda：经 rememberUpdatedState 中转，切档/换章后仍取最新
            val currentBeginChapterFlip: (Boolean) -> Unit by rememberUpdatedState(beginChapterFlip)
            // 跨章滑动改由根层「边界越界滑动」手势统一识别（见下方 pointerInput）：
            // 旧的 pager overscroll 累计（240dp 阈值、松手不结算）已移除——阈值远大于
            // 人手一次滑动距离，且末页拖拽 pager 不动无任何反馈，实际手感就是"滑不动"。

            // M6.3 跨页续拖：选区为章节绝对 char，翻页/重排不再清空（渲染按交集天然支持跨页）；
            // 仅章 / 排版 / 字体 / 双页切换才清空。dragging/dragSide 供气泡归属与隐藏。
            var dragging by remember(bookId) { mutableStateOf(false) }
            var dragSide by remember(bookId) { mutableStateOf("") }
            val converters = remember(bookId) {
                mutableStateMapOf<Int, (Offset) -> Int?>()
            }
            // M6.3-E 全书级拦截层：各页注册锚点/边界句柄（key = page.charStart，唯一）。
            val pageAnchors = remember(bookId) {
                mutableStateMapOf<Int, PageAnchorHandle>()
            }
            // 根 Box 在窗口中的原点（拦截层手势坐标 → 窗口坐标换算）。
            var screenOrigin by remember { mutableStateOf(Offset.Zero) }
            // M6.3-E pageCallbacks 是 remember(bookId) 单次构造，闭包直接捕获 pages/spreads
            // 会 stale；经 rememberUpdatedState 读最新值（章切换/排版变化后仍正确）。
            val pagesRef by rememberUpdatedState(pages)
            val spreadsRef by rememberUpdatedState(spreads)
            val useDoubleRef by rememberUpdatedState(useDouble)
            // M6.5 纵向连续（scroll 档）：单章信息流式连续滚动容器（LazyColumn）。
            // scrollListState 为本档页位置真源；跳转类（settlePage）按它落位。
            // 列表 item 布局 = [顶部哨兵(上一章)?] + 各页 + [底部哨兵(下一章)?]，
            // 页号 ↔ item 序号换算经顶部哨兵偏移完成。
            val pagingScroll = prefs.pagingMode == SettingsKeys.NOVEL_PAGE_SCROLL
            val scrollFocusRequester = remember { FocusRequester() }
            val scrollListState = rememberLazyListState(
                initialFirstVisibleItemIndex =
                    initialPageIndex + if (chapterIndex > 0) 1 else 0,
            )
            val pagingScrollRef by rememberUpdatedState(pagingScroll)
            val scrollTopSentinelRef by rememberUpdatedState(chapterIndex > 0)
            val scrollListIndexOf: (Int) -> Int = { pageIdx ->
                pageIdx + if (scrollTopSentinelRef) 1 else 0
            }
            // 当前页 = 首可见 item 减顶部哨兵偏移（页粒度，与进度锚定 charStart 同源）。
            val scrollCurrentPageIndexOf: () -> Int = {
                (scrollListState.firstVisibleItemIndex - if (scrollTopSentinelRef) 1 else 0)
                    .coerceIn(0, (pagesRef.size - 1).coerceAtLeast(0))
            }
            // M6.5 scroll 档页数计数：pager 静止（currentPage 不变），由滚动页号驱动。
            LaunchedEffect(scrollListState.firstVisibleItemIndex) {
                if (pagingScrollRef) sessionPages++
            }
            // 当前页快照（返回书架缩略图 + 拖拽跟手 front 复用）：翻页后后台重渲染，
            // snapshotChapter/Page 标记快照对应的章/页用于新鲜度校验。
            var currentSnapshot by remember(bookId) { mutableStateOf(activeSession?.firstPageBitmap) }
            var snapshotChapter by remember(bookId) { mutableIntStateOf(-1) }
            var snapshotPage by remember(bookId) { mutableIntStateOf(-1) }
            // M6-G 仿真翻页（pagecurl 库，Apache-2.0）：curl 档横向容器整体替换 HorizontalPager，
            // 拖拽跟手/松手 snap/内置卷曲动画全部由库手势驱动。curlState 为 curl 档页位置真源，
            // 经下方镜像 effect 同步回闲置 pagerState → 下游进度/页码/锚定消费点零改动。
            val curlState = rememberPageCurlState()
            val curlActiveNow by rememberUpdatedState(
                pageTransitionMode == SettingsKeys.PAGE_TRANSITION_CURL &&
                    prefs.pagingMode != SettingsKeys.NOVEL_PAGE_VERTICAL &&
                    prefs.pagingMode != SettingsKeys.NOVEL_PAGE_SCROLL,
            )
            // M6-G curl 档镜像：库容器为页位置真源，current 变化（翻页完成/拖拽跨页）时同步
            // 闲置 pagerState → 下游进度保存/页码/锚定/统计消费点继续读 pagerState 零改动。
            LaunchedEffect(curlState.current) {
                if (curlActiveNow) {
                    pagerState.scrollToPage(curlState.current)
                }
            }
            // M6-G 跳转类统一落位入口：curl 档写 curlState（镜像 effect 回同步 pagerState），
            // 其它档直接写 pagerState。目录/搜索/恢复进度/重锚/pendingFlip 全部经此。
            val settlePage: suspend (Int) -> Unit = { page ->
                when {
                    // M6.5 scroll 档：页号 → 列表 item 序号（含顶部哨兵偏移）落位。
                    pagingScrollRef -> scrollListState.scrollToItem(scrollListIndexOf(page))
                    curlActiveNow -> curlState.snapTo(page)
                    else -> pagerState.scrollToPage(page)
                }
            }
            // M6.3 跨页续选：拦截层（restricted 手势上下文）不能直接调 suspend 的
            // scrollToPage，改设 pendingFlip 信号，由本协程执行真实翻页。
            var pendingFlip by remember(bookId) { mutableStateOf<Int?>(null) }
            LaunchedEffect(pendingFlip) {
                pendingFlip?.let { target ->
                    val size = if (useDouble) spreads.size else pages.size
                    if (size > 0) {
                        settlePage(target.coerceIn(0, size - 1))
                    }
                    pendingFlip = null
                }
            }
            // M6-G 快翻累加器（漫画 B2 同款）：连点同向每击精确 ±1 页——slide/cover 档新击
            // 先把上一击未完成的翻页瞬移补完，再对本击播一页短动画；curl 档新击瞬移到累加
            // 锚点后由库播内置卷曲动画（动画完成才推进 current，排队靠本累加器）。
            var flipTarget by remember(bookId) { mutableIntStateOf(-1) }
            var flipForward by remember(bookId) { mutableStateOf(true) }
            // 翻页完全静止（动画落定/拖拽结束/跳页完成）后清零累加器。
            // curl 档下 pagerState 不参与滚动（isScrollInProgress 恒 false）→ 不清累加器。
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.isScrollInProgress }
                    .filter { !it }
                    .collect { if (!curlActiveNow) flipTarget = -1 }
            }
            // 拖拽起手丢弃累加目标（slide/cover 档 Pager 手势有效；curl 档容器为 PageCurl，
            // 不走 pagerState.interactionSource，本 collect 天然收不到）
            LaunchedEffect(pagerState) {
                pagerState.interactionSource.interactions.collect { interaction ->
                    if (interaction is DragInteraction.Start) flipTarget = -1
                }
            }
            // 切换翻页动画档丢弃未完成累加 + 撤掉跨章过渡
            LaunchedEffect(pageTransitionMode) {
                flipTarget = -1
                chapterFlip = null
            }
            // M6.5 切换方向档（scroll ↔ pager）：两套容器的页号体系互不相通，
            // 按当前进度锚点重落位（重锚 effect 统一消化 pendingRestoreOffset）。
            LaunchedEffect(prefs.pagingMode) {
                if (anchoredOnce && anchorOffset >= 0) {
                    pendingRestoreOffset = anchorOffset
                    pendingJumpLastPage = false
                }
            }
            // 就绪等待 + 播放：新章排版完成且已落位（restore / 末页意图被锚定 effect 消费）后
            // 才开始退场动画；3s 兜底直接撤掉快照，避免加载异常时屏幕被永久冻结。
            LaunchedEffect(chapterFlip) {
                val anim = chapterFlip ?: return@LaunchedEffect
                val ready = withTimeoutOrNull(3000) {
                    snapshotFlow {
                        !paginating && pagination.pages.isNotEmpty() &&
                            pendingRestoreOffset < 0 && !pendingJumpLastPage
                    }.first { it }
                }
                if (ready == null) {
                    if (chapterFlip === anim) chapterFlip = null
                    return@LaunchedEffect
                }
                anim.started = true
                // 让新章内容先真实绘制一帧再揭开快照，避免露出空白
                withFrameNanos { }
                anim.progress.animateTo(
                    targetValue = 1f,
                    animationSpec = if (anim.mode == SettingsKeys.PAGE_TRANSITION_CURL) {
                        NOVEL_CHAPTER_CURL_SPEC
                    } else {
                        NOVEL_CHAPTER_FLIP_SPEC
                    },
                )
                if (chapterFlip === anim) chapterFlip = null
            }
            // M6-G 点击翻页统一入口：按动画档分流。跳转类（jumpTo/恢复进度/目录/搜索/
            // 跨页续选 pendingFlip）与跨章不经过这里，保持瞬时。本 lambda 每次重组重建、
            // 捕获当次组合的最新状态；点击手势经 rememberUpdatedState 中转调用
            // （pointerInput 协程持有首组 lambda，直接捕获会 stale）。
            val flipTo: (Boolean) -> Unit = { forward ->
                scope.launch {
                    val size = if (useDoubleRef) spreadsRef.size else pagesRef.size
                    if (size <= 0) return@launch
                    val curlNow = curlActiveNow
                    val trusted = flipTarget >= 0 && flipForward == forward
                    val base = (if (trusted) {
                        flipTarget
                    } else if (curlNow) {
                        // curl 档：pagerState 由镜像滞后同步，动画中不可信 → 用库真源 current
                        curlState.current
                    } else if (forward) {
                        maxOf(pagerState.currentPage, pagerState.targetPage)
                    } else {
                        minOf(pagerState.currentPage, pagerState.targetPage)
                    }).coerceIn(0, size - 1)
                    val target = base + if (forward) 1 else -1
                    if (target < 0 || target >= size) return@launch
                    flipTarget = target
                    flipForward = forward
                    val mode = pageTransitionMode
                    when {
                        // 无动画档：瞬时（旧体验）
                        mode == SettingsKeys.PAGE_TRANSITION_INSTANT -> {
                            pagerState.scrollToPage(target)
                            flipTarget = -1
                        }
                        // 仿真（curl 档）：瞬移补完到累加锚点后由库播放内置卷曲动画
                        //（450ms keyframes、可打断；动画完成才推进 current → 连点排队靠累加器）
                        curlNow -> {
                            curlState.snapTo(base)
                            if (forward) curlState.next() else curlState.prev()
                        }
                        // 平移档 / 覆盖档：上一击瞬移补完 + 本击一页短动画
                        //（覆盖档用独立 240ms spec：盖入语义比分移更重）
                        else -> {
                            if (pagerState.currentPage != base ||
                                pagerState.currentPageOffsetFraction != 0f
                            ) {
                                pagerState.scrollToPage(base)
                            }
                            pagerState.animateScrollToPage(
                                target,
                                animationSpec = if (mode == SettingsKeys.PAGE_TRANSITION_COVER) {
                                    NOVEL_COVER_SPEC
                                } else {
                                    NOVEL_FLIP_SPEC
                                },
                            )
                        }
                    }
                }
            }
            val currentFlipTo: (Boolean) -> Unit by rememberUpdatedState(flipTo)
            // M6-G curl 档分区点击逻辑（左/右翻页含跨章、中区 chrome、选区收起）——库 tap 层
            // 会消费所有点击，非 curl 档仍走根层 detectTapGestures（同一套语义两处实现）。
            // 闭包经 rememberUpdatedState 中转：rememberPageCurlConfig 的 onCustomTap 不参与
            // saveable 重建，直接捕获组合态会 stale。
            val curlCustomTapImpl by rememberUpdatedState(
                tap@ { size: IntSize, offset: Offset ->
                    // 插图预览打开：库 tap 一律消费但不动作（预览层只处理自己的关闭）
                    if (previewOpenRef) return@tap true
                    // M6-G 长按刚建选区：库 tapGesture 抬手判定点击会走到这里——消费但不收起，
                    // 让长按建立的选区/气泡保留（否则第一分支会立刻收掉 → 长按表象失效）。
                    if (longPressFired) {
                        longPressFired = false
                        return@tap true
                    }
                    if (selection != null || bubble != null) {
                        selection = null
                        bubble = null
                        crossDrag = null
                        pendingCrossArrival = false
                        true
                    } else {
                        val cur = curlState.current
                        val lastIdx = if (useDoubleRef) spreadsRef.lastIndex else pagesRef.lastIndex
                        when {
                            offset.x < size.width * 0.32f -> {
                                scope.launch {
                                    when {
                                        cur > 0 -> currentFlipTo(false)
                                        // 首页点上一页 → 上一章末页（跨章：快照过渡层）
                                        chapterIndex > 0 -> {
                                            beginChapterFlip(false)
                                            pendingJumpLastPage = true
                                            chapterIndex -= 1
                                        }
                                    }
                                }
                                true
                            }
                            offset.x > size.width * 0.68f -> {
                                scope.launch {
                                    when {
                                        // 章内 → 翻页动画档；双页末页判定用 spreads.lastIndex
                                        cur < lastIdx -> currentFlipTo(true)
                                        // 末页点下一页 → 下一章首页（跨章：快照过渡层）
                                        chapterIndex < chapters.lastIndex -> {
                                            beginChapterFlip(true)
                                            pendingJumpLastPage = false
                                            pendingRestoreOffset = 0
                                            chapterIndex += 1
                                        }
                                    }
                                }
                                true
                            }
                            else -> {
                                chromeVisible = !chromeVisible
                                true
                            }
                        }
                    }
                },
            )
            // M6-G 库配置：背面纯纸背色（不透出下层）；分区点击经 onCustomTap 全接管，
            // 库内置 tap 区关闭。闭包只转发 ref，不捕获组合态。
            val curlConfig = rememberPageCurlConfig(
                backPageColor = theme.background,
                backPageContentAlpha = 0f,
                // 手势语义改「拖拽方向」定页：任意位置起拖，向左拖=下一页、向右拖=上一页
                //（默认 StartEndDragInteraction 按起始位置定方向：右半屏起拖只能向前，
                // 右区域向右拖回上一页会手势作废）
                dragInteraction = PageCurlConfig.GestureDragInteraction(),
                dragForwardEnabled = true,
                dragBackwardEnabled = true,
                tapForwardEnabled = false,
                tapBackwardEnabled = false,
                tapCustomEnabled = true,
                onCustomTap = { size, offset ->
                    curlCustomTapImpl(size, offset)
                },
            )
            // M6-G 选区/气泡激活时禁用库拖拽（config 字段为可变状态，重组幂等赋值）：
            // 防卷曲手势抢断划词拖动手柄。禁用走 vendor 静默回弹（不取消手势协程）。
            val curlDragEnabled = curlActiveNow &&
                selection == null && bubble == null && !previewOpenRef
            curlConfig.dragForwardEnabled = curlDragEnabled
            curlConfig.dragBackwardEnabled = curlDragEnabled
            // M6.3-E 跨章触发：握把翻页目标超本章时由 flipPage 调用。
            // 首跳冻结起点章段（pin 章），连续跨章追加途经整章段；不反向跨。
            val beginOrExtendCrossDrag: (Boolean) -> Unit = label@{ end ->
                val sel = selection ?: return@label
                if (if (end) chapterIndex >= chapters.lastIndex else chapterIndex <= 0) return@label
                val cd = crossDrag
                if (cd != null && cd.grabEnd != end) return@label
                val curLen = chapterText.length
                val newSeg = if (cd == null) {
                    // 首跳：起点章冻结 [pin, 章末]（拖 end）或 [章首, pin]（拖 start）。
                    if (end) {
                        CrossChapterSegment(
                            chapterIndex,
                            minOf(sel.startAbs, sel.endAbs),
                            curLen,
                        )
                    } else {
                        CrossChapterSegment(
                            chapterIndex,
                            0,
                            maxOf(sel.startAbs, sel.endAbs),
                        )
                    }
                } else {
                    // 续跳：途经章整段收录。
                    CrossChapterSegment(chapterIndex, 0, curLen)
                }
                crossDrag = CrossDrag(
                    grabEnd = end,
                    segments = (cd?.segments ?: emptyList()) + newSeg,
                )
                pendingCrossArrival = true
                if (end) {
                    pendingRestoreOffset = 0
                    pendingJumpLastPage = false
                    chapterIndex += 1
                } else {
                    pendingRestoreOffset = Int.MAX_VALUE
                    pendingJumpLastPage = true
                    chapterIndex -= 1
                }
            }
            // M6.3-E 渲染选区派生：pin 章显示冻结段，拖动章显示动态选区。
            val crossDragNow = crossDrag
            val displaySelection: NovelSelection? = when {
                crossDragNow == null -> selection
                chapterIndex == crossDragNow.segments.first().chapter ->
                    crossDragNow.segments.first().let {
                        NovelSelection(minOf(it.startAbs, it.endAbs), maxOf(it.startAbs, it.endAbs))
                    }
                else -> selection
            }
            LaunchedEffect(
                chapterIndex,
                prefs,
                fontFamily,
                useDouble,
            ) {
                bubble = null
                bubbleAnnotation = null
                converters.clear()
                pageAnchors.clear()
                val cd = crossDrag
                if (pendingCrossArrival && cd != null) {
                    // 跨章续拖：新章选区初始化。拖 end = 章首单字符；拖 start = 极大值占位
                    // （渲染/换算经 clamp 天然安全，等拦截层钉边界或手指跟随更新成实体）。
                    pendingCrossArrival = false
                    selection = if (cd.grabEnd) {
                        NovelSelection(0, 1)
                    } else {
                        NovelSelection(Int.MAX_VALUE - 1, Int.MAX_VALUE)
                    }
                } else {
                    // 普通翻章 / 排版变化：跨章选区一并取消。
                    pendingCrossArrival = false
                    crossDrag = null
                    selection = null
                }
            }

            // M6.3-A 标注回调（在 pagerState 之后定义；闭包读 var 最新值）。
            // M6.3 跨页续拖：选区为章节绝对 char；拦截层经 callbacks 读最新状态并翻页。
            // M6.3-E-F 可见页 keys 单点来源：抓取判定/边界查询/convertAt 白名单共用
            // （构造参数 lambda 间互不可见，须经局部变量转发）。必须经
            // rememberUpdatedState 的 *Ref 委托属性读取——直接捕获组合时的
            // pages/spreads/useDouble 会在 remember(bookId) 内 stale（首次组合
            // 时章文本未加载，pages 为空 → keys 永远为空 → 抓取恒失败）。
            val visiblePageKeysFn: () -> List<Int> = {
                when {
                    // M6.5 scroll 档：按 LazyList 实际可见 item 派生（哨兵行自动 miss）。
                    // 随滚动实时更新——手柄抓取判定/convertAt 白名单在连续滚动中保持正确。
                    pagingScrollRef -> scrollListState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                        pagesRef.getOrNull(info.index - if (scrollTopSentinelRef) 1 else 0)?.charStart
                    }
                    useDoubleRef -> {
                        spreadsRef.getOrNull(pagerState.currentPage)?.map { it.charStart }.orEmpty()
                    }
                    else -> {
                        listOfNotNull(pagesRef.getOrNull(pagerState.currentPage)?.charStart)
                    }
                }
            }
            val pageCallbacks = remember(bookId) {
                NovelPageCallbacks(
                    annotations = { annotations },
                    selection = { selection },
                    onSelectionStart = { startAbs ->
                        // M6.3-E 长按重建选区 = 放弃跨章选区。
                        crossDrag = null
                        pendingCrossArrival = false
                        selection = NovelSelection(startAbs, startAbs + 1)
                        bubble = NovelBubble(mode = 0)
                        bubbleAnnotation = null
                    },
                    onDragStart = { startAbs ->
                        val len = chapterText.length
                        val s = startAbs.coerceIn(0, len)
                        val cd = crossDrag
                        selection = when {
                            // M6.3-E 跨章拖 start（向章首延伸）：另一端钉新章章末。
                            cd != null && !cd.grabEnd -> NovelSelection(s, len)
                            // M6.3-E 跨章拖 end：start 端恒钉新章章首。
                            cd != null -> NovelSelection(0, maxOf(selection?.endAbs ?: s, s))
                            else -> selection?.copy(startAbs = s)
                        }
                    },
                    onDragEnd = { endAbs ->
                        val len = chapterText.length
                        val e = endAbs.coerceIn(0, len)
                        val cd = crossDrag
                        selection = when {
                            // M6.3-E 跨章拖 end（向章末延伸）：另一端钉新章章首。
                            cd != null && cd.grabEnd -> NovelSelection(0, e)
                            // M6.3-E 跨章拖 start：end 端恒钉新章章末。
                            cd != null -> NovelSelection(minOf(selection?.startAbs ?: e, e), len)
                            else -> selection?.copy(endAbs = e)
                        }
                    },
                    onCommit = { cs0, ce0, color, style ->
                        scope.launch {
                            // 防御：clamp 并保证至少一个字符。
                            // M6.3-E 跨章提交时 pin 章的 selection 属于拖动章（坐标不可用），
                            // 当前章区间用冻结段替代。
                            val cdCommit = crossDrag
                            val pinSeg = cdCommit?.segments?.firstOrNull { it.chapter == chapterIndex }
                            val cs = (pinSeg?.startAbs ?: cs0).coerceIn(0, chapterText.length)
                            val ce = (pinSeg?.endAbs ?: ce0).coerceIn(0, chapterText.length)
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
                            // M6.3-E 跨章冻结段提交：每段一条，旧章文本异步读（带 LRU 缓存）。
                            if (cdCommit != null) {
                                for (seg in cdCommit.segments) {
                                    if (seg.chapter == chapterIndex) continue
                                    val chapter = chapters.getOrNull(seg.chapter) ?: continue
                                    val segText = repository.readNovelChapter(bookId, chapter)
                                    val ss = seg.startAbs.coerceIn(0, segText.length)
                                    val se = seg.endAbs.coerceIn(0, segText.length)
                                    if (se <= ss) continue
                                    val segOverlap = repository.overlappingAnnotation(
                                        bookId, seg.chapter, ss, se,
                                    )
                                    if (segOverlap != null) {
                                        val ms = minOf(ss, segOverlap.charStart)
                                        val me = maxOf(se, segOverlap.charEnd)
                                        repository.updateAnnotation(
                                            id = segOverlap.id,
                                            color = color,
                                            style = style,
                                            snippet = segText.substring(ms, me).replace('\n', ' '),
                                            charStart = ms,
                                            charEnd = me,
                                        )
                                    } else {
                                        repository.addAnnotation(
                                            AnnotationRecord(
                                                id = 0L,
                                                bookId = bookId,
                                                kind = "highlight",
                                                chapterIdx = seg.chapter,
                                                charStart = ss,
                                                charEnd = se,
                                                snippet = segText.substring(ss, se)
                                                    .replace('\n', ' '),
                                                note = null,
                                                color = color,
                                                style = style,
                                                createdAt = now,
                                                updatedAt = now,
                                            ),
                                        )
                                    }
                                }
                                crossDrag = null
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
                    currentPageIndex = {
                        if (pagingScrollRef) scrollCurrentPageIndexOf() else pagerState.currentPage
                    },
                    // M6.3 翻页请求走信号：pendingFlip 由 LaunchedEffect 执行 scrollToPage。
                    // M6.3-E 翻页目标超本章 → 跨章延伸（边界检查在 beginOrExtend 内）。
                    flipPage = { target ->
                        val size = if (useDoubleRef) spreadsRef.size else pagesRef.size
                        when {
                            target < 0 -> beginOrExtendCrossDrag(false)
                            target >= size -> beginOrExtendCrossDrag(true)
                            else -> pendingFlip = target
                        }
                    },
                    convertAt = { fingerScreen ->
                        // M6.3-E-F 限定可见页白名单：跨章 dispose 窗口期旧章页换算器
                        // 仍在注册表，全表遍历会把手指位置换算成旧章字符（选区跳旧章）。
                        visiblePageKeysFn().mapNotNull { converters[it] }
                            .firstNotNullOfOrNull { it(fingerScreen) }
                    },
                    registerConverter = { key, fn -> converters[key] = fn },
                    unregisterConverter = { key, fn ->
                        if (converters[key] === fn) converters.remove(key)
                    },
                    // M6.3-E 可见页锚点/边界（全书级拦截层的抓取判定与翻页边界查询）。
                    visiblePageKeys = visiblePageKeysFn,
                    visiblePageBounds = {
                        visiblePageKeysFn().mapNotNull { pageAnchors[it] }
                    },
                    registerAnchor = { key, handle ->
                        pageAnchors[key] = handle
                        android.util.Log.d(
                            "CROSSDRAG",
                            "GREG2 key=$key map=${System.identityHashCode(pageAnchors)} " +
                                "obj=${System.identityHashCode(handle)}",
                        )
                    },
                    unregisterAnchor = { key, handle ->
                        val hit = pageAnchors[key] === handle
                        if (hit) pageAnchors.remove(key)
                        android.util.Log.d(
                            "CROSSDRAG",
                            "GUNREG2 key=$key map=${System.identityHashCode(pageAnchors)} " +
                                "obj=${System.identityHashCode(handle)} hit=$hit",
                        )
                    },
                    bubbleOwnerCharStart = lambda@{
                        val b = bubble ?: return@lambda null
                        val focus: Int = if (b.mode == 1) {
                            bubbleAnnotation?.charStart ?: return@lambda null
                        } else {
                            val sel = selection ?: return@lambda null
                            if (dragSide == "end") {
                                maxOf(sel.startAbs, sel.endAbs) - 1
                            } else {
                                minOf(sel.startAbs, sel.endAbs)
                            }
                        }
                        pagesRef.firstOrNull { p -> focus >= p.charStart && focus < p.charEnd }
                            ?.charStart
                    },
                    onDragActive = { side ->
                        dragging = side != null
                        dragSide = side.orEmpty()
                    },
                )
            }
            // 翻页后在后台预备当前页快照（双页=左右两半渲染进同一张位图）；返回时无需主线程排版。
            LaunchedEffect(
                pages,
                pagerState.currentPage,
                scrollListState.firstVisibleItemIndex,
                chapterIndex,
                prefs,
                fontFamily,
                snapshotWidthPx,
                snapshotHeightPx,
                startPadDp,
                endPadDp,
                topPadDp,
                bottomPadDp,
                useDouble,
            ) {
                // M6.5 scroll 档页号从 LazyList 派生；pager 档从 pagerState。
                val targetPage = if (pagingScrollRef) scrollCurrentPageIndexOf() else pagerState.currentPage
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
                            bottomPadPx = with(density) { bottomPadDp.toPx() },
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
                            bottomPadPx = with(density) { bottomPadDp.toPx() },
                        )
                    }
                } ?: return@LaunchedEffect
                if ((if (pagingScrollRef) scrollCurrentPageIndexOf() else pagerState.currentPage) ==
                    targetPage
                ) {
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
                settlePage(idx.coerceAtLeast(0))
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
                    settlePage(idx.coerceAtLeast(0))
                    pendingRestoreChar = -1
                    anchoredOnce = true
                }
                // char 超出当前分页覆盖（半截快排页）→ 等整章分页后再落位
            }

            // M6-G 章切换：累加器旧章页号失效
            LaunchedEffect(chapterIndex) {
                flipTarget = -1
            }

            LaunchedEffect(pagerState.currentPage, pages, spreads, useDouble, chapterIndex) {
                // M6.5 scroll 档 pagerState 不动（页号失真），进度由下方滚动 effect 保存。
                if (pagingScrollRef) return@LaunchedEffect
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
            // M6.5 scroll 档进度保存：当前页 = 视口首可见页（页粒度，charStart 锚定）。
            LaunchedEffect(scrollListState.firstVisibleItemIndex, pages, chapterIndex) {
                if (!pagingScrollRef) return@LaunchedEffect
                val pageIdx = scrollCurrentPageIndexOf()
                val currentCharStart = pages.getOrNull(pageIdx)?.charStart ?: return@LaunchedEffect
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

            // 就绪上报必须等「首次锚定」完成：宿主拿到它才会 markVisible 撤掉一镜到底
            // overlay。锚定（settlePage 到目标页）在首帧之后才执行，抢跑上报会让用户
            // 看到「第 0 页 → 目标页」的跳页；等它则跳页发生在 overlay 之下（不可见）。
            // 兜底：极端情况锚定永不置位（半截快排页覆盖不到目标 / 末页意图未就绪）时
            // 最多等 300ms 就放行，避免 overlay 永久挂住、返回不动。
            LaunchedEffect(loading, paginating, anchoredOnce) {
                if (loading || paginating) return@LaunchedEffect
                if (!anchoredOnce) {
                    withTimeoutOrNull(300) {
                        snapshotFlow { anchoredOnce }.first { it }
                    }
                }
                onReaderReady()
            }

            val requestBack: () -> Unit = {
                val snapshot = currentSnapshot
                val backPage = if (pagingScrollRef) scrollCurrentPageIndexOf() else pagerState.currentPage
                if (snapshot != null && snapshotChapter == chapterIndex && snapshotPage == backPage) {
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
                crossDrag = null
                pendingCrossArrival = false
            }
            BackHandler(
                enabled = !chapterListOpen && !quickPanel && notePanel == null &&
                    selection == null && bubble == null &&
                    !annotationListOpen && !bookmarkListOpen && !searchPanelOpen,
            ) {
                requestBack()
            }

            // M6.4 插图预览：点击正文插图 → 全屏预览（章内左右滑切换）。
            // 排版中/跨章过渡中不允许打开（此时点击区域与内容不匹配）。
            val openImagePreview: (String, Int) -> Unit = { _, charStart ->
                if (!paginating && chapterFlip == null) {
                    previewCharStart = charStart
                    previewSavedCharStart = -1
                    chromeVisible = false
                    selection = null
                    bubble = null
                    crossDrag = null
                    pendingCrossArrival = false
                }
            }
            val toastShort: (String) -> Unit = { msg ->
                android.widget.Toast.makeText(
                    context,
                    msg,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
            // 保存回落通道：API 26–28 与 SVG 走系统文件选择器（零存储权限）。
            var pendingSafSave by remember { mutableStateOf<Pair<File, String>?>(null) }
            val safSaveLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("image/*"),
            ) { uri ->
                val task = pendingSafSave
                pendingSafSave = null
                if (uri == null || task == null) {
                    previewSaving = false
                    return@rememberLauncherForActivityResult
                }
                previewSaving = true
                scope.launch {
                    val ok = NovelImageSaver.writeToUri(context, task.first, uri)
                    previewSaving = false
                    if (ok) {
                        previewSavedCharStart = previewCharStart
                        toastShort("已保存")
                    } else {
                        toastShort("保存失败")
                    }
                }
            }
            val savePreviewImage: (NovelPreviewImage) -> Unit = save@{ item ->
                if (previewSaving) return@save
                val src = File(context.filesDir, item.relPath)
                if (!src.exists()) {
                    toastShort("图片文件不存在")
                    return@save
                }
                val idx = chapterImages
                    .indexOfFirst { it.charStart == item.charStart }
                    .coerceAtLeast(0)
                val name = NovelImageSaver.defaultFileName(
                    bookTitle = book?.title ?: "插图",
                    chapterIndex = chapterIndex,
                    indexInChapter = idx,
                    relPath = item.relPath,
                )
                val mime = NovelImageSaver.mimeOf(item.relPath)
                previewSaving = true
                scope.launch {
                    when (val result = NovelImageSaver.save(context, src, name, mime)) {
                        is NovelImageSaveResult.Saved -> {
                            previewSaving = false
                            previewSavedCharStart = item.charStart
                            toastShort("已保存到相册（Pictures/Inkvol）")
                        }

                        is NovelImageSaveResult.NeedSaf -> {
                            pendingSafSave = src to name
                            previewSaving = false
                            runCatching { safSaveLauncher.launch(name) }
                                .onFailure {
                                    pendingSafSave = null
                                    toastShort("保存失败")
                                }
                        }

                        NovelImageSaveResult.Failed -> {
                            previewSaving = false
                            toastShort("保存失败")
                        }
                    }
                }
            }

            // M6-G 覆盖档滑动翻页开关：滑动只触发翻页动画（与单击同一条 flipTo 路径），
            // 不做跟手平移（覆盖档 pager 已禁用 userScrollEnabled）。选中/气泡/竖翻/预览不启用。
            val coverSwipeRef by rememberUpdatedState(
                pageTransitionMode == SettingsKeys.PAGE_TRANSITION_COVER &&
                    prefs.pagingMode != SettingsKeys.NOVEL_PAGE_VERTICAL &&
                    prefs.pagingMode != SettingsKeys.NOVEL_PAGE_SCROLL &&
                    selection == null && bubble == null && previewCharStart < 0,
            )

            // 边缘拖动（移植自漫画 ReaderScreen）：左缘=亮度、右缘=色温。
            // heightPx 由手势层传入（pointerInput 内才有 size），方向语义与漫画逐字一致：
            // 上滑变亮/变冷，下滑变暗/变暖。
            val novelEdgeDrag: (Boolean, Float, Float) -> Unit = { left, dy, heightPx ->
                if (left) {
                    val base = if (novelBrightness < 0f) 0.5f else novelBrightness
                    novelBrightness = (base - dy / heightPx).coerceIn(0.05f, 1f)
                    novelHudText = "亮度 ${(novelBrightness * 100).roundToInt()}%"
                } else {
                    novelColorTemp = (novelColorTemp - dy / heightPx * 1.6f).coerceIn(-1f, 1f)
                    novelHudText = when {
                        abs(novelColorTemp) < 0.05f -> "色温 中性"
                        novelColorTemp > 0f -> "色温 暖 ${(novelColorTemp * 100).roundToInt()}%"
                        else -> "色温 冷 ${(-novelColorTemp * 100).roundToInt()}%"
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.background)
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
                            // 插图预览打开时根层点击全部失效（预览层自己处理关闭）
                            if (previewOpenRef) return@detectTapGestures
                            // M6-G curl 档：分区点击由 PageCurl 的 onCustomTap 独家处理
                            //（tap 手势已 vendor 去掉 down consume，本层会同时收到点击），
                            // 此处跳过防双触发；选区收起也在 onCustomTap 第一分支处理。
                            if (curlActiveNow) {
                                return@detectTapGestures
                            }
                            // 选区 / 气泡激活时，点击空白 = 收起，不翻页不呼 chrome。
                            if (selection != null || bubble != null) {
                                selection = null
                                bubble = null
                                crossDrag = null
                                pendingCrossArrival = false
                                return@detectTapGestures
                            }
                            // M6.5 纵向连续：点击分区换纵轴——上/下 1/3 滚一页（页首对齐，
                            // 与进度锚定同粒度），中间开合 chrome。chrome 打开时上下滚仍生效
                            // （与 pager 档「chrome 开着也能翻页」同语义）。
                            if (pagingScrollRef) {
                                val pageCount = pagesRef.size
                                val curPage = scrollCurrentPageIndexOf()
                                when {
                                    offset.y < size.height * 0.32f -> {
                                        if (curPage > 0) scope.launch {
                                            scrollListState.scrollToItem(scrollListIndexOf(curPage - 1))
                                        }
                                    }
                                    offset.y > size.height * 0.68f -> {
                                        if (curPage < pageCount - 1) scope.launch {
                                            scrollListState.scrollToItem(scrollListIndexOf(curPage + 1))
                                        }
                                    }
                                    else -> chromeVisible = !chromeVisible
                                }
                                return@detectTapGestures
                            }
                            when {
                                offset.x < size.width * 0.32f -> scope.launch {
                                    when {
                                        // 章内 → 翻页动画档（flipTo）
                                        pagerState.currentPage > 0 -> currentFlipTo(false)
                                        // 首页点上一页 → 上一章末页（跨章：快照过渡层）。
                                        chapterIndex > 0 -> {
                                            beginChapterFlip(false)
                                            pendingJumpLastPage = true
                                            chapterIndex -= 1
                                        }
                                    }
                                }
                                offset.x > size.width * 0.68f -> scope.launch {
                                    // 双页模式 pager 页 = spread，末页判定必须用 spreads.lastIndex。
                                    val lastPagerIndex = if (useDouble) spreads.lastIndex else pages.lastIndex
                                    when {
                                        // 章内 → 翻页动画档（flipTo）
                                        pagerState.currentPage < lastPagerIndex ->
                                            currentFlipTo(true)
                                        // 末页点下一页 → 下一章首页（跨章：快照过渡层）。
                                        chapterIndex < chapters.lastIndex -> {
                                            beginChapterFlip(true)
                                            pendingJumpLastPage = false
                                            pendingRestoreOffset = 0
                                            chapterIndex += 1
                                        }
                                    }
                                }
                                else -> chromeVisible = !chromeVisible
                            }
                        }
                    }
                    // M6-G 覆盖档滑动翻页识别：横向滑动超阈值（且方向占优）→ 走与单击完全
                    // 相同的覆盖动画（不跟手平移）；末页左滑/首页右滑同点击层跨章。
                    .pointerInput(bookId) {
                        val threshold = with(density) { 48.dp.toPx() }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!coverSwipeRef) return@awaitEachGesture
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) return@awaitEachGesture
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: continue
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (kotlin.math.abs(dx) > threshold &&
                                    kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f
                                ) {
                                    change.consume()
                                    val forward = dx < 0f
                                    scope.launch {
                                        val cur = pagerState.currentPage
                                        val lastIdx = if (useDoubleRef) {
                                            spreadsRef.lastIndex
                                        } else {
                                            pagesRef.lastIndex
                                        }
                                        if (forward) {
                                            when {
                                                cur < lastIdx -> currentFlipTo(true)
                                                // 末页左滑 → 下一章首页（与点击同款：快照过渡层）
                                                chapterIndex < chapters.lastIndex -> {
                                                    beginChapterFlip(true)
                                                    pendingJumpLastPage = false
                                                    pendingRestoreOffset = 0
                                                    chapterIndex += 1
                                                }
                                            }
                                        } else {
                                            when {
                                                cur > 0 -> currentFlipTo(false)
                                                // 首页右滑 → 上一章末页（快照过渡层）
                                                chapterIndex > 0 -> {
                                                    beginChapterFlip(false)
                                                    pendingJumpLastPage = true
                                                    chapterIndex -= 1
                                                }
                                            }
                                        }
                                    }
                                    return@awaitEachGesture
                                }
                            }
                        }
                    }
                    // M6-G 边界越界滑动跨章：末页继续向后滑 / 首页继续向前滑 → 切章。
                    // 与覆盖档的滑动识别互斥（覆盖档 pager 已关原生拖拽，章内翻页与跨章都由
                    // 上面那段统一处理）；仿真档库手势在边界不启动、不消费事件，本层照常识别。
                    // 位移达阈值即触发（56dp，与覆盖档 48dp 同量级），不做累计，避免"滑不动"。
                    .pointerInput(bookId) {
                        val threshold = with(density) { 56.dp.toPx() }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // M6.5 scroll 档禁用：连续滚动没有吸附边界，向下拖 = 正常滚动，
                            // 跨章由顶部/底部哨兵行承接（见 LazyColumn 容器）。
                            if (pagingScrollRef) {
                                return@awaitEachGesture
                            }
                            // 覆盖档走自己的识别
                            if (pageTransitionMode == SettingsKeys.PAGE_TRANSITION_COVER &&
                                prefs.pagingMode != SettingsKeys.NOVEL_PAGE_VERTICAL
                            ) {
                                return@awaitEachGesture
                            }
                            // 选区 / 气泡 / 面板 / 过渡中 / 排版中 / 插图预览：不跨章
                            if (selection != null || bubble != null || quickPanel ||
                                chapterListOpen || notePanel != null ||
                                annotationListOpen || bookmarkListOpen || searchPanelOpen ||
                                chapterFlip != null || paginating || previewOpenRef
                            ) {
                                return@awaitEachGesture
                            }
                            val vertical = prefs.pagingMode == SettingsKeys.NOVEL_PAGE_VERTICAL
                            // 当前页真源：仿真档 pagerState 由镜像滞后同步，用库的 current
                            val cur = if (curlActiveNow) curlState.current else pagerState.currentPage
                            val lastIdx = if (useDoubleRef) spreadsRef.lastIndex else pagesRef.lastIndex
                            if (lastIdx < 0) return@awaitEachGesture
                            val canNext = cur >= lastIdx && chapterIndex < chapters.lastIndex
                            val canPrev = cur <= 0 && chapterIndex > 0
                            if (!canNext && !canPrev) return@awaitEachGesture
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) return@awaitEachGesture
                                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                val d = if (vertical) {
                                    change.position.y - down.position.y
                                } else {
                                    change.position.x - down.position.x
                                }
                                // 左滑/上滑（d<0）= 下一章；右滑/下滑（d>0）= 上一章
                                val forward = canNext && d <= -threshold
                                val backward = canPrev && d >= threshold
                                if (!forward && !backward) continue
                                change.consume()
                                scope.launch {
                                    if (forward) {
                                        currentBeginChapterFlip(true)
                                        pendingJumpLastPage = false
                                        pendingRestoreOffset = 0
                                        chapterIndex += 1
                                    } else {
                                        currentBeginChapterFlip(false)
                                        pendingJumpLastPage = true
                                        chapterIndex -= 1
                                    }
                                }
                                return@awaitEachGesture
                            }
                        }
                    }
                    // M6.3-E 全书级手柄拦截层：key 不含章/页/选区——跨章、跨页、选区高频
                    // 更新时手势协程均不重启，一切状态经回调读最新值。手柄抓取判定用
                    // 「可见页」（当前 spread 1~2 页）注册的锚点；新章锚点未就绪时
                    // 全部 null → 判定失败放弃（安全兜底，选区停顿不乱跳）。
                    .pointerInput(bookId, pageCallbacks) {
                        awaitEachGesture {
                            // 插图预览打开：不建选区、不拖手柄（全屏层之下不应有任何选区行为）
                            if (previewOpenRef) return@awaitEachGesture
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // 新手势起手：清除上一手势遗留的长按标记（防误消费本次点击）
                            longPressFired = false
                            val cb = pageCallbacks
                            val sel = cb.selection()
                            if (sel == null) {
                                // M6-G vendor 配套长按建选区（curl 档）：PageCurl 拖拽手势在
                                // 手指微移超 touch slop 瞬间 consume 事件，正文 detectTapGestures
                                // 的长按等待随之取消 → 长按选区建不起来。根层拦截层收到的 move
                                // 不检查消费标记（坐标照读），在此自行计时：按住 480ms 且累计
                                // 位移 < 24dp → convertAt 把按点转字符 → 建单点选区（语义与
                                // onSelectionStart 完全一致）；位移超阈值或提前抬起/取消 → 放弃
                                // 计时放行（卷曲拖拽/分区点击）。NovelPageContent 自己的长按
                                //（同时限 500ms）仍会触发，两边写同一 selection 后到方覆盖同
                                // 值，天然去重。本分支只监听不消费，不影响 PageCurl 手势。
                                val downScreen = down.position + screenOrigin
                                val slop = with(density) { 24.dp.toPx() }
                                var moved = false
                                val char: Int? = withTimeoutOrNull(480L) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.none { it.pressed }) {
                                            return@withTimeoutOrNull null
                                        }
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: continue
                                        if ((change.position - down.position).getDistance() > slop) {
                                            moved = true
                                            return@withTimeoutOrNull null
                                        }
                                    }
                                    @Suppress("UNREACHABLE_CODE") null
                                }
                                if (char != null && !moved) {
                                    crossDrag = null
                                    pendingCrossArrival = false
                                    selection = NovelSelection(char, char + 1)
                                    bubble = NovelBubble(mode = 0)
                                    bubbleAnnotation = null
                                    // 标记本次手势是长按建选区：抬手时库 tapGesture 判点击 →
                                    // onCustomTap 消费但不收起，选区保留可见
                                    longPressFired = true
                                }
                                return@awaitEachGesture
                            }
                            // M6.3-E-F 抓取判定改按渲染语义（左圆=lo、右圆=hi），与
                            // NovelPageContent 的圆点渲染（handleAnchorAbs(lo,true) /
                            // handleAnchorAbs(hi,false)）严格一致。旧公式按 start/end
                            // 角色：翻转选区且 lo 为行首时，end 端锚到「上一行末字符
                            // right」而渲染左圆在「行首 left」，错开一整行 → 抓取失败
                            // 或抓错端（拖 A 端动 B 端）。
                            val lo = minOf(sel.startAbs, sel.endAbs)
                            val hi = maxOf(sel.startAbs, sel.endAbs)
                            val leftIsStart = sel.startAbs <= sel.endAbs
                            var dLeft = Float.MAX_VALUE
                            var dRight = Float.MAX_VALUE
                            var leftAnchorX = Float.MAX_VALUE
                            var rightAnchorX = Float.MAX_VALUE
                            var headAnchorY = Float.MAX_VALUE
                            var tailAnchorY = Float.MAX_VALUE
                            var tailAnchorExists = false
                            val vb = cb.visiblePageBounds()
                            android.util.Log.d(
                                "CROSSDRAG",
                                "GCHK cur=${pagerState.currentPage} pagesN=${pagesRef.size} " +
                                    "useDouble=$useDoubleRef keys=${cb.visiblePageKeys()} " +
                                    "map=${System.identityHashCode(pageAnchors)} " +
                                    "reg=${pageAnchors.entries.joinToString { "${it.key}#${System.identityHashCode(it.value)}" }} " +
                                    "vbN=${vb.size}",
                            )
                            for (h in vb) {
                                h.anchor(lo, true)?.let {
                                    val d = (down.position + screenOrigin - (h.origin() + it))
                                        .getDistance()
                                    if (d < dLeft) {
                                        dLeft = d
                                        leftAnchorX = h.origin().x + it.x
                                        headAnchorY = h.origin().y + it.y
                                    }
                                }
                                h.anchor(hi, false)?.let {
                                    val d = (down.position + screenOrigin - (h.origin() + it))
                                        .getDistance()
                                    tailAnchorExists = true
                                    if (d < dRight) {
                                        dRight = d
                                        rightAnchorX = h.origin().x + it.x
                                        tailAnchorY = h.origin().y + it.y
                                    }
                                }
                            }
                            // M6.3-E-G 抓取半径 28→44dp：手柄圆点触控目标偏小（微信
                            // 读书 ~40dp），选区调整时手指略偏即抓取失败。
                            val grabRadius = with(density) { 44.dp.toPx() }
                            // M6.3-E-H 圆点改上下布局后，抓取判定统一「按圆」产出
                            // grabLo/grabHi（视觉左圆/右圆），最后一步才映射到
                            // start/end 角色——翻转选区下「上圆=lo」与角色独立，
                            // 空白区/纵向带分支若直接给角色会在翻转时抓错圆。
                            var grabLo = dLeft <= dRight && dLeft <= grabRadius
                            var grabHi = !grabLo && dRight <= grabRadius
                            if (!grabLo && !grabHi &&
                                kotlin.math.abs(dLeft - dRight) < 1f &&
                                dLeft <= grabRadius && leftAnchorX != Float.MAX_VALUE
                            ) {
                                // 零宽/单字选区两圆重叠：按手指相对两圆中点的 x 分端。
                                grabLo = (down.position.x + screenOrigin.x) <
                                    (leftAnchorX + rightAnchorX) / 2f
                                grabHi = !grabLo
                            }
                            // M6.3-E-J：兜底分支（页头/页尾空白、选区纵向带）不再 down 即抓
                            // ——旧实现三分支合计覆盖页面全部纵向且 x 不设界，普通点击
                            // 也被吞成拖动（点击空白/其它位置光标跟着动、无法退出选中）。
                            // 只记录命中标记，意图确认通过后再接管（见下方 grabStart 段）。
                            var fallbackLo = false
                            var fallbackHi = false
                            if (!grabLo && !grabHi) {
                                val downY = down.position.y + screenOrigin.y
                                if (tailAnchorExists && tailAnchorY != Float.MAX_VALUE &&
                                    downY > tailAnchorY
                                ) {
                                    // 页尾空白区（下圆之下）：抓下圆
                                    fallbackHi = true
                                } else if (headAnchorY != Float.MAX_VALUE &&
                                    downY < headAnchorY
                                ) {
                                    // 页头空白区（上圆之上）：抓上圆
                                    fallbackLo = true
                                } else if (headAnchorY != Float.MAX_VALUE &&
                                    tailAnchorExists && tailAnchorY != Float.MAX_VALUE &&
                                    downY >= headAnchorY && downY <= tailAnchorY
                                ) {
                                    // M6.3-E-G 选区纵向带内（手指在两圆之间）：抓较近一圆。
                                    if (dLeft <= dRight) fallbackLo = true else fallbackHi = true
                                }
                            }
                            // 角色映射：左圆属于 start（正常序）或 end（翻转序）。
                            // 后续 applyDrag/tickEdgeHold 的 grabStart/grabEnd 角色语义
                            // （拖 start 写 onDragStart）保持不变。兜底确认后 grabLo/grabHi
                            // 可能重算，故声明为 var。
                            var grabStart = if (leftIsStart) grabLo else grabHi
                            var grabEnd = if (leftIsStart) grabHi else grabLo
                            android.util.Log.d(
                                "CROSSDRAG",
                                "grab lo=$lo hi=$hi leftIsStart=$leftIsStart " +
                                    "dLeft=$dLeft dRight=$dRight " +
                                    "lo=$grabLo hi=$grabHi start/$grabEnd " +
                                    "fb=${fallbackLo || fallbackHi}",
                            )
                            // M6.3-E-F 方向与角色解耦：拖动端的视觉位置（lo/hi）决定
                            // 停住翻页/跨章方向，start/end 角色（写哪个回调）与之独立。
                            // 翻转选区下角色端的视觉位置随拖动变化——必须每帧动态判定，
                            // 静态抓取时快照会在翻转后判错方向。
                            fun draggingLower(): Boolean {
                                val sel = cb.selection() ?: return leftIsStart
                                return if (grabStart) {
                                    sel.startAbs <= sel.endAbs
                                } else {
                                    sel.endAbs < sel.startAbs
                                }
                            }
                            var dragStartPos = down.position
                            if (grabStart || grabEnd) {
                                // 半径域命中：立即接管（含零宽重叠分端）。
                                down.consume()
                                cb.onDragActive(if (grabStart) "start" else "end")
                            } else if (fallbackLo || fallbackHi) {
                                // M6.3-E-J 兜底意图确认：tap（无位移抬起）不抓不消费 →
                                // 正文 tap 接管（收起选区/点击分区）；MOVE 超 touch slop
                                // 或按住静止 ~180ms 才按兜底端接管拖动（按住=拖动与停住
                                // 翻页语义保留；180ms < 长按 500ms，确认后开始消费事件，
                                // 正文长按重建被 consumed 阻断）。down 已过分发期不消费，
                                // 接管后由拖动循环消费 MOVE——正文手势见 consumed 即退出。
                                val slop = viewConfiguration.touchSlop
                                var moved = 0f
                                var lastPos = down.position
                                var confirmed = false
                                while (true) {
                                    val ev = withTimeoutOrNull(180L) { awaitPointerEvent() }
                                    if (ev == null) {
                                        // 180ms 无新事件 = 按住不动 → 按住即拖动意图。
                                        confirmed = true
                                        break
                                    }
                                    val ch = ev.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (ch.isConsumed) return@awaitEachGesture
                                    if (!ch.pressed) return@awaitEachGesture
                                    moved += (ch.position - lastPos).getDistance()
                                    lastPos = ch.position
                                    if (moved > slop) {
                                        confirmed = true
                                        break
                                    }
                                }
                                if (!confirmed) return@awaitEachGesture
                                // 按当前手指位置重算兜底端（确认时手指可能已偏移）。
                                val curY = lastPos.y + screenOrigin.y
                                grabLo = false
                                grabHi = false
                                if (tailAnchorExists && tailAnchorY != Float.MAX_VALUE &&
                                    curY > tailAnchorY
                                ) {
                                    grabHi = true
                                } else if (headAnchorY != Float.MAX_VALUE && curY < headAnchorY) {
                                    grabLo = true
                                } else if (headAnchorY != Float.MAX_VALUE &&
                                    tailAnchorExists && tailAnchorY != Float.MAX_VALUE &&
                                    curY >= headAnchorY && curY <= tailAnchorY
                                ) {
                                    if (dLeft <= dRight) grabLo = true else grabHi = true
                                }
                                grabStart = if (leftIsStart) grabLo else grabHi
                                grabEnd = if (leftIsStart) grabHi else grabLo
                                if (!grabStart && !grabEnd) return@awaitEachGesture
                                android.util.Log.d(
                                    "CROSSDRAG",
                                    "grab fb-confirmed lo=$grabLo hi=$grabHi moved=$moved",
                                )
                                cb.onDragActive(if (grabStart) "start" else "end")
                                dragStartPos = lastPos
                            } else {
                                // 判定失败放弃（不消费）→ 正文长按正常处理重建选区。
                                return@awaitEachGesture
                            }
                            var lastAbs = if (grabStart) sel.startAbs else sel.endAbs
                            var lastWasLo =
                                lastAbs <= (if (grabStart) sel.endAbs else sel.startAbs)
                            // 握把自动翻页（M6.3 可靠性改造 + M6.3-E 跨章延伸）：
                            // 内容触边/手指贴缘 + 静止 ≥400ms → 翻页；翻页目标超本章 → 跨章。
                            val edgePx = with(density) { 72.dp.toPx() }
                            val holdMs = 400L
                            val cooldownMs = 300L
                            var stillSince = 0L
                            // M6.3-E 修复：hasFinger/lastFinger 在 down 时即就位——
                            // 旧代码只在首个 MOVE 后置位，导致「抓着手柄直接停住」
                            // （无 MOVE 事件）时帧路径永不计时，停住翻页/跨章死路。
                            // M6.3-E-J：兜底路径起点 = 意图确认时刻的手指位置。
                            var lastFinger = dragStartPos
                            var hasFinger = true
                            var cooldownUntil = 0L
                            var justFlipped = false

                            fun applyDrag(fingerWindow: Offset) {
                                // 翻页/跨章后首帧：拖动端钉到新页边界（连续停住 → 连续钉进）。
                                // 新章 pages 未就绪时 bounds 为空 → 等待（选区停顿，安全）。
                                if (justFlipped) {
                                    val bounds = cb.visiblePageBounds()
                                    if (bounds.isEmpty()) return
                                    if (!draggingLower()) {
                                        // 拖视觉右圆：钉到页尾最后文本字符（end 角色
                                        // exclusive → 含末字符需 +1；start 角色则 inclusive
                                        // 末字符 = lastTextEnd - 1）。
                                        val tail = bounds.maxBy { it.lastTextEnd }
                                        if (grabStart) {
                                            cb.onDragStart(tail.lastTextEnd - 1)
                                            lastAbs = tail.lastTextEnd - 1
                                        } else {
                                            cb.onDragEnd(tail.lastTextEnd)
                                            lastAbs = tail.lastTextEnd
                                        }
                                    } else {
                                        // 拖视觉左圆：钉到页首。
                                        val head = bounds.minBy { it.charStart }
                                        if (grabStart) {
                                            cb.onDragStart(head.charStart)
                                            lastAbs = head.charStart
                                        } else {
                                            cb.onDragEnd(head.charStart + 1)
                                            lastAbs = head.charStart + 1
                                        }
                                    }
                                    return
                                }
                                cb.convertAt(fingerWindow)?.let { raw ->
                                    // start 端 inclusive、end 端 exclusive（含当前字符）
                                    val abs = if (grabStart) raw else raw + 1
                                    // 方向钳制：另一端取实时选区（跨章后另一端 = 新章边界）
                                    val other = cb.selection()?.let {
                                        if (grabStart) it.endAbs else it.startAbs
                                    } ?: abs
                                    val nowLo = abs <= other
                                    val flipped = nowLo != lastWasLo
                                    if (!flipped || kotlin.math.abs(abs - lastAbs) > 1) {
                                        if (grabStart) cb.onDragStart(abs) else cb.onDragEnd(abs)
                                        lastAbs = abs
                                        lastWasLo = nowLo
                                    }
                                }
                            }

                            // 返回 true = 触发翻页/跨章（flipPage 由循环体调用，避免 local
                            // suspend fun 在 AwaitPointerEventScope（restricted）内的限制）。
                            fun tickEdgeHold(now: Long, atEdge: Boolean): Boolean {
                                val bounds = cb.visiblePageBounds()
                                if (bounds.isEmpty()) return false
                                val atTail = if (!draggingLower()) {
                                    // M6.3-E-F 拖视觉右圆：向页尾——tail 用 lastTextEnd
                                    //（最后文本字符）而非 charEnd（图片段不可达）。
                                    val tail = bounds.maxBy { it.lastTextEnd }
                                    (lastAbs >= tail.lastTextEnd - 2 && lastAbs <= tail.lastTextEnd) ||
                                        (lastAbs > tail.lastTextEnd && atEdge)
                                } else {
                                    // 拖视觉左圆：向页头。
                                    val head = bounds.minBy { it.charStart }
                                    (lastAbs <= head.charStart + 2 && lastAbs >= head.charStart) ||
                                        (lastAbs < head.charStart && atEdge)
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
                                            cb.flipPage(cb.currentPageIndex() + if (draggingLower()) -1 else 1)
                                        }
                                        applyDrag(lastFinger + screenOrigin)
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
                                // 静止判定加位移阈值：电容屏噪声不重置 400ms 计时
                                if ((change.position - lastFinger).getDistance() > 8f) {
                                    stillSince = 0L
                                    justFlipped = false
                                }
                                lastFinger = change.position
                                change.consume()
                                applyDrag(change.position + screenOrigin)
                                val atEdge = if (grabEnd) {
                                    lastFinger.y > size.height - edgePx ||
                                        lastFinger.x > size.width - edgePx
                                } else {
                                    lastFinger.y < edgePx || lastFinger.x < edgePx
                                }
                                if (tickEdgeHold(System.currentTimeMillis(), atEdge)) {
                                    cb.flipPage(cb.currentPageIndex() + if (draggingLower()) -1 else 1)
                                }
                            }
                            cb.onDragActive(null)
                        }
                    }
                    // 边缘手势层（移植自漫画）：挂在链尾——先于本层声明的兄弟层（分区点击 /
                    // 覆盖滑动 / 跨章拖拽 / 手柄拦截）一旦消费，本层即让位（见 novelEdgeDrag
                    // 内 isConsumed 检查），保证选区拖动、翻页拖拽、滚动语义零回归。
                    .novelEdgeDrag(
                        onDrag = novelEdgeDrag,
                        leftEnabled = novelEdgeBrightnessOn,
                        rightEnabled = novelEdgeColorTempOn,
                    )
                    .onGloballyPositioned { screenOrigin = it.positionInRoot() }
            ) {
                // M6-G 跨章覆盖档：旧页快照垫在最底层（新内容从侧面盖入，必须画在页面之下）。
                chapterFlip?.let { anim ->
                    if (anim.mode == SettingsKeys.PAGE_TRANSITION_COVER) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawImage(anim.snapshot)
                        }
                    }
                }
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
                        // M6-G 跨章过渡：页面容器整体位移（仅过渡期间非零）——
                        // slide/cover 档让新章内容从侧向滑入，curl 档保持不动（由覆盖层卷走旧页）。
                        // 常驻不透明底：cover 档位移时新内容不能透出下层旧页快照。
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val anim = chapterFlip
                                    if (anim != null &&
                                        anim.mode != SettingsKeys.PAGE_TRANSITION_CURL
                                    ) {
                                        val p = anim.progress.value.coerceIn(0f, 1f)
                                        val remain = (1f - p) * (if (anim.forward) 1f else -1f)
                                        if (anim.vertical) {
                                            translationY = remain * size.height
                                        } else {
                                            translationX = remain * size.width
                                        }
                                    }
                                }
                                .background(theme.background),
                        ) {
                        // M6-G 覆盖档（横翻生效；上下翻页由 flipTo 回退平移，pager 不套补偿）。
                        // 覆盖档下 pager 关闭拖拽（userScrollEnabled=false）：横向滑动由根层
                        // 识别后直接播与单击一致的覆盖动画，不存在拖拽跟手的补偿抖动。
                        val coverEnabled = pageTransitionMode == SettingsKeys.PAGE_TRANSITION_COVER &&
                            prefs.pagingMode != SettingsKeys.NOVEL_PAGE_VERTICAL &&
                            prefs.pagingMode != SettingsKeys.NOVEL_PAGE_SCROLL
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
                        // M6.5 纵向连续（scroll 档）：LazyColumn 连续滚动容器，单章信息流。
                        // item 布局 = [顶部哨兵(上一章)] + 各页 + [底部哨兵(下一章)]。
                        // 页间无缝：页 item 随内容自适应高度（不套视口定高），页排版用
                        // 零上下边距 + availHeightPx=0（关掉翻页档的余数平分装饰），
                        // 正文首尾相接成连续文本流；分页结构/进度锚定不动。
                        // 章首/章末呼吸：列表 contentPadding 上下 16dp（方案 A）。
                        if (pagingScroll) {
                            val volumeKeysEnabled = selection == null && bubble == null &&
                                !quickPanel && !chapterListOpen && notePanel == null &&
                                !annotationListOpen && !bookmarkListOpen && !searchPanelOpen &&
                                previewCharStart < 0 && chapterFlip == null
                            // 页间无缝排版参数：左右边距保留，上下清零。
                            val scrollPagePadding = PaddingValues(
                                start = startPadDp,
                                end = endPadDp,
                            )
                            LaunchedEffect(
                                pagingScroll, quickPanel, chapterListOpen, notePanel,
                                annotationListOpen, bookmarkListOpen, searchPanelOpen,
                                selection, bubble, chapterFlip,
                            ) {
                                // 面板全关后重新要焦点：音量键依赖本容器持有焦点。
                                if (pagingScroll && volumeKeysEnabled) {
                                    runCatching { scrollFocusRequester.requestFocus() }
                                }
                            }
                            LazyColumn(
                                state = scrollListState,
                                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(scrollFocusRequester)
                                    .focusable()
                                    .onPreviewKeyEvent { event ->
                                        // 音量键 = 上/下滚一页（scroll 档专属；pager 档无此功能）。
                                        val hit = event.type == KeyEventType.KeyDown &&
                                            (event.key == Key.VolumeUp || event.key == Key.VolumeDown) &&
                                            volumeKeysEnabled
                                        if (!hit) return@onPreviewKeyEvent false
                                        val curPage = scrollCurrentPageIndexOf()
                                        val target = (if (event.key == Key.VolumeDown) {
                                            curPage + 1
                                        } else {
                                            curPage - 1
                                        }).coerceIn(0, pages.size - 1)
                                        if (target == curPage) return@onPreviewKeyEvent false
                                        scope.launch {
                                            scrollListState.scrollToItem(scrollListIndexOf(target))
                                        }
                                        true
                                    },
                                // 插图预览打开时下层内容不跟随手指滚动（与 pager 档同语义）
                                userScrollEnabled = previewCharStart < 0,
                            ) {
                                if (chapterIndex > 0) {
                                    item(key = "novel_scroll_sentinel_top") {
                                        NovelScrollSentinelRow(
                                            label = "上一章 · " +
                                                (chapters.getOrNull(chapterIndex - 1)?.title ?: ""),
                                            enabled = true,
                                            theme = theme,
                                            onClick = {
                                                beginChapterFlip(false)
                                                pendingJumpLastPage = true
                                                chapterIndex -= 1
                                            },
                                        )
                                    }
                                }
                                items(
                                    count = pages.size,
                                    key = { idx -> "novel_scroll_page_${pages[idx].charStart}" },
                                ) { pageIdx ->
                                    // 不套定高：Box 随页内容自适应（LazyColumn item 高度无界），
                                    // 页与页正文首尾相接；页排版上下零边距 + 无余数垫高。
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        NovelPageContent(
                                            page = pages[pageIdx],
                                            prefs = prefs,
                                            theme = theme,
                                            fontFamily = fontFamily,
                                            contentPadding = scrollPagePadding,
                                            availHeightPx = 0,
                                            annotations = annotations,
                                            selection = displaySelection,
                                            bubble = bubble,
                                            bubbleAnnotation = bubbleAnnotation,
                                            callbacks = pageCallbacks,
                                            dragging = dragging,
                                            dragSide = dragSide,
                                            locateHighlight = if (locateHighlightChapter == chapterIndex) locateHighlight else null,
                                            onImageTap = openImagePreview,
                                        )
                                    }
                                }
                                if (chapterIndex < chapters.lastIndex) {
                                    item(key = "novel_scroll_sentinel_bottom") {
                                        NovelScrollSentinelRow(
                                            label = "下一章 · " +
                                                (chapters.getOrNull(chapterIndex + 1)?.title ?: ""),
                                            enabled = true,
                                            theme = theme,
                                            onClick = {
                                                beginChapterFlip(true)
                                                pendingRestoreOffset = 0
                                                pendingJumpLastPage = false
                                                chapterIndex += 1
                                            },
                                        )
                                    }
                                }
                            }
                        } else if (prefs.pagingMode == SettingsKeys.NOVEL_PAGE_VERTICAL) {
                            VerticalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                // M6.3 跨页续选：翻页后旧页保持组合，拦截层手势协程不被取消
                                // （默认 0 会在 scrollToPage 时销毁旧页 → 拖动中断）
                                beyondViewportPageCount = 1,
                                // 插图预览打开时下层页面不跟随手指滚动
                                userScrollEnabled = previewCharStart < 0,
                            ) { page ->
                                NovelPageContent(
                                    page = pages[page],
                                    prefs = prefs,
                                    theme = theme,
                                    fontFamily = fontFamily,
                                    contentPadding = pageContentPadding,
                                    availHeightPx = heightPx,
                                    annotations = annotations,
                                    selection = displaySelection,
                                    bubble = bubble,
                                    bubbleAnnotation = bubbleAnnotation,
                                    callbacks = pageCallbacks,
                                    dragging = dragging,
                                    dragSide = dragSide,
                                    locateHighlight = if (locateHighlightChapter == chapterIndex) locateHighlight else null,
                                    onImageTap = openImagePreview,
                                )
                            }
                        } else if (curlActiveNow) {
                            // M6-G curl 档：横向容器为 pagecurl 库（拖拽跟手/松手 snap/内置卷曲
                            // 动画由库手势驱动）。双页 spread 作为整体页卷曲（观感妥协：库为
                            // 整页卷曲语义）。跳转/锚定经 settlePage → curlState.snapTo。
                            val count = if (useDouble) spreads.size else pages.size
                            PageCurl(
                                count = count,
                                state = curlState,
                                config = curlConfig,
                                key = { index ->
                                    if (useDouble) {
                                        spreads.getOrNull(index)?.firstOrNull()?.charStart ?: index
                                    } else {
                                        pages.getOrNull(index)?.charStart ?: index
                                    }
                                },
                            ) { index ->
                                // 只有当前页层接收正文手势：库静止时 current-1 层压在 current 之上
                                //（drawNothing 不绘制但仍参与命中测试），否则长按会选中上一页的字符、
                                // 手柄锚点全部 miss（GANCH miss）→ 表现「长按拖选卡住/拖不动」。
                                val isCurrentLayer = index == curlState.current
                                // 库的层叠设计假设每页内容自带不透明背景（静止时 current 页
                                // 盖住底层 current+1 页）——NovelPageContent/NovelSpreadContent
                                // 只画文字不画背景，必须在此补不透明底，否则两页文字叠印
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(theme.background),
                                ) {
                                    if (useDouble) {
                                        val spread = spreads.getOrNull(index)
                                        NovelSpreadContent(
                                            left = spread?.getOrNull(0),
                                            right = spread?.getOrNull(1),
                                            prefs = prefs,
                                            theme = theme,
                                            fontFamily = fontFamily,
                                            outerPadding = PaddingValues(start = startPadDp, end = endPadDp),
                                            contentPadding = PaddingValues(top = topPadDp, bottom = bottomPadDp),
                                            availHeightPx = heightPx,
                                            annotations = annotations,
                                            selection = displaySelection,
                                            bubble = bubble,
                                            bubbleAnnotation = bubbleAnnotation,
                                            callbacks = pageCallbacks,
                                            dragging = dragging,
                                            dragSide = dragSide,
                                            locateHighlight = if (locateHighlightChapter == chapterIndex) locateHighlight else null,
                                            inputEnabled = isCurrentLayer,
                                            onImageTap = openImagePreview,
                                        )
                                    } else {
                                        NovelPageContent(
                                            page = pages[index],
                                            prefs = prefs,
                                            theme = theme,
                                            fontFamily = fontFamily,
                                            contentPadding = pageContentPadding,
                                            availHeightPx = heightPx,
                                            annotations = annotations,
                                            selection = displaySelection,
                                            bubble = bubble,
                                            bubbleAnnotation = bubbleAnnotation,
                                            callbacks = pageCallbacks,
                                            dragging = dragging,
                                            dragSide = dragSide,
                                            locateHighlight = if (locateHighlightChapter == chapterIndex) locateHighlight else null,
                                            inputEnabled = isCurrentLayer,
                                            onImageTap = openImagePreview,
                                        )
                                    }
                                }
                            }
                        } else if (useDouble) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
                                // 覆盖档：拖拽跟手关闭——横向滑动由根层识别后直接播覆盖动画
                                userScrollEnabled = !coverEnabled && previewCharStart < 0,
                            ) { index ->
                                val spreadBody: @Composable () -> Unit = {
                                    val spread = spreads.getOrNull(index)
                                    NovelSpreadContent(
                                        left = spread?.getOrNull(0),
                                        right = spread?.getOrNull(1),
                                        prefs = prefs,
                                        theme = theme,
                                        fontFamily = fontFamily,
                                        outerPadding = PaddingValues(start = startPadDp, end = endPadDp),
                                        contentPadding = PaddingValues(top = topPadDp, bottom = bottomPadDp),
                                        availHeightPx = heightPx,
                                        annotations = annotations,
                                        selection = displaySelection,
                                        bubble = bubble,
                                        bubbleAnnotation = bubbleAnnotation,
                                        callbacks = pageCallbacks,
                                        dragging = dragging,
                                        dragSide = dragSide,
                                        locateHighlight = if (locateHighlightChapter == chapterIndex) locateHighlight else null,
                                        onImageTap = openImagePreview,
                                    )
                                }
                                if (coverEnabled) {
                                    Box(
                                        modifier = Modifier
                                            // 进入页提升绘制层级（倒退翻页时进入页 index 更小，
                                            // 必须翻 Z 才能产生「盖入」效果）
                                            .zIndex(if (index == coverEnteringPage) 1f else 0f)
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                // 覆盖档：离开页抵消 pager 位移视觉固定；draw 阶段
                                                // 读 pager 状态，零重组。小说无 RTL，补偿符号恒正。
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
                                                        translationX = (position - leaving) * size.width
                                                    }
                                                }
                                            }
                                            // 覆盖档每页自带不透明底：页面内容只画文字不画背景，
                                            // 缺底时进入页盖入会与固定在下层的离开页文字叠印。
                                            // 放在 graphicsLayer 内侧：底随页位移一起走。
                                            .background(theme.background)
                                            .drawBehind {
                                                // 进入页靠近屏内一侧的边缘阴影（随盖入进度），
                                                // 落在进入页纸面上，增强纸张层次
                                                if (index == coverEnteringPage) {
                                                    val fraction = pagerState.currentPageOffsetFraction
                                                    if (fraction != 0f) {
                                                        val edgeW = (kotlin.math.abs(fraction) * size.width)
                                                            .coerceAtMost(32.dp.toPx())
                                                        if (edgeW > 1f) {
                                                            val left = fraction > 0f
                                                            drawRect(
                                                                brush = Brush.horizontalGradient(
                                                                    colors = if (left) {
                                                                        listOf(
                                                                            Color.Black.copy(alpha = 0.20f),
                                                                            Color.Transparent,
                                                                        )
                                                                    } else {
                                                                        listOf(
                                                                            Color.Transparent,
                                                                            Color.Black.copy(alpha = 0.20f),
                                                                        )
                                                                    },
                                                                    startX = if (left) 0f else size.width - edgeW,
                                                                    endX = if (left) edgeW else size.width,
                                                                ),
                                                                topLeft = Offset(if (left) 0f else size.width - edgeW, 0f),
                                                                size = Size(edgeW, size.height),
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                    ) { spreadBody() }
                                } else {
                                    spreadBody()
                                }
                            }
                        } else {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
                                // 覆盖档：拖拽跟手关闭——横向滑动由根层识别后直接播覆盖动画
                                userScrollEnabled = !coverEnabled && previewCharStart < 0,
                            ) { page ->
                                val pageBody: @Composable () -> Unit = {
                                    NovelPageContent(
                                        page = pages[page],
                                        prefs = prefs,
                                        theme = theme,
                                        fontFamily = fontFamily,
                                        contentPadding = pageContentPadding,
                                        availHeightPx = heightPx,
                                        annotations = annotations,
                                        selection = displaySelection,
                                        bubble = bubble,
                                        bubbleAnnotation = bubbleAnnotation,
                                        callbacks = pageCallbacks,
                                        dragging = dragging,
                                        dragSide = dragSide,
                                        onImageTap = openImagePreview,
                                    )
                                }
                                if (coverEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .zIndex(if (page == coverEnteringPage) 1f else 0f)
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                val fraction = pagerState.currentPageOffsetFraction
                                                if (fraction != 0f) {
                                                    val current = pagerState.currentPage
                                                    val target = pagerState.targetPage
                                                    val forward = target > current ||
                                                        (target == current && fraction < 0f)
                                                    val position = current + fraction
                                                    val leaving = (if (forward) floor(position) else ceil(position))
                                                        .toInt()
                                                    if (page == leaving) {
                                                        translationX = (position - leaving) * size.width
                                                    }
                                                }
                                            }
                                            // 同双页：每页自带不透明底（防盖入叠印）+ 进入页边缘阴影
                                            .background(theme.background)
                                            .drawBehind {
                                                if (page == coverEnteringPage) {
                                                    val fraction = pagerState.currentPageOffsetFraction
                                                    if (fraction != 0f) {
                                                        val edgeW = (kotlin.math.abs(fraction) * size.width)
                                                            .coerceAtMost(32.dp.toPx())
                                                        if (edgeW > 1f) {
                                                            val left = fraction > 0f
                                                            drawRect(
                                                                brush = Brush.horizontalGradient(
                                                                    colors = if (left) {
                                                                        listOf(
                                                                            Color.Black.copy(alpha = 0.20f),
                                                                            Color.Transparent,
                                                                        )
                                                                    } else {
                                                                        listOf(
                                                                            Color.Transparent,
                                                                            Color.Black.copy(alpha = 0.20f),
                                                                        )
                                                                    },
                                                                    startX = if (left) 0f else size.width - edgeW,
                                                                    endX = if (left) edgeW else size.width,
                                                                ),
                                                                topLeft = Offset(if (left) 0f else size.width - edgeW, 0f),
                                                                size = Size(edgeW, size.height),
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                    ) { pageBody() }
                                } else {
                                    pageBody()
                                }
                            }
                        }
                        }
                    }
                }

                // M6-G 跨章过渡覆盖层：旧页快照按档位退场（画在页面之上）。
                // 覆盖档不走这里——旧页由上面的底层 Canvas 垫在新内容之下。
                chapterFlip?.let { anim ->
                    if (anim.mode != SettingsKeys.PAGE_TRANSITION_COVER) {
                        Canvas(Modifier.fillMaxSize()) {
                            val p = anim.progress.value.coerceIn(0f, 1f)
                            if (anim.mode == SettingsKeys.PAGE_TRANSITION_CURL) {
                                drawChapterCurl(
                                    snapshot = anim.snapshot,
                                    progress = p,
                                    forward = anim.forward,
                                    paper = theme.background,
                                )
                            } else {
                                val d = if (anim.forward) 1f else -1f
                                if (anim.vertical) {
                                    translate(top = -d * p * size.height) {
                                        drawImage(anim.snapshot)
                                    }
                                } else {
                                    translate(left = -d * p * size.width) {
                                        drawImage(anim.snapshot)
                                    }
                                }
                            }
                        }
                    }
                }

                }

            // 色温叠加层（移植自漫画）：暖橙/冷蓝半透明罩，画在正文之上、顶底栏之下。
            if (novelColorTemp != 0f) {
                val tempAlpha = (abs(novelColorTemp) * 0.18f).coerceIn(0f, 0.18f)
                val tempColor = if (novelColorTemp > 0f) Color(0xFFFF8A00) else Color(0xFF4A90FF)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(tempColor.copy(alpha = tempAlpha)),
                )
            }

            // 拖动反馈 HUD（移植自漫画）：亮度/色温胶囊，拖动每帧更新、松手 900ms 淡出。
            AnimatedVisibility(
                visible = novelHudText != null,
                enter = fadeIn(tween(100)) + scaleIn(tween(120), initialScale = 0.96f),
                exit = fadeOut(tween(80)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = panelColor(theme),
                    border = BorderStroke(1.dp, novelOutline(theme)),
                ) {
                    Text(
                        // 显示中直接读实时文案（保留变量晚一帧，会闪一下空胶囊）；
                        // 退场时 novelHudText 已置空，用保留值撑完淡出
                        text = novelHudText ?: novelHudHeld,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.text,
                    )
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
                    pageLabel = if (pagingScroll) {
                        // M6.5 scroll 档：当前页 = 视口首可见页（读 LazyList 状态，翻页即重组）。
                        "P${scrollCurrentPageIndexOf() + 1}/${pages.size}"
                    } else if (useDouble) {
                        val first = pagerState.currentPage * 2 + 1
                        val second = first + 1
                        if (second <= pages.size) "P$first-$second/${pages.size}" else "P$first/${pages.size}"
                    } else {
                        "P${pagerState.currentPage + 1}/${pages.size}"
                    },
                    fraction = ((if (pagingScroll) scrollCurrentPageIndexOf() + 1 else pagerState.currentPage + 1).toFloat() /
                        (if (useDouble) spreads.size else pages.size).coerceAtLeast(1))
                        .coerceIn(0f, 1f),
                    theme = theme,
                    onSearch = {
                        chapterListOpen = false
                        searchPanelOpen = true
                    },
                    onChapterList = {
                        quickPanel = false
                        chapterListOpen = true
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
                        scope.launch { settlePage(0) }
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
                    val page = pages.getOrNull(
                        if (pagingScroll) scrollCurrentPageIndexOf() else pagerState.currentPage,
                    )
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
                prefsSolo = prefsSolo,
                onToggleSolo = { enable ->
                    scope.launch {
                        val snapshot = withContext(Dispatchers.IO) { settingsRepository.snapshotPreferences() }
                        settingsRepository.putBoolean(SettingsKeys.novelBookSoloKey(bookId), enable)
                        if (enable) {
                            // 打开瞬间：沿用本书历史快照（保留此前单独调过的偏好），无快照则以当前值为起点。
                            val perBook = settingsRepository.stringOf(snapshot, SettingsKeys.novelBookKey(bookId), "")
                            if (perBook.isNotBlank()) {
                                runCatching {
                                    prefs = kotlinx.serialization.json.Json.decodeFromString(
                                        NovelReadingPrefs.serializer(),
                                        perBook,
                                    )
                                }
                            }
                        }
                        prefsSolo = enable
                    }
                },
                doublePageMode = doublePageMode,
                onSetDoublePageMode = { value ->
                    // 立即同步本地状态（面板即时生效），异步持久化。
                    doublePageMode = value
                    scope.launch { settingsRepository.putString(SettingsKeys.NOVEL_DOUBLE_PAGE, value) }
                },
                pageTransitionMode = pageTransitionMode,
                onSetPageTransition = { value ->
                    // 立即同步本地状态（面板即时生效），异步持久化。
                    pageTransitionMode = value
                    scope.launch { settingsRepository.putString(SettingsKeys.NOVEL_PAGE_TRANSITION, value) }
                },
                onPickTheme = { value ->
                    // 跟随生效期间手动选浅色档 = 用户接管：自动关闭跟随（与设置项同步），选中即生效。
                    if (darkFollowActive && value != SettingsKeys.NOVEL_THEME_DARK) {
                        darkFollow = false
                        scope.launch { settingsRepository.putBoolean(SettingsKeys.NOVEL_DARK_FOLLOW, false) }
                    }
                    prefs = prefs.copy(theme = value)
                },
                effectiveThemeId = effectiveThemeId,
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

            // 深色跟随首次提示：居中玻璃卡（搜索面板/笔记面板同款结构），点「知道了」关闭；
            // 标记已在触发 effect 落盘，此后不再出现。
            if (darkFollowHintOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .pointerInput(Unit) {
                            detectTapGestures { darkFollowHintOpen = false }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = panelColor(theme),
                        border = BorderStroke(1.dp, novelOutline(theme)),
                        modifier = Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "背景已自动切换",
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.text,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "App 处于深色模式，小说背景已自动切换为墨黑；回到浅色模式时会自动恢复你原来的背景档。不需要自动切换的话，可以到 设置-外观 关闭「深色时小说背景自动切换」，或在阅读器排版面板里直接关闭。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.secondary,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "知道了",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { darkFollowHintOpen = false }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

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

            // M6.4 插图全屏预览：挂在根 Box 最上层（chrome/面板之后），内部自带 BackHandler。
            if (previewCharStart >= 0 && chapterImages.isNotEmpty()) {
                NovelImagePreviewOverlay(
                    images = chapterImages,
                    initialCharStart = previewCharStart,
                    bookTitle = book?.title ?: "插图",
                    chapterIndex = chapterIndex,
                    saving = previewSaving,
                    savedCharStart = previewSavedCharStart,
                    onClose = {
                        previewCharStart = -1
                        previewSavedCharStart = -1
                    },
                    onSave = { item, _ -> savePreviewImage(item) },
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
    // M6.3-E-F 注销带属主检查：跨章后不同章首页 charStart 同 key，无条件 remove
    // 会误删新页的注册（拖不动根因）。
    val unregisterConverter: (Int, (Offset) -> Int?) -> Unit,
    val onDragActive: (String?) -> Unit,
    // M6.3-E 全书级拦截层支持：可见页锚点/边界句柄注册表。
    // M6.3-E-F keys 单点来源：抓取判定/边界查询/convertAt 白名单共用。
    val visiblePageKeys: () -> List<Int>,
    val visiblePageBounds: () -> List<PageAnchorHandle>,
    val registerAnchor: (Int, PageAnchorHandle) -> Unit,
    val unregisterAnchor: (Int, PageAnchorHandle) -> Unit,
    // M6.3-E-H 02 修复：气泡归属唯一 owner——screen 级计算 focusChar/标注所在页，
    // 各页只判 page.charStart == owner，杜绝双页跨页选区时两页各挂一个气泡。
    val bubbleOwnerCharStart: () -> Int?,
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
    /** 可用页高（分页预算 heightPx 同源）：配合 page.contentHeightPx 把装填余数平分上下；<=0 不启用。 */
    availHeightPx: Int = 0,
    modifier: Modifier = Modifier,
    annotations: List<AnnotationRecord> = emptyList(),
    selection: NovelSelection? = null,
    bubble: NovelBubble? = null,
    bubbleAnnotation: AnnotationRecord? = null,
    callbacks: NovelPageCallbacks? = null,
    dragging: Boolean = false,
    dragSide: String = "",
    /** M7 搜索定位一次性高亮（章内绝对区间；本页章节命中才传）。 */
    locateHighlight: IntRange? = null,
    /**
     * 是否接收正文手势。仿真档的 pagecurl 容器会同时叠 3 层内容（current+1 / current /
     * current-1），静止时 current-1 层在最上——它虽然 drawNothing 不绘制，但仍参与命中测试，
     * 会抢走长按/拖动（表现为长按选中的是上一页的字符、手柄锚点 miss、拖不动）。
     * 因此只有 index == 当前页的那一层挂手势，其余层只绘制不接收输入。
     */
    inputEnabled: Boolean = true,
    /** 插图点击（relPath, 章内 charStart）：非空时图片区接收点击并打开预览。 */
    onImageTap: ((String, Int) -> Unit)? = null,
) {
    val gesturesOn = inputEnabled && callbacks != null && callbacks.annotationsEnabled()
    val layouts = remember(page) { arrayOfNulls<TextLayoutResult>(page.slices.size) }
    // slicePos = Text 相对 content root Box 内容区的坐标（手柄/气泡 offset 与其同坐标系，
    // 禁止混用 positionInRoot 窗口坐标，否则 padding 双计导致手柄整体漂移）。
    val slicePos = remember(page) { arrayOfNulls<Offset>(page.slices.size) }
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val rootPos = remember { mutableStateOf(Offset.Zero) } // 本页相对窗口（convert 屏幕换算用）
    // 主题色（强调色）：选区带/搜索命中底色跟主题色走（原写死浅蓝/橙）。
    val accent = MaterialTheme.colorScheme.primary

    // M6.3 选区为章节绝对 char：渲染按本页 slice 交集天然支持跨页；
    // 圆点按端点归属页各自渲染（startAbs / endAbs-1 落在本页才画）。
    val sel = selection
    val selLo = sel?.let { minOf(it.startAbs, it.endAbs) }
    val selHiEx = sel?.let { maxOf(it.startAbs, it.endAbs) }
    val selAbs: IntRange? = if (sel != null && selHiEx!! > selLo!!) selLo until selHiEx else null
    // M6.3-E-H 02 修复：气泡归属 screen 级唯一 owner（focusChar/标注所在页），
    // 双页跨页选区时两页各自自判会出现两个气泡——改为只判 owner 页。
    val bubbleOnPage = if (bubble != null &&
        callbacks?.bubbleOwnerCharStart() == page.charStart
    ) bubble else null

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
                    // 选区带跟主题色（原写死浅蓝）
                    SpanStyle(background = accent.copy(alpha = 0.20f)),
                    s - cs,
                    e - cs,
                )
            }
        }
        // M7 搜索定位一次性高亮（定位态 highlight，样式对齐标注高亮）
        locateHighlight?.let { range ->
            val s = max(range.first, cs)
            val e = min(range.last + 1, ce)
            if (e > s) {
                addStyleSafe(
                    // 搜索命中一次性高亮跟主题色（原写死橙）
                    SpanStyle(background = accent.copy(alpha = 0.28f)),
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
        if (best == null) {
            // M6.3-E-F 页尾下方空白吸附：手指落在最后文本 slice 底部之下（段间距/
            // 图片区上沿）但未远离本页时，吸附到末行末字符——「拖到页尾停住跨章」
            // 依赖 convert 返回页尾字符，严格范围判定会让手指差几像素全 miss。
            var lastTextIdx = -1
            var lastTextLayout: TextLayoutResult? = null
            for (i in page.slices.indices) {
                val l = layouts[i] ?: continue
                if (page.slices[i].imagePath != null) continue
                lastTextIdx = i
                lastTextLayout = l
            }
            val l = lastTextLayout
            if (lastTextIdx >= 0 && l != null) {
                val p = slicePos[lastTextIdx] ?: return null
                val local = posInRoot - p
                val belowY = local.y - l.size.height
                // TXT 页尾留白大（段落不拆页）：手指拖到视觉页底可能距末行
                // 数百像素——不再限 120px，末行之下、页内 x 范围全部吸附。
                if (belowY >= 0f &&
                    local.x >= -40f && local.x <= l.size.width + 160f
                ) {
                    val lastLine = l.lineCount - 1
                    val hi = max(l.getLineEnd(lastLine), l.getLineStart(lastLine))
                    return lastTextIdx to (hi - 1).coerceAtLeast(0)
                }
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
        // 微信读书样式：圆点无竖线；开头（isStart=true，渲染恒传 lo）圆点悬在
        // 首行顶上方，结尾圆点悬在末行底下方——上下夹住选区，比两圆同线更直觉。
        val line = l.getLineForOffset(o)
        val lineBottom = l.getLineBottom(line)
        val lineTop = l.getLineTop(line)
        // M6.3-F：悬停间距按端不同——行距额外空间全部在每行字形下方（descent
        // 侧）且两行行框紧贴，同值下观感不对称（真机实测：end 圆贴住下一行
        // 字形顶、start 圆悬在上一行行距区）。定案：start 5dp——圆点底落进
        // 首行行框顶 2dp、贴住选区首字顶（会轻微覆盖上一行字形底部，行距越
        // 小越明显，微信读书同款行为）；end 维持 9dp（已天然贴住下一行）。
        // M6.3-F 三调：start 3dp→2dp（用户微调定档）。
        // M6.3-H：end 8dp（用户多轮定档：18dp 起步→16dp→12dp→10dp→8dp；
        // 「跟 start 差不多的悬空」，start 2dp 不动）。
        val embed = with(density) { (if (isStart) 2.dp else 8.dp).toPx() }
        val x = if (isStart) box.left else box.right
        val y = if (isStart) lineTop - embed else lineBottom + embed
        return Offset(p.x + x, p.y + y)
    }

    fun handleAnchorAbs(abs: Int, isStart: Boolean): Offset? {
        val located = locateAbs(abs)
        if (located == null) {
            android.util.Log.d(
                "CROSSDRAG",
                "GANCH miss abs=$abs isStart=$isStart pageStart=${page.charStart} " +
                    "pageEnd=${page.charEnd} slices=${page.slices.size}",
            )
            return null
        }
        val (idx, off) = located
        return handleAnchor(idx, off, isStart)
    }

    // 拖动手柄期间隐藏气泡（dragging 由 screen 持有、拦截层经 callbacks.onDragActive 置位）。
    var bubbleMeasuredH by remember { mutableStateOf(0f) }
    val rootSize = remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    // M6.3-E-I：注册通道从「effect 副作用」改为「与组合同生死」——真机实测
    // （CROSSDRAG map 身份对账）证明 DisposableEffect 的注册写入在快排→全排
    // 页对象替换 / beyond 窗口快速滑动时序下会与组合状态一致性断裂：页实例
    // 存活（渲染正常）但注册在全局快照中丢失 → 抓取判定恒失败且无注销痕迹。
    // remember(page) 初始化随组合 apply 提交、与页面生死严格同步；页根
    // onGloballyPositioned 再做幂等 re-register 作布局期自愈。
    val anchorRegistration = remember(page) {
        callbacks?.let { cb ->
            val fn: (Offset) -> Int? = { screenPos ->
                convertRootToSliceOffset(screenPos - rootPos.value)?.let { (si, off) ->
                    page.slices.getOrNull(si)?.let { it.charStart + off }
                }
            }
            val h = PageAnchorHandle(
                charStart = page.charStart,
                charEnd = page.charEnd,
                // M6.3-E-F 图片段无字符锚点（convertAt 对其返回 null），拖 end 到
                // 页尾只能停在最后文本字符——atTail 判定须用 lastTextEnd 而非
                // charEnd，否则含图片段的页「页尾停住翻页/跨章」永不可达。
                lastTextEnd = page.slices.lastOrNull {
                    it.imagePath == null && it.text.isNotEmpty()
                }?.charEnd ?: page.charEnd,
                anchor = { abs, isStart -> handleAnchorAbs(abs, isStart) },
                // origin 实时读取：首帧 Zero 也不会把过期值带进换算。
                origin = { rootPos.value },
            )
            cb.registerConverter(page.charStart, fn)
            cb.registerAnchor(page.charStart, h)
            android.util.Log.d(
                "CROSSDRAG",
                "GREG reg key=${page.charStart} obj=${System.identityHashCode(h)} " +
                    "pageEnd=${page.charEnd}",
            )
            fn to h
        }
    }

    DisposableEffect(page, anchorRegistration) {
        onDispose {
            val cb = callbacks
            val reg = anchorRegistration
            if (cb != null && reg != null) {
                val (fn, h) = reg
                cb.unregisterConverter(page.charStart, fn)
                android.util.Log.d(
                    "CROSSDRAG",
                    "GUNREG dispose key=${page.charStart} obj=${System.identityHashCode(h)}",
                )
                cb.unregisterAnchor(page.charStart, h)
            }
            // M6.3 兜底：极端情况下（快速连续翻页销毁本页）拖动协程可能被取消
            // 而来不及调 onDragActive(null)，dragging=true 残留会让气泡永久隐藏。
            if (cb?.dragging() == true) cb.onDragActive(null)
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
        val rootH = rootSize.value.height.toFloat().coerceAtLeast(bubbleH + 16f)
        val gap = with(density) { 8.dp.toPx() }
        val preferAbove = selTop - bubbleH - gap > 0f
        // M6.3-E-H 01 修复：y 无 clamp 时跨页选区拖到最后一行，气泡（selBottom+gap）
        // 直接超出屏幕——x 有 clamp 而 y 漏了，补齐。
        val y = (if (preferAbove) {
            selTop - bubbleH - gap
        } else {
            selBottom + gap
        }).coerceIn(8f, rootH - bubbleH - 8f)
        val x = (anchorX - bubbleW / 2).coerceIn(8f, (rootW - bubbleW - 8f).coerceAtLeast(8f))
        return Offset(x, y)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            // M6.3-E 手柄拖动拦截层已上移至 NovelReaderScreen 根 Box（全书级）：
            // 章切换销毁本页不再中断拖动协程，跨章延伸在此架构下才可行。
            // 本页仅保留正文手势（长按选区/点击划线）与手柄/气泡渲染。
            .onGloballyPositioned {
                rootPos.value = it.positionInRoot()
                rootSize.value = it.size
                // M6.3-E-I 布局期注册自愈：幂等 re-register——注册写入若因
                // effect/组合时序断裂丢失，下一次布局 pass 即恢复（抓取判定
                // 前必有布局 pass，选区交互时页面布局早已稳定）。
                val reg = anchorRegistration
                val cb = callbacks
                if (reg != null && cb != null) {
                    val (fn, h) = reg
                    cb.registerConverter(page.charStart, fn)
                    cb.registerAnchor(page.charStart, h)
                }
            },
    ) {
        // M6.3-E-H 06 方案 A：分页按整行/整段装页，页高除不尽的余数原本全堆在页底
        // （顶部精确 = topPad，底部 = bottomPad + 余数）→ 恒定「下留空大于上」。
        // 余数一半垫到首行上方，上下视觉对称；页内坐标（slicePos/手柄/气泡）基于实测自动跟随。
        val pageRemPx = if (availHeightPx > 0 && page.contentHeightPx > 0) {
            (availHeightPx - page.contentHeightPx).coerceAtLeast(0)
        } else {
            0
        }
        val topExtraDp = with(density) { (pageRemPx / 2).toDp() }
        // M6.3-I：垫高从「Column 自身 padding(top)」改为「首部 Spacer 占位」——
        // 短章留空页光标/气泡整体上移一段的根因：slicePos（positionInParent）
        // 实测不包含 Column 自身 padding 的垫高（正常满页余数≈0 掩盖断裂）。
        // Spacer 是实打实的兄弟占位节点，positionInParent 必然包含它，
        // slicePos 天然含垫高——圆点/气泡/正文换算全部自动归位，视觉不变。
        Column(
            verticalArrangement = Arrangement.spacedBy(prefs.paragraphSpacing.dp),
        ) {
            Spacer(Modifier.height(topExtraDp))
            page.slices.forEachIndexed { i, slice ->
                val imagePath = slice.imagePath
                if (imagePath != null) {
                    NovelPageImage(
                        path = imagePath,
                        heightPx = slice.heightPx,
                        theme = theme,
                        inputEnabled = inputEnabled,
                        onTap = onImageTap?.let { tap ->
                            { tap(imagePath, slice.charStart) }
                        },
                    )
                } else {
                    val badge = annotations.firstOrNull {
                        it.note != null && it.charEnd > slice.charStart && it.charEnd <= slice.charEnd
                    }
                    Text(
                        text = annotatedSlice(i, slice),
                        // M6.4-K：续片渲染去首行缩进——与分页侧续片重测口径一致，
                        // 跨页续行的换行才与整块布局相同（否则页高超出预算、页尾裁切），
                        // 同时消除续页页首不该有的缩进。
                        style = if (slice.isBlockStart) {
                            novelTextStyle(prefs, theme, fontFamily)
                        } else {
                            novelTextStyle(prefs, theme, fontFamily)
                                .copy(textIndent = TextIndent(firstLine = 0.sp))
                        },
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
                                                val longPressSlop = viewConfiguration.touchSlop
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
                                                    // 关键修复：位移超过 touch slop 就是拖动（翻页/卷曲），
                                                    // 放弃长按。否则慢速拖过 longPressTimeout 会被误判成长按
                                                    // → 建选区 + 后续 move 被长按循环 consume → 库拖拽被判取消
                                                    // （complete=false 回弹）+ 选区激活禁用拖拽 = 「拖不动/卡住」。
                                                    if ((change.position - down.position).getDistance() >
                                                        longPressSlop
                                                    ) {
                                                        stolen = true
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
                // M6.3-E-H 02：气泡渲染必须挂在 owner 页（screen 级唯一归属）——
                // 旧条件只判 !dragging，跨页选区时两页各挂一个气泡。
                if (!dragging && bubbleOnPage != null && bubbleOnPage.mode == 0) {
                    android.util.Log.d(
                        "CROSSDRAG",
                        "bubble page=${page.charStart} mode=0 owner=" +
                            "${callbacks?.bubbleOwnerCharStart()}",
                    )
                    // 气泡以「本页可见端点的选区边界」放置：selTop = 首行顶、selBottom = 末行底。
                    val lineH = with(density) {
                        (prefs.fontSize * prefs.lineHeightRatio).dp.toPx()
                    }
                    val embed = with(density) { 2.dp.toPx() }
                    // 本页只有单端（跨页选区）时：selTop 不得硬编码 0f——那会让
                    // preferAbove 恒 false，气泡恒放末行下方并溢屏（01 问题）；
                    // 改用 endPos 上方两行作为本页选区上界近似。
                    val selTop = startPos?.y?.minus(lineH + embed)
                        ?: endPos?.y?.minus(lineH * 2 + embed)
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
    // 主题色（强调色）：原写死 #2196F3 蓝，改为跟随软件主题色。
    val tint = MaterialTheme.colorScheme.primary
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

/**
 * 插画位图会话级缓存（键 = 绝对路径，容量按字节封顶 32MB）。
 * 翻页 / 仿真卷曲动画期间页面组合件会重建，命中缓存即零延迟上屏；
 * 否则每次重建都会重启解码（10~50ms），图片页会闪出「[插图]」占位。
 * 离开阅读器时清空（见 NovelReaderScreen 的 DisposableEffect）。
 */
internal object NovelImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.asAndroidBitmap().allocationByteCount
    }

    fun get(path: String): ImageBitmap? = cache.get(path)

    fun put(path: String, bitmap: ImageBitmap) {
        cache.put(path, bitmap)
    }

    fun clear() = cache.evictAll()
}

/** M6.2-B：EPUB 图片切片渲染；解码失败显示占位。M6.4：点击打开全屏预览。 */
@Composable
private fun NovelPageImage(
    path: String,
    heightPx: Int,
    theme: NovelTheme,
    inputEnabled: Boolean = true,
    onTap: (() -> Unit)? = null,
) {
    // pointerInput 协程不随重组重启，点击回调用 rememberUpdatedState 取最新值。
    val currentOnTap by rememberUpdatedState(onTap)
    val density = LocalDensity.current
    val heightDp = with(density) { heightPx.coerceAtLeast(1).toDp() }
    // marker 存的是 filesDir 相对路径（epub_images/{bookId}/xxx），渲染时解析为绝对路径。
    val context = LocalContext.current
    val absolutePath = remember(path) { File(context.filesDir, path).absolutePath }
    // 缓存优先：含首次组合即命中（翻回看过的图 / 从邻页槽升为当前页）。
    var bitmap by remember(absolutePath) { mutableStateOf(NovelImageCache.get(absolutePath)) }
    LaunchedEffect(absolutePath) {
        if (bitmap != null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
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
        if (decoded != null) NovelImageCache.put(absolutePath, decoded)
        bitmap = decoded
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .then(
                if (inputEnabled && onTap != null) {
                    Modifier.pointerInput(path) {
                        awaitEachGesture {
                            // 插图点击：down 阶段消费即阻断父级 detectTapGestures
                            // （根层分区翻页/呼 chrome 的 awaitFirstDown 要求未被消费）；
                            // Pager 与 pagecurl 的拖拽均为 requireUnconsumed=false，
                            // 从图片区起手滑动仍能正常翻页/卷曲。
                            val down = awaitFirstDown(requireUnconsumed = true)
                            down.consume()
                            // 拖动/滑动过程中事件被消费 → 返回 null，不当作点击。
                            waitForUpOrCancellation() ?: return@awaitEachGesture
                            currentOnTap?.invoke()
                        }
                    }
                } else {
                    Modifier
                },
            ),
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
    /** 可用页高（分页预算同源），转发给两半页做余数平分。 */
    availHeightPx: Int = 0,
    annotations: List<AnnotationRecord> = emptyList(),
    selection: NovelSelection? = null,
    bubble: NovelBubble? = null,
    bubbleAnnotation: AnnotationRecord? = null,
    callbacks: NovelPageCallbacks? = null,
    dragging: Boolean = false,
    dragSide: String = "",
    locateHighlight: IntRange? = null,
    /** 同 NovelPageContent：仿真档非当前 spread 层只绘制、不接收正文手势。 */
    inputEnabled: Boolean = true,
    /** 插图点击（两半页各自上报自己的 charStart）。 */
    onImageTap: ((String, Int) -> Unit)? = null,
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
                availHeightPx = availHeightPx,
                modifier = Modifier.weight(1f),
                annotations = annotations,
                selection = selection,
                bubble = bubble,
                bubbleAnnotation = bubbleAnnotation,
                callbacks = callbacks,
                dragging = dragging,
                dragSide = dragSide,
                locateHighlight = locateHighlight,
                inputEnabled = inputEnabled,
                onImageTap = onImageTap,
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
                availHeightPx = availHeightPx,
                modifier = Modifier.weight(1f),
                annotations = annotations,
                selection = selection,
                bubble = bubble,
                bubbleAnnotation = bubbleAnnotation,
                callbacks = callbacks,
                dragging = dragging,
                dragSide = dragSide,
                locateHighlight = locateHighlight,
                inputEnabled = inputEnabled,
                onImageTap = onImageTap,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelTopBar(
    title: String,
    chapterTitle: String,
    theme: NovelTheme,
    onBack: () -> Unit,
    onBookmarks: () -> Unit,
    onAnnotations: () -> Unit,
    onSettings: () -> Unit,
) {
    // 菜单栏在场时状态栏也被唤出（沉浸），顶栏必须避让它。
    // 取「忽略可见性」的高度：状态栏显/隐切换的过渡帧里偏移恒定，不会先贴顶再下移；
    // 首帧 insets 尚未派发时会报 0，回落书架玻璃头持续写入的进程级记忆值。
    val liveStatusBarTop = WindowInsets.statusBarsIgnoringVisibility
        .asPaddingValues()
        .calculateTopPadding()
    val statusBarTop = if (liveStatusBarTop > 0.dp) {
        liveStatusBarTop
    } else {
        LibraryStatusBar.statusBarTopPx.dp
    }
    // 主题色（强调色）：图标跟主题色走，标题/章节名保持中性保证可读性。
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .padding(top = statusBarTop + 12.dp, start = 12.dp, end = 12.dp)
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = accent)
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
                Icon(Icons.Filled.BookmarkBorder, contentDescription = "书签", tint = accent)
            }
            IconButton(onClick = onAnnotations) {
                Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "注释", tint = accent)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "排版", tint = accent)
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
    /** 目录入口挪到底栏（顶栏只保留排版设置，用户 2026-11-25 定）。 */
    onChapterList: () -> Unit,
) {
    // 主题色（强调色）：进度条与图标跟主题色走，章节/页码文本保持中性。
    val accent = MaterialTheme.colorScheme.primary
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
            // 间距再平衡（方案一）：左起点 12dp 给文字呼吸感；统一 12dp 间隔；
            // 两个图标收进零间距组（48dp 槽自带 12dp 内边距，字形间距 24dp 为标准节奏，
            // 原 spacedBy 8 时是 32dp）；行尾 6dp + 槽内边距 = 字形距右缘 18dp，与左端接近对称。
            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "第 ${chapterIndex + 1}/$chapterCount 章",
                style = MaterialTheme.typography.labelMedium,
                color = theme.text,
            )
            BoxWithConstraints(
                modifier = Modifier.weight(1f).height(28.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
            Text(
                text = pageLabel,
                style = MaterialTheme.typography.labelMedium,
                color = theme.text,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索", tint = accent)
                }
                IconButton(onClick = onChapterList) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "目录", tint = accent)
                }
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
                    text = "还没有书签\n点「＋ 当前位置」收藏当前阅读位置",
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

    // M6.3 收口：搜索面板改居中卡片（笔记弹窗同款）——
    // 底部卡的 imePadding 实测被键盘穿透（面板被完全盖住），
    // 居中卡片的 imePadding 实测生效。
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(120, easing = FastOutLinearInEasing)) +
            scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(panelColor(theme))
                    .border(1.dp, novelOutline(theme), RoundedCornerShape(20.dp)),
            ) {
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

/**
 * M6.5 纵向连续档的跨章哨兵行：列表顶部「上一章」/底部「下一章」的矮条入口。
 * 点击即跨章（复用 beginChapterFlip 快照过渡 + pendingJumpLastPage/pendingRestoreOffset
 * 落位链），enabled=false（首章/末章）时不渲染点击态。
 */
@Composable
private fun NovelScrollSentinelRow(
    label: String,
    enabled: Boolean,
    theme: NovelTheme,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary else theme.secondary,
        )
    }
}

@Composable
private fun NovelQuickPanel(
    visible: Boolean,
    prefs: NovelReadingPrefs,
    prefsSolo: Boolean,
    onToggleSolo: (Boolean) -> Unit,
    doublePageMode: String,
    onSetDoublePageMode: (String) -> Unit,
    pageTransitionMode: String,
    onSetPageTransition: (String) -> Unit,
    onPickTheme: (String) -> Unit,
    effectiveThemeId: String,
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
        NovelSettingOption(SettingsKeys.NOVEL_THEME_KRAFT, "牛皮纸"),
        NovelSettingOption(SettingsKeys.NOVEL_THEME_GREEN, "护眼绿"),
        NovelSettingOption(SettingsKeys.NOVEL_THEME_MIST, "雾蓝"),
        NovelSettingOption(SettingsKeys.NOVEL_THEME_SAKURA, "樱粉"),
        NovelSettingOption(SettingsKeys.NOVEL_THEME_DARK, "墨黑"),
        NovelSettingOption(SettingsKeys.NOVEL_THEME_SLATE, "深灰蓝"),
    )
    val pagingOptions = listOf(
        NovelSettingOption(SettingsKeys.NOVEL_PAGE_HORIZONTAL, "左右翻页"),
        NovelSettingOption(SettingsKeys.NOVEL_PAGE_VERTICAL, "上下翻页"),
        NovelSettingOption(SettingsKeys.NOVEL_PAGE_SCROLL, "纵向连续"),
    )
    val doublePageOptions = listOf(
        NovelSettingOption(SettingsKeys.DOUBLE_PAGE_AUTO, "宽屏自动"),
        NovelSettingOption(SettingsKeys.DOUBLE_PAGE_ALWAYS, "始终双页"),
        NovelSettingOption(SettingsKeys.DOUBLE_PAGE_OFF, "关闭"),
    )
    val pageTransitionOptions = listOf(
        NovelSettingOption(SettingsKeys.PAGE_TRANSITION_INSTANT, "无动画"),
        NovelSettingOption(SettingsKeys.PAGE_TRANSITION_SLIDE, "平移"),
        NovelSettingOption(SettingsKeys.PAGE_TRANSITION_COVER, "覆盖"),
        NovelSettingOption(SettingsKeys.PAGE_TRANSITION_CURL, "仿真"),
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
            // 面板内未显式给 color 的 Text 统一跟随当前档正文色：InkvolTheme 不提供
            // LocalContentColor（默认纯黑），墨黑 / 深灰蓝档下会「黑字压深底」不可读。
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
                CompositionLocalProvider(LocalContentColor provides theme.text) {
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
                                "字体 / 字号 / 行距 / 字距 / 段距 / 缩进 / 边距 / 背景 / 翻页 / 双页",
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
                        title = "背景",
                        selectedId = effectiveThemeId,
                        options = themeOptions,
                        expandedKey = expandedKey,
                        key = "theme",
                        onToggleExpand = { expandedKey = it },
                        onPick = onPickTheme,
                    )
                    Spacer(Modifier.height(8.dp))
                    // 翻页动画为全局档（独立键）：仅点击翻页生效，拖拽与跳页保持瞬时。
                    // M6.5 纵向连续档没有翻页语义，隐藏本行（档位保留，切回翻页档恢复）。
                    if (prefs.pagingMode != SettingsKeys.NOVEL_PAGE_SCROLL) {
                        NovelSettingRow(
                            title = "动画",
                            selectedId = pageTransitionMode,
                            options = pageTransitionOptions,
                            expandedKey = expandedKey,
                            key = "pageTransition",
                            onToggleExpand = { expandedKey = it },
                            onPick = onSetPageTransition,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    NovelSettingRow(
                        title = "方向",
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
                    Spacer(Modifier.height(8.dp))
                    // M6.4-D 单独记忆：开 = 本书排版独立保存（改动只影响本书）；
                    // 关（默认）= 跟随全局默认，面板改动对所有书一起生效。整行单击切换。
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleSolo(!prefsSolo) }
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text("单独记忆", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            text = if (prefsSolo) "开启" else "关闭",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                }
            }
        }
    }
}
