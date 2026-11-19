package com.inkvol.app.ui.novel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.model.NovelChapterRecord
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.ui.theme.InkvolRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * M6.1-A 章节解析预览页。M6.1-B 会替换成真正的小说阅读器；
 * 当前用它验证导入的编码 / 章节切分 / 字节区间，不把 TXT 送进漫画阅读器。
 */
@Composable
fun NovelChapterPreviewScreen(
    repository: LibraryRepository,
    bookId: String,
    onBack: () -> Unit,
) {
    var book by remember { mutableStateOf<BookRecord?>(null) }
    var chapters by remember { mutableStateOf<List<NovelChapterRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(bookId) {
        val result = withContext(Dispatchers.IO) {
            val loadedBook = repository.bookById(bookId)
            val loadedChapters = repository.novelChapters(bookId)
            loadedBook to loadedChapters
        }
        book = result.first
        chapters = result.second
        loading = false
    }

    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book?.title ?: "小说章节",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "M6.1-A · 章节解析预览",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "解析中…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        Surface(
            shape = InkvolRadius.card,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = if (book == null) {
                        "书本不存在或已移除"
                    } else {
                        "${book!!.format} · ${chapters.size} 章 · ${formatBytes(book!!.fileSize)}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "字节区间指向导入期生成的 UTF-8 归一化文本，M6.1-B 阅读器可直接随机访问。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (chapters.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "没有解析到章节",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = 32.dp,
                ),
            ) {
                items(chapters, key = { it.index }) { chapter ->
                    Surface(
                        shape = InkvolRadius.card,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${chapter.index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = chapter.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "区间 ${chapter.byteStart}–${chapter.byteEnd} · 约 ${chapter.charCount} 字符",
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

private fun formatBytes(size: Long): String {
    if (size < 1024L) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024.0) return "${(kb * 10).roundToInt() / 10.0} KB"
    val mb = kb / 1024.0
    if (mb < 1024.0) return "${(mb * 10).roundToInt() / 10.0} MB"
    val gb = mb / 1024.0
    return "${(gb * 10).roundToInt() / 10.0} GB"
}
