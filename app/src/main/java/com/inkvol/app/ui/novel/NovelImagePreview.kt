package com.inkvol.app.ui.novel

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** 一处章内插图：relPath 与正文 [IMG:] marker 同源；charStart 用于定位（同图重复引用时唯一）。 */
internal data class NovelPreviewImage(
    val relPath: String,
    val charStart: Int,
)

/** 预览解码上限（长边）：正文缓存是 1440 采样，预览提到 2048 保留细节。 */
private const val PREVIEW_MAX_EDGE = 2048
private const val PREVIEW_MAX_SCALE = 5f

/**
 * 小说插图全屏预览（沉浸式，相册语义）：单击切换 chrome 显隐、未放大下滑关闭、
 * 章内左右滑切换、双指缩放、放大态单指平移、双击缩放/还原、底部保存。
 *
 * 手势分工：原始比例下图片层**不消费**横向单指事件（交还 [HorizontalPager] 翻图），
 * 但纵向占优且超阈值的下滑会消费并关闭；双指或已放大时消费（此时 pager 已由
 * userScrollEnabled 关闭）。
 */
@Composable
internal fun NovelImagePreviewOverlay(
    images: List<NovelPreviewImage>,
    initialCharStart: Int,
    bookTitle: String,
    chapterIndex: Int,
    saving: Boolean,
    savedCharStart: Int,
    onClose: () -> Unit,
    onSave: (NovelPreviewImage, Int) -> Unit,
) {
    if (images.isEmpty()) return
    val initialIndex = remember(images, initialCharStart) {
        images.indexOfFirst { it.charStart == initialCharStart }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // 沉浸 chrome：进入/切图/单击唤出时显示，2.5s 后自动淡出；单击在显示态直接隐藏。
    // chromeTick 作为计时重启信号（单击唤出、切图都推进）。
    var chromeShown by remember { mutableStateOf(true) }
    var chromeTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState.currentPage, chromeTick) {
        chromeShown = true
        delay(2500)
        chromeShown = false
    }

    // 切图回到原始比例（放大态跨页会让人以为图片被裁掉）。
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        pan = Offset.Zero
    }

    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 兜底层：消费本层所有指针事件。预览层是根 Box 的最后一个兄弟（最上层），
            // 其子树先处理、本修饰符后处理——消费 down 可让下层正文/图片手势
            // （awaitFirstDown 默认 requireUnconsumed=true）不启动；下层 Pager 另由
            // userScrollEnabled 关闭（消费标记不跨事件持久，单靠消费挡不住滚动）。
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = scale <= 1.001f,
        ) { page ->
            val item = images[page]
            NovelPreviewPage(
                relPath = item.relPath,
                scale = scale,
                pan = pan,
                onScale = { scale = it },
                onPan = { pan = it },
                // 单击 = 切换 chrome 显隐（不退出）；退出走 × / 返回键 / 下滑。
                onTap = {
                    if (chromeShown) {
                        chromeShown = false
                    } else {
                        chromeTick++
                    }
                },
                onClose = onClose,
            )
        }

        AnimatedVisibility(
            visible = chromeShown,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭预览", tint = Color.White)
                }
                Text(
                    text = bookTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${pagerState.currentPage + 1}/${images.size}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(4.dp))
            }
        }

        AnimatedVisibility(
            visible = chromeShown,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "第 ${chapterIndex + 1} 章 · 插图",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                )
                val current = images.getOrNull(pagerState.currentPage)
                val saved = current != null && savedCharStart == current.charStart
                Button(
                    onClick = {
                        if (current != null) {
                            onSave(current, images.indexOfFirst { it.charStart == current.charStart })
                        }
                    },
                    enabled = !saving && current != null,
                ) {
                    Text(if (saving) "保存中…" else if (saved) "已保存" else "保存")
                }
            }
        }
    }
}

@Composable
private fun NovelPreviewPage(
    relPath: String,
    scale: Float,
    pan: Offset,
    onScale: (Float) -> Unit,
    onPan: (Offset) -> Unit,
    onTap: () -> Unit,
    /** 未放大时下滑关闭（横向翻图交给 pager，互不抢占）。 */
    onClose: () -> Unit,
) {
    // 手势协程只随路径重启：回调用 rememberUpdatedState 取最新值。
    val onCloseRef by rememberUpdatedState(onClose)
    val context = LocalContext.current
    val absPath = remember(relPath) { File(context.filesDir, relPath).absolutePath }
    // 一级：正文渲染域已有 1440 采样缓存（点图瞬间必然命中），先上屏零等待；
    // 二级：后台按 2048 长边再解一次替换（不写回会话缓存，避免挤占正文预算）。
    var bitmap by remember(absPath) { mutableStateOf(NovelImageCache.get(absPath)) }
    var decodeFailed by remember(absPath) { mutableStateOf(false) }
    LaunchedEffect(absPath) {
        val decoded = decodePreviewImage(absPath)
        if (decoded != null) {
            bitmap = decoded
            decodeFailed = false
        } else if (bitmap == null) {
            // SVG 等 BitmapFactory 不支持的格式：明示不可预览，保存仍可另存原文件。
            decodeFailed = true
        }
    }

    // 手势协程只随路径重启：缩放/位移读最新值必须经 rememberUpdatedState。
    val scaleRef by rememberUpdatedState(scale)
    val panRef by rememberUpdatedState(pan)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded == null) {
            if (decodeFailed) {
                Text(
                    text = "该格式暂不支持预览\n可点下方「保存」另存原文件",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
            }
            return@Box
        }
        Image(
            bitmap = loaded,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scaleRef
                    scaleY = scaleRef
                    translationX = panRef.x
                    translationY = panRef.y
                }
                .pointerInput(absPath) {
                    // 下滑关闭阈值（与跨章滑动 56dp 同量级），方向占优防斜滑误触
                    val swipeCloseThreshold = 56.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                // 双指：缩放 + 平移，消费（pager 随即放弃本次拖拽）
                                val next = (scaleRef * event.calculateZoom())
                                    .coerceIn(1f, PREVIEW_MAX_SCALE)
                                onScale(next)
                                if (next > 1.001f) {
                                    onPan(clampPreviewPan(panRef + event.calculatePan(), next, size))
                                }
                                event.changes.forEach { it.consume() }
                            } else if (scaleRef > 1.001f) {
                                // 放大态单指：平移，消费（不触发下滑关闭，防误退）
                                onPan(
                                    clampPreviewPan(
                                        panRef + (pressed.first().position - last),
                                        scaleRef,
                                        size,
                                    ),
                                )
                                pressed.first().consume()
                            } else {
                                // 原始比例单指：横向交还 pager 翻图；纵向占优且超阈值 → 下滑关闭
                                val change = pressed.first()
                                if (change.isConsumed) {
                                    // pager 已接管（横向翻图），本次手势放弃
                                    return@awaitEachGesture
                                }
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (dy > swipeCloseThreshold && dy > kotlin.math.abs(dx) * 1.2f) {
                                    change.consume()
                                    onCloseRef()
                                    return@awaitEachGesture
                                }
                                // 未达阈值：不消费，继续观察（pager 翻图不受影响）
                            }
                            last = pressed.first().position
                        }
                    }
                }
                .pointerInput(absPath) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            if (scaleRef > 1.001f) {
                                onScale(1f)
                                onPan(Offset.Zero)
                            } else {
                                onScale(2.5f)
                            }
                        },
                    )
                },
        )
    }
}

private fun clampPreviewPan(pan: Offset, scale: Float, size: IntSize): Offset {
    val maxX = (scale - 1f) * size.width / 2f
    val maxY = (scale - 1f) * size.height / 2f
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

private suspend fun decodePreviewImage(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= PREVIEW_MAX_EDGE ||
            bounds.outHeight / (sample * 2) >= PREVIEW_MAX_EDGE
        ) {
            sample *= 2
        }
        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )?.asImageBitmap()
    }.getOrNull()
}
