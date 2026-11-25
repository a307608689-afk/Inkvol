package com.inkvol.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import com.inkvol.app.data.local.InkvolDatabase
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.data.settings.SettingsRepository
import com.inkvol.app.widget.ContinueReadingWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * M4-D 本地备份：
 * - 备份书夹 / 阅读进度 / 页签 / 阅读统计 / 全部应用偏好；
 * - 不复制漫画源文件，书本按内容指纹 ID 匹配；
 * - 恢复前先预检可匹配 / 缺失，恢复时只写已存在的书，不伪造书。
 */
class BackupRepository(
    private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
) {

    private val db = InkvolDatabase(context)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    suspend fun export(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val data = buildBackup()
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                writeJson(output, data)
            } ?: error("无法写入备份文件")
        }
    }

    suspend fun inspect(uri: Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val data = readBackup(uri)
            val currentIds = queryBookIds()
            val missing = data.books.filter { it.id !in currentIds }
            BackupPreview(
                version = data.version,
                generatedAt = data.generatedAt,
                bookCount = data.books.size,
                availableBookCount = data.books.size - missing.size,
                missingBookCount = missing.size,
                missingBookTitles = missing.take(8).map { it.title },
                categoryCount = data.categories.size,
                sessionCount = data.books.sumOf { it.readingSessions.size },
                preferenceCount = data.preferences.size,
                annotationCount = data.books.sumOf { it.annotations.size },
                novelBookmarkCount = data.books.sumOf { it.novelBookmarks.size },
                purifyRuleCount = data.purifyRules.size,
                isValid = data.version == 1,
            )
        }
    }

    suspend fun restore(uri: Uri): Result<RestoreResult> = withContext(Dispatchers.IO) {
        val data = readBackup(uri)
        restore(data)
    }

    suspend fun restore(data: BackupData): Result<RestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            val currentBooks = queryCurrentBooks().associateBy { it.id }
            var restoredBooks = 0
            var restoredProgress = 0
            var restoredSessions = 0
            var missingBooks = 0

            val sql = db.writableDatabase
            sql.beginTransaction()
            try {
                data.categories.forEach { category ->
                    sql.insertWithOnConflict(
                        "categories",
                        null,
                        ContentValues().apply {
                            put("id", category.id)
                            put("name", category.name)
                            put("color", category.color)
                            put("sort_key", category.sortKey)
                            put("created_at", category.createdAt)
                        },
                        android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }

                data.books.forEach { backupBook ->
                    val current = currentBooks[backupBook.id]
                    if (current == null) {
                        missingBooks++
                        return@forEach
                    }
                    restoredBooks++
                    sql.update(
                        "books",
                        ContentValues().apply { put("read_status", backupBook.readStatus) },
                        "id = ?",
                        arrayOf(current.id),
                    )

                    backupBook.progress?.let { progress ->
                        sql.insertWithOnConflict(
                            "progress",
                            null,
                            ContentValues().apply {
                                put("book_id", current.id)
                                put("page", progress.page)
                                put("percent", progress.percent)
                                put("finished", progress.finished)
                                put("session_seconds", progress.sessionSeconds)
                                put("updated_at", progress.updatedAt)
                            },
                            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                        )
                        restoredProgress++
                    }

                    sql.delete("page_bookmarks", "book_id = ?", arrayOf(current.id))
                    backupBook.pageBookmarks.forEach { bookmark ->
                        sql.insert(
                            "page_bookmarks",
                            null,
                            ContentValues().apply {
                                put("book_id", current.id)
                                put("page_index", bookmark.pageIndex)
                                put("created_at", bookmark.createdAt)
                                put("note", bookmark.note)
                            },
                        )
                    }

                    sql.delete("reading_sessions", "book_id = ?", arrayOf(current.id))
                    backupBook.readingSessions.forEach { session ->
                        sql.insert(
                            "reading_sessions",
                            null,
                            ContentValues().apply {
                                put("book_id", current.id)
                                put("started_at", session.startedAt)
                                put("ended_at", session.endedAt)
                                put("duration_ms", session.durationMs)
                                put("pages_read", session.pagesRead)
                            },
                        )
                        restoredSessions++
                    }

                    backupBook.categoryIds.forEach { categoryId ->
                        sql.insertWithOnConflict(
                            "book_categories",
                            null,
                            ContentValues().apply {
                                put("book_id", current.id)
                                put("category_id", categoryId)
                                put("added_at", System.currentTimeMillis())
                            },
                            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
                        )
                    }

                    // M6.3-D：小说进度（章 + 章内 char 偏移）
                    backupBook.novelProgress?.let { np ->
                        sql.insertWithOnConflict(
                            "novel_progress",
                            null,
                            ContentValues().apply {
                                put("book_id", current.id)
                                put("chapter_idx", np.chapterIdx)
                                put("char_offset", np.charOffset)
                                put("updated_at", np.updatedAt)
                            },
                            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                        )
                    }

                    // P3：漫画章节区间（先清后插，幂等）
                    sql.delete("book_sections", "book_id = ?", arrayOf(current.id))
                    backupBook.sections.forEach { section ->
                        sql.insert(
                            "book_sections",
                            null,
                            ContentValues().apply {
                                put("book_id", current.id)
                                put("idx", section.idx)
                                put("title", section.title)
                                put("page_start", section.pageStart)
                            },
                        )
                    }

                    // M6.3-D：小说书签（整书替换，章内 char 幂等）
                    sql.delete("bookmarks", "book_id = ?", arrayOf(current.id))
                    backupBook.novelBookmarks.forEach { bookmark ->
                        sql.insertWithOnConflict(
                            "bookmarks",
                            null,
                            ContentValues().apply {
                                put("book_id", current.id)
                                put("chapter_idx", bookmark.chapterIdx)
                                put("char_start", bookmark.charStart)
                                put("snippet", bookmark.snippet)
                                put("created_at", bookmark.createdAt)
                            },
                            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
                        )
                    }

                    // M6.3-D：小说标注（整书替换，自增 id 重新分配）
                    sql.delete("annotations", "book_id = ?", arrayOf(current.id))
                    backupBook.annotations.forEach { annotation ->
                        sql.insert(
                            "annotations",
                            null,
                            ContentValues().apply {
                                put("book_id", current.id)
                                put("kind", "highlight")
                                put("chapter_idx", annotation.chapterIdx)
                                put("char_start", annotation.charStart)
                                put("char_end", annotation.charEnd)
                                put("snippet", annotation.snippet)
                                put("note", annotation.note)
                                put("color", annotation.color)
                                put("style", annotation.style)
                                put("created_at", annotation.createdAt)
                                put("updated_at", annotation.updatedAt)
                            },
                        )
                    }
                }

                // M6.3-D：净化规则（只合并自定义规则，内置以本机为准）；
                // 按 pattern 去重，避免多次恢复重复插入同一规则。
                data.purifyRules.filter { !it.builtin }.forEach { rule ->
                    val exists = sql.rawQuery(
                        "SELECT 1 FROM purify_rules WHERE pattern = ? LIMIT 1",
                        arrayOf(rule.pattern),
                    ).use { it.moveToFirst() }
                    if (!exists) {
                        sql.insert(
                            "purify_rules",
                            null,
                            ContentValues().apply {
                                put("name", rule.name)
                                put("pattern", rule.pattern)
                                put("builtin", 0)
                                put("enabled", if (rule.enabled) 1 else 0)
                                put("created_at", System.currentTimeMillis())
                            },
                        )
                    }
                }

                data.preferences.forEach { preference ->
                    // 这里不在 DB 事务内，后续统一导入；事务只保护书库元数据。
                }
                sql.setTransactionSuccessful()
            } finally {
                sql.endTransaction()
            }

            data.preferences.forEach { preference ->
                settingsRepository.putPreference(preference.key, preference.type, preference.value)
            }

            libraryRepository.refresh()
            ContinueReadingWidget.updateAll(context)

            RestoreResult(
                restoredBooks = restoredBooks,
                restoredProgress = restoredProgress,
                restoredSessions = restoredSessions,
                missingBooks = missingBooks,
                restoredPreferences = data.preferences.size,
            )
        }
    }

    private suspend fun buildBackup(): BackupData {
        val prefs = settingsRepository.snapshotPreferences()
        val currentIds = queryBookIds()
        val categories = queryCategories()
        val categoryMap = queryCategoryMap()

        val books = queryBookRows().map { row ->
            BackupBook(
                id = row.id,
                title = row.title,
                format = row.format,
                fileSize = row.fileSize,
                pageCount = row.pageCount,
                readStatus = row.readStatus,
                categoryIds = categoryMap[row.id].orEmpty(),
                progress = queryProgress(row.id),
                pageBookmarks = queryBookmarks(row.id),
                readingSessions = queryReadingSessions(row.id),
                novelProgress = queryNovelProgress(row.id),
                novelBookmarks = queryNovelBookmarks(row.id),
                annotations = queryAnnotations(row.id),
                sections = queryBookSections(row.id),
            )
        }

        val preferences = prefs.asMap().mapNotNull { (key, value) ->
            when (value) {
                is Boolean -> BackupPreference(key.name, "boolean", value.toString())
                is Int -> BackupPreference(key.name, "int", value.toString())
                is Long -> BackupPreference(key.name, "long", value.toString())
                is Float -> BackupPreference(key.name, "float", value.toString())
                is Double -> BackupPreference(key.name, "double", value.toString())
                is String -> BackupPreference(key.name, "string", value)
                else -> null
            }
        }.sortedBy { it.key }

        return BackupData(
            version = 1,
            generatedAt = System.currentTimeMillis(),
            categories = categories,
            books = books,
            preferences = preferences,
            purifyRules = queryPurifyRules(),
        )
    }

    // ---------------- P3：漫画章节查询 ----------------

    private fun queryBookSections(bookId: String): List<BackupSection> =
        db.readableDatabase.rawQuery(
            "SELECT idx, title, page_start FROM book_sections WHERE book_id = ? ORDER BY idx",
            arrayOf(bookId),
        ).use { cursor ->
            val list = ArrayList<BackupSection>()
            while (cursor.moveToNext()) {
                list += BackupSection(
                    idx = cursor.getInt(0),
                    title = cursor.getString(1) ?: "",
                    pageStart = cursor.getInt(2),
                )
            }
            list
        }

    // ---------------- M6.3-D：小说数据查询 ----------------

    private fun queryNovelProgress(bookId: String): BackupNovelProgress? =
        db.readableDatabase.rawQuery(
            "SELECT chapter_idx, char_offset, updated_at FROM novel_progress WHERE book_id = ?",
            arrayOf(bookId),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                BackupNovelProgress(
                    chapterIdx = cursor.getInt(0),
                    charOffset = cursor.getInt(1),
                    updatedAt = cursor.getLong(2),
                )
            } else null
        }

    private fun queryNovelBookmarks(bookId: String): List<BackupNovelBookmark> {
        val result = ArrayList<BackupNovelBookmark>()
        db.readableDatabase.rawQuery(
            "SELECT chapter_idx, char_start, snippet, created_at FROM bookmarks " +
                "WHERE book_id = ? ORDER BY chapter_idx, char_start",
            arrayOf(bookId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += BackupNovelBookmark(
                    chapterIdx = cursor.getInt(0),
                    charStart = cursor.getInt(1),
                    snippet = cursor.getString(2),
                    createdAt = cursor.getLong(3),
                )
            }
        }
        return result
    }

    private fun queryAnnotations(bookId: String): List<BackupAnnotation> {
        val result = ArrayList<BackupAnnotation>()
        db.readableDatabase.rawQuery(
            "SELECT chapter_idx, char_start, char_end, snippet, note, color, style, created_at, updated_at " +
                "FROM annotations WHERE book_id = ? ORDER BY chapter_idx, char_start",
            arrayOf(bookId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += BackupAnnotation(
                    chapterIdx = cursor.getInt(0),
                    charStart = cursor.getInt(1),
                    charEnd = cursor.getInt(2),
                    snippet = cursor.getString(3),
                    note = cursor.getString(4),
                    color = cursor.getString(5),
                    style = cursor.getString(6),
                    createdAt = cursor.getLong(7),
                    updatedAt = cursor.getLong(8),
                )
            }
        }
        return result
    }

    private fun queryPurifyRules(): List<BackupPurifyRule> {
        val result = ArrayList<BackupPurifyRule>()
        db.readableDatabase.rawQuery(
            "SELECT name, pattern, builtin, enabled FROM purify_rules ORDER BY builtin DESC, created_at ASC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += BackupPurifyRule(
                    name = cursor.getString(0),
                    pattern = cursor.getString(1),
                    builtin = cursor.getInt(2) == 1,
                    enabled = cursor.getInt(3) == 1,
                )
            }
        }
        return result
    }

    private fun readBackup(uri: Uri): BackupData =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val text = input.readBytes().decodeToString()
            json.decodeFromString<BackupData>(text)
        } ?: error("无法读取备份文件")

    private fun writeJson(output: OutputStream, data: BackupData) {
        output.write(json.encodeToString(data).toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun queryBookIds(): Set<String> {
        val ids = HashSet<String>()
        db.readableDatabase.rawQuery("SELECT id FROM books", null).use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getString(0)
        }
        return ids
    }

    private data class BookRow(
        val id: String,
        val title: String,
        val format: String,
        val fileSize: Long,
        val pageCount: Int,
        val readStatus: String,
    )

    private fun queryBookRows(): List<BookRow> {
        val result = ArrayList<BookRow>()
        db.readableDatabase.rawQuery(
            "SELECT id, title, format, file_size, page_count, read_status FROM books",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += BookRow(
                    id = cursor.getString(0),
                    title = cursor.getString(1),
                    format = cursor.getString(2),
                    fileSize = cursor.getLong(3),
                    pageCount = cursor.getInt(4),
                    readStatus = cursor.getString(5),
                )
            }
        }
        return result
    }

    private fun queryCurrentBooks(): List<BookRecord> = db.queryBooks()

    private fun queryCategories(): List<BackupCategory> {
        val result = ArrayList<BackupCategory>()
        db.readableDatabase.rawQuery(
            "SELECT id, name, color, sort_key, created_at FROM categories ORDER BY sort_key, id",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += BackupCategory(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    color = cursor.getString(2),
                    sortKey = cursor.getInt(3),
                    createdAt = cursor.getLong(4),
                )
            }
        }
        return result
    }

    private fun queryCategoryMap(): Map<String, List<Long>> {
        val map = HashMap<String, MutableList<Long>>()
        db.readableDatabase.rawQuery("SELECT book_id, category_id FROM book_categories", null).use { cursor ->
            while (cursor.moveToNext()) {
                map.getOrPut(cursor.getString(0)) { ArrayList() } += cursor.getLong(1)
            }
        }
        return map
    }

    private fun queryProgress(bookId: String): BackupProgress? {
        db.readableDatabase.rawQuery(
            """
            SELECT page, percent, finished, session_seconds, updated_at
            FROM progress WHERE book_id = ?
            """.trimIndent(),
            arrayOf(bookId),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return BackupProgress(
                    page = cursor.getInt(0),
                    percent = cursor.getDouble(1),
                    finished = cursor.getInt(2),
                    sessionSeconds = cursor.getInt(3),
                    updatedAt = cursor.getLong(4),
                )
            }
        }
        return null
    }

    private fun queryBookmarks(bookId: String): List<BackupBookmark> {
        val result = ArrayList<BackupBookmark>()
        db.readableDatabase.rawQuery(
            "SELECT page_index, created_at, note FROM page_bookmarks WHERE book_id = ? ORDER BY page_index",
            arrayOf(bookId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += BackupBookmark(
                    pageIndex = cursor.getInt(0),
                    createdAt = cursor.getLong(1),
                    note = cursor.getString(2),
                )
            }
        }
        return result
    }

    private fun queryReadingSessions(bookId: String): List<BackupReadingSession> {
        val result = ArrayList<BackupReadingSession>()
        db.readableDatabase.rawQuery(
            """
            SELECT started_at, ended_at, duration_ms, pages_read
            FROM reading_sessions WHERE book_id = ? ORDER BY started_at
            """.trimIndent(),
            arrayOf(bookId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += BackupReadingSession(
                    startedAt = cursor.getLong(0),
                    endedAt = if (cursor.isNull(1)) null else cursor.getLong(1),
                    durationMs = cursor.getLong(2),
                    pagesRead = cursor.getInt(3),
                )
            }
        }
        return result
    }

    data class RestoreResult(
        val restoredBooks: Int,
        val restoredProgress: Int,
        val restoredSessions: Int,
        val missingBooks: Int,
        val restoredPreferences: Int,
    )

    companion object {
        const val MIME_TYPE = "application/json"
    }
}
