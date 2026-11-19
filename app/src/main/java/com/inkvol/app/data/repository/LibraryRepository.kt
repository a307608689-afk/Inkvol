package com.inkvol.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import com.inkvol.app.data.import.LibraryScanner
import com.inkvol.app.data.import.SingleFileImporter
import com.inkvol.app.data.local.InkvolDatabase
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.model.CategoryRecord
import com.inkvol.app.data.model.DuplicateGroup
import com.inkvol.app.data.model.LibraryRecord
import com.inkvol.app.data.model.AnnotationRecord
import com.inkvol.app.data.model.BookmarkRecord
import com.inkvol.app.data.model.NovelChapterRecord
import com.inkvol.app.data.model.NovelProgressRecord
import com.inkvol.app.data.model.ReadingHistoryEntry
import com.inkvol.app.data.model.PurifyRuleRecord
import com.inkvol.app.data.model.ReadingStats
import com.inkvol.app.data.model.ScanState
import com.inkvol.app.data.model.TrashBook
import com.inkvol.app.ui.reader.panel.PanelLayoutCache
import com.inkvol.app.data.source.BookContentSource
import com.inkvol.app.data.source.BookSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 书架仓储（M2）：SQLite + 托管文件夹扫描 + 全局书夹。
 *
 * 分类是全局的（跨书库），删除书夹只解除关系；隐藏书库的过滤在 SQL 层完成。
 */
class LibraryRepository(private val context: Context) {

    private val db = InkvolDatabase(context)
    private val scanner = LibraryScanner(context, db)
    private val singleImporter = SingleFileImporter(context, db)
    private val sourceFactory = BookSourceFactory(context)
    private val novelChapterCache = android.util.LruCache<String, String>(6)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _books = MutableStateFlow<List<BookRecord>>(emptyList())
    val books: StateFlow<List<BookRecord>> = _books.asStateFlow()

    private val _categories = MutableStateFlow<List<CategoryRecord>>(emptyList())
    val categories: StateFlow<List<CategoryRecord>> = _categories.asStateFlow()

    private val _categoryBookIds = MutableStateFlow<Map<Long, Set<String>>>(emptyMap())
    val categoryBookIds: StateFlow<Map<Long, Set<String>>> = _categoryBookIds.asStateFlow()

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _readingStats = MutableStateFlow(ReadingStats())
    val readingStats: StateFlow<ReadingStats> = _readingStats.asStateFlow()

    // M6.3-C：净化规则（设置页管理，导入时应用）
    private val _purifyRules = MutableStateFlow<List<PurifyRuleRecord>>(emptyList())
    val purifyRules: StateFlow<List<PurifyRuleRecord>> = _purifyRules.asStateFlow()

    fun refreshPurifyRules() {
        scope.launch { _purifyRules.value = db.queryPurifyRules() }
    }

    fun addPurifyRule(name: String, pattern: String) {
        scope.launch {
            db.addPurifyRule(name, pattern)
            _purifyRules.value = db.queryPurifyRules()
        }
    }

    fun deletePurifyRule(id: Long) {
        scope.launch {
            db.deletePurifyRule(id)
            _purifyRules.value = db.queryPurifyRules()
        }
    }

    fun setPurifyRuleEnabled(id: Long, enabled: Boolean) {
        scope.launch {
            db.setPurifyRuleEnabled(id, enabled)
            _purifyRules.value = db.queryPurifyRules()
        }
    }

    private val _readingHistory = MutableStateFlow<List<ReadingHistoryEntry>>(emptyList())
    val readingHistory: StateFlow<List<ReadingHistoryEntry>> = _readingHistory.asStateFlow()

    private val _libraries = MutableStateFlow<List<LibraryRecord>>(emptyList())
    val libraries: StateFlow<List<LibraryRecord>> = _libraries.asStateFlow()

    private val _trashBooks = MutableStateFlow<List<TrashBook>>(emptyList())
    val trashBooks: StateFlow<List<TrashBook>> = _trashBooks.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups.asStateFlow()

    private val _duplicatePageReports = MutableStateFlow<List<com.inkvol.app.data.model.BookDuplicatePageReport>>(emptyList())
    val duplicatePageReports: StateFlow<List<com.inkvol.app.data.model.BookDuplicatePageReport>> =
        _duplicatePageReports.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        scope.launch { reload() }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) { reload() }

    fun moveBooksToTrash(ids: Collection<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            db.moveBooksToTrash(ids)
            reload()
            _notice.value = "已移入回收站 ${ids.size} 本"
        }
    }

    fun restoreBooksFromTrash(ids: Collection<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            db.restoreBooksFromTrash(ids)
            reload()
            _notice.value = "已恢复 ${ids.size} 本"
        }
    }

    fun purgeTrash(ids: Collection<String>, deleteFiles: Boolean) {
        scope.launch {
            val result = deleteBooks(ids, deleteFiles)
            _notice.value = if (result.filesFailed > 0) {
                "已清空 ${result.removed} 本，${result.filesFailed} 个源文件删除失败"
            } else {
                "已清空回收站 ${result.removed} 本"
            }
        }
    }

    fun setLibraryHidden(id: Long, hidden: Boolean) {
        scope.launch {
            db.setLibraryHidden(id, hidden)
            reload()
            _notice.value = if (hidden) "书库已隐藏" else "书库已显示"
        }
    }

    fun updateBooksMetadata(
        ids: Collection<String>,
        series: String?,
        writer: String?,
        tags: String?,
        readStatus: String?,
    ) {
        if (ids.isEmpty()) return
        scope.launch {
            db.updateBooksMetadata(ids, series, writer, tags, readStatus)
            reload()
            _notice.value = "已批量更新 ${ids.size} 本"
        }
    }

    fun rescanLibrary(library: LibraryRecord) {
        scope.launch {
            _scan.value = ScanState(running = true, current = "扫描「${library.name}」…")
            runCatching {
                val folders = withContext(Dispatchers.IO) {
                    db.queryFolders().filter { it.libraryId == library.id }
                }
                folders.forEach { folder ->
                    // 单文件导入的虚拟文件夹不是 SAF 目录，跳过
                    if (folder.treeUri == SingleFileImporter.SINGLE_LIBRARY_URI) return@forEach
                    scanner.scan(
                        Uri.parse(folder.treeUri),
                        folder.libraryId,
                        folder.id,
                    ) { count, current ->
                        _scan.value = ScanState(running = true, scanned = count, current = current)
                    }
                }
            }.onFailure {
                Log.w("InkvolScan", "library scan failed: ${library.name}", it)
            }
            reload()
            _scan.value = ScanState(running = false)
            _notice.value = "「${library.name}」扫描完成"
        }
    }

    /**
     * 书库管理「移除」：停止扫描该书库的文件夹并从书库列表移除。
     * 书架中来自这些文件夹的书同步移除（级联清进度/书签/分类归属）；同一本书若还有其它位置则保留；
     * 手机里的源文件不动。单文件导入虚拟库不支持移除（走书架单本移除）。
     */
    fun removeLibrary(library: LibraryRecord) {
        if (_scan.value.running) {
            _notice.value = "正在扫描中，请等扫描完成后再移除"
            return
        }
        scope.launch {
            val removed = withContext(Dispatchers.IO) {
                val folders = db.queryFolders().filter {
                    it.libraryId == library.id && it.treeUri != SingleFileImporter.SINGLE_LIBRARY_URI
                }
                if (folders.isEmpty()) return@withContext null
                val bookIds = ArrayList<String>()
                val covers = ArrayList<String>()
                folders.forEach { folder ->
                    val result = db.removeFolder(folder.id)
                    bookIds += result.bookIds
                    covers += result.covers
                }
                // 文件夹全部移除后书库壳一并删除，书库列表不留 0 本空卡
                if (db.queryFolders().none { it.libraryId == library.id }) {
                    db.deleteLibrary(library.id)
                }
                bookIds to covers
            }
            if (removed == null) {
                _notice.value = "该书库没有可移除的文件夹"
                return@launch
            }
            cleanupBookFiles(removed.first, removed.second)
            reload()
            _notice.value = "已移除「${library.name}」，书架同步更新，本地文件未删除"
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    // ---------------- 阅读器 ----------------

    suspend fun bookById(id: String): BookRecord? = withContext(Dispatchers.IO) {
        db.queryBook(id)
    }

    suspend fun openSource(book: BookRecord): BookContentSource =
        sourceFactory.open(book)

    suspend fun novelChapters(bookId: String): List<NovelChapterRecord> = withContext(Dispatchers.IO) {
        db.queryNovelChapters(bookId)
    }

    /** M6.1-D 设置页：全部未删除小说的书 ID。 */
    suspend fun novelBookIds(): List<String> = withContext(Dispatchers.IO) {
        db.queryNovelBookIds()
    }

    /** M6.1-B：按章节 byte 区间随机读取归一化 UTF-8 文本，M6.1-C 加 LRU 缓存。 */
    suspend fun readNovelChapter(bookId: String, chapter: NovelChapterRecord): String =
        withContext(Dispatchers.IO) {
            val key = "$bookId:${chapter.index}"
            novelChapterCache.get(key)?.let { return@withContext it }
            val file = File(context.filesDir, "novel_text/$bookId.txt")
            if (!file.exists()) return@withContext ""
            val length = (chapter.byteEnd - chapter.byteStart).coerceAtLeast(0L)
            if (length <= 0L) return@withContext ""
            val output = java.io.ByteArrayOutputStream(length.toInt().coerceAtMost(64 * 1024 * 1024))
            java.io.RandomAccessFile(file, "r").use { random ->
                random.seek(chapter.byteStart)
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val read = random.read(
                        buffer,
                        0,
                        buffer.size.toLong().coerceAtMost(remaining).toInt(),
                    )
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
            val text = output.toString("UTF-8")
            novelChapterCache.put(key, text)
            text
        }

    suspend fun novelProgress(bookId: String): NovelProgressRecord? = withContext(Dispatchers.IO) {
        db.queryNovelProgress(bookId)
    }

    // ---------------- M6.3-A 标注 ----------------

    suspend fun annotations(bookId: String, chapterIdx: Int? = null): List<AnnotationRecord> =
        withContext(Dispatchers.IO) {
            db.queryAnnotations(bookId, chapterIdx)
        }

    suspend fun addAnnotation(record: AnnotationRecord): Long = withContext(Dispatchers.IO) {
        db.insertAnnotation(record)
    }

    suspend fun updateAnnotation(
        id: Long,
        color: String? = null,
        style: String? = null,
        note: String? = null,
        charStart: Int? = null,
        charEnd: Int? = null,
        snippet: String? = null,
    ) = withContext(Dispatchers.IO) {
        db.updateAnnotation(id, color, style, note, charStart, charEnd, snippet)
    }

    suspend fun deleteAnnotation(id: Long) = withContext(Dispatchers.IO) {
        db.deleteAnnotation(id)
    }

    // ---------------- M6.3-B 书签 / 搜索 / upsert ----------------

    /** 同章内与区间有交集的既有标注（重复划线 upsert 判定）。 */
    suspend fun overlappingAnnotation(
        bookId: String,
        chapterIdx: Int,
        charStart: Int,
        charEnd: Int,
    ): AnnotationRecord? = withContext(Dispatchers.IO) {
        db.queryOverlappingAnnotation(bookId, chapterIdx, charStart, charEnd)
    }

    suspend fun addBookmark(bookId: String, chapterIdx: Int, charStart: Int, snippet: String) =
        withContext(Dispatchers.IO) {
            db.addBookmark(bookId, chapterIdx, charStart, snippet)
        }

    suspend fun deleteBookmark(id: Long) = withContext(Dispatchers.IO) {
        db.deleteBookmark(id)
    }

    suspend fun bookmarks(bookId: String): List<BookmarkRecord> = withContext(Dispatchers.IO) {
        db.queryBookmarks(bookId)
    }

    fun saveReadingSession(bookId: String, startedAt: Long, durationMs: Long, pagesRead: Int = 0) {
        scope.launch { db.saveReadingSession(bookId, startedAt, durationMs, pagesRead) }
    }

    fun saveNovelProgress(bookId: String, chapterIndex: Int, charOffset: Int, overallPercent: Double) {
        scope.launch {
            db.saveNovelProgress(bookId, chapterIndex, charOffset)
            db.saveProgress(bookId, chapterIndex, overallPercent)
        }
    }

    suspend fun progressPage(bookId: String): Int = withContext(Dispatchers.IO) {
        db.queryProgressPage(bookId)
    }

    fun saveProgress(bookId: String, page: Int, percent: Double) {
        scope.launch {
            db.saveProgress(bookId, page, percent)
        }
    }

    suspend fun startReadingSession(bookId: String): Long = withContext(Dispatchers.IO) {
        db.startReadingSession(bookId)
    }

    fun updateReadingSessionDuration(sessionId: Long, durationMs: Long) {
        if (sessionId <= 0 || durationMs <= 0) return
        scope.launch { db.updateReadingSessionDuration(sessionId, durationMs) }
    }

    fun addReadingPages(sessionId: Long, pages: Int) {
        if (sessionId <= 0 || pages <= 0) return
        scope.launch { db.addReadingPages(sessionId, pages) }
    }

    fun endReadingSession(sessionId: Long?) {
        if (sessionId == null || sessionId <= 0) return
        scope.launch {
            db.endReadingSession(sessionId)
            reloadStats()
            com.inkvol.app.widget.ContinueReadingWidget.updateAll(context)
        }
    }

    fun refreshStats() {
        scope.launch { reloadStats() }
    }

    private suspend fun reloadStats() {
        _readingStats.value = db.queryReadingStats()
        _readingHistory.value = db.queryReadingHistory()
        _libraries.value = db.queryLibraries()
        _trashBooks.value = db.queryTrashBooks()
        _duplicateGroups.value = queryDuplicateGroups(db.queryBooks())
    }

    private fun queryDuplicateGroups(books: List<BookRecord>): List<DuplicateGroup> {
        return books
            .filter { it.title.isNotBlank() }
            .groupBy { Quad(it.title.trim().lowercase(), it.format.uppercase(), it.pageCount, it.fileSize) }
            .filter { it.value.size > 1 }
            .map { (key, group) ->
                DuplicateGroup(
                    title = group.first().title,
                    format = key.second,
                    pageCount = key.third,
                    fileSize = group.sumOf { it.fileSize },
                    books = group.sortedBy { it.title },
                )
            }
            .sortedByDescending { it.books.size }
    }

    private data class Quad(
        val first: String,
        val second: String,
        val third: Int,
        val fourth: Long,
    )

    fun detectDuplicatePages() {
        scope.launch {
            val reports = withContext(Dispatchers.IO) {
                db.queryBooks().mapNotNull { book ->
                    runCatching {
                        val pageGroups = HashMap<String, MutableList<Int>>()
                        sourceFactory.open(book).use { source ->
                            for (index in 0 until source.pageCount) {
                                source.openPage(index).use { input ->
                                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                                    val buffer = ByteArray(64 * 1024)
                                    var read = 0
                                    while (read < buffer.size) {
                                        val count = input.read(buffer, read, buffer.size - read)
                                        if (count < 0) break
                                        digest.update(buffer, read, count)
                                        read += count
                                    }
                                    val hash = digest.digest().joinToString("") {
                                        "%02x".format(it)
                                    }
                                    pageGroups.getOrPut(hash) { ArrayList() } += index
                                }
                            }
                        }
                        val groups = pageGroups.values.filter { it.size > 1 }
                        if (groups.isEmpty()) {
                            null
                        } else {
                            com.inkvol.app.data.model.BookDuplicatePageReport(
                                bookId = book.id,
                                title = book.title,
                                pageCount = book.pageCount,
                                groups = groups,
                            )
                        }
                    }.getOrNull()
                }
            }
            _duplicatePageReports.value = reports
            _notice.value = if (reports.isEmpty()) {
                "书内重复页检测完成，未发现重复"
            } else {
                "书内重复页检测完成，${reports.size} 本有重复页"
            }
        }
    }

    // ---------------- 导入 ----------------

    fun importTree(uri: Uri) {
        scope.launch { runScan(uri) }
    }

    fun importDocument(uri: Uri) {
        scope.launch {
            _scan.value = ScanState(running = true, current = "导入文件…")
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            try {
                val book = singleImporter.import(uri)
                reload()
                _scan.value = ScanState(running = false, added = 1)
                _notice.value = "已加入书架：《${book.title}》"
            } catch (error: SingleFileImporter.AlreadyExistsException) {
                _scan.value = ScanState(running = false)
                _notice.value = error.message
            } catch (error: Throwable) {
                Log.w("InkvolImport", "single file import failed", error)
                _scan.value = ScanState(running = false)
                _notice.value = error.message ?: "导入失败"
            }
        }
    }

    fun rescanAll() {
        scope.launch {
            val folders = withContext(Dispatchers.IO) { db.queryFolders() }
                // 单文件导入的虚拟文件夹（inkvol://single-file）不是 SAF 目录，扫描必失败，跳过
                .filter { it.treeUri != SingleFileImporter.SINGLE_LIBRARY_URI }
            if (folders.isEmpty()) {
                _notice.value = "还没有导入文件夹"
                return@launch
            }
            _scan.value = ScanState(running = true, current = "重新扫描…")
            var added = 0
            val failedNames = ArrayList<String>()
            folders.forEach { folder ->
                runCatching {
                    val result = scanner.scan(
                        Uri.parse(folder.treeUri),
                        folder.libraryId,
                        folder.id,
                    ) { count, current ->
                        _scan.value = ScanState(running = true, scanned = count, current = current)
                    }
                    added += result.added
                }.onFailure {
                    failedNames += folder.name
                    Log.w("InkvolScan", "rescan failed: ${folder.treeUri}", it)
                }
            }
            reload()
            _scan.value = ScanState(running = false, added = added)
            _notice.value = if (failedNames.isEmpty()) {
                "扫描完成 · 新增 $added 本"
            } else {
                "扫描完成 · 新增 $added 本，${failedNames.size} 个文件夹失败（${failedNames.joinToString("、")}）"
            }
        }
    }

    private suspend fun runScan(uri: Uri) {
        _scan.value = ScanState(running = true)
        try {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onFailure { Log.w("InkvolScan", "persist permission failed", it) }
            // 追加持久化写权限：连文件删除需要；部分 Provider 不授写权，失败不阻塞导入
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.onFailure { Log.w("InkvolScan", "persist write permission failed", it) }

            val displayName = queryRootName(uri) ?: "本地漫画"
            val libraryId = db.findOrCreateLibrary(uri.toString(), displayName)
            val folderId = db.findOrCreateFolder(libraryId, uri.toString(), displayName)

            val result = scanner.scan(uri, libraryId, folderId) { count, current ->
                _scan.value = ScanState(
                    running = true,
                    scanned = count,
                    added = count,
                    current = current,
                )
            }
            reload()
            _scan.value = ScanState(running = false, scanned = result.added, added = result.added)
            _notice.value = if (result.added > 0) {
                "导入完成 · 新增 ${result.added} 本"
            } else {
                "没有发现新书"
            }
        } catch (error: Throwable) {
            Log.e("InkvolScan", "import failed", error)
            _scan.value = ScanState(running = false, error = error.message ?: "扫描失败")
        }
    }

    // ---------------- 删除 ----------------

    data class DeleteResult(
        val removed: Int,
        val filesDeleted: Int,
        val filesFailed: Int,
    )

    /**
     * 删除书本。deleteFiles=true 时尝试删除源文件（仅树内文件；单文件导入无树权限自动跳过）。
     * 删除失败（无写权限 / Provider 不支持）降级为仅从书架移除。
     */
    suspend fun deleteBooks(ids: Collection<String>, deleteFiles: Boolean): DeleteResult =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext DeleteResult(0, 0, 0)
            var filesDeleted = 0
            var filesFailed = 0
            if (deleteFiles) {
                ids.forEach { id ->
                    db.queryBookLocations(id).forEach { loc ->
                        if (loc.treeUri.isBlank() || loc.treeUri == SingleFileImporter.SINGLE_LIBRARY_URI) {
                            return@forEach
                        }
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(
                            Uri.parse(loc.treeUri),
                            loc.docId,
                        )
                        val ok = runCatching {
                            DocumentsContract.deleteDocument(context.contentResolver, docUri)
                        }.getOrDefault(false)
                        if (ok) filesDeleted++ else filesFailed++
                    }
                }
            }
            val covers = db.deleteBooks(ids)
            cleanupBookFiles(ids, covers)
            reload()
            _notice.value = when {
                deleteFiles && filesFailed == 0 && filesDeleted > 0 -> "已删除 ${ids.size} 本（含源文件）"
                deleteFiles && filesFailed > 0 -> "已删除 ${ids.size} 本，其中 $filesFailed 本文件删除失败（可能无写入权限）"
                else -> "已从书架移除 ${ids.size} 本"
            }
            DeleteResult(removed = ids.size, filesDeleted = filesDeleted, filesFailed = filesFailed)
        }

    private fun cleanupBookFiles(ids: Collection<String>, covers: List<String>) {
        covers.forEach { path -> runCatching { File(path).delete() } }
        val idSet = ids.toSet()
        idSet.forEach { id ->
            runCatching { PanelLayoutCache.clear(context, id) }
            runCatching { com.inkvol.app.data.import.TxtNovelParser.deleteText(context, id) }
            // M6.2-C：EPUB 图片目录随书清理
            runCatching { File(context.filesDir, "epub_images/$id").deleteRecursively() }
        }
        File(context.cacheDir, "import").listFiles()?.filter { file ->
            file.isFile && idSet.any { id -> file.name == id || file.name.startsWith("$id.") }
        }?.forEach { runCatching { it.delete() } }
    }

    // ---------------- 书夹 ----------------

    fun createCategory(name: String, color: String) {
        scope.launch {
            db.insertCategory(name.trim(), color)
            reload()
        }
    }

    fun renameCategory(id: Long, name: String) {
        scope.launch {
            db.renameCategory(id, name.trim())
            reload()
        }
    }

    fun setCategoryColor(id: Long, color: String) {
        scope.launch {
            db.setCategoryColor(id, color)
            reload()
        }
    }

    fun deleteCategory(id: Long) {
        scope.launch {
            db.deleteCategory(id)
            reload()
            _notice.value = "已删除书夹，书本仍在书架"
        }
    }

    fun moveCategory(id: Long, delta: Int) {
        scope.launch {
            db.moveCategory(id, delta)
            reload()
        }
    }

    fun reorderCategories(orderedIds: Collection<Long>) {
        if (orderedIds.isEmpty()) return
        scope.launch {
            db.reorderCategories(orderedIds)
            reload()
        }
    }

    fun addBooksToCategory(bookIds: Collection<String>, categoryId: Long) {
        if (bookIds.isEmpty()) return
        scope.launch {
            val inserted = db.addBooksToCategory(bookIds, categoryId)
            reload()
            _notice.value = if (inserted > 0) "已加入书夹 · $inserted 本" else "这些书已在书夹里"
        }
    }

    fun removeBooksFromCategory(bookIds: Collection<String>, categoryId: Long) {
        if (bookIds.isEmpty()) return
        scope.launch {
            db.removeBooksFromCategory(bookIds, categoryId)
            reload()
            _notice.value = "已从书夹移出 ${bookIds.size} 本"
        }
    }

    // ---------------- 内部 ----------------

    private suspend fun reload() {
        withContext(Dispatchers.IO) {
            _books.value = db.queryBooks()
            _categories.value = db.queryCategories()
            _categoryBookIds.value = db.queryCategoryBookIds()
            db.purgeExpiredTrash()
            _readingStats.value = db.queryReadingStats()
            _readingHistory.value = db.queryReadingHistory()
            _libraries.value = db.queryLibraries()
            _trashBooks.value = db.queryTrashBooks()
            _duplicateGroups.value = queryDuplicateGroups(db.queryBooks())
        }
    }

    private fun queryRootName(uri: Uri): String? = runCatching {
        val rootDocId = DocumentsContract.getTreeDocumentId(uri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, rootDocId)
        context.contentResolver.query(
            docUri,
            arrayOf(Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
}
