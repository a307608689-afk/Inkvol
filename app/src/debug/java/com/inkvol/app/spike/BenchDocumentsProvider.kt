package com.inkvol.app.spike

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract.Document

/**
 * 仅 debug 使用的「文档树」ContentProvider。
 *
 * 注意：这里刻意**不继承 DocumentsProvider** —— 系统要求 DocumentsProvider 必须由
 * `MANAGE_DOCUMENTS`（signature|privileged）保护，普通 App 注册会直接抛 SecurityException。
 * 而 DocumentsContract 只是构造 `content://authority/tree/...` URI，查询走 ContentResolver，
 * 所以普通 ContentProvider 即可完成基准测试。
 *
 * 暴露 1000 个虚拟图片文件，用于量化「一次批量 query」与
 * 「DocumentFile 逐项访问」的开销差异。
 */
class BenchDocumentsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.inkvol.app.spike.docs"
        const val ROOT_ID = "root"
        const val FILE_COUNT = 1000

        private val DEFAULT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
        )
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val segments = uri.pathSegments
        val documentIndex = segments.indexOf("document")
        require(documentIndex >= 0 && segments.size > documentIndex + 1) { "bad uri: $uri" }
        val documentId = segments[documentIndex + 1]
        val isChildren = segments.lastOrNull() == "children"
        val cols = projection ?: DEFAULT_PROJECTION

        val cursor = MatrixCursor(cols)
        when {
            isChildren -> {
                repeat(FILE_COUNT) { index -> addFileRow(cursor, cols, index) }
            }

            documentId == ROOT_ID -> addRootRow(cursor, cols)
            else -> {
                val index = parseIndex(documentId)
                require(index >= 0) { "unknown document: $documentId" }
                addFileRow(cursor, cols, index)
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        val segments = uri.pathSegments
        val documentIndex = segments.indexOf("document")
        val documentId = segments.getOrNull(documentIndex + 1) ?: return null
        return if (documentId == ROOT_ID) Document.MIME_TYPE_DIR else "image/jpeg"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun parseIndex(documentId: String): Int =
        if (documentId.startsWith("file_")) {
            documentId.removePrefix("file_").toIntOrNull() ?: -1
        } else {
            -1
        }

    private fun addRootRow(cursor: MatrixCursor, cols: Array<out String>) {
        val row = arrayOfNulls<Any>(cols.size)
        cols.forEachIndexed { i, col ->
            row[i] = when (col) {
                Document.COLUMN_DOCUMENT_ID -> ROOT_ID
                Document.COLUMN_DISPLAY_NAME -> "Inkvol Bench"
                Document.COLUMN_MIME_TYPE -> Document.MIME_TYPE_DIR
                Document.COLUMN_FLAGS -> 0
                else -> null
            }
        }
        cursor.addRow(row)
    }

    private fun addFileRow(cursor: MatrixCursor, cols: Array<out String>, index: Int) {
        val row = arrayOfNulls<Any>(cols.size)
        cols.forEachIndexed { i, col ->
            row[i] = when (col) {
                Document.COLUMN_DOCUMENT_ID -> "file_$index"
                Document.COLUMN_DISPLAY_NAME -> "page_%04d.jpg".format(index)
                Document.COLUMN_SIZE -> 1024L * (index + 1)
                Document.COLUMN_LAST_MODIFIED -> 1_700_000_000_000L + index
                Document.COLUMN_MIME_TYPE -> "image/jpeg"
                Document.COLUMN_FLAGS -> 0
                else -> null
            }
        }
        cursor.addRow(row)
    }
}
