package com.inkvol.app.spike

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inkvol.app.data.import.SingleFileImporter
import com.inkvol.app.data.local.InkvolDatabase
import com.inkvol.app.data.model.BookRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M2 书夹数据层：多对多、未分类、删除只解除关系、排序。
 */
@RunWith(AndroidJUnit4::class)
class M2CategoryTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun book(id: String, title: String) = BookRecord(
        id = id,
        type = "COMIC",
        title = title,
        series = null,
        volume = null,
        writer = null,
        tags = null,
        pageCount = 10,
        format = "CBZ",
        fileSize = 1024,
        coverPath = null,
        addedAt = System.currentTimeMillis(),
        docId = "doc_$id",
        displayPath = "$title.cbz",
    )

    @Test
    fun categoriesAndUnclassified() {
        val db = InkvolDatabase(context)
        db.clearAll()

        val libraryId = db.findOrCreateLibrary("test://m2", "M2")
        val folderId = db.findOrCreateFolder(libraryId, "test://m2", "M2")
        db.upsertBook(book("b1", "Alpha"), folderId)
        db.upsertBook(book("b2", "Beta"), folderId)
        db.upsertBook(book("b3", "Gamma"), folderId)

        val waitList = db.insertCategory("待看", "#C8462F")
        val best = db.insertCategory("神作", "#4CAF7D")

        db.addBooksToCategory(listOf("b1", "b2"), waitList)
        db.addBooksToCategory(listOf("b2"), best)
        db.addBooksToCategory(listOf("b2"), best) // 重复加入应幂等

        val categories = db.queryCategories()
        assertEquals(2, categories.size)
        assertEquals(2, categories.first { it.id == waitList }.bookCount)
        assertEquals(1, categories.first { it.id == best }.bookCount)
        assertEquals(2, db.queryBooksInCategory(waitList).size)
        assertEquals(1, db.queryUnclassifiedBooks().size)

        db.moveCategory(best, -1)
        assertEquals("神作", db.queryCategories().first().name)

        db.deleteCategory(waitList)
        assertEquals(3, db.queryBooks().size)
        // b2 仍在「神作」，所以未分类只剩 b1/b3
        assertEquals(2, db.queryUnclassifiedBooks().size)
        assertEquals(1, db.queryCategories().size)
        assertTrue(db.queryCategoryBookIds()[best]?.contains("b2") == true)
    }

    @Test
    fun singleFileImport() {
        val db = InkvolDatabase(context)
        db.clearAll()

        val treeUri = DocumentsContract.buildTreeDocumentUri(
            TestLibraryProvider.AUTHORITY,
            TestLibraryProvider.ROOT_ID,
        )
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            TestLibraryProvider.CBZ_ID,
        )

        val book = runBlocking { SingleFileImporter(context, db).import(documentUri) }

        assertEquals("CBZ", book.format)
        assertEquals(3, book.pageCount)
        assertNotNull(book.coverPath)
        assertEquals("单文件导入", db.queryBooks().first().series)
        assertTrue(book.docId.startsWith("content://"))
    }
}
