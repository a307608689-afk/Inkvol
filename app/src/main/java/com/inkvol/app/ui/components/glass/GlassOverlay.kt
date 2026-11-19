package com.inkvol.app.ui.components.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 覆盖层动效形态（《设计规范》§5 三模式的动效侧映射）。
 *
 * 全部只做 `graphicsLayer` 位移/缩放/淡入，**不改 radius / blurRadius / tint** ——
 * 逐帧改材质参数会让 `drawBackdrop` 每帧重建 RenderEffect，必掉帧。
 */
enum class GlassOverlayMotion {
    /** A 下拉菜单：从锚点角放大 + 轻微下坠收口 */
    Anchored,

    /** B 居中卡：0.94 → 1 缩放 + 淡入 */
    Card,

    /** C-2 底部面板：40dp 浮起 */
    BottomPanel,

    /** 阅读器底部面板：同上但快一档 */
    ReaderPanel,
}

/**
 * 覆盖层"仍然存在"的聚合器。
 *
 * 退场动画期间面板仍在组合里，此时 chrome（底栏/侧栏）不能提前回来，
 * 否则会出现「退场中的面板 + 淡入回来的底栏」两件玻璃同框。
 * 页面/宿主把 [present] 并入 chrome 让位条件即可。
 */
@Stable
class GlassOverlayPresence {

    private val entries = mutableStateMapOf<Any, Boolean>()

    var present by mutableStateOf(false)
        private set

    internal fun report(key: Any, value: Boolean) {
        if (value) entries[key] = true else entries.remove(key)
        val next = entries.isNotEmpty()
        if (next != present) present = next
    }
}

@Composable
fun rememberGlassOverlayPresence(): GlassOverlayPresence = remember { GlassOverlayPresence() }

/**
 * 页面内玻璃覆盖层（全屏透明关闭层 + 玻璃面板 + 进出场动效）。
 *
 * 调用约定：
 * - **始终组合**，用 `visible` 控制显隐（不能再用 `if (open) Overlay(...)`，那会砍掉退场动画）；
 * - `modifier` 只放尺寸/外边距（padding / widthIn / heightIn），`alignment` 决定贴哪一侧，
 *   两者合成后的几何与改造前的 `Modifier.align(...) + padding(...)` 逐像素一致；
 * - `content` 是面板内部内容，玻璃卡（radius / StrongBlur / Outline）由本组件统一提供；
 * - 数据型面板（编辑某个具体条目）需由调用方保留最后一帧数据，见 `LibraryScreen` 的 retained 写法。
 */
@Composable
fun GlassOverlay(
    visible: Boolean,
    motion: GlassOverlayMotion,
    onDismiss: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    radius: Dp = 16.dp,
    origin: TransformOrigin = TransformOrigin.Center,
    presence: GlassOverlayPresence? = null,
    tint: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val animationsEnabled = rememberAnimationsEnabled()

    val enterMs = when (motion) {
        GlassOverlayMotion.Anchored -> 200
        GlassOverlayMotion.Card -> 220
        GlassOverlayMotion.BottomPanel -> GlassMotion.OVERLAY_ENTER_MS
        GlassOverlayMotion.ReaderPanel -> GlassMotion.READER_ENTER_MS
    }
    val exitMs = when (motion) {
        GlassOverlayMotion.Anchored -> 130
        GlassOverlayMotion.Card -> 150
        GlassOverlayMotion.BottomPanel -> GlassMotion.OVERLAY_EXIT_MS
        GlassOverlayMotion.ReaderPanel -> GlassMotion.READER_EXIT_MS
    }
    val alphaEnterMs = when (motion) {
        GlassOverlayMotion.Anchored -> 140
        GlassOverlayMotion.Card -> 160
        GlassOverlayMotion.BottomPanel -> 160
        GlassOverlayMotion.ReaderPanel -> 120
    }
    val alphaEnterDelay = when (motion) {
        GlassOverlayMotion.BottomPanel -> 40
        GlassOverlayMotion.ReaderPanel -> 60
        else -> 0
    }
    val alphaExitMs = when (motion) {
        GlassOverlayMotion.Anchored -> 90
        GlassOverlayMotion.Card -> 120
        GlassOverlayMotion.BottomPanel -> 120
        GlassOverlayMotion.ReaderPanel -> 100
    }
    val scaleFrom = when (motion) {
        GlassOverlayMotion.Anchored -> GlassMotion.MENU_SCALE_FROM
        GlassOverlayMotion.Card -> GlassMotion.CARD_SCALE_FROM
        else -> 1f
    }
    val scaleTo = when (motion) {
        GlassOverlayMotion.Anchored -> GlassMotion.MENU_SCALE_TO
        GlassOverlayMotion.Card -> GlassMotion.CARD_SCALE_TO
        else -> 1f
    }
    val enterOffsetPx = with(density) {
        when (motion) {
            // 起手在锚点上方 8dp，落下来收口（"从按钮里掉出来"）
            GlassOverlayMotion.Anchored -> (-GlassMotion.MENU_LIFT_DP).dp.toPx()
            GlassOverlayMotion.BottomPanel,
            GlassOverlayMotion.ReaderPanel,
            -> GlassMotion.PANEL_RISE_DP.dp.toPx()

            GlassOverlayMotion.Card -> 0f
        }
    }
    val exitOffsetPx = with(density) {
        when (motion) {
            // 退场往上收一点，与进入方向一致
            GlassOverlayMotion.Anchored -> (-GlassMotion.MENU_LIFT_DP / 2f).dp.toPx()
            GlassOverlayMotion.BottomPanel,
            GlassOverlayMotion.ReaderPanel,
            -> GlassMotion.PANEL_FALL_DP.dp.toPx()

            GlassOverlayMotion.Card -> 0f
        }
    }

    val alpha = remember { Animatable(if (visible) 1f else 0f) }
    val scale = remember { Animatable(if (visible) 1f else scaleFrom) }
    val offsetY = remember { Animatable(if (visible) 0f else enterOffsetPx) }

    // 退场动画跑完才真正卸载（只在起止各翻转一次，不每帧重组）
    var present by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        val show = visible
        if (show) present = true
        if (!animationsEnabled) {
            alpha.snapTo(if (show) 1f else 0f)
            scale.snapTo(if (show) 1f else scaleTo)
            offsetY.snapTo(if (show) 0f else exitOffsetPx)
            if (!show) present = false
            return@LaunchedEffect
        }
        if (show) {
            // 目标是 1 / 0：退场中途被重新点开时，三个值从当前值继续往目标跑（§16 可打断）
            coroutineScope {
                launch {
                    alpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = alphaEnterMs,
                            delayMillis = alphaEnterDelay,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
                launch { scale.animateTo(1f, tween(enterMs, easing = FastOutSlowInEasing)) }
                launch { offsetY.animateTo(0f, tween(enterMs, easing = FastOutSlowInEasing)) }
            }
        } else {
            coroutineScope {
                launch { alpha.animateTo(0f, tween(alphaExitMs, easing = FastOutLinearInEasing)) }
                launch { scale.animateTo(scaleTo, tween(exitMs, easing = FastOutLinearInEasing)) }
                launch { offsetY.animateTo(exitOffsetPx, tween(exitMs, easing = FastOutLinearInEasing)) }
            }
            present = false
        }
    }

    val overlayKey = remember { Any() }
    LaunchedEffect(present) { presence?.report(overlayKey, present) }
    DisposableEffect(presence) {
        onDispose { presence?.report(overlayKey, false) }
    }

    if (!present) return

    Box(modifier = Modifier.fillMaxSize()) {
        // 关闭层：不画压暗（§5 通用规则），退场期间仍拦截点击防穿透误触，
        // 但只在 target 仍为打开时才执行 dismiss（防连点重复触发）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (visible) onDismiss() },
        )
        Box(
            modifier = modifier
                .align(alignment)
                .graphicsLayer {
                    // draw 阶段读取，不触发重组、不参与测量
                    this.alpha = alpha.value
                    translationY = offsetY.value
                    val s = scale.value
                    scaleX = s
                    scaleY = s
                    transformOrigin = origin
                }
                // 面板内点击不透给关闭层
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            GlassRounded(
                backdrop = backdrop,
                tier = GlassTier.Full,
                radius = radius,
                blurRadius = GlassDimens.StrongBlur,
                edge = GlassEdge.Outline,
                tint = tint,
            ) {
                content()
            }
        }
    }
}
