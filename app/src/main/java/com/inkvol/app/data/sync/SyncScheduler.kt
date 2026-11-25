package com.inkvol.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 同步触发器（M4.5）：进度保存防抖 2min / 退后台补同步 / 启动补同步；
 * 仅 WiFi 门禁 + 30min 失败冷却 + 请求预算软闸。手动「立即同步」绕过 WiFi 门禁。
 */
class SyncScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
    private val sync: SyncRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null

    /** 进度保存后调用：置 dirty + 防抖自动同步。 */
    fun onProgressSaved() {
        scope.launch {
            if (!autoEnabled()) return@launch
            sync.markDirty()
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(DEBOUNCE_MS)
                runAutoSync()
            }
        }
    }

    /** 退后台：有 dirty 立即补同步。 */
    fun onBackground() {
        scope.launch {
            if (!autoEnabled()) return@launch
            if (sync.hasDirtyFlag()) runAutoSync()
        }
    }

    /** 应用启动：上次进程死亡遗留的 dirty 补同步（一次性）。 */
    fun onAppReady() {
        scope.launch {
            if (!autoEnabled()) return@launch
            if (sync.hasDirtyFlag()) runAutoSync()
        }
    }

    private suspend fun autoEnabled(): Boolean {
        val prefs = settings.snapshotPreferences()
        // 未同意当前隐私政策前不发起任何联网同步（与首启同意门同源）
        if (settings.stringOf(prefs, SettingsKeys.PRIVACY_ACCEPTED_VERSION, "") !=
            SettingsKeys.PRIVACY_POLICY_VERSION
        ) {
            return false
        }
        if (!settings.booleanOf(prefs, SettingsKeys.SYNC_ENABLED, false)) return false
        if (!settings.booleanOf(prefs, SettingsKeys.SYNC_AUTO, true)) return false
        return true
    }

    private suspend fun runAutoSync() {
        val prefs = settings.snapshotPreferences()
        val cooldownUntil = settings.longOf(prefs, SettingsKeys.SYNC_COOLDOWN_UNTIL, 0)
        // 失败冷却期：静默跳过（dirty 保留，冷却结束后的下一次触发补上）；状态行要能看出来
        if (System.currentTimeMillis() < cooldownUntil) {
            sync.noteGated(waitingWifi = false, cooldownUntil = cooldownUntil)
            return
        }
        // 请求预算软闸：剩余不足以完成最小同步（PROPFIND×2 + PUT）时跳过
        if (budgetRemaining() < MIN_SYNC_REQUESTS) return
        // 仅 WiFi 门禁：不满足保留 dirty 等下次（状态行提示「等待 WiFi」，不静默）
        if (settings.booleanOf(prefs, SettingsKeys.SYNC_WIFI_ONLY, true) && !isWifi()) {
            sync.noteGated(waitingWifi = true)
            return
        }
        sync.noteGated(waitingWifi = false)
        val entry = sync.syncNow("自动")
        consumeBudget(entry.requests)
        if (entry.ok) sync.clearDirtyFlag()
    }

    // ---------- 请求预算（30min 滑窗，坚果云 600/30min 留足余量） ----------

    private suspend fun budgetRemaining(): Int {
        val prefs = settings.snapshotPreferences()
        val windowStart = settings.longOf(prefs, SettingsKeys.SYNC_BUDGET_WINDOW, 0)
        var used = settings.intOf(prefs, SettingsKeys.SYNC_BUDGET_USED, 0)
        if (System.currentTimeMillis() - windowStart > BUDGET_WINDOW_MS) used = 0
        return BUDGET_LIMIT - used
    }

    private suspend fun consumeBudget(count: Int) {
        val prefs = settings.snapshotPreferences()
        val windowStart = settings.longOf(prefs, SettingsKeys.SYNC_BUDGET_WINDOW, 0)
        var used = settings.intOf(prefs, SettingsKeys.SYNC_BUDGET_USED, 0)
        if (System.currentTimeMillis() - windowStart > BUDGET_WINDOW_MS) {
            settings.putLong(SettingsKeys.SYNC_BUDGET_WINDOW, System.currentTimeMillis())
            used = 0
        }
        settings.putInt(SettingsKeys.SYNC_BUDGET_USED, used + count)
    }

    // ---------- WiFi 门禁 ----------

    private fun isWifi(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && (
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            )
    }.getOrDefault(false)

    private companion object {
        const val DEBOUNCE_MS = 2L * 60 * 1000
        const val BUDGET_WINDOW_MS = 30L * 60 * 1000
        const val BUDGET_LIMIT = 200
        const val MIN_SYNC_REQUESTS = 3
    }
}
