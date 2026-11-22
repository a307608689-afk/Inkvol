package com.inkvol.app.ui.components.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/**
 * 「加号」菜单（导入文件夹 / 导入文件 / 重新扫描 / 书库管理）。
 *
 * 手机端标题栏加号与平板侧栏加号共用同一组件与同一询问语义：
 * - 手机：`alignment = TopEnd`，锚点 = 标题行加号（右上角），origin 右上；
 * - 平板：`alignment = TopEnd` + 右让位 = 侧栏宽 + 间距，锚点 = 侧栏动作槽，origin 右上。
 *
 * 锚点几何由调用方经 [modifier]（padding / widthIn）与 [alignment] / [origin] 给出，
 * 玻璃卡（radius / StrongBlur / Outline）与 Anchored 动效由 [GlassOverlay] 统一提供。
 */
@Composable
fun PlusMenuOverlay(
    visible: Boolean,
    presence: GlassOverlayPresence?,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onFolder: () -> Unit,
    onFile: () -> Unit,
    onRescan: () -> Unit,
    onManager: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopEnd,
    origin: TransformOrigin = TransformOrigin(1f, 0f),
) {
    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.Anchored,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = alignment,
        origin = origin,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MenuRow(Icons.Filled.CreateNewFolder, "导入文件夹", "文件留在原地，不复制", onFolder)
            HorizontalDivider()
            MenuRow(Icons.AutoMirrored.Filled.InsertDriveFile, "导入文件", "CBZ / RAR / 7z / PDF", onFile)
            HorizontalDivider()
            MenuRow(Icons.Filled.Refresh, "重新扫描", "检查文件夹里的新增与变更", onRescan)
            HorizontalDivider()
            MenuRow(Icons.Filled.Settings, "书库管理", "多书库 / 隐藏书库 / 回收站 / 重复检测", onManager)
        }
    }
}

@Composable
internal fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    /**
     * 选中态：null = 无选中语义（加号菜单，保持原样外观）；true = 排序菜单当前档
     * （图标/标题主色 + 行尾对勾）；false = 未选档（图标降为次要色）。
     */
    selected: Boolean? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when (selected) {
                true -> scheme.primary
                false -> scheme.onSurfaceVariant
                null -> scheme.primary
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected == true) scheme.primary else scheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected == true) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选中",
                tint = scheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
