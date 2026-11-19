package com.inkvol.app.data.source

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.inkvol.app.data.model.BookRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 把书架记录还原成可读内容源：
 * - FOLDER：按 treeUri + docId 批量 query 图片子项；
 * - CBZ/CBR/CB7：优先复用导入期 cache，缺失时从 SAF/单文件 URI 复制；
 * - PDF：M3 暂不支持阅读（页数/封面已在导入期完成）。
 */
class BookSourceFactory(private val context: Context) {

    suspend fun open(book: BookRecord): BookContentSource = withContext(Dispatchers.IO) {
        when (book.format.uppercase()) {
            "FOLDER" -> openFolder(book)
            "CBZ" -> ZipSource(localFile(book, "zip"))
            "CBR" -> RarSource(localFile(book, "rar"))
            "CB7" -> SevenZSource(localFile(book, "7z"))
            "PDF" -> PdfSource(localFile(book, "pdf"))
            else -> throw UnsupportedOperationException("暂不支持 ${book.format}")
        }
    }

    private fun openFolder(book: BookRecord): FolderSource {
        val treeUri = book.treeUri ?: error("missing tree uri")
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(
            Uri.parse(treeUri),
            book.docId,
        )
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
        )
        val images = ArrayList<ImageDoc>()
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                if (!SourceRules.isImageName(name)) continue
                images += ImageDoc(
                    docId = cursor.getString(idIndex),
                    name = name,
                    size = if (cursor.isNull(sizeIndex)) 0 else cursor.getLong(sizeIndex),
                )
            }
        }
        if (images.isEmpty()) error("文件夹里没有图片")
        return FolderSource(context.contentResolver, Uri.parse(treeUri), images)
    }

    private fun localFile(book: BookRecord, extension: String): File {
        val dir = File(context.cacheDir, "import").apply { mkdirs() }
        val target = File(dir, "${book.id}.$extension")
        if (target.exists() && target.length() > 0) return target

        val uri = when {
            book.docId.startsWith("content://") -> Uri.parse(book.docId)
            book.treeUri != null -> DocumentsContract.buildDocumentUriUsingTree(
                Uri.parse(book.treeUri),
                book.docId,
            )

            else -> error("missing document uri")
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("无法打开文件")

        if (target.length() <= 0) error("文件为空")
        return target
    }
}
