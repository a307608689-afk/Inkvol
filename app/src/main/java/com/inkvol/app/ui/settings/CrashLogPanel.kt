package com.inkvol.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.core.content.FileProvider
import com.inkvol.app.data.diagnostics.CrashLogFile
import com.inkvol.app.data.diagnostics.CrashLogStore
import com.inkvol.app.ui.components.glass.GlassOverlay
import com.inkvol.app.ui.components.glass.GlassOverlayMotion
import com.inkvol.app.ui.components.glass.GlassOverlayPresence
import com.inkvol.app.ui.components.glass.LocalGlassChrome
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 崩溃日志面板（《设计规范》§5 模式 C-2）。
 * 日志只在本机：导出必须由用户主动点分享，走 FileProvider。
 */
@Composable
fun CrashLogPanel(
    visible: Boolean,
    logs: List<CrashLogFile>,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chrome = LocalGlassChrome.current
    var confirmClear by remember { mutableStateOf(false) }

    fun uriOf(entry: CrashLogFile) = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", entry.file)
    }.getOrNull()

    fun share(entry: CrashLogFile) {
        val uri = uriOf(entry)
        if (uri == null) {
            onNotice("无法读取该日志")
            return
        }
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Inkvol 崩溃日志 ${entry.displayTime}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "分享崩溃日志"))
        }.onFailure { onNotice("没有可用的分享目标") }
    }

    fun shareAll() {
        val uris = ArrayList(logs.mapNotNull { uriOf(it) })
        if (uris.isEmpty()) {
            onNotice("无法读取日志")
            return
        }
        runCatching {
            val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "text/plain"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "Inkvol 崩溃日志（全部）")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "分享全部崩溃日志"))
        }.onFailure { onNotice("没有可用的分享目标") }
    }

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
            .heightIn(max = crashPanelMaxHeight()),
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
                        "崩溃日志",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${logs.size} 条 · ${CrashLogStore.formatSize(logs.sumOf { it.sizeBytes })} · 仅存本机",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        "暂无崩溃记录。应用异常退出时会自动写入本机，最多保留 5 份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(logs, key = { it.name }) { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = entry.displayTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = CrashLogStore.formatSize(entry.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { share(entry) }) { Text("分享") }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                TextButton(
                    enabled = logs.isNotEmpty(),
                    onClick = {
                        if (!confirmClear) {
                            confirmClear = true
                            return@TextButton
                        }
                        confirmClear = false
                        scope.launch {
                            val removed = withContext(Dispatchers.IO) {
                                CrashLogStore.deleteAll(context)
                            }
                            onRefresh()
                            onNotice("已清除 $removed 条崩溃日志")
                        }
                    },
                ) {
                    Text(
                        text = if (confirmClear) "确认清空" else "清空",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = logs.isNotEmpty(), onClick = { shareAll() }) {
                    Text("分享全部")
                }
            }
        }
    }
}

@Composable
private fun crashPanelMaxHeight(): Dp =
    (LocalConfiguration.current.screenHeightDp * 0.6f).dp
