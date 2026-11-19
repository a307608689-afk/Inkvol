package com.inkvol.app

import android.app.Application
import android.os.StrictMode
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.data.scan.ScanScheduler
import com.inkvol.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 手写依赖容器：M0 只保留设置与书架两个本地依赖，不引入任何网络组件。 */
class AppContainer(application: Application) {
    val settingsRepository = SettingsRepository(application)
    val libraryRepository = LibraryRepository(application)
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
