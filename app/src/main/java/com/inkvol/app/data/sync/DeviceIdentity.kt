package com.inkvol.app.data.sync

import android.os.Build
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.data.settings.SettingsRepository
import java.util.UUID

/**
 * 设备身份：deviceId 首次启用时生成 UUID 并持久化（跨重启稳定，云端文件名即它的 json 名）；
 * deviceName 默认厂商+型号，用户可在设置页改名（空串 = 用默认）。
 */
object DeviceIdentity {

    /** 取现有 deviceId，无则生成并持久化。 */
    suspend fun deviceId(settings: SettingsRepository): String {
        val existing = settings.stringOf(settings.snapshotPreferences(), SettingsKeys.SYNC_DEVICE_ID, "")
        if (existing.isNotBlank()) return existing
        val created = UUID.randomUUID().toString()
        settings.putString(SettingsKeys.SYNC_DEVICE_ID, created)
        return created
    }

    suspend fun deviceName(settings: SettingsRepository): String {
        val stored = settings.stringOf(
            settings.snapshotPreferences(),
            SettingsKeys.SYNC_DEVICE_NAME,
            "",
        ).trim()
        return stored.ifBlank { defaultDeviceName() }
    }

    fun defaultDeviceName(): String = listOfNotNull(
        Build.MANUFACTURER?.trim()?.takeIf { it.isNotEmpty() },
        Build.MODEL?.trim()?.takeIf { it.isNotEmpty() },
    ).joinToString(" ").ifBlank { "Android 设备" }
}
