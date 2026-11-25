package com.inkvol.app.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupPreference(
    val key: String,
    val type: String,
    val value: String,
)

@Serializable
data class BackupCategory(
    val id: Long,
    val name: String,
    val color: String,
    val sortKey: Int,
    val createdAt: Long,
)

@Serializable
data class BackupProgress(
    val page: Int,
    val percent: Double,
    val finished: Int,
    val sessionSeconds: Int,
    val updatedAt: Long,
)

@Serializable
data class BackupBookmark(
    val pageIndex: Int,
    val createdAt: Long,
    val note: String? = null,
)

@Serializable
data class BackupReadingSession(
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationMs: Long = 0,
    val pagesRead: Int = 0,
)

/** M6.3-D：小说标注（划线/笔记）。 */
@Serializable
data class BackupAnnotation(
    val chapterIdx: Int,
    val charStart: Int,
    val charEnd: Int,
    val snippet: String,
    val note: String? = null,
    val color: String,
    val style: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** M6.3-D：小说书签（章内 char 定位）。 */
@Serializable
data class BackupNovelBookmark(
    val chapterIdx: Int,
    val charStart: Int,
    val snippet: String,
    val createdAt: Long,
)

/** M6.3-D：小说阅读进度（章 + 章内 char 偏移）。 */
@Serializable
data class BackupNovelProgress(
    val chapterIdx: Int,
    val charOffset: Int,
    val updatedAt: Long,
)

/** M6.3-D：净化规则（全局；恢复时只合并自定义规则，内置以本机为准）。 */
@Serializable
data class BackupPurifyRule(
    val name: String,
    val pattern: String,
    val builtin: Boolean = false,
    val enabled: Boolean = true,
)

/** P3：漫画章节条目（派生数据，仅备份防重算）。 */
@Serializable
data class BackupSection(
    val idx: Int,
    val title: String,
    val pageStart: Int,
)

@Serializable
data class BackupBook(
    val id: String,
    val title: String,
    val format: String,
    val fileSize: Long,
    val pageCount: Int,
    val readStatus: String,
    val categoryIds: List<Long> = emptyList(),
    val progress: BackupProgress? = null,
    val pageBookmarks: List<BackupBookmark> = emptyList(),
    val readingSessions: List<BackupReadingSession> = emptyList(),
    // M6.3-D：小说相关数据（EPUB/TXT）
    val novelProgress: BackupNovelProgress? = null,
    val novelBookmarks: List<BackupNovelBookmark> = emptyList(),
    val annotations: List<BackupAnnotation> = emptyList(),
    // P3：漫画章节区间（缺省空 = 旧备份兼容，恢复后由阅读器 write-through 补齐）
    val sections: List<BackupSection> = emptyList(),
)

@Serializable
data class BackupData(
    val version: Int,
    val app: String = "Inkvol",
    val generatedAt: Long,
    val categories: List<BackupCategory> = emptyList(),
    val books: List<BackupBook> = emptyList(),
    val preferences: List<BackupPreference> = emptyList(),
    val purifyRules: List<BackupPurifyRule> = emptyList(),
)

@Serializable
data class BackupPreview(
    val version: Int,
    val generatedAt: Long,
    val bookCount: Int,
    val availableBookCount: Int,
    val missingBookCount: Int,
    val missingBookTitles: List<String> = emptyList(),
    val categoryCount: Int,
    val sessionCount: Int,
    val preferenceCount: Int,
    val annotationCount: Int = 0,
    val novelBookmarkCount: Int = 0,
    val purifyRuleCount: Int = 0,
    val isValid: Boolean,
)
