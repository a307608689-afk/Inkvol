package com.inkvol.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 全 App 统一的开关样式（A3 档）。
 *
 * 默认 Material3 色板在本工程纸白/墨黑主题下未选中态近乎不可辨：
 * track = surfaceContainerHighest（#DED8CE）与 thumb = outline（#DCD6CD）只差 2/255。
 * 这里改成「中性轨道 + 主题色圆点」，圆点本身即状态指示：
 * - 未选中：track = surfaceContainerHigh，thumb = primary 75%，细描边 onSurfaceVariant 35%
 * - 选中：track = primary，thumb = onPrimary
 * - 描边保证在玻璃面板 / 任意 backdrop 底上胶囊轮廓都稳定
 *
 * 颜色全部取 colorScheme token，随主题强调色（applyAccent）与动态取色自动变化。
 */
@Composable
fun InkvolSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            // 选中：主题色轨道 + 高对比圆点
            checkedThumbColor = colors.onPrimary,
            checkedTrackColor = colors.primary,
            checkedBorderColor = Color.Transparent,
            checkedIconColor = colors.onPrimary,
            // 未选中：主题色圆点 + 中性轨道 + 可见细描边
            uncheckedThumbColor = colors.primary.copy(alpha = 0.75f),
            uncheckedTrackColor = colors.surfaceContainerHigh,
            uncheckedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.35f),
            uncheckedIconColor = colors.primary,
            // 禁用态按 M3 语义压透明度
            disabledCheckedThumbColor = colors.onSurface.copy(alpha = 0.38f),
            disabledCheckedTrackColor = colors.onSurface.copy(alpha = 0.12f),
            disabledUncheckedThumbColor = colors.onSurface.copy(alpha = 0.38f),
            disabledUncheckedTrackColor = colors.surfaceContainerHigh.copy(alpha = 0.6f),
            disabledUncheckedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.15f),
        ),
    )
}
