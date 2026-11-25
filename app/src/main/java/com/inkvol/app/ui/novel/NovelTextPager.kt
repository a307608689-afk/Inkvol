package com.inkvol.app.ui.novel

import android.graphics.BitmapFactory
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.inkvol.app.data.model.NovelReadingPrefs
import java.io.File
import kotlin.math.roundToInt

/** M6.1-B：TXT 段落块模型；char 偏移指向章节文本，用于进度锚定。M6.2-B： imagePath 非空 = EPUB 图片块。 */
data class NovelTextBlock(
    val text: String,
    val charStart: Int,
    val charEnd: Int,
    val imagePath: String? = null,
)

/** 一个分页切片；段落跨页时把段落拆到下一页。 */
data class NovelTextSlice(
    val text: String,
    val charStart: Int,
    val charEnd: Int,
    val isBlockStart: Boolean,
    val heightPx: Int,
    val imagePath: String? = null,
)

data class NovelTextPage(
    val slices: List<NovelTextSlice>,
    val charStart: Int,
    val charEnd: Int,
    /** 渲染视角内容高度（Σ 片高 + 每片一个段距——含渲染侧「垫高 Spacer 与首片」之间的段距，与 spacedBy 渲染口径一致）；渲染侧用它把分页余数平分上下。0 = 未统计。 */
    val contentHeightPx: Int = 0,
)

data class NovelPagination(
    val blocks: List<NovelTextBlock>,
    val pages: List<NovelTextPage>,
)

/** 按排版偏好把章节文本切成可渲染页；使用 Compose TextMeasurer，不接 WebView。 */
object NovelTextPager {

    private val IMG_MARKER = Regex("^\\[IMG:([^\\]]+)]$")

    fun empty(): NovelPagination = NovelPagination(emptyList(), emptyList())

    fun paginate(
        chapterText: String,
        prefs: NovelReadingPrefs,
        fontFamily: FontFamily?,
        widthPx: Int,
        heightPx: Int,
        density: androidx.compose.ui.unit.Density,
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        maxPages: Int = Int.MAX_VALUE,
        stopAfterCharOffset: Int = -1,
        /**
         * 插图根目录（通常是 filesDir）：marker 里存的是相对路径，量图片宽高比必须补上根目录，
         * 否则 decodeFile 找不到文件 → 所有插图按 4:3 兜底（高度全错）。
         */
        imageBaseDir: File? = null,
    ): NovelPagination {
        val blocks = parseBlocks(chapterText)
        if (blocks.isEmpty() || widthPx <= 0 || heightPx <= 0) {
            return NovelPagination(blocks, emptyList())
        }

        val textStyle = TextStyle(
            fontSize = prefs.fontSize.sp,
            lineHeight = (prefs.fontSize * prefs.lineHeightRatio).sp,
            letterSpacing = prefs.letterSpacing.sp,
            fontFamily = fontFamily,
            textIndent = TextIndent(firstLine = (prefs.indentChars * prefs.fontSize).sp),
        )
        val paragraphSpacingPx = with(density) { prefs.paragraphSpacing.dp.toPx().roundToInt() }
        // M6.4-K 续片重测样式：整块布局里续行无首行缩进，子串带 firstLine 缩进
        // 单独重测会换行不一致（可能多折一行）→ 超出页预算被裁。
        val continuationStyle = textStyle.copy(textIndent = TextIndent(firstLine = 0.sp))
        val textWidthPx = widthPx
        val pageHeightPx = heightPx
        val pages = ArrayList<NovelTextPage>()
        var currentPage = ArrayList<NovelTextSlice>()
        var currentHeight = 0
        var currentPageStart = -1
        var currentPageEnd = -1

        fun flushPage() {
            if (currentPage.isEmpty()) return
            pages += NovelTextPage(
                slices = currentPage.toList(),
                charStart = currentPageStart,
                charEnd = currentPageEnd,
                contentHeightPx = currentHeight,
            )
            currentPage = ArrayList()
            currentHeight = 0
            currentPageStart = -1
            currentPageEnd = -1
        }

        // M6.4-K 页底裁切修复：渲染侧 Column 的 spacedBy 在「余数垫高 Spacer 与首片」
        // 之间也会插一个段距（渲染恒比分页预算多一个段距；页尾余数 < 2×段距时底部
        // 溢出、末行被裁掉一部分）。预算侧同步恒计一个段距（覆盖 Spacer 缺口），
        // 预算与渲染逐像素对齐。
        fun appendSlice(slice: NovelTextSlice) {
            currentHeight += slice.heightPx + paragraphSpacingPx
            currentPage += slice
            if (currentPageStart < 0) currentPageStart = slice.charStart
            currentPageEnd = slice.charEnd
        }

        /** 本页剩余可装高度（已扣除下一个段距缺口，空页含 Spacer 与首片之间的段距）。 */
        fun pageRemain(): Int =
            pageHeightPx - currentHeight - paragraphSpacingPx

        // 目标偏移只用于决定「排到哪一页为止」，且**必须等整页刷出后再停**：
        // 若在追加第一片后就 break，目标页只剩一行/一段；渲染侧把页内余量平分到上下
        //（remPx/2），预打开快照与首屏就表现为「屏幕中间只冒出一行字，随后才出现整页正文」。
        val hasTarget = stopAfterCharOffset >= 0
        // 早停改由 targetReached + 页刷出触发，不再按块数预截断（否则填不满目标页）。
        val blockLimit = blocks.size

        var targetReached = false
        var stopped = false
        blocks.take(blockLimit).forEachIndexed { blockIndex, block ->
            if (stopped) return@forEachIndexed
            // M6.2-B：EPUB 图片块 → 单个图片 slice，高度按内容宽等比缩放，最高占满一页；图片不跨页。
            if (block.imagePath != null) {
                // M6.4-K：首位段距已恒计预算，图片满页高会溢出一个段距 → 可用高同步收紧。
                val slice = imageSlice(
                    block,
                    textWidthPx,
                    pageHeightPx - paragraphSpacingPx,
                    imageBaseDir,
                )
                if (currentPage.isNotEmpty() && slice.heightPx > pageRemain()) {
                    flushPage()
                    if (targetReached) {
                        stopped = true
                        return@forEachIndexed
                    }
                }
                appendSlice(slice)
                if (hasTarget && currentPageEnd > stopAfterCharOffset) targetReached = true
                return@forEachIndexed
            }
            val layout = textMeasurer.measure(
                text = block.text,
                style = textStyle,
                constraints = Constraints(maxWidth = textWidthPx),
            )
            val lineCount = layout.lineCount.coerceAtLeast(1)
            // 行高向上取整：行数预算只少不多估 → 片实测高度必不超预算，页尾零头收敛到不足一行。
            val lineHeightPx = ((layout.size.height + lineCount - 1) / lineCount).coerceAtLeast(1)
            var start = 0
            while (start < lineCount) {
                var fitLines = pageRemain() / lineHeightPx
                if (currentPage.isEmpty() && fitLines <= 0) fitLines = 1 // 空页保底，防死循环
                val end = (start + fitLines).coerceAtMost(lineCount)
                if (end <= start) {
                    // 非空页连一行也放不下 → 切页重试（空页已被保底覆盖，不会死循环）。
                    flushPage()
                    if (targetReached) {
                        // 目标页已满页刷出、内容完整，可以停
                        stopped = true
                        break
                    }
                    continue
                }
                val sliceStart = layout.getLineStart(start)
                val sliceEnd = layout.getLineEnd(end - 1)
                val sliceText = block.text.substring(sliceStart, sliceEnd)
                // 整块装入直接用整块测量高度；跨页拆片/尾片按子串实测。
                val sliceHeight = if (start == 0 && end == lineCount) {
                    layout.size.height
                } else {
                    textMeasurer.measure(
                        text = sliceText,
                        // M6.4-K：续片（start>0）用无缩进样式重测，与整块布局换行一致。
                        style = if (start == 0) textStyle else continuationStyle,
                        constraints = Constraints(maxWidth = textWidthPx),
                    ).size.height
                }
                appendSlice(
                    NovelTextSlice(
                        text = sliceText,
                        charStart = block.charStart + sliceStart,
                        charEnd = block.charStart + sliceEnd,
                        isBlockStart = start == 0,
                        heightPx = sliceHeight,
                    ),
                )
                // 目标偏移已覆盖：只做标记，继续把这一页填满（否则会留下「只有一行」的残页）。
                if (hasTarget && currentPageEnd > stopAfterCharOffset) targetReached = true
                start = end
                if (start < lineCount) {
                    // 段落跨页（Kindle 同款惯例）：余行进下页，替代旧的「整段推下页」，
                    // 页尾装填零头从「一段+段距」收敛到不足一行 → 上下留空恒小且页间稳定。
                    flushPage()
                    if (targetReached) {
                        // 目标页已满页刷出、内容完整，可以停
                        stopped = true
                        break
                    }
                    if (pages.size >= maxPages) {
                        stopped = true
                        break
                    }
                }
            }
        }
        flushPage()
        return NovelPagination(blocks, pages)
    }

    private fun parseBlocks(text: String): List<NovelTextBlock> {
        val blocks = ArrayList<NovelTextBlock>()
        var current = StringBuilder()
        var currentStart = 0
        var index = 0
        text.lines().forEach { rawLine ->
            val lineStart = index
            val lineEnd = lineStart + rawLine.length
            index = lineEnd + 1
            // M6.2-B：EPUB 图片标记行 → 独立图片块
            val marker = IMG_MARKER.matchEntire(rawLine.trim())
            if (marker != null) {
                flush(current, currentStart, lineStart, blocks)
                current = StringBuilder()
                blocks += NovelTextBlock(
                    text = rawLine.trim(),
                    charStart = lineStart,
                    charEnd = lineEnd,
                    imagePath = marker.groupValues[1],
                )
                return@forEach
            }
            if (rawLine.isBlank()) {
                flush(current, currentStart, lineStart, blocks)
                current = StringBuilder()
            } else {
                if (current.isEmpty()) currentStart = lineStart
                current.append(rawLine).append('\n')
            }
        }
        flush(current, currentStart, text.length, blocks)
        return blocks
    }

    /** 图片块切片：宽 = 内容宽，高 = 宽 / 宽高比，clamp 到单页可装高度（页高 − 首位段距）；解码失败按 4:3 兜底。 */
    private fun imageSlice(
        block: NovelTextBlock,
        contentWidthPx: Int,
        maxSliceHeightPx: Int,
        imageBaseDir: File?,
    ): NovelTextSlice {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // M6.4：marker 是相对路径，必须先挂根目录再 decodeFile（此前直接 File(相对路径)，
        // cwd 下不存在 → outWidth=0 → 全部插图按 4:3 兜底，长图/竖图比例失真）。
        val imageFile = imageBaseDir?.let { File(it, block.imagePath!!) }
            ?: File(block.imagePath!!)
        runCatching { BitmapFactory.decodeFile(imageFile.absolutePath, bounds) }
        val height = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            (contentWidthPx.toFloat() * bounds.outHeight / bounds.outWidth).roundToInt()
        } else {
            contentWidthPx * 3 / 4
        }
        return NovelTextSlice(
            text = "",
            charStart = block.charStart,
            charEnd = block.charEnd,
            isBlockStart = true,
            heightPx = height.coerceIn(1, maxSliceHeightPx.coerceAtLeast(1)),
            imagePath = block.imagePath,
        )
    }

    private fun flush(
        current: StringBuilder,
        start: Int,
        end: Int,
        out: MutableList<NovelTextBlock>,
    ) {
        val text = current.toString().trim()
        if (text.isNotEmpty()) out += NovelTextBlock(text = text, charStart = start, charEnd = end)
    }
}
