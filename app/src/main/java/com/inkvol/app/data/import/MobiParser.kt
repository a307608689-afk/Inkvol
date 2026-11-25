package com.inkvol.app.data.import

import android.content.Context
import com.inkvol.app.data.model.NovelChapterRecord
import com.inkvol.app.data.source.MobiCodec
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * M6.4 MOBI 导入解析器（旧格式 PalmDOC，无 DRM）。
 *
 * 覆盖：PDB 容器 + MOBI 头 + compression 1（无压缩）/ 2（PalmDOC LZ77）；
 * 章节按 `<mbp:section>` 拆分，无标记时整本一章兜底（与 TXT 同策略）。
 * 正文 HTML 复用 EpubParser.htmlToText（`<p>/<br>/<h1>` → 换行、实体解码、行内空白压缩）。
 *
 * 不支持：HUFF/CDIC 压缩（compression=17480，AZW3/KF8 常用）、DRM（encryption≠0）。
 * 图片：旧 MOBI 图片在独立资源记录，正文 `<img recindex="N">` 引用（N → 绝对记录号
 * first_image_index + N - 1）。提取后落盘 filesDir/epub_images/{bookId}/ 并替换为
 * [IMG:] marker 行——与 EPUB 同目录同格式，分页/渲染/预览/保存链路零改动复用；
 * 提取失败的图片降级为 [插图] 文本。
 */
object MobiParser {

    /** 正文图片引用标签（大小写不敏感；recindex/mediumrecindex/lowrecindex 在标签内提取）。 */
    private val MOBI_IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)

    data class Parsed(
        val textFile: File,
        val chapters: List<NovelChapterRecord>,
        val coverPath: String?,
    )

    fun parse(
        context: Context,
        bookId: String,
        local: File,
        purifyRules: List<Regex> = emptyList(),
    ): Parsed {
        val bytes = local.readBytes()
        if (bytes.size < 78 + 8) throw IllegalArgumentException("MOBI 文件过小或损坏")

        // ---------------- PDB 容器 ----------------
        val numRecords = MobiCodec.u16(bytes, 76)
        if (numRecords < 2) throw IllegalArgumentException("MOBI 无记录")
        val offsets = IntArray(numRecords + 1)
        for (i in 0 until numRecords) {
            val base = 78 + i * 8
            offsets[i] = MobiCodec.u32(bytes, base)
        }
        offsets[numRecords] = bytes.size

        // ---------------- record 0：PalmDOC + MOBI 头 ----------------
        val r0 = bytes.copyOfRange(offsets[0], offsets[1])
        if (r0.size < 32) throw IllegalArgumentException("MOBI 头损坏")
        val compression = MobiCodec.u16(r0, 0)
        val textLength = MobiCodec.u32(r0, 4)
        val textRecordCount = MobiCodec.u16(r0, 8)
        val encryption = MobiCodec.u16(r0, 14)
        if (encryption != 0) throw IllegalArgumentException("DRM 加密的 MOBI 不受支持")
        if (compression == 17480) throw IllegalArgumentException("暂不支持 HUFF/CDIC 压缩的 MOBI（AZW3/KF8）")
        if (compression != 1 && compression != 2) throw IllegalArgumentException("未知压缩方式 $compression")
        if (r0.size < 24 || String(r0, 16, 4, Charsets.US_ASCII) != "MOBI") {
            throw IllegalArgumentException("不是 MOBI 格式（缺少 MOBI 头）")
        }
        val textEncoding = MobiCodec.u32(r0, 28)
        val charset = if (textEncoding == 65001) Charsets.UTF_8 else charset("windows-1252")

        // first_image_index（MOBI 头 offset 0x6C = 108）：图片资源区第一条记录（绝对记录号）。
        // 0 / 越界 = 无图或头过短，正文 recindex 走启发式 fallback。
        val firstImageIndex = if (r0.size >= 112) MobiCodec.u32(r0, 108) else 0

        // ---------------- 文本记录解压 + 拼接 ----------------
        val htmlBytes = ByteArrayOutputStream(textLength.coerceAtMost(16_000_000))
        for (i in 1..textRecordCount) {
            val s = offsets.getOrElse(i) { throw IllegalArgumentException("MOBI 记录越界") }
            val e = offsets.getOrElse(i + 1) { bytes.size }
            val rec = bytes.copyOfRange(s, e)
            if (compression == 2) {
                MobiCodec.palmDocDecompress(rec, charset, htmlBytes)
            } else {
                htmlBytes.write(rec)
            }
        }
        val html = String(htmlBytes.toByteArray(), Charsets.UTF_8)

        // ---------------- 图片记录提取（recindex → 落盘 + marker 行） ----------------
        val imageDir = File(File(context.filesDir, "epub_images"), bookId)
        val htmlWithMarkers = replaceRecindexImages(
            html = html,
            offsets = offsets,
            raw = bytes,
            textRecordCount = textRecordCount,
            firstImageIndex = firstImageIndex,
            imageDir = imageDir,
            bookId = bookId,
        )

        // ---------------- 章节拆分 ----------------
        val sectionHtml = if (htmlWithMarkers.contains("<mbp:section>")) {
            htmlWithMarkers.split("<mbp:section>").filter { it.isNotBlank() }
        } else {
            listOf(htmlWithMarkers)
        }

        val textDir = File(context.filesDir, "novel_text").apply { mkdirs() }
        val textFile = File(textDir, "$bookId.txt")
        val chapters = ArrayList<NovelChapterRecord>()
        var byteOffset = 0L
        FileOutputStream(textFile).use { output ->
            fun append(text: String) {
                val encoded = text.toByteArray(Charsets.UTF_8)
                output.write(encoded)
                byteOffset += encoded.size
            }

            sectionHtml.forEachIndexed { index, section ->
                // 标题已写入章节行，正文里去掉首个 h1-h6 避免重复（与 EpubParser 同款）
                val headingStripped = EpubParser.H_TAG.find(section)?.let {
                    section.removeRange(it.range)
                } ?: section
                val body = EpubParser.htmlToText(headingStripped)
                if (body.isBlank() && sectionHtml.size > 1) return@forEachIndexed
                val title = EpubParser.chapterTitle(section)
                    ?: "第 ${index + 1} 章"
                val chapterStart = byteOffset
                append("$title\n\n")
                val purifiedBody = if (purifyRules.isEmpty()) body else body
                    .split('\n')
                    .filter { line -> purifyRules.none { it.containsMatchIn(line) } }
                    .joinToString("\n")
                append(purifiedBody)
                if (!purifiedBody.endsWith("\n")) append("\n")
                chapters += NovelChapterRecord(
                    index = chapters.size,
                    title = title,
                    byteStart = chapterStart,
                    byteEnd = byteOffset,
                    charCount = body.length + title.length + 2,
                )
            }
        }
        if (chapters.isEmpty()) throw IllegalArgumentException("MOBI 未解析出任何内容")

        return Parsed(textFile = textFile, chapters = chapters, coverPath = null)
    }

    /**
     * 正文 `<img recindex="N">`（含 mediumrecindex/lowrecindex 变体）→ 落盘图片 + [IMG:] marker 行。
     *
     * recindex 的规范语义：绝对记录号 = first_image_index + N - 1（不能按「第几条图片记录」
     * 数——图片区夹非图片记录时会错位）。first_image_index 无效时退化启发式：从文本记录
     * 之后按「第 k 条图片魔数记录 = recindex k」收集（多数转换器把图片连续存放）。
     * 按被引用的 recindex 懒提取（不做全量落盘）；同一记录多处引用只写一次盘；
     * 提取失败降级为 [插图] 文本（与 EPUB 失败路径一致）。
     */
    private fun replaceRecindexImages(
        html: String,
        offsets: IntArray,
        raw: ByteArray,
        textRecordCount: Int,
        firstImageIndex: Int,
        imageDir: File,
        bookId: String,
    ): String {
        val firstImageValid = firstImageIndex > 0 && firstImageIndex < offsets.size - 1
        // 启发式 fallback：recindex -> 绝对记录号（仅 first_image_index 无效时构建）
        val heuristicMap = HashMap<Int, Int>()
        if (!firstImageValid) {
            var k = 0
            for (r in (textRecordCount + 1) until offsets.size - 1) {
                val s = offsets[r]
                val e = offsets[r + 1]
                if (e - s < 8) continue
                if (MobiCodec.imageExtOf(raw.copyOfRange(s, e)) != null) {
                    k++
                    heuristicMap[k] = r
                }
            }
        }

        val out = StringBuilder(html.length + 256)
        var last = 0
        MOBI_IMG_TAG.findAll(html).forEach { m ->
            val tag = m.value
            out.append(html, last, m.range.first)
            last = m.range.last + 1

            fun attrInt(name: String): Int? =
                Regex("\\b${Regex.escape(name)}\\s*=\\s*\"?([0-9]+)\"?", RegexOption.IGNORE_CASE)
                    .find(tag)?.groupValues?.get(1)?.toIntOrNull()

            val recindex = attrInt("recindex")
                ?: attrInt("mediumrecindex")
                ?: attrInt("lowrecindex")
            val absIdx = when {
                recindex == null || recindex <= 0 -> null
                firstImageValid -> firstImageIndex + recindex - 1
                else -> heuristicMap[recindex]
            }
            var marker: String? = null
            if (absIdx != null && absIdx in 1 until offsets.size - 1) {
                val s = offsets[absIdx]
                val e = offsets[absIdx + 1]
                val rec = raw.copyOfRange(s, e)
                val ext = MobiCodec.imageExtOf(rec)
                if (ext != null) {
                    imageDir.mkdirs()
                    val file = File(imageDir, "mobi_r$absIdx.$ext")
                    if (!file.exists() || file.length() != rec.size.toLong()) {
                        FileOutputStream(file).use { it.write(rec) }
                    }
                    marker = "epub_images/$bookId/mobi_r$absIdx.$ext"
                }
            }
            if (marker != null) {
                out.append('\n').append("[IMG:").append(marker).append(']').append('\n')
            } else {
                out.append("\n[插图]\n")
            }
        }
        out.append(html, last, html.length)
        return out.toString()
    }
}
