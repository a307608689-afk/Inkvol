package com.inkvol.app.ui.library

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.inkvol.app.data.import.SingleFileImporter
import com.inkvol.app.data.model.LibraryRecord
import com.inkvol.app.data.model.TrashBook
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.ui.components.glass.GlassDimens
import com.inkvol.app.ui.components.glass.LocalGlassChrome
import com.inkvol.app.ui.theme.InkvolRadius
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private enum class ManagerTab(val label: String) {
    Libraries("书库"),
    Trash("回收站"),
    Duplicates("重复"),
}

@Composable
fun LibraryManagerScreen(
    repository: LibraryRepository,
    onBack: () -> Unit,
    initialTab: String = "libraries",
    // M4.5 同步（用于移除确认卡的同步范围提示）
    syncRepository: com.inkvol.app.data.sync.SyncRepository? = null,
) {
    val chrome = LocalGlassChrome.current
    // 页面自己声明状态栏状态（GlassImmersiveHeader 退场 show 已移除，每个顶层页
    // 入场自管）：本页无玻璃头状态机，入场恒显示（BEHAVIOR_DEFAULT 持久生效，
    // 防上一页的 TRANSIENT 行为残留把 show 变成「几秒后自动缩回」）。
    val managerView = LocalView.current
    LaunchedEffect(Unit) {
        runCatching {
            val controller = WindowCompat.getInsetsController(managerView.context.findManagerWindow(), managerView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
    val libraries by repository.libraries.collectAsStateWithLifecycle()
    val trash by repository.trashBooks.collectAsStateWithLifecycle()
    val duplicates by repository.duplicateGroups.collectAsStateWithLifecycle()
    val duplicatePageReports by repository.duplicatePageReports.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(initialTabToTab(initialTab)) }
    var removeTarget by remember { mutableStateOf<LibraryRecord?>(null) }
    val notice by repository.notice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(4000)
            repository.clearNotice()
        }
    }

    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, start = 20.dp, end = 20.dp),
        ) {
            Text(
                text = "书库管理",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = notice ?: "多书库 / 隐藏书库 / 回收站 / 重复检测",
                style = MaterialTheme.typography.bodySmall,
                color = if (notice == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ManagerTab.entries.forEach { candidate ->
                    val selected = tab == candidate
                    Surface(
                        onClick = { tab = candidate },
                        shape = InkvolRadius.pill,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clip(InkvolRadius.pill),
                    ) {
                        Text(
                            text = candidate.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .clip(InkvolRadius.pill)
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    bottom = chrome.contentBottomForPage + GlassDimens.ListBottomExtraScroll,
                ),
            ) {
                when (tab) {
                    ManagerTab.Libraries -> items(libraries, key = { it.id }) { library ->
                        LibraryCard(library, repository, onRemove = { removeTarget = library })
                    }
                    ManagerTab.Trash -> {
                        if (trash.isEmpty()) {
                            item { EmptyCard("回收站是空的", "从书架移除的书会保留 30 天") }
                        } else {
                            item { EmptyCard("保留期 30 天", "到期后会在启动时自动清空") }
                            items(trash, key = { it.book.id }) { entry ->
                                TrashCard(entry, repository)
                            }
                        }
                    }
                    ManagerTab.Duplicates -> {
                        item {
                            Button(onClick = { repository.detectDuplicatePages() }) {
                                Text("检测书内重复页")
                            }
                        }
                        if (duplicatePageReports.isNotEmpty()) {
                            item { EmptyCard("书内重复页", "共 ${duplicatePageReports.size} 本包含疑似重复") }
                            items(duplicatePageReports, key = { it.bookId }) { report ->
                                DuplicatePageCard(report)
                            }
                        } else {
                            item { EmptyCard("未检测到书内重复页", "点击上方按钮对当前可见书架逐页采样比对") }
                        }
                        if (duplicates.isEmpty()) {
                            item { EmptyCard("未发现跨书疑似重复", "按标题 / 格式 / 页数 / 文件大小进行本地比对") }
                        } else {
                            items(duplicates, key = { it.title + it.format + it.pageCount }) { group ->
                                DuplicateCard(group)
                            }
                        }
                    }
                }
            }
        }

        removeTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { removeTarget = null },
                title = { Text("移除「${target.name}」？") },
                text = {
                    val syncLine = if (syncRepository?.state?.value?.enabled == true) {
                        "\n\n已开启同步：本次移除的书将同步从所有设备的书架移除，云端对应数据一并清理。"
                    } else {
                        ""
                    }
                    Text(
                        text = "移除后将不再扫描该文件夹，书架中来自它的书会同步移除" +
                            "（阅读进度、书签、分类归属一并清除）；同一本书若还存在于其它文件夹则保留。" +
                            "手机里的源文件不会被删除；回收站中属于它的书会被彻底清除。" +
                            "之后重新导入同一文件夹，将作为新书重新进入书架。" +
                            syncLine,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            repository.removeLibrary(target)
                            removeTarget = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("移除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { removeTarget = null }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

private fun initialTabToTab(initialTab: String): ManagerTab = when (initialTab) {
    "trash" -> ManagerTab.Trash
    "duplicates" -> ManagerTab.Duplicates
    else -> ManagerTab.Libraries
}

@Composable
private fun LibraryCard(
    library: LibraryRecord,
    repository: LibraryRepository,
    onRemove: () -> Unit,
) {
    Surface(
        shape = InkvolRadius.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = library.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${library.bookCount} 本 · 回收站 ${library.trashCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (library.hidden) "隐藏" else "显示",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = !library.hidden,
                        onCheckedChange = { visible ->
                            repository.setLibraryHidden(library.id, !visible)
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { repository.rescanLibrary(library) }) {
                    Text("重新扫描")
                }
                if (library.rootTreeUri != SingleFileImporter.SINGLE_LIBRARY_URI) {
                    OutlinedButton(
                        onClick = onRemove,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("移除")
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = SimpleDateFormat("yyyy/M/d", Locale.getDefault())
                        .format(Date(library.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

@Composable
private fun TrashCard(
    entry: TrashBook,
    repository: LibraryRepository,
) {
    Surface(
        shape = InkvolRadius.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = InkvolRadius.cover,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .width(58.dp)
                    .aspectRatio(0.72f),
            ) {
                val cover = entry.book.coverPath
                if (cover != null) {
                    AsyncImage(
                        model = File(cover),
                        contentDescription = entry.book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
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
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(entry.deletedAt))}" +
                        " · ${entry.daysUntilPurge} 天后清空",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { repository.restoreBooksFromTrash(listOf(entry.book.id)) }) {
                Text("恢复")
            }
        }
    }
}

@Composable
private fun DuplicateCard(group: com.inkvol.app.data.model.DuplicateGroup) {
    Surface(
        shape = InkvolRadius.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${group.books.size} 份 · ${group.format} · ${group.pageCount} 页 · " +
                    "${group.fileSize / 1024} KB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            group.books.forEach { book ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = book.displayPath.ifBlank { book.title },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${book.fileSize / 1024} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = "请核对后手动删除；不会自动删除重复项。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DuplicatePageCard(report: com.inkvol.app.data.model.BookDuplicatePageReport) {
    Surface(
        shape = InkvolRadius.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = report.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "总 ${report.pageCount} 页 · 疑似重复 ${report.duplicatePageCount} 页 · " +
                    "${report.groups.size} 组",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            report.groups.take(5).forEach { group ->
                Text(
                    text = "页码：P" + group.sorted().joinToString(" / ") { index -> "${index + 1}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
            }
            if (report.groups.size > 5) {
                Text(
                    text = "还有 ${report.groups.size - 5} 组",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, message: String) {
    Surface(
        shape = InkvolRadius.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 从 Compose 的 LocalView 向上找到宿主 Window（状态栏控制用，SearchScreen 同款）。 */
private fun Context.findManagerWindow(): Window = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findManagerWindow()
    else -> error("无法定位宿主 Window")
}
