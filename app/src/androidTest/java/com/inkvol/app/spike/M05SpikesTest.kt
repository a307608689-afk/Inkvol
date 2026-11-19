package com.inkvol.app.spike

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * M0.5 技术验证：
 * 1) SAF：DocumentsContract 单次批量 query vs DocumentFile 逐项访问（1000 文件）；
 * 2) 归档页解码：同一张大图的 inSampleSize 1/2/4 解码耗时；
 * 3) PDF：PdfRenderer 逐页渲染耗时。
 *
 * 说明：测试 provider 与被测代码同进程，IPC 成本被低估；
 * 本测试关注「调用次数差异」与「解码/渲染的绝对量级」，绝对值仅供基线参考。
 */
@RunWith(AndroidJUnit4::class)
class M05SpikesTest {

    private val tag = "INKVOL_SPIKE"
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun safBatchQueryVsDocumentFile() {
        val resolver = context.contentResolver
        val authority = BenchDocumentsProvider.AUTHORITY
        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, BenchDocumentsProvider.ROOT_ID)
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            BenchDocumentsProvider.ROOT_ID,
        )
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_MIME_TYPE,
        )

        fun batchOnce(): Int {
            var count = 0
            resolver.query(childUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)
                    cursor.getString(1)
                    cursor.getLong(2)
                    cursor.getLong(3)
                    count++
                }
            }
            return count
        }

        fun documentFileOnce(): Int {
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
            val files = dir.listFiles()
            files.forEach { file ->
                file.name
                file.length()
                file.lastModified()
            }
            return files.size
        }

        // 预热
        batchOnce()
        documentFileOnce()

        val batchStart = SystemClock.elapsedRealtime()
        var count = 0
        repeat(3) { count = batchOnce() }
        val batchMs = (SystemClock.elapsedRealtime() - batchStart) / 3.0

        val docStart = SystemClock.elapsedRealtime()
        val docCount = documentFileOnce()
        val docMs = (SystemClock.elapsedRealtime() - docStart).toDouble()

        Log.i(
            tag,
            "SAF files=$count batchAvg=%.2fms documentFile=%.2fms ratio=%.1fx".format(
                batchMs,
                docMs,
                if (batchMs > 0) docMs / batchMs else 0.0,
            ),
        )
        assertTrue(count == BenchDocumentsProvider.FILE_COUNT)
        assertTrue(docCount == BenchDocumentsProvider.FILE_COUNT)
    }

    @Test
    fun bitmapDecodeBenchmark() {
        val width = 3000
        val height = 4000
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        val paint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = Color.DKGRAY
        paint.textSize = 96f
        repeat(24) { line ->
            canvas.drawText("INKVOL decode benchmark line $line", 40f, 160f + line * 140f, paint)
        }

        val file = File(context.cacheDir, "spike_page.jpg")
        FileOutputStream(file).use { out -> source.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        source.recycle()

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        fun decode(sample: Int): Long {
            val start = SystemClock.elapsedRealtime()
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            val cost = SystemClock.elapsedRealtime() - start
            bitmap?.recycle()
            return cost
        }

        decode(1); decode(2); decode(4)
        val full = (0 until 3).sumOf { decode(1) } / 3.0
        val half = (0 until 3).sumOf { decode(2) } / 3.0
        val quarter = (0 until 3).sumOf { decode(4) } / 3.0

        Log.i(
            tag,
            "DECODE source=${bounds.outWidth}x${bounds.outHeight} jpeg=${file.length()}B " +
                "full=%.1fms sample2=%.1fms sample4=%.1fms".format(full, half, quarter),
        )
        assertTrue(full > 0)
    }

    @Test
    fun pdfRendererBenchmark() {
        val file = File(context.cacheDir, "spike.pdf")
        val document = PdfDocument()
        repeat(30) { index ->
            val info = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
            val page = document.startPage(info)
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 24f
            }
            page.canvas.drawText("Inkvol PDF page ${index + 1}", 48f, 96f, paint)
            document.finishPage(page)
        }
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        var renderMs = 0.0
        var pageCount = 0
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                pageCount = renderer.pageCount
                val target = Bitmap.createBitmap(1080, 1500, Bitmap.Config.ARGB_8888)
                val start = SystemClock.elapsedRealtime()
                repeat(5) { index ->
                    renderer.openPage(index).use { page ->
                        page.render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
                renderMs = (SystemClock.elapsedRealtime() - start) / 5.0
                target.recycle()
            }
        }

        Log.i(
            tag,
            "PDF pages=$pageCount size=${file.length()}B render1080x1500Avg=%.1fms".format(renderMs),
        )
        assertTrue(pageCount == 30)
        assertTrue(renderMs > 0)
    }
}
