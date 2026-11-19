package com.inkvol.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 「回到顶部」事件总线：
 * 宿主侧栏/再次点击当前 Tab 触发，当前顶层页订阅并把自己的列表滚到顶部。
 * 页面没有可滚动列表时自然无消费者，不需要额外判断。
 */
class ScrollToTopBus {

    private val _events = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun request() {
        _events.tryEmit(Unit)
    }
}

val LocalScrollToTop = staticCompositionLocalOf { ScrollToTopBus() }

/**
 * 书架网格滚动位置（进程级单例）：
 * 一镜到底返回书架时显式恢复，绕开 NavHost rememberSaveable 的「一次性落位」时序。
 * 进程内进阅读器再返回必然命中；进程被杀后重开自然回顶，不做持久化。
 */
object LibraryGridScroll {
    var index: Int = 0
    var offset: Int = 0

    /**
     * 离开书架时头部是否处于折叠态。
     * 折叠现在由「前置滚动消费」实现，列表滚动位置可能仍是 (0,0)，
     * 光看 index/offset 判断不出折叠态，故单独记一位。
     */
    var collapsed: Boolean = false

    /**
     * 离开书架时是否横屏；null = 本会话尚未记录过（老进程状态）。
     * 阅读期间旋转过再返回时，offset 是旧方向的像素语义、跨方向直接用会落错
     * 位置，恢复逻辑据此走跨方向路径（index 保留、offset 归零 + 锚点校准）。
     */
    var landscape: Boolean? = null

    /**
     * 最近打开的书 id（宿主 onOpenBook 写入）。跨方向恢复时把视口定位到它，
     * 保证返回动画的飞回目标卡在视口内、能回报新方向下的正确坐标。
     */
    var anchorBookId: String? = null
}

/**
 * 书架的「状态栏是否可见」进程级记录：
 * 玻璃头在书架活跃期间持续写入真实显隐；返回书架时 closeReaderOpen 按它恢复系统栏、
 * 方向显隐状态以它做初值——保证「进书时隐藏→返回仍隐藏」「进书时显示→返回仍显示」，
 * 不再用「是否在顶部」的启发式猜测。
 */
object LibraryStatusBar {
    var visible: Boolean = true
}
