package com.inkvol.app.data.import

import android.content.Context
import com.inkvol.app.data.model.NovelChapterRecord
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.SequenceInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * M6.1-A TXT 导入解析器。
 *
 * 目标不是解析成完整阅读器模型，而是把原始 TXT 归一化成 UTF-8 文本，
 * 并给出后续阅读器可直接随机访问的章节字节区间。没有识别到章节时整本一个章节兜底。
 */
object TxtNovelParser {

    data class Parsed(
        val textFile: File,
        val chapters: List<NovelChapterRecord>,
        val charset: Charset,
    )

    fun parse(
        context: Context,
        bookId: String,
        input: InputStream,
        purifyRules: List<Regex> = emptyList(),
    ): Parsed {
        val head = ByteArray(8192)
        val headLength = input.read(head).coerceAtLeast(0)
        val headBytes = head.copyOf(headLength)
        val charset = detectCharset(headBytes)
        val fullInput = SequenceInputStream(
            ByteArrayInputStream(headBytes),
            input,
        )
        val outputDir = File(context.filesDir, "novel_text").apply { mkdirs() }
        val textFile = File(outputDir, "$bookId.txt")
        val chapters = ArrayList<NovelChapterRecord>()
        var byteOffset = 0L
        var pendingChapter: NovelChapterRecord? = null
        var totalChars = 0

        val reader = BufferedReader(InputStreamReader(fullInput, charset))
        FileOutputStream(textFile).use { output ->
            var lineIndex = 0
            while (true) {
                val rawLine = reader.readLine() ?: break
                val line = (if (lineIndex == 0) rawLine.trimStart('\uFEFF') else rawLine)
                    .trimEnd('\r', '\n')
                // M6.3-C 净化：命中任一启用规则的行整行不入副本（广告/水印/推广行）
                if (purifyRules.any { it.containsMatchIn(line) }) {
                    lineIndex++
                    continue
                }
                val encoded = (line + "\n").toByteArray(StandardCharsets.UTF_8)
                val lineStart = byteOffset
                byteOffset += encoded.size
                totalChars += encoded.size

                if (isChapterTitle(line)) {
                    pendingChapter?.let { chapters += it.copy(byteEnd = lineStart) }
                    val nextIndex = chapters.size + if (pendingChapter == null) 0 else 1
                    pendingChapter = NovelChapterRecord(
                        index = nextIndex,
                        title = normalizedTitle(line, lineIndex),
                        byteStart = lineStart,
                        byteEnd = byteOffset,
                        charCount = encoded.size,
                    )
                } else if (chapters.isEmpty() && pendingChapter == null && line.isNotBlank()) {
                    // 章标题前如果有内容，仍然给一个“开篇”区间，避免正文丢失。
                    pendingChapter = NovelChapterRecord(
                        index = 0,
                        title = "开篇",
                        byteStart = 0L,
                        byteEnd = byteOffset,
                        charCount = byteOffset.toInt(),
                    )
                }

                output.write(encoded)
                pendingChapter = pendingChapter?.copy(
                    charCount = pendingChapter.charCount + rawLine.length + 1,
                )
                lineIndex++
            }
        }

        pendingChapter?.let { chapters += it.copy(byteEnd = byteOffset) }
        if (chapters.isEmpty()) {
            chapters += NovelChapterRecord(
                index = 0,
                title = "全文",
                byteStart = 0L,
                byteEnd = byteOffset,
                charCount = byteOffset.toInt(),
            )
        }
        return Parsed(
            textFile = textFile,
            chapters = chapters.mapIndexed { index, chapter -> chapter.copy(index = index) },
            charset = charset,
        )
    }

    fun deleteText(context: Context, bookId: String) {
        runCatching { File(File(context.filesDir, "novel_text"), "$bookId.txt").delete() }
    }

    private fun detectCharset(bytes: ByteArray): Charset {
        val headLength = bytes.size

        if (headLength >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return StandardCharsets.UTF_8
        }
        if (headLength >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return StandardCharsets.UTF_16LE
        }
        if (headLength >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return StandardCharsets.UTF_16BE
        }
        val zeroCount = bytes.count { it == 0.toByte() }
        if (zeroCount > bytes.size / 8 && bytes.size > 8) {
            return StandardCharsets.UTF_16LE
        }

        runCatching {
            Charset.forName("UTF-8").newDecoder().run {
                reset()
                decode(java.nio.ByteBuffer.wrap(bytes))
            }
            return StandardCharsets.UTF_8
        }
        return Charset.forName("GB18030")
    }

    private val chapterPatterns = listOf(
        Regex("""^\s*(第\s*[0-9一二三四五六七八九十百千万零〇两]+\s*[章节回卷集篇部][^\n]{0,80})$"""),
        Regex("""^\s*(Chapter\s+\d+\b[^\n]{0,80})$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(\d{1,4}\s*[.、::]\s*\S[^\n]{0,80})$"""),
    )

    private fun isChapterTitle(line: String): Boolean {
        if (line.isBlank() || line.length > 100) return false
        return chapterPatterns.any { it.matches(line) }
    }

    private fun normalizedTitle(line: String, fallbackIndex: Int): String =
        line.trim().take(80).ifBlank { "第 ${fallbackIndex + 1} 章" }
}
