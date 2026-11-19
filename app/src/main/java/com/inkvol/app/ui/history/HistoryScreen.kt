package com.inkvol.app.ui.history

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.inkvol.app.data.model.ReadingHistoryEntry
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.ui.components.LocalReaderOpenController
import com.inkvol.app.ui.components.LocalScrollToTop
import com.inkvol.app.ui.components.ReaderOpenPhase
import com.inkvol.app.ui.components.ReaderOpenRect
import com.inkvol.app.ui.components.TabPage
import com.inkvol.app.ui.components.glass.GlassDimens
import com.inkvol.app.ui.components.glass.LocalGlassChrome
import com.inkvol.app.ui.theme.InkvolRadius
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史 / 统计（M4-B）：
 * - 顶部显示本地阅读统计总览；
 * - 列表显示最近读过的书，点击继续阅读；
 * - 会话时长 / 页数由阅读器采集，退后台自动断开。
 */
@Composable
fun HistoryScreen(
    repository: LibraryRepository,
    onOpenBook: (entry: ReadingHistoryEntry, rect: ReaderOpenRect?) -> Unit,
) {
    val chrome = LocalGlassChrome.current
    val stats by repository.readingStats.collectAsStateWithLifecycle()
    val history by repository.readingHistory.collectAsStateWithLifecycle()
    val openController = LocalReaderOpenController.current
    // 列表状态提到页面级：折叠顶栏要靠它接手势（TabPage 的折叠分支必须拿到这个实例）
    val listState = rememberLazyListState()
    val scrollToTop = LocalScrollToTop.current
    // 一镜到底回报稳定信号：列表落位稳定前卡片上报的封面坐标不可信（返回重组的
    // 瞬态布局）。settled 前不回报，controller 收到首个 settled 回报才解除
    // 「旋转后返回」的起飞闸。
    var listSettled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var prevCount = -1
        var stable = 0
        var attempts = 0
        while (attempts < 40) {
            val count = listState.layoutInfo.totalItemsCount
            if (count == prevCount) stable++ else { stable = 0; prevCount = count }
            if (stable >= 2) break
            withFrameNanos { }
            attempts++
        }
        listSettled = true
    }

    LaunchedEffect(Unit) {
        repository.refreshStats()
    }
    DisposableEffect(Unit) {
        onDispose { repository.refreshStats() }
    }
    // 回顶（重复点历史 Tab）：滚回列表顶部并轻量刷新统计/历史（仅重读数据库）
    LaunchedEffect(Unit) {
        scrollToTop.events.collect {
            if (listState.layoutInfo.totalItemsCount > 0) {
                listState.animateScrollToItem(0)
            }
            repository.refreshStats()
        }
    }
    // 一镜到底返回锚点：历史页列表走 NavHost saveable 恢复，快照的 index/offset
    // 在新方向布局下落点会漂移（实测竖开横返目标卡滚出视口——卡片不组合就永远
    // 不会回报，起飞闸只能等超时按旧坐标飞）。settled 后若目标卡不在视口内，
    // 滚动把它带回视口，保证 Closing 期间有可信回报、返回动画飞向正确位置。
    LaunchedEffect(listSettled) {
        val ctl = openController
        Log.d(
            "InkvolOpen",
            "history anchor effect settled=$listSettled phase=${ctl?.phase} bookId=${ctl?.bookId} historySize=${history.size}",
        )
        if (ctl == null) return@LaunchedEffect
        if (!listSettled || ctl.phase != ReaderOpenPhase.Closing) return@LaunchedEffect
        val targetId = ctl.bookId ?: return@LaunchedEffect
        val entryIndex = history.indexOfFirst { it.book.id == targetId }
        if (entryIndex < 0 || history.isEmpty()) return@LaunchedEffect
        val itemIndex = entryIndex + 1 // item 0 是统计卡
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == itemIndex }
        Log.d("InkvolOpen", "history anchor item=$itemIndex visible=$visible")
        // 无条件 scroll（同位置也走一次 relayout）：Closing 重组后 LazyColumn 若没有
        // 新布局 pass，卡片的 onGloballyPositioned 不会触发（实测目标卡 visible=true
        // 却零回调），可信回报永远不来、起飞闸只能超时。scrollToItem 强制落位一次，
        // 卡片随布局回调上报当前位置。
        runCatching { listState.scrollToItem(itemIndex) }
        Log.d("InkvolOpen", "history anchor scrolled to $itemIndex")
    }

    TabPage(
        title = "历史",
        subtitle = "阅读统计与最近阅读",
        collapsible = true,
        scrollState = listState,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = chrome.contentBottomForPage + GlassDimens.ListBottomExtraScroll,
            ),
        ) {
            item(key = "stats") {
                ReadingStatsCard(stats)
            }

            if (history.isEmpty()) {
                item(key = "empty") {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = InkvolRadius.card,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                text = "还没有阅读记录",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "打开书并开始阅读后，会在这里显示时长、页数与最近阅读。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item(key = "recent-label") {
                    Text(
                        text = "最近阅读",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                }
                items(history, key = { it.book.id }) { entry ->
                    ReadingHistoryCard(
                        entry = entry,
                        reportSettled = listSettled,
                        onClick = { rect -> onOpenBook(entry, rect) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ReadingStatsCard(stats: com.inkvol.app.data.model.ReadingStats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = InkvolRadius.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "阅读统计",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatCell("累计时长", formatReadingDuration(stats.totalDurationMs), Modifier.weight(1f))
                StatCell("累计页数", stats.totalPagesRead.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatCell("最近 7 天", formatReadingDuration(stats.last7DaysDurationMs), Modifier.weight(1f))
                StatCell("最近 30 天", formatReadingDuration(stats.last30DaysDurationMs), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatCell("阅读会话", stats.sessionCount.toString(), Modifier.weight(1f))
                StatCell("读过的书", stats.bookCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReadingHistoryCard(
    entry: ReadingHistoryEntry,
    reportSettled: Boolean,
    onClick: (ReaderOpenRect?) -> Unit,
) {
    var coverBounds by remember { mutableStateOf<ReaderOpenRect?>(null) }
    val openController = LocalReaderOpenController.current
    // onGloballyPositioned 只在位置变化时回调，落位后不再有回调；settled 翻转时
    // 用最后已知位置补发一次，保证起飞闸能收到首条可信坐标。
    LaunchedEffect(reportSettled) {
        if (reportSettled) {
            coverBounds?.let { openController?.updateSourceRect(entry.book.id, it, true) }
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InkvolRadius.card)
            .clickable { onClick(coverBounds) },
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
                    .width(68.dp)
                    .aspectRatio(0.72f)
                    .clip(InkvolRadius.cover)
                    // 一镜到底起飞点：封面矩形随布局回报，Closing 时封面飞回当前位置。
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
                        openController?.updateSourceRect(entry.book.id, rect, reportSettled)
                    }
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                val cover = entry.book.coverPath
                if (cover != null) {
                    AsyncImage(
                        model = File(cover),
                        contentDescription = entry.book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = entry.book.title.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = formatLastRead(entry.lastReadAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "进度 ${(entry.book.percent * 100).toInt().coerceIn(0, 100)}% · " +
                        "${formatReadingDuration(entry.totalDurationMs)} · ${entry.totalPagesRead} 页",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatReadingDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun formatLastRead(timestamp: Long): String {
    if (timestamp <= 0L) return "刚刚"
    return SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(timestamp))
}
