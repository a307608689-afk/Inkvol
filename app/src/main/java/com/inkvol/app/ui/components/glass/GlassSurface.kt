package com.inkvol.app.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangularShape

/**
 * 液态玻璃表面：全站唯一的材质入口。
 *
 * 三档行为：
 * - [GlassTier.Full]：vibrancy + blur + lens（折射/色散），选中项与分页胶囊使用；
 * - [GlassTier.Lite]：只做 vibrancy + blur，未选中胶囊使用，省掉透镜着色器；
 * - [GlassTier.None] 或平台不支持 RenderEffect：退化为半透明渐变 + 描边 + 内高光，
 *   几何与动效完全一致，只是没有真实模糊。
 *
 * 注意：`lens()` 只接受 RoundedRectangularShape / CornerBasedShape，其它形状会抛
 * UnsupportedOperationException，这里提前判型并降级，避免运行时崩溃。
 */
@Composable
fun GlassSurface(
    backdrop: Backdrop?,
    shape: Shape,
    tier: GlassTier,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 6.dp,
    refractionHeight: Dp = 14.dp,
    refractionAmount: Dp = 10.dp,
    highlightAlpha: Float = 0.9f,
    innerGlowAlpha: Float = 0.08f,
    edge: GlassEdge = GlassEdge.Highlight,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val surfaceTint = if (tint == Color.Unspecified) GlassDefaults.surfaceTint() else tint
    val outline = GlassDefaults.outlineColor()

    val canRefract = GlassCapabilities.refractionSupported && shape.supportsLens()
    val useBackdrop = tier != GlassTier.None && backdrop != null && GlassCapabilities.blurSupported

    // 描边模式：边缘高光基本关闭、内高光压暗，换成一圈细黑描边
    val isOutlined = edge == GlassEdge.Outline
    val effectiveHighlightAlpha = if (isOutlined) highlightAlpha * 0.12f else highlightAlpha
    val effectiveInnerGlow = if (isOutlined) innerGlowAlpha * 0.35f else innerGlowAlpha

    val highlight = if (effectiveHighlightAlpha > 0.01f) {
        remember(effectiveHighlightAlpha) {
            Highlight(width = 0.9.dp, blurRadius = 1.4.dp, alpha = effectiveHighlightAlpha)
        }
    } else {
        null
    }
    // shape / highlight 同样被库当作 remember key，固定成稳定引用避免每帧重建效果节点
    val shapeProvider: () -> Shape = remember(shape) { { shape } }
    val highlightProvider: () -> Highlight? = remember(highlight) { { highlight } }

    val glassModifier = if (useBackdrop) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = shapeProvider,
            effects = {
                vibrancy()
                blur(blurRadius.toPx())
                if (tier == GlassTier.Full && canRefract) {
                    // depthEffect 需要额外的深度图层输入，未提供时部分设备会在
                    // RenderThread 里访问空纹理；这里只保留折射本身，稳妥优先。
                    lens(
                        refractionHeight = refractionHeight.toPx(),
                        refractionAmount = refractionAmount.toPx(),
                        depthEffect = false,
                        chromaticAberration = false,
                    )
                }
            },
            highlight = highlightProvider,
            // 去掉投影：投影只会在内容上糊出一条灰边
            shadow = { null },
            layerBlock = layerBlock,
            onDrawSurface = {
                drawRect(surfaceTint)
                drawInnerGlow(effectiveInnerGlow)
            },
        )
    } else {
        Modifier
            .clip(shape)
            .background(GlassDefaults.fallbackBrush(surfaceTint))
            .border(width = 1.dp, color = outline, shape = shape)
    }

    val outlinedModifier = if (isOutlined) {
        glassModifier.border(
            width = GlassDefaults.EdgeStrokeWidth,
            color = GlassDefaults.edgeStrokeColor(),
            shape = shape,
        )
    } else {
        glassModifier
    }

    Box(modifier = modifier.then(outlinedModifier), content = content)
}

/**
 * 胶囊形状的便捷入口：连续曲率胶囊（iOS 26 观感的来源），并保证 lens 可用。
 */
@Composable
fun GlassCapsule(
    backdrop: Backdrop?,
    tier: GlassTier,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 6.dp,
    edge: GlassEdge = GlassEdge.Highlight,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        backdrop = backdrop,
        shape = GlassCapsuleShape,
        tier = tier,
        modifier = modifier,
        tint = tint,
        blurRadius = blurRadius,
        edge = edge,
        layerBlock = layerBlock,
        content = content,
    )
}

/**
 * 圆角矩形玻璃。
 *
 * 注意同心圆角规则：内部选中块必须用 `radius - 内边距` 的圆角，才能与外框同心；
 * 直接用 Capsule（半径 = 半高）会让内块曲率与外框对不上，看起来"歪"。
 */
@Composable
fun GlassRounded(
    backdrop: Backdrop?,
    tier: GlassTier,
    radius: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 6.dp,
    edge: GlassEdge = GlassEdge.Highlight,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        backdrop = backdrop,
        shape = remember(radius) { RoundedCornerShape(radius) },
        tier = tier,
        modifier = modifier,
        tint = tint,
        blurRadius = blurRadius,
        edge = edge,
        layerBlock = layerBlock,
        content = content,
    )
}

/** 玻璃材质配色：深浅两套，深色刻意压低着色与描边强度，避免墨黑底上发灰 */
object GlassDefaults {

    @Composable
    fun surfaceTint(): Color {
        val scheme = MaterialTheme.colorScheme
        val dark = isSystemInDarkTheme()
        // 垫层增强：深色内容上玻璃必须有"实底"，否则灰字灰图标会沉进背景。
        // 深色 = 深黑压暗 55%（深邃暗玻璃，参考 iOS 深色液态玻璃）；浅色 = 白纱 52%
        return if (dark) {
            Color.Black.copy(alpha = 0.55f)
        } else {
            scheme.surface.copy(alpha = 0.52f)
        }
    }

    /** 阅读器内覆盖层的垫层：面板背后是漫画页（明暗不可控），tint 需比全站默认实一档保文字对比 */
    @Composable
    fun readerPanelTint(): Color {
        val scheme = MaterialTheme.colorScheme
        val dark = isSystemInDarkTheme()
        return if (dark) {
            Color.Black.copy(alpha = 0.78f)
        } else {
            scheme.surface.copy(alpha = 0.86f)
        }
    }

    @Composable
    fun outlineColor(): Color {
        val scheme = MaterialTheme.colorScheme
        val dark = isSystemInDarkTheme()
        return if (dark) {
            scheme.outline.copy(alpha = 0.22f)
        } else {
            scheme.outlineVariant.copy(alpha = 0.40f)
        }
    }

    /** 分页条表面：比底栏再淡一档，两层玻璃才有前后层次 */
    @Composable
    fun pageBarTint(): Color {
        val scheme = MaterialTheme.colorScheme
        val dark = isSystemInDarkTheme()
        return if (dark) {
            scheme.onSurface.copy(alpha = 0.04f)
        } else {
            scheme.surface.copy(alpha = 0.08f)
        }
    }

    /** 描边模式的细线颜色：outline（比 outlineVariant 深一档，稍加深的灰线） */
    @Composable
    fun edgeStrokeColor(): Color {
        return MaterialTheme.colorScheme.outline
    }

    /** 描边模式的线宽 */
    val EdgeStrokeWidth: Dp = 1.dp

    /** 选中态的品牌色浸染：用色相混合而非直接叠加，避免糊死底下的封面 */
    @Composable
    fun selectedTint(selected: Boolean, emphasize: Boolean = false): Color {
        if (!selected) return Color.Unspecified
        val scheme = MaterialTheme.colorScheme
        val dark = isSystemInDarkTheme()
        val alpha = if (dark) 0.24f else 0.20f
        val extra = if (emphasize) 0.06f else 0f
        return scheme.primary.copy(alpha = (alpha + extra).coerceAtMost(1f))
    }

    @Composable
    fun fallbackBrush(surfaceTint: Color): Brush {
        val dark = isSystemInDarkTheme()
        val top = surfaceTint.copy(alpha = if (dark) 0.72f else 0.86f)
        val bottom = surfaceTint.copy(alpha = if (dark) 0.56f else 0.70f)
        return remember(surfaceTint, dark) {
            Brush.verticalGradient(colors = listOf(top, bottom))
        }
    }
}

/** 顶部内高光：一条自上而下淡出的白色洗光，模拟玻璃厚度 */
private fun DrawScope.drawInnerGlow(alpha: Float) {
    if (alpha <= 0f) return
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = alpha), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.85f,
        ),
    )
}

/** lens 只支持圆角矩形类形状，其它形状在库里会直接抛异常 */
private fun Shape.supportsLens(): Boolean =
    this is RoundedRectangularShape || this is CornerBasedShape
