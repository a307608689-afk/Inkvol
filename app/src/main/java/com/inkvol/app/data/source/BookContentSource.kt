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

data class PageRef(
    val index: Int,
    val name: String,
    val size: Long,
)

/**
 * 统一的内容源：文件夹散页、ZIP/CBZ、7z/CB7、RAR/CBR、PDF 都通过它取页。
 */
interface BookContentSource : Closeable {
    val pageCount: Int
    fun listPages(): List<PageRef>
    fun openPage(index: Int): InputStream
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
class ZipSource(private val file: File) : BookContentSource {

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

    override fun close() {
        runCatching { zip.close() }
    }
}

/** 7z / CB7：commons-compress 随机访问 + LZMA2 解码。 */
class SevenZSource(private val file: File) : BookContentSource {

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

    override fun close() {
        runCatching { sevenZ.close() }
    }
}

/** RAR / CBR：junrar 8.x，同时支持 RAR4 与 RAR5（含 RAR5 解码引擎）。 */
class RarSource(private val file: File) : BookContentSource {

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

    override fun close() {
        runCatching { archive.close() }
    }
}

/** PDF：PdfRenderer 渲染成位图流，接入统一 PageLoader。 */
class PdfSource(private val file: File) : BookContentSource {

    private val descriptor = ParcelFileDescriptor.open(
        file,
        ParcelFileDescriptor.MODE_READ_ONLY,
    )
    private val renderer = PdfRenderer(descriptor)
    private val renderLock = Any()

    override val pageCount: Int get() = renderer.pageCount

    override fun listPages(): List<PageRef> =
        (0 until pageCount).map { PageRef(it, "page_${it + 1}", 0) }

    override fun openPage(index: Int): InputStream = synchronized(renderLock) {
        renderer.openPage(index).use { page ->
            val scale = 2f
            val width = (page.width * scale).toInt().coerceIn(1, 2400)
            val height = (page.height * scale).toInt().coerceIn(1, 3200)
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
