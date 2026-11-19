package com.inkvol.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.ui.InkvolApp
import com.inkvol.app.ui.theme.InkvolTheme

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

            InkvolTheme(
                darkTheme = when (themeMode) {
                    SettingsKeys.THEME_LIGHT -> false
                    SettingsKeys.THEME_DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                },
                dynamicColor = dynamicColor,
            ) {
                InkvolApp(
                    settingsRepository = container.settingsRepository,
                    libraryRepository = container.libraryRepository,
                    pendingReaderBookId = pendingReaderBookId,
                    onPendingReaderConsumed = { pendingReaderBookId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val bookId = intent.getStringExtra(EXTRA_OPEN_READER_BOOK_ID)
        if (!bookId.isNullOrBlank()) pendingReaderBookId = bookId
    }

    companion object {
        const val ACTION_OPEN_READER = "com.inkvol.app.action.OPEN_READER"
        const val EXTRA_OPEN_READER_BOOK_ID = "extra_open_reader_book_id"
    }
}
