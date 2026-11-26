package com.inkvol.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
     * 离开书架时的视图档 × 分类档（一级网格的「组合键」）。
     * 恢复目标是「事实」而不是推导：之前用 initialViewMode（进书架时的 prefs 快照）+
     * LibraryUiState.typeFilter 反推，只要两者与离开时的实时值不同源（prefs 首帧为
     * null / 书架内切过视图），恢复就作用在一个没组合的网格上 → 回顶且 settled 卡住。
     */
    var viewMode: String? = null
    var typeFilter: String? = null

    /**
     * 离开时的折叠量（0..1，extent / expandedTop）。
     * 只记「是否完全折叠」的布尔会让部分折叠返回时被强制展开——顶部让位瞬间长出来
     * （「整页下跳 + 顶部露白」）。按比例记录可天然吸收状态栏/头部高度变化。
     */
    var extentFraction: Float? = null

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
 * 书架「列表样式」一级页（书夹/系列/平铺三视图联动）的滚动位置（进程级单例），
 * 与 [LibraryGridScroll] 同范式：离开书架（列表样式活跃时）落值，进书架首帧直接
 * 构造 LazyListState 恢复。与网格状态互不覆盖——网格/列表各自记住自己的位置，
 * 样式切换原位恢复。
 * [active] = 离开时列表样式是否活跃；[viewMode]/[typeFilter] = 离开时的视图 × 分类
 * 组合（列表态 map 的键，9 组合各持一份）。
 * 列表行定高：跨方向恢复 index 直接有效、offset 归零即可；平铺列表的书行开书，
 * 跨方向锚点校准由恢复 effect 按 anchorBookId 重算（与网格同思路）。
 */
object LibraryShelfListScroll {
    var active: Boolean = false
    var viewMode: String? = null
    var typeFilter: String? = null
    var index: Int = 0
    var offset: Int = 0
    var landscape: Boolean? = null
}

/**
 * 书架二级页（书夹 / 系列 / 智能书夹）的滚动位置（进程级单例）。
 * 与 LibraryGridScroll 同范式：进阅读器时落值、返回首帧直接构造 LazyGridState 恢复。
 * 同一时刻只会有一个二级页存活，所以单槽位即可；[key] 用于确认「恢复的还是同一个
 * 二级页」，显式返回一级时清空（保留「退出再进自然回顶」的既有语义）。
 */
object LibraryDetailScroll {
    /** 目标键：user:<id> / system:<kind> / series:<name> */
    var key: String? = null
    var index: Int = 0
    var offset: Int = 0
    /** 离开时二级页显示的是列表样式（library_shelf_view=list）而非网格。 */
    var isList: Boolean = false
}

/**
 * 书架的「状态栏是否可见」进程级记录：
 * 玻璃头在书架活跃期间持续写入真实显隐；返回书架时 closeReaderOpen 按它恢复系统栏、
 * 方向显隐状态以它做初值——保证「进书时隐藏→返回仍隐藏」「进书时显示→返回仍显示」，
 * 不再用「是否在顶部」的启发式猜测。
 */
object LibraryStatusBar {
    var visible: Boolean = true

    /**
     * 状态栏高度（px，书架活跃期间持续写入）。
     * 一镜到底返回时页面是新组合的，首帧 insets 可能还没派发
     * （`statusBarsIgnoringVisibility` 报 0），会让标题/让位先塌一个状态栏高度再回来
     * （「标题跳到状态栏位置然后再下来」）。玻璃头在 insets 有效时把这个值记住，
     * 首帧为 0 时用它兜底。
     */
    var statusBarTopPx: Float = 0f
}

/**
 * 书架「下拉重扫」的进度共享（进程级单例，LibraryStatusBar 同款模式）：
 * LibraryScreen 的 PullToRefreshBox 写入拉距，chrome 读取做箭头形变/旋转——
 * 手机 = 玻璃头加号钮「+ ↔ 重扫箭头」形变；平板 = 侧栏刷新钮跟手转。
 * 旋转/透明度一律在 graphicsLayer 内读取，不进重组。
 */
object LibraryPullRefresh {

    /** 0..1 下拉蓄力进度（PullToRefreshState.distanceFraction 透传）。 */
    var pullFraction by mutableFloatStateOf(0f)

    /** 重扫进行中（宿主从 repository scan.running 同步；含后台扫描）。 */
    var rescanActive by mutableStateOf(false)
}
