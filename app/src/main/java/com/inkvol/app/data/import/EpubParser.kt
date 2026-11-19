package com.inkvol.app.data.import

import android.content.Context
import android.graphics.BitmapFactory
import com.inkvol.app.data.model.NovelChapterRecord
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.zip.ZipFile
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll

/**
 * M6.2 EPUB 导入解析器（受限块模型 · 第二轮）。
 *
 * 相比 M6.2-A 新增：
 * - TOC/nav 精确标题映射（EPUB3 nav.xhtml 与 EPUB2 toc.ncx）；
 * - 真实图片提取：`<img>` / svg `<image>` 解析到 filesDir/epub_images/{bookId}/，
 *   正文以 `[IMG:相对路径]` 标记行占位，NovelTextPager / NovelReaderScreen 渲染真实图片；
 * - 脚注清理：`<aside>`（EPUB 脚注容器）、`<rt>/<rp>`（注音标注）整块移除。
 *
 * 仍复用 TXT 链路：归一化 UTF-8 + novel_chapters 字节区间 + NovelReaderScreen。
 * 每章延迟解析（大书导入提速）留到 M6.2-C。
 */
object EpubParser {

    data class Parsed(
        val textFile: File,
        val chapters: List<NovelChapterRecord>,
        val coverPath: String?,
    )

    /** 正文里的图片标记行；NovelTextPager 把它解析为图片块。 */
    fun imageMarker(relativePath: String): String = "[IMG:$relativePath]"

    private val IMG_MARKER = Regex("^\\[IMG:([^\\]]+)]$")

    private val BLOCK_CLOSE = Regex(
        "</(?:p|div|h[1-6]|li|blockquote|tr|section|article|header|footer|figcaption)>",
        RegexOption.IGNORE_CASE,
    )
    private val BLOCK_OPEN = Regex(
        "<(?:p|div|h[1-6]|li|blockquote|tr|section|article|header|footer|figcaption)\\b[^>]*>",
        RegexOption.IGNORE_CASE,
    )
    private val IMG_TAG = Regex("<img\\b[^>]*>|<image\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val BR_TAG = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val ANY_TAG = Regex("<[^>]+>")
    private val H_TAG = Regex("<h([1-6])\\b[^>]*>([\\s\\S]*?)</h\\1>", RegexOption.IGNORE_CASE)
    private val HEAD_TAG = Regex("<head\\b[\\s\\S]*?</head>", RegexOption.IGNORE_CASE)
    private val DROP_TAG = Regex(
        "<(script|style|svg|aside)\\b[\\s\\S]*?</\\1>|<!--[\\s\\S]*?-->",
        RegexOption.IGNORE_CASE,
    )
    private val RT_RP_TAG = Regex(
        "<(rt|rp)\\b[^>]*>[\\s\\S]*?</\\1>",
        RegexOption.IGNORE_CASE,
    )
    private val TITLE_TAG = Regex("<title\\b[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)

    /** M6.2-C：svg 块（可能包着 <image>）→ 只保留其中的 [IMG:] 标记行，其余（path/rect 等）丢弃。 */
    private val SVG_TAG = Regex("<svg\\b[\\s\\S]*?</svg>", RegexOption.IGNORE_CASE)
    private val SVG_MARKER_SCAN = Regex("\\[IMG:[^\\]]+]")

    /** M6.2-C：章节 XHTML → 文本的并行度。ZipFile 每线程独立打开，无共享句柄。 */
    private const val PARSE_PARALLELISM = 4

    fun parse(
        context: Context,
        bookId: String,
        zipFile: File,
        purifyRules: List<Regex> = emptyList(),
    ): Parsed {
        val textDir = File(context.filesDir, "novel_text").apply { mkdirs() }
        val textFile = File(textDir, "$bookId.txt")
        val coverPath = extractCover(context, bookId, zipFile)
        val imageDir = File(context.filesDir, "epub_images/$bookId")

        // 顺序收集 spine 任务，保证章序；转换并行，拼接顺序不变。
        data class SpineTask(val href: String, val entryName: String, val chapterDir: String, val html: String)
        val tasks = ArrayList<SpineTask>()
        var tocTitles: Map<String, String> = emptyMap()
        runCatching {
            ZipFile(zipFile).use { zip ->
                val container = zip.entryText("META-INF/container.xml")
                val opfPath = container
                    ?.let { Regex("<rootfile[^>]*full-path=\"([^\"]+)\"").find(it)?.groupValues[1] }
                    ?: throw IllegalArgumentException("EPUB 缺少 OPF（container.xml）")
                val opfEntry = findEntry(zip, opfPath)
                    ?: throw IllegalArgumentException("EPUB 缺少 OPF：$opfPath")
                val opfText = zip.entryText(opfEntry.name) ?: ""
                val opfDir = opfEntry.name.substringBeforeLast('/', "")

                val manifest = parseManifest(opfText)
                tocTitles = buildTocTitles(zip, manifest, opfDir)
                val spineRefs = Regex("<itemref[^>]*idref=\"([^\"]+)\"[^>]*>").findAll(opfText)
                    .map { it.groupValues[1] }
                    .toList()
                if (spineRefs.isEmpty()) throw IllegalArgumentException("EPUB spine 为空")

                for (idref in spineRefs) {
                    val item = manifest[idref] ?: continue
                    if (!item.mediaType.contains("html") && !item.mediaType.contains("xml")) continue
                    // nav 目录文档自身不作为章节
                    if ("nav" in item.properties) continue
                    val href = resolveHref(opfDir, item.href) ?: continue
                    val entry = findEntry(zip, href) ?: continue
                    val html = zip.entryText(entry.name) ?: continue
                    tasks += SpineTask(
                        href = href,
                        entryName = entry.name,
                        chapterDir = entry.name.substringBeforeLast('/', ""),
                        html = html,
                    )
                }
            }
        }.getOrElse { throw it }

        if (tasks.isEmpty()) throw IllegalArgumentException("EPUB 未解析出任何章节")

        // M6.2-C：并行转换（分批限并发），转换结果按 spine 顺序落盘。
        val bodies = arrayOfNulls<String>(tasks.size)
        val h1Titles = arrayOfNulls<String>(tasks.size)
        kotlinx.coroutines.runBlocking {
            val scope = this
            tasks.indices.step(PARSE_PARALLELISM).forEach { start ->
                val groupEnd = minOf(start + PARSE_PARALLELISM, tasks.size)
                (start until groupEnd).map { i ->
                    val task = tasks[i]
                    scope.async(kotlinx.coroutines.Dispatchers.IO) {
                        val converted = ZipFile(zipFile).use { z ->
                            val htmlWithMarkers = replaceImages(z, task.html, task.chapterDir, imageDir, bookId)
                            // svg 块（整页 svg 封面/插图）只保留其中的图片标记
                            collapseSvg(htmlWithMarkers)
                        }
                        // 标题已单独写入章节行，正文里去掉首个 h1-h6 避免重复。
                        val htmlNoHeading = H_TAG.find(converted)?.let {
                            converted.removeRange(it.range)
                        } ?: converted
                        bodies[i] = htmlToText(htmlNoHeading)
                        h1Titles[i] = chapterTitle(task.html)
                    }
                }.joinAll()
            }
        }

        val chapters = ArrayList<NovelChapterRecord>()
        var byteOffset = 0L
        FileOutputStream(textFile).use { output ->
            fun append(text: String) {
                val encoded = text.toByteArray(Charsets.UTF_8)
                output.write(encoded)
                byteOffset += encoded.size
            }

            // M6.2-C：按 spine 顺序拼接并行转换结果；章区间 = 标题行起点 → 本章末尾。
            tasks.forEachIndexed { index, task ->
                val body = bodies[index] ?: return@forEachIndexed
                val title = tocTitles[task.href]
                    ?: tocTitles[task.entryName]
                    ?: h1Titles[index]
                    ?: "第 ${index + 1} 章"

                val chapterStart = byteOffset
                append("$title\n\n")
                // M6.3-C 净化：命中启用规则的行不入副本（图片标记行除外）
                val purifiedBody = if (purifyRules.isEmpty()) body else body
                    .split('\n')
                    .filter { line ->
                        IMG_MARKER.matches(line.trim()) ||
                            purifyRules.none { it.containsMatchIn(line) }
                    }
                    .joinToString("\n")
                append(purifiedBody)
                if (!purifiedBody.endsWith("\n")) append("\n")
                chapters += NovelChapterRecord(
                    index = index,
                    title = title,
                    byteStart = chapterStart,
                    byteEnd = byteOffset,
                    charCount = (byteOffset - chapterStart).toInt(),
                )
            }
            if (chapters.isEmpty()) throw IllegalArgumentException("EPUB 未解析出任何章节")
        }

        return Parsed(
            textFile = textFile,
            chapters = chapters,
            coverPath = coverPath,
        )
    }

    // ---------------- OPF / manifest ----------------

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String,
    )

    private fun parseManifest(opfText: String): Map<String, ManifestItem> {
        val result = HashMap<String, ManifestItem>()
        Regex("<item\\b[^>]*>").findAll(opfText).forEach { m ->
            val tag = m.value
            fun attr(name: String): String? =
                Regex("\\b$name\\s*=\\s*\"([^\"]*)\"")
                    .find(tag)?.groupValues?.get(1)
            val id = attr("id") ?: return@forEach
            val href = attr("href") ?: return@forEach
            result[id] = ManifestItem(
                id = id,
                href = href,
                mediaType = attr("media-type") ?: "",
                properties = attr("properties") ?: "",
            )
        }
        return result
    }

    /**
     * M6.2-B：TOC 精确标题。
     * EPUB3：properties 含 "nav" 的 XHTML 里的 <a href>；EPUB2：toc.ncx 的 navPoint。
     */
    private fun buildTocTitles(
        zip: ZipFile,
        manifest: Map<String, ManifestItem>,
        opfDir: String,
    ): Map<String, String> {
        val titles = HashMap<String, String>()
        runCatching {
            val navItem = manifest.values.firstOrNull {
                "nav" in it.properties && (it.mediaType.contains("html") || it.mediaType.contains("xml"))
            }
            if (navItem != null) {
                val navHref = resolveHref(opfDir, navItem.href) ?: return@runCatching
                val navEntry = findEntry(zip, navHref) ?: return@runCatching
                val navDir = navEntry.name.substringBeforeLast('/', "")
                val navText = zip.entryText(navEntry.name) ?: return@runCatching
                Regex("<a\\b[^>]*href\\s*=\\s*\"([^\"]+)\"[^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
                    .findAll(navText)
                    .forEach { m ->
                        val target = resolveHref(navDir, m.groupValues[1]) ?: return@forEach
                        val title = unescape(m.groupValues[2])
                        if (title.isNotEmpty()) titles[target] = title
                    }
                return@runCatching
            }

            val ncxItem = manifest.values.firstOrNull {
                it.mediaType == "application/x-dtbncx+xml"
            } ?: return@runCatching
            val ncxHref = resolveHref(opfDir, ncxItem.href) ?: return@runCatching
            val ncxEntry = findEntry(zip, ncxHref) ?: return@runCatching
            val ncxText = zip.entryText(ncxEntry.name) ?: return@runCatching

            // navPoint 可能嵌套；按起始位置切段，每段取第一个 text 与 content。
            val starts = Regex("<navPoint\\b[^>]*>", RegexOption.IGNORE_CASE)
                .findAll(ncxText)
                .map { it.range.first }
                .toList()
            starts.forEachIndexed { i, start ->
                val end = starts.getOrElse(i + 1) { ncxText.length }
                val chunk = ncxText.substring(start, end)
                val label = Regex("<text>([\\s\\S]*?)</text>", RegexOption.IGNORE_CASE)
                    .find(chunk)?.groupValues?.get(1)
                val src = Regex("<content[^>]*src\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                    .find(chunk)?.groupValues?.get(1)
                val title = label?.let { unescape(it) } ?: return@forEachIndexed
                val target = src?.let { resolveHref(opfDir, it) } ?: return@forEachIndexed
                if (title.isNotEmpty()) titles[target] = title
            }
        }
        return titles
    }

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
        return getInputStream(entry).use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }

    // ---------------- 图片提取 ----------------

    /**
     * 把章节内 `<img src>` / svg `<image xlink:href>` 解压到
     * filesDir/epub_images/{bookId}/，正文替换为 [IMG:相对路径] 标记行。
     * 解析失败的图片降级为 [插图] 文本。
     */
    private fun replaceImages(
        zip: ZipFile,
        html: String,
        chapterDir: String,
        imageDir: File,
        bookId: String,
    ): String {
        val out = StringBuilder(html.length + 256)
        var last = 0
        IMG_TAG.findAll(html).forEach { m ->
            val tag = m.value
            out.append(html, last, m.range.first)
            last = m.range.last + 1

            fun attr(name: String): String? =
                Regex("\\b${Regex.escape(name)}\\s*=\\s*\"([^\"]*)\"|\\b${Regex.escape(name)}\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE)
                    .find(tag)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

            val src = attr("src") ?: attr("xlink:href") ?: attr("href")
            val resolved = src?.let { resolveHref(chapterDir, it) }
            val entry = resolved?.let { findEntry(zip, it) }
            if (resolved != null && entry != null) {
                val bytes = runCatching {
                    zip.getInputStream(entry).use { it.readBytes() }
                }.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    val safeName = resolved.replace('/', '_')
                    imageDir.mkdirs()
                    val file = File(imageDir, safeName)
                    if (!file.exists() || file.length() != bytes.size.toLong()) {
                        FileOutputStream(file).use { it.write(bytes) }
                    }
                    out.append('\n').append(imageMarker("epub_images/$bookId/$safeName")).append('\n')
                    return@forEach
                }
            }
            out.append("\n[插图]\n")
        }
        out.append(html, last, html.length)
        return out.toString()
    }

    // ---------------- XHTML → 纯文本 ----------------

    /** M6.2-C：svg 块只保留其中的 [IMG:] 标记行，丢弃 path/rect 等矢量描述。 */
    private fun collapseSvg(html: String): String =
        SVG_TAG.replace(html) { m ->
            val markers = SVG_MARKER_SCAN.findAll(m.value).map { it.value }.toList()
            if (markers.isEmpty()) {
                " "
            } else {
                "\n" + markers.joinToString("\n") + "\n"
            }
        }

    fun htmlToText(html: String): String {
        var text = html
            .replace(HEAD_TAG, " ")
            .replace(DROP_TAG, " ")
            .replace(RT_RP_TAG, "")
            .replace(BR_TAG, "\n")
            .replace(BLOCK_CLOSE, "\n\n")
            .replace(BLOCK_OPEN, "\n")
            .replace(ANY_TAG, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
                m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: ""
            }
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
            }

        // 行内空白压缩 + 段落间最多一个空行
        val lines = text.split('\n')
            .map { it.replace(Regex("[ \\t\\x0B\\f\\r]+"), " ").trim() }
        val out = ArrayList<String>(lines.size)
        var blank = true
        for (line in lines) {
            if (line.isEmpty()) {
                if (!blank) out.add("")
                blank = true
            } else {
                out.add(line)
                blank = false
            }
        }
        return out.joinToString("\n").trim()
    }

    private fun chapterTitle(html: String): String? {
        val heading = H_TAG.find(html)?.groupValues?.get(2)
            ?.let { unescape(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (heading != null) return heading
        val docTitle = TITLE_TAG.find(html)?.groupValues?.get(1)
            ?.let { unescape(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return docTitle
    }

    private fun unescape(raw: String): String = raw
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: ""
        }
        .replace(Regex("&#(\\d+);")) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
        }
        .replace(Regex("\\s+"), " ")
        .trim()

    // ---------------- 封面 ----------------

    private fun extractCover(context: Context, bookId: String, zipFile: File): String? {
        return runCatching {
            ZipFile(zipFile).use { zip ->
                val container = zip.entryText("META-INF/container.xml")
                val opfPath = container
                    ?.let { Regex("<rootfile[^>]*full-path=\"([^\"]+)\"").find(it)?.groupValues[1] }
                    ?: return@use null
                val opfEntry = findEntry(zip, opfPath) ?: return@use null
                val opfText = zip.entryText(opfEntry.name) ?: return@use null
                val opfDir = opfEntry.name.substringBeforeLast('/', "")
                val manifest = parseManifest(opfText)

                val coverItem = manifest.values.firstOrNull { "cover-image" in it.properties }
                    ?: opfText.takeIf { Regex("meta[^>]*name\\s*=\\s*\"cover\"").containsMatchIn(it) }
                        ?.let {
                            Regex("meta[^>]*name\\s*=\\s*\"cover\"[^>]*content\\s*=\\s*\"([^\"]+)\"")
                                .find(it)?.groupValues?.get(1)
                                ?.let { coverId -> manifest[coverId] }
                        }
                    ?: manifest.values.firstOrNull {
                        it.mediaType.startsWith("image/") &&
                            it.href.contains("cover", ignoreCase = true)
                    }

                val entry = coverItem?.let { resolveHref(opfDir, it.href) }?.let { findEntry(zip, it) }
                    ?: return@use null
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null

                val dir = File(context.filesDir, "novel_covers").apply { mkdirs() }
                val file = File(dir, "$bookId.png")
                FileOutputStream(file).use { output ->
                    bitmap.compress(
                        android.graphics.Bitmap.CompressFormat.PNG,
                        92,
                        output,
                    )
                }
                file.absolutePath
            }
        }.getOrNull()
    }
}
