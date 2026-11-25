package com.inkvol.app.data.import

import android.util.Log
import com.inkvol.app.data.source.EpubImageIndex
import com.inkvol.app.data.source.MobiImageIndex
import java.io.File

/**
 * 小说 / 漫画类型探针（A 档：保守，宁可漏判也不把图文小说判成漫画）。
 *
 * 判定只用 EPUB 自身的结构信号，不猜文件名：
 * - S1 固定版式（OPF `rendition:layout=pre-paginated`）且成页图片 ≥ 3；
 * - S2 抽样章平均文字 < 30 字/章、成页图片 ≥ 5 且章数 ≥ 3（一页一图典型结构）；
 * - S3 ZIP 内图片条目 ≥ 10 且全文估算文字 < 1500 字（画集 / 图集）。
 *
 * 页数与 [EpubImageIndex] 同源，直接作为漫画阅读器的总页数使用。
 * 判不出（解析失败）返回 null，调用方按原小说路径处理，零回归。
 */
object NovelComicProbe {

    private const val TAG = "InkvolType"

    private const val SAMPLE_DOCS = 8
    private const val MIN_FIXED_LAYOUT_PAGES = 3
    private const val MIN_SPINE_IMAGES = 5
    private const val MIN_SPINE_DOCS = 3
    private const val MAX_CHARS_PER_DOC = 30
    private const val MIN_IMAGE_ENTRIES = 10
    private const val MAX_ESTIMATED_TEXT = 1500L

    // MOBI 没有 fixed-layout / spine，只有文字量一条判据轴，阈值比 EPUB 再严一档
    private const val MOBI_MIN_PAGES = 5
    private const val MOBI_MAX_TEXT = 1500
    private const val MOBI_MIN_PAGES_LOOSE = 10
    private const val MOBI_MAX_TEXT_LOOSE = 3000

    data class Verdict(
        val isComic: Boolean,
        val pageCount: Int,
    )

    fun probeEpub(file: File): Verdict? {
        val index = EpubImageIndex.parse(file, SAMPLE_DOCS) ?: return null
        val pages = index.images.size
        if (pages <= 0) return Verdict(false, 0)

        val avgChars = if (index.sampledDocs > 0) {
            index.sampledTextLen / index.sampledDocs
        } else {
            -1
        }
        // 抽样文档字数按章数线性外推；漫画 EPUB 每章只有一张图，外推同样很小。
        val estimatedText = if (index.sampledDocs > 0 && index.spineDocs > 0) {
            index.sampledTextLen.toLong() * index.spineDocs / index.sampledDocs
        } else {
            index.sampledTextLen.toLong()
        }

        val comic = when {
            index.fixedLayout && pages >= MIN_FIXED_LAYOUT_PAGES -> true
            avgChars in 0 until MAX_CHARS_PER_DOC &&
                pages >= MIN_SPINE_IMAGES && index.spineDocs >= MIN_SPINE_DOCS -> true
            index.imageEntries >= MIN_IMAGE_ENTRIES && estimatedText < MAX_ESTIMATED_TEXT -> true
            else -> false
        }

        Log.i(
            TAG,
            "[TYPEDETECT] file=${file.name} imgEntries=${index.imageEntries} " +
                "spineDocs=${index.spineDocs} imgInSpine=${index.spineImages} pages=$pages " +
                "sampled=${index.sampledTextLen}/${index.sampledDocs} est=$estimatedText avg=$avgChars " +
                "fixed=${index.fixedLayout} -> ${if (comic) "COMIC" else "NOVEL"}",
        )
        return Verdict(comic, pages)
    }

    /**
     * MOBI 漫画判定：图片页来自 [MobiImageIndex]（资源记录切页，与漫画内容源同源）。
     * DRM / HUFF/CDIC（AZW3/KF8）/ 非 MOBI 头返回 null，保持原小说导入路径与报错文案。
     */
    fun probeMobi(file: File): Verdict? {
        val index = MobiImageIndex.parse(file) ?: return null
        val pages = index.pages.size
        if (pages <= 0) return Verdict(false, 0)

        val comic = when {
            pages >= MOBI_MIN_PAGES && index.textLen < MOBI_MAX_TEXT -> true
            pages >= MOBI_MIN_PAGES_LOOSE && index.textLen < MOBI_MAX_TEXT_LOOSE -> true
            else -> false
        }

        Log.i(
            TAG,
            "[TYPEDETECT] mobi file=${file.name} pages=$pages imgTags=${index.imgTags} " +
                "textLen=${index.textLen} -> ${if (comic) "COMIC" else "NOVEL"}",
        )
        return Verdict(comic, pages)
    }
}
