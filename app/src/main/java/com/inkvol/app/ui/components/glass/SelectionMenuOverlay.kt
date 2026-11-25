package com.inkvol.app.ui.components.glass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import com.kyant.backdrop.Backdrop

/**
 * 书架多选条的「⋯」溢出菜单。
 *
 * 底栏已收紧为「删除 / 书夹 / ⋯」三个一级动作，低频与输出类动作全部收进这里：
 * 编辑元数据 / 移出书夹（仅书夹二级页）/ 导出到… / 分享… / 删除。
 *
 * 锚点几何由调用方经 [modifier]（padding / widthIn）、[alignment] 与 [origin] 给出，
 * 与 [PlusMenuOverlay] 共用同一玻璃卡与 Anchored 动效。
 *
 * [canTransfer] = false（选中项全是文件夹型漫画）时，导出与分享置灰并说明原因。
 */
@Composable
fun SelectionMenuOverlay(
    visible: Boolean,
    presence: GlassOverlayPresence?,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRemoveFromCategory: (() -> Unit)?,
    /** null = 不显示「切换阅读类型」行（选中项不支持 EPUB 互转时由调用方置 null）。 */
    onRetype: (() -> Unit)? = null,
    retypeTitle: String = "切换阅读类型",
    retypeHint: String = "",
    retypeEnabled: Boolean = true,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    canTransfer: Boolean,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomEnd,
    origin: TransformOrigin = TransformOrigin(1f, 1f),
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
            MenuRow(
                icon = Icons.Filled.Edit,
                title = "编辑元数据",
                subtitle = "标题 / 系列 / 作者 / 标签",
                onClick = onEdit,
            )
            if (onRetype != null) {
                HorizontalDivider()
                MenuRow(
                    icon = Icons.Filled.Refresh,
                    title = retypeTitle,
                    subtitle = retypeHint,
                    onClick = onRetype,
                    enabled = retypeEnabled,
                )
            }
            if (onRemoveFromCategory != null) {
                HorizontalDivider()
                MenuRow(
                    icon = Icons.Filled.RemoveCircleOutline,
                    title = "移出书夹",
                    subtitle = "从当前书夹移除，不删书",
                    onClick = onRemoveFromCategory,
                )
            }
            HorizontalDivider()
            MenuRow(
                icon = Icons.Filled.FileDownload,
                title = "导出到…",
                subtitle = if (canTransfer) "单本选文件名，多本选文件夹" else "文件夹漫画暂不支持",
                onClick = onExport,
                enabled = canTransfer,
            )
            HorizontalDivider()
            MenuRow(
                icon = Icons.Filled.Share,
                title = "分享…",
                subtitle = if (canTransfer) "微信 / QQ / 网盘" else "文件夹漫画暂不支持",
                onClick = onShare,
                enabled = canTransfer,
            )
            HorizontalDivider()
            MenuRow(
                icon = Icons.Filled.Delete,
                title = "删除",
                subtitle = "从书架移除，或连文件删除",
                onClick = onDelete,
                danger = true,
            )
        }
    }
}
