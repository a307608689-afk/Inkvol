package com.inkvol.app.ui.reader.panel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inkvol.app.ui.theme.InkvolRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class SpikeCase(
    val title: String,
    val expected: PanelDetector.LayoutStatus,
    val thumbnail: Bitmap,
    val layout: PanelDetector.Layout,
) {
    val passed: Boolean get() = layout.status == expected
}

@Composable
fun PanelSpikeScreen(onBack: () -> Unit) {
    var cases by remember { mutableStateOf<List<SpikeCase>?>(null) }
    LaunchedEffect(Unit) {
        cases = withContext(Dispatchers.Default) { buildCases() }
    }

    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                )
            }
            Column {
                Text(
                    text = "分格检测实验",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "M5-A · 本地投影法 · 不接入阅读器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val loadedCases = cases
        if (loadedCases == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val passed = loadedCases.count { it.passed }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (passed == loadedCases.size) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.24f)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "通过 $passed / ${loadedCases.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "规整分格只做矩形投影，斜向 / 出血页必须整页兜底。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(loadedCases) { case ->
                    CaseCard(case)
                }
            }
        }
    }
}

@Composable
private fun CaseCard(case: SpikeCase) {
    Surface(
        shape = InkvolRadius.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = case.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "期望 ${statusLabel(case.expected)} · 实际 ${statusLabel(case.layout.status)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (case.passed) "通过" else "失败",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (case.passed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(case.thumbnail.width.toFloat() / case.thumbnail.height)
                    .clip(InkvolRadius.card)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = InkvolRadius.card,
                    ),
            ) {
                Image(
                    bitmap = case.thumbnail.asImageBitmap(),
                    contentDescription = case.title,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.matchParentSize(),
                )
                if (case.layout.status == PanelDetector.LayoutStatus.Panelized) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        case.layout.frames.forEach { frame ->
                            drawRect(
                                color = androidx.compose.ui.graphics.Color(0xFF34D399),
                                topLeft = Offset(
                                    x = frame.left * size.width,
                                    y = frame.top * size.height,
                                ),
                                size = Size(
                                    width = frame.width * size.width,
                                    height = frame.height * size.height,
                                ),
                                style = Stroke(width = 3.dp.toPx()),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "分格 ${case.layout.frames.size} · 置信度 ${(case.layout.confidence * 100).toInt()}% · " +
                    "${case.layout.elapsedMs}ms · ${reasonLabel(case.layout.reason)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusLabel(status: PanelDetector.LayoutStatus): String = when (status) {
    PanelDetector.LayoutStatus.Panelized -> "分格"
    PanelDetector.LayoutStatus.WholePage -> "整页"
}

private fun reasonLabel(reason: PanelDetector.FallbackReason?): String = when (reason) {
    null -> "成功"
    PanelDetector.FallbackReason.NoSplittableGutter -> "未发现可分割间隙"
    PanelDetector.FallbackReason.TooSmallFrames -> "分格过小"
    PanelDetector.FallbackReason.TooManyFrames -> "分格过多"
    PanelDetector.FallbackReason.LowConfidence -> "置信度不足"
    PanelDetector.FallbackReason.InvalidBitmap -> "无效图片"
    PanelDetector.FallbackReason.AnalysisFailed -> "分析失败"
}

private fun buildCases(): List<SpikeCase> {
    val width = 540
    val height = 810
    val specs = listOf(
        Triple("单格页（含留白）", PanelDetector.LayoutStatus.WholePage) {
            gridBitmap(width, height, columns = 1, rows = 1)
        },
        Triple("左右两格", PanelDetector.LayoutStatus.Panelized) {
            gridBitmap(width, height, columns = 2, rows = 1)
        },
        Triple("上下两格", PanelDetector.LayoutStatus.Panelized) {
            gridBitmap(width, height, columns = 1, rows = 2)
        },
        Triple("2×2 四格", PanelDetector.LayoutStatus.Panelized) {
            gridBitmap(width, height, columns = 2, rows = 2)
        },
        Triple("2×3 六格", PanelDetector.LayoutStatus.Panelized) {
            gridBitmap(width, height, columns = 3, rows = 2)
        },
        Triple("出血页", PanelDetector.LayoutStatus.WholePage) {
            gridBitmap(
                width = width,
                height = height,
                columns = 1,
                rows = 1,
                marginRatio = 0f,
            )
        },
        Triple("斜向分隔", PanelDetector.LayoutStatus.WholePage) {
            diagonalBitmap(width, height)
        },
        Triple("大图 2×2", PanelDetector.LayoutStatus.Panelized) {
            gridBitmap(1080, 1620, columns = 2, rows = 2)
        },
    )

    return specs.map { (title, expected, factory) ->
        val bitmap = factory()
        val layout = PanelDetector.analyze(
            bitmap = bitmap,
            readingDirection = PanelDetector.ReadingDirection.RightToLeft,
        )
        val thumbnailWidth = 320
        val thumbnailHeight = (thumbnailWidth.toFloat() / bitmap.width * bitmap.height).roundToInt()
        val thumbnail = Bitmap.createScaledBitmap(bitmap, thumbnailWidth, thumbnailHeight, true)
        if (thumbnail !== bitmap) bitmap.recycle()
        SpikeCase(title, expected, thumbnail, layout)
    }
}

private fun gridBitmap(
    width: Int,
    height: Int,
    columns: Int,
    rows: Int,
    marginRatio: Float = 0.06f,
    gutterRatio: Float = 0.02f,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = true }
    val marginX = width * marginRatio
    val marginY = height * marginRatio
    val gutterX = width * gutterRatio
    val gutterY = height * gutterRatio
    val cellWidth = (width - marginX * 2 - gutterX * (columns - 1)) / columns
    val cellHeight = (height - marginY * 2 - gutterY * (rows - 1)) / rows

    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val left = marginX + column * (cellWidth + gutterX)
            val top = marginY + row * (cellHeight + gutterY)
            paint.color = panelColor(row * columns + column)
            canvas.drawRect(
                RectF(left, top, left + cellWidth, top + cellHeight),
                paint,
            )
        }
    }
    return bitmap
}

private fun diagonalBitmap(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.rgb(18, 18, 20))
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
    }
    val path = Path().apply {
        moveTo(0f, height * 0.18f)
        lineTo(width.toFloat(), height * 0.62f)
        lineTo(width.toFloat(), height * 0.78f)
        lineTo(0f, height * 0.34f)
        close()
    }
    canvas.drawPath(path, paint)
    return bitmap
}

private fun panelColor(index: Int): Int = when (index % 8) {
    0 -> Color.rgb(244, 114, 182)
    1 -> Color.rgb(251, 146, 60)
    2 -> Color.rgb(96, 165, 250)
    3 -> Color.rgb(74, 222, 128)
    4 -> Color.rgb(167, 139, 250)
    5 -> Color.rgb(250, 204, 21)
    6 -> Color.rgb(45, 212, 191)
    else -> Color.rgb(251, 113, 133)
}
