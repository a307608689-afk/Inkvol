package com.inkvol.app.data.source

import com.inkvol.app.util.NaturalOrder
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * EPUB 图片页索引：把一本 EPUB 里「该按页展示的图片」按阅读顺序列出来。
 *
 * 取序优先级：
 * 1. spine 顺序 —— 逐章 XHTML 里 `<img>` / `<image>` 的引用顺序（固定版式漫画的唯一正确顺序）；
 * 2. manifest 图片项自然序 —— spine 抽不到图时兜底（如 CSS background 放图）；
 * 3. ZIP 全图片自然序 —— 当 CBZ 用。
 *
 * 同时输出判定用统计量（spine 文档数 / 抽样文字量 / 固定版式标记），
 * 供 [com.inkvol.app.data.import.NovelComicProbe] 使用。
 *
 * **页数必须与 [EpubImageSource] 同源**：探针判出的 pageCount 就是阅读器总页数，
 * 两处各算一套会让进度 / 页码对不上。
 */
object EpubImageIndex {

    data class Result(
        /** 有序图片条目名（zip entry name）。 */
        val images: List<String>,
        /** spine 里的内容文档数（不含 nav）。 */
        val spineDocs: Int,
        /** spine 文档中引用到的图片数（去重前，去重后即 images.size 的上界）。 */
        val spineImages: Int,
        /** 实际抽样统计文字量的文档数。 */
        val sampledDocs: Int,
        /** 抽样文档去标签字符数合计。 */
        val sampledTextLen: Int,
        /** OPF 是否声明固定版式（rendition:layout=pre-paginated）。 */
        val fixedLayout: Boolean,
        /** ZIP 内图片条目总数（未按 spine 过滤）。 */
        val imageEntries: Int,
        /** 章节划分：每个含图的 spine 文档一章（仅 spine 取序可用时非空）。 */
        val chapters: List<Chapter>,
    )

    /** 章 = 一个含图的 spine 文档；images 是该文档按引用顺序的图片条目名。 */
    data class Chapter(
        val title: String,
        val images: List<String>,
    )

    private val IMG_TAG = Regex("<img\\b[^>]*>|<image\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ANY_TAG = Regex("<[^>]+>")
    private val ENTITY = Regex("&[#a-zA-Z0-9]+;")
    private val NAV_ANCHOR = Regex(
        "<a\\b[^>]*href\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</a>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val NAV_POINT = Regex(
        "<navLabel>\\s*<text[^>]*>(.*?)</text>\\s*</navLabel>\\s*<content\\b[^>]*src\\s*=\\s*\"([^\"]+)\"",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val ITEM_TAG = Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ITEMREF_TAG = Regex("<itemref\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ROOTFILE = Regex("<rootfile[^>]*full-path=\"([^\"]+)\"")
    private val FIXED_LAYOUT_PROP = Regex(
        "<meta\\b[^>]*property\\s*=\\s*\"rendition:layout\"[^>]*>\\s*([^<]+)",
        RegexOption.IGNORE_CASE,
    )
    private val FIXED_LAYOUT_NAME = Regex(
        "<meta\\b[^>]*name\\s*=\\s*\"rendition:layout\"[^>]*content\\s*=\\s*\"([^\"]*)\"",
        RegexOption.IGNORE_CASE,
    )

    private data class ManifestItem(
        val href: String,
        val mediaType: String,
        val properties: String,
    )

    fun parse(file: File, sampleDocs: Int = 8): Result? = runCatching {
        ZipFile(file).use { zip ->
            val allImages = zip.entries().asSequence()
                .filter { !it.isDirectory && SourceRules.isImageName(it.name) }
                .sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
                .toList()

            val container = zip.entryText("META-INF/container.xml")
            val opfPath = container?.let { ROOTFILE.find(it)?.groupValues?.get(1) }
            val opfEntry = opfPath?.let { findEntry(zip, it) }
            val opfText = opfEntry?.let { zip.entryText(it.name) }.orEmpty()
            val opfDir = opfEntry?.name?.substringBeforeLast('/', "") ?: ""

            val manifest = parseManifest(opfText)
            val spine = ITEMREF_TAG.findAll(opfText)
                .mapNotNull { attrOf(it.value, "idref") }
                .toList()
            // 章节标题：nav.xhtml（EPUB3）/ toc.ncx（EPUB2）锚文本；取不到用文档文件名兜底
            val tocLabels = parseTocLabels(zip, manifest, opfDir)

            val fixedLayout =
                FIXED_LAYOUT_PROP.find(opfText)?.groupValues?.get(1)?.trim()
                    ?.equals("pre-paginated", ignoreCase = true) == true ||
                    FIXED_LAYOUT_NAME.find(opfText)?.groupValues?.get(1)?.trim()
                        ?.equals("pre-paginated", ignoreCase = true) == true

            val spineImages = LinkedHashSet<String>()
            val spineChapters = ArrayList<Chapter>()
            var spineDocs = 0
            var spineImageRefs = 0
            var sampledDocs = 0
            var sampledText = 0

            spine.forEach spine@{ idref ->
                val item = manifest[idref] ?: return@spine
                if (!item.mediaType.contains("html") && !item.mediaType.contains("xml")) return@spine
                if ("nav" in item.properties) return@spine
                val href = resolveHref(opfDir, item.href) ?: return@spine
                val entry = findEntry(zip, href) ?: return@spine
                spineDocs++
                val html = zip.entryText(entry.name) ?: return@spine
                val chapterDir = entry.name.substringBeforeLast('/', "")

                val docImages = ArrayList<String>()
                IMG_TAG.findAll(html).forEach img@{ m ->
                    val tag = m.value
                    val src = attrOf(tag, "src")
                        ?: attrOf(tag, "xlink:href")
                        ?: attrOf(tag, "href")
                        ?: return@img
                    val resolved = resolveHref(chapterDir, src) ?: return@img
                    val imgEntry = findEntry(zip, resolved) ?: return@img
                    if (!SourceRules.isImageName(imgEntry.name)) return@img
                    spineImageRefs++
                    spineImages += imgEntry.name
                    docImages += imgEntry.name
                }

                // 含图的 spine 文档 = 一章（spine 取序可用时才生效，见 spineUsable）
                if (docImages.isNotEmpty()) {
                    spineChapters += Chapter(
                        title = tocLabels[href] ?: decodeEntryTitle(entry.name),
                        images = docImages,
                    )
                }

                if (sampledDocs < sampleDocs) {
                    sampledText += textLength(html)
                    sampledDocs++
                }
            }

            // 正文里几乎没引用但 ZIP 里图很多（CSS background 等）时退回全图片自然序，
            // 否则会明显少页。
            val spineUsable = spineImages.isNotEmpty() &&
                !(allImages.size >= 10 && spineImages.size * 2 < allImages.size)

            val images = when {
                spineUsable -> spineImages.toList()
                else -> manifestImageNames(zip, manifest, opfDir).ifEmpty { allImages.map { it.name } }
            }

            Result(
                images = images,
                spineDocs = spineDocs,
                spineImages = spineImageRefs,
                sampledDocs = sampledDocs,
                sampledTextLen = sampledText,
                fixedLayout = fixedLayout,
                imageEntries = allImages.size,
                chapters = if (spineUsable) spineChapters else emptyList(),
            )
        }
    }.getOrNull()

    /**
     * 目录标题表：归一化文档路径 → 标题。
     * EPUB3 = manifest properties 含 nav 的 XHTML 里的 `<a href>`；
     * EPUB2 = media-type 为 dtbncx 的 toc.ncx 的 navLabel/text + content src。
     * 锚点 href 去掉 #fragment 后按目录文档所在目录解析。
     */
    private fun parseTocLabels(
        zip: java.util.zip.ZipFile,
        manifest: Map<String, ManifestItem>,
        opfDir: String,
    ): Map<String, String> {
        val labels = HashMap<String, String>()

        val navItem = manifest.values.firstOrNull { "nav" in it.properties }
        if (navItem != null && navItem.mediaType.contains("html")) {
            val navHref = resolveHref(opfDir, navItem.href) ?: return labels
            val navEntry = findEntry(zip, navHref)
            val navDir = navEntry?.name?.substringBeforeLast('/', "") ?: ""
            zip.entryText(navEntry?.name ?: return labels)?.let { html ->
                NAV_ANCHOR.findAll(html).forEach { m ->
                    val label = plainText(m.groupValues[2])
                    if (label.isEmpty()) return@forEach
                    resolveHref(navDir, m.groupValues[1])?.let { href -> labels[href] = label }
                }
            }
        }

        val ncxItem = manifest.values.firstOrNull {
            it.mediaType.contains("dtbncx", ignoreCase = true)
        }
        if (ncxItem != null) {
            val ncxHref = resolveHref(opfDir, ncxItem.href) ?: return labels
            val ncxEntry = findEntry(zip, ncxHref)
            val ncxDir = ncxEntry?.name?.substringBeforeLast('/', "") ?: ""
            zip.entryText(ncxEntry?.name ?: return labels)?.let { ncx ->
                NAV_POINT.findAll(ncx).forEach { m ->
                    val label = plainText(m.groupValues[1])
                    if (label.isEmpty()) return@forEach
                    resolveHref(ncxDir, m.groupValues[2])?.let { href -> labels[href] = label }
                }
            }
        }

        return labels
    }

    /** 锚文本 → 纯文本：去内嵌标签 + 解实体 + 压空白。 */
    private fun plainText(raw: String): String =
        raw.replace(ANY_TAG, " ")
            .replace(ENTITY, " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    /** 兜底章节标题：条目文件名去目录去扩展名（URL 解码失败用原名）。 */
    private fun decodeEntryTitle(entryName: String): String {
        val name = entryName.substringAfterLast('/').substringBeforeLast('.')
        return runCatching { URLDecoder.decode(name, "UTF-8") }.getOrDefault(name)
    }

    private fun manifestImageNames(
        zip: ZipFile,
        manifest: Map<String, ManifestItem>,
        opfDir: String,
    ): List<String> = manifest.values
        .filter { it.mediaType.startsWith("image/", ignoreCase = true) }
        .mapNotNull { item ->
            val href = resolveHref(opfDir, item.href) ?: return@mapNotNull null
            findEntry(zip, href)?.name?.takeIf { SourceRules.isImageName(it) }
        }
        .distinct()
        .sortedWith { a, b -> NaturalOrder.compare(a, b) }

    private fun parseManifest(opfText: String): Map<String, ManifestItem> {
        val result = HashMap<String, ManifestItem>()
        ITEM_TAG.findAll(opfText).forEach { m ->
            val tag = m.value
            val id = attrOf(tag, "id") ?: return@forEach
            val href = attrOf(tag, "href") ?: return@forEach
            result[id] = ManifestItem(
                href = href,
                mediaType = attrOf(tag, "media-type").orEmpty(),
                properties = attrOf(tag, "properties").orEmpty(),
            )
        }
        return result
    }

    /** 去标签 + 去实体后的非空白字符数（判定文字量的唯一口径）。 */
    private fun textLength(html: String): Int {
        val text = html.replace(ANY_TAG, " ").replace(ENTITY, " ")
        return text.count { !it.isWhitespace() }
    }

    private fun attrOf(tag: String, name: String): String? =
        Regex("\\b${Regex.escape(name)}\\s*=\\s*\"([^\"]*)\"|\\b${Regex.escape(name)}\\s*=\\s*'([^']*)'")
            .find(tag)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

    private fun resolveHref(baseDir: String, href: String): String? {
        val cleaned = href.substringBefore('#').trim()
        if (cleaned.isEmpty()) return null
        val joined = if (baseDir.isEmpty()) cleaned else "$baseDir/$cleaned"
        return normalizePath(joined)
    }

    private fun normalizePath(path: String): String {
        val decoded = runCatching { URLDecoder.decode(path, "UTF-8") }.getOrDefault(path)
        val parts = ArrayList<String>()
        decoded.split('/').forEach { part ->
            when (part) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun findEntry(zip: ZipFile, path: String): java.util.zip.ZipEntry? {
        val normalized = normalizePath(path)
        return zip.getEntry(normalized)
            ?: zip.entries().asSequence().firstOrNull {
                normalizePath(it.name).equals(normalized, ignoreCase = true)
            }
    }

    private fun ZipFile.entryText(name: String): String? {
        val entry = findEntry(this, name) ?: return null
        return runCatching {
            getInputStream(entry).use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }
}
