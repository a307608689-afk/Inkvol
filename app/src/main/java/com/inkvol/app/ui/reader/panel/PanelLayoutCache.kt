package com.inkvol.app.ui.reader.panel

import android.content.Context
import com.inkvol.app.util.Fingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * M5-C：分格布局磁盘缓存。
 *
 * 只缓存矩形与置信度，不缓存裁剪后的位图。缓存 key 包含书 ID、单页内容指纹、
 * 裁边 / 修复 / 阅读方向 / 分格档位，因此任何会影响检测结果的输入变化都会失效。
 */
object PanelLayoutCache {

    private const val CACHE_VERSION = "panel-cache-v1"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class CachedFrame(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    @Serializable
    private data class CachedLayout(
        val version: String,
        val status: String,
        val frames: List<CachedFrame>,
        val confidence: Float,
        val reason: String?,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val analysisWidth: Int,
        val analysisHeight: Int,
    )

    data class Key(
        val bookId: String,
        val pageIndex: Int,
        val pageFingerprint: String,
        val trimMargins: Boolean,
        val repair: Int,
        val panelMode: String,
        val rtl: Boolean,
    )

    suspend fun load(context: Context, key: Key): PanelDetector.Layout? = withContext(Dispatchers.IO) {
        runCatching {
            val file = cacheFile(context, key)
            if (!file.exists() || file.length() <= 0L) return@runCatching null
            val cached = json.decodeFromString<CachedLayout>(file.readText())
            if (cached.version != CACHE_VERSION) return@runCatching null
            val status = when (cached.status) {
                "Panelized" -> PanelDetector.LayoutStatus.Panelized
                else -> PanelDetector.LayoutStatus.WholePage
            }
            val reason = cached.reason?.let { reason ->
                runCatching { PanelDetector.FallbackReason.valueOf(reason) }.getOrNull()
            }
            PanelDetector.Layout(
                status = status,
                frames = cached.frames.map {
                    PanelDetector.Frame(it.left, it.top, it.right, it.bottom)
                },
                confidence = cached.confidence,
                reason = reason,
                sourceWidth = cached.sourceWidth,
                sourceHeight = cached.sourceHeight,
                analysisWidth = cached.analysisWidth,
                analysisHeight = cached.analysisHeight,
                elapsedMs = 0L,
            )
        }.getOrNull()
    }

    suspend fun save(context: Context, key: Key, layout: PanelDetector.Layout): Unit =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = CachedLayout(
                    version = CACHE_VERSION,
                    status = layout.status.name,
                    frames = layout.frames.map {
                        CachedFrame(it.left, it.top, it.right, it.bottom)
                    },
                    confidence = layout.confidence,
                    reason = layout.reason?.name,
                    sourceWidth = layout.sourceWidth,
                    sourceHeight = layout.sourceHeight,
                    analysisWidth = layout.analysisWidth,
                    analysisHeight = layout.analysisHeight,
                )
                val file = cacheFile(context, key)
                val temp = File(file.parentFile, file.name + ".tmp")
                FileOutputStream(temp).use { output ->
                    output.write(
                        json.encodeToString(CachedLayout.serializer(), payload).toByteArray(),
                    )
                }
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
            }.getOrDefault(Unit)
        }

    /** 建议在设置清理 / 删书时调用；目前 ReaderScreen 只按书维护内存缓存。 */
    fun clear(context: Context, bookId: String): Int {
        val dir = File(context.filesDir, "panel_cache")
        return dir.listFiles()?.count { file ->
            file.isFile && file.name.startsWith(hashPrefix(bookId)) && file.delete()
        } ?: 0
    }

    /** 设置页「清理分格缓存」：清空全部布局缓存。 */
    suspend fun clearAll(context: Context): Int = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "panel_cache")
        dir.listFiles()?.count { file -> file.isFile && file.delete() } ?: 0
    }

    /** 设置页展示当前缓存条数。 */
    suspend fun count(context: Context): Int = withContext(Dispatchers.IO) {
        File(context.filesDir, "panel_cache").listFiles()?.count { it.isFile } ?: 0
    }

    private fun cacheFile(context: Context, key: Key): File {
        val dir = File(context.filesDir, "panel_cache").apply { mkdirs() }
        return File(dir, "${hashPrefix(key.bookId)}-${cacheKey(key)}.json")
    }

    private fun hashPrefix(bookId: String): String =
        Fingerprint.ofBytes("book|$bookId".toByteArray()).take(16)

    private fun cacheKey(key: Key): String = Fingerprint.ofBytes(
        listOf(
            CACHE_VERSION,
            key.bookId,
            key.pageIndex.toString(),
            key.pageFingerprint,
            key.trimMargins.toString(),
            key.repair.toString(),
            key.panelMode,
            key.rtl.toString(),
        ).joinToString("|").toByteArray(),
    )
}
