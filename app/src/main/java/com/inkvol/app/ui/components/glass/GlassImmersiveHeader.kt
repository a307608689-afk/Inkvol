package com.inkvol.app.ui.components.glass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.Window
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kyant.backdrop.Backdrop
import com.inkvol.app.ui.components.LibraryGridScroll
import com.inkvol.app.ui.components.LibraryStatusBar
import com.inkvol.app.ui.components.LocalReaderActive
import com.inkvol.app.ui.components.rememberScrollDirectionVisibility
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** 一枚分类/榜单玻璃胶囊的数据 */
data class GlassHeaderChip(
    val key: String,
    val label: String,
    val selected: Boolean,
    val loading: Boolean,
    val onClick: () -> Unit,
    /** 可选长按动作（默认 null = 不响应长按），书架页分类胶囊长按开管理面板 */
    val onLongClick: (() -> Unit)? = null,
)

/**
 * 头部内容区的固定高度（不含状态栏）：标题块 + 分类行 + 呼吸留白。
 * 折叠 = 这一整块 + 状态栏被「吃掉」，与既有让位口径（状态栏 + 118dp）一致。
 */
private val HEADER_CONTENT_HEIGHT = 118.dp

/** 分类胶囊行的展开态纵向位置（状态栏之下 60dp） */
private val CHIPS_TOP_OFFSET = 60.dp

/**
 * 沉浸顶栏的共享状态。
 *
 * 折叠不再是「二态布尔 + 补间动画」：折叠量 [extentPx] 由列表的前置滚动消费
 * （[scrollConnection]）逐帧推进，头部（标题块 + 分类行）作为刚性整体按同一位移
 * 上移，网格让位同步收缩 —— 三者同位移，观感等同于「页面跟着手指滑上去」：
 * 手指停住画面就停住，反向立即反向，不存在"越阈值后自己演完"的补间。
 */
@Stable
class GlassHeaderState internal constructor(
    /** 头部内部让位用的状态栏高度（一镜到底期间可被冻结） */
    val statusBarTop: Dp,
    /** 展开态顶部让位总高 = statusBarTop + [HEADER_CONTENT_HEIGHT] */
    val expandedTop: Dp,
    private val density: Density,
    private val extentState: MutableState<Float>,
    /** 消费式折叠的手势连接：必须挂在滚动容器的祖先上 */
    val scrollConnection: NestedScrollConnection,
    private val expandImpl: () -> Unit,
) {
    /**
     * 已折叠高度（px），0 = 完全展开。
     * 读取处夹到 [0, expandedTop]：头部内容高度是动态的（二级页会多算一行标题行，
     * 进出二级页时 H 变化 ±一行高），夹紧可保证任何时序下头部都不会多滑 / 让位变负。
     */
    val extentPx: Float
        get() = extentState.value.coerceIn(0f, with(density) { expandedTop.toPx() })

    /** 头部组整体上移量（px，负数 = 向上）；只在 graphicsLayer 里读，不参与重组 */
    val headerOffsetPx: Float get() = -extentPx

    /** 0 展开 / 1 完全折叠 */
    val progress: Float
        get() {
            val total = with(density) { expandedTop.toPx() }
            return if (total <= 0f) 0f else (extentPx / total).coerceIn(0f, 1f)
        }

    /** 网格顶部让位 = 展开值 - 已折叠量（折完归 0，内容顶到屏幕最上） */
    val gridTopPadding: Dp
        get() = with(density) {
            (expandedTop.toPx() - extentPx).coerceAtLeast(0f).toDp()
        }

    /** 完全折叠（头部整块离屏） */
    val collapsed: Boolean
        get() = extentPx >= with(density) { expandedTop.toPx() } - 0.5f

    /** 把头部平滑滑回展开态：回顶 / 视图切换等离散时机用 */
    fun expand() = expandImpl()
}

/**
 * 沉浸顶栏状态（网格容器版，书架）。
 *
 * 折叠量由「前置滚动消费」驱动（见 [GlassHeaderState.scrollConnection]），头部刚体上移、
 * 内容让位同步收缩，分类行与标题同进同出；状态栏按滚动方向隐显（可按页关闭）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberGlassHeaderState(
    gridState: LazyGridState,
    /** 变化时把头部滑回「展开」：视图切换 / 二级页进出等离散时机 */
    resetKey: Any? = null,
    /** 一镜到底返回期间冻结的状态栏让位（null = 实时值） */
    frozenStatusBarTop: Dp? = null,
    /** 一镜到底 Closing 期间挂起状态栏控制与手势消费 */
    closingHold: Boolean = false,
    /**
     * 页面尚未完成恢复（一镜到底返回后书架网格/二级页还在落位）时挂起状态栏控制。
     * Closing 只覆盖反向动画那 ~280ms，恢复（逐帧重申 + 数据稳定）会延续到 Idle 之后；
     * 这期间 atTop/方向显隐读到的是瞬态位置，先 show 后 hide 就是用户看到的「状态栏跳一下」。
     */
    statusBarHold: Boolean = false,
    /** 头部内容高度（不含状态栏）：书架 = 标题块 + 分类行 38 + 呼吸 = 118dp */
    headerContentHeight: Dp = HEADER_CONTENT_HEIGHT,
    /** 是否用进程级 holder 做跨路由恢复（阅读器往返）：非书架页传 false */
    useLibraryHolder: Boolean = true,
    /** 本页是否接管系统状态栏隐显（false = 恒显，适合设置这类工具页） */
    statusBarAutoHide: Boolean = true,
    /** 条目总数（用于「在顶部」判定守卫：未组合/空数据的网格不算触顶） */
    totalItemCount: () -> Int = { gridState.layoutInfo.totalItemsCount },
): GlassHeaderState = rememberGlassHeaderStateCore(
    stateKey = gridState,
    positionProvider = {
        gridState.firstVisibleItemIndex * 10000 + gridState.firstVisibleItemScrollOffset
    },
    canScrollForward = { gridState.canScrollForward },
    canScrollBackward = { gridState.canScrollBackward },
    isScrollInProgress = { gridState.isScrollInProgress },
    resetKey = resetKey,
    frozenStatusBarTop = frozenStatusBarTop,
    closingHold = closingHold,
    statusBarHold = statusBarHold,
    headerContentHeight = headerContentHeight,
    useLibraryHolder = useLibraryHolder,
    statusBarAutoHide = statusBarAutoHide,
    totalItemCount = totalItemCount,
)

/**
 * 沉浸顶栏状态（列表容器版）：历史 / 设置等 LazyColumn 页面用，语义与网格版一致。
 * 默认不接进程级 holder（进页恒展开），长度不够一屏时自动不折（见 core 内短表守卫）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberGlassHeaderState(
    listState: LazyListState,
    resetKey: Any? = null,
    frozenStatusBarTop: Dp? = null,
    closingHold: Boolean = false,
    statusBarHold: Boolean = false,
    /** 只有标题块：12(上留白) + 62(标题两行) + 4(下留白) + 10(呼吸) = 88dp */
    headerContentHeight: Dp = 88.dp,
    useLibraryHolder: Boolean = false,
    statusBarAutoHide: Boolean = true,
    totalItemCount: () -> Int = { listState.layoutInfo.totalItemsCount },
): GlassHeaderState = rememberGlassHeaderStateCore(
    stateKey = listState,
    positionProvider = {
        listState.firstVisibleItemIndex * 10000 + listState.firstVisibleItemScrollOffset
    },
    canScrollForward = { listState.canScrollForward },
    canScrollBackward = { listState.canScrollBackward },
    isScrollInProgress = { listState.isScrollInProgress },
    resetKey = resetKey,
    frozenStatusBarTop = frozenStatusBarTop,
    closingHold = closingHold,
    statusBarHold = statusBarHold,
    headerContentHeight = headerContentHeight,
    useLibraryHolder = useLibraryHolder,
    statusBarAutoHide = statusBarAutoHide,
    totalItemCount = totalItemCount,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun rememberGlassHeaderStateCore(
    stateKey: Any,
    positionProvider: () -> Int,
    canScrollForward: () -> Boolean,
    canScrollBackward: () -> Boolean,
    isScrollInProgress: () -> Boolean,
    resetKey: Any?,
    frozenStatusBarTop: Dp?,
    closingHold: Boolean,
    statusBarHold: Boolean,
    headerContentHeight: Dp,
    useLibraryHolder: Boolean,
    statusBarAutoHide: Boolean,
    totalItemCount: () -> Int,
): GlassHeaderState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // ---- 几何：H = 状态栏 + 头部内容高 ----
    // 基准取「忽略可见性」的状态栏高度：状态栏随滚动隐显时 H 保持恒定，
    // 折叠位移不会因为 inset 变化而抖动（状态栏显示态下与实时值相等）。
    val liveStableTop = WindowInsets.statusBarsIgnoringVisibility
        .asPaddingValues()
        .calculateTopPadding()
    // 记忆化兜底：一镜到底返回时页面是新组合的，首帧 insets 可能尚未派发
    // （statusBarsIgnoringVisibility 报 0）→ 标题/让位先塌掉一个状态栏高度、下一帧
    // 又回来，观感是「标题跳到状态栏位置然后再下来」。用书架活跃期持续写入的进程级
    // 记忆值补这一次（设备真无状态栏时记忆值也是 0，无副作用）。
    val rememberedTopPx = if (useLibraryHolder) LibraryStatusBar.statusBarTopPx else 0f
    val stableStatusBarTop = if (liveStableTop.value > 0f || rememberedTopPx <= 0f) {
        liveStableTop
    } else {
        rememberedTopPx.dp
    }
    LaunchedEffect(liveStableTop.value) {
        if (useLibraryHolder && liveStableTop.value > 0f) {
            LibraryStatusBar.statusBarTopPx = liveStableTop.value
        }
    }
    val statusBarTop = frozenStatusBarTop ?: stableStatusBarTop
    val expandedTop = statusBarTop + headerContentHeight
    val expandedTopPx = with(density) { expandedTop.toPx() }
    val expandedTopPxState = rememberUpdatedState(expandedTopPx)
    val closingHoldState = rememberUpdatedState(closingHold)

    // ---- 折叠量：唯一真源（px，0 = 展开，H = 完全折叠）----
    // 书架：初值读进程级 LibraryGridScroll（进阅读器前保存的位置）：返回书架首帧就落在
    // 正确折叠态、不做动画。inset 首帧可能还是 0，对齐一次真实 H（同帧生效）。
    // 历史/设置等列表页：折叠量走 rememberSaveable，与 LazyListState 同一条 NavHost
    // saveState/restoreState 通道——切 Tab 离开时 onDispose 落盘，回来首帧同步恢复：
    // 折着离开就折着回来（展开离开就展开回来），「列表位置」与「折叠量」永远不失步。
    val collapsedBefore = remember {
        useLibraryHolder && (
            LibraryGridScroll.collapsed ||
                LibraryGridScroll.index > 0 || LibraryGridScroll.offset > 60
            )
    }
    // 折叠量比例通道（书架）：部分折叠也能原样恢复，且天然吸收状态栏/头部高度变化
    // （二级页头部多一行标题行，H 会变）。
    val restoreFraction = remember {
        if (useLibraryHolder) LibraryGridScroll.extentFraction else null
    }
    // 列表页的保存通道：-1 = 无保存态（首次进入）
    val savedExtent = rememberSaveable { mutableStateOf(-1f) }
    val savedFullyCollapsed = rememberSaveable { mutableStateOf(false) }
    val extentState = remember {
        mutableStateOf(
            when {
                restoreFraction != null -> restoreFraction * expandedTopPx
                collapsedBefore -> expandedTopPx
                savedExtent.value >= 0f -> savedExtent.value
                else -> 0f
            }
        )
    }
    // 恢复的对齐：
    //  - 比例通道：本身是比例，除非首帧 H 还未就绪（=0）才需补算一次；
    //  - px 通道（旧布尔/列表页 saveable）：与原 H 绑定，恢复后对齐一次真实 H。
    val alignPending = remember {
        mutableStateOf(
            if (restoreFraction != null) expandedTopPx <= 0f
            else collapsedBefore || savedFullyCollapsed.value
        )
    }
    LaunchedEffect(expandedTopPx) {
        if (expandedTopPx <= 0f) return@LaunchedEffect
        if (alignPending.value) {
            extentState.value = restoreFraction?.let { it * expandedTopPx } ?: expandedTopPx
            alignPending.value = false
        } else if (extentState.value > expandedTopPx) {
            // 头部高度变小（退出二级页：标题行不再计入头部）时夹回上限，
            // 否则头部会多滑一段、让位为负。
            extentState.value = expandedTopPx
        }
    }

    val settleJob = remember { mutableStateOf<Job?>(null) }
    // 最后一次滚动方向：1 = 上滑（折叠中），-1 = 下滑（回看 / 回顶）
    val lastDir = remember { mutableIntStateOf(0) }

    fun cancelSettle() {
        settleJob.value?.cancel()
        settleJob.value = null
    }

    fun animateExtentTo(target: Float) {
        cancelSettle()
        val start = extentState.value
        if (start == target) return
        Log.d("InkvolOpen", "diag extent settle $start -> $target")
        settleJob.value = scope.launch {
            animate(
                initialValue = start,
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = 1f,
                ),
            ) { value, _ -> extentState.value = value }
        }
    }

    // ---- 消费式折叠：头部吃掉前置滚动 ----
    // 嵌套滚动约定：available.y < 0 = 手指上滑（内容往后走），> 0 = 手指下滑（回看）。
    // 上滑：先折头部（列表原地不动，内容靠让位收缩 1:1 上移），折完列表才开始滚；
    // 下滑：只有列表已经回顶才展开头部（中途下滑交给列表，不抢手势）。
    // 因为 delta 被吃掉，列表在折叠期间不滚 —— 内容位移只来自让位，不会出现「双倍速」。
    val collapseConnection = remember(stateKey) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Closing 期间网格位置是恢复前旧值，不接手势
                if (closingHoldState.value) return Offset.Zero
                val dy = available.y
                if (dy == 0f) return Offset.Zero
                val total = expandedTopPxState.value
                if (total <= 0f) return Offset.Zero
                val cur = extentState.value
                lastDir.intValue = if (dy < 0f) 1 else -1
                if (dy < 0f) {
                    // 手指上滑 → 折叠
                    if (cur >= total - 0.5f) return Offset.Zero
                    // 真正一屏装不下的短内容不折叠（两头都滚不动）：否则会塌出一片空白。
                    // 不能只看 canScrollForward——列表停在底部时它同样是 false，但上方还有
                    // 内容可看、折叠不会塌空白；只看它会把「恢复到底部」误判成短内容，
                    // 变成必须回顶才能折叠。
                    if (cur <= 0.5f && !canScrollForward() && !canScrollBackward()) return Offset.Zero
                    cancelSettle()
                    val take = minOf(-dy, total - cur)
                    extentState.value = cur + take
                    return Offset(0f, -take)
                }
                // 手指下滑 → 展开，但列表不在顶部时这段是翻内容，不给头部
                if (cur <= 0.5f) return Offset.Zero
                if (canScrollBackward()) return Offset.Zero
                cancelSettle()
                val take = minOf(dy, cur)
                extentState.value = cur - take
                return Offset(0f, take)
            }
        }
    }

    // 手势 / 惯性结束：已回顶、且最后一下是「下滑」时把头部补完展开。
    // 触发点是「手势结束」而不是「检测到在顶」——拖拽过程中画面全程只由手指驱动，
    // 不会出现「手指没动、画面自己往上蹿」。
    LaunchedEffect(stateKey) {
        snapshotFlow { isScrollInProgress() }
            .distinctUntilChanged()
            .drop(1)
            .collect { inProgress ->
                if (inProgress) return@collect
                if (closingHoldState.value) return@collect
                if (extentState.value <= 0.5f) return@collect
                if (canScrollBackward()) return@collect
                if (lastDir.intValue > 0) return@collect
                animateExtentTo(0f)
            }
    }

    // resetKey（视图模式切换）变化时滑回展开；跳过首组合——首组合的重置就是
    // 返回时强制展开的来源，armed 后只响应真正发生的 resetKey 变化。
    // closingHold 守卫：返回组合后 prefs 从 null 加载，viewMode 从默认值变为实际值
    // 会触发 resetKey 重启——此时 Closing 未结束，不能把折叠态重置成展开。
    var resetArmed by remember { mutableStateOf(false) }
    LaunchedEffect(resetKey) {
        if (resetArmed && !closingHoldState.value) animateExtentTo(0f)
        resetArmed = true
    }

    // ---- 状态栏显隐（保持既有语义：滚动方向 + 触顶）----
    // 必须跟着列表实例重建：书夹目录页与分类页是两个不同的 LazyGridState，
    // 不重建会一直读旧的（停在顶部）→ atTop 恒真 → 状态栏永远强制显示
    val atTop by remember(stateKey) {
        // 空数据 / 未组合（如一镜到底返回二级页时一级网格未参与组合）不算触顶：
        // 否则 showActions 会被 atTop 恒真压成显示，与进书前的隐藏态打架。
        derivedStateOf { positionProvider() < 8 && totalItemCount() > 0 }
    }
    // resetKey 变化时重建方向监听（初始为显示），避免沿用上一处的隐藏状态。
    // 一镜到底返回以 LibraryStatusBar.visible（进书前的真实显隐）做初值：
    // 进书时隐藏 → 返回保持隐藏，不再被初值 true 顶成显示。
    val directionShow = key(resetKey, stateKey) {
        rememberScrollDirectionVisibility(
            positionProvider = {
                // 头部折叠量也计入「内容位置」：折叠 = 内容继续往后走（攒够位移就隐藏
                // 状态栏），展开 = 往回走（手指下滑即唤出）。折叠期间列表 offset 不动，
                // 不把折叠量算进来，方向显隐拿不到任何位移，状态栏会一直亮着。
                positionProvider() + extentState.value.toInt()
            },
            // 书架以进阅读器前的真实显隐做初值；其它页（不接 holder）恒以显示进场
            initialVisible = if (useLibraryHolder) LibraryStatusBar.visible else true,
        )
    }
    // 「触顶必现」只在头部也展开时才成立：折叠时列表 offset 通常仍是 0（滚动被头部
    // 吃掉了），若照旧按 atTop 强制显示，状态栏就不会跟着头部一起隐藏。
    val headerExpanded = extentState.value <= 0.5f
    // 不加「到底强制显示」兜底：快速滑到底再触发分页加载时，
    // canScrollForward 会短暂变 false 再变 true，导致状态栏「回来又消失」（闪烁）。
    // 「卡住恢复不了」已由列表底部 240dp 滚动余量保障（总能滚，下滑即唤回）
    val showActions = directionShow || (atTop && headerExpanded)
    // 书架活跃期间把状态栏真实显隐持续写入进程级 holder：进书瞬间最后写入的值即
    // 「进书前状态」——返回时 closeReaderOpen 按它恢复系统栏、方向显隐以它做初值。
    val readerActiveNow = LocalReaderActive.current
    LaunchedEffect(showActions, readerActiveNow, statusBarHold) {
        // 只有书架会把这个值当「进书前状态」读回去，其它页不要污染它。
        // statusBarHold（恢复中）期间不写：此时 atTop/方向读到的是瞬态位置，
        // 写进去会把「进书前状态」覆盖成错误值（返回后状态栏乱跳的源头之一）。
        if (!readerActiveNow && useLibraryHolder && !statusBarHold) {
            LibraryStatusBar.visible = showActions
        }
    }

    // ---- 状态栏随滚动隐显 ----
    val view = LocalView.current
    val readerActive = LocalReaderActive.current
    DisposableEffect(Unit) {
        onDispose {
            // 折叠态落进进程级 holder：返回书架（含一镜到底）时首帧就按它恢复
            if (useLibraryHolder) {
                LibraryGridScroll.collapsed =
                    extentState.value >= expandedTopPxState.value - 0.5f
                // 比例通道：部分折叠也原样恢复（布尔只能表达「折满/未折满」，
                // 中途离开会被当成展开，返回时顶部让位突然长出来 = 整页下跳）。
                LibraryGridScroll.extentFraction = if (expandedTopPxState.value > 0f) {
                    (extentState.value / expandedTopPxState.value).coerceIn(0f, 1f)
                } else {
                    0f
                }
            } else {
                // 列表页折叠量写进 rememberSaveable 保存通道：本效果挂在页面内容里，
                // onDispose 先于外层 SaveableStateProvider 的保存执行，这里写入的值
                // 就是切 Tab 时被保存的值，下次进入首帧同步恢复（见 extentState 初值）。
                savedExtent.value = extentState.value
                savedFullyCollapsed.value =
                    extentState.value >= expandedTopPxState.value - 0.5f
            }
        }
    }
    // 入场预置：书架（holder 页）入场首帧按进程级 holder 直接落状态栏，不等恢复落位。
    // 此前退场页 onDispose 的迟到 show（NavHost 转场结束才 dispose，晚于本页落位
    // hide）会把「切回书架后的隐藏态」持续顶回显示——退场 show 已移除，改为每个
    // 顶层页入场自己声明状态栏状态：
    // - holder 隐藏 → TRANSIENT hide：最终态必是隐藏，瞬态滚动位置不影响该结论，
    //   无需等 gridSettled，顺带消除「切回到落位之间状态栏亮一下」的短闪；
    // - holder 显示 → BEHAVIOR_DEFAULT show：对称处理，防上一页隐藏态残留。
    // 历史/设置/搜索页入场由各自的显隐 effect 恒 show，书库管理页自管，无需此预置。
    // 阅读器持有控制权 / 一镜到底 Closing 期间不动：closeReaderOpen 已按 holder
    // 恢复系统栏，小说返回的恢复由宿主 pendingNovelBarRestore 在 Idle 接管。
    LaunchedEffect(Unit) {
        if (!useLibraryHolder || readerActive || closingHold) return@LaunchedEffect
        runCatching {
            val controller = WindowCompat.getInsetsController(view.context.findHeaderWindow(), view)
            if (LibraryStatusBar.visible) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
    // 状态栏随滚动隐显（上滑收起沉浸、下滑/触顶唤出），系统栏控制单点在本效果：
    // - 显示态：BEHAVIOR_DEFAULT（非瞬态），show 持久生效、不会几秒后自动缩回；
    // - 隐藏态：TRANSIENT，下滑可临时唤回（几秒后自动缩回，沉浸滚动）；
    //   关键：阅读器设置了 TRANSIENT 且退出不复位，若不在 show 前复位成非瞬态，
    //   返回书架的 show 会被系统当作临时唤出——状态栏闪现几秒后自动缩回。
    // - closingHold=true（一镜到底 Closing）挂起：返回首帧 gridState 是 NavHost
    //   旧快照 (0,0)，atTop 误判 true 会先 show，滚动恢复落位后又 hide——闪现。
    // - statusBarHold=true（页面恢复未完成）继续挂起：Closing 只覆盖反向动画那一段，
    //   网格/二级页的恢复（逐帧重申 + 数据稳定）会延续到 Idle 之后，这期间显隐判定
    //   同样不可信。要求「进书前没隐藏 → 返回不隐藏；进书前隐藏 → 返回还隐藏」，
    //   就必须等恢复落位后再动系统栏。
    LaunchedEffect(showActions, readerActive, closingHold, statusBarHold, statusBarAutoHide) {
        if (readerActive || closingHold || statusBarHold) return@LaunchedEffect
        runCatching {
            val controller = WindowCompat.getInsetsController(view.context.findHeaderWindow(), view)
            if (!statusBarAutoHide) {
                // 本页不接管隐显：无条件显示。否则从「书架隐藏态」切过来会没有状态栏，
                // 且本页不再有 hide 路径，用户只能靠系统手势临时唤回。
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.statusBars())
            } else if (showActions) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    return GlassHeaderState(
        statusBarTop = statusBarTop,
        expandedTop = expandedTop,
        density = density,
        extentState = extentState,
        scrollConnection = collapseConnection,
        expandImpl = { animateExtentTo(0f) },
    )
}

/**
 * 沉浸顶栏的标题块（标题 + 副标题）：自状态栏下开始，随折叠量整体上移。
 * 位移走 graphicsLayer（不参与测量），因此滚动中与内容 1:1 同行，不做高度动画。
 */
@Composable
fun GlassHeaderTitleBlock(
    state: GlassHeaderState,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    /** 右侧附加让位（平板沉浸侧栏宽度），避免动作按钮被玻璃压住 */
    endInset: Dp = 0.dp,
    /** 标题字号：书架用 headlineSmall；历史/设置这类「页」保留 headlineLarge */
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodySmall,
    /** 标题上方留白：书架 6dp（贴状态栏）；只有标题的「页」12dp */
    topPad: Dp = 6.dp,
    /** 标题下方留白：书架 6dp；只有标题的「页」4dp（下方空隙由 H 的呼吸补齐） */
    bottomPad: Dp = 6.dp,
    /** 标题右侧的动作区（如书架的书夹/搜索/视图/排序按钮），随标题一起收起 */
    actions: @Composable RowScope.() -> Unit = {},
) {
    // 完全折叠时整块已离屏，直接不组合（不是靠 alpha 隐藏）
    if (state.collapsed) return
    Row(
        modifier = modifier
            // 位移只在 layer 里做：不触发测量、不做高度动画，与内容同一节奏上滑
            .graphicsLayer { translationY = state.headerOffsetPx }
            // 用 headerState 的状态栏让位（可被返回动画冻结），不再直读实时 safeDrawing，
            // 避免返回期间标题与已冻结的网格让位发生相对位移
            .padding(top = state.statusBarTop)
            .padding(start = 20.dp, end = 20.dp + endInset, top = topPad, bottom = bottomPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = subtitleStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}

/** 顶栏动作区的圆形玻璃按钮（40dp 正圆、浅灰描边、按压缩放） */
@Composable
fun GlassHeaderCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) GlassMotion.PRESS_SCALE else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh,
        ),
        label = "headerBtnScale",
    )
    val scheme = MaterialTheme.colorScheme

    GlassRounded(
        backdrop = backdrop,
        tier = GlassTier.Full,
        radius = 20.dp,
        blurRadius = GlassDimens.StrongBlur,
        edge = GlassEdge.Outline,
        modifier = modifier
            .size(40.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        layerBlock = {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) scheme.primary else scheme.onSurface.copy(alpha = 0.82f),
            modifier = Modifier.align(Alignment.Center).size(20.dp),
        )
    }
}

/**
 * 玻璃下拉面板（页面内覆盖层，不用 Popup——Popup 是独立窗口，玻璃采样不到页面背景）。
 * 含全屏透明关闭层，点面板外任意处收起。
 */
@Composable
fun GlassDropdownPanel(
    expanded: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    /** 退场期间需要让宿主 chrome 等待时传入页面聚合器 */
    presence: GlassOverlayPresence? = null,
    /** 放大原点：默认居中，锚定在触发件上时传对应角 */
    origin: TransformOrigin = TransformOrigin.Center,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassOverlay(
        visible = expanded,
        motion = GlassOverlayMotion.Anchored,
        onDismiss = onDismiss,
        backdrop = backdrop,
        modifier = modifier,
        origin = origin,
        presence = presence,
    ) {
        Column(
            // 宽度自适应最长选项（否则 fillMaxWidth 的选项会把面板撑到大半屏）
            modifier = Modifier
                .padding(vertical = 6.dp)
                .width(IntrinsicSize.Min)
                .widthIn(min = 168.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/** 玻璃下拉面板中的一项：选中项品牌色 + 勾选 */
@Composable
fun GlassDropdownItem(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) scheme.primary else scheme.onSurface,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Outlined.Check,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 沉浸顶栏的分类胶囊悬浮行：与标题块刚性同组、随折叠量整体上移，
 * 平板右边界与内容列对齐（不压侧栏），右端 12dp 圆角收口。
 */
@Composable
fun GlassHeaderChipsRow(
    state: GlassHeaderState,
    chips: List<GlassHeaderChip>,
    backdrop: Backdrop?,
    chrome: GlassChromeInsets,
    modifier: Modifier = Modifier,
    /** 额外下移量（给将来需要在胶囊行上方插悬浮件的页面预留），默认 0 不影响其它页面 */
    topExtra: Dp = 0.dp,
) {
    if (state.collapsed) return
    val railMode = chrome.railEndInset > 0.dp
    Box(
        modifier = modifier
            .fillMaxSize()
            // 与标题块同一位移：整组一起滑走，相对位置恒定（不做独立淡出/上浮）
            .graphicsLayer { translationY = state.headerOffsetPx },
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                // 用 headerState 的状态栏让位（可被返回动画冻结），与标题/网格同源
                .padding(top = state.statusBarTop)
                .padding(
                    top = (CHIPS_TOP_OFFSET + topExtra).coerceAtLeast(0.dp),
                    end = if (railMode) 16.dp + chrome.railEndInset else 0.dp,
                ),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = if (railMode) 12.dp else 28.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(chips, key = { it.key }) { chip ->
                GlassHeaderChipItem(chip = chip, backdrop = backdrop)
            }
        }
    }
}

/** 单枚玻璃胶囊：选中品牌色加粗，切换中转圈，按压缩放；长按动作可选 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassHeaderChipItem(
    chip: GlassHeaderChip,
    backdrop: Backdrop?,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 长按触发先给触觉反馈，再执行调用方动作
    val haptics = LocalHapticFeedback.current
    val longClick: (() -> Unit)? = chip.onLongClick?.let { action ->
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            action()
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) GlassMotion.PRESS_SCALE else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh,
        ),
        label = "chipScale",
    )
    val scheme = MaterialTheme.colorScheme

    GlassRounded(
        backdrop = backdrop,
        tier = if (chip.loading) GlassTier.Lite else GlassTier.Full,
        radius = 18.dp,
        blurRadius = GlassDimens.StrongBlur,
        edge = GlassEdge.Outline,
        modifier = Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = chip.onClick,
            onLongClick = longClick,
        ),
        layerBlock = {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (chip.loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            }
            Text(
                text = chip.label,
                color = if (chip.selected) scheme.primary else scheme.onSurface.copy(alpha = 0.82f),
                fontSize = 13.sp,
                fontWeight = if (chip.selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

/** 从 Compose 的 LocalView 向上找到宿主 Window（控制系统状态栏用） */
private fun Context.findHeaderWindow(): Window = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findHeaderWindow()
    else -> error("无法定位宿主 Window")
}
