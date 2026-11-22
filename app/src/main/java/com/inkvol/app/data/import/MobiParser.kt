package com.inkvol.app.data.import

import android.content.Context
import com.inkvol.app.data.model.NovelChapterRecord
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
 * 图片：旧 MOBI 图片在独立资源记录（recindex 引用），首轮降级为 `[插图]` 占位，
 * 与 EPUB 图片提取失败的降级路径一致。
 */
object MobiParser {

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
        val numRecords = u16(bytes, 76)
        if (numRecords < 2) throw IllegalArgumentException("MOBI 无记录")
        val offsets = IntArray(numRecords + 1)
        for (i in 0 until numRecords) {
            val base = 78 + i * 8
            offsets[i] = u32(bytes, base)
        }
        offsets[numRecords] = bytes.size

        // ---------------- record 0：PalmDOC + MOBI 头 ----------------
        val r0 = bytes.copyOfRange(offsets[0], offsets[1])
        if (r0.size < 32) throw IllegalArgumentException("MOBI 头损坏")
        val compression = u16(r0, 0)
        val textLength = u32(r0, 4)
        val textRecordCount = u16(r0, 8)
        val encryption = u16(r0, 14)
        if (encryption != 0) throw IllegalArgumentException("DRM 加密的 MOBI 不受支持")
        if (compression == 17480) throw IllegalArgumentException("暂不支持 HUFF/CDIC 压缩的 MOBI（AZW3/KF8）")
        if (compression != 1 && compression != 2) throw IllegalArgumentException("未知压缩方式 $compression")
        if (r0.size < 24 || String(r0, 16, 4, Charsets.US_ASCII) != "MOBI") {
            throw IllegalArgumentException("不是 MOBI 格式（缺少 MOBI 头）")
        }
        val textEncoding = u32(r0, 28)
        val charset = if (textEncoding == 65001) Charsets.UTF_8 else charset("windows-1252")

        // ---------------- 文本记录解压 + 拼接 ----------------
        val htmlBytes = ByteArrayOutputStream(textLength.coerceAtMost(16_000_000))
        for (i in 1..textRecordCount) {
            val s = offsets.getOrElse(i) { throw IllegalArgumentException("MOBI 记录越界") }
            val e = offsets.getOrElse(i + 1) { bytes.size }
            val rec = bytes.copyOfRange(s, e)
            if (compression == 2) {
                palmDocDecompress(rec, charset, htmlBytes)
            } else {
                htmlBytes.write(rec)
            }
        }
        val html = String(htmlBytes.toByteArray(), Charsets.UTF_8)

        // ---------------- 章节拆分 ----------------
        val sectionHtml = if (html.contains("<mbp:section>")) {
            html.split("<mbp:section>").filter { it.isNotBlank() }
        } else {
            listOf(html)
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

    /** PalmDOC LZ77 解压。输出为 textEncoding 编码的字节流。 */
    private fun palmDocDecompress(data: ByteArray, charset: java.nio.charset.Charset, out: ByteArrayOutputStream) {
        // 可增长的字节缓冲（回引用需要随机读已解压内容）
        var buf = ByteArray(maxOf(64, data.size * 2))
        var size = 0
        fun ensure(extra: Int) {
            if (size + extra > buf.size) {
                buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
            }
        }
        fun writeByte(b: Int) {
            ensure(1)
            buf[size++] = b.toByte()
        }
        fun writeChar(code: Int) {
            val encoded = code.toChar().toString().toByteArray(charset)
            ensure(encoded.size)
            encoded.copyInto(buf, size)
            size += encoded.size
        }

        var i = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            when {
                b == 0 -> { writeByte(0); i += 1 }
                b <= 8 -> {
                    val n = minOf(b, data.size - i - 1)
                    ensure(n)
                    for (j in 0 until n) buf[size++] = data[i + 1 + j]
                    i += b + 1
                }
                b <= 0x7F -> { writeByte(b); i += 1 }
                b <= 0xBF -> {
                    // 两字节回引用：distance 11 位、length 3 位（+3）
                    if (i + 1 >= data.size) break
                    val g = (b shl 8) or (data[i + 1].toInt() and 0xFF)
                    i += 2
                    val distance = (g shr 3) and 0x7FF
                    val length = (g and 0x7) + 3
                    ensure(length)
                    var src = size - distance
                    if (src < 0) src = 0
                    repeat(length) { j ->
                        val v = if (src + j < size) buf[src + j].toInt() and 0xFF else 0x20
                        buf[size++] = v.toByte()
                    }
                }
                b == 0xC0 -> {
                    // space + ascii80
                    if (i + 1 >= data.size) break
                    writeByte(0x20)
                    writeByte((data[i + 1].toInt() and 0xFF) xor 0x80)
                    i += 2
                }
                b <= 0xDF -> {
                    // 两字节 unicode（UTF-8 风格 0x80-0x7FF）
                    if (i + 1 >= data.size) break
                    val c = data[i + 1].toInt() and 0xFF
                    writeChar(((b and 0x1F) shl 6) or (c and 0x3F))
                    i += 2
                }
                else -> { writeByte(b); i += 1 }
            }
        }
        out.write(buf, 0, size)
    }

    private fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)
}
