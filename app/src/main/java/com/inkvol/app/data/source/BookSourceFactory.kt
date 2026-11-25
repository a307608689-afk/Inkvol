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
        val archiveCacheRoot = File(context.cacheDir, "import")
        when (book.format.uppercase()) {
            "FOLDER" -> openFolder(book)
            // 嵌套归档（顶层套子压缩包）自动走 NestedArchiveSource，否则原行为
            "CBZ" -> openComicSource(localFile(book, "zip"), "ZIP", book.id, archiveCacheRoot)
            "CBR" -> openComicSource(localFile(book, "rar"), "RAR", book.id, archiveCacheRoot)
            "CB7" -> openComicSource(localFile(book, "7z"), "SEVEN_Z", book.id, archiveCacheRoot)
            "PDF" -> PdfSource(localFile(book, "pdf"))
            // 漫画型 EPUB：页序列由 OPF/spine 解析（EpubImageIndex），不混入封面 / 装饰图。
            "EPUB" -> EpubImageSource(localFile(book, "epub"))
            // 漫画型 MOBI：页序列由 PDB 资源记录切页（MobiImageIndex），recindex 引用序优先。
            "MOBI" -> MobiImageSource(localFile(book, "mobi"))
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

    /**
     * 取（必要时落地）可随机访问的本地副本。
     * internal 供 LibraryRepository 的类型转换复用，避免「导入缓存命名」两套规则。
     */
    internal fun localFile(book: BookRecord, extension: String): File {
        val dir = File(context.cacheDir, "import").apply { mkdirs() }
        val target = File(dir, "${book.id}.$extension")
        if (target.exists() && target.length() > 0) return target

        val uri = when {
            book.docId.startsWith("content://") -> Uri.parse(book.docId)
            // 同步下载 / 本地文件导入：docId 是 file://（此前落到 else 直接抛错）
            book.docId.startsWith("file://") -> Uri.parse(book.docId)
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
