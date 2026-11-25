package com.inkvol.app.data.source

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 漫画型 EPUB 的内容源：EPUB 本质是 ZIP，页序列由 [EpubImageIndex] 解析
 * （spine 引用顺序 → manifest → 全图片自然序三级兜底）。
 *
 * 与 [ZipSource] 的差异：不把封面 / 装饰图 / 图标当页，按 OPF 的真实阅读顺序出页。
 * 小说 EPUB 永远不会走到这里（`type=NOVEL` 走小说阅读器）。
 */
class EpubImageSource(private val file: File) : BookContentSource, SectionedContentSource {

    private val zip = ZipFile(file)
    private val entries: List<ZipEntry>
    private val pages: List<PageRef>
    private val sections: List<BookSection>

    init {
        val index = EpubImageIndex.parse(file)
        val names = index?.images.orEmpty()
        entries = names.mapNotNull { name -> runCatching { zip.getEntry(name) }.getOrNull() }
        pages = entries.mapIndexed { index, entry -> PageRef(index, entry.name, entry.size) }
        // 章节（解析期已产出）：每章首图映射到扁平页列表的页号。
        // 页级打包抑制：条目极多且每条只有一两张图 = 「每页一个 XHTML」的打包形态
        // （Kmoe 等页级转换器产物），其 spine/ncx 目录是页级假目录，按单章节书处理。
        val rawChapters = index?.chapters.orEmpty()
        val chapters =
            if (rawChapters.size >= 30 && rawChapters.all { it.images.size <= 2 }) {
                emptyList()
            } else {
                rawChapters
            }
        val pageIndexOf = pages.associate { it.name to it.index }
        sections = chapters
            .mapNotNull { chapter ->
                chapter.images.firstNotNullOfOrNull { pageIndexOf[it] }?.let { start ->
                    BookSection(0, chapter.title, start)
                }
            }
            .filter { it.pageStart in pages.indices }
            .sortedBy { it.pageStart }
            .distinctBy { it.pageStart }
            .mapIndexed { i, section -> section.copy(idx = i) }
    }

    /** 少于 2 章视为单章节书，返回空表（UI 完全退化）。 */
    override fun listSections(): List<BookSection> =
        if (sections.size >= 2) sections else emptyList()

    override val pageCount: Int get() = pages.size

    override fun listPages(): List<PageRef> = pages

    override fun openPage(index: Int): InputStream {
        val entry = entries.getOrNull(index) ?: error("page $index out of range")
        return zip.getInputStream(entry)
    }

    override fun close() {
        runCatching { zip.close() }
    }
}
