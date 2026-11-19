package com.inkvol.app.data.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inkvol.app.ui.components.glass.GlassOverlay
import com.inkvol.app.ui.components.glass.GlassOverlayMotion
import com.inkvol.app.ui.components.glass.GlassOverlayPresence
import com.kyant.backdrop.Backdrop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreSheet(
    visible: Boolean,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
    preview: BackupPreview,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
) {
    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.Card,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "恢复备份",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "生成时间 " + SimpleDateFormat(
                    "yyyy年M月d日 HH:mm",
                    Locale.getDefault(),
                ).format(Date(preview.generatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            PreviewRow("可恢复书籍", "${preview.availableBookCount} 本")
            PreviewRow("备份中的书", "${preview.bookCount} 本")
            PreviewRow("缺失书籍", "${preview.missingBookCount} 本")
            PreviewRow("书夹", preview.categoryCount.toString())
            PreviewRow("阅读会话", preview.sessionCount.toString())
            PreviewRow("应用偏好", preview.preferenceCount.toString())

            if (preview.missingBookCount > 0) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.24f),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "以下书不在当前书架，相关数据将跳过：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = preview.missingBookTitles.joinToString("、"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "恢复会合并书夹，并替换备份中可匹配书的进度、页签与统计。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onRestore, enabled = !busy) {
                    Text(if (busy) "恢复中…" else "确认恢复")
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(8.dp))
}
