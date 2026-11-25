package com.inkvol.app.data.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** 一条本地崩溃日志（InkvolApplication 写入 filesDir/crash，最多 5 份）。 */
data class CrashLogFile(
    val file: File,
    val name: String,
    val displayTime: String,
    val sizeBytes: Long,
)

/** 崩溃日志的读取/清空单一真源；导出走 FileProvider + ACTION_SEND。 */
object CrashLogStore {

    const val DIR = "crash"

    fun dir(context: Context): File = File(context.filesDir, DIR)

    fun list(context: Context): List<CrashLogFile> =
        dir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            ?.map { file ->
                CrashLogFile(
                    file = file,
                    name = file.name,
                    displayTime = parseTime(file.name),
                    sizeBytes = file.length(),
                )
            }
            ?: emptyList()

    fun read(context: Context, entry: CrashLogFile): String =
        runCatching { entry.file.readText() }.getOrDefault("读取失败")

    fun deleteAll(context: Context): Int {
        val files = dir(context).listFiles() ?: return 0
        var removed = 0
        files.forEach { if (it.delete()) removed++ }
        return removed
    }

    fun count(context: Context): Int = list(context).size

    fun totalBytes(context: Context): Long = list(context).sumOf { it.sizeBytes }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f)
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
        else -> "$bytes B"
    }

    private fun parseTime(fileName: String): String {
        val raw = fileName.removePrefix("crash-").removeSuffix(".txt")
        val parsed = runCatching {
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).parse(raw)
        }.getOrNull() ?: return raw
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(parsed)
    }
}
