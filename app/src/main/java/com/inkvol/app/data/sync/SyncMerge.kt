package com.inkvol.app.data.sync

/**
 * M4.5 合并引擎（纯函数，无 Android 依赖，可单测）。
 *
 * 原则：
 * - 远端多设备先收敛为「远端共识」（同规则 LWW），再与本地逐字段合并；
 * - 时间 LWW 为主，同毫秒取位置更靠后者；readStatus 跟随进度赢家；
 * - 标注/书签并集去重（复合键），同键内容冲突按 updatedAt/createdAt LWW；
 * - 会话只追加并集（startedAt 键），本地已满 200 条时只补比最旧更新者；
 * - 墓碑：book=对端书架移除（本机更新则保留并计冲突），标注/书签=对端真删；
 *   胜出墓碑落本地表防复活；在读书进度不被覆盖（openBookId 保护）；
 * - 远端 novelProgress.chapterIdx 超出本地章节数 → clamp 到最后一章。
 */
object SyncMerge {

    /** 净化规则复合键分隔符（NUL 字符不会出现在规则名/正则里）。 */
    private val KEY_SEP: String = 0.toChar().toString()

    /** 本机单书快照（doc 构建同源 + 复合键到本地 id 映射，供 LWW 更新与删除定位）。 */
    class LocalBook(
        val state: SyncBookState,
        val annotationIdByKey: Map<String, Long>,
        val novelBookmarkIdByKey: Map<String, Long>,
    )

    /** 本机快照。 */
    class LocalSnapshot(
        val books: Map<String, LocalBook>,
        val tombstones: List<SyncTombstone>,
        val purifyKeys: Set<String>, // name+KEY_SEP+pattern（含内置，占去重空间）
        val chapterCounts: Map<String, Int>,
    )

    /** 合并输出（SyncRepository 事务写回）。全部为「应用到本地」的变更集。 */
    class MergeResult(
        val progressWins: Map<String, SyncProgress> = emptyMap(),
        val novelProgressWins: Map<String, SyncNovelProgress> = emptyMap(),
        val readStatusUpdates: Map<String, String> = emptyMap(),
        val comicBookmarkUpserts: Map<String, List<SyncComicBookmark>> = emptyMap(),
        val comicBookmarkDeletes: Map<String, Set<Int>> = emptyMap(),
        val novelBookmarkInserts: Map<String, List<SyncNovelBookmark>> = emptyMap(),
        val novelBookmarkDeletes: Set<Long> = emptySet(),
        val annotationInserts: Map<String, List<SyncAnnotation>> = emptyMap(),
        val annotationUpdates: Map<Long, SyncAnnotation> = emptyMap(),
        val annotationDeletes: Set<Long> = emptySet(),
        val sessionInserts: Map<String, List<SyncReadingSession>> = emptyMap(),
        val purifyInserts: List<SyncPurifyRule> = emptyList(),
        val bookDeletes: List<String> = emptyList(),
        val tombstonesToRecord: List<SyncTombstone> = emptyList(),
        val prefsUpdates: Map<String, SyncBookPrefs> = emptyMap(),
        val conflicts: Int = 0,
        val pulledBooks: Int = 0,
    )

    /** 远端共识单书容器（多设备文档两两收敛）。 */
    private class RemoteBook {
        var readStatus: String = ""
        var progress: SyncProgress? = null
        var novelProgress: SyncNovelProgress? = null
        var bookPrefs: SyncBookPrefs? = null
        val comicBookmarks = LinkedHashMap<Int, SyncComicBookmark>()
        val novelBookmarks = LinkedHashMap<String, SyncNovelBookmark>()
        val annotations = LinkedHashMap<String, SyncAnnotation>()
        val sessions = LinkedHashMap<Long, SyncReadingSession>()
    }

    private class RemoteConsensus {
        val books = LinkedHashMap<String, RemoteBook>()
        val tombstones = LinkedHashMap<String, SyncTombstone>() // key → 最新 deletedAt
        val purify = LinkedHashMap<String, SyncPurifyRule>()    // name+KEY_SEP+pattern
    }

    fun merge(local: LocalSnapshot, remotes: List<SyncDeviceDoc>, openBookId: String?): MergeResult {
        val consensus = buildConsensus(remotes)
        val localTombstones = LinkedHashMap<String, SyncTombstone>().apply {
            local.tombstones.forEach { put(it.key, it) }
        }
        val result = Builder()

        applyTombstones(local, consensus, localTombstones, result)

        consensus.books.forEach { (bookId, remote) ->
            val localBook = local.books[bookId] ?: return@forEach
            if (isBookTombstoned(bookId, consensus, localTombstones)) return@forEach
            mergeBook(bookId, localBook, remote, consensus, localTombstones, local, openBookId, result)
        }

        mergePurify(local, consensus, result)
        return result.build()
    }

    private fun buildConsensus(remotes: List<SyncDeviceDoc>): RemoteConsensus {
        val consensus = RemoteConsensus()
        remotes.forEach { doc ->
            doc.tombstones.forEach { tomb ->
                val existing = consensus.tombstones[tomb.key]
                if (existing == null || tomb.deletedAt > existing.deletedAt) {
                    consensus.tombstones[tomb.key] = tomb
                }
            }
            doc.purifyRules.forEach { rule ->
                if (rule.name.isNotBlank() && rule.pattern.isNotBlank()) {
                    consensus.purify[purifyKey(rule.name, rule.pattern)] = rule
                }
            }
            doc.books.forEach { book ->
                val target = consensus.books.getOrPut(book.bookId) { RemoteBook() }
                target.readStatus = book.readStatus
                target.progress = pickProgress(target.progress, book.progress)
                target.novelProgress = pickNovelProgress(target.novelProgress, book.novelProgress)
                book.bookPrefs?.let { prefs ->
                    val current = target.bookPrefs
                    if (current == null || prefs.updatedAt > current.updatedAt) target.bookPrefs = prefs
                }
                book.comicBookmarks.forEach { bm ->
                    val current = target.comicBookmarks[bm.pageIndex]
                    if (current == null || bm.createdAt > current.createdAt) {
                        target.comicBookmarks[bm.pageIndex] = bm
                    }
                }
                book.novelBookmarks.forEach { bm ->
                    val key = "${bm.chapterIdx}:${bm.charStart}"
                    val current = target.novelBookmarks[key]
                    if (current == null || bm.createdAt > current.createdAt) {
                        target.novelBookmarks[key] = bm
                    }
                }
                book.annotations.forEach { ann ->
                    val key = annotationKey(ann)
                    val current = target.annotations[key]
                    if (current == null || ann.updatedAt > current.updatedAt) {
                        target.annotations[key] = ann
                    }
                }
                book.readingSessions.forEach { session ->
                    target.sessions.putIfAbsent(session.startedAt, session)
                }
            }
        }
        return consensus
    }

    private fun purifyKey(name: String, pattern: String) = name + KEY_SEP + pattern

    private fun pickProgress(a: SyncProgress?, b: SyncProgress?): SyncProgress? {
        if (a == null) return b
        if (b == null) return a
        return when {
            b.updatedAt > a.updatedAt -> b
            b.updatedAt < a.updatedAt -> a
            else -> if (b.percent > a.percent || b.page > a.page) b else a
        }
    }

    private fun pickNovelProgress(a: SyncNovelProgress?, b: SyncNovelProgress?): SyncNovelProgress? {
        if (a == null) return b
        if (b == null) return a
        return when {
            b.updatedAt > a.updatedAt -> b
            b.updatedAt < a.updatedAt -> a
            else -> if (b.chapterIdx > a.chapterIdx ||
                (b.chapterIdx == a.chapterIdx && b.charOffset > a.charOffset)
            ) b else a
        }
    }

    private fun annotationKey(ann: SyncAnnotation) =
        "${ann.chapterIdx}:${ann.charStart}:${ann.charEnd}:${ann.kind}"

    // ---------- 墓碑应用 ----------

    private fun applyTombstones(
        local: LocalSnapshot,
        consensus: RemoteConsensus,
        localTombstones: MutableMap<String, SyncTombstone>,
        result: Builder,
    ) {
        consensus.tombstones.values.forEach { tomb ->
            // 本地已知（自己删过或已学过）：以较新者为准，不重复动作
            localTombstones[tomb.key]?.let { known ->
                if (tomb.deletedAt > known.deletedAt) localTombstones[tomb.key] = tomb
                return@forEach
            }
            when (tomb.kind) {
                TombstoneKinds.BOOK -> {
                    val bookId = tomb.key.removePrefix(TombstoneKeys.BOOK_PREFIX)
                    val localBook = local.books[bookId]
                    if (localBook == null) {
                        // 本来就没有：记录墓碑（文件同步据此阻止自动下载）
                        result.tombstonesToRecord += tomb
                    } else if (localTouch(localBook) > tomb.deletedAt) {
                        // 删除之后本机还有更新：保留本机，计冲突
                        result.conflicts++
                    } else {
                        result.bookDeletes += bookId
                        result.tombstonesToRecord += tomb
                    }
                }
                TombstoneKinds.ANNOTATION -> {
                    val parts = tomb.key.removePrefix(TombstoneKeys.ANN_PREFIX).split(':')
                    if (parts.size == 5) {
                        val chapterIdx = parts[1].toIntOrNull()
                        val charStart = parts[2].toIntOrNull()
                        val charEnd = parts[3].toIntOrNull()
                        val kind = parts[4]
                        val localBook = local.books[parts[0]]
                        val key = "$chapterIdx:$charStart:$charEnd:$kind"
                        val localId = localBook?.annotationIdByKey?.get(key)
                        if (localId != null) {
                            val updated = localBook.state.annotations
                                .firstOrNull { annotationKey(it) == key }?.updatedAt ?: 0L
                            if (updated > tomb.deletedAt) {
                                result.conflicts++
                            } else {
                                result.annotationDeletes += localId
                                result.tombstonesToRecord += tomb
                            }
                        } else {
                            result.tombstonesToRecord += tomb
                        }
                    }
                }
                TombstoneKinds.NOVEL_BOOKMARK -> {
                    val parts = tomb.key.removePrefix(TombstoneKeys.NBM_PREFIX).split(':')
                    if (parts.size == 3) {
                        val key = "${parts[1]}:${parts[2]}"
                        val localBook = local.books[parts[0]]
                        val localId = localBook?.novelBookmarkIdByKey?.get(key)
                        if (localId != null) {
                            val created = localBook.state.novelBookmarks.firstOrNull {
                                "${it.chapterIdx}:${it.charStart}" == key
                            }?.createdAt ?: 0L
                            if (created > tomb.deletedAt) {
                                result.conflicts++
                            } else {
                                result.novelBookmarkDeletes += localId
                                result.tombstonesToRecord += tomb
                            }
                        } else {
                            result.tombstonesToRecord += tomb
                        }
                    }
                }
                TombstoneKinds.COMIC_BOOKMARK -> {
                    val parts = tomb.key.removePrefix(TombstoneKeys.CBM_PREFIX).split(':')
                    if (parts.size == 2) {
                        val pageIndex = parts[1].toIntOrNull() ?: -1
                        val localBook = local.books[parts[0]]
                        val localBm = localBook?.state?.comicBookmarks
                            ?.firstOrNull { it.pageIndex == pageIndex }
                        if (localBm != null) {
                            if (localBm.createdAt > tomb.deletedAt) {
                                result.conflicts++
                            } else {
                                result.comicBookmarkDeletes.getOrPut(parts[0]) { HashSet() } += pageIndex
                                result.tombstonesToRecord += tomb
                            }
                        } else {
                            result.tombstonesToRecord += tomb
                        }
                    }
                }
            }
        }
    }

    /** 本机该书最后被触及的时间（进度/标注/偏好），用于墓碑 vs 本地更新的 LWW。 */
    private fun localTouch(book: LocalBook): Long = maxOf(
        book.state.progress?.updatedAt ?: 0L,
        book.state.novelProgress?.updatedAt ?: 0L,
        book.state.bookPrefs?.updatedAt ?: 0L,
        book.state.annotations.maxOfOrNull { it.updatedAt } ?: 0L,
    )

    private fun isBookTombstoned(
        bookId: String,
        consensus: RemoteConsensus,
        localTombstones: Map<String, SyncTombstone>,
    ): Boolean {
        val key = TombstoneKeys.book(bookId)
        val remote = consensus.tombstones[key]?.deletedAt ?: 0L
        val local = localTombstones[key]?.deletedAt ?: 0L
        return maxOf(remote, local) > 0L
    }

    // ---------- 单书状态合并 ----------

    private fun mergeBook(
        bookId: String,
        local: LocalBook,
        remote: RemoteBook,
        consensus: RemoteConsensus,
        localTombstones: Map<String, SyncTombstone>,
        snapshot: LocalSnapshot,
        openBookId: String?,
        result: Builder,
    ) {
        result.pulledBooks++
        val protected = bookId == openBookId

        // 进度（漫画）：在读书保护
        val localProgress = local.state.progress
        val remoteProgress = remote.progress
        if (!protected && remoteProgress != null) {
            val winner = pickProgress(localProgress, remoteProgress)
            if (winner === remoteProgress && winner != localProgress) {
                if (localProgress != null && differs(localProgress, remoteProgress)) {
                    result.conflicts++
                }
                result.progressWins[bookId] = winner.copy(
                    sessionSeconds = maxOf(
                        localProgress?.sessionSeconds ?: 0,
                        remoteProgress.sessionSeconds,
                    ),
                )
                result.readStatusUpdates[bookId] = remote.readStatus
            }
        }

        // 小说进度：LWW + 章节 clamp（防版本间解析差异）
        val localNovel = local.state.novelProgress
        val remoteNovel = remote.novelProgress
        if (!protected && remoteNovel != null) {
            val chapterCount = snapshot.chapterCounts[bookId] ?: 0
            val clamped = if (chapterCount > 0 && remoteNovel.chapterIdx >= chapterCount) {
                remoteNovel.copy(chapterIdx = chapterCount - 1)
            } else {
                remoteNovel
            }
            val winner = pickNovelProgress(localNovel, clamped)
            if (winner === clamped && winner != localNovel) {
                if (localNovel != null &&
                    (winner.chapterIdx != localNovel.chapterIdx ||
                        winner.charOffset != localNovel.charOffset)
                ) {
                    result.conflicts++
                }
                result.novelProgressWins[bookId] = winner
                result.readStatusUpdates[bookId] = remote.readStatus
            }
        }

        // 漫画书签
        remote.comicBookmarks.forEach { (pageIndex, remoteBm) ->
            val tombKey = TombstoneKeys.comicBookmark(bookId, pageIndex)
            val tomb = newestTombstone(localTombstones, consensus, tombKey)
            if (tomb != null && remoteBm.createdAt <= tomb.deletedAt) return@forEach
            val localBm = local.state.comicBookmarks.firstOrNull { it.pageIndex == pageIndex }
            when {
                localBm == null ->
                    result.comicBookmarkUpserts.getOrPut(bookId) { ArrayList() } += remoteBm
                remoteBm.createdAt > localBm.createdAt -> {
                    result.comicBookmarkUpserts.getOrPut(bookId) { ArrayList() } += remoteBm
                    if (remoteBm.note != localBm.note) result.conflicts++
                }
            }
        }

        // 小说书签
        remote.novelBookmarks.forEach { (_, remoteBm) ->
            val tombKey = TombstoneKeys.novelBookmark(bookId, remoteBm.chapterIdx, remoteBm.charStart)
            val tomb = newestTombstone(localTombstones, consensus, tombKey)
            if (tomb != null && remoteBm.createdAt <= tomb.deletedAt) return@forEach
            val localBm = local.state.novelBookmarks.firstOrNull {
                it.chapterIdx == remoteBm.chapterIdx && it.charStart == remoteBm.charStart
            }
            if (localBm == null) {
                result.novelBookmarkInserts.getOrPut(bookId) { ArrayList() } += remoteBm
            } else if (remoteBm.createdAt > localBm.createdAt) {
                result.novelBookmarkInserts.getOrPut(bookId) { ArrayList() } += remoteBm
            }
        }

        // 标注
        remote.annotations.forEach { (key, remoteAnn) ->
            val tombKey = TombstoneKeys.annotation(
                bookId,
                remoteAnn.chapterIdx,
                remoteAnn.charStart,
                remoteAnn.charEnd,
                remoteAnn.kind,
            )
            val tomb = newestTombstone(localTombstones, consensus, tombKey)
            if (tomb != null && remoteAnn.updatedAt <= tomb.deletedAt) return@forEach
            val localAnn = local.state.annotations.firstOrNull { annotationKey(it) == key }
            val localId = local.annotationIdByKey[key]
            when {
                localAnn == null || localId == null ->
                    result.annotationInserts.getOrPut(bookId) { ArrayList() } += remoteAnn
                remoteAnn.updatedAt > localAnn.updatedAt -> {
                    result.annotationUpdates[localId] = remoteAnn
                    result.conflicts++
                }
                remoteAnn.updatedAt < localAnn.updatedAt -> Unit // 本地胜出，push 覆盖远端
                else -> if (contentDiffers(localAnn, remoteAnn)) {
                    result.annotationUpdates[localId] = remoteAnn
                    result.conflicts++
                }
            }
        }

        // 会话：只追加，接近上限时只补比本地最旧更新者
        val localSessions = local.state.readingSessions
        val cutoff = if (localSessions.size >= 200) {
            localSessions.minOfOrNull { it.startedAt } ?: 0L
        } else {
            0L
        }
        remote.sessions.values.forEach { session ->
            if (session.startedAt <= cutoff) return@forEach
            val exists = localSessions.any { it.startedAt == session.startedAt }
            if (!exists) {
                result.sessionInserts.getOrPut(bookId) { ArrayList() } += session
            }
        }

        // per-book 偏好：LWW（ReaderScreen 落盘时间戳为准）
        remote.bookPrefs?.let { remotePrefs ->
            val localPrefs = local.state.bookPrefs
            if (localPrefs == null || remotePrefs.updatedAt > localPrefs.updatedAt) {
                if (remotePrefs.raw != localPrefs?.raw) {
                    result.prefsUpdates[bookId] = remotePrefs
                }
            }
        }
    }

    private fun newestTombstone(
        localTombstones: Map<String, SyncTombstone>,
        consensus: RemoteConsensus,
        key: String,
    ): SyncTombstone? {
        val a = localTombstones[key]
        val b = consensus.tombstones[key]
        return when {
            a == null -> b
            b == null -> a
            else -> if (b.deletedAt > a.deletedAt) b else a
        }
    }

    private fun differs(a: SyncProgress, b: SyncProgress) =
        a.page != b.page || a.percent != b.percent

    private fun contentDiffers(a: SyncAnnotation, b: SyncAnnotation) =
        a.note != b.note || a.color != b.color || a.style != b.style ||
            a.snippet != b.snippet || a.charEnd != b.charEnd

    private fun mergePurify(local: LocalSnapshot, consensus: RemoteConsensus, result: Builder) {
        consensus.purify.values.forEach { rule ->
            val key = purifyKey(rule.name, rule.pattern)
            if (key !in local.purifyKeys) {
                result.purifyInserts += rule
            }
        }
    }

    private class Builder {
        val progressWins = LinkedHashMap<String, SyncProgress>()
        val novelProgressWins = LinkedHashMap<String, SyncNovelProgress>()
        val readStatusUpdates = LinkedHashMap<String, String>()
        val comicBookmarkUpserts = LinkedHashMap<String, MutableList<SyncComicBookmark>>()
        val comicBookmarkDeletes = LinkedHashMap<String, MutableSet<Int>>()
        val novelBookmarkInserts = LinkedHashMap<String, MutableList<SyncNovelBookmark>>()
        val novelBookmarkDeletes = LinkedHashSet<Long>()
        val annotationInserts = LinkedHashMap<String, MutableList<SyncAnnotation>>()
        val annotationUpdates = LinkedHashMap<Long, SyncAnnotation>()
        val annotationDeletes = LinkedHashSet<Long>()
        val sessionInserts = LinkedHashMap<String, MutableList<SyncReadingSession>>()
        val purifyInserts = ArrayList<SyncPurifyRule>()
        val bookDeletes = ArrayList<String>()
        val tombstonesToRecord = ArrayList<SyncTombstone>()
        val prefsUpdates = LinkedHashMap<String, SyncBookPrefs>()
        var conflicts = 0
        var pulledBooks = 0

        fun build(): MergeResult = MergeResult(
            progressWins = progressWins,
            novelProgressWins = novelProgressWins,
            readStatusUpdates = readStatusUpdates,
            comicBookmarkUpserts = comicBookmarkUpserts,
            comicBookmarkDeletes = comicBookmarkDeletes,
            novelBookmarkInserts = novelBookmarkInserts,
            novelBookmarkDeletes = novelBookmarkDeletes,
            annotationInserts = annotationInserts,
            annotationUpdates = annotationUpdates,
            annotationDeletes = annotationDeletes,
            sessionInserts = sessionInserts,
            purifyInserts = purifyInserts,
            bookDeletes = bookDeletes,
            tombstonesToRecord = tombstonesToRecord,
            prefsUpdates = prefsUpdates,
            conflicts = conflicts,
            pulledBooks = pulledBooks,
        )
    }
}
