package com.inkvol.app.data.source

import android.util.Log
import com.github.junrar.Archive
import com.inkvol.app.util.NaturalOrder
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * 嵌套归档支持（P2）：CBZ/CBR/CB7 顶层再套子压缩包（zip/rar/7z/pdf）时，
 * 每个子包（或顶层文件夹）= 一章，目录可跳。
 *
 * 机制：子包条目解到 cacheDir/import/{书id}/，用现成的 Zip/Rar/SevenZ/Pdf 源打开
 * （零新解码器）；外层文件夹图与散图直接读外层归档。同名同尺寸的解包副本直接复用
 * （缓存被系统清掉后自动重建）。解包失败的子包整章剔除——页与目录都不出现，避免「点了没反应」。
 *
 * 边界：深度 1 层；单子包 500MB 上限；加密子包跳过。
 * 目标文件名由本类生成（序号.扩展名），子包条目名永不落盘路径，无 zip-slip 面。
 */
class NestedArchiveSource(
    private val file: File,
    kind: String,
    extractDir: File,
) : BookContentSource, SectionedContentSource {

    /** 章 = 顶层文件夹前缀 / 已解包子压缩包 / 顶层散图组。 */
    private sealed class Chapter {
        abstract val title: String
        abstract val sortKey: String
    }

    private class FolderChapter(
        override val title: String,
        override val sortKey: String,
        val names: List<String>,
    ) : Chapter()

    private class LooseChapter(val names: List<String>) : Chapter() {
        override val title = "散页"
        override val sortKey = ""
    }

    private class ArchiveChapter(
        override val title: String,
        override val sortKey: String,
        val source: BookContentSource,
    ) : Chapter()

    private val raw: RawArchive
    private val outerSizes: Map<String, Long>
    private val chapters: List<Chapter>

    /** 每章的全局页区间（与 [chapters] 对齐）。 */
    private val pageRanges: List<IntRange>
    private val pages: List<PageRef>
    private val sections: List<BookSection>

    init {
        raw = RawArchive(file, kind)
        outerSizes = raw.entries.associate { it.name to it.size }

        val extracted = NestedArchiveExpander.expand(raw, extractDir)

        val outerImages = raw.entries
            .filter { SourceRules.isImageName(it.name) }
            .map { it.name }
        val folderMap = LinkedHashMap<String, MutableList<String>>()
        val loose = ArrayList<String>()
        outerImages.forEach { name ->
            if ('/' in name) {
                folderMap.getOrPut(name.substringBefore('/')) { ArrayList() } += name
            } else {
                loose += name
            }
        }
        folderMap.values.forEach { it.sortWith { a, b -> NaturalOrder.compare(a, b) } }
        loose.sortWith { a, b -> NaturalOrder.compare(a, b) }

        val built = ArrayList<Chapter>()
        folderMap.forEach { (prefix, names) ->
            built += FolderChapter(prefix, prefix, names)
        }
        extracted.forEach { (entryName, target) ->
            val title = entryName.substringAfterLast('/').substringBeforeLast('.')
            val inner = openInner(target)
            if (inner == null) {
                Log.w(TAG, "nested chapter skipped (cannot open): $entryName")
            } else {
                built += ArchiveChapter(title, entryName, inner)
            }
        }
        if (loose.isNotEmpty()) built += LooseChapter(loose)
        built.sortWith { a, b -> NaturalOrder.compare(a.sortKey, b.sortKey) }
        chapters = built

        // 扁平页表：章按序展开，区间记录章起始页
        val flat = ArrayList<PageRef>()
        val ranges = ArrayList<IntRange>()
        chapters.forEach { chapter ->
            val start = flat.size
            chapterPages(chapter).forEach { flat += it }
            ranges += start..(flat.size - 1).coerceAtLeast(start)
        }
        pageRanges = ranges
        pages = flat
        sections = chapters.mapIndexed { index, chapter ->
            BookSection(index, chapter.title, ranges[index].first)
        }
    }

    override val pageCount: Int get() = pages.size

    override fun listPages(): List<PageRef> = pages

    override fun openPage(index: Int): InputStream {
        val rangeIndex = pageRanges.indexOfFirst { index in it }
        val range = pageRanges.getOrNull(rangeIndex) ?: error("page $index out of range")
        val local = index - range.first
        return when (val chapter = chapters[rangeIndex]) {
            is FolderChapter -> raw.open(chapter.names[local])
            is LooseChapter -> raw.open(chapter.names[local])
            is ArchiveChapter -> chapter.source.openPage(local)
        }
    }

    /** 少于 2 章视为单章节书（如仅一个子包），返回空表。 */
    override fun listSections(): List<BookSection> =
        if (sections.size >= 2) sections else emptyList()

    override fun close() {
        runCatching { raw.close() }
        chapters.forEach { chapter ->
            if (chapter is ArchiveChapter) runCatching { chapter.source.close() }
        }
    }

    private fun chapterPages(chapter: Chapter): List<PageRef> = when (chapter) {
        is FolderChapter -> chapter.names.map { name ->
            PageRef(0, name, outerSizes[name] ?: 0L)
        }
        is LooseChapter -> chapter.names.map { name ->
            PageRef(0, name, outerSizes[name] ?: 0L)
        }
        is ArchiveChapter -> runCatching { chapter.source.listPages() }.getOrDefault(emptyList())
    }

    private fun openInner(target: File): BookContentSource? = runCatching {
        when (SourceRules.extensionOf(target.name)) {
            "zip", "cbz" -> ZipSource(target)
            "rar", "cbr" -> RarSource(target)
            "7z", "cb7" -> SevenZSource(target)
            "pdf" -> PdfSource(target)
            else -> null
        }
    }.getOrNull()

    private companion object {
        const val TAG = "InkvolNested"
    }
}

/**
 * 外层归档的原始条目访问（图片与子压缩包都要读得到，按 kind 三选一实现）。
 * 不做并发包装——与 ZipSource/RarSource/SevenZSource 现有的并发语义保持一致。
 */
internal class RawArchive(private val file: File, kind: String) : Closeable {

    data class Entry(val name: String, val size: Long)

    private val zip: ZipFile?
    private val rar: Archive?
    private val sevenZ: SevenZFile?

    val entries: List<Entry>

    init {
        when (kind) {
            "ZIP" -> {
                zip = ZipFile(file)
                rar = null
                sevenZ = null
                entries = zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .map { Entry(it.name, it.size.coerceAtLeast(0)) }
                    .toList()
            }
            "RAR" -> {
                rar = Archive(file)
                zip = null
                sevenZ = null
                entries = rar.fileHeaders
                    .filter { !it.isDirectory }
                    .map { Entry(it.fileName, it.fullUnpackSize) }
            }
            "SEVEN_Z" -> {
                sevenZ = SevenZFile.builder().setFile(file).get()
                zip = null
                rar = null
                entries = sevenZ.entries
                    .filter { !it.isDirectory }
                    .map { Entry(it.name, it.size) }
            }
            else -> error("unsupported outer kind: $kind")
        }
    }

    fun open(name: String): InputStream = when {
        zip != null -> zip.getInputStream(zip.getEntry(name) ?: error("missing entry $name"))
        rar != null -> {
            val header = rar.fileHeaders.firstOrNull { it.fileName == name }
                ?: error("missing entry $name")
            rar.getInputStream(header)
        }
        else -> {
            val sevenZ = requireNotNull(sevenZ)
            val entry = sevenZ.entries.firstOrNull { it.name == name }
                ?: error("missing entry $name")
            sevenZ.getInputStream(entry)
        }
    }

    override fun close() {
        runCatching { zip?.close() }
        runCatching { rar?.close() }
        runCatching { sevenZ?.close() }
    }
}

/** 顶层子压缩包解包：条目名 → 本地文件；加密/损坏/超限的子包被跳过。 */
internal object NestedArchiveExpander {

    const val MAX_INNER_BYTES = 500L * 1024 * 1024
    private const val TAG = "InkvolNested"

    fun expand(raw: RawArchive, targetDir: File): Map<String, File> {
        val nested = raw.entries.filter {
            '/' !in it.name && SourceRules.isArchiveName(it.name)
        }
        if (nested.isEmpty()) return emptyMap()
        targetDir.mkdirs()
        val result = HashMap<String, File>()
        nested.forEachIndexed { index, entry ->
            val ext = SourceRules.extensionOf(entry.name).ifBlank { "bin" }
            val target = File(targetDir, "%03d.%s".format(index, ext))
            // 同名同尺寸副本直接复用（缓存被清后 size 不匹配则重新解包）
            if (entry.size > 0 && target.exists() && target.length() == entry.size) {
                result[entry.name] = target
                return@forEachIndexed
            }
            try {
                raw.open(entry.name).use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            if (total + read > MAX_INNER_BYTES) {
                                error("nested archive too large: ${entry.name}")
                            }
                            output.write(buffer, 0, read)
                            total += read
                        }
                    }
                }
                if (target.length() > 0) result[entry.name] = target
            } catch (error: Throwable) {
                runCatching { target.delete() }
                Log.w(TAG, "nested expand skipped: ${entry.name}", error)
            }
        }
        return result
    }
}

fun hasNestedArchives(file: File, kind: String): Boolean = runCatching {
    RawArchive(file, kind).use { raw ->
        raw.entries.any { '/' !in it.name && SourceRules.isArchiveName(it.name) }
    }
}.getOrDefault(false)

/**
 * CBZ/CBR/CB7 统一入口：顶层有子压缩包走嵌套源（每子包/每文件夹一章），
 * 否则维持原有单源行为，零回归。
 */
fun openComicSource(
    file: File,
    kind: String,
    fingerprint: String,
    cacheRoot: File,
): BookContentSource =
    if (hasNestedArchives(file, kind)) {
        NestedArchiveSource(file, kind, File(cacheRoot, fingerprint))
    } else {
        when (kind) {
            "ZIP" -> ZipSource(file)
            "RAR" -> RarSource(file)
            "SEVEN_Z" -> SevenZSource(file)
            else -> error("unsupported kind $kind")
        }
    }
