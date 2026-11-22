package com.inkvol.app.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.model.FullTextBookGroup
import com.inkvol.app.data.model.FullTextHit
import com.inkvol.app.data.model.SearchChapterRow
import com.inkvol.app.data.model.SearchFilter
import com.inkvol.app.data.model.TriStateSet
import com.inkvol.app.ui.components.LocalReaderOpenController
import com.inkvol.app.ui.components.ReaderOpenRect
import com.inkvol.app.ui.components.glass.GlassCapsule
import com.inkvol.app.ui.components.glass.GlassDimens
import com.inkvol.app.ui.components.glass.GlassEdge
import com.inkvol.app.ui.components.glass.GlassTier
import com.inkvol.app.ui.theme.InkvolRadius
import com.kyant.backdrop.Backdrop
import java.io.File

/** 三态循环状态：未选 → 只看（include）→ 屏蔽（exclude）→ 取消。 */
enum class Tri { OFF, INCLUDE, EXCLUDE }

fun triStateOf(id: String, set: TriStateSet): Tri = when {
    id in set.include -> Tri.INCLUDE
    id in set.exclude -> Tri.EXCLUDE
    else -> Tri.OFF
}

fun Tri.next(): Tri = when (this) {
    Tri.OFF -> Tri.INCLUDE
    Tri.INCLUDE -> Tri.EXCLUDE
    Tri.EXCLUDE -> Tri.OFF
}

fun TriStateSet.with(id: String, tri: Tri): TriStateSet = when (tri) {
    Tri.OFF -> copy(include = include - id, exclude = exclude - id)
    Tri.INCLUDE -> copy(include = include + id, exclude = exclude - id)
    Tri.EXCLUDE -> copy(include = include - id, exclude = exclude + id)
}

/** 关键词命中高亮（AnnotatedString + primary 加粗）。 */
@Composable
fun HighlightText(
    text: String,
    query: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val scheme = MaterialTheme.colorScheme
    val annotated = remember(text, query, scheme.primary) {
        buildAnnotatedString {
            append(text)
            val q = query.trim()
            if (q.isNotEmpty()) {
                var idx = text.indexOf(q, ignoreCase = true)
                while (idx >= 0) {
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, color = scheme.primary),
                        idx,
                        (idx + q.length).coerceAtMost(text.length),
                    )
                    idx = text.indexOf(q, idx + q.length, ignoreCase = true)
                }
            }
        }
    }
    Text(
        text = annotated,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

/** 通用玻璃胶囊 chip（规范 §2.3「搜索筛选胶囊」档位：GlassCapsule / Full / StrongBlur / Outline / 默认 tint）。 */
@Composable
fun GlassPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    selectedContainer: Color = MaterialTheme.colorScheme.primary,
    selectedContent: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val scheme = MaterialTheme.colorScheme
    GlassCapsule(
        backdrop = backdrop,
        tier = GlassTier.Full,
        blurRadius = GlassDimens.StrongBlur,
        edge = GlassEdge.Outline,
        modifier = modifier.height(36.dp),
    ) {
        val container = if (selected) selectedContainer else Color.Transparent
        val content = if (selected) selectedContent else scheme.onSurface.copy(alpha = 0.82f)
        // 着色/点击层用 matchParentSize（不参与测量）→ 玻璃节点宽度跟随文字 wrap，
        // 不会被 FlowRow/父容器的整行约束拉满（真机 bug：每格按钮横贯全屏）。
        // 需要等宽网格时由调用方给 modifier 传 fillMaxWidth（ChipGridTwo 的 weight 容器内）。
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(percent = 50))
                .background(container)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}

/** 三态循环 chip：OFF / 只看（primary）/ 屏蔽（error）。 */
@Composable
fun TriStateChip(
    label: String,
    tri: Tri,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tri) {
        Tri.OFF -> Color.Transparent to scheme.onSurface.copy(alpha = 0.82f)
        Tri.INCLUDE -> scheme.primary to scheme.onPrimary
        Tri.EXCLUDE -> scheme.error to scheme.onError
    }
    GlassPillChip(
        label = label,
        selected = tri != Tri.OFF,
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        selectedContainer = container,
        selectedContent = content,
    )
}

/** 分组标题行（「书 · 24 本」+ 尾部动作）。 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * 搜索结果书卡：视觉对齐书架 BookCell（封面 0.72 / cover 14 圆角 / 首字占位），
 * 标题带关键词高亮；封面坐标回报一镜到底（搜索页无网格恢复落位，坐标恒可信）。
 */
@Composable
fun SearchBookCard(
    book: BookRecord,
    query: String,
    onClick: (ReaderOpenRect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var coverBounds by remember { mutableStateOf<ReaderOpenRect?>(null) }
    val openController = LocalReaderOpenController.current
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(InkvolRadius.cover)
                .clickable { onClick(coverBounds) }
                .onGloballyPositioned { coords ->
                    val bounds = coords.boundsInRoot()
                    val rect = ReaderOpenRect(
                        left = bounds.left,
                        top = bounds.top,
                        width = bounds.width,
                        height = bounds.height,
                    )
                    coverBounds = rect
                    openController?.updateSourceRect(book.id, rect, true)
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
        }
        Spacer(Modifier.height(6.dp))
        HighlightText(
            text = book.title,
            query = query,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
        )
        val base = if (book.type == "NOVEL") "${book.pageCount} 章 · ${book.format}" else "${book.pageCount} 页 · ${book.format}"
        val progress = when {
            book.readStatus == BookRecord.READ_FINISHED -> " · 已读完"
            book.percent > 0.0 -> " · ${(book.percent * 100).toInt()}%"
            else -> ""
        }
        Text(
            text = base + progress,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 章节标题命中行（点击打开阅读器该章）。 */
@Composable
fun ChapterHitRow(
    row: SearchChapterRow,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(56.dp)
                .clip(InkvolRadius.cover)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val cover = row.coverPath
            if (cover != null) {
                AsyncImage(
                    model = File(cover),
                    contentDescription = row.bookTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = row.bookTitle.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.bookTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HighlightText(
                text = row.chapterTitle,
                query = query,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/**
 * 正文命中按书聚合行：默认首条摘要，可展开全部（≤100 处/书）。
 * 点摘要打开阅读器定位到该处。
 */
@Composable
fun BodyGroupRow(
    group: FullTextBookGroup,
    query: String,
    bookLimited: Boolean,
    onOpenHit: (FullTextHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(group.book.id) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(InkvolRadius.card)
            .background(scheme.surfaceContainerLow)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(48.dp)
                    .clip(InkvolRadius.cover)
                    .background(scheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                val cover = group.book.coverPath
                if (cover != null) {
                    AsyncImage(
                        model = File(cover),
                        contentDescription = group.book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = group.book.title.take(1),
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                HighlightText(
                    text = group.book.title,
                    query = query,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onBackground,
                    maxLines = 1,
                )
                val suffix = if (bookLimited) " · 已达单书上限" else ""
                Text(
                    text = "${group.book.format} · 共 ${group.hits.size} 处$suffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (group.hits.size > 1) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "展开 ${group.hits.size} 处")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val visible = if (expanded) group.hits else listOf(group.hits.first())
        visible.forEach { hit ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenHit(hit) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "第 ${hit.chapterIdx + 1} 章 ${hit.chapterTitle}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HighlightText(
                    text = hit.context,
                    query = query,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface,
                    maxLines = 2,
                )
            }
        }
    }
}

/** 面板/初始态共用的组标题小字。 */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 20.dp),
    )
}

/** 筛选面板 chips 容器间距。 */
val ChipFlowSpacing = Arrangement.spacedBy(8.dp)
