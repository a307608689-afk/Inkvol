package com.inkvol.app.spike

import android.provider.DocumentsContract
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inkvol.app.data.import.LibraryScanner
import com.inkvol.app.data.local.InkvolDatabase
import com.inkvol.app.util.Fingerprint
import com.inkvol.app.util.NaturalOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M1 端到端：SAF 目录树 → 分类 → 指纹 → 页数探测 → 封面 → SQLite。
 *
 * 测试 provider 提供：
 * 测试库/测试系列/第1卷（4 页）、第2卷（3 页）、单本.cbz（3 页）。
 * 期望：3 本书，页数 4/3/3，封面全部落地。
 */
@RunWith(AndroidJUnit4::class)
class M1ScanTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun scanRealLibraryTree() {
        val db = InkvolDatabase(context)
        db.clearAll()

        val treeUri = DocumentsContract.buildTreeDocumentUri(
            TestLibraryProvider.AUTHORITY,
            TestLibraryProvider.ROOT_ID,
        )
        val libraryId = db.findOrCreateLibrary(treeUri.toString(), "测试库")
        val folderId = db.findOrCreateFolder(libraryId, treeUri.toString(), "测试库")

        val scanner = LibraryScanner(context, db)
        val result = runBlocking {
            scanner.scan(treeUri, libraryId, folderId) { _, _ -> }
        }
        val books = db.queryBooks()

        Log.i(
            "INKVOL_SPIKE",
            "M1 scan added=${result.added} skipped=${result.skipped} failed=${result.failed} " +
                "books=" + books.joinToString { "${it.title}:${it.pageCount}:${it.format}:${it.coverPath != null}" },
        )

        assertEquals(5, result.added)
        assertEquals(5, books.size)
        assertEquals(4, books.first { it.title == "第1卷" }.pageCount)
        assertEquals(3, books.first { it.title == "第2卷" }.pageCount)
        assertEquals(3, books.first { it.title == "单本" }.pageCount)
        assertEquals(2, books.first { it.title == "附加" }.pageCount)
        assertEquals(2, books.first { it.title == "扫描件" }.pageCount)
        assertTrue(books.all { it.coverPath != null })
    }

    @Test
    fun naturalOrderAndFingerprintStability() {
        assertTrue(NaturalOrder.compare("page_2.jpg", "page_10.jpg") < 0)
        assertTrue(NaturalOrder.compare("第2话_1.png", "第10话_1.png") < 0)

        val a = listOf("p2.jpg" to 100L, "p1.jpg" to 200L)
        val b = listOf("p1.jpg" to 200L, "p2.jpg" to 100L)
        assertEquals(Fingerprint.ofFolder(a), Fingerprint.ofFolder(b))
        assertTrue(Fingerprint.ofFolder(a) != Fingerprint.ofFolder(listOf("p1.jpg" to 201L)))
    }
}
