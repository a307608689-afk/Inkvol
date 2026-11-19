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

/** 一个分页切片；slice 可能把长段落拆到下一页。 */
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
        val textWidthPx = widthPx
        val pageHeightPx = heightPx
        val slices = ArrayList<NovelTextSlice>()

        // 只排到目标偏移所在段落的下一段：预打开快照无需整章排版。
        val targetBlockIndex = if (stopAfterCharOffset >= 0) {
            blocks.indexOfFirst { stopAfterCharOffset >= it.charStart && stopAfterCharOffset < it.charEnd }
        } else {
            -1
        }
        val blockLimit = if (targetBlockIndex >= 0) targetBlockIndex + 2 else blocks.size

        blocks.take(blockLimit).forEachIndexed { blockIndex, block ->
            // M6.2-B：EPUB 图片块 → 单个图片 slice，高度按内容宽等比缩放，最高占满一页。
            if (block.imagePath != null) {
                slices += imageSlice(block, textWidthPx, pageHeightPx)
                return@forEachIndexed
            }
            val layout = textMeasurer.measure(
                text = block.text,
                style = textStyle,
                constraints = Constraints(maxWidth = textWidthPx),
            )
            val lineCount = layout.lineCount.coerceAtLeast(1)
            if (layout.size.height <= pageHeightPx) {
                slices += NovelTextSlice(
                    text = block.text,
                    charStart = block.charStart,
                    charEnd = block.charEnd,
                    isBlockStart = true,
                    heightPx = layout.size.height,
                )
                return@forEachIndexed
            }

            // 长段落按行拆页；同一行组仍保持原始换行结果，避免重排错页。
            val lineHeightPx = (layout.size.height.toFloat() / lineCount).roundToInt().coerceAtLeast(1)
            val linesPerPage = (pageHeightPx / lineHeightPx).coerceAtLeast(1)
            var start = 0
            while (start < lineCount) {
                val end = (start + linesPerPage).coerceAtMost(lineCount)
                val sliceStart = layout.getLineStart(start)
                val sliceEnd = layout.getLineEnd(end - 1)
                val sliceText = block.text.substring(sliceStart, sliceEnd)
                val sliceLayout = textMeasurer.measure(
                    text = sliceText,
                    style = textStyle,
                    constraints = Constraints(maxWidth = textWidthPx),
                )
                slices += NovelTextSlice(
                    text = sliceText,
                    charStart = block.charStart + sliceStart,
                    charEnd = block.charStart + sliceEnd,
                    isBlockStart = start == 0,
                    heightPx = sliceLayout.size.height,
                )
                start = end
            }
            if (blockIndex != blockLimit - 1) {
                slices.lastOrNull()?.let { slice ->
                    slices[slices.lastIndex] = slice.copy(heightPx = slice.heightPx + paragraphSpacingPx)
                }
            }
        }

        val pages = ArrayList<NovelTextPage>()
        var currentPage = ArrayList<NovelTextSlice>()
        var currentHeight = 0
        var currentPageStart = -1
        var currentPageEnd = -1

        for (slice in slices) {
            val needed = slice.heightPx + if (currentPage.isEmpty()) 0 else paragraphSpacingPx
            if (currentPage.isNotEmpty() && currentHeight + needed > pageHeightPx) {
                pages += NovelTextPage(
                    slices = currentPage.toList(),
                    charStart = currentPageStart,
                    charEnd = currentPageEnd,
                )
                if (pages.size >= maxPages) break
                currentPage = ArrayList()
                currentHeight = 0
                currentPageStart = -1
                currentPageEnd = -1
            }

            val extra = if (currentPage.isEmpty()) 0 else paragraphSpacingPx
            currentPage += slice
            currentHeight += slice.heightPx + extra
            if (currentPageStart < 0) currentPageStart = slice.charStart
            currentPageEnd = slice.charEnd
            // 目标偏移所在页已完整生成即可停止。
            if (stopAfterCharOffset >= 0 && currentPageEnd > stopAfterCharOffset) break
        }
        if (pages.size < maxPages && currentPage.isNotEmpty()) {
            pages += NovelTextPage(
                slices = currentPage.toList(),
                charStart = currentPageStart,
                charEnd = currentPageEnd,
            )
        }
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

    /** 图片块切片：宽 = 内容宽，高 = 宽 / 宽高比，clamp 到一页高；解码失败按 4:3 兜底。 */
    private fun imageSlice(
        block: NovelTextBlock,
        contentWidthPx: Int,
        pageHeightPx: Int,
    ): NovelTextSlice {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(File(block.imagePath!!).absolutePath, bounds) }
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
            heightPx = height.coerceIn(1, pageHeightPx.coerceAtLeast(1)),
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
