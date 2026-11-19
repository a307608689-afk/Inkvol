package com.inkvol.app.data.import

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import com.inkvol.app.data.local.InkvolDatabase
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.source.BookContentSource
import com.inkvol.app.data.source.PdfSource
import com.inkvol.app.data.source.RarSource
import com.inkvol.app.data.source.SevenZSource
import com.inkvol.app.data.source.SourceRules
import com.inkvol.app.data.source.ZipSource
import com.inkvol.app.util.Fingerprint
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 托管文件夹扫描器。
 *
 * 关键约束（M0.5 实测）：目录列举走 DocumentsContract 批量 query，
 * 一次 Cursor 取全量元数据；禁止 DocumentFile 逐项访问。
 */
class LibraryScanner(
    private val context: Context,
    private val db: InkvolDatabase,
) {

    data class Result(val added: Int, val skipped: Int, val failed: Int)

    companion object {
        private const val MAX_ARCHIVE_CACHE_BYTES = 200L * 1024 * 1024
    }

    private val resolver: ContentResolver = context.contentResolver
    private var added = 0
    private var skipped = 0
    private var failed = 0
    private var processed = 0
    private var onProgress: (Int, String) -> Unit = { _, _ -> }
    private lateinit var treeUri: Uri
    private var folderId: Long = 0

    suspend fun scan(
        uri: Uri,
        libraryId: Long,
        folderId: Long,
        depthLimit: Int = 3,
        onProgress: (Int, String) -> Unit,
    ): Result {
        this.treeUri = uri
        this.folderId = folderId
        this.onProgress = onProgress
        added = 0
        skipped = 0
        failed = 0
        processed = 0

        val rootDocId = DocumentsContract.getTreeDocumentId(uri)
        val rootName = queryDisplayName(rootDocId) ?: "漫画库"
        val scanStart = System.currentTimeMillis()
        // 先探活：目录列举失败（权限失效/存储卸载）时跳过对账，避免整库被误清
        val accessible = isFolderAccessible(rootDocId)
        scanDirectory(rootDocId, rootName, parentSeries = null, depth = 0, depthLimit = depthLimit)
        pruneArchiveCache()
        db.markFolderScanned(folderId)
        if (accessible) {
            val covers = db.removeMissingLocations(folderId, scanStart)
            covers.forEach { path -> runCatching { File(path).delete() } }
        }
        Log.i(
            "InkvolScan",
            "scan done root=$rootName added=$added skipped=$skipped failed=$failed",
        )
        return Result(added, skipped, failed)
    }

    /** 探活：root 下能否成功 query（query 返回 null 视为不可访问）。 */
    private fun isFolderAccessible(rootDocId: String): Boolean = runCatching {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
        resolver.query(uri, arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null) != null
    }.getOrDefault(false)

    private fun scanDirectory(
        docId: String,
        displayName: String,
        parentSeries: String?,
        depth: Int,
        depthLimit: Int,
    ) {
        val children = listChildren(docId)
        val images = children.filter { !it.isDirectory && SourceRules.isImageName(it.name) }
        val archives = children.filter { !it.isDirectory && SourceRules.isArchiveName(it.name) }
        val novels = children.filter { !it.isDirectory && SourceRules.isNovelName(it.name) }
        val directories = children.filter { it.isDirectory }

        val series = parentSeries ?: displayName

        if (images.isNotEmpty()) {
            importFolderBook(displayName, series, docId, images)
        }
        archives.forEach { archive -> importArchive(archive, series) }
        novels.forEach { novel -> importNovel(novel, series) }

        if (depth < depthLimit) {
            directories.forEach { child ->
                scanDirectory(child.docId, child.name, displayName, depth + 1, depthLimit)
            }
        }
    }

    private fun importFolderBook(
        title: String,
        series: String,
        docId: String,
        images: List<DocEntry>,
    ) {
        try {
            val fingerprint = Fingerprint.ofFolder(images.map { it.name to it.size })
            if (db.hasBook(fingerprint)) {
                db.touchBookLocation(fingerprint, folderId)
                skipped++
                return
            }
            val cover = saveCover(fingerprint) { index ->
                val doc = images.sortedWith { a, b -> com.inkvol.app.util.NaturalOrder.compare(a.name, b.name) }[index]
                openDocument(doc.docId)
            }
            val book = BookRecord(
                id = fingerprint,
                type = "COMIC",
                title = title,
                series = series,
                volume = null,
                writer = null,
                tags = null,
                pageCount = images.size,
                format = "FOLDER",
                fileSize = images.sumOf { it.size },
                coverPath = cover,
                addedAt = System.currentTimeMillis(),
                docId = docId,
                displayPath = title,
            )
            db.upsertBook(book, folderId)
            added++
            processed++
            onProgress(processed, title)
        } catch (error: Throwable) {
            failed++
            Log.w("InkvolScan", "folder book failed: $title", error)
        }
    }

    private fun importArchive(entry: DocEntry, series: String) {
        try {
            val fingerprint = Fingerprint.ofArchive(entry.size) { openDocument(entry.docId) }
            if (db.hasBook(fingerprint)) {
                db.touchBookLocation(fingerprint, folderId)
                skipped++
                return
            }
            val local = copyToCache(entry, fingerprint)
            val head = ByteArray(8)
            val read = local.inputStream().use { it.read(head) }
            val kind = SourceRules.detect(head.copyOf(read.coerceAtLeast(0)))
            if (kind == null || kind !in setOf("ZIP", "RAR", "SEVEN_Z", "PDF")) {
                skipped++
                Log.i("InkvolScan", "unsupported archive ${entry.name} kind=$kind")
                return
            }

            val title = entry.name.substringBeforeLast('.')
            val probe = probeArchive(kind, local, fingerprint)
            if (probe.pageCount <= 0) {
                skipped++
                Log.w("InkvolScan", "empty or unreadable archive ${entry.name} kind=$kind")
                return
            }

            val book = BookRecord(
                id = fingerprint,
                type = "COMIC",
                title = probe.meta?.title ?: title,
                series = probe.meta?.series ?: series,
                volume = probe.meta?.volume,
                writer = probe.meta?.writer,
                tags = probe.meta?.tags,
                pageCount = probe.pageCount,
                format = probe.format,
                fileSize = entry.size,
                coverPath = probe.coverPath,
                addedAt = System.currentTimeMillis(),
                docId = entry.docId,
                displayPath = entry.name,
            )
            db.upsertBook(book, folderId)
            added++
            processed++
            onProgress(processed, title)
        } catch (error: Throwable) {
            failed++
            Log.w("InkvolScan", "archive failed: ${entry.name}", error)
        }
    }

    private fun importNovel(entry: DocEntry, series: String) {
        try {
            val fingerprint = Fingerprint.ofArchive(entry.size) { openDocument(entry.docId) }
            val title = entry.name.substringBeforeLast('.')
            val isEpub = SourceRules.extensionOf(entry.name) == "epub"
            // M6.3-C 净化：启用中的规则在解析时按行过滤（广告/水印/推广行不入副本）
            val purifyRegexes = db.queryPurifyRules(enabledOnly = true)
                .map { runCatching { Regex(it.pattern) }.getOrNull() }
                .filterNotNull()
            val parsedChapters: List<com.inkvol.app.data.model.NovelChapterRecord>
            val parsedCoverPath: String?
            if (isEpub) {
                // EPUB 需要 ZipFile 随机访问，先落一份缓存副本
                val local = copyToCache(entry, fingerprint)
                val parsed = EpubParser.parse(context, fingerprint, local, purifyRegexes)
                parsedChapters = parsed.chapters
                parsedCoverPath = parsed.coverPath
            } else {
                val parsed = openDocument(entry.docId).use {
                    TxtNovelParser.parse(context, fingerprint, it, purifyRegexes)
                }
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
                series = series,
                volume = null,
                writer = null,
                tags = null,
                pageCount = parsedChapters.size,
                format = format,
                fileSize = entry.size,
                coverPath = coverPath,
                addedAt = System.currentTimeMillis(),
                docId = entry.docId,
                displayPath = entry.name,
            )
            db.upsertBook(book, folderId)
            db.replaceNovelChapters(fingerprint, parsedChapters)
            if (book.addedAt > 0L && db.queryNovelChapterCount(fingerprint) == parsedChapters.size) {
                db.touchBookLocation(fingerprint, folderId)
            }
            added++
            processed++
            onProgress(processed, title)
        } catch (error: Throwable) {
            failed++
            Log.w("InkvolScan", "novel failed: ${entry.name}", error)
        }
    }

    private fun listChildren(parentDocId: String): List<DocEntry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )
        val result = ArrayList<DocEntry>()
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                result += DocEntry(
                    docId = cursor.getString(idIndex),
                    name = cursor.getString(nameIndex) ?: "",
                    isDirectory = cursor.getString(mimeIndex) == Document.MIME_TYPE_DIR,
                    size = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex),
                )
            }
        }
        return result
    }

    private fun queryDisplayName(docId: String): String? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        resolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private fun openDocument(docId: String): InputStream {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return resolver.openInputStream(uri) ?: error("cannot open $docId")
    }

    private fun copyToCache(entry: DocEntry, fingerprint: String): File {
        val dir = File(context.cacheDir, "import").apply { mkdirs() }
        val ext = SourceRules.extensionOf(entry.name).ifBlank { "bin" }
        val target = File(dir, "$fingerprint.$ext")
        if (target.exists() && target.length() == entry.size) return target
        openDocument(entry.docId).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return target
    }

    private data class Probe(
        val pageCount: Int,
        val format: String,
        val coverPath: String?,
        val meta: ComicInfoParser.Meta?,
    )

    private fun probeArchive(kind: String, file: File, fingerprint: String): Probe = when (kind) {
        "ZIP" -> openSourceProbe(ZipSource(file), "CBZ", fingerprint).copy(
            meta = runCatching {
                java.util.zip.ZipFile(file).use { ComicInfoParser.parseZip(it) }
            }.getOrNull(),
        )

        "RAR" -> openSourceProbe(RarSource(file), "CBR", fingerprint)
        "SEVEN_Z" -> openSourceProbe(SevenZSource(file), "CB7", fingerprint)
        "PDF" -> {
            val pages = runCatching { PdfSource(file).use { it.pageCount } }.getOrDefault(0)
            Probe(
                pageCount = pages,
                format = "PDF",
                coverPath = if (pages > 0) savePdfCover(fingerprint, file) else null,
                meta = null,
            )
        }

        else -> Probe(0, kind, null, null)
    }

    private fun openSourceProbe(
        source: BookContentSource,
        format: String,
        fingerprint: String,
    ): Probe = source.use {
        val count = it.pageCount
        Probe(
            pageCount = count,
            format = format,
            coverPath = if (count > 0) saveCover(fingerprint) { _ -> it.openPage(0) } else null,
            meta = null,
        )
    }

    /** PDF 首页渲染成封面（512px 宽），供书架展示。 */
    private fun savePdfCover(fingerprint: String, file: File): String? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount <= 0) {
                    null
                } else {
                    renderer.openPage(0).use { page ->
                        val width = 512
                        val height = (width * page.height.toFloat() / page.width)
                            .toInt()
                            .coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val dir = File(context.filesDir, "covers").apply { mkdirs() }
                        val out = File(dir, "$fingerprint.jpg")
                        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
                        bitmap.recycle()
                        out.absolutePath
                    }
                }
            }
        }
    }.getOrElse {
        Log.w("InkvolScan", "pdf cover failed $fingerprint", it)
        null
    }

    /** 归档抽取缓存封顶 200MB，按最后访问时间淘汰。 */
    private fun pruneArchiveCache() {
        val dir = File(context.cacheDir, "import")
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= MAX_ARCHIVE_CACHE_BYTES) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    /** 抽首图做封面：先量尺寸再按 256px 降采样，避免整页大图进内存。 */
    private fun saveCover(
        fingerprint: String,
        open: (Int) -> InputStream,
    ): String? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open(0).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = open(0).use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val file = File(dir, "$fingerprint.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
            bitmap.recycle()
            file.absolutePath
        }.getOrElse {
            Log.w("InkvolScan", "cover failed $fingerprint", it)
            null
        }
    }

    private data class DocEntry(
        val docId: String,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
    )
}
