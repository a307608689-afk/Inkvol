package com.inkvol.app.data.source

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Log
import com.github.junrar.Archive
import com.inkvol.app.util.NaturalOrder
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PageRef(
    val index: Int,
    val name: String,
    val size: Long,
)

/** 章节：idx 递增，pageStart = 该章在扁平页列表中的起始页（0 基）。 */
data class BookSection(
    val idx: Int,
    val title: String,
    val pageStart: Int,
)

/**
 * 可选能力：内容源自带章节划分（EPUB spine / MOBI 章节 / 归档内顶层文件夹分组）。
 * listSections 必须是纯内存操作（各源在构造/解析期已把章节算好）；
 * 少于 2 章时返回空表——单章节书在 UI 上完全退化，零感知。
 */
interface SectionedContentSource {
    fun listSections(): List<BookSection>
}

/**
 * 顶层路径前缀分组：按「a/b.jpg 的 a」把自然序页名切成连续章节区间。
 * 仅当全部页都带目录前缀时生效（根目录散页与前缀页交错时区间语义不可靠，直接放弃）。
 */
fun folderSectionsOf(names: List<String>): List<BookSection> {
    if (names.isEmpty() || names.any { '/' !in it }) return emptyList()
    val sections = ArrayList<BookSection>()
    var currentPrefix: String? = null
    names.forEachIndexed { index, name ->
        val prefix = name.substringBefore('/')
        if (prefix != currentPrefix) {
            sections += BookSection(sections.size, prefix, index)
            currentPrefix = prefix
        }
    }
    return sections
}

/**
 * 统一的内容源：文件夹散页、ZIP/CBZ、7z/CB7、RAR/CBR、PDF 都通过它取页。
 */
interface BookContentSource : Closeable {
    val pageCount: Int
    fun listPages(): List<PageRef>
    fun openPage(index: Int): InputStream

    /**
     * 可选的页面像素尺寸（宽, 高）。
     * PDF 等「渲染一次很贵」的来源实现它，解码器就不必为了取 bounds 先渲染一次。
     */
    fun pageBounds(index: Int): Pair<Int, Int>? = null
}

/**
 * 可选：能把页面直接渲染成 Bitmap 的来源（目前只有 PDF）。
 *
 * 实现它之后，PageLoader 会跳过「渲染成图片流 → BitmapFactory 再解码」的中转，
 * 直接拿到位图；未实现该接口的来源（ZIP/7z/RAR/文件夹）行为完全不变。
 */
interface BitmapPageSource {
    /**
     * 按目标显示尺寸渲染第 [index] 页（像素，fit-inside）。
     * 线程安全由实现保证；返回的位图归调用方持有，来源不缓存也不 recycle。
     */
    fun renderPage(index: Int, targetWidth: Int, targetHeight: Int): Bitmap?
}

data class ImageDoc(
    val docId: String,
    val name: String,
    val size: Long,
)

/** 托管文件夹里的一本「散页漫画」。 */
class FolderSource(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    images: List<ImageDoc>,
) : BookContentSource {

    private val orderedDocs = images.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
    private val pages = orderedDocs.mapIndexed { index, doc -> PageRef(index, doc.name, doc.size) }

    override val pageCount: Int get() = pages.size

    override fun listPages(): List<PageRef> = pages

    override fun openPage(index: Int): InputStream {
        val doc = orderedDocs.getOrNull(index) ?: error("page $index out of range")
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, doc.docId)
        return resolver.openInputStream(uri) ?: error("cannot open ${doc.name}")
    }

    override fun close() = Unit
}

/** ZIP / CBZ：直接随机访问，按自然序过滤图片条目。 */
class ZipSource(private val file: File) : BookContentSource, SectionedContentSource {

    private val zip = ZipFile(file)
    private val entries = zip.entries().asSequence()
        .filter { !it.isDirectory && SourceRules.isImageName(it.name) }
        .sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
        .toList()
    private val pages = entries.mapIndexed { index, entry -> PageRef(index, entry.name, entry.size) }

    override val pageCount: Int get() = pages.size

    override fun listPages(): List<PageRef> = pages

    override fun openPage(index: Int): InputStream {
        val entry = entries.getOrNull(index) ?: error("page $index out of range")
        return zip.getInputStream(entry)
    }

    /** zip 内顶层文件夹 = 一章（如「第01话/001.jpg」）；全部散页时无章节。 */
    override fun listSections(): List<BookSection> =
        folderSectionsOf(entries.map { it.name })

    override fun close() {
        runCatching { zip.close() }
    }
}

/** 7z / CB7：commons-compress 随机访问 + LZMA2 解码。 */
class SevenZSource(private val file: File) : BookContentSource, SectionedContentSource {

    private val sevenZ = SevenZFile.builder().setFile(file).get()
    private val entries = sevenZ.entries
        .filter { !it.isDirectory && SourceRules.isImageName(it.name) }
        .sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
    private val pages = entries.mapIndexed { index, entry -> PageRef(index, entry.name, entry.size) }

    override val pageCount: Int get() = pages.size

    override fun listPages(): List<PageRef> = pages

    override fun openPage(index: Int): InputStream {
        val entry = entries.getOrNull(index) ?: error("page $index out of range")
        return sevenZ.getInputStream(entry)
    }

    /** 7z 内顶层文件夹 = 一章；全部散页时无章节。 */
    override fun listSections(): List<BookSection> =
        folderSectionsOf(entries.map { it.name })

    override fun close() {
        runCatching { sevenZ.close() }
    }
}

/** RAR / CBR：junrar 8.x，同时支持 RAR4 与 RAR5（含 RAR5 解码引擎）。 */
class RarSource(private val file: File) : BookContentSource, SectionedContentSource {

    private val archive = Archive(file)
    private val headers = archive.fileHeaders
        .filter { !it.isDirectory && SourceRules.isImageName(it.fileName) }
        .sortedWith { a, b -> NaturalOrder.compare(a.fileName, b.fileName) }
    private val pages = headers.mapIndexed { index, header ->
        PageRef(index, header.fileName, header.fullUnpackSize)
    }

    override val pageCount: Int get() = pages.size

    override fun listPages(): List<PageRef> = pages

    override fun openPage(index: Int): InputStream {
        val header = headers.getOrNull(index) ?: error("page $index out of range")
        return archive.getInputStream(header)
    }

    /** RAR 内顶层文件夹 = 一章；全部散页时无章节。 */
    override fun listSections(): List<BookSection> =
        folderSectionsOf(headers.map { it.fileName })

    override fun close() {
        runCatching { archive.close() }
    }
}

/**
 * PDF：PdfRenderer 渲染成位图。
 * 阅读链路走 [BitmapPageSource.renderPage] 直通入口（不再编码成 PNG 再解码）；
 * [openPage] 仅保留给指纹/面板分析等按流读取的旧旁路。
 */
class PdfSource(private val file: File) : BookContentSource, BitmapPageSource {

    private val descriptor = ParcelFileDescriptor.open(
        file,
        ParcelFileDescriptor.MODE_READ_ONLY,
    )
    private val renderer = PdfRenderer(descriptor)
    private val renderLock = Any()

    // 构造时缓存页数：关闭后再读 PdfRenderer.getPageCount() 会抛 IllegalStateException
    // （阅读器退出动画/在途预取仍可能摸到 source.pageCount）。
    private val pageCountCache = renderer.pageCount

    override val pageCount: Int get() = pageCountCache

    override fun listPages(): List<PageRef> =
        (0 until pageCount).map { PageRef(it, "page_${it + 1}", 0) }

    override fun pageBounds(index: Int): Pair<Int, Int>? = synchronized(renderLock) {
        runCatching {
            renderer.openPage(index).use { it.width to it.height }
        }.getOrNull()
    }

    /**
     * 按显示目标尺寸直接渲染：一次 render 即得位图，省掉 PNG 编码 + 解码。
     * PdfRenderer 只允许单页在场，这里沿用同一把 [renderLock] 串行。
     */
    override fun renderPage(index: Int, targetWidth: Int, targetHeight: Int): Bitmap? =
        synchronized(renderLock) {
            runCatching {
                renderer.openPage(index).use { page ->
                    val (width, height) = renderSize(
                        pageWidth = page.width,
                        pageHeight = page.height,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                    )
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // PdfRenderer 输出 alpha 背景；不先擦白会表现为黑页。
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }.getOrNull()
        }

    /**
     * fit-inside 目标框（页面在屏幕上按 ContentScale.Fit 显示，按同尺寸渲染最省且不糊），
     * 再用像素预算兜底：宽页面/超长页面在超大目标下会按比例回缩，避免 OOM。
     */
    private fun renderSize(
        pageWidth: Int,
        pageHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Pair<Int, Int> {
        val safeW = pageWidth.coerceAtLeast(1)
        val safeH = pageHeight.coerceAtLeast(1)
        val boxW = targetWidth.coerceIn(1, MAX_RENDER_SIDE).toFloat()
        val boxH = targetHeight.coerceIn(1, MAX_RENDER_SIDE).toFloat()
        var scale = minOf(boxW / safeW, boxH / safeH)
        if (!scale.isFinite() || scale <= 0f) scale = 1f
        val pixels = safeW.toDouble() * safeH.toDouble() * scale.toDouble() * scale.toDouble()
        if (pixels > MAX_RENDER_PIXELS) {
            scale *= sqrt(MAX_RENDER_PIXELS / pixels).toFloat()
        }
        val width = (safeW * scale).roundToInt().coerceIn(1, MAX_RENDER_SIDE)
        val height = (safeH * scale).roundToInt().coerceIn(1, MAX_RENDER_SIDE)
        return width to height
    }

    override fun openPage(index: Int): InputStream = synchronized(renderLock) {
        renderer.openPage(index).use { page ->
            // 旧流式旁路（指纹/面板分析用），阅读链路已改走 renderPage。
            // 大页面上限：长边 1600px。
            // 之前固定 2x，遇到 2000x3000 的漫画页就是 4000x6000 = 96MB 位图，
            // 直接 OOM（表现为一直转圈，随后闪退）。这里按目标尺寸等比缩放。
            val maxSide = 1600f
            val scale = minOf(
                2f,
                maxSide / page.width.coerceAtLeast(1).toFloat(),
                maxSide / page.height.coerceAtLeast(1).toFloat(),
            )
            val width = (page.width * scale).toInt().coerceIn(1, maxSide.toInt())
            val height = (page.height * scale).toInt().coerceIn(1, maxSide.toInt())
            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888,
            )
            // PdfRenderer 输出 alpha 背景；不先擦白会表现为黑页。
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, output)
            bitmap.recycle()
            ByteArrayInputStream(output.toByteArray())
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    private companion object {
        /** 渲染边上限：高倍放大（loadHighRes）时也不会突破 TextureView 常用上限。 */
        const val MAX_RENDER_SIDE = 4096

        /** 像素预算：12M px ≈ 48MB ARGB_8888，防宽页 / 弯折页在超大目标下 OOM。 */
        const val MAX_RENDER_PIXELS = 12_000_000.0
    }
}

object SourceRules {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "avif")
    private val archiveExtensions = setOf("zip", "cbz", "rar", "cbr", "7z", "cb7", "pdf")
    private val novelExtensions = setOf("txt", "epub", "mobi")

    fun isImageName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in imageExtensions

    fun isArchiveName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in archiveExtensions

    fun isNovelName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in novelExtensions

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    /** 不信扩展名：读 8 字节魔数。 */
    fun detect(bytes: ByteArray): String? {
        if (bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())
        ) {
            return "ZIP"
        }
        if (bytes.size >= 6 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x61.toByte() &&
            bytes[2] == 0x72.toByte() && bytes[3] == 0x21.toByte()
        ) {
            return "RAR"
        }
        if (bytes.size >= 6 &&
            bytes[0] == 0x37.toByte() && bytes[1] == 0x7A.toByte() &&
            bytes[2] == 0xBC.toByte() && bytes[3] == 0xAF.toByte()
        ) {
            return "SEVEN_Z"
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()
        ) {
            return "PDF"
        }
        if (bytes.size >= 68 &&
            bytes[60] == 0x42.toByte() && bytes[61] == 0x4F.toByte() &&
            bytes[62] == 0x4F.toByte() && bytes[63] == 0x4B.toByte() &&
            bytes[64] == 0x4D.toByte() && bytes[65] == 0x4F.toByte() &&
            bytes[66] == 0x42.toByte() && bytes[67] == 0x49.toByte()
        ) {
            return "MOBI"
        }
        return null
    }
}
