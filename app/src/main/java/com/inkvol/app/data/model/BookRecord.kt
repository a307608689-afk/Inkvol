package com.inkvol.app.data.model

/** 书架上的最小书本模型。 */
data class BookRecord(
    val id: String,
    val type: String,
    val title: String,
    val series: String?,
    val volume: String?,
    val writer: String?,
    val tags: String?,
    val pageCount: Int,
    val format: String,
    val fileSize: Long,
    val coverPath: String?,
    val addedAt: Long,
    val docId: String,
    val displayPath: String,
    val readStatus: String = READ_UNREAD,
    val percent: Double = 0.0,
    /**
     * 最近阅读时间（漫画 progress.updated_at 与小说 novel_progress.updated_at 取 MAX），
     * 未读过恒 0——书架「最近阅读」排序档的数据源，SQL 里保证有值。
     */
    val lastReadAt: Long = 0,
    /** SAF 树 URI；单文件导入为 inkvol://single-file */
    val treeUri: String? = null,
) {
    companion object {
        const val READ_UNREAD = "UNREAD"
        const val READ_READING = "READING"
        const val READ_FINISHED = "FINISHED"
    }
}

/** 用户书夹（分类）。 */
data class CategoryRecord(
    val id: Long,
    val name: String,
    val color: String,
    val sortKey: Int,
    val bookCount: Int,
)

/** 系统虚拟书夹。 */
enum class SystemCategory(val label: String) {
    UNREAD("未读"),
    READING("在读"),
    FINISHED("读完"),
    BOOKMARKED("有书签"),
    UNCLASSIFIED("未分类"),
}

/** 扫描进度。running=false 时 error 非空表示上次扫描失败。 */
data class ScanState(
    val running: Boolean = false,
    val scanned: Int = 0,
    val added: Int = 0,
    val current: String = "",
    val error: String? = null,
)

/** M4-B 本地阅读统计总览。 */
data class ReadingStats(
    val totalDurationMs: Long = 0,
    val totalPagesRead: Long = 0,
    val sessionCount: Long = 0,
    val bookCount: Long = 0,
    val last7DaysDurationMs: Long = 0,
    val last30DaysDurationMs: Long = 0,
)

/** M6.3-C：文本净化规则（导入时按行过滤广告/水印/推广行）。 */
data class PurifyRuleRecord(
    val id: Long,
    val name: String,
    val pattern: String,
    val builtin: Boolean,
    val enabled: Boolean,
)

/** 历史页的一本最近阅读书。 */
data class ReadingHistoryEntry(
    val book: BookRecord,
    val totalDurationMs: Long,
    val totalPagesRead: Long,
    val lastReadAt: Long,
)

/** M4-E 多书库管理。 */
data class LibraryRecord(
    val id: Long,
    val name: String,
    val rootTreeUri: String,
    val hidden: Boolean,
    val createdAt: Long,
    val bookCount: Int,
    val trashCount: Int,
)

/** 回收站条目；deletedAt 用于 30 天保留期。 */
data class TrashBook(
    val book: BookRecord,
    val deletedAt: Long,
) {
    val daysUntilPurge: Int
        get() = ((deletedAt + 30L * 24 * 60 * 60 * 1000 - System.currentTimeMillis())
            .coerceAtLeast(0) / (24 * 60 * 60 * 1000)).toInt() + 1
}

/** M4-E 疑似重复分组。 */
data class DuplicateGroup(
    val title: String,
    val format: String,
    val pageCount: Int,
    val fileSize: Long,
    val books: List<BookRecord>,
)

/** M4-E 书内重复页检测报告。 */
data class BookDuplicatePageReport(
    val bookId: String,
    val title: String,
    val pageCount: Int,
    val groups: List<List<Int>>,
) {
    val duplicatePageCount: Int get() = groups.sumOf { it.size }
}

/** M6.1-A TXT 章节区间；byte 偏移指向规范化 UTF-8 文本文件。 */
data class NovelChapterRecord(
    val index: Int,
    val title: String,
    val byteStart: Long,
    val byteEnd: Long,
    val charCount: Int,
) {
    val isWholeBook: Boolean get() = index == 0
}

/** M6.1-B 小说进度；章节 + 字符偏移锚定，字号/字体变化不漂移。 */
data class NovelProgressRecord(
    val chapterIndex: Int,
    val charOffset: Int,
    val updatedAt: Long,
)

/** M6.3-A 小说标注：highlight（note 非空 = 带笔记）/ bookmark，锚定章节 + 字符区间。 */
data class AnnotationRecord(
    val id: Long,
    val bookId: String,
    val kind: String,
    val chapterIdx: Int,
    val charStart: Int,
    val charEnd: Int,
    val snippet: String,
    val note: String?,
    val color: String,
    val style: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** M6.3-B 小说书签：锚定章节 + 章内字符偏移。 */
data class BookmarkRecord(
    val id: Long,
    val bookId: String,
    val chapterIdx: Int,
    val charStart: Int,
    val snippet: String,
    val createdAt: Long,
)

/** M6.1-B 小说阅读排版偏好；per-book 保存。 */
@kotlinx.serialization.Serializable
data class NovelReadingPrefs(
    val fontId: String = "system",
    val fontSize: Int = 18,
    val lineHeightRatio: Float = 1.5f,
    val letterSpacing: Int = 0,
    val paragraphSpacing: Int = 8,
    val indentChars: Int = 2,
    val pagePadding: Int = 24,
    val theme: String = "paper",
    val pagingMode: String = "horizontal",
)

/** M6.1-B 已导入字体。 */
@kotlinx.serialization.Serializable
data class NovelFontRecord(
    val id: String,
    val name: String,
    val path: String,
)
