package com.inkvol.app.data.import

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.inkvol.app.data.local.InkvolDatabase
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.source.SourceRules
import com.inkvol.app.util.Fingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 单文件导入（SAF OpenDocument）：
 * - 引用式：book_locations.doc_id 保存完整 document URI，不复制用户文件；
 * - 探针需要随机访问，所以只在 cacheDir 放一份临时副本用于页数/封面，导入后可被 LRU 清理；
 * - 统一归入「单文件导入」虚拟库，排在「未分类」里。
 */
class SingleFileImporter(
    private val context: Context,
    private val db: InkvolDatabase,
) {

    class AlreadyExistsException : Exception("这本书已经在书架里了")
    class UnsupportedException(message: String) : Exception(message)

    suspend fun import(uri: Uri): BookRecord = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "未命名"
        val size = querySize(uri)
        if (size <= 0) throw UnsupportedException("无法读取文件大小")

        val fingerprint = Fingerprint.ofArchive(size) {
            resolver.openInputStream(uri) ?: throw UnsupportedException("无法打开文件")
        }
        if (db.hasBook(fingerprint)) throw AlreadyExistsException()

        val cacheDir = File(context.cacheDir, "import").apply { mkdirs() }
        val ext = displayName.substringAfterLast('.', "bin")
        val local = File(cacheDir, "$fingerprint.$ext")
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(local).use { output -> input.copyTo(output) }
        } ?: throw UnsupportedException("无法读取文件")

        val libraryId = db.findOrCreateLibrary(SINGLE_LIBRARY_URI, "单文件导入")
        val folderId = db.findOrCreateFolder(libraryId, SINGLE_LIBRARY_URI, "单文件导入")
        val title = displayName.substringBeforeLast('.')

        if (SourceRules.isNovelName(displayName)) {
            val isEpub = SourceRules.extensionOf(displayName) == "epub"
            val parsedChapters: List<com.inkvol.app.data.model.NovelChapterRecord>
            val parsedCoverPath: String?
            if (isEpub) {
                val parsed = EpubParser.parse(context, fingerprint, local)
                parsedChapters = parsed.chapters
                parsedCoverPath = parsed.coverPath
            } else {
                val parsed = resolver.openInputStream(uri)?.use { input ->
                    TxtNovelParser.parse(context, fingerprint, input)
                } ?: throw UnsupportedException("无法读取 TXT")
                parsedChapters = parsed.chapters
                parsedCoverPath = null
            }
            val format = if (isEpub) "EPUB" else "TXT"
            val coverPath = parsedCoverPath
                ?: NovelCoverGenerator.save(context, fingerprint, title, parsedChapters.size)
            val book = BookRecord(
                id = fingerprint,
                type = "NOVEL",
                title = title,
                series = "单文件导入",
                volume = null,
                writer = null,
                tags = null,
                pageCount = parsedChapters.size,
                format = format,
                fileSize = size,
                coverPath = coverPath,
                addedAt = System.currentTimeMillis(),
                docId = uri.toString(),
                displayPath = displayName,
            )
            db.upsertBook(book, folderId)
            db.replaceNovelChapters(fingerprint, parsedChapters)
            return@withContext book
        }

        val probe = ArchiveProber(context).probe(local, fingerprint)
            ?: throw UnsupportedException("暂不支持这种格式")

        val book = BookRecord(
            id = fingerprint,
            type = "COMIC",
            title = probe.meta?.title ?: title,
            series = probe.meta?.series ?: "单文件导入",
            volume = probe.meta?.volume,
            writer = probe.meta?.writer,
            tags = probe.meta?.tags,
            pageCount = probe.pageCount,
            format = probe.format,
            fileSize = size,
            coverPath = probe.coverPath,
            addedAt = System.currentTimeMillis(),
            docId = uri.toString(),
            displayPath = displayName,
        )
        db.upsertBook(book, folderId)
        return@withContext book
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun querySize(uri: Uri): Long = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L
    }.getOrDefault(0L)

    companion object {
        const val SINGLE_LIBRARY_URI = "inkvol://single-file"
    }
}
