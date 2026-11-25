package com.inkvol.app.data.cleanup

import android.content.Context
import java.io.File

/** 一项可清理的缓存（bytes/count 为扫描快照）。 */
data class CleanupItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val bytes: Long,
    val count: Int,
    /** 深度项：默认不勾选，需要用户二次确认；只删可重建/无主数据。 */
    val deep: Boolean,
)

/**
 * 空间清理单一真源。
 *
 * 只允许删除：
 * - cacheDir/import：归档抽取临时副本，按需重建（LibraryScanner/BookSourceFactory）；
 * - cacheDir/reader_share：单页分享临时图；
 * - filesDir/panel_cache：分格检测缓存，下次阅读重新检测；
 * - filesDir/crash：崩溃日志（用户主动清理）；
 * - covers/novel_covers 中没有对应书籍的孤儿封面。
 *
 * **不删除** filesDir/novel_text 与 filesDir/epub_images：它们是正文/插图来源，
 * 缺失时阅读器不会自动重建（readChapterBytes 直接返回空）。
 */
object CacheCleaner {

    const val ID_IMPORT = "import_cache"
    const val ID_SHARE = "share_cache"
    const val ID_PANEL = "panel_cache"
    const val ID_CRASH = "crash_logs"
    const val ID_ORPHAN_COVERS = "orphan_covers"

    private val safeIds = setOf(ID_IMPORT, ID_SHARE, ID_PANEL)

    fun isSafe(id: String): Boolean = id in safeIds

    fun scan(context: Context, validBookIds: Set<String>): List<CleanupItem> = listOf(
        CleanupItem(
            id = ID_IMPORT,
            title = "导入缓存",
            subtitle = "归档抽取的临时副本，再次打开会自动重建",
            bytes = sizeOfFiles(File(context.cacheDir, "import")),
            count = countOfFiles(File(context.cacheDir, "import")),
            deep = false,
        ),
        CleanupItem(
            id = ID_SHARE,
            title = "分享临时图",
            subtitle = "分享阅读页时生成，最多保留一张",
            bytes = sizeOfFiles(File(context.cacheDir, "reader_share")),
            count = countOfFiles(File(context.cacheDir, "reader_share")),
            deep = false,
        ),
        CleanupItem(
            id = ID_PANEL,
            title = "分格检测缓存",
            subtitle = "下次打开重新检测（大图首开会略慢）",
            bytes = sizeOfFiles(File(context.filesDir, "panel_cache")),
            count = countOfFiles(File(context.filesDir, "panel_cache")),
            deep = false,
        ),
        CleanupItem(
            id = ID_CRASH,
            title = "崩溃日志",
            subtitle = "闪退记录；清空后将无法事后排查",
            bytes = sizeOfFiles(File(context.filesDir, "crash")),
            count = countOfFiles(File(context.filesDir, "crash")),
            deep = true,
        ),
        CleanupItem(
            id = ID_ORPHAN_COVERS,
            title = "孤儿封面",
            subtitle = "书已删除但封面文件残留",
            bytes = orphanCovers(context, validBookIds).sumOf { it.length() },
            count = orphanCovers(context, validBookIds).size,
            deep = true,
        ),
    )

    /** 执行清理，返回实际释放的字节数。 */
    fun clear(context: Context, ids: Set<String>, validBookIds: Set<String>): Long {
        var freed = 0L
        if (ID_IMPORT in ids) {
            freed += deleteFiles(File(context.cacheDir, "import"))
        }
        if (ID_SHARE in ids) {
            freed += deleteFiles(File(context.cacheDir, "reader_share"))
        }
        if (ID_PANEL in ids) {
            freed += deleteFiles(File(context.filesDir, "panel_cache"))
        }
        if (ID_CRASH in ids) {
            freed += deleteFiles(File(context.filesDir, "crash"))
        }
        if (ID_ORPHAN_COVERS in ids) {
            orphanCovers(context, validBookIds).forEach { file ->
                val length = file.length()
                if (file.delete()) freed += length
            }
        }
        return freed
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024f / 1024f)
        bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024f)
        else -> "$bytes B"
    }

    private fun orphanCovers(context: Context, validBookIds: Set<String>): List<File> {
        if (validBookIds.isEmpty()) return emptyList()
        val dirs = listOf(
            File(context.filesDir, "covers") to "jpg",
            File(context.filesDir, "novel_covers") to "png",
        )
        return dirs.flatMap { (dir, ext) ->
            dir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".$ext") }
                ?.filter { it.name.substringBeforeLast('.') !in validBookIds }
                ?: emptyList()
        }
    }

    private fun deleteFiles(dir: File): Long {
        val files = dir.listFiles() ?: return 0L
        var freed = 0L
        files.forEach { file ->
            val length = file.recursiveLength()
            if (file.deleteRecursively()) freed += length
        }
        return freed
    }

    private fun sizeOfFiles(dir: File): Long =
        dir.listFiles()?.sumOf { it.recursiveLength() } ?: 0L

    private fun countOfFiles(dir: File): Int =
        dir.listFiles()?.sumOf { it.recursiveCount() } ?: 0

    private fun File.recursiveLength(): Long =
        if (isFile) length() else (listFiles() ?: emptyArray()).sumOf { it.recursiveLength() }

    private fun File.recursiveCount(): Int =
        if (isFile) 1 else (listFiles() ?: emptyArray()).sumOf { it.recursiveCount() }
}
