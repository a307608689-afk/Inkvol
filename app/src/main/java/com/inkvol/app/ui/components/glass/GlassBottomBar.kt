package com.inkvol.app.ui.components.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle
import com.inkvol.app.ui.navigation.TopDestination

/**
 * 底部导航：一整条满宽玻璃胶囊，五等分，**无内部色块**。
 *
 * 选中态 = 图标 + 文字直接用强调色（对齐 TiebaPura 的语言），
 * 层级完全靠颜色区分，不再有"内外两层胶囊"的对齐问题。
 * 未选中项用 onSurface 82%，保证浅色玻璃上的对比度。
 */
@Composable
fun GlassBottomBar(
    destinations: List<TopDestination>,
    selected: TopDestination?,
    onSelect: (TopDestination) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    /** 滚动进行中关闭透镜，只保留模糊，省掉折射着色器 */
    lensEnabled: Boolean = true,
    edge: GlassEdge = GlassEdge.Highlight,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    GlassRounded(
        backdrop = backdrop,
        tier = if (lensEnabled) GlassTier.Full else GlassTier.Lite,
        radius = GlassDimens.BarCorner,
        blurRadius = GlassDimens.StrongBlur,
        edge = edge,
        modifier = modifier
            .widthIn(max = GlassDimens.BarMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = GlassDimens.BarHorizontalMargin)
            .padding(bottom = GlassDimens.BarBottomMargin + navBottom)
            .height(GlassDimens.BarHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                // 内容整体内收：首尾项离玻璃两端各让 8dp，五项仍严格等分
                .padding(horizontal = GlassDimens.BarInnerPaddingX),
        ) {
            destinations.forEach { destination ->
                GlassBottomBarItem(
                    destination = destination,
                    isSelected = destination == selected,
                    onClick = { onSelect(destination) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun GlassBottomBarItem(
    destination: TopDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) GlassMotion.PRESS_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "barItemPress",
    )

    val scheme = MaterialTheme.colorScheme
    val tint = if (isSelected) scheme.primary else scheme.onSurface.copy(alpha = 0.82f)

    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            tint = tint,
            modifier = Modifier
                .size(GlassDimens.IconSize)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
        )
    }
}

/**
 * 底栏整体占用的高度（含手势条安全区），宿主用它做 chrome 位移与内容让位。
 */
@Composable
fun rememberGlassBottomBarHeight(): Dp {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return GlassDimens.BarHeight + GlassDimens.BarBottomMargin + navBottom
}

/** 胶囊形状的公共实例（GlassCapsule 复用），避免每帧重建 */
internal val GlassCapsuleShape = Capsule(RoundedCornerStyle.Continuous)
