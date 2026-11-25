package com.inkvol.app.data.source

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * MOBI 图片页索引：把 PDB 资源记录里的图片按阅读顺序切分成页。
 *
 * 页序两级：
 * 1. 正文 `<img recindex="N">` 引用序（N → 绝对记录号 first_image_index + N - 1）——真值；
 * 2. 资源记录出现顺序（first_image_index 无效或无引用时兜底，生成器写入顺序即页序）。
 *
 * 图片边界：一张图可占多个连续记录。以「记录开头命中图片魔数」起新页，
 * 终止条件 = 上一记录尾部出现该格式终止标记（JPEG FFD9 / PNG IEND / GIF 0x3B）、
 * 或当前记录开头是 4 个大写 ASCII 的资源边界记录（FLIS/EOFC/RESC/FONT...）、
 * 或下一记录又命中图片魔数。页字节惰性拼接（大文件不全量进内存）。
 *
 * 页数与判定（NovelComicProbe.probeMobi）同源。DRM / HUFF/CDIC / 非 MOBI 头返回 null，
 * 调用方按原小说路径处理。
 */
object MobiImageIndex {

    data class Result(
        /** 页 = 记录号闭区间。 */
        val pages: List<IntRange>,
        /** 记录偏移表（MobiImageSource.openPage 拼页用）。 */
        val offsets: IntArray,
        /** 解压正文去标签 + 去实体后的非空白字符数（判定口径与 EPUB 一致）。 */
        val textLen: Int,
        /** 正文 `<img recindex>` 引用数。 */
        val imgTags: Int,
        /** 章节起始页号（页列表下标，不含 0；少于 2 个为空）。 */
        val chapterPages: List<Int>,
    )

    private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ANY_TAG = Regex("<[^>]+>")
    private val ENTITY = Regex("&[#a-zA-Z0-9]+;")

    fun parse(file: File): Result? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val fileLen = raf.length()
            if (fileLen < 78L + 8) return@use null

            val numRecords = run {
                raf.seek(76L)
                val buf = ByteArray(2)
                raf.readFully(buf)
                MobiCodec.u16(buf, 0)
            }
            if (numRecords < 2) return@use null

            val table = ByteArray(numRecords * 8)
            raf.seek(78L)
            raf.readFully(table)
            val offsets = IntArray(numRecords + 1)
            for (i in 0 until numRecords) offsets[i] = MobiCodec.u32(table, i * 8)
            offsets[numRecords] = fileLen.toInt()

            fun readBytes(start: Int, end: Int): ByteArray {
                val size = (end - start).coerceAtLeast(0)
                val buf = ByteArray(size)
                if (size > 0) {
                    raf.seek(start.toLong())
                    raf.readFully(buf)
                }
                return buf
            }

            // ---------------- record 0：PalmDOC + MOBI 头 ----------------
            val r0 = readBytes(offsets[0], offsets[1])
            if (r0.size < 32) return@use null
            val compression = MobiCodec.u16(r0, 0)
            val textLength = MobiCodec.u32(r0, 4)
            val textRecordCount = MobiCodec.u16(r0, 8)
            val encryption = MobiCodec.u16(r0, 14)
            if (encryption != 0) return@use null
            if (compression != 1 && compression != 2) return@use null
            if (r0.size < 24 || String(r0, 16, 4, Charsets.US_ASCII) != "MOBI") return@use null
            val charset = if (MobiCodec.u32(r0, 28) == 65001) {
                Charsets.UTF_8
            } else {
                charset("windows-1252")
            }
            val firstImageIndex = if (r0.size >= 112) MobiCodec.u32(r0, 108) else 0

            // ---------------- 解压文本（判定 + recindex 页序） ----------------
            val htmlBytes = ByteArrayOutputStream(textLength.coerceAtMost(16_000_000))
            for (i in 1..textRecordCount) {
                val rec = readBytes(offsets[i], offsets[i + 1])
                if (compression == 2) {
                    MobiCodec.palmDocDecompress(rec, charset, htmlBytes)
                } else {
                    htmlBytes.write(rec)
                }
            }
            val html = String(htmlBytes.toByteArray(), Charsets.UTF_8)
            val textLen = html.replace(ANY_TAG, " ").replace(ENTITY, " ")
                .count { !it.isWhitespace() }

            // recindex → 文内偏移（章节边界判定需要：img 引用落在哪个 mbp:section 段里）
            val imgRefs = IMG_TAG.findAll(html).mapNotNull { m ->
                Regex("\\brecindex\\s*=\\s*\"?([0-9]+)\"?", RegexOption.IGNORE_CASE)
                    .find(m.value)?.groupValues?.get(1)?.toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { it to m.range.first }
            }.toList()

            // ---------------- 图片区扫描 + 多记录切页 ----------------
            val firstImageValid = firstImageIndex > textRecordCount && firstImageIndex < numRecords
            val scanStart = if (firstImageValid) firstImageIndex else textRecordCount + 1
            val ranges = ArrayList<IntRange>()
            var currentStart = -1
            var currentExt: String? = null

            for (r in scanStart until numRecords) {
                val s = offsets[r]
                val e = offsets[r + 1]
                if (e <= s) continue
                val head = readBytes(s, minOf(e, s + 16))
                val ext = MobiCodec.imageExtOf(head)
                if (ext != null) {
                    if (currentStart >= 0) ranges += currentStart..(r - 1)
                    currentStart = r
                    currentExt = ext
                    continue
                }
                if (currentStart < 0) continue
                // 非图片记录：资源边界记录（FLIS/EOFC/RESC...）或上一记录尾部已到终止标记 → 结页
                val asciiBoundary = head.size >= 4 && head.take(4).all {
                    it in 'A'.code.toByte()..'Z'.code.toByte()
                }
                val tailStart = maxOf(offsets[r - 1], offsets[r] - 12)
                val tail = readBytes(tailStart, offsets[r])
                val terminated = when (currentExt) {
                    "jpg" -> tail.size >= 2 && (0 until tail.size - 1).any {
                        tail[it] == 0xFF.toByte() && tail[it + 1] == 0xD9.toByte()
                    }
                    "png" -> String(tail, Charsets.US_ASCII).contains("IEND")
                    "gif" -> tail.isNotEmpty() && tail.last() == 0x3B.toByte()
                    else -> false
                }
                if (asciiBoundary || terminated) {
                    ranges += currentStart..(r - 1)
                    currentStart = -1
                    currentExt = null
                }
            }
            if (currentStart >= 0) ranges += currentStart..(numRecords - 1)

            // ---------------- 页序：recindex 引用序优先 ----------------
            val ordered = if (firstImageValid && imgRefs.isNotEmpty() && ranges.isNotEmpty()) {
                val byStart = ranges.associateBy { it.first }
                val referenced = imgRefs.map { (n, _) -> byStart[firstImageIndex + n - 1] }
                    .filterNotNull()
                val referencedSet = referenced.toHashSet()
                val unmatched = ranges.filter { it !in referencedSet }
                // 引用匹配率过低说明 recindex 语义对不上（如老转换器），退回扫描序
                if (referenced.size * 2 >= ranges.size) referenced + unmatched else ranges
            } else {
                ranges
            }

            // ---------------- 章节边界：mbp:section 段 → 段内首图 = 章起始页 ----------------
            // MOBI 无逐章标题，目录里按「第 N 话」编号呈现；无边界标记或只有 1 章则不产出。
            val chapterPages = run {
                val result = ArrayList<Int>()
                if (firstImageValid && ordered.isNotEmpty()) {
                    val sectionOffsets = Regex("<mbp:section", RegexOption.IGNORE_CASE)
                        .findAll(html).map { it.range.first }.toList()
                    if (sectionOffsets.size >= 2) {
                        val pageByRec = ordered.mapIndexed { i, r -> r.first to i }.toMap()
                        // 段起点 = 0 + 每个 <mbp:section 标记；imgRefs 按文序排列，双指针扫段
                        val segStarts = intArrayOf(0) + sectionOffsets.toIntArray()
                        var refPos = 0
                        for (s in 1 until segStarts.size) {
                            while (refPos < imgRefs.size && imgRefs[refPos].second < segStarts[s]) {
                                refPos++
                            }
                            if (refPos >= imgRefs.size) break
                            val pageIdx = pageByRec[firstImageIndex + imgRefs[refPos].first - 1]
                            if (pageIdx != null && pageIdx > 0 &&
                                (result.isEmpty() || result.last() != pageIdx)
                            ) {
                                result += pageIdx
                            }
                        }
                    }
                }
                if (result.size >= 2) result else emptyList()
            }

            Result(
                pages = ordered,
                offsets = offsets,
                textLen = textLen,
                imgTags = imgRefs.size,
                chapterPages = chapterPages,
            )
        }
    }.getOrNull()
}

/**
 * 漫画型 MOBI 的内容源：页区间来自 [MobiImageIndex]，openPage 时按记录区间
 * 惰性拼接字节（大文件不全量进内存）。
 */
class MobiImageSource(private val file: File) : BookContentSource, SectionedContentSource {

    private val lock = Any()
    private val raf = RandomAccessFile(file, "r")
    private val offsets: IntArray
    private val pages: List<IntRange>
    private val pageRefs: List<PageRef>
    private val chapterPages: List<Int>

    init {
        val index = MobiImageIndex.parse(file)
        offsets = index?.offsets ?: IntArray(0)
        pages = index?.pages.orEmpty()
        pageRefs = pages.mapIndexed { i, range -> PageRef(i, "record_${range.first}", 0) }
        chapterPages = index?.chapterPages.orEmpty()
    }

    /** MOBI 无逐章标题，按「第 N 话」编号；少于 2 章返回空表。 */
    override fun listSections(): List<BookSection> =
        chapterPages.takeIf { it.size >= 2 }
            ?.mapIndexed { i, start -> BookSection(i, "第 ${i + 1} 话", start) }
            .orEmpty()

    override val pageCount: Int get() = pages.size

    override fun listPages(): List<PageRef> = pageRefs

    override fun openPage(index: Int): InputStream = synchronized(lock) {
        val range = pages.getOrNull(index) ?: error("page $index out of range")
        val out = ByteArrayOutputStream()
        for (r in range.first..range.last) {
            val s = offsets[r]
            val e = offsets[r + 1]
            val buf = ByteArray((e - s).coerceAtLeast(0))
            if (buf.isNotEmpty()) {
                raf.seek(s.toLong())
                raf.readFully(buf)
                out.write(buf)
            }
        }
        ByteArrayInputStream(out.toByteArray())
    }

    override fun close() {
        runCatching { raf.close() }
    }
}
