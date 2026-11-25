package com.inkvol.app.ui.novel

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 小说插图保存（零权限）：
 * - API 29+ 走 MediaStore 一键存相册（Pictures/Inkvol），不需要任何存储权限；
 * - API 26–28 与 SVG 回落 SAF（CreateDocument），由宿主 Activity 结果回调发起；
 * - 一律写**原文件字节**（不重编码，保留 GIF/SVG/EXIF）。
 */
sealed interface NovelImageSaveResult {
    data class Saved(val displayName: String) : NovelImageSaveResult

    /** 需要宿主发起 SAF CreateDocument（低版本或相册不适用的格式）。 */
    data class NeedSaf(val displayName: String, val mime: String) : NovelImageSaveResult

    object Failed : NovelImageSaveResult
}

object NovelImageSaver {

    const val GALLERY_SUB_DIR = "Inkvol"

    private val MIME_BY_EXT = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "svg" to "image/svg+xml",
    )

    fun mimeOf(path: String): String =
        MIME_BY_EXT[extOf(path)] ?: "image/jpeg"

    /** 相册一键保存只对位图格式成立：SVG 相册读不出来，回落 SAF 更符合预期。 */
    private fun galleryFriendly(mime: String): Boolean = mime != "image/svg+xml"

    fun extOf(path: String): String =
        path.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 } ?: "jpg"

    /** `书名_第N章_图K.ext`；书名清洗 FAT/SAF 非法字符。 */
    fun defaultFileName(
        bookTitle: String,
        chapterIndex: Int,
        indexInChapter: Int,
        relPath: String,
    ): String {
        val safeTitle = bookTitle
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            .trim('_', '.')
            .ifEmpty { "Inkvol" }
            .take(48)
        return "${safeTitle}_第${chapterIndex + 1}章_图${indexInChapter + 1}.${extOf(relPath)}"
    }

    /**
     * 保存入口：能走相册就直接写相册，否则返回 [NovelImageSaveResult.NeedSaf] 让宿主拉起选择器。
     */
    suspend fun save(
        context: Context,
        src: File,
        displayName: String,
        mime: String,
    ): NovelImageSaveResult = withContext(Dispatchers.IO) {
        if (!src.exists() || src.length() <= 0L) return@withContext NovelImageSaveResult.Failed
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !galleryFriendly(mime)) {
            return@withContext NovelImageSaveResult.NeedSaf(displayName, mime)
        }
        saveToGallery(context, src, displayName, mime)
    }

    private fun saveToGallery(
        context: Context,
        src: File,
        displayName: String,
        mime: String,
    ): NovelImageSaveResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + GALLERY_SUB_DIR,
            )
            // 写入期间对外不可见，写完置 0；失败必须删掉半成品，否则相册留 0 字节条目。
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return NovelImageSaveResult.Failed

        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: error("openOutputStream returned null")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            NovelImageSaveResult.Saved(displayName)
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            NovelImageSaveResult.Failed
        }
    }

    /** SAF 目标写入：调用方已拿到用户选择的 uri。 */
    suspend fun writeToUri(context: Context, src: File, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                } != null
            }.getOrDefault(false)
        }
}
