package com.inkvol.app.spike

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 仅 debug：模拟一个真实漫画文件夹树，供 M1 扫描端到端测试。
 *
 * root
 * ├── 测试系列/
 * │   ├── 第1卷/  (4 张 JPG)
 * │   └── 第2卷/  (3 张 JPG)
 * └── 单本.cbz    (3 页)
 */
class TestLibraryProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.inkvol.app.spike.library"
        const val ROOT_ID = "root"
        const val SERIES_ID = "dir_series"
        const val V1_ID = "dir_v1"
        const val V2_ID = "dir_v2"
        const val CBZ_ID = "file_cbz"
        const val CB7_ID = "file_cb7"
        const val PDF_ID = "file_pdf"

        private val DEFAULT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }

    private var filesReady = false

    override fun onCreate(): Boolean {
        ensureFiles()
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        ensureFiles()
        val segments = uri.pathSegments
        val documentIndex = segments.indexOf("document")
        require(documentIndex >= 0 && segments.size > documentIndex + 1) { "bad uri $uri" }
        val documentId = segments[documentIndex + 1]
        val isChildren = segments.lastOrNull() == "children"
        val cols = projection ?: DEFAULT_PROJECTION
        val cursor = MatrixCursor(cols)
        if (isChildren) {
            childrenOf(documentId).forEach { addRow(cursor, cols, it) }
        } else {
            addRow(cursor, cols, nodeOf(documentId))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        val documentId = uri.pathSegments.getOrNull(uri.pathSegments.indexOf("document") + 1) ?: return null
        return nodeOf(documentId).mime
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        ensureFiles()
        val documentId = uri.pathSegments.getOrNull(uri.pathSegments.indexOf("document") + 1)
            ?: error("bad uri $uri")
        val file = fileFor(documentId) ?: error("not a file: $documentId")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private data class Node(
        val id: String,
        val name: String,
        val mime: String,
        val size: Long,
    )

    private fun childrenOf(parentId: String): List<Node> = when (parentId) {
        ROOT_ID -> listOf(Node(SERIES_ID, "测试系列", Document.MIME_TYPE_DIR, 0))
        SERIES_ID -> listOf(
            Node(V1_ID, "第1卷", Document.MIME_TYPE_DIR, 0),
            Node(V2_ID, "第2卷", Document.MIME_TYPE_DIR, 0),
            Node(CBZ_ID, "单本.cbz", "application/vnd.comicbook+zip", fileFor(CBZ_ID)?.length() ?: 0),
            Node(CB7_ID, "附加.cb7", "application/x-7z-compressed", fileFor(CB7_ID)?.length() ?: 0),
            Node(PDF_ID, "扫描件.pdf", "application/pdf", fileFor(PDF_ID)?.length() ?: 0),
        )

        V1_ID -> (0 until 4).map { index ->
            val id = "v1_$index"
            Node(id, "page_${index + 1}.jpg", "image/jpeg", fileFor(id)?.length() ?: 0)
        }

        V2_ID -> (0 until 3).map { index ->
            val id = "v2_$index"
            Node(id, "page_${index + 1}.jpg", "image/jpeg", fileFor(id)?.length() ?: 0)
        }

        else -> emptyList()
    }

    private fun nodeOf(documentId: String): Node = when (documentId) {
        ROOT_ID -> Node(ROOT_ID, "测试库", Document.MIME_TYPE_DIR, 0)
        SERIES_ID -> Node(SERIES_ID, "测试系列", Document.MIME_TYPE_DIR, 0)
        V1_ID -> Node(V1_ID, "第1卷", Document.MIME_TYPE_DIR, 0)
        V2_ID -> Node(V2_ID, "第2卷", Document.MIME_TYPE_DIR, 0)
        CBZ_ID -> Node(CBZ_ID, "单本.cbz", "application/vnd.comicbook+zip", fileFor(CBZ_ID)?.length() ?: 0)
        CB7_ID -> Node(CB7_ID, "附加.cb7", "application/x-7z-compressed", fileFor(CB7_ID)?.length() ?: 0)
        PDF_ID -> Node(PDF_ID, "扫描件.pdf", "application/pdf", fileFor(PDF_ID)?.length() ?: 0)
        else -> childrenOf(V1_ID).firstOrNull { it.id == documentId }
            ?: childrenOf(V2_ID).firstOrNull { it.id == documentId }
            ?: error("unknown document $documentId")
    }

    private fun addRow(cursor: MatrixCursor, cols: Array<out String>, node: Node) {
        val row = arrayOfNulls<Any>(cols.size)
        cols.forEachIndexed { index, col ->
            row[index] = when (col) {
                Document.COLUMN_DOCUMENT_ID -> node.id
                Document.COLUMN_DISPLAY_NAME -> node.name
                Document.COLUMN_SIZE -> if (node.mime == Document.MIME_TYPE_DIR) null else node.size
                Document.COLUMN_MIME_TYPE -> node.mime
                Document.COLUMN_LAST_MODIFIED -> 1_700_000_000_000L
                else -> null
            }
        }
        cursor.addRow(row)
    }

    private fun fileFor(documentId: String): File? {
        val dir = File(requireNotNull(context).cacheDir, "library_test")
        return when {
            documentId == CBZ_ID -> File(dir, "book.cbz")
            documentId == CB7_ID -> File(dir, "book.cb7")
            documentId == PDF_ID -> File(dir, "book.pdf")
            documentId.startsWith("v1_") -> File(dir, "v1_${documentId.removePrefix("v1_")}.jpg")
            documentId.startsWith("v2_") -> File(dir, "v2_${documentId.removePrefix("v2_")}.jpg")
            else -> null
        }
    }

    @Synchronized
    private fun ensureFiles() {
        if (filesReady) return
        val ctx = context ?: return
        val dir = File(ctx.cacheDir, "library_test").apply { mkdirs() }
        (0 until 4).forEach { index -> writeImage(File(dir, "v1_$index.jpg"), index, Color.rgb(60, 120, 200)) }
        (0 until 3).forEach { index -> writeImage(File(dir, "v2_$index.jpg"), index, Color.rgb(200, 120, 60)) }

        val cbz = File(dir, "book.cbz")
        ZipOutputStream(FileOutputStream(cbz)).use { zip ->
            repeat(3) { index ->
                val page = File(dir, "cbz_$index.jpg")
                writeImage(page, index, Color.rgb(90, 160, 90))
                zip.putNextEntry(ZipEntry("page_${index + 1}.jpg"))
                page.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }

        val cb7 = File(dir, "book.cb7")
        SevenZOutputFile(cb7).use { sevenZ ->
            repeat(2) { index ->
                val page = File(dir, "cb7_$index.jpg")
                writeImage(page, index, Color.rgb(150, 90, 180))
                val entry = SevenZArchiveEntry().apply { name = "page_${index + 1}.jpg" }
                sevenZ.putArchiveEntry(entry)
                val bytes = page.readBytes()
                sevenZ.write(bytes, 0, bytes.size)
                sevenZ.closeArchiveEntry()
            }
        }

        val pdf = File(dir, "book.pdf")
        val document = PdfDocument()
        try {
            repeat(2) { index ->
                val info = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                val page = document.startPage(info)
                page.canvas.drawColor(Color.WHITE)
                val paint = Paint().apply {
                    color = Color.BLACK
                    textSize = 36f
                    isAntiAlias = true
                }
                page.canvas.drawText("Inkvol PDF ${index + 1}", 60f, 120f, paint)
                document.finishPage(page)
            }
            FileOutputStream(pdf).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        filesReady = true
    }

    private fun writeImage(file: File, index: Int, color: Int) {
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint().apply {
            this.color = Color.WHITE
            textSize = 64f
            isAntiAlias = true
        }
        canvas.drawText("${index + 1}", 100f, 190f, paint)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
    }
}
