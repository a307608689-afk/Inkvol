package com.inkvol.app.ui.reader

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** M4-C：把当前漫画页导出到 cache/share，并交给系统分享面板。 */
object ReaderShare {

    suspend fun sharePage(
        context: Context,
        bitmap: Bitmap?,
        bookTitle: String,
        pageNumber: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        if (bitmap == null || bitmap.isRecycled) return@withContext false
        val shareDir = File(context.cacheDir, "reader_share").apply { mkdirs() }
        // 只保留一页临时分享图，避免 cache 膨胀。
        shareDir.listFiles()?.forEach { old -> old.delete() }
        val file = File(
            shareDir,
            "inkvol-p${pageNumber.coerceAtLeast(1)}.png",
        )
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, output)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val text = "$bookTitle · P$pageNumber · Inkvol"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri("Inkvol page", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "分享当前页").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    }
}
