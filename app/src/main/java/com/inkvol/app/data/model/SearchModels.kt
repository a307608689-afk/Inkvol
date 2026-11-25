package com.inkvol.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * M7 全局搜索模型：筛选 / 模板 / 章节·正文命中。
 *
 * 筛选持久化走 kotlinx.serialization（与备份链路同款），三态语义对齐 SSMH：
 * 点一下「只看」（include）→ 再点「屏蔽」（exclude）→ 第三下取消。
 * 「未分类」虚拟项覆盖「不在书夹」诉求。
 */

/** 三态筛选集合：include=只看、exclude=屏蔽。 */
@Serializable
data class TriStateSet(
    val include: Set<String> = emptySet(),
    val exclude: Set<String> = emptySet(),
) {
    val isActive: Boolean get() = include.isNotEmpty() || exclude.isNotEmpty()
    val count: Int get() = include.size + exclude.size

    /** 单值字段（系列/作者）判定：命中 exclude 滤掉；include 非空时任一命中才保留。 */
    fun matchesValue(value: String?): Boolean {
        if (include.isEmpty() && exclude.isEmpty()) return true
        if (value != null && value in exclude) return false
        if (include.isEmpty()) return true
        return value != null && value in include
    }

    /** 多值字段（标签）判定：include 任一命中即过（OR），命中 exclude 任一即滤掉。 */
    fun matchesAny(values: Collection<String>): Boolean {
        if (include.isEmpty() && exclude.isEmpty()) return true
        if (values.any { it in exclude }) return false
        if (include.isEmpty()) return true
        return values.any { it in include }
    }
}

/** 全局搜索筛选（DataStore JSON 持久化；筛选记忆与模板共用）。 */
@Serializable
data class SearchFilter(
    val type: String? = null, // null=全部 / COMIC / NOVEL（单选）
    val categories: TriStateSet = TriStateSet(), // 书夹 id；含 UNCLASSIFIED_ID=未分类
    val folders: TriStateSet = TriStateSet(), // folder id（Long 字符串化）
    val series: TriStateSet = TriStateSet(), // 系列名精确值
    val tags: TriStateSet = TriStateSet(), // 标签名精确值
    val writers: TriStateSet = TriStateSet(), // 作者名精确值
    val readStatus: String? = null, // null / UNREAD / READING / FINISHED（单选）
    val formats: Set<String> = emptySet(), // FOLDER/CBZ/CBR/CB7/PDF/TXT/EPUB/MOBI
    /** 分区筛选（多选）：参与结果页的模块。缺省=全开（旧筛选/模板反序列化走默认值）。 */
    val sections: Set<String> = ALL_SECTIONS,
) {
    /** 分区开关（结果页按此隐藏对应模块；查询层同步短路）。 */
    fun sectionOn(id: String): Boolean = id in sections

    /** 角标口径：类型≠空 + 状态≠空 + 格式非空 + 分区非全开 + 各三态 include+exclude 计数。 */
    fun activeCount(): Int =
        (if (type != null) 1 else 0) +
            categories.count + folders.count + series.count + tags.count +
            writers.count +
            (if (readStatus != null) 1 else 0) +
            formats.size +
            (if (sections != ALL_SECTIONS) 1 else 0)

    fun isEmpty(): Boolean = activeCount() == 0

    /** 无需外部映射的字段判定（书夹/文件夹需 id 映射，在仓储层判定）。 */
    fun matchesBasic(book: BookRecord): Boolean =
        (type == null || book.type == type) &&
            (readStatus == null || book.readStatus == readStatus) &&
            (formats.isEmpty() || book.format in formats) &&
            series.matchesValue(book.series) &&
            writers.matchesValue(book.writer) &&
            tags.matchesAny(book.tagList())

    companion object {
        /** 虚拟书夹 id：「未分类」= 不在任何书夹的书。 */
        const val UNCLASSIFIED_ID = "0"

        /** 与 LibraryScanner 入库字面量对齐（detect 的 ZIP/RAR/SEVEN_Z 只是中间值，不入库）。 */
        val FORMAT_OPTIONS = listOf("FOLDER", "CBZ", "CBR", "CB7", "PDF", "TXT", "EPUB", "MOBI")

        /** 分区 id：书 / 笔记（批注+划线+书签）/ 章节标题 / 正文。 */
        const val SECTION_BOOK = "book"
        const val SECTION_NOTE = "note"
        const val SECTION_CHAPTER = "chapter"
        const val SECTION_BODY = "body"
        val ALL_SECTIONS = setOf(SECTION_BOOK, SECTION_NOTE, SECTION_CHAPTER, SECTION_BODY)
    }
}

/** ComicInfo 同款「, 」分隔标签串 → 标签列表。 */
fun BookRecord.tagList(): List<String> =
    tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

/** 筛选模板（SSMH FilterTemplate 同款：最近使用排序，长按重命名/删除）。 */
@Serializable
data class SearchFilterTemplate(
    val id: String,
    val name: String,
    val filter: SearchFilter,
    val createdAt: Long,
    val lastUsedAt: Long = 0L,
)

object SearchFilterCodec {
    const val LIMIT = 8

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
    }

    fun encodeList(list: List<SearchFilterTemplate>): String =
        json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SearchFilterTemplate.serializer()), list)

    fun decodeList(raw: String?): List<SearchFilterTemplate> =
        runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(SearchFilterTemplate.serializer()),
                raw ?: return emptyList(),
            )
        }.getOrDefault(emptyList())

    fun encodeFilter(filter: SearchFilter): String =
        json.encodeToString(SearchFilter.serializer(), filter)

    fun decodeFilter(raw: String?): SearchFilter? = runCatching {
        raw ?: return null
        json.decodeFromString(SearchFilter.serializer(), raw)
    }.getOrNull()

    fun encodeStrings(list: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), list)

    fun decodeStrings(raw: String?): List<String> = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), raw ?: return emptyList())
    }.getOrDefault(emptyList())
}

/** 章节标题命中（bookId 已含封面等展示字段；打开阅读器走 chapterIdx）。 */
data class SearchChapterRow(
    val bookId: String,
    val bookTitle: String,
    val coverPath: String?,
    val bookType: String,
    val chapterIdx: Int,
    val chapterTitle: String,
)

/** 筛选 facet 数据源（进页一次预载）。 */
data class SearchFacets(
    val categories: List<CategoryRecord>,
    val folders: List<FolderFacet>,
    val series: List<String>,
    val tags: List<TagFacet>,
    val writers: List<String>,
)

data class FolderFacet(val id: Long, val name: String)

data class TagFacet(val name: String, val count: Int)

/** 正文命中（口径与 NovelReaderScreen 书内搜索一致：章内字符偏移 + 上下文摘要）。 */
data class FullTextHit(
    val book: BookRecord,
    val chapterIdx: Int,
    val chapterTitle: String,
    val charPos: Int,
    val context: String,
    val matchLen: Int,
    val ctxOffset: Int,
)

/** 正文流式扫描事件：按书聚合 + 进度 + 终局（上限信息）。 */
sealed class FullTextEvent {
    data class BookBatch(
        val book: BookRecord,
        val hits: List<FullTextHit>,
    ) : FullTextEvent()

    data class Progress(
        val scanned: Int,
        val total: Int,
        val currentTitle: String,
    ) : FullTextEvent()

    data class Done(
        /** 达到单书上限的书 id（显示「已达上限」）。 */
        val bookLimitedIds: Set<String>,
        /** 全库 300 处软上限被截断。 */
        val globalLimited: Boolean,
    ) : FullTextEvent()
}

/** 正文命中按书聚合组（UI 展开行）。 */
data class FullTextBookGroup(
    val book: BookRecord,
    val hits: List<FullTextHit>,
)

/** 笔记命中：标注（划线/批注/书签同存 annotations 表）+ 其所在书。 */
data class SearchNoteRow(
    val book: BookRecord,
    val annotation: com.inkvol.app.data.model.AnnotationRecord,
)

/** 搜索页会话状态（repository 持有，跨 Tab/进阅读器存活；Screen 渲染）。 */
data class SearchState(
    val query: String = "",
    val searched: Boolean = false,
    val loading: Boolean = false,
    val books: List<BookRecord> = emptyList(),
    val notes: List<SearchNoteRow> = emptyList(),
    val chapterHits: List<SearchChapterRow> = emptyList(),
    val bodyGroups: List<FullTextBookGroup> = emptyList(),
    /** 正文扫描进度（scanned to total）；null=未在扫。 */
    val bodyScanned: Int = 0,
    val bodyTotal: Int = 0,
    val bodyCurrentTitle: String = "",
    val bodyDone: Boolean = true,
    val bodyBookLimitedIds: Set<String> = emptySet(),
    val bodyGlobalLimited: Boolean = false,
    /** 进页一次预载的筛选 facet（书夹/文件夹/系列/标签/作者 distinct）。 */
    val facets: SearchFacets? = null,
    val history: List<String> = emptyList(),
    val filter: SearchFilter = SearchFilter(),
    val templates: List<SearchFilterTemplate> = emptyList(),
    val activeTemplateId: String? = null,
) {
    /** 结果态（有提交过搜索或仍有结果）：BackHandler 退回初始态。 */
    val resultMode: Boolean get() = searched || query.isNotEmpty()
}
