package com.inkvol.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import com.inkvol.app.data.import.ArchiveProber
import com.inkvol.app.data.import.EpubParser
import com.inkvol.app.data.import.LibraryScanner
import com.inkvol.app.data.import.MobiParser
import com.inkvol.app.data.import.NovelCoverGenerator
import com.inkvol.app.data.import.SingleFileImporter
import com.inkvol.app.data.import.TxtNovelParser
import com.inkvol.app.data.local.InkvolDatabase
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.model.BookSectionRecord
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
import com.inkvol.app.data.model.FolderFacet
import com.inkvol.app.data.model.FullTextEvent
import com.inkvol.app.data.model.FullTextHit
import com.inkvol.app.data.model.SearchChapterRow
import com.inkvol.app.data.model.SearchNoteRow
import com.inkvol.app.data.model.SearchFacets
import com.inkvol.app.data.model.SearchFilter
import com.inkvol.app.data.model.SearchFilterCodec
import com.inkvol.app.data.model.SearchFilterTemplate
import com.inkvol.app.data.model.SearchState
import com.inkvol.app.data.model.TagFacet
import com.inkvol.app.data.model.TriStateSet
import com.inkvol.app.data.model.tagList
import com.inkvol.app.data.model.FullTextBookGroup
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.data.settings.SettingsRepository
import com.inkvol.app.ui.reader.panel.PanelLayoutCache
import com.inkvol.app.data.source.BookContentSource
import com.inkvol.app.data.source.BookSourceFactory
import com.inkvol.app.data.source.EpubImageSource
import com.inkvol.app.data.source.MobiImageSource
import com.inkvol.app.data.source.SectionedContentSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 书架仓储（M2）：SQLite + 托管文件夹扫描 + 全局书夹。
 *
 * 分类是全局的（跨书库），删除书夹只解除关系；隐藏书库的过滤在 SQL 层完成。
 */
class LibraryRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * 应用私有根目录。EPUB 插图的 marker 是相对路径（epub_images/{bookId}/xxx），
     * 分页量图片宽高比时需要拼这个根（否则 decodeFile 找不到文件、比例全部按 4:3 兜底）。
     */
    val filesDir: File get() = context.filesDir

    private val db = InkvolDatabase(context)
    private val scanner = LibraryScanner(context, db)
    private val singleImporter = SingleFileImporter(context, db)
    private val sourceFactory = BookSourceFactory(context)
    private val novelChapterCache = android.util.LruCache<String, String>(6)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /**
     * 扫描互斥：LibraryScanner 是单例且带成员计数器，并发调用会互相污染；
     * 同时保护「v12 路径回填」与用户主动重扫/导入不撞车。
     */
    private val scanMutex = Mutex()

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

    /** 供同步模块发射完成/失败通知（AppContainer 装配后可用）。 */
    fun postNotice(message: String) {
        _notice.value = message
    }

    /** M4.5 同步挂钩：进度保存后通知（setter 注入避免构造环；null = 同步未装配）。 */
    var syncOnProgressSaved: (() -> Unit)? = null

    /** M4.5 同步挂钩：书籍被级联硬删（多选删除/书库移除/回收站清除）后通知。 */
    var syncOnBooksPurged: ((Collection<String>) -> Unit)? = null

    init {
        scope.launch { reload() }
        scope.launch { restoreSearchSession() }
        // v12 无感迁移：旧库的 book_locations.folder_path 为空（系列视图只能按旧
        // series 名退化），冷启动延迟 6s 后台重扫一次回填文件夹层级；失败 24h 后再试。
        scope.launch {
            delay(6_000)
            backfillSeriesPaths()
        }
    }

    /** 扫描深度（设置→书架，缺省 3 层）：每次扫描前读一次，改动即时生效。 */
    private suspend fun scanDepth(): Int = runCatching {
        val prefs = settingsRepository.snapshotPreferences()
        settingsRepository.stringOf(prefs, SettingsKeys.LIBRARY_SCAN_DEPTH, "3")
            .toIntOrNull()
            ?.coerceIn(1, 6)
            ?: 3
    }.getOrDefault(3)

    /**
     * v12 系列路径回填（无感迁移）：只扫「非单文件」文件夹库，已存在的书走 skip
     * 分支由 [InkvolDatabase.updateBookLocationFolder] 顺带补 folder_path。
     * 成功一轮后 hasMissingFolderPath() 自然为 false，此后不再触发。
     * 与用户主动扫描互斥（tryLock 失败直接放弃，下次冷启动再试）。
     */
    private suspend fun backfillSeriesPaths() {
        val folders = withContext(Dispatchers.IO) {
            if (_scan.value.running || !db.hasMissingFolderPath()) {
                return@withContext emptyList<InkvolDatabase.FolderRow>()
            }
            val all = db.queryFolders().filter { it.treeUri != SingleFileImporter.SINGLE_LIBRARY_URI }
            if (all.isEmpty()) return@withContext emptyList()
            val lastTry = runCatching {
                settingsRepository.stringOf(
                    settingsRepository.snapshotPreferences(),
                    SettingsKeys.SERIES_PATH_BACKFILL_AT,
                    "0",
                ).toLongOrNull() ?: 0L
            }.getOrDefault(0L)
            if (System.currentTimeMillis() - lastTry < 24L * 60 * 60 * 1000) {
                return@withContext emptyList()
            }
            runCatching {
                settingsRepository.putString(
                    SettingsKeys.SERIES_PATH_BACKFILL_AT,
                    System.currentTimeMillis().toString(),
                )
            }
            all
        }
        if (folders.isEmpty()) return
        val depth = scanDepth()
        if (!scanMutex.tryLock()) return
        try {
            folders.forEach { folder ->
                runCatching {
                    scanner.scan(
                        Uri.parse(folder.treeUri),
                        folder.libraryId,
                        folder.id,
                        depthLimit = depth,
                    ) { _, _ -> }
                }.onFailure {
                    Log.w("InkvolScan", "series path backfill failed: ${folder.treeUri}", it)
                }
            }
        } finally {
            scanMutex.unlock()
        }
        reload()
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
            val depth = scanDepth()
            scanMutex.withLock {
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
                            depthLimit = depth,
                        ) { count, current ->
                            _scan.value = ScanState(running = true, scanned = count, current = current)
                        }
                    }
                }.onFailure {
                    Log.w("InkvolScan", "library scan failed: ${library.name}", it)
                }
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
            syncOnBooksPurged?.invoke(removed.first)
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

    data class RetypeResult(val ok: Int, val failed: Int)

    /**
     * EPUB / MOBI 阅读类型互转。
     *
     * - toComic = true：按图片页重建（pageCount = 图片页数，封面取第一页），清小说章节与进度；
     * - toComic = false：重新解析章节文本，清漫画页进度与页书签；
     * - 成功后写 book_type_overrides，重扫不再自动判定覆盖用户的选择。
     *
     * 源文件不动，只改书架记录与派生数据；进度按提示从 0 开始。
     */
    /**
     * P3：章节落库 write-through——库中无记录才写（存量书打开一次即补齐）。
     * 阅读路径始终从内容源现算，DB 只服务备份，不做读取缓存。
     */
    fun saveBookSectionsIfEmpty(
        bookId: String,
        sections: List<com.inkvol.app.data.source.BookSection>,
    ) {
        if (sections.isEmpty()) return
        runCatching {
            if (db.queryBookSections(bookId).isNotEmpty()) return
            db.replaceBookSections(
                bookId,
                sections.map { BookSectionRecord(it.idx, it.title, it.pageStart) },
            )
        }
    }

    suspend fun retypeBooks(ids: Collection<String>, toComic: Boolean): RetypeResult =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext RetypeResult(0, 0)
            var ok = 0
            var failed = 0
            ids.forEach { id ->
                runCatching {
                    val book = db.queryBook(id) ?: error("书不见了")
                    val ext = book.format.lowercase()
                    if (ext != "epub" && ext != "mobi") error("仅支持 EPUB / MOBI")
                    val local = sourceFactory.localFile(book, ext)

                    if (toComic) {
                        if (book.type == "COMIC") return@runCatching
                        val source = if (ext == "epub") EpubImageSource(local) else MobiImageSource(local)
                        try {
                            val pages = source.pageCount
                            if (pages <= 0) error("没有可用的图片页")
                            val cover = ArchiveProber(context)
                                .saveCover(id) { index -> source.openPage(index) }
                            db.updateBookKind(id, "COMIC", book.format.uppercase(), pages, cover)
                            // P3：章节落库（备份用）
                            runCatching {
                                db.replaceBookSections(
                                    id,
                                    (source as? SectionedContentSource)?.listSections().orEmpty()
                                        .map {
                                            BookSectionRecord(it.idx, it.title, it.pageStart)
                                        },
                                )
                            }
                        } finally {
                            source.close()
                        }
                        // 小说侧派生数据整体清掉，避免搜索命中 / 继续阅读串味
                        db.deleteNovelProgress(id)
                        runCatching { File(context.filesDir, "epub_images/$id").deleteRecursively() }
                        runCatching { TxtNovelParser.deleteText(context, id) }
                    } else {
                        if (book.type == "NOVEL") return@runCatching
                        // 两个 parse 返回类型不同，不能写成三元分支（会推断成 Any）
                        val parsedChapters: List<NovelChapterRecord>
                        val parsedCover: String?
                        if (ext == "epub") {
                            val parsed = EpubParser.parse(context, id, local)
                            parsedChapters = parsed.chapters
                            parsedCover = parsed.coverPath
                        } else {
                            val parsed = MobiParser.parse(context, id, local)
                            parsedChapters = parsed.chapters
                            parsedCover = parsed.coverPath
                        }
                        if (parsedChapters.isEmpty()) error("没有解析出章节")
                        db.replaceNovelChapters(id, parsedChapters)
                        val cover = parsedCover
                            ?: NovelCoverGenerator.save(context, id, book.title, parsedChapters.size)
                        db.updateBookKind(id, "NOVEL", book.format.uppercase(), parsedChapters.size, cover)
                        db.deleteComicProgress(id)
                        // 漫画侧章节区间随之作废
                        runCatching { db.clearBookSections(id) }
                        runCatching { PanelLayoutCache.clear(context, id) }
                    }
                    db.markTypeOverridden(id)
                    ok++
                }.onFailure { error ->
                    failed++
                    Log.w(
                        "InkvolRetype",
                        "retype failed: $id -> ${if (toComic) "COMIC" else "NOVEL"}",
                        error,
                    )
                }
            }
            if (ok > 0) reload()
            postNotice(
                if (failed == 0) {
                    "已转换 $ok 本"
                } else {
                    "已转换 $ok 本，失败 $failed 本"
                },
            )
            RetypeResult(ok, failed)
        }

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
            val text = readChapterBytes(bookId, chapter)
            novelChapterCache.put(key, text)
            text
        }

    /** M7 全文搜索专用：同一读法但**不进 LRU**——全库扫描会疯狂替换缓存项，挤出阅读器热章节。 */
    suspend fun readNovelChapterRaw(bookId: String, chapter: NovelChapterRecord): String =
        withContext(Dispatchers.IO) { readChapterBytes(bookId, chapter) }

    private fun readChapterBytes(bookId: String, chapter: NovelChapterRecord): String {
        val file = File(context.filesDir, "novel_text/$bookId.txt")
        if (!file.exists()) return ""
        val length = (chapter.byteEnd - chapter.byteStart).coerceAtLeast(0L)
        if (length <= 0L) return ""
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
        return output.toString("UTF-8")
    }

    // ---------------- M7 全局搜索 ----------------

    /** 单书命中上限（与阅读器书内搜索同口径）；全库软上限防超大库淹没。 */
    private companion object {
        const val FULL_TEXT_PER_BOOK_LIMIT = 100
        const val FULL_TEXT_GLOBAL_LIMIT = 300
    }

    /**
     * 书目搜索：全量候选内存过滤（书量 <1万，与书架 filteredBooks 同模式）。
     * SQL 不便表达逗号标签与书夹/文件夹映射，统一在内存判定；JOIN 去重口径由 db.queryBooks 保证。
     */
    suspend fun searchBooks(query: String, filter: SearchFilter): List<BookRecord> =
        withContext(Dispatchers.IO) {
            val books = db.queryBooks()
            val categoryIds = db.queryCategoryBookIds()
            val folderIds = db.queryBookFolderIds()
            filterBooks(books, query, filter, categoryIds, folderIds)
        }

    private fun filterBooks(
        books: List<BookRecord>,
        query: String,
        filter: SearchFilter,
        categoryIds: Map<Long, Set<String>>,
        folderIds: Map<String, Set<Long>>,
    ): List<BookRecord> {
        val q = query.trim()
        val result = books.filter { book ->
            val keywordHit = q.isEmpty() ||
                listOf(
                    book.title,
                    book.series ?: "",
                    book.volume ?: "",
                    book.writer ?: "",
                    book.tags ?: "",
                    book.displayPath,
                ).any { it.contains(q, ignoreCase = true) }
            keywordHit &&
                filter.matchesBasic(book) &&
                matchCategories(book.id, filter.categories, categoryIds) &&
                matchFolders(book.id, filter.folders, folderIds)
        }
        return sortBooksByRelevance(result, q)
    }

    /** 书夹三态判定：UNCLASSIFIED_ID 虚拟项 = 不在任何书夹。 */
    private fun matchCategories(
        bookId: String,
        tri: TriStateSet,
        categoryIds: Map<Long, Set<String>>,
    ): Boolean {
        if (!tri.isActive) return true
        val inAny = categoryIds.values.any { bookId in it }
        fun hit(id: String): Boolean =
            if (id == SearchFilter.UNCLASSIFIED_ID) !inAny
            else categoryIds[id.toLongOrNull()]?.contains(bookId) == true
        if (tri.include.isNotEmpty() && tri.include.none { hit(it) }) return false
        if (tri.exclude.isNotEmpty() && tri.exclude.any { hit(it) }) return false
        return true
    }

    /** 文件夹三态判定（同书夹结构）。 */
    private fun matchFolders(
        bookId: String,
        tri: TriStateSet,
        folderIds: Map<String, Set<Long>>,
    ): Boolean {
        if (!tri.isActive) return true
        val owned = folderIds[bookId] ?: emptySet()
        fun hit(id: String): Boolean = id.toLongOrNull()?.let { it in owned } == true
        if (tri.include.isNotEmpty() && tri.include.none { hit(it) }) return false
        if (tri.exclude.isNotEmpty() && tri.exclude.any { hit(it) }) return false
        return true
    }

    /** 相关性排序：标题前缀命中 > 标题包含 > 其余字段；同级按加入时间新→旧。 */
    private fun sortBooksByRelevance(list: List<BookRecord>, query: String): List<BookRecord> {
        if (query.isBlank()) return list.sortedByDescending { it.addedAt }
        val ql = query.lowercase()
        return list.sortedWith(
            compareBy(
                { !it.title.lowercase().startsWith(ql) },
                { !it.title.lowercase().contains(ql) },
                { -it.addedAt },
            ),
        )
    }

    /** 章节标题搜索：SQL LIKE + 书籍级筛选兜底（书夹/文件夹/系列/作者/标签在内存对齐书目口径）。 */
    suspend fun searchChapterHits(query: String, filter: SearchFilter): List<SearchChapterRow> =
        withContext(Dispatchers.IO) {
            val rows = db.querySearchChapters(query, filter.type, filter.formats, filter.readStatus)
            if (rows.isEmpty()) return@withContext rows
            if (filter.isEmpty()) return@withContext rows
            val allowed = filterBooks(
                db.queryBooks(),
                "",
                filter,
                db.queryCategoryBookIds(),
                db.queryBookFolderIds(),
            ).map { it.id }.toSet()
            rows.filter { it.bookId in allowed }
        }

    /**
     * 笔记搜索：annotations 表（划线/批注/书签同表），note（批注文字）/ snippet（划线原文）
     * 任一命中即出，最近修改优先；书级筛选（类型/格式/状态/书夹/文件夹）与书目同口径。
     */
    suspend fun searchNotes(query: String, filter: SearchFilter): List<SearchNoteRow> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext emptyList()
            val annotations = db.queryAllAnnotations()
            if (annotations.isEmpty()) return@withContext emptyList()
            val booksById = db.queryBooks().associateBy { it.id }
            val allowed: Set<String>? = if (filter.isEmpty()) {
                null
            } else {
                filterBooks(
                    db.queryBooks(),
                    "",
                    filter,
                    db.queryCategoryBookIds(),
                    db.queryBookFolderIds(),
                ).map { it.id }.toSet()
            }
            annotations.mapNotNull { ann ->
                val book = booksById[ann.bookId] ?: return@mapNotNull null
                val noteHit = ann.note?.contains(q, ignoreCase = true) == true
                val snippetHit = ann.snippet.contains(q, ignoreCase = true)
                if (!noteHit && !snippetHit) return@mapNotNull null
                if (allowed != null && ann.bookId !in allowed) return@mapNotNull null
                SearchNoteRow(book, ann)
            }.let { if (it.size > 100) it.take(100) else it }
        }

    /** 筛选 facet 数据源（进页一次预载：书夹/文件夹/系列/标签/作者 distinct）。 */
    suspend fun searchFacets(): SearchFacets = withContext(Dispatchers.IO) {
        val books = db.queryBooks()
        val tagCounts = HashMap<String, Int>()
        val seriesSet = LinkedHashSet<String>()
        val writerSet = LinkedHashSet<String>()
        books.forEach { book ->
            book.tagList().forEach { tagCounts.merge(it, 1, Int::plus) }
            book.series?.takeIf { it.isNotBlank() }?.let { seriesSet.add(it) }
            book.writer?.takeIf { it.isNotBlank() }?.let { writerSet.add(it) }
        }
        SearchFacets(
            categories = db.queryCategories(),
            folders = db.queryFolders().map { FolderFacet(it.id, it.name) },
            series = seriesSet.toList().sortedWith(String.CASE_INSENSITIVE_ORDER),
            tags = tagCounts.entries
                .map { TagFacet(it.key, it.value) }
                .sortedWith(compareByDescending<TagFacet> { it.count }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
            writers = writerSet.toList().sortedWith(String.CASE_INSENSITIVE_ORDER),
        )
    }

    /**
     * 正文全文流式扫描：串行逐书（LRU 不友好也没必要并行 IO）、每书/全库双上限、
     * 逐书 emit 事件（调用方取消 Flow 即取消扫描）。中文子串匹配 indexOf(ignoreCase)，无分词依赖。
     */
    fun searchNovelFullText(
        books: List<BookRecord>,
        query: String,
    ): Flow<FullTextEvent> = flow {
        val limitedIds = mutableSetOf<String>()
        var globalCount = 0
        var scanned = 0
        for (book in books) {
            currentCoroutineContext().ensureActive()
            scanned++
            emit(FullTextEvent.Progress(scanned, books.size, book.title))
            val hits = ArrayList<FullTextHit>()
            for (chapter in db.queryNovelChapters(book.id)) {
                currentCoroutineContext().ensureActive()
                val text = readChapterBytes(book.id, chapter)
                if (text.isEmpty()) continue
                var idx = text.indexOf(query, ignoreCase = true)
                while (idx >= 0 && hits.size < FULL_TEXT_PER_BOOK_LIMIT && globalCount < FULL_TEXT_GLOBAL_LIMIT) {
                    val ctxStart = (idx - 24).coerceAtLeast(0)
                    val ctxEnd = (idx + query.length + 40).coerceAtMost(text.length)
                    hits += FullTextHit(
                        book = book,
                        chapterIdx = chapter.index,
                        chapterTitle = chapter.title,
                        charPos = idx,
                        context = text.substring(ctxStart, ctxEnd).replace('\n', ' '),
                        matchLen = query.length,
                        ctxOffset = ctxStart,
                    )
                    globalCount++
                    idx = text.indexOf(query, idx + query.length, ignoreCase = true)
                }
                if (hits.size >= FULL_TEXT_PER_BOOK_LIMIT) {
                    limitedIds += book.id
                    break
                }
                if (globalCount >= FULL_TEXT_GLOBAL_LIMIT) break
            }
            if (hits.isNotEmpty()) emit(FullTextEvent.BookBatch(book, hits.toList()))
            if (globalCount >= FULL_TEXT_GLOBAL_LIMIT) break
        }
        emit(FullTextEvent.Done(limitedIds.toSet(), globalCount >= FULL_TEXT_GLOBAL_LIMIT))
    }.flowOn(Dispatchers.IO)

    // ---------------- M7 搜索会话（状态 + 历史/筛选/模板持久化） ----------------

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private var searchJob: Job? = null
    private var bodyScanJob: Job? = null

    /** 进页预载筛选 facet（幂等；书架扫描完成后 facets 需刷新时置回 null 重载）。 */
    fun ensureSearchFacets() {
        if (_search.value.facets != null) return
        scope.launch { _search.value = _search.value.copy(facets = searchFacets()) }
    }

    /** facet 失效（后台扫描完成）后强制重载。 */
    fun invalidateSearchFacets() {
        _search.value = _search.value.copy(facets = null)
        ensureSearchFacets()
    }

    /**
     * 执行搜索：书目/章节即时（repository.searchBooks 内存过滤毫秒级）；
     * 正文扫描延迟 bodyDebounceMs 再起跑（输入触发 400ms 防打字抖动，筛选变化 0ms 立即重启）。
     * 关键词 <2 字符不扫正文（1 字命中泛滥）；类型筛=仅漫画时不扫（无正文）。
     */
    fun runSearch(rawQuery: String, bodyDebounceMs: Long = 400L) {
        val q = rawQuery.trim()
        val filter = _search.value.filter
        searchJob?.cancel()
        _search.value = _search.value.copy(
            query = q,
            searched = q.isNotEmpty(),
            loading = q.isNotEmpty(),
            books = emptyList(),
            notes = emptyList(),
            chapterHits = emptyList(),
            bodyGroups = emptyList(),
            bodyScanned = 0,
            bodyTotal = 0,
            bodyCurrentTitle = "",
            bodyDone = true,
            bodyBookLimitedIds = emptySet(),
            bodyGlobalLimited = false,
        )
        if (q.isEmpty()) {
            bodyScanJob?.cancel()
            return
        }
        pushHistory(q)
        searchJob = scope.launch {
            // 分区筛选：关掉的分区查询直接短路（正文关=不起扫描，见 startBodyScan）
            val books = if (filter.sectionOn(SearchFilter.SECTION_BOOK)) searchBooks(q, filter) else emptyList()
            val chapters = if (filter.sectionOn(SearchFilter.SECTION_CHAPTER)) searchChapterHits(q, filter) else emptyList()
            val notes = if (filter.sectionOn(SearchFilter.SECTION_NOTE)) searchNotes(q, filter) else emptyList()
            // 本 Job 已被更新的 runSearch 取消时不再写回旧结果
            currentCoroutineContext().ensureActive()
            _search.value = _search.value.copy(loading = false, books = books, chapterHits = chapters, notes = notes)
        }
        startBodyScan(q, filter, bodyDebounceMs)
    }

    private fun startBodyScan(q: String, filter: SearchFilter, debounceMs: Long) {
        bodyScanJob?.cancel()
        // 正文分区被筛选关掉：不起扫描（runSearch 已把 bodyGroups/bodyTotal 复位）。
        if (q.length < 2 || filter.type == "COMIC" || !filter.sectionOn(SearchFilter.SECTION_BODY)) return
        bodyScanJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            currentCoroutineContext().ensureActive()
            val bodyBooks = searchBooks(q, filter).filter { it.type == "NOVEL" }
            _search.value = _search.value.copy(
                bodyScanned = 0,
                bodyTotal = bodyBooks.size,
                bodyDone = false,
                bodyBookLimitedIds = emptySet(),
                bodyGlobalLimited = false,
            )
            searchNovelFullText(bodyBooks, q).collect { event ->
                when (event) {
                    is FullTextEvent.BookBatch -> _search.value = _search.value.copy(
                        bodyGroups = _search.value.bodyGroups + FullTextBookGroup(event.book, event.hits),
                    )

                    is FullTextEvent.Progress -> _search.value = _search.value.copy(
                        bodyScanned = event.scanned,
                        bodyTotal = event.total,
                        bodyCurrentTitle = event.currentTitle,
                    )

                    is FullTextEvent.Done -> _search.value = _search.value.copy(
                        bodyDone = true,
                        bodyBookLimitedIds = event.bookLimitedIds,
                        bodyGlobalLimited = event.globalLimited,
                    )
                }
            }
        }
    }

    /** 手动停止正文扫描（保留已聚合结果）。 */
    fun stopBodyScan() {
        bodyScanJob?.cancel()
        bodyScanJob = null
        _search.value = _search.value.copy(bodyDone = true, bodyScanned = 0, bodyTotal = 0)
    }

    /** 从结果态退回初始态：清结果不清历史/筛选/模板（SSMH clearSearch 同语义）。 */
    fun clearSearch() {
        searchJob?.cancel()
        bodyScanJob?.cancel()
        _search.value = _search.value.copy(
            query = "",
            searched = false,
            loading = false,
            books = emptyList(),
            notes = emptyList(),
            chapterHits = emptyList(),
            bodyGroups = emptyList(),
            bodyScanned = 0,
            bodyTotal = 0,
            bodyCurrentTitle = "",
            bodyDone = true,
            bodyBookLimitedIds = emptySet(),
            bodyGlobalLimited = false,
        )
    }

    /** 筛选变化：持久化 + 有关键词时立即重跑（正文 0ms 立即重启，范围变了）。 */
    fun setSearchFilter(filter: SearchFilter) {
        _search.value = _search.value.copy(filter = filter, activeTemplateId = null)
        scope.launch {
            runCatching { settingsRepository.putString(SettingsKeys.SEARCH_FILTER, SearchFilterCodec.encodeFilter(filter)) }
        }
        if (_search.value.query.isNotEmpty()) runSearch(_search.value.query, bodyDebounceMs = 0)
    }

    private fun pushHistory(q: String) {
        val next = (listOf(q) + _search.value.history.filterNot { it == q }).take(30)
        _search.value = _search.value.copy(history = next)
        scope.launch {
            runCatching { settingsRepository.putString(SettingsKeys.SEARCH_HISTORY, SearchFilterCodec.encodeStrings(next)) }
        }
    }

    fun clearSearchHistory() {
        _search.value = _search.value.copy(history = emptyList())
        scope.launch {
            runCatching { settingsRepository.putString(SettingsKeys.SEARCH_HISTORY, SearchFilterCodec.encodeStrings(emptyList())) }
        }
    }

    /** 保存模板；达上限返回 false（由 UI 提示）。 */
    fun saveSearchTemplate(name: String, filter: SearchFilter): Boolean {
        val current = _search.value.templates
        if (current.size >= SearchFilterCodec.LIMIT) return false
        val item = SearchFilterTemplate(
            id = java.util.UUID.randomUUID().toString(),
            name = uniqueTemplateName(name.trim().ifBlank { "未命名模板" }, current),
            filter = filter,
            createdAt = System.currentTimeMillis(),
        )
        val next = listOf(item) + current
        _search.value = _search.value.copy(templates = next)
        persistTemplates(next)
        return true
    }

    fun renameSearchTemplate(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val others = _search.value.templates.filterNot { it.id == id }
        val next = _search.value.templates.map { template ->
            if (template.id == id) template.copy(name = uniqueTemplateName(trimmed, others)) else template
        }
        _search.value = _search.value.copy(templates = next)
        persistTemplates(next)
    }

    fun deleteSearchTemplate(id: String) {
        val next = _search.value.templates.filterNot { it.id == id }
        val activeGone = _search.value.activeTemplateId == id
        _search.value = _search.value.copy(templates = next, activeTemplateId = if (activeGone) null else _search.value.activeTemplateId)
        persistTemplates(next)
    }

    /** 应用模板：更新最近使用并置顶，回写筛选记忆并立即重跑。 */
    fun applySearchTemplate(id: String): SearchFilter? {
        val target = _search.value.templates.firstOrNull { it.id == id } ?: return null
        val updated = target.copy(lastUsedAt = System.currentTimeMillis())
        val next = listOf(updated) + _search.value.templates.filterNot { it.id == id }
        _search.value = _search.value.copy(templates = next, filter = target.filter, activeTemplateId = id)
        persistTemplates(next)
        scope.launch {
            runCatching { settingsRepository.putString(SettingsKeys.SEARCH_FILTER, SearchFilterCodec.encodeFilter(target.filter)) }
        }
        if (_search.value.query.isNotEmpty()) runSearch(_search.value.query, bodyDebounceMs = 0)
        return target.filter
    }

    private fun persistTemplates(list: List<SearchFilterTemplate>) {
        scope.launch {
            runCatching { settingsRepository.putString(SettingsKeys.SEARCH_FILTER_TEMPLATES, SearchFilterCodec.encodeList(list)) }
        }
    }

    private fun uniqueTemplateName(base: String, existing: List<SearchFilterTemplate>): String {
        if (existing.none { it.name == base }) return base
        var index = 2
        while (existing.any { it.name == "$base $index" }) index++
        return "$base $index"
    }

    private suspend fun restoreSearchSession() {
        runCatching {
            val prefs = settingsRepository.snapshotPreferences()
            _search.value = _search.value.copy(
                history = SearchFilterCodec.decodeStrings(
                    settingsRepository.stringOf(prefs, SettingsKeys.SEARCH_HISTORY, ""),
                ),
                filter = SearchFilterCodec.decodeFilter(
                    settingsRepository.stringOf(prefs, SettingsKeys.SEARCH_FILTER, ""),
                ) ?: SearchFilter(),
                templates = SearchFilterCodec.decodeList(
                    settingsRepository.stringOf(prefs, SettingsKeys.SEARCH_FILTER_TEMPLATES, ""),
                ),
            )
        }
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
            syncOnProgressSaved?.invoke()
        }
    }

    suspend fun progressPage(bookId: String): Int = withContext(Dispatchers.IO) {
        db.queryProgressPage(bookId)
    }

    fun saveProgress(bookId: String, page: Int, percent: Double) {
        scope.launch {
            db.saveProgress(bookId, page, percent)
            syncOnProgressSaved?.invoke()
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
            // 持久化写权限：单文件导入是引用式，日后「删除源文件」要直接 deleteDocument，
            // 只拿读权限重启后必失败；部分 Provider 不授写权，降级只拿读权限，不阻塞导入。
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.recoverCatching {
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
            val depth = scanDepth()
            var added = 0
            val failedNames = ArrayList<String>()
            scanMutex.withLock {
                folders.forEach { folder ->
                    runCatching {
                        val result = scanner.scan(
                            Uri.parse(folder.treeUri),
                            folder.libraryId,
                            folder.id,
                            depthLimit = depth,
                        ) { count, current ->
                            _scan.value = ScanState(running = true, scanned = count, current = current)
                        }
                        added += result.added
                    }.onFailure {
                        failedNames += folder.name
                        Log.w("InkvolScan", "rescan failed: ${folder.treeUri}", it)
                    }
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

            val depth = scanDepth()
            val result = scanMutex.withLock {
                scanner.scan(uri, libraryId, folderId, depthLimit = depth) { count, current ->
                    _scan.value = ScanState(
                        running = true,
                        scanned = count,
                        added = count,
                        current = current,
                    )
                }
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
     * 删除书本。deleteFiles=true 时尝试删除源文件（文件夹内文件 / 单文件导入引用 / 同步下载文件）。
     * 删除失败（无写入权限 / Provider 不支持）降级为仅从书架移除。
     */
    suspend fun deleteBooks(ids: Collection<String>, deleteFiles: Boolean): DeleteResult =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext DeleteResult(0, 0, 0)
            var filesDeleted = 0
            var filesFailed = 0
            if (deleteFiles) {
                ids.forEach { id ->
                    db.queryBookLocations(id).forEach { loc ->
                        when (deleteSourceFile(loc)) {
                            true -> filesDeleted++
                            false -> filesFailed++
                            null -> Unit
                        }
                    }
                }
            }
            val covers = db.deleteBooks(ids)
            cleanupBookFiles(ids, covers)
            syncOnBooksPurged?.invoke(ids)
            reload()
            _notice.value = when {
                deleteFiles && filesFailed == 0 && filesDeleted > 0 -> "已删除 ${ids.size} 本（含源文件）"
                deleteFiles && filesFailed > 0 -> "已删除 ${ids.size} 本，$filesFailed 个源文件未能删除（无写入权限或不支持）"
                else -> "已从书架移除 ${ids.size} 本"
            }
            DeleteResult(removed = ids.size, filesDeleted = filesDeleted, filesFailed = filesFailed)
        }

    /**
     * 删除一处源文件。按 doc_id 形态分三支（单文件导入原先被整体跳过，导致永远删不掉）：
     * - content:// 完整 document URI（单文件 SAF 导入）：直接 deleteDocument——不能再过
     *   buildDocumentUriUsingTree，它的 tree 是虚拟串 inkvol://single-file，拼出来是非法的；
     * - file://（M4.5 同步下载到 filesDir/library）：App 私有文件，直接删；
     * - 其它（文件夹扫描导入的树内 child docId）：维持 buildDocumentUriUsingTree。
     * 返回 null 表示无可用信息（老数据 tree_uri 缺失等），不计成功也不计失败。
     */
    private fun deleteSourceFile(loc: InkvolDatabase.BookLocation): Boolean? {
        val docId = loc.docId
        if (docId.isBlank()) return null
        return try {
            when {
                docId.startsWith("content://") -> {
                    val docUri = Uri.parse(docId)
                    val ok = DocumentsContract.deleteDocument(context.contentResolver, docUri)
                    if (ok) releaseSourcePermission(docUri)
                    ok
                }
                docId.startsWith("file://") -> {
                    val path = Uri.parse(docId).path
                    if (path.isNullOrBlank()) {
                        null
                    } else {
                        File(path).delete()
                    }
                }
                loc.treeUri.isNotBlank() && loc.treeUri != SingleFileImporter.SINGLE_LIBRARY_URI -> {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(
                        Uri.parse(loc.treeUri),
                        docId,
                    )
                    DocumentsContract.deleteDocument(context.contentResolver, docUri)
                }
                else -> null
            }
        } catch (error: Throwable) {
            Log.w("InkvolDelete", "delete source failed: $docId", error)
            false
        }
    }

    /** 源文件已删：收回持久化授权，避免 grant 累积到系统上限（失败无妨）。 */
    private fun releaseSourcePermission(uri: Uri) {
        val resolver = context.contentResolver
        runCatching {
            resolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.recoverCatching {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
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
            idSet.any { id -> file.name == id || file.name.startsWith("$id.") }
        }?.forEach { file ->
            runCatching {
                // 目录 = 嵌套归档（P2）的子包解包目录，递归清
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
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
            val purged = db.purgeExpiredTrash()
            if (purged.isNotEmpty()) syncOnBooksPurged?.invoke(purged)
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
