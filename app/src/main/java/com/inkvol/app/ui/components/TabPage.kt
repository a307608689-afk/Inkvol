package com.inkvol.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inkvol.app.ui.components.glass.GlassHeaderTitleBlock
import com.inkvol.app.ui.components.glass.LocalGlassChrome
import com.inkvol.app.ui.components.glass.rememberGlassHeaderState

/**
 * 顶层 Tab 的统一骨架：状态栏让位 + 标题块 + 内容区。
 *
 * 左右让位从 [LocalGlassChrome] 读 railEndInset（平板侧栏），
 * 页面不再各自硬编码；底部让位交由各页自己的列表 contentPadding 处理。
 *
 * 两种形态：
 * - 默认（`collapsible = false`）：标题在流内、永远占位，内容从标题下方开始滚；
 * - 可折叠（传 `scrollState` + `collapsible = true`）：标题块改成叠层，跟随滚动
 *   「跟手」上移（机制见 [rememberGlassHeaderState]，与书架同一套），内容让位同步收缩。
 *   注意：折叠模式必须把页面的 LazyListState 提上来传进来，否则手势连不上。
 */
@Composable
fun TabPage(
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null,
    /** 折叠模式的列表状态；null 时走静态标题结构（与旧行为完全一致） */
    scrollState: LazyListState? = null,
    collapsible: Boolean = false,
    /** 头部内容高度（不含状态栏）：12(上留白) + 62(标题两行) + 4(下留白) + 10(呼吸) = 88dp */
    headerContentHeight: Dp = 88.dp,
    /** 变化时把头部滑回展开（如设置二级页切换：传分组 id） */
    headerResetKey: Any? = null,
    /** 本页是否接管系统状态栏隐显（设置这类工具页传 false = 恒显） */
    statusBarAutoHide: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val chrome = LocalGlassChrome.current

    if (!collapsible || scrollState == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 状态栏 + 刘海一起让位；仅用 statusBarsPadding 时，
                // 系统栏从隐藏恢复的瞬间可能拿到 0 inset，标题会被状态栏盖住。
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(start = 20.dp, end = 20.dp + chrome.railEndInset)
                .padding(top = 18.dp),
        ) {
            TabPageTitleRow(title = title, subtitle = subtitle, action = action)
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                content = content,
            )
        }
        return
    }

    val headerState = rememberGlassHeaderState(
        listState = scrollState,
        resetKey = headerResetKey,
        headerContentHeight = headerContentHeight,
        useLibraryHolder = false,
        statusBarAutoHide = statusBarAutoHide,
    )

    // 回顶总线（底栏重复点当前 Tab / 侧栏回顶）：滚到顶后把头部滑回展开
    val scrollToTop = LocalScrollToTop.current
    LaunchedEffect(scrollState) {
        scrollToTop.events.collect {
            if (scrollState.layoutInfo.totalItemsCount > 0) {
                scrollState.animateScrollToItem(0)
            }
            headerState.expand()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 折叠的手势消费：必须挂在列表的祖先上
            .nestedScroll(headerState.scrollConnection),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp + chrome.railEndInset)
                // 让位随折叠量收缩：折叠期间列表不滚，内容与头部 1:1 同行
                .padding(top = headerState.gridTopPadding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                content = content,
            )
        }
        GlassHeaderTitleBlock(
            state = headerState,
            title = title,
            subtitle = subtitle,
            // 顶层「页」保留大标题；折叠行为与书架一致
            titleStyle = MaterialTheme.typography.headlineLarge,
            subtitleStyle = MaterialTheme.typography.bodyMedium,
            // 标题离状态栏 12dp、离首项 4dp，剩下的 10dp 呼吸由 headerContentHeight 提供
            topPad = 12.dp,
            bottomPad = 4.dp,
            endInset = chrome.railEndInset,
            modifier = Modifier.align(Alignment.TopCenter),
            actions = { action?.invoke() },
        )
    }
}

/** 静态标题行（非折叠形态，与骨架原有排版逐项一致） */
@Composable
private fun TabPageTitleRow(
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action?.invoke()
    }
}
