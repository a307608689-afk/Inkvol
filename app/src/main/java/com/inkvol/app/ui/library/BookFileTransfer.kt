package com.inkvol.app.ui.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.inkvol.app.data.model.BookRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * 书籍文件的「导出到…」与「分享」。
 *
 * 零存储权限：
 * - 读源文件沿用既有 docId 三分法（content:// / file:// / 树内 child）；
 * - 导出写目标走 SAF（CreateDocument 单本 / OpenDocumentTree 批量）；
 * - 分享先复制到 cacheDir/share_file 换成友好文件名，再交给 FileProvider。
 *
 * 文件夹型漫画（format=FOLDER）没有单文件，[isTransferable] 返回 false，入口置灰。
 */
object BookFileTransfer {

    const val SHARE_DIR = "share_file"

    /** 一次系统分享的上限：避免 Binder 事务过大，也避免接收方卡死。 */
    const val MAX_SHARE_COUNT = 20

    fun isTransferable(book: BookRecord): Boolean = book.format.uppercase() != "FOLDER"

    /** 扩展名：优先取 docId 末段，其次按 format 兜底。 */
    fun extOf(book: BookRecord): String {
        val fromDoc = book.docId
            .substringAfterLast('/')
            .substringAfterLast(':')
            .substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.isNotEmpty() && it.length <= 5 && it.all { ch -> ch.isLetterOrDigit() } }
        if (fromDoc != null) return fromDoc
        return when (book.format.uppercase()) {
            "CBZ" -> "cbz"
            "CBR" -> "cbr"
            "CB7" -> "7z"
            "PDF" -> "pdf"
            "EPUB" -> "epub"
            "MOBI" -> "mobi"
            "TXT" -> "txt"
            else -> "bin"
        }
    }

    fun mimeOf(ext: String): String = when (ext.lowercase()) {
        "epub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        "zip", "cbz" -> "application/zip"
        "rar", "cbr" -> "application/vnd.rar"
        "7z", "cb7" -> "application/x-7z-compressed"
        "txt" -> "text/plain"
        "mobi" -> "application/x-mobipocket-ebook"
        else -> "application/octet-stream"
    }

    /** 导出/分享用的文件名：书名去非法字符 + 扩展名。 */
    fun fileNameOf(book: BookRecord): String {
        val ext = extOf(book)
        val base = sanitize(book.title).ifBlank { book.id.take(8) }
        return if (base.endsWith(".$ext", ignoreCase = true)) base else "$base.$ext"
    }

    fun sanitize(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_").trim().take(60)

    /** 按 docId 形态打开源文件流（与删除时的三分法一致）。 */
    fun openSource(context: Context, book: BookRecord): InputStream? {
        val docId = book.docId
        if (docId.isBlank()) return null
        return when {
            docId.startsWith("content://") ->
                context.contentResolver.openInputStream(Uri.parse(docId))

            docId.startsWith("file://") ->
                Uri.parse(docId).path
                    ?.let { path -> File(path).takeIf { it.exists() && it.isFile } }
                    ?.inputStream()

            book.treeUri.isNullOrBlank() -> null

            else -> {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(
                    Uri.parse(book.treeUri),
                    docId,
                )
                context.contentResolver.openInputStream(docUri)
            }
        }
    }

    /** 单本导出到 SAF 目标（CreateDocument 或批量 createDocument 返回的 uri）。 */
    suspend fun exportTo(context: Context, book: BookRecord, target: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = openSource(context, book) ?: error("无法读取源文件")
                source.use { input ->
                    val output = context.contentResolver.openOutputStream(target)
                        ?: error("无法写入目标位置")
                    output.use { dest ->
                        input.copyTo(dest)
                        Unit
                    }
                }
            }
        }

    /**
     * 系统分享：复制到 cacheDir/share_file（文件名友好），FileProvider 授权后拉起分享面板。
     * 每次都先清空该目录，最多保留一批临时副本。
     */
    suspend fun share(context: Context, books: List<BookRecord>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(books.isNotEmpty()) { "没有可分享的书籍" }
                require(books.size <= MAX_SHARE_COUNT) { "一次最多分享 $MAX_SHARE_COUNT 本" }
                val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val uris = ArrayList<Uri>(books.size)
                books.forEach { book ->
                    val file = File(dir, uniqueName(dir, fileNameOf(book)))
                    val source = openSource(context, book) ?: error("无法读取《${book.title}》")
                    source.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                    uris += FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                }
                val intent = if (uris.size == 1) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = mimeOf(extOf(books.first()))
                        putExtra(Intent.EXTRA_STREAM, uris.first())
                        clipData = ClipData.newRawUri("Inkvol book", uris.first())
                    }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        clipData = ClipData.newRawUri("Inkvol books", uris.first()).also { clip ->
                            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                        }
                    }
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(intent, "分享书籍"))
            }
        }

    private fun uniqueName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 2
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext"
            if (!File(dir, candidate).exists()) return candidate
            index++
        }
    }
}
