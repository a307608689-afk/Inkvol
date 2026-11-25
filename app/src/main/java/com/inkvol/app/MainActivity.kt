package com.inkvol.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.ui.InkvolApp
import com.inkvol.app.ui.legal.PrivacyConsentScreen
import com.inkvol.app.ui.lock.AppLockGate
import com.inkvol.app.ui.theme.InkvolTheme
import com.inkvol.app.ui.theme.ThemeAccent
import com.inkvol.app.ui.theme.themeAccentOf

class MainActivity : ComponentActivity() {

    private var pendingReaderBookId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingReaderBookId = intent?.getStringExtra(EXTRA_OPEN_READER_BOOK_ID)
        val container = (application as InkvolApplication).container
        setContent {
            val prefs by container.settingsRepository.preferences.collectAsStateWithLifecycle(
                // 内存缓存快照做首帧值：冷启动/重建时避免主题模式先落默认再翻转。
                initialValue = container.settingsRepository.cachedPreferences(),
            )
            val themeMode = prefs?.let {
                container.settingsRepository.stringOf(it, SettingsKeys.APP_THEME, SettingsKeys.THEME_SYSTEM)
            } ?: SettingsKeys.THEME_SYSTEM
            val dynamicColor = prefs?.let {
                container.settingsRepository.booleanOf(it, SettingsKeys.DYNAMIC_COLOR, false)
            } ?: false
            // 自定义主题色：走同一份快照做首帧值，冷启动不会先朱红再跳色。
            val accent = themeAccentOf(
                prefs?.let {
                    container.settingsRepository.stringOf(
                        it,
                        SettingsKeys.THEME_ACCENT,
                        ThemeAccent.VERMILION.id,
                    )
                },
            )

            // 状态栏图标外观是窗口级属性，enableEdgeToEdge 只在 onCreate 按「系统」
            // 夜间模式判定一次；App 内手动切深浅不重建 Activity，需在此同步。
            val darkTheme = when (themeMode) {
                SettingsKeys.THEME_LIGHT -> false
                SettingsKeys.THEME_DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                )
            }

            InkvolTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
                accent = accent,
            ) {
                val prefsNow = prefs
                val acceptedVersion = prefsNow?.let {
                    container.settingsRepository.stringOf(
                        it,
                        SettingsKeys.PRIVACY_ACCEPTED_VERSION,
                        "",
                    )
                }
                when {
                    // 首帧等待 DataStore 快照：避免给已同意的老用户闪一下同意页
                    prefsNow == null -> Unit
                    acceptedVersion != SettingsKeys.PRIVACY_POLICY_VERSION -> PrivacyConsentScreen(
                        settingsRepository = container.settingsRepository,
                        onDecline = { finishAffinity() },
                    )
                    else -> AppLockGate(
                        enabled = container.settingsRepository.booleanOf(
                            prefsNow,
                            SettingsKeys.APP_LOCK_ENABLED,
                            false,
                        ),
                        onExit = { finishAffinity() },
                    ) {
                        InkvolApp(
                            settingsRepository = container.settingsRepository,
                            libraryRepository = container.libraryRepository,
                            syncRepository = container.syncRepository,
                            pendingReaderBookId = pendingReaderBookId,
                            onPendingReaderConsumed = { pendingReaderBookId = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val bookId = intent.getStringExtra(EXTRA_OPEN_READER_BOOK_ID)
        if (!bookId.isNullOrBlank()) pendingReaderBookId = bookId
    }

    /** M4.5：退后台补同步（有持久 dirty 且自动同步开启时）。 */
    override fun onStop() {
        super.onStop()
        (application as InkvolApplication).container.syncScheduler.onBackground()
    }

    companion object {
        const val ACTION_OPEN_READER = "com.inkvol.app.action.OPEN_READER"
        const val EXTRA_OPEN_READER_BOOK_ID = "extra_open_reader_book_id"
    }
}
