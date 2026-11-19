package com.inkvol.app.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop

/**
 * 分页悬浮层：负责"贴在底栏正上方 + 跟随 chrome 一起沉出屏幕"。
 *
 * 调用方只需给出 `Modifier.align(Alignment.BottomCenter)`，其余定位与联动都在这里。
 */
@Composable
fun GlassPageBarOverlay(
    visible: Boolean,
    page: Int,
    loading: Boolean,
    hasMore: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 必须传"不含本组件自身"的采集层（页面内自建，包裹列表即可）。
     * 传宿主那层会导致采集子树里包含采样节点本身 → RenderNode 无限递归 → RenderThread 崩。
     */
    backdrop: Backdrop? = null,
) {
    if (!visible) return
    val chrome = LocalGlassChrome.current
    // 平板侧栏形态：挂屏幕左侧、垂直居中，且常驻不隐藏
    val railMode = chrome.railMode
    val progress = if (railMode) 0f else LocalGlassChromeProgress.current
    // 没有底部 chrome（二级页 / 平板侧栏形态）时只让出导航栏，不能贴到屏幕最底被手势条裁掉
    val baseBottom = if (!chrome.chromeVisible) {
        chrome.bottomGap + chrome.pageBarGap
    } else {
        chrome.bottomBarHeight + chrome.bottomGap + chrome.pageBarGap
    }

    GlassPageBar(
        page = page,
        loading = loading,
        hasMore = hasMore,
        onPrev = onPrev,
        onNext = onNext,
        onJump = onJump,
        backdrop = backdrop,
        modifier = modifier
            .padding(
                // 平板：与侧栏同列（左缘 14）；手机：与底栏同源（左缘 24）
                start = if (railMode) GlassDimens.RailMargin else GlassDimens.BarHorizontalMargin,
                bottom = if (railMode) 0.dp else baseBottom,
            )
            .offset(y = if (railMode) 0.dp else (chrome.pageBarHeight + GlassDimens.PageBarGap) * progress)
            .graphicsLayer {
                alpha = if (railMode) 1f else 1f - (1f - GlassMotion.HIDDEN_ALPHA) * progress
            },
    )
}

/**
 * 一整条分页胶囊：`‹ | 第 N 页 | ›` 三个区段收在同一条玻璃里，
 * 用竖直细分割线分区，而不是三枚互相分离的胶囊。
 */
@Composable
private fun GlassPageBar(
    page: Int,
    loading: Boolean,
    hasMore: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (Int) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    var showJump by remember { mutableStateOf(false) }
    var input by remember(page) { mutableStateOf(page.toString()) }
    val scheme = MaterialTheme.colorScheme

    GlassCapsule(
        backdrop = backdrop,
        tier = if (loading) GlassTier.Lite else GlassTier.Full,
        blurRadius = GlassDimens.StrongBlur,
        tint = GlassDefaults.pageBarTint(),
        modifier = modifier.height(GlassDimens.PageBarHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                // 与底栏一致：两端箭头离玻璃边缘各让 8dp
                .padding(horizontal = GlassDimens.BarInnerPaddingX),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            PageBarArrow(
                icon = Icons.Outlined.ChevronLeft,
                description = "上一页",
                enabled = page > 1 && !loading,
                onClick = onPrev,
            )

            val pageInteraction = remember { MutableInteractionSource() }
            Text(
                text = if (loading) "加载中…" else "第 $page 页",
                color = scheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .clickable(
                        enabled = !loading,
                        interactionSource = pageInteraction,
                        indication = null,
                    ) {
                        input = page.toString()
                        showJump = true
                    }
                    .padding(horizontal = 20.dp),
            )

            PageBarArrow(
                icon = Icons.Outlined.ChevronRight,
                description = "下一页",
                enabled = hasMore && !loading,
                onClick = onNext,
            )
        }
    }

    if (showJump) {
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("跳转到页") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value -> input = value.filter { it.isDigit() } },
                    singleLine = true,
                    label = { Text("页码") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        input.toIntOrNull()?.takeIf { it > 0 }?.let(onJump)
                        showJump = false
                    },
                ) { Text("跳转") }
            },
            dismissButton = {
                TextButton(onClick = { showJump = false }) { Text("取消") }
            },
        )
    }
}

/**
 * 横排页数栏：‹ 第 N 页 › 一条玻璃横胶囊（手机底部悬浮行用）。
 */
@Composable
fun GlassPageBarHorizontal(
    page: Int,
    loading: Boolean,
    hasMore: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (Int) -> Unit,
    backdrop: Backdrop?,
    edge: GlassEdge = GlassEdge.Highlight,
    modifier: Modifier = Modifier,
) {
    var showJump by remember { mutableStateOf(false) }
    var input by remember(page) { mutableStateOf(page.toString()) }
    val scheme = MaterialTheme.colorScheme

    GlassRounded(
        backdrop = backdrop,
        tier = if (loading) GlassTier.Lite else GlassTier.Full,
        radius = 20.dp,
        blurRadius = GlassDimens.StrongBlur,
        edge = edge,
        modifier = modifier.height(GlassDimens.PageBarHeight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageBarArrow(
                icon = Icons.Outlined.ChevronLeft,
                description = "上一页",
                enabled = page > 1 && !loading,
                onClick = onPrev,
            )
            val numInteraction = remember { MutableInteractionSource() }
            Text(
                text = if (loading) "…" else "第 $page 页",
                color = scheme.primary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.30f),
                        offset = Offset(0f, 1f),
                        blurRadius = 2f,
                    ),
                ),
                maxLines = 1,
                modifier = Modifier
                    .clickable(
                        enabled = !loading,
                        interactionSource = numInteraction,
                        indication = null,
                    ) {
                        input = page.toString()
                        showJump = true
                    }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            PageBarArrow(
                icon = Icons.Outlined.ChevronRight,
                description = "下一页",
                enabled = hasMore && !loading,
                onClick = onNext,
            )
        }
    }

    if (showJump) {
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("跳转到页") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value -> input = value.filter { it.isDigit() } },
                    singleLine = true,
                    label = { Text("页码") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        input.toIntOrNull()?.takeIf { it > 0 }?.let(onJump)
                        showJump = false
                    },
                ) { Text("跳转") }
            },
            dismissButton = {
                TextButton(onClick = { showJump = false }) { Text("取消") }
            },
        )
    }
}

/**
 * 竖排页数栏：上箭头 / 页码数字 / 下箭头，从上往下排——
 * 平板侧栏形态下**套在自己的玻璃竖条里**（独立玻璃件，与导航条分离）。
 */
@Composable
fun PageBarVertical(
    page: Int,
    loading: Boolean,
    hasMore: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (Int) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    var showJump by remember { mutableStateOf(false) }
    var input by remember(page) { mutableStateOf(page.toString()) }
    val scheme = MaterialTheme.colorScheme

    GlassRounded(
        backdrop = backdrop,
        tier = if (loading) GlassTier.Lite else GlassTier.Full,
        radius = GlassDimens.RailCorner,
        blurRadius = GlassDimens.StrongBlur,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        VerticalArrow(
            icon = Icons.Outlined.KeyboardArrowUp,
            description = "上一页",
            enabled = page > 1 && !loading,
            onClick = onPrev,
        )
        val numInteraction = remember { MutableInteractionSource() }
        Text(
            text = if (loading) "…" else "$page",
            color = scheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge.copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.30f),
                    offset = Offset(0f, 1f),
                    blurRadius = 2f,
                ),
            ),
            maxLines = 1,
            modifier = Modifier
                .clickable(
                    enabled = !loading,
                    interactionSource = numInteraction,
                    indication = null,
                ) {
                    input = page.toString()
                    showJump = true
                }
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
        VerticalArrow(
            icon = Icons.Outlined.KeyboardArrowDown,
            description = "下一页",
            enabled = hasMore && !loading,
            onClick = onNext,
        )
    }
        }

    if (showJump) {
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("跳转到页") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value -> input = value.filter { it.isDigit() } },
                    singleLine = true,
                    label = { Text("页码") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        input.toIntOrNull()?.takeIf { it > 0 }?.let(onJump)
                        showJump = false
                    },
                ) { Text("跳转") }
            },
            dismissButton = {
                TextButton(onClick = { showJump = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun VerticalArrow(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = scheme.onSurface.copy(alpha = if (enabled) 0.72f else 0.28f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PageBarArrow(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(GlassDimens.PageBarHeight)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = scheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.32f),
            modifier = Modifier.size(20.dp),
        )
    }
}


