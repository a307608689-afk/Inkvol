package com.inkvol.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 按滚动方向决定悬浮元素是否可见。
 *
 * - 内容上滑（继续往后看）→ 隐藏
 * - 内容下滑（往回翻）→ 出现
 * - 停止滚动 → 保持现状，不自己弹回
 *
 * 位置用 `index * 10000 + offset` 换算成单调整数，阈值 3 用来滤掉惯性滚动的细微抖动。
 */
@Composable
fun rememberScrollDirectionVisibility(
    positionProvider: () -> Int,
    /**
     * 首次采样前的初始可见性。默认 true（冷启动/首次进页面）。
     * 一镜到底返回时传进书前的真实状态（LibraryStatusBar.visible），
     * 避免每次返回都把状态栏/悬浮件顶成「显示」。
     */
    initialVisible: Boolean = true,
): Boolean {
    var visible by remember { mutableStateOf(initialVisible) }
    var last by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        snapshotFlow { positionProvider() }
            .distinctUntilChanged()
            .collect { position ->
                val previous = last
                if (previous < 0) {
                    // 首次采样只记录，避免进页面瞬间被判定成「下滑」而隐藏
                    last = position
                    return@collect
                }
                val delta = position - previous
                when {
                    // 内容上滑（继续往后看）→ 隐藏
                    delta > 3 -> visible = false
                    // 内容下滑（往回翻）→ 出现
                    delta < -3 -> visible = true
                }
                last = position
            }
    }

    return visible
}
