package com.inkvol.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.model.CategoryRecord
import com.inkvol.app.data.model.LibraryRecord
import com.inkvol.app.data.model.NovelChapterRecord
import com.inkvol.app.data.model.AnnotationRecord
import com.inkvol.app.data.model.BookmarkRecord
import com.inkvol.app.data.model.NovelProgressRecord
import com.inkvol.app.data.model.PurifyRuleRecord
import com.inkvol.app.data.model.ReadingHistoryEntry
import com.inkvol.app.data.model.ReadingStats
import com.inkvol.app.data.model.SearchChapterRow
import com.inkvol.app.data.model.TrashBook

/**
 * M2 数据库：
 * - books / book_locations / libraries / folders：导入与书架；
 * - categories / book_categories：全局书夹（多对多）；
 * - progress / page_bookmarks：为 M3 阅读器预留，M2 只读 percent。
 */
class InkvolDatabase(context: Context) : SQLiteOpenHelper(context, "inkvol.db", null, 11) {

    override fun onCreate(db: SQLiteDatabase) {
        createCoreTables(db)
        createM2Tables(db)
        createM4BTables(db)
        createM6Tables(db)
        createM63BTables(db)
        createSyncTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createM2Tables(db)
        }
        if (oldVersion < 4) {
            createM4BTables(db)
        }
        if (oldVersion < 5) {
            runCatching {
                db.execSQL("ALTER TABLE books ADD COLUMN deleted_at INTEGER")
                db.execSQL("CREATE INDEX idx_books_deleted ON books(deleted_at)")
            }
        }
        if (oldVersion < 6) {
            createM6Tables(db)
        }
        if (oldVersion < 7) {
            createNovelProgressTable(db)
        }
        if (oldVersion < 8) {
            createM63Tables(db)
        }
        if (oldVersion < 9) {
            createM63BTables(db)
        }
        // v10：书单功能合并进书夹，删除书单表（数据为空表，无需迁移）
        if (oldVersion < 10) {
            db.execSQL("DROP TABLE IF EXISTS book_list_items")
            db.execSQL("DROP TABLE IF EXISTS book_lists")
        }
        // v11：M4.5 同步墓碑表（删除传播凭据）
        if (oldVersion < 11) {
            createSyncTables(db)
        }
    }

    /** M4.5：同步墓碑表（跨设备删除传播；kind+key 唯一）。 */
    private fun createSyncTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_tombstones(
                kind TEXT NOT NULL,
                key TEXT NOT NULL,
                deleted_at INTEGER NOT NULL,
                PRIMARY KEY(kind, key)
            )
            """.trimIndent(),
        )
    }

    private fun createCoreTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE libraries(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL DEFAULT 'MIXED',
                root_tree_uri TEXT NOT NULL UNIQUE,
                scan_depth INTEGER NOT NULL DEFAULT 3,
                hidden INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                sort_key INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE folders(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                library_id INTEGER NOT NULL,
                tree_uri TEXT NOT NULL,
                display_name TEXT,
                last_scan_at INTEGER,
                enabled INTEGER NOT NULL DEFAULT 1,
                offline INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE books(
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                series TEXT,
                volume TEXT,
                writer TEXT,
                tags TEXT,
                page_count INTEGER NOT NULL,
                format TEXT NOT NULL,
                file_size INTEGER NOT NULL,
                cover_path TEXT,
                added_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                read_status TEXT NOT NULL DEFAULT 'UNREAD',
                deleted_at INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_books_deleted ON books(deleted_at)")
        db.execSQL(
            """
            CREATE TABLE book_locations(
                book_id TEXT NOT NULL,
                folder_id INTEGER NOT NULL,
                doc_id TEXT NOT NULL,
                display_path TEXT NOT NULL,
                last_seen_at INTEGER NOT NULL,
                is_primary INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(book_id, doc_id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_books_added ON books(added_at DESC)")
        db.execSQL("CREATE INDEX idx_locations_book ON book_locations(book_id)")
    }

    private fun createM2Tables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE categories(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                color TEXT NOT NULL,
                sort_key INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE book_categories(
                book_id TEXT NOT NULL,
                category_id INTEGER NOT NULL,
                added_at INTEGER NOT NULL,
                PRIMARY KEY(book_id, category_id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_book_categories_cat ON book_categories(category_id)")
        db.execSQL(
            """
            CREATE TABLE progress(
                book_id TEXT PRIMARY KEY,
                page INTEGER NOT NULL DEFAULT 0,
                percent REAL NOT NULL DEFAULT 0,
                finished INTEGER NOT NULL DEFAULT 0,
                session_seconds INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE page_bookmarks(
                book_id TEXT NOT NULL,
                page_index INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                note TEXT,
                PRIMARY KEY(book_id, page_index)
            )
            """.trimIndent(),
        )
        // v1 -> v2 升级时补列（新库由 CREATE TABLE 已包含）
        runCatching {
            db.execSQL("ALTER TABLE books ADD COLUMN read_status TEXT NOT NULL DEFAULT 'UNREAD'")
        }
    }

    private fun createM4BTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE reading_sessions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                book_id TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                pages_read INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_reading_sessions_book ON reading_sessions(book_id, started_at)")
        db.execSQL("CREATE INDEX idx_reading_sessions_started ON reading_sessions(started_at)")
    }

    private fun createNovelProgressTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS novel_progress(
                book_id TEXT PRIMARY KEY,
                chapter_idx INTEGER NOT NULL,
                char_offset INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createM6Tables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE novel_chapters(
                book_id TEXT NOT NULL,
                idx INTEGER NOT NULL,
                title TEXT NOT NULL,
                byte_start INTEGER NOT NULL,
                byte_end INTEGER NOT NULL,
                char_count INTEGER NOT NULL,
                PRIMARY KEY(book_id, idx)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_novel_chapters_book ON novel_chapters(book_id, idx)")
        createNovelProgressTable(db)
        createM63Tables(db)
    }

    /** M6.3-A：标注 / 净化规则 / 阅读统计。 */
    private fun createM63Tables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS annotations(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                book_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                chapter_idx INTEGER NOT NULL,
                char_start INTEGER NOT NULL,
                char_end INTEGER NOT NULL,
                snippet TEXT NOT NULL,
                note TEXT,
                color TEXT NOT NULL DEFAULT 'yellow',
                style TEXT NOT NULL DEFAULT 'bg',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_annotations_book ON annotations(book_id, chapter_idx)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purify_rules(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                pattern TEXT NOT NULL,
                builtin INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS novel_purify(
                book_id TEXT PRIMARY KEY,
                enabled INTEGER NOT NULL DEFAULT 1,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        // reading_sessions 复用 v1 已有表（started_at/ended_at/duration_ms/pages_read）
        // 内置净化规则：常见站点水印 / 广告行
        listOf(
            "本站广告" to "\\s*本章未完.*点击下一页.*",
            "站点水印" to "\\s*.*(最新章节|首发于|www\\.|\\.com|\\.net|\\.cc).{0,40}",
            "推广行" to "\\s*[(（]?[①②③④⑤⑥⑦⑧⑨⑩].*[)）]?\\s*",
        ).forEach { (name, pattern) ->
            db.execSQL(
                "INSERT OR IGNORE INTO purify_rules(name, pattern, builtin, enabled, created_at) VALUES(?, ?, 1, 1, ?)",
                arrayOf<String>(name, pattern, System.currentTimeMillis().toString()),
            )
        }
    }

    /** M6.3-B：书签。char_start 章内锚点（与进度锚定同语义），UNIQUE 保证同位置幂等。 */
    private fun createM63BTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bookmarks(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                book_id TEXT NOT NULL,
                chapter_idx INTEGER NOT NULL,
                char_start INTEGER NOT NULL,
                snippet TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                UNIQUE(book_id, chapter_idx, char_start)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_bookmarks_book ON bookmarks(book_id, chapter_idx)",
        )
    }

    // ---------------- 导入 ----------------

    fun findOrCreateLibrary(treeUri: String, displayName: String): Long {
        readableDatabase.rawQuery(
            "SELECT id FROM libraries WHERE root_tree_uri = ?",
            arrayOf(treeUri),
        ).use { cursor -> if (cursor.moveToFirst()) return cursor.getLong(0) }
        val values = ContentValues().apply {
            put("name", displayName)
            put("type", "MIXED")
            put("root_tree_uri", treeUri)
            put("scan_depth", 3)
            put("hidden", 0)
            put("created_at", System.currentTimeMillis())
            put("sort_key", 0)
        }
        return writableDatabase.insert("libraries", null, values)
    }

    fun findOrCreateFolder(libraryId: Long, treeUri: String, displayName: String): Long {
        readableDatabase.rawQuery(
            "SELECT id FROM folders WHERE tree_uri = ?",
            arrayOf(treeUri),
        ).use { cursor -> if (cursor.moveToFirst()) return cursor.getLong(0) }
        val values = ContentValues().apply {
            put("library_id", libraryId)
            put("tree_uri", treeUri)
            put("display_name", displayName)
            put("last_scan_at", 0L)
            put("enabled", 1)
            put("offline", 0)
        }
        return writableDatabase.insert("folders", null, values)
    }

    fun markFolderScanned(folderId: Long) {
        writableDatabase.update(
            "folders",
            ContentValues().apply {
                put("last_scan_at", System.currentTimeMillis())
                put("offline", 0)
            },
            "id = ?",
            arrayOf(folderId.toString()),
        )
    }

    /** 书本以内容指纹为主键：已存在时只更新可变量，不重置 added_at。 */
    fun upsertBook(book: BookRecord, folderId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existingAddedAt = db.rawQuery(
                "SELECT added_at FROM books WHERE id = ?",
                arrayOf(book.id),
            ).use { if (it.moveToFirst()) it.getLong(0) else book.addedAt }
            val exists = existingAddedAt > 0L
            val values = ContentValues().apply {
                put("id", book.id)
                put("type", book.type)
                put("title", book.title)
                put("series", book.series)
                put("volume", book.volume)
                put("writer", book.writer)
                put("tags", book.tags)
                put("page_count", book.pageCount)
                put("format", book.format)
                put("file_size", book.fileSize)
                put("cover_path", book.coverPath)
                put("added_at", existingAddedAt)
                put("updated_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("books", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            val location = ContentValues().apply {
                put("book_id", book.id)
                put("folder_id", folderId)
                put("doc_id", book.docId)
                put("display_path", book.displayPath)
                put("last_seen_at", System.currentTimeMillis())
                put("is_primary", 1)
            }
            db.insertWithOnConflict("book_locations", null, location, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun hasBook(id: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM books WHERE id = ?", arrayOf(id)).use { it.moveToFirst() }

    /**
     * 扫描「再次见到」已存在的书时刷新可见时间戳。
     * 已存在的书走 skip 分支不会调用 upsertBook，last_seen_at 若停留在上次扫描时刻，
     * 会被 removeMissingLocations 的 last_seen_at < scanStart 对账误判为消失并级联删除。
     */
    fun touchBookLocation(bookId: String, folderId: Long) {
        writableDatabase.update(
            "book_locations",
            ContentValues().apply { put("last_seen_at", System.currentTimeMillis()) },
            "book_id = ? AND folder_id = ?",
            arrayOf(bookId, folderId.toString()),
        )
    }

    fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("book_categories", null, null)
            db.delete("categories", null, null)
            db.delete("reading_sessions", null, null)
            db.delete("progress", null, null)
            db.delete("page_bookmarks", null, null)
            db.delete("novel_chapters", null, null)
            db.delete("novel_progress", null, null)
            db.delete("annotations", null, null)
            db.delete("reading_sessions", null, null)
            db.delete("novel_purify", null, null)
            db.delete("book_locations", null, null)
            db.delete("books", null, null)
            db.delete("folders", null, null)
            db.delete("libraries", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------------- 删除 ----------------

    data class BookLocation(
        val folderId: Long,
        val docId: String,
        val treeUri: String,
    )

    /** 按 book_id 查所有物理位置（含各位置所属 folder 的 tree_uri），供「连文件删除」。 */
    fun queryBookLocations(bookId: String): List<BookLocation> {
        val result = ArrayList<BookLocation>()
        readableDatabase.rawQuery(
            """
            SELECT l.folder_id, l.doc_id, COALESCE(f.tree_uri, '')
            FROM book_locations l
            LEFT JOIN folders f ON f.id = l.folder_id
            WHERE l.book_id = ?
            """.trimIndent(),
            arrayOf(bookId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += BookLocation(
                    folderId = cursor.getLong(0),
                    docId = cursor.getString(1),
                    treeUri = cursor.getString(2),
                )
            }
        }
        return result
    }

    /**
     * 级联删除书本：删所有 location、分类关系、进度、书签、主记录。
     * 返回被删书本的封面路径（非 null），供调用方清理封面文件。
     */
    fun deleteBooks(bookIds: Collection<String>): List<String> {
        if (bookIds.isEmpty()) return emptyList()
        val db = writableDatabase
        val covers = ArrayList<String>()
        db.beginTransaction()
        try {
            bookIds.forEach { bookId ->
                db.rawQuery("SELECT cover_path FROM books WHERE id = ?", arrayOf(bookId)).use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) {
                        cursor.getString(0)?.let { covers += it }
                    }
                }
                db.delete("book_locations", "book_id = ?", arrayOf(bookId))
                deleteBookCascade(db, bookId)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return covers
    }

    /**
     * 对账：删除某文件夹下「扫描期间未再出现」的 location（last_seen_at < seenBefore）。
     * 若某本书删完该 location 后不再有其它位置，则级联删除主记录。
     * 返回被级联删除书本的封面路径（非 null）。
     */
    fun removeMissingLocations(folderId: Long, seenBefore: Long): List<String> {
        val db = writableDatabase
        val covers = ArrayList<String>()
        val stale = ArrayList<String>()
        db.beginTransaction()
        try {
            db.rawQuery(
                "SELECT book_id FROM book_locations WHERE folder_id = ? AND last_seen_at < ?",
                arrayOf(folderId.toString(), seenBefore.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) stale += cursor.getString(0)
            }
            db.delete(
                "book_locations",
                "folder_id = ? AND last_seen_at < ?",
                arrayOf(folderId.toString(), seenBefore.toString()),
            )
            stale.forEach { bookId ->
                val stillExists = db.rawQuery(
                    "SELECT 1 FROM book_locations WHERE book_id = ?",
                    arrayOf(bookId),
                ).use { it.moveToFirst() }
                if (!stillExists) {
                    db.rawQuery("SELECT cover_path FROM books WHERE id = ?", arrayOf(bookId)).use { cursor ->
                        if (cursor.moveToFirst() && !cursor.isNull(0)) {
                            cursor.getString(0)?.let { covers += it }
                        }
                    }
                    deleteBookCascade(db, bookId)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return covers
    }

    private fun deleteBookCascade(db: SQLiteDatabase, bookId: String) {
        db.delete("book_categories", "book_id = ?", arrayOf(bookId))
        db.delete("reading_sessions", "book_id = ?", arrayOf(bookId))
        db.delete("progress", "book_id = ?", arrayOf(bookId))
        db.delete("page_bookmarks", "book_id = ?", arrayOf(bookId))
        db.delete("novel_chapters", "book_id = ?", arrayOf(bookId))
        db.delete("novel_progress", "book_id = ?", arrayOf(bookId))
        db.delete("annotations", "book_id = ?", arrayOf(bookId))
        db.delete("novel_purify", "book_id = ?", arrayOf(bookId))
        db.delete("books", "id = ?", arrayOf(bookId))
    }

    data class FolderRemoval(val bookIds: List<String>, val covers: List<String>)

    /**
     * 移除文件夹（书库管理「移除」）：删除该文件夹下全部位置，失去所有位置的书级联删除主记录，
     * 并删除 folders 行本身（此后 queryFolders 不再返回，前台/后台扫描自动跳过）。
     * 与 removeMissingLocations 的区别：不按 last_seen_at 过滤（全部删除）。
     * 返回被级联删除书本的 id 与封面路径，供调用方清理封面/分格缓存/TXT 正文等文件。
     */
    fun removeFolder(folderId: Long): FolderRemoval {
        val db = writableDatabase
        val covers = ArrayList<String>()
        val removed = ArrayList<String>()
        db.beginTransaction()
        try {
            db.rawQuery(
                "SELECT DISTINCT book_id FROM book_locations WHERE folder_id = ?",
                arrayOf(folderId.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) removed += cursor.getString(0)
            }
            db.delete("book_locations", "folder_id = ?", arrayOf(folderId.toString()))
            removed.forEach { bookId ->
                val stillExists = db.rawQuery(
                    "SELECT 1 FROM book_locations WHERE book_id = ?",
                    arrayOf(bookId),
                ).use { it.moveToFirst() }
                if (!stillExists) {
                    db.rawQuery("SELECT cover_path FROM books WHERE id = ?", arrayOf(bookId)).use { cursor ->
                        if (cursor.moveToFirst() && !cursor.isNull(0)) {
                            cursor.getString(0)?.let { covers += it }
                        }
                    }
                    deleteBookCascade(db, bookId)
                }
            }
            db.delete("folders", "id = ?", arrayOf(folderId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return FolderRemoval(removed, covers)
    }

    data class FolderRow(
        val id: Long,
        val libraryId: Long,
        val treeUri: String,
        val name: String,
    )

    fun queryFolders(): List<FolderRow> {
        val result = ArrayList<FolderRow>()
        readableDatabase.rawQuery(
            "SELECT id, library_id, tree_uri, display_name FROM folders WHERE enabled = 1",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += FolderRow(
                    cursor.getLong(0),
                    cursor.getLong(1),
                    cursor.getString(2),
                    cursor.getString(3) ?: "",
                )
            }
        }
        return result
    }

    /** 文件夹行是否仍存在（ScanWorker 快照循环中防「已移除文件夹」复活位置）。 */
    fun hasFolder(id: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM folders WHERE id = ?",
            arrayOf(id.toString()),
        ).use { it.moveToFirst() }

    // ---------------- 书架查询 ----------------

    private val bookSelect = """
        SELECT b.id, b.type, b.title, b.series, b.volume, b.writer, b.tags,
               b.page_count, b.format, b.file_size, b.cover_path, b.added_at,
               l.doc_id, l.display_path, b.read_status, COALESCE(p.percent, 0), f.tree_uri,
               COALESCE(
                   p.updated_at,
                   (SELECT MAX(updated_at) FROM novel_progress WHERE book_id = b.id),
                   0
               )
        FROM books b
        LEFT JOIN book_locations l ON l.rowid = (
            SELECT rowid FROM book_locations
            WHERE book_id = b.id AND is_primary = 1
            ORDER BY last_seen_at DESC
            LIMIT 1
        )
        LEFT JOIN folders f ON f.id = l.folder_id
        LEFT JOIN libraries lib ON lib.id = f.library_id
        LEFT JOIN progress p ON p.book_id = b.id
    """.trimIndent()

    private val visibleWhere = "(lib.hidden IS NULL OR lib.hidden = 0) AND b.deleted_at IS NULL"

    fun queryBooks(): List<BookRecord> = rawBooks(
        "$bookSelect WHERE $visibleWhere ORDER BY b.added_at DESC",
        null,
    )

    fun queryBook(id: String): BookRecord? = rawBooks(
        "$bookSelect WHERE b.id = ? AND b.deleted_at IS NULL",
        arrayOf(id),
    ).firstOrNull()

    fun queryProgressPage(bookId: String): Int =
        readableDatabase.rawQuery(
            "SELECT page FROM progress WHERE book_id = ?",
            arrayOf(bookId),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun saveProgress(bookId: String, page: Int, percent: Double) {
        val values = ContentValues().apply {
            put("book_id", bookId)
            put("page", page)
            put("percent", percent)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            "progress",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        writableDatabase.update(
            "books",
            ContentValues().apply {
                put("read_status", if (percent >= 0.99) BookRecord.READ_FINISHED else BookRecord.READ_READING)
                put("updated_at", System.currentTimeMillis())
            },
            "id = ?",
            arrayOf(bookId),
        )
    }

    fun queryBooksInCategory(categoryId: Long): List<BookRecord> = rawBooks(
        """
        $bookSelect
        JOIN book_categories bc ON bc.book_id = b.id AND bc.category_id = ?
        WHERE $visibleWhere
        ORDER BY b.added_at DESC
        """.trimIndent(),
        arrayOf(categoryId.toString()),
    )

    fun queryUnclassifiedBooks(): List<BookRecord> = rawBooks(
        """
        $bookSelect
        WHERE $visibleWhere
          AND NOT EXISTS (SELECT 1 FROM book_categories bc WHERE bc.book_id = b.id)
        ORDER BY b.added_at DESC
        """.trimIndent(),
        null,
    )

    private fun rawBooks(sql: String, args: Array<String>?): List<BookRecord> {
        val result = ArrayList<BookRecord>()
        readableDatabase.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                result += BookRecord(
                    id = cursor.getString(0),
                    type = cursor.getString(1),
                    title = cursor.getString(2),
                    series = cursor.getString(3),
                    volume = cursor.getString(4),
                    writer = cursor.getString(5),
                    tags = cursor.getString(6),
                    pageCount = cursor.getInt(7),
                    format = cursor.getString(8),
                    fileSize = cursor.getLong(9),
                    coverPath = cursor.getString(10),
                    addedAt = cursor.getLong(11),
                    docId = cursor.getString(12) ?: "",
                    displayPath = cursor.getString(13) ?: "",
                    readStatus = cursor.getString(14) ?: BookRecord.READ_UNREAD,
                    percent = cursor.getDouble(15),
                    lastReadAt = cursor.getLong(17),
                    treeUri = cursor.getString(16),
                )
            }
        }
        // 兜底去重：位置一对多残留（如历史脏数据）不再让同一本书出现两行，
        // 否则书架 LazyGrid 以 id 作 key 会重复崩溃。
        return result.distinctBy { it.id }
    }

    // ---------------- 小说章节（M6.1-A） ----------------

    fun replaceNovelChapters(bookId: String, chapters: List<NovelChapterRecord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("novel_chapters", "book_id = ?", arrayOf(bookId))
            chapters.forEach { chapter ->
                db.insertWithOnConflict(
                    "novel_chapters",
                    null,
                    ContentValues().apply {
                        put("book_id", bookId)
                        put("idx", chapter.index)
                        put("title", chapter.title)
                        put("byte_start", chapter.byteStart)
                        put("byte_end", chapter.byteEnd)
                        put("char_count", chapter.charCount)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun queryNovelChapters(bookId: String): List<NovelChapterRecord> {
        val result = ArrayList<NovelChapterRecord>()
        readableDatabase.rawQuery(
            """
            SELECT idx, title, byte_start, byte_end, char_count
            FROM novel_chapters
            WHERE book_id = ?
            ORDER BY idx
            """.trimIndent(),
            arrayOf(bookId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += NovelChapterRecord(
                    index = cursor.getInt(0),
                    title = cursor.getString(1),
                    byteStart = cursor.getLong(2),
                    byteEnd = cursor.getLong(3),
                    charCount = cursor.getInt(4),
                )
            }
        }
        return result
    }

    // ---------------- M7 全局搜索 ----------------

    /**
     * 章节标题命中：LIKE + 书籍级基础筛选（type/format/readStatus）。
     * 书夹/文件夹/系列/作者/标签筛选在仓储层做（需要映射与逗号拆分）。
     * JOIN 口径对齐 bookSelect：主位置 rowid 子查询去重 + 隐藏书库过滤。
     */
    fun querySearchChapters(
        query: String,
        type: String?,
        formats: Set<String>,
        readStatus: String?,
    ): List<SearchChapterRow> {
        val like = "%" + escapeLike(query) + "%"
        val clauses = ArrayList<String>()
        val args = ArrayList<String>()
        clauses.add("nc.title LIKE ? ESCAPE '\\'")
        args.add(like)
        if (type != null) {
            clauses.add("b.type = ?")
            args.add(type)
        }
        if (readStatus != null) {
            clauses.add("b.read_status = ?")
            args.add(readStatus)
        }
        if (formats.isNotEmpty()) {
            clauses.add("b.format IN (${formats.joinToString(",") { "?" }})")
            args.addAll(formats)
        }
        val sql = """
            SELECT b.id, b.title, b.cover_path, b.type, nc.idx, nc.title
            FROM novel_chapters nc
            JOIN books b ON b.id = nc.book_id AND b.deleted_at IS NULL
            LEFT JOIN folders f ON f.id = (
                SELECT folder_id FROM book_locations
                WHERE book_id = b.id
                ORDER BY last_seen_at DESC
                LIMIT 1
            )
            LEFT JOIN libraries lib ON lib.id = f.library_id
            WHERE (lib.hidden IS NULL OR lib.hidden = 0) AND ${clauses.joinToString(" AND ")}
            ORDER BY b.added_at DESC, nc.idx
        """.trimIndent()
        val result = ArrayList<SearchChapterRow>()
        readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                result += SearchChapterRow(
                    bookId = cursor.getString(0),
                    bookTitle = cursor.getString(1),
                    coverPath = cursor.getString(2),
                    bookType = cursor.getString(3),
                    chapterIdx = cursor.getInt(4),
                    chapterTitle = cursor.getString(5) ?: "",
                )
            }
        }
        return result
    }

    /** LIKE 通配符转义（配合 ESCAPE '\'）。 */
    private fun escapeLike(raw: String): String = raw
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    /** 书 → 所在文件夹 id 集（文件夹三态筛选用）。 */
    fun queryBookFolderIds(): Map<String, Set<Long>> {
        val map = HashMap<String, MutableSet<Long>>()
        readableDatabase.rawQuery(
            "SELECT book_id, folder_id FROM book_locations",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                map.getOrPut(cursor.getString(0)) { mutableSetOf() }.add(cursor.getLong(1))
            }
        }
        return map
    }

    /** 搜索期更新书目元数据（EPUB 回填）；null 表示不动该列。 */
    fun updateBookMetadata(bookId: String, writer: String?, tags: String?) {
        val values = ContentValues()
        if (writer != null) values.put("writer", writer)
        if (tags != null) values.put("tags", tags)
        if (values.size() == 0) return
        values.put("updated_at", System.currentTimeMillis())
        writableDatabase.update("books", values, "id = ?", arrayOf(bookId))
    }

    data class EpubBackfillRow(
        val id: String,
        val docId: String,
        val fileSize: Long,
    )

    /**
     * EPUB 元数据回填候选：该文件夹下 writer 与 tags 都为空的 EPUB。
     * 哨兵约定：解析后无任何元数据的书写 tags=''（非 NULL），下次不再命中。
     */
    fun queryEpubBackfillCandidates(folderId: Long): List<EpubBackfillRow> {
        val result = ArrayList<EpubBackfillRow>()
        readableDatabase.rawQuery(
            """
            SELECT DISTINCT b.id, l.doc_id, b.file_size
            FROM books b
            JOIN book_locations l ON l.book_id = b.id
            WHERE l.folder_id = ? AND b.type = 'NOVEL' AND b.format = 'EPUB'
              AND b.writer IS NULL AND b.tags IS NULL AND b.deleted_at IS NULL
            """.trimIndent(),
            arrayOf(folderId.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += EpubBackfillRow(
                    id = cursor.getString(0),
                    docId = cursor.getString(1),
                    fileSize = cursor.getLong(2),
                )
            }
        }
        return result
    }

    /** M6.3-A：标注查询（可限定章节）。 */
    fun queryAnnotations(bookId: String, chapterIdx: Int? = null): List<AnnotationRecord> {
        val sql = if (chapterIdx != null) {
            "SELECT id, book_id, kind, chapter_idx, char_start, char_end, snippet, note, color, style, created_at, updated_at " +
                "FROM annotations WHERE book_id = ? AND chapter_idx = ? ORDER BY char_start"
        } else {
            "SELECT id, book_id, kind, chapter_idx, char_start, char_end, snippet, note, color, style, created_at, updated_at " +
                "FROM annotations WHERE book_id = ? ORDER BY chapter_idx, char_start"
        }
        val args = if (chapterIdx != null) arrayOf(bookId, chapterIdx.toString()) else arrayOf(bookId)
        return readableDatabase.rawQuery(sql, args).use { cursor ->
            val result = ArrayList<AnnotationRecord>()
            while (cursor.moveToNext()) {
                result += AnnotationRecord(
                    id = cursor.getLong(0),
                    bookId = cursor.getString(1),
                    kind = cursor.getString(2),
                    chapterIdx = cursor.getInt(3),
                    charStart = cursor.getInt(4),
                    charEnd = cursor.getInt(5),
                    snippet = cursor.getString(6) ?: "",
                    note = if (cursor.isNull(7)) null else cursor.getString(7),
                    color = cursor.getString(8) ?: "yellow",
                    style = cursor.getString(9) ?: "bg",
                    createdAt = cursor.getLong(10),
                    updatedAt = cursor.getLong(11),
                )
            }
            result
        }
    }

    fun insertAnnotation(record: AnnotationRecord): Long {
        return writableDatabase.insertWithOnConflict(
            "annotations",
            null,
            ContentValues().apply {
                put("book_id", record.bookId)
                put("kind", record.kind)
                put("chapter_idx", record.chapterIdx)
                put("char_start", record.charStart)
                put("char_end", record.charEnd)
                put("snippet", record.snippet)
                put("note", record.note)
                put("color", record.color)
                put("style", record.style)
                put("created_at", record.createdAt)
                put("updated_at", record.updatedAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun updateAnnotation(
        id: Long,
        color: String?,
        style: String?,
        note: String?,
        charStart: Int?,
        charEnd: Int?,
        snippet: String? = null,
    ) {
        val values = ContentValues().apply {
            put("updated_at", System.currentTimeMillis())
            color?.let { put("color", it) }
            style?.let { put("style", it) }
            note?.let { put("note", it) }
            charStart?.let { put("char_start", it) }
            charEnd?.let { put("char_end", it) }
            snippet?.let { put("snippet", it) }
        }
        if (values.size() > 1) {
            writableDatabase.update("annotations", values, "id = ?", arrayOf(id.toString()))
        }
    }

    fun deleteAnnotation(id: Long) {
        writableDatabase.delete("annotations", "id = ?", arrayOf(id.toString()))
    }

    /** M6.3-B：同章内与 [charStart, charEnd) 有交集的既有标注（重复划线 upsert 用）。 */
    fun queryOverlappingAnnotation(
        bookId: String,
        chapterIdx: Int,
        charStart: Int,
        charEnd: Int,
    ): AnnotationRecord? =
        readableDatabase.rawQuery(
            "SELECT id, book_id, kind, chapter_idx, char_start, char_end, snippet, note, color, style, created_at, updated_at " +
                "FROM annotations WHERE book_id = ? AND chapter_idx = ? AND char_start < ? AND char_end > ? LIMIT 1",
            arrayOf(bookId, chapterIdx.toString(), charEnd.toString(), charStart.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                AnnotationRecord(
                    id = cursor.getLong(0),
                    bookId = cursor.getString(1),
                    kind = cursor.getString(2),
                    chapterIdx = cursor.getInt(3),
                    charStart = cursor.getInt(4),
                    charEnd = cursor.getInt(5),
                    snippet = cursor.getString(6),
                    note = cursor.getString(7),
                    color = cursor.getString(8),
                    style = cursor.getString(9),
                    createdAt = cursor.getLong(10),
                    updatedAt = cursor.getLong(11),
                )
            } else {
                null
            }
        }

    // ---------------- M6.3-C：净化规则 ----------------

    fun queryPurifyRules(enabledOnly: Boolean = false): List<PurifyRuleRecord> {
        val result = ArrayList<PurifyRuleRecord>()
        readableDatabase.rawQuery(
            "SELECT id, name, pattern, builtin, enabled FROM purify_rules " +
                (if (enabledOnly) "WHERE enabled = 1 " else "") +
                "ORDER BY builtin DESC, created_at ASC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += PurifyRuleRecord(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    pattern = cursor.getString(2),
                    builtin = cursor.getInt(3) == 1,
                    enabled = cursor.getInt(4) == 1,
                )
            }
        }
        return result
    }

    fun addPurifyRule(name: String, pattern: String) {
        writableDatabase.insert(
            "purify_rules",
            null,
            ContentValues().apply {
                put("name", name)
                put("pattern", pattern)
                put("builtin", 0)
                put("enabled", 1)
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun deletePurifyRule(id: Long) {
        writableDatabase.delete("purify_rules", "id = ?", arrayOf(id.toString()))
    }

    fun setPurifyRuleEnabled(id: Long, enabled: Boolean) {
        writableDatabase.update(
            "purify_rules",
            ContentValues().apply {
                put("enabled", if (enabled) 1 else 0)
            },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    // ---------------- M6.3-B：书签 ----------------

    /** 加书签：同位置幂等（UNIQUE 约束 INSERT OR IGNORE）。 */
    fun addBookmark(bookId: String, chapterIdx: Int, charStart: Int, snippet: String) {
        writableDatabase.insertWithOnConflict(
            "bookmarks",
            null,
            ContentValues().apply {
                put("book_id", bookId)
                put("chapter_idx", chapterIdx)
                put("char_start", charStart)
                put("snippet", snippet)
                put("created_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun deleteBookmark(id: Long) {
        writableDatabase.delete("bookmarks", "id = ?", arrayOf(id.toString()))
    }

    fun queryBookmarks(bookId: String): List<BookmarkRecord> =
        readableDatabase.rawQuery(
            "SELECT id, book_id, chapter_idx, char_start, snippet, created_at " +
                "FROM bookmarks WHERE book_id = ? ORDER BY chapter_idx, char_start",
            arrayOf(bookId),
        ).use { cursor ->
            val result = ArrayList<BookmarkRecord>()
            while (cursor.moveToNext()) {
                result += BookmarkRecord(
                    id = cursor.getLong(0),
                    bookId = cursor.getString(1),
                    chapterIdx = cursor.getInt(2),
                    charStart = cursor.getInt(3),
                    snippet = cursor.getString(4),
                    createdAt = cursor.getLong(5),
                )
            }
            result
        }

    /** M6.3-A：阅读时长会话（复用 v1 reading_sessions 表）。 */
    fun saveReadingSession(bookId: String, startedAt: Long, durationMs: Long, pagesRead: Int = 0) {
        if (durationMs <= 0L) return
        writableDatabase.insertWithOnConflict(
            "reading_sessions",
            null,
            ContentValues().apply {
                put("book_id", bookId)
                put("started_at", startedAt)
                put("ended_at", System.currentTimeMillis())
                put("duration_ms", durationMs)
                put("pages_read", pagesRead)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun queryNovelChapterCount(bookId: String): Int {
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM novel_chapters WHERE book_id = ?",
            arrayOf(bookId),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** M6.1-D 设置页：全部未删除小说的书 ID（应用全局排版默认用）。 */
    fun queryNovelBookIds(): List<String> {
        val result = ArrayList<String>()
        readableDatabase.rawQuery(
            "SELECT id FROM books WHERE type = 'NOVEL' AND deleted_at IS NULL",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.getString(0)
        }
        return result
    }

    fun queryNovelProgress(bookId: String): NovelProgressRecord? {
        return readableDatabase.rawQuery(
            "SELECT chapter_idx, char_offset, updated_at FROM novel_progress WHERE book_id = ?",
            arrayOf(bookId),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                NovelProgressRecord(
                    chapterIndex = cursor.getInt(0),
                    charOffset = cursor.getInt(1),
                    updatedAt = cursor.getLong(2),
                )
            } else {
                null
            }
        }
    }

    fun saveNovelProgress(bookId: String, chapterIndex: Int, charOffset: Int) {
        writableDatabase.insertWithOnConflict(
            "novel_progress",
            null,
            ContentValues().apply {
                put("book_id", bookId)
                put("chapter_idx", chapterIndex)
                put("char_offset", charOffset)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    // ---------------- 分类（书夹） ----------------

    fun queryCategories(): List<CategoryRecord> {
        val result = ArrayList<CategoryRecord>()
        readableDatabase.rawQuery(
            """
            SELECT c.id, c.name, c.color, c.sort_key,
                   (
                       SELECT COUNT(*)
                       FROM book_categories bc
                       WHERE bc.category_id = c.id
                         AND EXISTS (
                             SELECT 1
                             FROM book_locations bl
                             LEFT JOIN folders f ON f.id = bl.folder_id
                             LEFT JOIN libraries lib ON lib.id = f.library_id
                             WHERE bl.book_id = bc.book_id
                               AND (lib.hidden IS NULL OR lib.hidden = 0)
                         )
                   ) AS cnt
            FROM categories c
            ORDER BY c.sort_key, c.id
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += CategoryRecord(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    color = cursor.getString(2),
                    sortKey = cursor.getInt(3),
                    bookCount = cursor.getInt(4),
                )
            }
        }
        return result
    }

    fun insertCategory(name: String, color: String): Long {
        val sortKey = readableDatabase.rawQuery(
            "SELECT COALESCE(MAX(sort_key), -1) + 1 FROM categories",
            null,
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val values = ContentValues().apply {
            put("name", name)
            put("color", color)
            put("sort_key", sortKey)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("categories", null, values)
    }

    fun renameCategory(id: Long, name: String) {
        writableDatabase.update(
            "categories",
            ContentValues().apply { put("name", name) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun setCategoryColor(id: Long, color: String) {
        writableDatabase.update(
            "categories",
            ContentValues().apply { put("color", color) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    /** 删除书夹只解除关系，书本仍在书架。 */
    fun deleteCategory(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("book_categories", "category_id = ?", arrayOf(id.toString()))
            db.delete("categories", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun moveCategory(id: Long, delta: Int) {
        val db = writableDatabase
        val ordered = ArrayList<Pair<Long, Int>>()
        db.rawQuery("SELECT id, sort_key FROM categories ORDER BY sort_key, id", null).use { cursor ->
            while (cursor.moveToNext()) ordered += cursor.getLong(0) to cursor.getInt(1)
        }
        val index = ordered.indexOfFirst { it.first == id }
        val target = (index + delta).coerceIn(0, ordered.lastIndex)
        if (index < 0 || index == target) return
        val (otherId, otherSort) = ordered[target]
        val (selfId, selfSort) = ordered[index]
        db.beginTransaction()
        try {
            db.update("categories", ContentValues().apply { put("sort_key", otherSort) }, "id = ?", arrayOf(selfId.toString()))
            db.update("categories", ContentValues().apply { put("sort_key", selfSort) }, "id = ?", arrayOf(otherId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun reorderCategories(orderedIds: Collection<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            orderedIds.forEachIndexed { index, id ->
                db.update(
                    "categories",
                    ContentValues().apply { put("sort_key", index) },
                    "id = ?",
                    arrayOf(id.toString()),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addBooksToCategory(bookIds: Collection<String>, categoryId: Long): Int {
        val db = writableDatabase
        var inserted = 0
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            bookIds.forEach { bookId ->
                val values = ContentValues().apply {
                    put("book_id", bookId)
                    put("category_id", categoryId)
                    put("added_at", now)
                }
                if (db.insertWithOnConflict("book_categories", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                    inserted++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted
    }

    fun removeBooksFromCategory(bookIds: Collection<String>, categoryId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            bookIds.forEach { bookId ->
                db.delete(
                    "book_categories",
                    "book_id = ? AND category_id = ?",
                    arrayOf(bookId, categoryId.toString()),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 分类 id -> 书本 id 集合，供书架一次性过滤，避免逐本查询。 */
    fun queryCategoryBookIds(): Map<Long, Set<String>> {
        val map = HashMap<Long, MutableSet<String>>()
        readableDatabase.rawQuery("SELECT category_id, book_id FROM book_categories", null).use { cursor ->
            while (cursor.moveToNext()) {
                map.getOrPut(cursor.getLong(0)) { HashSet() } += cursor.getString(1)
            }
        }
        return map
    }

    // ---------------- 阅读统计（M4-B） ----------------

    /** 开新会话前兜底关闭遗留 active 会话；duration 用最后一次心跳，避免进程死亡后台计时膨胀。 */
    fun startReadingSession(bookId: String): Long {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            db.execSQL(
                """
                UPDATE reading_sessions
                SET ended_at = started_at + duration_ms
                WHERE ended_at IS NULL
                """.trimIndent(),
            )
            val values = ContentValues().apply {
                put("book_id", bookId)
                put("started_at", now)
                put("duration_ms", 0L)
                put("pages_read", 0)
            }
            val id = db.insert("reading_sessions", null, values)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun addReadingPages(sessionId: Long, pages: Int) {
        if (pages <= 0) return
        writableDatabase.execSQL(
            "UPDATE reading_sessions SET pages_read = pages_read + ? WHERE id = ? AND ended_at IS NULL",
            arrayOf(pages.toString(), sessionId.toString()),
        )
    }

    fun updateReadingSessionDuration(sessionId: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        writableDatabase.execSQL(
            """
            UPDATE reading_sessions
            SET duration_ms = MAX(duration_ms, ?)
            WHERE id = ? AND ended_at IS NULL
            """.trimIndent(),
            arrayOf(durationMs.toString(), sessionId.toString()),
        )
    }

    fun endReadingSession(sessionId: Long) {
        val now = System.currentTimeMillis()
        writableDatabase.execSQL(
            """
            UPDATE reading_sessions
            SET duration_ms = MAX(duration_ms, ? - started_at),
                ended_at = ?
            WHERE id = ? AND ended_at IS NULL
            """.trimIndent(),
            arrayOf(now.toString(), now.toString(), sessionId.toString()),
        )
    }

    fun queryReadingStats(now: Long = System.currentTimeMillis()): ReadingStats {
        val day = 24L * 60L * 60L * 1000L
        var totalDuration = 0L
        var totalPages = 0L
        var sessionCount = 0L
        var bookCount = 0L
        var last7 = 0L
        var last30 = 0L
        readableDatabase.rawQuery(
            """
            SELECT
                COALESCE(SUM(rs.duration_ms), 0),
                COALESCE(SUM(rs.pages_read), 0),
                COUNT(*),
                COUNT(DISTINCT rs.book_id),
                COALESCE(SUM(CASE WHEN rs.started_at >= ? THEN rs.duration_ms ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN rs.started_at >= ? THEN rs.duration_ms ELSE 0 END), 0)
            FROM reading_sessions rs
            WHERE EXISTS (
                SELECT 1 FROM books b WHERE b.id = rs.book_id AND b.deleted_at IS NULL
            )
            """.trimIndent(),
            arrayOf((now - 7L * day).toString(), (now - 30L * day).toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                totalDuration = cursor.getLong(0)
                totalPages = cursor.getLong(1)
                sessionCount = cursor.getLong(2)
                bookCount = cursor.getLong(3)
                last7 = cursor.getLong(4)
                last30 = cursor.getLong(5)
            }
        }
        return ReadingStats(
            totalDurationMs = totalDuration,
            totalPagesRead = totalPages,
            sessionCount = sessionCount,
            bookCount = bookCount,
            last7DaysDurationMs = last7,
            last30DaysDurationMs = last30,
        )
    }

    fun queryReadingHistory(limit: Int = 30): List<ReadingHistoryEntry> {
        val result = ArrayList<ReadingHistoryEntry>()
        readableDatabase.rawQuery(
            """
            SELECT b.id, b.type, b.title, b.series, b.volume, b.writer, b.tags,
                   b.page_count, b.format, b.file_size, b.cover_path, b.added_at,
                   l.doc_id, l.display_path, b.read_status, COALESCE(p.percent, 0), f.tree_uri,
                   COALESCE(SUM(rs.duration_ms), 0),
                   COALESCE(SUM(rs.pages_read), 0),
                   COALESCE(MAX(COALESCE(rs.ended_at, rs.started_at)), 0)
            FROM books b
            JOIN reading_sessions rs ON rs.book_id = b.id
            LEFT JOIN book_locations l ON l.rowid = (
                SELECT rowid FROM book_locations
                WHERE book_id = b.id AND is_primary = 1
                ORDER BY last_seen_at DESC
                LIMIT 1
            )
            LEFT JOIN folders f ON f.id = l.folder_id
            LEFT JOIN libraries lib ON lib.id = f.library_id
            LEFT JOIN progress p ON p.book_id = b.id
            WHERE (lib.hidden IS NULL OR lib.hidden = 0)
              AND b.deleted_at IS NULL
            GROUP BY b.id
            ORDER BY MAX(COALESCE(rs.ended_at, rs.started_at)) DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val book = BookRecord(
                    id = cursor.getString(0),
                    type = cursor.getString(1),
                    title = cursor.getString(2),
                    series = cursor.getString(3),
                    volume = cursor.getString(4),
                    writer = cursor.getString(5),
                    tags = cursor.getString(6),
                    pageCount = cursor.getInt(7),
                    format = cursor.getString(8),
                    fileSize = cursor.getLong(9),
                    coverPath = cursor.getString(10),
                    addedAt = cursor.getLong(11),
                    docId = cursor.getString(12) ?: "",
                    displayPath = cursor.getString(13) ?: "",
                    readStatus = cursor.getString(14) ?: BookRecord.READ_UNREAD,
                    percent = cursor.getDouble(15),
                    treeUri = cursor.getString(16),
                )
                result += ReadingHistoryEntry(
                    book = book,
                    totalDurationMs = cursor.getLong(17),
                    totalPagesRead = cursor.getLong(18),
                    lastReadAt = cursor.getLong(19),
                )
            }
        }
        return result
    }

    // ---------------- 书库 / 回收站 / 元数据（M4-E） ----------------

    fun queryLibraries(): List<LibraryRecord> {
        val result = ArrayList<LibraryRecord>()
        readableDatabase.rawQuery(
            """
            SELECT l.id, l.name, l.root_tree_uri, l.hidden, l.created_at,
                   (
                       SELECT COUNT(*)
                       FROM books b
                       JOIN book_locations bl ON bl.book_id = b.id
                       JOIN folders f ON f.id = bl.folder_id
                       WHERE f.library_id = l.id AND b.deleted_at IS NULL
                   ) AS book_count,
                   (
                       SELECT COUNT(*)
                       FROM books b
                       JOIN book_locations bl ON bl.book_id = b.id
                       JOIN folders f ON f.id = bl.folder_id
                       WHERE f.library_id = l.id AND b.deleted_at IS NOT NULL
                   ) AS trash_count
            FROM libraries l
            ORDER BY l.sort_key, l.created_at, l.id
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += LibraryRecord(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    rootTreeUri = cursor.getString(2),
                    hidden = cursor.getInt(3) != 0,
                    createdAt = cursor.getLong(4),
                    bookCount = cursor.getInt(5),
                    trashCount = cursor.getInt(6),
                )
            }
        }
        return result
    }

    fun setLibraryHidden(id: Long, hidden: Boolean) {
        writableDatabase.update(
            "libraries",
            ContentValues().apply { put("hidden", if (hidden) 1 else 0) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    /** 书库壳删除（文件夹全部移除后调用，避免书库列表留 0 本空卡）。 */
    fun deleteLibrary(id: Long) {
        writableDatabase.delete("libraries", "id = ?", arrayOf(id.toString()))
    }

    fun moveBooksToTrash(ids: Collection<String>) {
        if (ids.isEmpty()) return
        writableDatabase.execSQL(
            """
            UPDATE books
            SET deleted_at = ?
            WHERE deleted_at IS NULL AND id IN (${ids.joinToString(",") { "?" }})
            """.trimIndent(),
            arrayOf(System.currentTimeMillis().toString(), *ids.toTypedArray()),
        )
    }

    fun restoreBooksFromTrash(ids: Collection<String>) {
        if (ids.isEmpty()) return
        writableDatabase.execSQL(
            """
            UPDATE books
            SET deleted_at = NULL
            WHERE deleted_at IS NOT NULL AND id IN (${ids.joinToString(",") { "?" }})
            """.trimIndent(),
            arrayOf(*ids.toTypedArray()),
        )
    }

    fun queryTrashBooks(): List<TrashBook> {
        val result = ArrayList<TrashBook>()
        rawBooks(
            """
            $bookSelect
            WHERE b.deleted_at IS NOT NULL
            ORDER BY b.deleted_at DESC
            """.trimIndent(),
            null,
        ).forEach { book ->
            readableDatabase.rawQuery(
                "SELECT deleted_at FROM books WHERE id = ?",
                arrayOf(book.id),
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    result += TrashBook(book = book, deletedAt = cursor.getLong(0))
                }
            }
        }
        return result
    }

    fun queryExpiredTrashIds(now: Long = System.currentTimeMillis()): List<String> {
        val result = ArrayList<String>()
        readableDatabase.rawQuery(
            "SELECT id FROM books WHERE deleted_at IS NOT NULL AND deleted_at <= ?",
            arrayOf((now - 30L * 24 * 60 * 60 * 1000).toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.getString(0)
        }
        return result
    }

    // ---------- M4.5 同步（WebDAV）----------

    data class TombstoneRow(val kind: String, val key: String, val deletedAt: Long)

    /** doc 构建用书籍行（仅非回收站书；bookId 即内容指纹，跨设备一致）。 */
    data class SyncBookRow(
        val id: String,
        val title: String,
        val format: String,
        val fileSize: Long,
        val readStatus: String,
    )

    data class SyncProgressRow(
        val page: Int,
        val percent: Double,
        val finished: Int,
        val sessionSeconds: Int,
        val updatedAt: Long,
    )

    data class SyncComicBookmarkRow(
        val pageIndex: Int,
        val createdAt: Long,
        val note: String?,
    )

    data class SyncSessionRow(
        val startedAt: Long,
        val endedAt: Long?,
        val durationMs: Long,
        val pagesRead: Int,
    )

    fun addSyncTombstones(items: List<TombstoneRow>) {
        if (items.isEmpty()) return
        val sql = writableDatabase
        sql.beginTransaction()
        try {
            items.forEach { row ->
                sql.insertWithOnConflict(
                    "sync_tombstones",
                    null,
                    ContentValues().apply {
                        put("kind", row.kind)
                        put("key", row.key)
                        put("deleted_at", row.deletedAt)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
    }

    fun querySyncTombstones(): List<TombstoneRow> {
        val result = ArrayList<TombstoneRow>()
        readableDatabase.rawQuery(
            "SELECT kind, key, deleted_at FROM sync_tombstones",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += TombstoneRow(cursor.getString(0), cursor.getString(1), cursor.getLong(2))
            }
        }
        return result
    }

    /** 清理过期墓碑（30 天），返回删除行数。 */
    fun purgeSyncTombstones(before: Long): Int =
        writableDatabase.delete("sync_tombstones", "deleted_at < ?", arrayOf(before.toString()))

    fun querySyncBookRows(): List<SyncBookRow> {
        val result = ArrayList<SyncBookRow>()
        readableDatabase.rawQuery(
            "SELECT id, title, format, file_size, read_status FROM books WHERE deleted_at IS NULL",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SyncBookRow(
                    id = cursor.getString(0),
                    title = cursor.getString(1),
                    format = cursor.getString(2),
                    fileSize = cursor.getLong(3),
                    readStatus = cursor.getString(4),
                )
            }
        }
        return result
    }

    fun queryProgressRow(bookId: String): SyncProgressRow? =
        readableDatabase.rawQuery(
            "SELECT page, percent, finished, session_seconds, updated_at FROM progress WHERE book_id = ?",
            arrayOf(bookId),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                SyncProgressRow(cursor.getInt(0), cursor.getDouble(1), cursor.getInt(2), cursor.getInt(3), cursor.getLong(4))
            } else null
        }

    fun queryComicBookmarksRow(bookId: String): List<SyncComicBookmarkRow> {
        val result = ArrayList<SyncComicBookmarkRow>()
        readableDatabase.rawQuery(
            "SELECT page_index, created_at, note FROM page_bookmarks WHERE book_id = ? ORDER BY page_index",
            arrayOf(bookId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SyncComicBookmarkRow(
                    pageIndex = cursor.getInt(0),
                    createdAt = cursor.getLong(1),
                    note = if (cursor.isNull(2)) null else cursor.getString(2),
                )
            }
        }
        return result
    }

    /** 会话列表（新→旧），上限截断防 doc 无限膨胀；旧会话留本地不计入同步。 */
    fun querySyncSessions(bookId: String, limit: Int = 200): List<SyncSessionRow> {
        val result = ArrayList<SyncSessionRow>()
        readableDatabase.rawQuery(
            """
            SELECT started_at, ended_at, duration_ms, pages_read
            FROM reading_sessions WHERE book_id = ?
            ORDER BY started_at DESC LIMIT ?
            """.trimIndent(),
            arrayOf(bookId, limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SyncSessionRow(
                    startedAt = cursor.getLong(0),
                    endedAt = if (cursor.isNull(1)) null else cursor.getLong(1),
                    durationMs = cursor.getLong(2),
                    pagesRead = cursor.getInt(3),
                )
            }
        }
        return result
    }

    /** 供同步事务内使用的级联删除包装（不走自带事务，由调用方统一管理）。 */
    fun cascadeDeleteBooksInTransaction(sql: SQLiteDatabase, bookIds: Collection<String>) {
        bookIds.forEach { deleteBookCascade(sql, it) }
    }


    fun updateBooksMetadata(
        ids: Collection<String>,
        series: String?,
        writer: String?,
        tags: String?,
        readStatus: String?,
    ) {
        if (ids.isEmpty()) return
        val values = ContentValues()
        series?.let { values.put("series", it) }
        writer?.let { values.put("writer", it) }
        tags?.let { values.put("tags", it) }
        readStatus?.let { values.put("read_status", it) }
        values.put("updated_at", System.currentTimeMillis())
        writableDatabase.update(
            "books",
            values,
            "id IN (${ids.joinToString(",") { "?" }}) AND deleted_at IS NULL",
            ids.toTypedArray(),
        )
    }

    fun purgeExpiredTrash(now: Long = System.currentTimeMillis()): List<String> {
        val ids = queryExpiredTrashIds(now)
        if (ids.isNotEmpty()) deleteBooks(ids)
        return ids
    }
}
