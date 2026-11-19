package com.inkvol.app.data.import

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.inkvol.app.data.source.BookContentSource
import com.inkvol.app.data.source.PdfSource
import com.inkvol.app.data.source.RarSource
import com.inkvol.app.data.source.SevenZSource
import com.inkvol.app.data.source.SourceRules
import com.inkvol.app.data.source.ZipSource
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 归档探测：格式识别 → 页数 → 首页封面 → ComicInfo。
 * 扫描器与单文件导入共用，避免两条路径的封面/页数逻辑漂移。
 */
class ArchiveProber(private val context: Context) {

    data class Result(
        val pageCount: Int,
        val format: String,
        val coverPath: String?,
        val meta: ComicInfoParser.Meta?,
    )

    fun probe(file: File, fingerprint: String): Result? {
        val head = ByteArray(8)
        val read = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(0)
        val kind = SourceRules.detect(head.copyOf(read.coerceAtLeast(0))) ?: return null
        return runCatching {
            when (kind) {
                "ZIP" -> openSourceProbe(ZipSource(file), "CBZ", fingerprint).copy(
                    meta = runCatching {
                        java.util.zip.ZipFile(file).use { ComicInfoParser.parseZip(it) }
                    }.getOrNull(),
                )

                "RAR" -> openSourceProbe(RarSource(file), "CBR", fingerprint)
                "SEVEN_Z" -> openSourceProbe(SevenZSource(file), "CB7", fingerprint)
                "PDF" -> {
                    val pages = PdfSource(file).use { it.pageCount }
                    Result(pages, "PDF", if (pages > 0) savePdfCover(fingerprint, file) else null, null)
                }

                else -> null
            }
        }.getOrElse {
            Log.w("InkvolProbe", "probe failed: ${file.name}", it)
            null
        }
    }

    private fun openSourceProbe(
        source: BookContentSource,
        format: String,
        fingerprint: String,
    ): Result = source.use {
        val count = it.pageCount
        Result(
            pageCount = count,
            format = format,
            coverPath = if (count > 0) saveCover(fingerprint) { _ -> it.openPage(0) } else null,
            meta = null,
        )
    }

    fun saveCover(fingerprint: String, open: (Int) -> InputStream): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(0).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = open(0).use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val out = File(dir, "$fingerprint.jpg")
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        bitmap.recycle()
        out.absolutePath
    }.getOrElse {
        Log.w("InkvolProbe", "cover failed $fingerprint", it)
        null
    }

    private fun savePdfCover(fingerprint: String, file: File): String? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount <= 0) {
                    null
                } else {
                    renderer.openPage(0).use { page ->
                        val width = 512
                        val height = (width * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val dir = File(context.filesDir, "covers").apply { mkdirs() }
                        val out = File(dir, "$fingerprint.jpg")
                        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
                        bitmap.recycle()
                        out.absolutePath
                    }
                }
            }
        }
    }.getOrElse {
        Log.w("InkvolProbe", "pdf cover failed $fingerprint", it)
        null
    }
}
