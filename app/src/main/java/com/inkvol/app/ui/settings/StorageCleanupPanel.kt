package com.inkvol.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inkvol.app.data.cleanup.CacheCleaner
import com.inkvol.app.data.cleanup.CleanupItem
import com.inkvol.app.ui.components.glass.GlassOverlay
import com.inkvol.app.ui.components.glass.GlassOverlayMotion
import com.inkvol.app.ui.components.glass.GlassOverlayPresence
import com.inkvol.app.ui.components.glass.LocalGlassChrome
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 空间清理面板（《设计规范》§5 模式 C-2）。
 * 只清可重建缓存与孤儿封面；书籍、进度、书签、正文与插图一律不动。
 */
@Composable
fun StorageCleanupPanel(
    visible: Boolean,
    items: List<CleanupItem>,
    validBookIds: Set<String>,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chrome = LocalGlassChrome.current
    var selected by remember(items) {
        mutableStateOf(items.filter { !it.deep }.map { it.id }.toSet())
    }
    var confirmDeep by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    val selectedBytes = items.filter { it.id in selected }.sumOf { it.bytes }
    val hasDeepSelected = items.any { it.deep && it.id in selected }

    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.BottomPanel,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = Alignment.BottomCenter,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp + chrome.railEndInset,
                bottom = 16.dp,
            )
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(max = cleanupPanelMaxHeight()),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "空间清理",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "只清可重建缓存；书籍、进度与标注不受影响",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Checkbox(
                            checked = item.id in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + item.id else selected - item.id
                                confirmDeep = false
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.deep) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Text(
                            text = if (item.bytes > 0) {
                                CacheCleaner.formatSize(item.bytes)
                            } else {
                                "${item.count} 项"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "深度项（橙色说明）：孤儿封面在书删除后已无主；崩溃日志清空后无法事后排查。" +
                        "正文缓存与 EPUB 插图不在清理范围内。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "已选 ${CacheCleaner.formatSize(selectedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = selected.isNotEmpty() && !working,
                    onClick = {
                        if (hasDeepSelected && !confirmDeep) {
                            confirmDeep = true
                            return@Button
                        }
                        working = true
                        scope.launch {
                            val freed = withContext(Dispatchers.IO) {
                                CacheCleaner.clear(context, selected, validBookIds)
                            }
                            working = false
                            confirmDeep = false
                            onRefresh()
                            onNotice("已释放 ${CacheCleaner.formatSize(freed)}")
                        }
                    },
                ) {
                    Text(
                        when {
                            working -> "清理中…"
                            hasDeepSelected && !confirmDeep -> "清理（含深度项）"
                            hasDeepSelected -> "确认清理"
                            else -> "清理"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun cleanupPanelMaxHeight(): Dp =
    (LocalConfiguration.current.screenHeightDp * 0.6f).dp
