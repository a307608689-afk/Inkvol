package com.inkvol.app.data.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.inkvol.app.data.import.SingleFileImporter
import com.inkvol.app.data.local.InkvolDatabase
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 书籍文件同步执行器（M4.5）：无状态对账式。
 * 每次 tick：PROPFIND books/ → 云端缺就传、本地缺就下、墓碑/删除队列就删。
 * 传输并发 1、每轮限额，超出的留给下一 tick（对账天然收敛）。
 */
class SyncFiles(
    private val context: Context,
    private val db: InkvolDatabase,
    private val importer: SingleFileImporter,
    private val settings: SettingsRepository,
) {

    data class Outcome(
        val uploaded: Int = 0,
        val downloaded: Int = 0,
        val deletedCloud: Int = 0,
        val failed: Int = 0,
    ) {
        val hasWork: Boolean get() = uploaded + downloaded + deletedCloud > 0
    }

    /** 云端书籍清单项（云端书籍卡展示用）。 */
    data class CloudBookInfo(
        val bookId: String,
        val fileName: String,
        val ext: String,
        val size: Long,
        val existsLocally: Boolean,
    )

    /**
     * 上传资格：非文件夹漫画（format != FOLDER）才可单文件传输；
     * 位置命中范围（全部 / 单文件导入库 / 勾选的文件夹）即可。
     */
    fun eligibleBooks(scope: SyncFileScope): List<EligibleFile> {
        val result = ArrayList<EligibleFile>()
        db.querySyncBookRows().forEach { row ->
            if (row.format == "FOLDER") return@forEach
            val location = db.queryBookLocations(row.id).firstOrNull { loc ->
                scope.all ||
                    (loc.treeUri == SingleFileImporter.SINGLE_LIBRARY_URI && scope.single) ||
                    loc.folderId in scope.folderIds
            } ?: return@forEach
            // docId 末段通常是文件名（SAF document id 或完整 URI），取其扩展名
            val ext = location.docId
                .substringAfterLast('/')
                .substringAfterLast(':')
                .substringAfterLast('.', "bin")
                .lowercase()
            result += EligibleFile(
                bookId = row.id,
                ext = ext,
                size = row.fileSize,
                treeUri = location.treeUri,
                docId = location.docId,
            )
        }
        return result
    }

    data class EligibleFile(
        val bookId: String,
        val ext: String,
        val size: Long,
        val treeUri: String,
        val docId: String,
    )

    suspend fun run(
        client: WebDavClient,
        config: SyncRepository.SyncConfig,
        remoteTitles: Map<String, String>,
        tombstonedBookIds: Set<String>,
        scope: SyncFileScope,
        onRequest: () -> Unit,
        onProgress: (String) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        var uploaded = 0
        var downloaded = 0
        var deletedCloud = 0
        var failed = 0
        val cloudEntries = client.list(SyncRepository.BOOK_DIR)
        onRequest()
        val cloudByName = cloudEntries.filter { !it.isDirectory && it.name.contains('.') }
            .associateBy { it.name.substringBeforeLast('.') }

        // 1) 云端删除队列（书库移除/多选删除联动）
        val pendingDeletes = readPendingDeletes()
        if (pendingDeletes.isNotEmpty()) {
            pendingDeletes.forEach { bookId ->
                cloudByName[bookId]?.let { entry ->
                    runCatching {
                        client.delete("$SyncRepository.BOOK_DIR${entry.name}")
                        onRequest()
                        deletedCloud++
                    }.onFailure { failed++ }
                }
            }
            savePendingDeletes(emptyList())
        }

        // 2) 墓碑书：云端文件也清掉（防其它设备把文件重新下载回来）
        tombstonedBookIds.forEach { bookId ->
            cloudByName[bookId]?.let { entry ->
                runCatching {
                    client.delete("$SyncRepository.BOOK_DIR${entry.name}")
                    onRequest()
                    deletedCloud++
                }.onFailure { failed++ }
            }
        }

        val localBookIds = db.querySyncBookRows().map { it.id }.toSet()

        // 3) 上传：本地有、云端无（fingerprint 命名 + 对账天然去重）
        val eligible = eligibleBooks(scope)
        eligible.forEach { file ->
            if (uploaded >= UPLOAD_CAP_PER_RUN || failed >= FAIL_ABORT) return@forEach
            if (cloudByName.containsKey(file.bookId)) return@forEach
            runCatching {
                openLocationStream(file).use { input ->
                    client.upload("$SyncRepository.BOOK_DIR${file.bookId}.${file.ext}", input.readBytes())
                }
                onRequest()
                uploaded++
                onProgress("已上传 ${file.bookId.take(8)}…")
            }.onFailure { failed++ }
        }

        // 4) 下载：云端有、本地无、非墓碑 → 落 filesDir/library/ 持久化并导入
        cloudByName.forEach { (bookId, entry) ->
            if (downloaded >= DOWNLOAD_CAP_PER_RUN) return@forEach
            if (bookId in localBookIds || bookId in tombstonedBookIds) return@forEach
            runCatching {
                val bytes = client.download("$SyncRepository.BOOK_DIR${entry.name}")
                onRequest()
                val dir = File(context.filesDir, "library").apply { mkdirs() }
                val local = File(dir, entry.name)
                if (!local.exists() || local.length() != bytes.size.toLong().toLong()) {
                    local.writeBytes(bytes)
                }
                val title = remoteTitles[bookId] ?: bookId
                val displayName = if ('.' in title) title else "$title.${entry.extOf()}"
                importer.importLocalFile(local, displayName)
                downloaded++
                onProgress("已导入 $title")
            }.onFailure { failed++ }
        }

        Outcome(uploaded, downloaded, deletedCloud, failed)
    }

    private fun DavEntry.extOf(): String = name.substringAfterLast('.', "bin")

    private fun openLocationStream(file: EligibleFile): java.io.InputStream {
        return if (file.treeUri == SingleFileImporter.SINGLE_LIBRARY_URI) {
            context.contentResolver.openInputStream(Uri.parse(file.docId))
                ?: error("cannot open ${file.docId}")
        } else {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                Uri.parse(file.treeUri),
                file.docId,
            )
            context.contentResolver.openInputStream(docUri) ?: error("cannot open ${file.docId}")
        }
    }

    // ---------- 删除队列与云端清单 ----------

    private suspend fun readPendingDeletes(): List<String> {
        val raw = settings.stringOf(settings.snapshotPreferences(), SettingsKeys.SYNC_DELETE_PENDING, "")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            Json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private suspend fun savePendingDeletes(values: List<String>) {
        settings.putString(
            SettingsKeys.SYNC_DELETE_PENDING,
            Json.encodeToString(ListSerializer(String.serializer()), values),
        )
    }

    /** 云端书籍对账（云端书籍卡展示用）：只读 PROPFIND，不传输。 */
    suspend fun listCloudBooks(client: WebDavClient, onRequest: () -> Unit): List<CloudBookInfo> =
        withContext(Dispatchers.IO) {
            val entries = runCatching { client.list(SyncRepository.BOOK_DIR) }.getOrElse { return@withContext emptyList() }
            onRequest()
            val localIds = db.querySyncBookRows().map { it.id }.toSet()
            entries.filter { !it.isDirectory && it.name.contains('.') }.map { entry ->
                CloudBookInfo(
                    bookId = entry.name.substringBeforeLast('.'),
                    fileName = entry.name,
                    ext = entry.extOf(),
                    size = entry.contentLength,
                    existsLocally = entry.name.substringBeforeLast('.') in localIds,
                )
            }.sortedBy { it.fileName }
        }

    /** 云端书籍卡手动下载单个文件并导入（title 取远端 doc，缺省用 fingerprint）。 */
    suspend fun downloadOne(client: WebDavClient, info: CloudBookInfo, title: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = client.download("$SyncRepository.BOOK_DIR${info.fileName}")
                val dir = File(context.filesDir, "library").apply { mkdirs() }
                val local = File(dir, info.fileName)
                if (!local.exists() || local.length() != bytes.size.toLong()) local.writeBytes(bytes)
                val t = title ?: info.bookId
                val displayName = if ('.' in t) t else "$t.${info.ext}"
                importer.importLocalFile(local, displayName)
                Unit
            }
        }

    private companion object {
        const val UPLOAD_CAP_PER_RUN = 40
        const val DOWNLOAD_CAP_PER_RUN = 10
        const val FAIL_ABORT = 6
    }
}
