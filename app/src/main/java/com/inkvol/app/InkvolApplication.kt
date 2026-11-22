package com.inkvol.app

import android.app.Application
import android.os.StrictMode
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.data.scan.ScanScheduler
import com.inkvol.app.data.settings.SettingsRepository
import com.inkvol.app.data.sync.SyncRepository
import com.inkvol.app.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 手写依赖容器：本地依赖 + M4.5 WebDAV 同步（可选模块，同步未启用零网络行为）。 */
class AppContainer(application: Application) {
    val settingsRepository = SettingsRepository(application)
    val libraryRepository = LibraryRepository(application, settingsRepository)
    val syncRepository = SyncRepository(application, settingsRepository, libraryRepository)
    val syncScheduler = SyncScheduler(application, settingsRepository, syncRepository)

    init {
        // 同步挂钩：进度保存 → 防抖自动同步；书籍级联硬删（多选删除/书库移除/回收站清除）→ 墓碑 + 传播。
        libraryRepository.syncOnProgressSaved = { syncScheduler.onProgressSaved() }
        libraryRepository.syncOnBooksPurged = { ids -> syncRepository.onBooksPurged(ids) }
        syncRepository.refreshState()
        syncScheduler.onAppReady()
    }
}

class InkvolApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        if (BuildConfig.DEBUG) {
            // debug 包检测主线程磁盘 IO：冷启动与翻页路径用日志暴露问题
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build(),
            )
        }
        container = AppContainer(this)
        ScanScheduler.schedule(this)
        // Coil 首次创建在主线程会做类加载与资源探测，提前在后台预热，
        // 让书架上第一张封面直接拿到现成实例。
        CoroutineScope(Dispatchers.Default).launch {
            SingletonImageLoader.get(this@InkvolApplication)
        }
    }

    /**
     * 崩溃兜底：完整堆栈写入本地文件，随后仍走系统崩溃流程。
     * 自用/上架版本都没有崩溃上报平台，本地日志是唯一排查手段；最多保留 5 份。
     */
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = java.io.File(filesDir, "crash").apply { mkdirs() }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                java.io.File(dir, "crash-$stamp.txt").writeText(
                    buildString {
                        appendLine("time: $stamp")
                        appendLine("thread: ${thread.name}")
                        appendLine("version: ${BuildConfig.VERSION_NAME}")
                        appendLine(
                            "device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}" +
                                " (API ${android.os.Build.VERSION.SDK_INT})",
                        )
                        appendLine()
                        appendLine(android.util.Log.getStackTraceString(throwable))
                    },
                )
                dir.listFiles()?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun newImageLoader(context: PlatformContext) =
        coil3.ImageLoader.Builder(context).build()
}
