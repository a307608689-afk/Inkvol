package com.inkvol.app.ui.novel

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import com.inkvol.app.data.model.NovelFontRecord
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** M6.1-B 小说字体管理：系统字体 + SAF 导入的 TTF/OTF。 */
object NovelFontRegistry {

    private val json = Json { ignoreUnknownKeys = true }
    private val typefaceCache = android.util.LruCache<String, FontFamily?>(8)

    suspend fun fonts(settingsRepository: SettingsRepository): List<NovelFontRecord> =
        withContext(Dispatchers.IO) {
            val raw = settingsRepository.snapshotPreferences()
                .let { settingsRepository.stringOf(it, SettingsKeys.NOVEL_FONTS, "[]") }
            runCatching { json.decodeFromString<List<NovelFontRecord>>(raw) }.getOrDefault(emptyList())
        }

    suspend fun import(
        context: Context,
        uri: Uri,
        settingsRepository: SettingsRepository,
    ): NovelFontRecord? = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(context, uri) ?: "导入字体.ttf"
            val ext = displayName.substringAfterLast('.', "ttf").lowercase()
                .takeIf { it in setOf("ttf", "otf") } ?: "ttf"
            val dir = File(context.filesDir, "fonts").apply { mkdirs() }
            val id = UUID.randomUUID().toString()
            val target = File(dir, "$id.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: return@withContext null
            val current = fonts(settingsRepository).toMutableList()
            val record = NovelFontRecord(id = id, name = displayName.substringBeforeLast('.'), path = target.absolutePath)
            current += record
            settingsRepository.putString(SettingsKeys.NOVEL_FONTS, json.encodeToString(current))
            record
        }.getOrNull()
    }

    suspend fun delete(
        context: Context,
        record: NovelFontRecord,
        settingsRepository: SettingsRepository,
    ) {
        withContext(Dispatchers.IO) {
            runCatching { File(record.path).delete() }
            val current = fonts(settingsRepository).filterNot { it.id == record.id }
            settingsRepository.putString(SettingsKeys.NOVEL_FONTS, json.encodeToString(current))
        }
    }

    fun fontFamily(record: NovelFontRecord?): FontFamily? {
        val path = record?.path ?: return null
        typefaceCache.get(path)?.let { return it }
        val family = runCatching { FontFamily(Typeface.createFromFile(File(path))) }.getOrNull()
        typefaceCache.put(path, family)
        return family
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }
}
