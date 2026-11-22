package com.inkvol.app.data.scan

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.inkvol.app.data.import.LibraryScanner
import com.inkvol.app.data.import.SingleFileImporter
import com.inkvol.app.data.local.InkvolDatabase

/**
 * 后台增量扫描：遍历所有已授权文件夹，复用前台扫描器。
 *
 * 不占前台服务：单次任务受 WorkManager 10 分钟限制；超大库后续可按文件夹拆分批次。
 */
class ScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = InkvolDatabase(context)
        val scanner = LibraryScanner(context, db)
        val folders = db.queryFolders()
        if (folders.isEmpty()) {
            Log.i(TAG, "no folders to scan")
            return Result.success()
        }

        var scanned = 0
        var failed = 0
        folders.forEach { folder ->
            // 单文件导入的虚拟文件夹（inkvol://single-file）不是 SAF 目录，扫描必失败，跳过
            if (folder.treeUri == SingleFileImporter.SINGLE_LIBRARY_URI) return@forEach
            // 快照可能在排队后被用户「移除」：文件夹行已删则跳过，避免给死文件夹复活位置
            if (!db.hasFolder(folder.id)) return@forEach
            val result = runCatching {
                scanner.scan(
                    Uri.parse(folder.treeUri),
                    folder.libraryId,
                    folder.id,
                ) { _, _ -> }
            }
            result.onSuccess { scanned += it.added }
                .onFailure {
                    failed++
                    Log.w(TAG, "background scan failed: ${folder.treeUri}", it)
                }
        }
        Log.i(TAG, "background scan done added=$scanned failed=$failed")
        // M6.3-D 收口：后台扫描完成通知 UI 层刷新
        // （书架的书籍列表是手动 refresh 的快照，不主动刷新会让新书/筛选统计滞后）
        (applicationContext as? com.inkvol.app.InkvolApplication)?.container
            ?.libraryRepository?.refresh()
        return Result.success()
    }

    companion object {
        private const val TAG = "InkvolScanWorker"
    }
}
