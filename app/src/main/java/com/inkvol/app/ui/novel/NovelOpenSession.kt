package com.inkvol.app.ui.novel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.model.NovelChapterRecord
import com.inkvol.app.data.model.NovelFontRecord
import com.inkvol.app.data.model.NovelReadingPrefs
import kotlin.math.roundToInt

/** 小说一镜到底的预打开数据；Reader 直接复用，避免飞行期间重复读库、读章与首页排版。 */
class NovelOpenSession(
    val book: BookRecord,
    val chapters: List<NovelChapterRecord>,
    val chapterIndex: Int,
    val chapterText: String,
    val prefs: NovelReadingPrefs,
    val fonts: List<NovelFontRecord>,
    val pendingRestoreOffset: Int,
    val firstPagination: NovelPagination,
    val firstPageBitmap: ImageBitmap,
    /** 生成 firstPagination 时的分页宽度（双页=半页宽）；Reader 用于缓存命中校验。 */
    val paginationWidthPx: Int,
    /** M7 搜索定位：章内字符偏移（>=0 时 Reader 显示一次性临时高亮）。 */
    val locateChar: Int = -1,
    val locateLen: Int = 0,
)

/** 双页中缝宽度：分页预算与 spread 渲染/快照共用同一常量，保证排版一致。 */
internal val NOVEL_SPREAD_GUTTER = 16.dp

/** 使用与 NovelPageContent 相同的 Compose 文本测量/绘制链生成当前页快照；双页时左右两半页渲染进同一张位图。 */
internal object NovelPageSnapshot {
    fun render(
        page: NovelTextPage?,
        prefs: NovelReadingPrefs,
        theme: NovelTheme,
        fontFamily: FontFamily?,
        widthPx: Int,
        heightPx: Int,
        density: Density,
        textMeasurer: TextMeasurer,
        startPadPx: Float,
        endPadPx: Float,
        topPadPx: Float,
        /** 底部留白（与阅读器 bottomPadDp 同源）：配合 contentHeightPx 把分页余数平分上下，快照与实际页面首行位置一致。 */
        bottomPadPx: Float = 0f,
        secondPage: NovelTextPage? = null,
        /** 双页时每半页宽（与分页预算同源取整）；<=0 或无 secondPage 时按单页渲染。 */
        halfWidthPx: Int = 0,
        gutterPx: Float = 0f,
    ): ImageBitmap {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        // 正文快照 = 纯色不透明背景 + 实色文字，无 alpha 语义；RGB_565 内存与 GPU
        // 纹理上传减半（全屏 1080×2400 ARGB_8888 ≈ 10MB → 5MB），抗锯齿边缘混入 RGB 即可，
        // 交接帧观感无损，直接降低一镜到底打开/返回交叉时的首次纹理上传成本。
        val bitmap = ImageBitmap(width, height, ImageBitmapConfig.Rgb565)
        val canvas = Canvas(bitmap)
        val dual = secondPage != null && halfWidthPx > 0
        CanvasDrawScope().draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(width.toFloat(), height.toFloat()),
        ) {
            drawRect(theme.background)
            if (page == null) return@draw

            fun drawColumn(target: NovelTextPage, x: Float, contentWidth: Int) {
                // 渲染余数平分上下（与 NovelPageContent 同口径）：页高 - 内容高的余数一半垫到首行上方。
                // M6.4-K 对齐：真实渲染 Column(spacedBy) 在「余数垫高 Spacer 与首片」之间也插一个
                // 段距，快照这里必须同样在首片前补一个段距，否则快照正文比真实正文偏上一个段距，
                // 打开动画交接瞬间正文下跳（「正文落地轻微位置调整」）。
                val remPx = (
                    height - topPadPx.roundToInt() - bottomPadPx.roundToInt() -
                        target.contentHeightPx
                    ).coerceAtLeast(0)
                var top = topPadPx + remPx / 2f + prefs.paragraphSpacing.dp.toPx()
                target.slices.forEachIndexed { index, slice ->
                    val layout = textMeasurer.measure(
                        text = AnnotatedString(slice.text),
                        // M6.4-K：续片快照同样去首行缩进，与分页/阅读器渲染口径一致。
                        style = if (slice.isBlockStart) {
                            novelTextStyle(prefs, theme, fontFamily)
                        } else {
                            novelTextStyle(prefs, theme, fontFamily)
                                .copy(textIndent = TextIndent(firstLine = 0.sp))
                        },
                        constraints = Constraints(maxWidth = contentWidth),
                    )
                    drawText(
                        textLayoutResult = layout,
                        color = theme.text,
                        topLeft = Offset(x, top),
                    )
                    top += slice.heightPx
                    if (index != target.slices.lastIndex) {
                        top += prefs.paragraphSpacing.dp.toPx()
                    }
                }
            }

            if (!dual) {
                val contentWidth = (width - startPadPx - endPadPx).roundToInt().coerceAtLeast(1)
                drawColumn(page, startPadPx, contentWidth)
            } else {
                val gutter = gutterPx.coerceAtLeast(0f)
                drawColumn(page, startPadPx, halfWidthPx)
                drawColumn(secondPage, startPadPx + halfWidthPx + gutter, halfWidthPx)
            }
        }
        return bitmap
    }
}

/** 挖孔安全留白：基准边距与挖孔 insets（另加少量呼吸空隙）取大者；无挖孔时恒等于基准。 */
internal fun cutoutSafeDp(baseline: Dp, insetDp: Dp): Dp =
    if (insetDp.value <= 0f) {
        baseline
    } else {
        baseline.coerceAtLeast(insetDp + 6.dp)
    }
