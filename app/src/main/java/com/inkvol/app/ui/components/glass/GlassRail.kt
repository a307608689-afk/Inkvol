package com.inkvol.app.ui.components.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.inkvol.app.ui.navigation.TopDestination
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

/**
 * 侧栏形态：
 * - [Full]：平板（宽 ≥600dp 且高 ≥480dp）——顶部动作槽 + 底部导航；
 * - [Compact]：手机横屏（宽 ≥600dp 且高 <480dp）——只显示底部导航。
 *
 * 页数竖条不再由宿主侧栏渲染：平板阅读器在自己的页面层内绘制，
 * 避免页面玻璃误挂宿主 backdrop。
 */
enum class RailVariant { Full, Compact }

/** 平板侧栏顶部动作槽的三个默认动作。 */
data class RailActions(
    val onImport: () -> Unit,
    val onRescan: () -> Unit,
    val onToTop: () -> Unit,
    /** 导入钮长按：与手机加号对齐，强制弹出三选菜单（null = 无长按行为）。 */
    val onImportLongClick: (() -> Unit)? = null,
)

/**
 * 右缘侧栏，三件独立玻璃件：
 * 顶部动作槽（仅 Full）→ 弹性空隙 → 底部导航条。
 *
 * 导航贴底、动作贴顶；以后新增功能一律进顶部槽。
 * 常驻不隐显：不读 LocalGlassChromeProgress。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassRail(
    destinations: List<TopDestination>,
    selected: TopDestination?,
    onSelect: (TopDestination) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    lensEnabled: Boolean = true,
    variant: RailVariant = RailVariant.Full,
    actions: RailActions? = null,
    /** 隐显进度（0 显示 / 1 收起）：当前侧栏形态常驻，宿主传 0 */
    hideProgress: Float = 0f,
) {
    var railHeightPx by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .padding(end = GlassDimens.RailMargin)
            .width(GlassDimens.RailWidth)
            .fillMaxHeight()
            .onSizeChanged { railHeightPx = it.height }
            // 顶部让位用「忽略可见性」的状态栏高度：状态栏隐藏时不回缩（三个动作钮
            // 位置恒定不跳动），显示时按钮仍在它下方（不被重叠）。
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(top = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .offset { IntOffset(0, (hideProgress * railHeightPx).roundToInt()) }
            .graphicsLayer { alpha = (1f - hideProgress).coerceAtLeast(0f) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (variant == RailVariant.Full && actions != null) {
            RailActionColumn(
                actions = actions,
                backdrop = backdrop,
                lensEnabled = lensEnabled,
            )
        }

        Spacer(Modifier.weight(1f))

        RailNavBar(
            destinations = destinations,
            selected = selected,
            onSelect = onSelect,
            backdrop = backdrop,
            lensEnabled = lensEnabled,
        )
    }
}

@Composable
private fun RailNavBar(
    destinations: List<TopDestination>,
    selected: TopDestination?,
    onSelect: (TopDestination) -> Unit,
    backdrop: Backdrop?,
    lensEnabled: Boolean,
) {
    val navHeight = GlassDimens.RailItemHeight * destinations.size + GlassDimens.RailNavPadding * 2
    GlassRounded(
        backdrop = backdrop,
        tier = if (lensEnabled) GlassTier.Full else GlassTier.Lite,
        radius = GlassDimens.RailCorner,
        blurRadius = GlassDimens.StrongBlur,
        edge = GlassEdge.Outline,
        modifier = Modifier
            .fillMaxWidth()
            .height(navHeight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = GlassDimens.RailNavPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            destinations.forEach { destination ->
                GlassRailItem(
                    destination = destination,
                    isSelected = destination == selected,
                    onClick = { onSelect(destination) },
                    modifier = Modifier
                        .height(GlassDimens.RailItemHeight)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun GlassRailItem(
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
        label = "railItemPress",
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

/** 侧栏顶部动作槽：三颗独立圆玻璃钮，新功能后续从这里往上加。 */
@Composable
private fun RailActionColumn(
    actions: RailActions,
    backdrop: Backdrop?,
    lensEnabled: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GlassDimens.RailActionGap),
    ) {
        RailFab(
            Icons.Filled.Add,
            "导入",
            actions.onImport,
            backdrop,
            lensEnabled,
            onLongClick = actions.onImportLongClick,
        )
        RailFab(Icons.Outlined.Refresh, "重新扫描", actions.onRescan, backdrop, lensEnabled)
        RailFab(Icons.Outlined.VerticalAlignTop, "回到顶部", actions.onToTop, backdrop, lensEnabled)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RailFab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    lensEnabled: Boolean,
    edge: GlassEdge = GlassEdge.Outline,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) GlassMotion.PRESS_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "railFabScale",
    )
    val scheme = MaterialTheme.colorScheme

    GlassRounded(
        backdrop = backdrop,
        tier = if (lensEnabled) GlassTier.Full else GlassTier.Lite,
        radius = GlassDimens.RailFabSize / 2,
        blurRadius = GlassDimens.StrongBlur,
        edge = edge,
        modifier = modifier
            // requiredSize：无视父约束强制精确尺寸，保证三颗钮严格等宽等高
            .requiredSize(width = GlassDimens.RailFabSize, height = GlassDimens.RailFabSize)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        layerBlock = {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = scheme.onSurface.copy(alpha = 0.82f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(19.dp),
        )
    }
}
