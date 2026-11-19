package com.inkvol.app.ui.reader.panel

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * M5-A：本地漫画分格检测 Spike。
 *
 * 目标不是给出最终产品级算法，而是验证「投影法 + 递归分割 + 置信度兜底」这条路是否可用。
 * 输入是解码后的页图，输出是归一化分格矩形；不修改源文件，也不接阅读器主流程。
 */
object PanelDetector {

    enum class LayoutStatus {
        WholePage,
        Panelized,
    }

    enum class ReadingDirection {
        LeftToRight,
        RightToLeft,
    }

    enum class FallbackReason {
        NoSplittableGutter,
        TooSmallFrames,
        TooManyFrames,
        LowConfidence,
        InvalidBitmap,
        AnalysisFailed,
    }

    data class Frame(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    data class Layout(
        val status: LayoutStatus,
        val frames: List<Frame>,
        val confidence: Float,
        val reason: FallbackReason?,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val analysisWidth: Int,
        val analysisHeight: Int,
        val elapsedMs: Long,
    )

    private data class IntRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    private data class Gutter(
        val start: Int,
        val endInclusive: Int,
        val score: Float,
    ) {
        val length: Int get() = endInclusive - start + 1
        val center: Int get() = start + length / 2
    }

    private const val MAX_SAMPLE_DIMENSION = 720
    private const val MAX_RECURSION_DEPTH = 4
    private const val MAX_FRAMES = 12
    private const val MIN_CONFIDENCE = 0.62f

    fun analyze(
        bitmap: Bitmap?,
        readingDirection: ReadingDirection = ReadingDirection.RightToLeft,
        maxSampleDimension: Int = MAX_SAMPLE_DIMENSION,
    ): Layout {
        val startedAt = System.nanoTime()
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return wholePage(
                width = 0,
                height = 0,
                analysisWidth = 0,
                analysisHeight = 0,
                elapsedMs = elapsed(startedAt),
                reason = FallbackReason.InvalidBitmap,
            )
        }

        val scaled = try {
            downsample(bitmap, maxSampleDimension)
        } catch (_: Throwable) {
            return wholePage(
                width = bitmap.width,
                height = bitmap.height,
                analysisWidth = 0,
                analysisHeight = 0,
                elapsedMs = elapsed(startedAt),
                reason = FallbackReason.AnalysisFailed,
            )
        }
        val ownScaled = scaled !== bitmap

        try {
            val width = scaled.width
            val height = scaled.height
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            val background = estimateBackground(pixels, width, height)
            val rawMask = buildForegroundMask(pixels, width, height, background)
            val mask = cleanMask(rawMask, width, height)
            val region = contentBounds(mask, width, height)
                ?: return wholePage(
                    width = bitmap.width,
                    height = bitmap.height,
                    analysisWidth = width,
                    analysisHeight = height,
                    elapsedMs = elapsed(startedAt),
                    reason = FallbackReason.NoSplittableGutter,
                )

            val regions = splitRegion(
                mask = mask,
                width = width,
                height = height,
                region = region,
                depth = 0,
            )

            if (regions.size < 2) {
                return wholePage(
                    width = bitmap.width,
                    height = bitmap.height,
                    analysisWidth = width,
                    analysisHeight = height,
                    elapsedMs = elapsed(startedAt),
                    reason = FallbackReason.NoSplittableGutter,
                )
            }

            val frames = regions
                .mapNotNull { region -> normalizedFrame(region, width, height) }
                .sortedWith(
                    compareBy(
                        { it.top },
                        { if (readingDirection == ReadingDirection.RightToLeft) -it.left else it.left },
                    ),
                )

            if (frames.size < 2) {
                return wholePage(
                    width = bitmap.width,
                    height = bitmap.height,
                    analysisWidth = width,
                    analysisHeight = height,
                    elapsedMs = elapsed(startedAt),
                    reason = FallbackReason.TooSmallFrames,
                )
            }
            if (frames.size > MAX_FRAMES) {
                return wholePage(
                    width = bitmap.width,
                    height = bitmap.height,
                    analysisWidth = width,
                    analysisHeight = height,
                    elapsedMs = elapsed(startedAt),
                    reason = FallbackReason.TooManyFrames,
                )
            }

            val confidence = scoreLayout(frames, width, height)
            if (confidence < MIN_CONFIDENCE) {
                return wholePage(
                    width = bitmap.width,
                    height = bitmap.height,
                    analysisWidth = width,
                    analysisHeight = height,
                    elapsedMs = elapsed(startedAt),
                    reason = FallbackReason.LowConfidence,
                    confidence = confidence,
                )
            }

            return Layout(
                status = LayoutStatus.Panelized,
                frames = frames,
                confidence = confidence,
                reason = null,
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
                analysisWidth = width,
                analysisHeight = height,
                elapsedMs = elapsed(startedAt),
            )
        } catch (_: Throwable) {
            return wholePage(
                width = bitmap.width,
                height = bitmap.height,
                analysisWidth = scaled.width,
                analysisHeight = scaled.height,
                elapsedMs = elapsed(startedAt),
                reason = FallbackReason.AnalysisFailed,
            )
        } finally {
            if (ownScaled) scaled.recycle()
        }
    }

    private fun downsample(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun estimateBackground(pixels: IntArray, width: Int, height: Int): Int {
        val inset = max(1, (min(width, height) * 0.04f).roundToInt())
        val reds = ArrayList<Int>()
        val greens = ArrayList<Int>()
        val blues = ArrayList<Int>()
        val stepX = max(1, width / 128)
        val stepY = max(1, height / 128)

        for (x in 0 until width step stepX) {
            for (y in 0 until min(inset, height)) {
                samplesAt(pixels, y * width + x, reds, greens, blues)
            }
            for (y in max(0, height - inset) until height) {
                samplesAt(pixels, y * width + x, reds, greens, blues)
            }
        }
        for (y in 0 until height step stepY) {
            for (x in 0 until min(inset, width)) {
                samplesAt(pixels, y * width + x, reds, greens, blues)
            }
            for (x in max(0, width - inset) until width) {
                samplesAt(pixels, y * width + x, reds, greens, blues)
            }
        }

        if (reds.isEmpty()) return Color.WHITE
        reds.sort()
        greens.sort()
        blues.sort()
        val r = reds[reds.size / 2]
        val g = greens[greens.size / 2]
        val b = blues[blues.size / 2]
        return Color.rgb(r, g, b)
    }

    private fun samplesAt(
        pixels: IntArray,
        index: Int,
        reds: MutableList<Int>,
        greens: MutableList<Int>,
        blues: MutableList<Int>,
    ) {
        val pixel = pixels[index]
        reds += Color.red(pixel)
        greens += Color.green(pixel)
        blues += Color.blue(pixel)
    }

    private fun buildForegroundMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        background: Int,
    ): BooleanArray {
        val mask = BooleanArray(pixels.size)
        val bgR = Color.red(background)
        val bgG = Color.green(background)
        val bgB = Color.blue(background)
        val threshold = 72 // 三个通道平均差约 24，扫描噪点不会全部判成内容。
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val distance = abs(Color.red(pixel) - bgR) +
                abs(Color.green(pixel) - bgG) +
                abs(Color.blue(pixel) - bgB)
            mask[index] = distance > threshold
        }
        return mask
    }

    /** 去掉孤立噪点，避免扫描件上一两个杂色像素干扰投影。 */
    private fun cleanMask(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val cleaned = BooleanArray(mask.size)
        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) {
                val index = rowStart + x
                if (!mask[index]) continue
                var neighbors = 0
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= width) continue
                        if (dx == 0 && dy == 0) continue
                        if (mask[ny * width + nx]) neighbors++
                    }
                }
                cleaned[index] = neighbors >= 1
            }
        }
        return cleaned
    }

    private fun contentBounds(
        mask: BooleanArray,
        width: Int,
        height: Int,
    ): IntRect? {
        var left = -1
        var right = -1
        var top = -1
        var bottom = -1
        val rowThreshold = max(1, (width * 0.003f).roundToInt())
        val columnThreshold = max(1, (height * 0.003f).roundToInt())

        for (y in 0 until height) {
            var count = 0
            val rowStart = y * width
            for (x in 0 until width) {
                if (mask[rowStart + x]) count++
            }
            if (count >= rowThreshold) {
                if (top < 0) top = y
                bottom = y + 1
            }
        }
        if (top < 0) return null

        for (x in 0 until width) {
            var count = 0
            for (y in 0 until height) {
                if (mask[y * width + x]) count++
            }
            if (count >= columnThreshold) {
                if (left < 0) left = x
                right = x + 1
            }
        }
        if (left < 0 || right <= left || bottom <= top) return null
        return IntRect(left, top, right, bottom)
    }

    private fun splitRegion(
        mask: BooleanArray,
        width: Int,
        height: Int,
        region: IntRect,
        depth: Int,
    ): List<IntRect> {
        val bounds = contentBoundsIn(mask, width, height, region) ?: return emptyList()
        if (bounds.width < 4 || bounds.height < 4) return listOf(bounds)
        if (depth >= MAX_RECURSION_DEPTH) return listOf(bounds)

        val horizontalGutters = findGutters(mask, width, height, bounds, horizontal = true)
        val verticalGutters = findGutters(mask, width, height, bounds, horizontal = false)
        val bestHorizontal = horizontalGutters.maxByOrNull { it.score }
        val bestVertical = verticalGutters.maxByOrNull { it.score }

        val useHorizontal = when {
            bestHorizontal == null -> false
            bestVertical == null -> true
            else -> bestHorizontal.score >= bestVertical.score
        }

        if (useHorizontal) {
            val gutter = bestHorizontal ?: return listOf(bounds)
            val splitAt = gutter.center
            if (splitAt <= bounds.top + 1 || splitAt >= bounds.bottom - 1) return listOf(bounds)
            val upper = splitRegion(
                mask = mask,
                width = width,
                height = height,
                region = IntRect(bounds.left, bounds.top, bounds.right, splitAt),
                depth = depth + 1,
            )
            val lower = splitRegion(
                mask = mask,
                width = width,
                height = height,
                region = IntRect(bounds.left, splitAt, bounds.right, bounds.bottom),
                depth = depth + 1,
            )
            return mergeRegions(upper + lower)
        }

        if (bestVertical == null) return listOf(bounds)
        val splitAt = bestVertical.center
        if (splitAt <= bounds.left + 1 || splitAt >= bounds.right - 1) return listOf(bounds)
        val left = splitRegion(
            mask = mask,
            width = width,
            height = height,
            region = IntRect(bounds.left, bounds.top, splitAt, bounds.bottom),
            depth = depth + 1,
        )
        val right = splitRegion(
            mask = mask,
            width = width,
            height = height,
            region = IntRect(splitAt, bounds.top, bounds.right, bounds.bottom),
            depth = depth + 1,
        )
        return mergeRegions(left + right)
    }

    private fun contentBoundsIn(
        mask: BooleanArray,
        width: Int,
        height: Int,
        region: IntRect,
    ): IntRect? {
        if (region.width <= 0 || region.height <= 0) return null
        val rowThreshold = max(1, (region.width * 0.003f).roundToInt())
        val columnThreshold = max(1, (region.height * 0.003f).roundToInt())

        var top = -1
        var bottom = -1
        for (y in region.top until region.bottom) {
            var count = 0
            val rowStart = y * width
            for (x in region.left until region.right) {
                if (mask[rowStart + x]) count++
            }
            if (count >= rowThreshold) {
                if (top < 0) top = y
                bottom = y + 1
            }
        }
        if (top < 0) return null

        var left = -1
        var right = -1
        for (x in region.left until region.right) {
            var count = 0
            for (y in top until bottom) {
                if (mask[y * width + x]) count++
            }
            if (count >= columnThreshold) {
                if (left < 0) left = x
                right = x + 1
            }
        }
        if (left < 0 || right <= left) return null
        return IntRect(left, top, right, bottom)
    }

    private fun findGutters(
        mask: BooleanArray,
        width: Int,
        height: Int,
        region: IntRect,
        horizontal: Boolean,
    ): List<Gutter> {
        val dimension = if (horizontal) region.height else region.width
        val otherDimension = if (horizontal) region.width else region.height
        if (dimension < 4 || otherDimension < 4) return emptyList()

        val projection = IntArray(dimension)
        if (horizontal) {
            for (y in region.top until region.bottom) {
                val rowStart = y * width
                var count = 0
                for (x in region.left until region.right) {
                    if (mask[rowStart + x]) count++
                }
                projection[y - region.top] = count
            }
        } else {
            for (x in region.left until region.right) {
                var count = 0
                for (y in region.top until region.bottom) {
                    if (mask[y * width + x]) count++
                }
                projection[x - region.left] = count
            }
        }

        // M5-D：真实页的 gutter 不一定完全干净，允许少量残影/噪点。
        val gutterThreshold = max(1, (otherDimension * 0.004f).roundToInt())
        val minRun = max(2, (dimension * 0.006f).roundToInt())
        val result = ArrayList<Gutter>()
        var runStart = -1

        fun addRun(start: Int, endInclusive: Int) {
            if (start <= 0 || endInclusive >= dimension - 1) return
            val length = endInclusive - start + 1
            if (length < minRun) return
            val center = start + length / 2
            val positionScore = if (dimension <= 2) {
                1f
            } else {
                1f - abs(center - dimension / 2f) / (dimension / 2f)
            }
            val score = (length / dimension.toFloat()) * (0.65f + 0.35f * positionScore)
            result += Gutter(
                start = start + (if (horizontal) region.top else region.left),
                endInclusive = endInclusive + (if (horizontal) region.top else region.left),
                score = score,
            )
        }

        for (index in 0 until dimension) {
            val isGutter = projection[index] <= gutterThreshold
            if (isGutter && runStart < 0) {
                runStart = index
            } else if (!isGutter && runStart >= 0) {
                addRun(runStart, index - 1)
                runStart = -1
            }
        }
        if (runStart >= 0) addRun(runStart, dimension - 1)
        return result
    }

    private fun mergeRegions(regions: List<IntRect>): List<IntRect> = regions.filter { it.width > 0 && it.height > 0 }

    private fun normalizedFrame(
        region: IntRect,
        width: Int,
        height: Int,
    ): Frame? {
        if (width <= 0 || height <= 0) return null
        val left = region.left / width.toFloat()
        val top = region.top / height.toFloat()
        val right = region.right / width.toFloat()
        val bottom = region.bottom / height.toFloat()
        if (right - left <= 0.01f || bottom - top <= 0.01f) return null
        return Frame(
            left = left.coerceIn(0f, 1f),
            top = top.coerceIn(0f, 1f),
            right = right.coerceIn(0f, 1f),
            bottom = bottom.coerceIn(0f, 1f),
        )
    }

    private fun scoreLayout(frames: List<Frame>, width: Int, height: Int): Float {
        if (frames.isEmpty()) return 0f
        val areas = frames.map { it.width * it.height }
        val totalArea = areas.sum()
        val meanArea = totalArea / frames.size

        val countScore = when (frames.size) {
            in 2..9 -> 1f
            in 10..MAX_FRAMES -> 0.70f
            else -> 0f
        }

        val minPageSide = min(width, height).toFloat()
        val sizeScore = frames.map { frame ->
            val pixelWidth = frame.width * width
            val pixelHeight = frame.height * height
            min(pixelWidth, pixelHeight) / minPageSide
        }.average().toFloat().let { (it / 0.18f).coerceIn(0f, 1f) }

        val aspectScore = frames.map { frame ->
            val aspect = (frame.width * width) / max(1f, frame.height * height)
            if (aspect in 0.25f..4f) 1f else 0f
        }.average().toFloat()

        val variance = areas.map { area ->
            val delta = area - meanArea
            delta * delta
        }.average().toFloat()
        val balanceScore = if (meanArea > 0f) {
            (1f - sqrt(variance) / meanArea).coerceIn(0f, 1f)
        } else {
            0f
        }

        val coverageScore = if (totalArea in 0.18f..0.98f) 1f else 0f
        return 0.30f * countScore +
            0.20f * sizeScore +
            0.20f * aspectScore +
            0.15f * balanceScore +
            0.15f * coverageScore
    }

    private fun wholePage(
        width: Int,
        height: Int,
        analysisWidth: Int,
        analysisHeight: Int,
        elapsedMs: Long,
        reason: FallbackReason?,
        confidence: Float = 1f,
    ): Layout = Layout(
        status = LayoutStatus.WholePage,
        frames = if (width > 0 && height > 0) {
            listOf(Frame(left = 0f, top = 0f, right = 1f, bottom = 1f))
        } else {
            emptyList()
        },
        confidence = confidence,
        reason = reason,
        sourceWidth = width,
        sourceHeight = height,
        analysisWidth = analysisWidth,
        analysisHeight = analysisHeight,
        elapsedMs = elapsedMs,
    )

    private fun elapsed(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}
