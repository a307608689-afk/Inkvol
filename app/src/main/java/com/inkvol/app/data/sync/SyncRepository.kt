package com.inkvol.app.data.sync

import android.content.Context
import com.inkvol.app.data.local.InkvolDatabase
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 同步状态机（M4.5）：pull → merge → push 固定顺序；Mutex 单飞。
 * 只负责「一次同步做什么」，何时做（防抖/退后台/WiFi 门禁）归 SyncScheduler。
 */
class SyncRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val db = InkvolDatabase(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val syncFiles = SyncFiles(
        context,
        db,
        com.inkvol.app.data.import.SingleFileImporter(context, db),
        settings,
    )

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    /** 在读书探针：InkvolApp 组合根注入（返回当前打开的书 id，未打开为 null）。 */
    @Volatile
    var openBookProbe: (() -> String?)? = null

    @Serializable
    data class SyncUiState(
        val configured: Boolean = false,
        val enabled: Boolean = false,
        val syncing: Boolean = false,
        val lastOkAt: Long = 0,
        val lastResult: String = "",
        val deviceName: String = "",
        val serverHost: String = "",
    )

    /** 一次同步的配置快照（密码解密后仅在内存流转，不落盘不进日志）。 */
    data class SyncConfig(
        val enabled: Boolean,
        val server: String,
        val account: String,
        val password: String,
        val basePath: String,
        val deviceId: String,
        val deviceName: String,
    )

    // ---------- 状态与配置 ----------

    /** 设置变更后刷新状态行（设置页观察此流）。 */
    fun refreshState() {
        scope.launch {
            val prefs = settings.snapshotPreferences()
            val server = settings.stringOf(prefs, SettingsKeys.SYNC_SERVER, "")
            val account = settings.stringOf(prefs, SettingsKeys.SYNC_ACCOUNT, "")
            val hasPassword = settings.stringOf(prefs, SettingsKeys.SYNC_PASSWORD_ENC, "").isNotBlank()
            val configured = server.isNotBlank() && account.isNotBlank() && hasPassword
            _state.update {
                it.copy(
                    configured = configured,
                    enabled = configured && settings.booleanOf(prefs, SettingsKeys.SYNC_ENABLED, false),
                    lastOkAt = settings.longOf(prefs, SettingsKeys.SYNC_LAST_OK_AT, 0),
                    lastResult = settings.stringOf(prefs, SettingsKeys.SYNC_LAST_RESULT, ""),
                    deviceName = DeviceIdentity.deviceName(settings),
                    serverHost = server.takeIf { s -> s.isNotBlank() }
                        ?.removePrefix("https://")?.removePrefix("http://")?.substringBefore('/') ?: "",
                )
            }
        }
    }

    /** 读取配置；未配置（服务器/账号/密码任一缺失）返回 null。密码密文解密失败按未配置。 */
    private suspend fun readConfig(): SyncConfig? = withContext(Dispatchers.IO) {
        val prefs = settings.snapshotPreferences()
        val server = settings.stringOf(prefs, SettingsKeys.SYNC_SERVER, "").trim()
        val account = settings.stringOf(prefs, SettingsKeys.SYNC_ACCOUNT, "").trim()
        val enc = settings.stringOf(prefs, SettingsKeys.SYNC_PASSWORD_ENC, "")
        if (server.isBlank() || account.isBlank() || enc.isBlank()) return@withContext null
        val password = CryptoBox.decrypt(enc) ?: return@withContext null
        val enabled = settings.booleanOf(prefs, SettingsKeys.SYNC_ENABLED, false)
        val basePath = settings.stringOf(prefs, SettingsKeys.SYNC_BASE_PATH, DEFAULT_BASE_PATH)
            .let { if (it.startsWith("/")) it else "/$it" }.let { if (it.endsWith("/")) it else "$it/" }
        SyncConfig(
            enabled = enabled,
            server = server,
            account = account,
            password = password,
            basePath = basePath,
            deviceId = DeviceIdentity.deviceId(settings),
            deviceName = DeviceIdentity.deviceName(settings),
        )
    }

    // ---------- 同步入口 ----------

    /** 手动/自动触发的异步入口（异常内部消化，只进同步记录）。 */
    fun syncNowAsync(reason: String) {
        scope.launch { runCatching { syncNow(reason) } }
    }

    /** 一次完整同步：pull → merge → apply → push。返回记录（供调用方展示）。 */
    suspend fun syncNow(reason: String): SyncLogEntry = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val config = readConfig()
        if (config == null || !config.enabled) {
            return@withContext SyncLogEntry(at = startedAt, ok = false, errorKind = "offline", message = "同步未启用")
        }
        syncMutex.withLock {
            _state.update { it.copy(syncing = true) }
            try {
                val client = WebDavClient(config.server, config.basePath, config.account, config.password)
                var requests = 0
                // 1) 基目录探测 + 首次建目录（404 才 MKCOL，省请求）
                val baseEntries = runCatching { client.list("") }
                    .onSuccess { requests++ }
                    .getOrElse { return@withContext fail(startedAt, reason, it, requests) }
                if (baseEntries.isEmpty()) {
                    runCatching {
                        client.makeCollection("")
                        client.makeCollection(DEVICE_DIR)
                        client.makeCollection(BOOK_DIR)
                    }.onSuccess { requests += 3 }
                        .getOrElse { return@withContext fail(startedAt, reason, it, requests) }
                }
                // 2) pull：其它设备文档（Last-Modified 未变则跳过 GET）
                val remotes = pullRemoteDocs(client, config) { requests++ }
                    .getOrElse { return@withContext fail(startedAt, reason, it, requests) }
                // 3) 本机 doc 构建（排除回收站书）
                val ownDoc = buildOwnDoc(config)
                // 4) merge
                val snapshot = buildLocalSnapshot(ownDoc)
                val openBookId = openBookProbe?.invoke()
                val merged = SyncMerge.merge(snapshot, remotes.mapNotNull { it.parsed }, openBookId)
                // 5) apply（事务）
                var applied = 0
                if (hasChanges(merged)) {
                    applyMerge(merged)
                    applied = merged.pulledBooks
                }
                // 6) push 本机 doc（恒 PUT：1 个请求换「永远正确」）
                val docBytes = json.encodeToString(SyncDeviceDoc.serializer(), ownDoc)
                    .toByteArray(Charsets.UTF_8)
                runCatching { client.upload("$DEVICE_DIR${config.deviceId}.json", docBytes) }
                    .onSuccess { requests++ }
                    .getOrElse { return@withContext fail(startedAt, reason, it, requests) }
                // 7) 收尾
                saveLastModifiedCache(remotes)
                // 8) 文件同步（开关开启时；对账式，限额分批收敛）
                runFilePhase(client, config, remotes) { requests++ }?.let { outcome ->
                    if (outcome.hasWork) {
                        library.postNotice(
                            "文件同步 · 上传 ${outcome.uploaded} · 下载 ${outcome.downloaded}" +
                                if (outcome.failed > 0) " · 失败 ${outcome.failed}" else "",
                        )
                    }
                    if (outcome.downloaded > 0) {
                        // 新书落库，书架立即刷新
                        library.refresh()
                    }
                }
                return@withContext succeed(startedAt, reason, merged, applied, requests)
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    // ---------- pull ----------

    private class RemoteFetched(
        val deviceId: String,
        val lastModified: Long,
        val parsed: SyncDeviceDoc?,
    )

    /** 拉取其它设备文档；Last-Modified 未变复用缓存跳过 GET（省请求预算）。 */
    private suspend fun pullRemoteDocs(
        client: WebDavClient,
        config: SyncConfig,
        onRequest: () -> Unit,
    ): Result<List<RemoteFetched>> = runCatching {
        val entries = client.list(DEVICE_DIR)
        onRequest()
        val cache = readLastModifiedCache()
        val result = ArrayList<RemoteFetched>()
        entries.forEach { entry ->
            if (entry.isDirectory) return@forEach
            if (!entry.name.endsWith(".json")) return@forEach
            val remoteId = entry.name.removeSuffix(".json")
            if (remoteId == config.deviceId) return@forEach
            val cachedLm = cache[remoteId]
            if (cachedLm != null && cachedLm == entry.lastModified) {
                result += RemoteFetched(remoteId, entry.lastModified, null)
                return@forEach
            }
            val bytes = client.download("$DEVICE_DIR${entry.name}")
            onRequest()
            val doc = runCatching {
                json.decodeFromString(SyncDeviceDoc.serializer(), String(bytes, Charsets.UTF_8))
            }.getOrNull()
            if (doc != null && doc.formatVersion > SYNC_FORMAT_VERSION) {
                // 新版本格式：跳过合并（旧端不认识的结构），提示升级
                library.postNotice("检测到更高版本的同步数据，请升级应用后同步")
            }
            if (doc != null && doc.formatVersion == SYNC_FORMAT_VERSION) {
                result += RemoteFetched(remoteId, entry.lastModified, doc)
            }
        }
        result
    }

    private suspend fun readLastModifiedCache(): Map<String, Long> {
        val raw = settings.stringOf(settings.snapshotPreferences(), SettingsKeys.SYNC_LAST_MODIFIED, "")
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString(
                MapSerializer(String.serializer(), Long.serializer()),
                raw,
            )
        }.getOrDefault(emptyMap())
    }

    private suspend fun saveLastModifiedCache(remotes: List<RemoteFetched>) {
        val current = readLastModifiedCache().toMutableMap()
        remotes.forEach { current[it.deviceId] = it.lastModified }
        settings.putString(
            SettingsKeys.SYNC_LAST_MODIFIED,
            json.encodeToString(MapSerializer(String.serializer(), Long.serializer()), current),
        )
    }

    /** 文件同步阶段（开关开启才跑）；返回 null = 未启用/跳过。 */
    private suspend fun runFilePhase(
        client: WebDavClient,
        config: SyncConfig,
        remotes: List<RemoteFetched>,
        onRequest: () -> Unit,
    ): SyncFiles.Outcome? {
        val prefs = settings.snapshotPreferences()
        val single = settings.booleanOf(prefs, SettingsKeys.SYNC_FILES_SINGLE, true)
        val all = settings.booleanOf(prefs, SettingsKeys.SYNC_FILES_ALL, false)
        if (!single && !all) return null
        val tombstonedIds = ownTombstonedBookIds()
        val remoteTitles = remotes.mapNotNull { it.parsed }
            .flatMap { doc -> doc.books.map { it.bookId to it.title } }
            .toMap()
            .filterValues { it.isNotBlank() }
        return syncFiles.run(
            client = client,
            config = config,
            remoteTitles = remoteTitles,
            tombstonedBookIds = tombstonedIds,
            scopeAll = all,
            onRequest = onRequest,
            onProgress = { },
        )
    }

    private suspend fun ownTombstonedBookIds(): Set<String> =
        db.querySyncTombstones()
            .filter { it.kind == TombstoneKinds.BOOK }
            .map { it.key.removePrefix(TombstoneKeys.BOOK_PREFIX) }
            .toSet()

    /** 云端书籍清单（云端书籍卡展示用）。 */
    suspend fun listCloudBooks(): Result<List<SyncFiles.CloudBookInfo>> = withContext(Dispatchers.IO) {
        val config = readConfig()
            ?: return@withContext Result.failure(IllegalStateException("同步未配置"))
        val client = WebDavClient(config.server, config.basePath, config.account, config.password)
        Result.success(syncFiles.listCloudBooks(client) { })
    }

    /** 云端书籍卡手动下载导入（标题从远端 doc 提取，缺省用 fingerprint）。 */
    suspend fun downloadCloudBook(info: SyncFiles.CloudBookInfo): Result<Unit> = withContext(Dispatchers.IO) {
        val config = readConfig()
            ?: return@withContext Result.failure(IllegalStateException("同步未配置"))
        val client = WebDavClient(config.server, config.basePath, config.account, config.password)
        val remotes = pullRemoteDocs(client, config) { }.getOrDefault(emptyList())
        val title = remotes.mapNotNull { it.parsed }
            .firstOrNull { doc -> doc.books.any { it.bookId == info.bookId } }
            ?.books?.firstOrNull { it.bookId == info.bookId }?.title
        syncFiles.downloadOne(client, info, title)
    }

    private fun hasChanges(result: SyncMerge.MergeResult): Boolean = with(result) {
        progressWins.isNotEmpty() || novelProgressWins.isNotEmpty() ||
            readStatusUpdates.isNotEmpty() || comicBookmarkUpserts.isNotEmpty() ||
            comicBookmarkDeletes.isNotEmpty() || novelBookmarkInserts.isNotEmpty() ||
            novelBookmarkDeletes.isNotEmpty() || annotationInserts.isNotEmpty() ||
            annotationUpdates.isNotEmpty() || annotationDeletes.isNotEmpty() ||
            sessionInserts.isNotEmpty() || purifyInserts.isNotEmpty() ||
            bookDeletes.isNotEmpty() || tombstonesToRecord.isNotEmpty() ||
            prefsUpdates.isNotEmpty()
    }

    // ---------- 本机 doc 构建 ----------

    private suspend fun buildOwnDoc(config: SyncConfig): SyncDeviceDoc {
        val now = System.currentTimeMillis()
        val books = db.querySyncBookRows().map { row ->
            val progress = db.queryProgressRow(row.id)?.let {
                SyncProgress(it.page, it.percent, it.finished, it.sessionSeconds, it.updatedAt)
            }
            val novelProgress = db.queryNovelProgress(row.id)?.let {
                SyncNovelProgress(it.chapterIndex, it.charOffset, it.updatedAt)
            }
            val comicBookmarks = db.queryComicBookmarksRow(row.id).map {
                SyncComicBookmark(it.pageIndex, it.createdAt, it.note)
            }
            val novelBookmarks = db.queryBookmarks(row.id).map {
                SyncNovelBookmark(it.chapterIdx, it.charStart, it.snippet, it.createdAt)
            }
            val annotations = db.queryAnnotations(row.id).map {
                SyncAnnotation(
                    chapterIdx = it.chapterIdx,
                    charStart = it.charStart,
                    charEnd = it.charEnd,
                    snippet = it.snippet,
                    note = it.note,
                    color = it.color,
                    style = it.style,
                    kind = it.kind,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            }
            val sessions = db.querySyncSessions(row.id).map {
                SyncReadingSession(it.startedAt, it.endedAt, it.durationMs, it.pagesRead)
            }
            SyncBookState(
                bookId = row.id,
                title = row.title,
                format = row.format,
                fileSize = row.fileSize,
                readStatus = row.readStatus,
                progress = progress,
                novelProgress = novelProgress,
                comicBookmarks = comicBookmarks,
                novelBookmarks = novelBookmarks,
                annotations = annotations,
                readingSessions = sessions,
                bookPrefs = readBookPrefs(row.id),
            )
        }
        val tombstones = db.querySyncTombstones().map {
            SyncTombstone(it.kind, it.key, it.deletedAt)
        }
        val purify = db.queryPurifyRules().filter { !it.builtin }.map {
            SyncPurifyRule(it.name, it.pattern, it.enabled)
        }
        return SyncDeviceDoc(
            deviceId = config.deviceId,
            deviceName = config.deviceName,
            updatedAt = now,
            books = books,
            tombstones = tombstones,
            purifyRules = purify,
        )
    }

    private suspend fun readBookPrefs(bookId: String): SyncBookPrefs? {
        val prefs = settings.snapshotPreferences()
        val raw = settings.stringOf(prefs, SettingsKeys.readerBookKey(bookId), "")
        if (raw.isBlank()) return null
        val ts = settings.longOf(prefs, SettingsKeys.bookPrefTsKey(bookId), 0L)
        return SyncBookPrefs(raw, ts)
    }

    private suspend fun buildLocalSnapshot(ownDoc: SyncDeviceDoc): SyncMerge.LocalSnapshot {
        val annotationIds = HashMap<String, HashMap<String, Long>>()
        val novelBookmarkIds = HashMap<String, HashMap<String, Long>>()
        val bookIds = ownDoc.books.map { it.bookId }
        val annotationsByBook = bookIds.associateWith { id -> db.queryAnnotations(id) }
        val bookmarksByBook = bookIds.associateWith { id -> db.queryBookmarks(id) }
        annotationsByBook.forEach { (id, list) ->
            val map = HashMap<String, Long>()
            list.forEach { map["${it.chapterIdx}:${it.charStart}:${it.charEnd}:${it.kind}"] = it.id }
            annotationIds[id] = map
        }
        bookmarksByBook.forEach { (id, list) ->
            val map = HashMap<String, Long>()
            list.forEach { map["${it.chapterIdx}:${it.charStart}"] = it.id }
            novelBookmarkIds[id] = map
        }
        val localBooks = ownDoc.books.associate { state ->
            state.bookId to SyncMerge.LocalBook(
                state = state,
                annotationIdByKey = annotationIds[state.bookId] ?: emptyMap(),
                novelBookmarkIdByKey = novelBookmarkIds[state.bookId] ?: emptyMap(),
            )
        }
        val chapterCounts = db.queryNovelBookIds().associateWith { db.queryNovelChapterCount(it) }
        val purifyKeys = db.queryPurifyRules().map { it.name + 0.toChar() + it.pattern }.toSet()
        return SyncMerge.LocalSnapshot(
            books = localBooks,
            tombstones = ownDoc.tombstones,
            purifyKeys = purifyKeys,
            chapterCounts = chapterCounts,
        )
    }

    // ---------- apply（事务写回） ----------

    /** 合并变更集事务写回：任一失败整体回滚，绝不污染本地库。 */
    private suspend fun applyMerge(result: SyncMerge.MergeResult) {
        val sql = db.writableDatabase
        sql.beginTransaction()
        try {
            // 远端 book 墓碑胜出 → 本地级联删（书架移除，不动本地源文件）
            db.cascadeDeleteBooksInTransaction(sql, result.bookDeletes)

            result.progressWins.forEach { (bookId, p) ->
                sql.insertWithOnConflict(
                    "progress",
                    null,
                    android.content.ContentValues().apply {
                        put("book_id", bookId)
                        put("page", p.page)
                        put("percent", p.percent)
                        put("finished", p.finished)
                        put("session_seconds", p.sessionSeconds)
                        put("updated_at", p.updatedAt)
                    },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            result.novelProgressWins.forEach { (bookId, np) ->
                sql.insertWithOnConflict(
                    "novel_progress",
                    null,
                    android.content.ContentValues().apply {
                        put("book_id", bookId)
                        put("chapter_idx", np.chapterIdx)
                        put("char_offset", np.charOffset)
                        put("updated_at", np.updatedAt)
                    },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            result.readStatusUpdates.forEach { (bookId, status) ->
                sql.update(
                    "books",
                    android.content.ContentValues().apply {
                        put("read_status", status)
                        put("updated_at", System.currentTimeMillis())
                    },
                    "id = ?",
                    arrayOf(bookId),
                )
            }
            result.comicBookmarkUpserts.forEach { (bookId, list) ->
                list.forEach { bm ->
                    sql.insertWithOnConflict(
                        "page_bookmarks",
                        null,
                        android.content.ContentValues().apply {
                            put("book_id", bookId)
                            put("page_index", bm.pageIndex)
                            put("created_at", bm.createdAt)
                            bm.note?.let { put("note", it) }
                        },
                        android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
            result.comicBookmarkDeletes.forEach { (bookId, pages) ->
                pages.forEach { page ->
                    sql.delete("page_bookmarks", "book_id = ? AND page_index = ?", arrayOf(bookId, page.toString()))
                }
            }
            result.novelBookmarkInserts.forEach { (bookId, list) ->
                list.forEach { bm ->
                    sql.insertWithOnConflict(
                        "bookmarks",
                        null,
                        android.content.ContentValues().apply {
                            put("book_id", bookId)
                            put("chapter_idx", bm.chapterIdx)
                            put("char_start", bm.charStart)
                            put("snippet", bm.snippet)
                            put("created_at", bm.createdAt)
                        },
                        android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
                    )
                }
            }
            result.novelBookmarkDeletes.forEach { id ->
                sql.delete("bookmarks", "id = ?", arrayOf(id.toString()))
            }
            result.annotationInserts.forEach { (bookId, list) ->
                list.forEach { ann ->
                    sql.insert(
                        "annotations",
                        null,
                        android.content.ContentValues().apply {
                            put("book_id", bookId)
                            put("kind", ann.kind)
                            put("chapter_idx", ann.chapterIdx)
                            put("char_start", ann.charStart)
                            put("char_end", ann.charEnd)
                            put("snippet", ann.snippet)
                            ann.note?.let { put("note", it) }
                            put("color", ann.color)
                            put("style", ann.style)
                            put("created_at", ann.createdAt)
                            put("updated_at", ann.updatedAt)
                        },
                    )
                }
            }
            result.annotationUpdates.forEach { (id, ann) ->
                sql.update(
                    "annotations",
                    android.content.ContentValues().apply {
                        put("snippet", ann.snippet)
                        put("note", ann.note)
                        put("color", ann.color)
                        put("style", ann.style)
                        put("char_end", ann.charEnd)
                        put("updated_at", ann.updatedAt)
                    },
                    "id = ?",
                    arrayOf(id.toString()),
                )
            }
            result.annotationDeletes.forEach { id ->
                sql.delete("annotations", "id = ?", arrayOf(id.toString()))
            }
            result.sessionInserts.forEach { (bookId, list) ->
                list.forEach { session ->
                    val exists = sql.rawQuery(
                        "SELECT 1 FROM reading_sessions WHERE book_id = ? AND started_at = ?",
                        arrayOf(bookId, session.startedAt.toString()),
                    ).use { it.moveToFirst() }
                    if (!exists) {
                        sql.insert(
                            "reading_sessions",
                            null,
                            android.content.ContentValues().apply {
                                put("book_id", bookId)
                                put("started_at", session.startedAt)
                                put("ended_at", session.endedAt)
                                put("duration_ms", session.durationMs)
                                put("pages_read", session.pagesRead)
                            },
                        )
                    }
                }
            }
            result.purifyInserts.forEach { rule ->
                val exists = sql.rawQuery(
                    "SELECT 1 FROM purify_rules WHERE name = ? AND pattern = ?",
                    arrayOf(rule.name, rule.pattern),
                ).use { it.moveToFirst() }
                if (!exists) {
                    sql.insert(
                        "purify_rules",
                        null,
                        android.content.ContentValues().apply {
                            put("name", rule.name)
                            put("pattern", rule.pattern)
                            put("builtin", 0)
                            put("enabled", if (rule.enabled) 1 else 0)
                            put("created_at", System.currentTimeMillis())
                        },
                    )
                }
            }
            result.tombstonesToRecord.forEach { tomb ->
                sql.insertWithOnConflict(
                    "sync_tombstones",
                    null,
                    android.content.ContentValues().apply {
                        put("kind", tomb.kind)
                        put("key", tomb.key)
                        put("deleted_at", tomb.deletedAt)
                    },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        // per-book 偏好在事务外走 DataStore（LWW 时间戳一并写）
        result.prefsUpdates.forEach { (bookId, prefs) ->
            settings.putString(SettingsKeys.readerBookKey(bookId), prefs.raw)
            settings.putLong(SettingsKeys.bookPrefTsKey(bookId), prefs.updatedAt)
        }
        // 墓碑过期清理（30 天）顺带执行
        db.purgeSyncTombstones(System.currentTimeMillis() - TOMBSTONE_TTL_MS)
    }

    // ---------- 删除联动与 dirty ----------

    /** 书籍被级联硬删（多选删除/书库移除/回收站彻底清除）后调用：写墓碑 + 触发同步。 */
    fun onBooksPurged(bookIds: Collection<String>) {
        if (bookIds.isEmpty()) return
        scope.launch {
            val now = System.currentTimeMillis()
            db.addSyncTombstones(
                bookIds.map { InkvolDatabase.TombstoneRow(TombstoneKinds.BOOK, TombstoneKeys.book(it), now) },
            )
            // 云端文件删除队列（文件同步批次消费）
            val pending = readStringList(SettingsKeys.SYNC_DELETE_PENDING) + bookIds
            saveStringList(SettingsKeys.SYNC_DELETE_PENDING, pending.distinct())
            markDirty()
            syncNowAsync("删除传播")
        }
    }

    /** 持久脏标记：任何本地数据变动后置位；退后台/下次启动补同步。 */
    fun markDirty() {
        scope.launch { settings.putBoolean(SettingsKeys.SYNC_DIRTY, true) }
    }

    suspend fun hasDirtyFlag(): Boolean =
        settings.booleanOf(settings.snapshotPreferences(), SettingsKeys.SYNC_DIRTY, false)

    suspend fun clearDirtyFlag() = settings.putBoolean(SettingsKeys.SYNC_DIRTY, false)

    // ---------- 成败收尾 ----------

    private fun classify(e: Throwable): Pair<String, String> = when {
        e is DavException && e.statusCode == 401 -> "auth" to "账号或应用密码错误（401）"
        e is DavException && e.statusCode == 403 -> "auth" to "权限被拒绝（403）"
        e is DavException && e.statusCode == 503 -> "server" to "服务端繁忙（503），稍后自动重试"
        e is DavException && e.statusCode in 500..599 -> "server" to "服务器错误（${e.statusCode}）"
        e is DavException && e.statusCode in 400..499 -> "server" to "请求被拒绝（${e.statusCode}）"
        e is DavException -> "network" to "网络错误：${e.message ?: ""}"
        else -> "network" to "网络不可达：${e.message ?: ""}"
    }

    private suspend fun fail(
        startedAt: Long,
        reason: String,
        error: Throwable,
        requests: Int,
    ): SyncLogEntry {
        val (kind, message) = classify(error)
        val entry = SyncLogEntry(at = startedAt, ok = false, requests = requests, errorKind = kind, message = message)
        appendLog(entry, reason)
        return entry
    }

    private suspend fun succeed(
        startedAt: Long,
        reason: String,
        merged: SyncMerge.MergeResult,
        applied: Int,
        requests: Int,
    ): SyncLogEntry {
        val entry = SyncLogEntry(
            at = startedAt,
            ok = true,
            pulled = applied,
            pushed = 1,
            conflicts = merged.conflicts,
            requests = requests,
        )
        appendLog(entry, reason)
        if (applied > 0) {
            library.refresh()
            library.postNotice("同步完成 · 拉取 $applied 项" + if (merged.conflicts > 0) " · ${merged.conflicts} 处冲突" else "")
        }
        return entry
    }

    private suspend fun appendLog(entry: SyncLogEntry, reason: String) {
        val existing = readLog()
        val updated = (listOf(entry) + existing).take(LOG_LIMIT)
        settings.putString(SettingsKeys.SYNC_LOG, json.encodeToString(ListSerializer(SyncLogEntry.serializer()), updated))
        settings.putString(
            SettingsKeys.SYNC_LAST_RESULT,
            if (entry.ok) {
                "${timeText(entry.at)} · 成功 · 拉取 ${entry.pulled} · 冲突 ${entry.conflicts}"
            } else {
                "${timeText(entry.at)} · 失败 · ${entry.message ?: entry.errorKind ?: ""}"
            },
        )
        if (entry.ok) {
            settings.putLong(SettingsKeys.SYNC_LAST_OK_AT, entry.at)
            settings.putInt(SettingsKeys.SYNC_FAILURES, 0)
        } else {
            val failures = settings.intOf(settings.snapshotPreferences(), SettingsKeys.SYNC_FAILURES, 0) + 1
            settings.putInt(SettingsKeys.SYNC_FAILURES, failures)
            if (failures >= FAILURE_COOLDOWN_THRESHOLD) {
                settings.putLong(
                    SettingsKeys.SYNC_COOLDOWN_UNTIL,
                    System.currentTimeMillis() + FAILURE_COOLDOWN_MS,
                )
            }
        }
        if (reason != "手动") refreshState()
    }

    private fun readLog(): List<SyncLogEntry> {
        val raw = settings.cachedPreferences()
            ?.let { settings.stringOf(it, SettingsKeys.SYNC_LOG, "") }
            ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(SyncLogEntry.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun syncLog(): List<SyncLogEntry> = readLog()

    private suspend fun readStringList(key: String): List<String> {
        val raw = settings.stringOf(settings.snapshotPreferences(), key, "")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private suspend fun saveStringList(key: String, values: List<String>) {
        settings.putString(key, json.encodeToString(ListSerializer(String.serializer()), values))
    }

    private fun timeText(at: Long): String =
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(at))

    companion object {
        const val DEVICE_DIR = "devices/"
        const val BOOK_DIR = "books/"
        const val DEFAULT_BASE_PATH = "/Inkvol/"
        const val LOG_LIMIT = 20
        const val TOMBSTONE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val FAILURE_COOLDOWN_THRESHOLD = 3
        const val FAILURE_COOLDOWN_MS = 30L * 60 * 1000
    }
}
