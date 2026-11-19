package com.inkvol.app.data.scan

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 自动扫描调度：
 * - 每次冷启动尝试排队一次增量扫描（KEEP：已有任务不重复）；
 * - M6.3：加 15s initialDelay——冷启动时立刻扫描会与打开书籍的
 *   EPUB 解析/binder 查询竞争，实测首次打开小说被卡 21.5s（ANR）；
 * - 电量低时延后，失败指数退避。
 */
object ScanScheduler {

    private const val UNIQUE_NAME = "inkvol-auto-scan"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInitialDelay(15, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.MINUTES,
            )
            .addTag("library-scan")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
