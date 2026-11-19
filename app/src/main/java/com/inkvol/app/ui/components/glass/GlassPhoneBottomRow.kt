package com.inkvol.app.ui.components.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.kyant.backdrop.Backdrop

/**
 * 手机底部悬浮行：页数横条（左）+ 三颗独立玻璃钮横排（右）。
 *
 * 左右边缘与玻璃底栏对齐（同 24dp 边距），整体悬浮在内容上，
 * 隐显位移由宿主用与底栏相同的进度驱动（同相位沉出/浮回）。
 */
@Composable
fun GlassPhoneBottomRow(
    backdrop: Backdrop?,
    lensEnabled: Boolean,
    edge: GlassEdge = GlassEdge.Highlight,
    /** 隐显进度（0 显示 / 1 收起） */
    hideProgress: Float = 0f,
    /** 底部让位（给底栏的高度）：计入下沉距离，收起时才能完全滑出屏幕 */
    bottomAvoidance: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val pageInfo = LocalRailPageSlot.current.value
    val fabActions = LocalRailFabSlot.current.value
    var ownHeightPx by remember { mutableIntStateOf(0) }

    Row(
        modifier = modifier
            // 尺子在垫位外层：量出的是"自身 + 垫位"的总占位（138dp），
            // 内层玻璃下沉这段距离后完全滑出屏幕；垫位本身是透明空间，不会留痕
            .onSizeChanged { ownHeightPx = it.height }
            .padding(bottom = bottomAvoidance)
            .offset { IntOffset(0, (hideProgress * ownHeightPx).roundToInt()) }
            .padding(horizontal = GlassDimens.BarHorizontalMargin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        pageInfo?.let { info ->
            GlassPageBarHorizontal(
                page = info.page,
                loading = info.loading,
                hasMore = info.hasMore,
                onPrev = info.onPrev,
                onNext = info.onNext,
                onJump = info.onJump,
                backdrop = backdrop,
                edge = edge,
            )
        }
        Spacer(Modifier.weight(1f))
        fabActions?.let { actions ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RailFab(
                    icon = Icons.Outlined.FileDownload,
                    label = "下载管理",
                    onClick = actions.onDownloads,
                    backdrop = backdrop,
                    lensEnabled = lensEnabled,
                    edge = edge,
                )
                RailFab(
                    icon = Icons.Outlined.Refresh,
                    label = "刷新",
                    onClick = actions.onRefresh,
                    backdrop = backdrop,
                    lensEnabled = lensEnabled,
                    edge = edge,
                    // 视觉间距补偿：真机校准值在 GlassDimens.FabSpacingCompensation（-2.6dp）
                    modifier = Modifier.offset {
                        IntOffset(GlassDimens.FabSpacingCompensation.toPx().roundToInt(), 0)
                    },
                )
                RailFab(
                    icon = Icons.Outlined.VerticalAlignTop,
                    label = "回到顶部",
                    onClick = actions.onToTop,
                    backdrop = backdrop,
                    lensEnabled = lensEnabled,
                    edge = edge,
                )
            }
        }
    }
}
