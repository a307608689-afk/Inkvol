package com.inkvol.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import java.io.IOException

private val Context.inkvolSettings: DataStore<Preferences> by preferencesDataStore(name = "inkvol_settings")

/**
 * 设置存取的最小实现。
 *
 * M0 只保留读写原语；声明式设置项（SettingsSpec DSL）在 M4 设置页批次接入，
 * 届时直接复用 SSMH 的 SettingsSpecs 结构。
 */
class SettingsRepository(private val context: Context) {

    /**
     * 最近一次读取成功的偏好快照（内存缓存）。
     * 返回书架等场景用它做 collectAsStateWithLifecycle 的 initialValue：DataStore 首次
     * 发射是异步的（几十 ms），期间 prefs=null 会让 viewMode 退回默认值（书架视图），
     * 实际值（如平铺）到达后网格结构整体翻转、锚点被夹回 (0,0)——返回动画下露出的
     * 书架瞬态跳动（「闪一下」）。用缓存做首帧值即可零翻转。
     */
    @Volatile
    private var lastKnown: Preferences? = null

    fun cachedPreferences(): Preferences? = lastKnown

    val preferences: Flow<Preferences> = context.inkvolSettings.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .onEach { lastKnown = it }

    suspend fun snapshotPreferences(): Preferences = context.inkvolSettings.data.first()

    suspend fun putPreference(key: String, type: String, value: String) {
        context.inkvolSettings.edit { prefs ->
            when (type) {
                "boolean" -> prefs[booleanPreferencesKey(key)] = value.toBooleanStrictOrNull() ?: false
                "int" -> prefs[intPreferencesKey(key)] = value.toIntOrNull() ?: 0
                "long" -> prefs[longPreferencesKey(key)] = value.toLongOrNull() ?: 0L
                "float" -> prefs[floatPreferencesKey(key)] = value.toFloatOrNull() ?: 0f
                "double" -> prefs[doublePreferencesKey(key)] = value.toDoubleOrNull() ?: 0.0
                else -> prefs[stringPreferencesKey(key)] = value
            }
        }
    }

    suspend fun putBoolean(key: String, value: Boolean) {
        context.inkvolSettings.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun putString(key: String, value: String) {
        context.inkvolSettings.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun putInt(key: String, value: Int) {
        context.inkvolSettings.edit { it[intPreferencesKey(key)] = value }
    }

    fun booleanOf(prefs: Preferences, key: String, default: Boolean): Boolean =
        prefs[booleanPreferencesKey(key)] ?: default

    fun stringOf(prefs: Preferences, key: String, default: String): String =
        prefs[stringPreferencesKey(key)] ?: default

    fun intOf(prefs: Preferences, key: String, default: Int): Int =
        prefs[intPreferencesKey(key)] ?: default

    fun readerOpenDurationMs(speed: String?): Int = when (speed) {
        SettingsKeys.OPEN_SPEED_FASTER -> 240
        SettingsKeys.OPEN_SPEED_FAST -> 260
        SettingsKeys.OPEN_SPEED_SLOW -> 320
        SettingsKeys.OPEN_SPEED_SLOWER -> 360
        else -> 280
    }
}
