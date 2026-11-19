package com.inkvol.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 阅读器激活标记：
 * 书架沉浸顶栏在阅读器期间必须让出系统栏控制权，
 * 否则它的 LaunchedEffect(showActions) 会在阅读器隐藏状态栏后再次 show，
 * 造成真机上状态栏“退不掉”。
 */
val LocalReaderActive = staticCompositionLocalOf { false }
