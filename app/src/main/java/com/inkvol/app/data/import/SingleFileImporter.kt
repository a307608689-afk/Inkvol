package com.inkvol.app.data.import

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.inkvol.app.data.local.InkvolDatabase
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.source.BookContentSource
import com.inkvol.app.data.source.EpubImageSource
import com.inkvol.app.data.source.MobiImageSource
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

    private fun comicSourceOf(format: String, local: File): BookContentSource =
        if (format == "EPUB") EpubImageSource(local) else MobiImageSource(local)

    suspend fun import(uri: Uri): BookRecord = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "未命名"
        val size = querySize(uri)
        if (size <= 0) throw UnsupportedException("无法读取文件大小")

        val fingerprint = Fingerprint.ofArchive(size) {
            resolver.openInputStream(uri) ?: throw UnsupportedException("无法打开文件")
        }
        // 回收站里的书不算「已存在」：允许重新导入，upsertBook 复用同一指纹行（deleted_at 为空）复活
        if (db.hasActiveBook(fingerprint)) throw AlreadyExistsException()

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
            val ext = SourceRules.extensionOf(displayName)
            val isEpub = ext == "epub"
            val isMobi = ext == "mobi"

            // EPUB/MOBI 漫画：与扫描器同一套探针（手动指定过类型的书不覆盖）
            if (isEpub || isMobi) {
                val comicFormat = if (isEpub) "EPUB" else "MOBI"
                val forced = db.isTypeOverridden(fingerprint)
                val forcedComic = forced && db.queryBook(fingerprint)?.type == "COMIC"
                val verdict = if (forced) {
                    null
                } else if (isEpub) {
                    NovelComicProbe.probeEpub(local)
                } else {
                    NovelComicProbe.probeMobi(local)
                }
                if (forcedComic || (verdict?.isComic == true && verdict.pageCount > 0)) {
                    val pages = verdict?.pageCount
                        ?: runCatching {
                            comicSourceOf(comicFormat, local).use { it.pageCount }
                        }.getOrDefault(0)
                    if (pages > 0) {
                        var sections: List<com.inkvol.app.data.source.BookSection> = emptyList()
                        val cover = runCatching {
                            comicSourceOf(comicFormat, local).use { source ->
                                sections = (source as? com.inkvol.app.data.source.SectionedContentSource)
                                    ?.listSections().orEmpty()
                                ArchiveProber(context)
                                    .saveCover(fingerprint) { index -> source.openPage(index) }
                            }
                        }.getOrNull()
                        val book = BookRecord(
                            id = fingerprint,
                            type = "COMIC",
                            title = title,
                            series = "单文件导入",
                            volume = null,
                            writer = null,
                            tags = null,
                            pageCount = pages,
                            format = comicFormat,
                            fileSize = size,
                            coverPath = cover,
                            addedAt = System.currentTimeMillis(),
                            docId = uri.toString(),
                            displayPath = displayName,
                        )
                        db.upsertBook(book, folderId)
                        // P3：章节落库（备份用）
                        runCatching {
                            db.replaceBookSections(
                                fingerprint,
                                sections.map {
                                    com.inkvol.app.data.model.BookSectionRecord(
                                        it.idx, it.title, it.pageStart,
                                    )
                                },
                            )
                        }
                        db.deleteNovelProgress(fingerprint)
                        return@withContext book
                    }
                }
            }

            val parsedChapters: List<com.inkvol.app.data.model.NovelChapterRecord>
            val parsedCoverPath: String?
            if (isEpub) {
                val parsed = EpubParser.parse(context, fingerprint, local)
                parsedChapters = parsed.chapters
                parsedCoverPath = parsed.coverPath
            } else if (isMobi) {
                val parsed = MobiParser.parse(context, fingerprint, local)
                parsedChapters = parsed.chapters
                parsedCoverPath = parsed.coverPath
            } else {
                val parsed = resolver.openInputStream(uri)?.use { input ->
                    TxtNovelParser.parse(context, fingerprint, input)
                } ?: throw UnsupportedException("无法读取 TXT")
                parsedChapters = parsed.chapters
                parsedCoverPath = null
            }
            val format = when {
                isEpub -> "EPUB"
                isMobi -> "MOBI"
                else -> "TXT"
            }
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
        // P3：章节落库（备份用；普通无章节归档 sections 为空 = 整表替换自清）
        runCatching {
            db.replaceBookSections(
                fingerprint,
                probe.sections.map {
                    com.inkvol.app.data.model.BookSectionRecord(it.idx, it.title, it.pageStart)
                },
            )
        }
        return@withContext book
    }

    /**
     * M4.5 文件同步下载导入：文件已由调用方持久化在 filesDir/library/（docId=file://）。
     * 指纹校验去重；书架已存在时抛 AlreadyExistsException 由调用方忽略。
     */
    suspend fun importLocalFile(file: File, displayName: String): BookRecord = withContext(Dispatchers.IO) {
        val size = file.length()
        if (size <= 0) throw UnsupportedException("文件为空")
        val fingerprint = Fingerprint.ofArchive(size) { file.inputStream() }
        // 回收站里的书不算「已存在」：允许重新导入，upsertBook 复用同一指纹行（deleted_at 为空）复活
        if (db.hasActiveBook(fingerprint)) throw AlreadyExistsException()

        val libraryId = db.findOrCreateLibrary(SINGLE_LIBRARY_URI, "单文件导入")
        val folderId = db.findOrCreateFolder(libraryId, SINGLE_LIBRARY_URI, "单文件导入")
        val title = displayName.substringBeforeLast('.')

        if (SourceRules.isNovelName(displayName)) {
            val ext = SourceRules.extensionOf(displayName)
            val isEpub = ext == "epub"
            val isMobi = ext == "mobi"

            // EPUB/MOBI 漫画：与扫描器同一套探针（手动指定过类型的书不覆盖）
            if (isEpub || isMobi) {
                val comicFormat = if (isEpub) "EPUB" else "MOBI"
                val forced = db.isTypeOverridden(fingerprint)
                val forcedComic = forced && db.queryBook(fingerprint)?.type == "COMIC"
                val verdict = if (forced) {
                    null
                } else if (isEpub) {
                    NovelComicProbe.probeEpub(file)
                } else {
                    NovelComicProbe.probeMobi(file)
                }
                if (forcedComic || (verdict?.isComic == true && verdict.pageCount > 0)) {
                    val pages = verdict?.pageCount
                        ?: runCatching {
                            comicSourceOf(comicFormat, file).use { it.pageCount }
                        }.getOrDefault(0)
                    if (pages > 0) {
                        var sections: List<com.inkvol.app.data.source.BookSection> = emptyList()
                        val cover = runCatching {
                            comicSourceOf(comicFormat, file).use { source ->
                                sections = (source as? com.inkvol.app.data.source.SectionedContentSource)
                                    ?.listSections().orEmpty()
                                ArchiveProber(context)
                                    .saveCover(fingerprint) { index -> source.openPage(index) }
                            }
                        }.getOrNull()
                        val book = BookRecord(
                            id = fingerprint,
                            type = "COMIC",
                            title = title,
                            series = "单文件导入",
                            volume = null,
                            writer = null,
                            tags = null,
                            pageCount = pages,
                            format = comicFormat,
                            fileSize = size,
                            coverPath = cover,
                            addedAt = System.currentTimeMillis(),
                            docId = Uri.fromFile(file).toString(),
                            displayPath = displayName,
                        )
                        db.upsertBook(book, folderId)
                        // P3：章节落库（备份用）
                        runCatching {
                            db.replaceBookSections(
                                fingerprint,
                                sections.map {
                                    com.inkvol.app.data.model.BookSectionRecord(
                                        it.idx, it.title, it.pageStart,
                                    )
                                },
                            )
                        }
                        db.deleteNovelProgress(fingerprint)
                        return@withContext book
                    }
                }
            }

            val parsedChapters: List<com.inkvol.app.data.model.NovelChapterRecord>
            val parsedCoverPath: String?
            if (isEpub) {
                val parsed = EpubParser.parse(context, fingerprint, file)
                parsedChapters = parsed.chapters
                parsedCoverPath = parsed.coverPath
            } else if (isMobi) {
                val parsed = MobiParser.parse(context, fingerprint, file)
                parsedChapters = parsed.chapters
                parsedCoverPath = parsed.coverPath
            } else {
                val parsed = TxtNovelParser.parse(context, fingerprint, file.inputStream())
                parsedChapters = parsed.chapters
                parsedCoverPath = null
            }
            val format = when {
                isEpub -> "EPUB"
                isMobi -> "MOBI"
                else -> "TXT"
            }
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
                docId = Uri.fromFile(file).toString(),
                displayPath = displayName,
            )
            db.upsertBook(book, folderId)
            db.replaceNovelChapters(fingerprint, parsedChapters)
            return@withContext book
        }

        val probe = ArchiveProber(context).probe(file, fingerprint)
            ?: throw UnsupportedException("暂不支持这种格式")

        val book = BookRecord(
            id = fingerprint,
            type = "COMIC",
            title = probe.meta?.title ?: title,
            series = "单文件导入",
            volume = probe.meta?.volume,
            writer = probe.meta?.writer,
            tags = probe.meta?.tags,
            pageCount = probe.pageCount,
            format = probe.format,
            fileSize = size,
            coverPath = probe.coverPath,
            addedAt = System.currentTimeMillis(),
            docId = Uri.fromFile(file).toString(),
            displayPath = displayName,
        )
        db.upsertBook(book, folderId)
        // P3：章节落库（备份用；普通无章节归档 sections 为空 = 整表替换自清）
        runCatching {
            db.replaceBookSections(
                fingerprint,
                probe.sections.map {
                    com.inkvol.app.data.model.BookSectionRecord(it.idx, it.title, it.pageStart)
                },
            )
        }
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
