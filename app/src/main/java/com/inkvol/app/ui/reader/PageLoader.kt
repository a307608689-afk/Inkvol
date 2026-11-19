package com.inkvol.app.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.LruCache
import com.inkvol.app.data.source.BookContentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.roundToInt

/**
 * 阅读器页面解码：
 * - 以屏幕尺寸计算 inSampleSize；
 * - Bitmap LRU（按字节，占最大内存 1/6）；
 * - 预取当前页 ±2，翻回已解码页零等待；
 * - R2 裁边；
 * - R3 高倍放大时按原始页面区域解码；
 * - R7 扫描件修复（轻/强两档）。
 */
class PageLoader(
    val source: BookContentSource,
    private val targetWidth: Int,
    private val targetHeight: Int,
) : Closeable {

    private val cache = object : LruCache<String, Bitmap>(maxCacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val zoomCache = object : LruCache<String, Bitmap>(maxHighResBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** 缩略图单独一份小缓存，避免把整页大图塞进缩略图网格。 */
    private val thumbCache = object : LruCache<Int, Bitmap>(96) {
        override fun sizeOf(key: Int, value: Bitmap): Int = 1
    }

    suspend fun load(
        index: Int,
        trimMargins: Boolean = false,
        repair: Int = 0,
    ): Bitmap? {
        val key = cacheKey(index, trimMargins, repair)
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decode(index)
                bitmap
                    ?.let { if (repair > 0) repairScan(it, repair) else it }
                    ?.let { if (trimMargins) trimMargins(it) else it }
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }

    /**
     * R3：放大 >1.5x 时用原始页面重新解码，避免低分辨率位图放大发虚。
     * 这里返回整页原始分辨率结果，配合可见区域解码已足够单页/双页使用。
     */
    suspend fun loadHighRes(
        index: Int,
        trimMargins: Boolean = false,
        repair: Int = 0,
        targetScale: Float = 2f,
    ): Bitmap? {
        val key = "zoom|$index|$trimMargins|$repair|${targetScale.roundToInt()}"
        zoomCache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                source.openPage(index).use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

                val desiredWidth = (targetWidth * targetScale)
                    .roundToInt()
                    .coerceIn(1, bounds.outWidth)
                val desiredHeight = (targetHeight * targetScale)
                    .roundToInt()
                    .coerceIn(1, bounds.outHeight)

                var sample = 1
                while (bounds.outWidth / (sample * 2) >= desiredWidth &&
                    bounds.outHeight / (sample * 2) >= desiredHeight
                ) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val rect = Rect(0, 0, bounds.outWidth, bounds.outHeight)
                val bitmap = source.openPage(index).use { stream ->
                    val decoder = BitmapRegionDecoder.newInstance(stream, false)
                        ?: return@runCatching null
                    try {
                        decoder.decodeRegion(rect, options)
                    } finally {
                        decoder.recycle()
                    }
                }
                bitmap
                    ?.let { if (repair > 0) repairScan(it, repair) else it }
                    ?.let { if (trimMargins) trimMargins(it) else it }
            }.getOrNull()?.also { zoomCache.put(key, it) }
        }
    }

    suspend fun loadThumb(index: Int): Bitmap? {
        thumbCache.get(index)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { decode(index, thumbWidth = 220, thumbHeight = 300) }
                .onSuccess { bitmap -> if (bitmap != null) thumbCache.put(index, bitmap) }
                .getOrNull()
        }
    }

    suspend fun prefetch(indices: List<Int>, repair: Int = 0) {
        indices
            .filter { it >= 0 && it < source.pageCount }
            .forEach { index ->
                val key = cacheKey(index, trimMargins = false, repair = repair)
                if (cache.get(key) == null) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            decode(index)?.let { if (repair > 0) repairScan(it, repair) else it }
                        }.getOrNull()?.let { cache.put(key, it) }
                    }
                }
            }
    }

    private fun decode(
        index: Int,
        thumbWidth: Int = targetWidth,
        thumbHeight: Int = targetHeight,
    ): Bitmap? {
        if (index !in 0 until source.pageCount) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.openPage(index).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= thumbWidth &&
            bounds.outHeight / (sample * 2) >= thumbHeight
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return source.openPage(index).use { BitmapFactory.decodeStream(it, null, options) }
    }

    /** R7：轻/强两档。做对比度、白点校正与去黄，不做复杂局部增强。 */
    private fun repairScan(source: Bitmap, strength: Int): Bitmap {
        val amount = when (strength) {
            2 -> 0.70f
            1 -> 0.38f
            else -> return source
        }
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)

        val contrast = 1f + 0.28f * amount
        val yellowCut = 0.16f * amount
        val blueLift = 0.16f * amount
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val alpha = (pixel ushr 24) and 0xff
            if (alpha == 0) continue

            var r = ((pixel shr 16) and 0xff).toFloat()
            var g = ((pixel shr 8) and 0xff).toFloat()
            var b = (pixel and 0xff).toFloat()

            r = ((r - 128f) * contrast + 128f).coerceIn(0f, 255f)
            g = ((g - 128f) * contrast + 128f).coerceIn(0f, 255f)
            b = ((b - 128f) * contrast + 128f).coerceIn(0f, 255f)

            r *= 1f - yellowCut
            b *= 1f + blueLift

            // 轻微白点校正：高亮区更接近白，避免灰黄底。
            val brightness = (r + g + b) / 3f
            if (brightness > 168f) {
                val t = ((brightness - 168f) / 87f).coerceIn(0f, 1f) * amount
                r += (255f - r) * t
                g += (255f - g) * t
                b += (255f - b) * t
            }

            pixels[i] = (alpha shl 24) or
                (r.roundToInt().coerceIn(0, 255) shl 16) or
                (g.roundToInt().coerceIn(0, 255) shl 8) or
                b.roundToInt().coerceIn(0, 255)
        }
        output.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return output
    }

    /** 裁掉页面四边的均匀留白；最多裁掉 20%，避免误裁正文。 */
    private fun trimMargins(source: Bitmap): Bitmap {
        if (source.width < 8 || source.height < 8) return source
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)

        var minX = source.width
        var minY = source.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until source.height) {
            val row = y * source.width
            for (x in 0 until source.width) {
                val pixel = pixels[row + x]
                val alpha = (pixel ushr 24) and 0xff
                if (alpha < 16) continue
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                if (r >= 238 && g >= 238 && b >= 238) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0 || maxY < 0) return source

        val maxTrimX = (source.width * 0.20f).toInt()
        val maxTrimY = (source.height * 0.20f).toInt()
        val left = minX.coerceAtMost(maxTrimX)
        val top = minY.coerceAtMost(maxTrimY)
        val width = ((maxX - minX + 1).coerceAtLeast(1))
            .coerceAtMost(source.width - left)
        val height = ((maxY - minY + 1).coerceAtLeast(1))
            .coerceAtMost(source.height - top)
        if (width == source.width && height == source.height) return source
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    private fun cacheKey(index: Int, trimMargins: Boolean, repair: Int): String =
        "page|$index|$trimMargins|$repair"

    override fun close() {
        cache.evictAll()
        zoomCache.evictAll()
        thumbCache.evictAll()
        runCatching { source.close() }
    }

    private fun maxCacheBytes(): Int {
        val max = Runtime.getRuntime().maxMemory()
        return (max / 6).coerceAtMost(96L * 1024 * 1024).toInt()
    }

    private fun maxHighResBytes(): Int {
        val max = Runtime.getRuntime().maxMemory()
        return (max / 4).coerceAtMost(64L * 1024 * 1024).toInt()
    }
}
