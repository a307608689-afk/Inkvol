package com.inkvol.app.ui.reader.panel

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inkvol.app.ui.components.glass.GlassDefaults
import com.inkvol.app.ui.components.glass.GlassDimens
import com.inkvol.app.ui.components.glass.GlassEdge
import com.inkvol.app.ui.components.glass.GlassOverlay
import com.inkvol.app.ui.components.glass.GlassOverlayMotion
import com.inkvol.app.ui.components.glass.GlassOverlayPresence
import com.inkvol.app.ui.components.glass.GlassRounded
import com.inkvol.app.ui.components.glass.GlassTier
import com.inkvol.app.ui.reader.PageLoader
import com.inkvol.app.ui.theme.InkvolRadius
import com.kyant.backdrop.Backdrop

/**
 * M5-D：手动分格编辑与调试信息。
 *
 * 面向当前页的一个 Layout。用户可以微调任意一个 frame 的四边，
 * 保存后回调交给 ReaderScreen 更新内存缓存并写入 PanelLayoutCache。
 */
@Composable
fun ReaderPanelEditorSheet(
    visible: Boolean,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
    loader: PageLoader,
    pageIndex: Int,
    layout: PanelDetector.Layout,
    initialFrameIndex: Int,
    repair: Int,
    trimMargins: Boolean,
    onDismiss: () -> Unit,
    onSave: (PanelDetector.Layout) -> Unit,
) {
    // 面板常驻组合（退场动画需要）：不可见时不解码整页，避免长期占一张位图
    val baseBitmap by produceState<Bitmap?>(
        initialValue = null,
        pageIndex,
        loader,
        trimMargins,
        repair,
        visible,
    ) {
        value = if (visible) loader.load(pageIndex, trimMargins, repair) else null
    }
    var selectedFrameIndex by remember(layout) {
        mutableIntStateOf(initialFrameIndex.coerceIn(0, (layout.frames.lastIndex).coerceAtLeast(0)))
    }
    var frames by remember(layout) { mutableStateOf(layout.frames) }
    // 每次重新打开都从已保存的 layout 起步（草稿不跨次保留）
    LaunchedEffect(visible) {
        if (visible) {
            frames = layout.frames
            selectedFrameIndex = initialFrameIndex.coerceIn(0, layout.frames.lastIndex.coerceAtLeast(0))
        }
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val debugJson = remember(layout, frames) { debugJson(layout.status, layout.confidence, layout.reason, frames) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(debugJson.toByteArray())
                }
            }
        }
    }

    fun updateSelected(transform: (PanelDetector.Frame) -> PanelDetector.Frame) {
        frames = frames.mapIndexed { index, frame ->
            if (index == selectedFrameIndex) transform(frame) else frame
        }
    }

    if (visible) {
        BackHandler { onDismiss() }
    }
    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.ReaderPanel,
        onDismiss = onDismiss,
        backdrop = backdrop,
        tint = GlassDefaults.readerPanelTint(),
        presence = presence,
        radius = 20.dp,
        alignment = Alignment.BottomCenter,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .fillMaxWidth()
            .heightIn(max = 560.dp),
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "手动分格编辑",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "微调当前页分格矩形，保存后写入缓存",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "关闭",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(InkvolRadius.card)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = InkvolRadius.card,
                        ),
                ) {
                    val bitmap = baseBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "编辑页预览",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        frames.forEachIndexed { index, frame ->
                            val selected = index == selectedFrameIndex
                            drawRect(
                                color = if (selected) {
                                    androidx.compose.ui.graphics.Color(0xFFFFB74D)
                                } else {
                                    androidx.compose.ui.graphics.Color(0xFF34D399).copy(alpha = 0.7f)
                                },
                                topLeft = Offset(
                                    x = frame.left * size.width,
                                    y = frame.top * size.height,
                                ),
                                size = Size(
                                    width = frame.width * size.width,
                                    height = frame.height * size.height,
                                ),
                                style = Stroke(width = if (selected) 4.dp.toPx() else 2.dp.toPx()),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = {
                        selectedFrameIndex = (selectedFrameIndex - 1).coerceAtLeast(0)
                    }) { Text("上一格") }
                    Text(
                        text = "格 ${selectedFrameIndex + 1}/${frames.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedButton(onClick = {
                        selectedFrameIndex = (selectedFrameIndex + 1).coerceAtMost(frames.lastIndex)
                    }) { Text("下一格") }
                }

                Spacer(Modifier.height(10.dp))
                val frame = frames.getOrNull(selectedFrameIndex)
                if (frame == null) {
                    Text(
                        text = "当前页没有可编辑分格",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Slider(
                        value = frame.left,
                        onValueChange = { value ->
                            updateSelected { it.copy(left = value.coerceIn(0f, it.right - 0.02f)) }
                        },
                        valueRange = 0f..1f,
                    )
                    Slider(
                        value = frame.top,
                        onValueChange = { value ->
                            updateSelected { it.copy(top = value.coerceIn(0f, it.bottom - 0.02f)) }
                        },
                        valueRange = 0f..1f,
                    )
                    Slider(
                        value = frame.right,
                        onValueChange = { value ->
                            updateSelected { it.copy(right = value.coerceIn(it.left + 0.02f, 1f)) }
                        },
                        valueRange = 0f..1f,
                    )
                    Slider(
                        value = frame.bottom,
                        onValueChange = { value ->
                            updateSelected { it.copy(bottom = value.coerceIn(it.top + 0.02f, 1f)) }
                        },
                        valueRange = 0f..1f,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { frames = layout.frames }) { Text("还原") }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(debugJson))
                    }) { Text("复制 JSON") }
                    OutlinedButton(onClick = {
                        exportLauncher.launch("inkvol-panel-p${pageIndex + 1}.json")
                    }) { Text("导出 JSON") }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = debugJson,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(10.dp),
                )

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        onSave(
                            layout.copy(
                                frames = frames,
                                confidence = 1f,
                                reason = null,
                                elapsedMs = 0L,
                            ),
                        )
                    }) { Text("保存") }
                }
            }
        }
}

private fun debugJson(
    status: PanelDetector.LayoutStatus,
    confidence: Float,
    reason: PanelDetector.FallbackReason?,
    frames: List<PanelDetector.Frame>,
): String = buildString {
    appendLine("{")
    appendLine("  \"status\": \"${status.name}\",")
    appendLine("  \"confidence\": ${"%.3f".format(confidence)},")
    appendLine("  \"reason\": ${reason?.let { "\"${it.name}\"" } ?: "null"},")
    appendLine("  \"frames\": [")
    frames.forEachIndexed { index, frame ->
        append(
            "    {\"left\": ${"%.4f".format(frame.left)}, " +
                "\"top\": ${"%.4f".format(frame.top)}, " +
                "\"right\": ${"%.4f".format(frame.right)}, " +
                "\"bottom\": ${"%.4f".format(frame.bottom)}}",
        )
        if (index != frames.lastIndex) append(",")
        appendLine()
    }
    appendLine("  ]")
    append("}")
}
