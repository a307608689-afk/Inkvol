package com.inkvol.app.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.Log
import android.util.LruCache
import com.inkvol.app.data.source.BitmapPageSource
import com.inkvol.app.data.source.BookContentSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * 阅读器页面解码：
 * - 以屏幕尺寸计算 inSampleSize / 直通渲染尺寸；
 * - Bitmap LRU（按字节，占最大内存 1/6）；
 * - 同一页同一时刻只渲染一次（单飞），PDF 的原始渲染另有一份 raw 缓存给裁边/修复变体复用；
 * - 预取当前页 ±2，翻回已解码页零等待；预取有静默门且可被可见页请求中止；
 * - R2 裁边；
 * - R3 高倍放大时重渲染原始页面区域；
 * - R7 扫描件修复（轻/强两档）。
 */
class PageLoader(
    val source: BookContentSource,
    targetWidth: Int,
    targetHeight: Int,
) : Closeable {

    @Volatile
    private var targetWidthPx = targetWidth.coerceAtLeast(1)

    @Volatile
    private var targetHeightPx = targetHeight.coerceAtLeast(1)

    /** 当前渲染目标宽度（px）：可见页、预取、缩略图都从这里取。 */
    val targetWidth: Int get() = targetWidthPx

    val targetHeight: Int get() = targetHeightPx

    /** 目标尺寸代数：旋转/分屏变化会 +1，旧尺寸的在途结果不再写回缓存。 */
    @Volatile
    private var sizeGeneration = 0

    /**
     * 「可见页」请求代数：预取每渲染一页前对比，用户翻页后立即中止剩余预取，
     * 避免预取把 PdfRenderer 的串行锁占满、拖慢正要看的那一页。
     */
    private val displayEpoch = AtomicInteger(0)

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

    /**
     * 直通渲染（PDF）的原始位图：裁边/扫描件修复等变体共用同一次渲染结果，
     * 否则同一页会因为 cache key 不同而被渲染两遍。
     */
    private val rawCache = object : LruCache<String, Bitmap>(maxRawBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** 在途渲染表：同一 key 只让第一个请求真正渲染，其余 await 同一结果。 */
    private val inflight = HashMap<String, CompletableDeferred<Bitmap?>>()
    private val inflightLock = Any()

    /** 关闭后仍可能有在途协程（预取/缩略图）读 source.pageCount，这里统一兜底为 0。 */
    @Volatile
    private var closed = false

    private fun pageCountSafe(): Int =
        if (closed) 0 else runCatching { source.pageCount }.getOrDefault(0)

    /**
     * 窗口尺寸变化（旋转 / 分屏 / 折叠展开）时更新渲染目标。
     * 缓存是按旧尺寸生成的，直接清空——否则新方向下会一直显示按旧宽度渲染的字糊页。
     */
    fun updateTargetSize(width: Int, height: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (w == targetWidthPx && h == targetHeightPx) return
        targetWidthPx = w
        targetHeightPx = h
        sizeGeneration++
        cache.evictAll()
        zoomCache.evictAll()
        rawCache.evictAll()
        Log.d(TAG, "targetSize=${w}x$h gen=$sizeGeneration")
    }

    suspend fun load(
        index: Int,
        trimMargins: Boolean = false,
        repair: Int = 0,
    ): Bitmap? {
        // 可见页请求优先级最高：通知预取立即停下（预取会白占串行渲染锁）。
        displayEpoch.incrementAndGet()
        val key = cacheKey(index, trimMargins, repair)
        cache.get(key)?.let { return it }
        val gen = sizeGeneration
        return withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decode(index)
                bitmap
                    ?.let { if (repair > 0) repairScan(it, repair) else it }
                    ?.let { if (trimMargins) trimMargins(it) else it }
            }.onFailure {
                Log.w(TAG, "load($index) failed", it)
            }.getOrNull()?.also { bitmap ->
                // 解码期间尺寸变了（旋转）：按旧尺寸出的图不写回，避免后续一直命中糊图。
                if (gen == sizeGeneration) cache.put(key, bitmap)
            }
        }
    }

    /**
     * R3：放大 >1.5x 时重渲染页面，避免低分辨率位图放大发虚。
     */
    suspend fun loadHighRes(
        index: Int,
        trimMargins: Boolean = false,
        repair: Int = 0,
        targetScale: Float = 2f,
    ): Bitmap? {
        displayEpoch.incrementAndGet()
        val key = "zoom|$index|$trimMargins|$repair|${targetScale.roundToInt()}"
        zoomCache.get(key)?.let { return it }
        val gen = sizeGeneration
        return withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeHighRes(index, targetScale)
                bitmap
                    ?.let { if (repair > 0) repairScan(it, repair) else it }
                    ?.let { if (trimMargins) trimMargins(it) else it }
            }.getOrNull()?.also { bitmap ->
                if (gen == sizeGeneration) zoomCache.put(key, bitmap)
            }
        }
    }

    suspend fun loadThumb(index: Int): Bitmap? {
        thumbCache.get(index)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { decode(index, THUMB_WIDTH, THUMB_HEIGHT) }
                .onSuccess { bitmap -> if (bitmap != null) thumbCache.put(index, bitmap) }
                .getOrNull()
        }
    }

    suspend fun prefetch(
        indices: List<Int>,
        repair: Int = 0,
        trimMargins: Boolean = false,
    ) {
        if (indices.isEmpty()) return
        // 静默门：连续翻页时后一次 LaunchedEffect 会取消前一次，快速翻页期间预取不启动。
        delay(PREFETCH_SETTLE_MS)
        val epoch = displayEpoch.get()
        val count = pageCountSafe()
        for (index in indices) {
            if (index < 0 || index >= count) continue
            // 用户已经翻页 / 已经请求了可见页：剩余预取整体作废。
            if (displayEpoch.get() != epoch) return
            // 与可见页同 key（含裁边/修复设置），预取落地后翻过去直接命中。
            val key = cacheKey(index, trimMargins, repair)
            if (cache.get(key) != null) continue
            withContext(Dispatchers.IO) {
                runCatching {
                    decode(index)
                        ?.let { if (repair > 0) repairScan(it, repair) else it }
                        ?.let { if (trimMargins) trimMargins(it) else it }
                }.getOrNull()?.let { cache.put(key, it) }
            }
        }
    }

    private suspend fun decode(
        index: Int,
        widthHint: Int = targetWidthPx,
        heightHint: Int = targetHeightPx,
    ): Bitmap? {
        if (index !in 0 until pageCountSafe()) return null
        val wantWidth = widthHint.coerceAtLeast(1)
        val wantHeight = heightHint.coerceAtLeast(1)

        (source as? BitmapPageSource)?.let { bitmapSource ->
            // A：能直接给位图的来源（PDF）跳过「渲染成图片流 → BitmapFactory 解码」。
            return renderOnce(rawKey(index, wantWidth, wantHeight), cacheRaw = true) {
                val startedAt = System.currentTimeMillis()
                val bitmap = runCatching {
                    bitmapSource.renderPage(index, wantWidth, wantHeight)
                }.getOrNull()
                Log.d(
                    TAG,
                    "render page=$index target=${wantWidth}x$wantHeight " +
                        "size=${bitmap?.width}x${bitmap?.height} " +
                        "took=${System.currentTimeMillis() - startedAt}ms",
                )
                bitmap
            }
        }

        // 原有流式路径（ZIP/CBZ/7z/RAR/文件夹）：逻辑保持不变。
        // PDF 等来源可直接给出尺寸，避免为了取 bounds 先渲染一次（大 PDF 会因此 OOM）
        val known = runCatching { source.pageBounds(index) }.getOrNull()
        val srcWidth: Int
        val srcHeight: Int
        if (known != null && known.first > 0 && known.second > 0) {
            srcWidth = known.first
            srcHeight = known.second
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            source.openPage(index).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            srcWidth = bounds.outWidth
            srcHeight = bounds.outHeight
        }

        var sample = 1
        while (srcWidth / (sample * 2) >= wantWidth &&
            srcHeight / (sample * 2) >= wantHeight
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return renderOnce("stream|$index|$wantWidth|$wantHeight", cacheRaw = false) {
            source.openPage(index).use { BitmapFactory.decodeStream(it, null, options) }
        }
    }

    private suspend fun decodeHighRes(index: Int, targetScale: Float): Bitmap? {
        if (index !in 0 until pageCountSafe()) return null

        (source as? BitmapPageSource)?.let { bitmapSource ->
            // PDF 是矢量：直接按放大后的目标重渲染，比把普通位图当原图裁切清晰得多。
            val wantWidth = (targetWidthPx * targetScale).roundToInt().coerceAtLeast(1)
            val wantHeight = (targetHeightPx * targetScale).roundToInt().coerceAtLeast(1)
            return renderOnce(rawKey(index, wantWidth, wantHeight, zoom = true), cacheRaw = false) {
                val startedAt = System.currentTimeMillis()
                val bitmap = runCatching {
                    bitmapSource.renderPage(index, wantWidth, wantHeight)
                }.getOrNull()
                Log.d(
                    TAG,
                    "renderHiRes page=$index target=${wantWidth}x$wantHeight " +
                        "size=${bitmap?.width}x${bitmap?.height} " +
                        "took=${System.currentTimeMillis() - startedAt}ms",
                )
                bitmap
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.openPage(index).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val desiredWidth = (targetWidthPx * targetScale)
            .roundToInt()
            .coerceIn(1, bounds.outWidth)
        val desiredHeight = (targetHeightPx * targetScale)
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
        return renderOnce("streamHi|$index|$desiredWidth|$desiredHeight", cacheRaw = false) {
            source.openPage(index).use { stream ->
                val decoder = BitmapRegionDecoder.newInstance(stream, false)
                    ?: return@renderOnce null
                try {
                    decoder.decodeRegion(rect, options)
                } finally {
                    decoder.recycle()
                }
            }
        }
    }

    /**
     * 同一 key 同一时刻只渲染一次。
     * - owner 负责真正渲染，[cacheRaw] 时把原始结果写进 [rawCache]，其它请求 await 同一结果；
     * - owner 被取消 / 失败时，等待中的请求最多重试一次自己接管渲染，
     *   避免可见页拿到 null 永久停在转圈。
     */
    private suspend fun renderOnce(
        key: String,
        cacheRaw: Boolean,
        render: () -> Bitmap?,
    ): Bitmap? {
        repeat(2) {
            if (cacheRaw) rawCache.get(key)?.let { return it }
            var owned = false
            val deferred = synchronized(inflightLock) {
                val existing = inflight[key]
                if (existing != null && !existing.isCompleted) {
                    existing
                } else {
                    CompletableDeferred<Bitmap?>().also {
                        inflight[key] = it
                        owned = true
                    }
                }
            }
            if (!owned) {
                val shared = runCatching { deferred.await() }.getOrNull()
                if (shared != null) return shared
                return@repeat
            }
            try {
                val bitmap = render()
                if (bitmap != null && cacheRaw) rawCache.put(key, bitmap)
                deferred.complete(bitmap)
                return bitmap
            } catch (error: Throwable) {
                deferred.completeExceptionally(error)
                throw error
            } finally {
                synchronized(inflightLock) {
                    if (inflight[key] === deferred) inflight.remove(key)
                }
            }
        }
        return null
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

    private fun rawKey(index: Int, width: Int, height: Int, zoom: Boolean = false): String =
        if (zoom) "raw|z|$index|$width|$height" else "raw|$index|$width|$height"

    override fun close() {
        closed = true
        cache.evictAll()
        zoomCache.evictAll()
        thumbCache.evictAll()
        rawCache.evictAll()
        runCatching { source.close() }
    }

    /** 供组合期读取：关闭后恒为 0，不再触碰已释放的底层 source。 */
    val pageCount: Int get() = pageCountSafe()

    private fun maxCacheBytes(): Int {
        val max = Runtime.getRuntime().maxMemory()
        return (max / 6).coerceAtMost(96L * 1024 * 1024).toInt()
    }

    private fun maxHighResBytes(): Int {
        val max = Runtime.getRuntime().maxMemory()
        return (max / 4).coerceAtMost(64L * 1024 * 1024).toInt()
    }

    private fun maxRawBytes(): Int {
        val max = Runtime.getRuntime().maxMemory()
        return (max / 8).coerceAtMost(48L * 1024 * 1024).toInt()
    }

    private companion object {
        const val TAG = "InkvolPage"

        /** 预取静默门：页面停住这么久才开预取（快速翻页期间永不启动）。 */
        const val PREFETCH_SETTLE_MS = 150L

        const val THUMB_WIDTH = 220
        const val THUMB_HEIGHT = 300
    }
}
