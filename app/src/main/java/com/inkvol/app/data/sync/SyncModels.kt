package com.inkvol.app.data.sync

import kotlinx.serialization.Serializable

/** 同步文档格式版本：结构破坏性变更时 +1；旧端读到更高版本应跳过合并并提示升级。 */
const val SYNC_FORMAT_VERSION = 1

/** 墓碑种类。 */
object TombstoneKinds {
    const val BOOK = "book"
    const val ANNOTATION = "annotation"
    const val COMIC_BOOKMARK = "comic_bookmark"
    const val NOVEL_BOOKMARK = "novel_bookmark"
}

/**
 * 墓碑键构造（跨设备稳定：不含本地自增 id，前缀区分种类）。
 * 删除传播的唯一凭据：pull 端以此判定「对端条目已被某设备删除」。
 */
object TombstoneKeys {
    const val BOOK_PREFIX = "book:"
    const val ANN_PREFIX = "ann:"
    const val CBM_PREFIX = "cbm:"
    const val NBM_PREFIX = "nbm:"

    fun book(bookId: String) = BOOK_PREFIX + bookId

    fun annotation(bookId: String, chapterIdx: Int, charStart: Int, charEnd: Int, kind: String) =
        "ann:$bookId:$chapterIdx:$charStart:$charEnd:$kind"

    fun comicBookmark(bookId: String, pageIndex: Int) = "cbm:$bookId:$pageIndex"

    fun novelBookmark(bookId: String, chapterIdx: Int, charStart: Int) =
        "nbm:$bookId:$chapterIdx:$charStart"
}

// ---------- 云端文档模型（@Serializable；字段口径与 BackupModels 对齐，不发明第二套） ----------

@Serializable
data class SyncProgress(
    val page: Int = 0,
    val percent: Double = 0.0,
    val finished: Int = 0,
    val sessionSeconds: Int = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class SyncNovelProgress(
    val chapterIdx: Int = 0,
    val charOffset: Int = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class SyncComicBookmark(
    val pageIndex: Int,
    val createdAt: Long,
    val note: String? = null,
)

@Serializable
data class SyncNovelBookmark(
    val chapterIdx: Int,
    val charStart: Int,
    val snippet: String,
    val createdAt: Long,
)

@Serializable
data class SyncAnnotation(
    val chapterIdx: Int,
    val charStart: Int,
    val charEnd: Int,
    val snippet: String,
    val note: String? = null,
    val color: String = "yellow",
    val style: String = "bg",
    val kind: String = "highlight",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class SyncReadingSession(
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationMs: Long = 0,
    val pagesRead: Int = 0,
)

/** per-book 阅读偏好：DataStore 里的原始编码串 + 变更时间戳（LWW 依据）。 */
@Serializable
data class SyncBookPrefs(
    val raw: String,
    val updatedAt: Long,
)

@Serializable
data class SyncBookState(
    val bookId: String,
    val title: String,
    val format: String,
    val fileSize: Long,
    val readStatus: String,
    val progress: SyncProgress? = null,
    val novelProgress: SyncNovelProgress? = null,
    val comicBookmarks: List<SyncComicBookmark> = emptyList(),
    val novelBookmarks: List<SyncNovelBookmark> = emptyList(),
    val annotations: List<SyncAnnotation> = emptyList(),
    val readingSessions: List<SyncReadingSession> = emptyList(),
    val bookPrefs: SyncBookPrefs? = null,
)

/** 自定义净化规则（同步合并用；内置规则以各端本机为准不互传，仅占用 name+pattern 去重空间）。 */
@Serializable
data class SyncPurifyRule(
    val name: String,
    val pattern: String,
    val enabled: Boolean = true,
)

/**
 * 删除墓碑：本地硬删除时写入，pull 端据此阻止「已删内容被远端拉回」，
 * push 端把它写给其它设备执行真删。保留 30 天后可清理。
 */
@Serializable
data class SyncTombstone(
    val kind: String,
    val key: String,
    val deletedAt: Long,
)

/**
 * 每设备唯一云端文档：/Inkvol/devices/<deviceId>.json（只写自己的，零写冲突）。
 */
@Serializable
data class SyncDeviceDoc(
    val formatVersion: Int = SYNC_FORMAT_VERSION,
    val deviceId: String,
    val deviceName: String,
    val updatedAt: Long,
    val books: List<SyncBookState> = emptyList(),
    val tombstones: List<SyncTombstone> = emptyList(),
    val purifyRules: List<SyncPurifyRule> = emptyList(),
)

// ---------- 运行时模型（不落盘到云端） ----------

/** 单条同步记录（最近 20 条持久化于 DataStore）。 */
@Serializable
data class SyncLogEntry(
    val at: Long,
    val ok: Boolean,
    val pulled: Int = 0,
    val pushed: Int = 0,
    val conflicts: Int = 0,
    val requests: Int = 0,
    val errorKind: String? = null, // network / auth / server / offline / format / cancelled
    val message: String? = null,
)

/** 远端设备文档（文件名 = <deviceId>.json）。bytes 为 null 表示 Last-Modified 未变，跳过下载。 */
class RemoteDoc(
    val deviceId: String,
    val lastModified: Long,
    val bytes: ByteArray?,
)
