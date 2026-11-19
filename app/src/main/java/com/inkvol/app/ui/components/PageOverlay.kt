package com.inkvol.app.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * 页面级覆盖层控制器：
 * 打开菜单/编辑器/选择面板时通知宿主隐藏底栏与侧栏，避免 chrome 压在遮罩之上。
 */
class PageOverlayController internal constructor(
    private val onActive: (Boolean) -> Unit,
) {
    fun setActive(active: Boolean) = onActive(active)
}

val LocalPageOverlayController = compositionLocalOf { PageOverlayController {} }
