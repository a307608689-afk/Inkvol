package com.inkvol.app.ui.search

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.model.SearchFilterTemplate
import com.inkvol.app.data.model.SearchState
import com.inkvol.app.data.model.SystemCategory
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.ui.components.ReaderOpenRect
import com.inkvol.app.ui.components.glass.GlassCapsule
import com.inkvol.app.ui.components.glass.GlassDimens
import com.inkvol.app.ui.components.glass.GlassEdge
import com.inkvol.app.ui.components.glass.GlassOverlayPresence
import com.inkvol.app.ui.components.glass.GlassRounded
import com.inkvol.app.ui.components.glass.GlassTier
import com.inkvol.app.ui.components.glass.LocalGlassChrome
import com.inkvol.app.ui.components.glass.rememberGlassOverlayPresence
import com.inkvol.app.ui.library.LibraryTarget
import com.inkvol.app.ui.library.LibraryUiState
import com.inkvol.app.ui.theme.InkvolRadius
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/**
 * 全局搜索页（第 4 个顶层 Tab）。
 *
 * 结构对齐 SSMH SearchScreen：悬浮玻璃搜索框行（滚动隐显）+ 分组流结果
 * （实体命中 chips → 书网格 → 章节 → 正文聚合）+ 模式 C 筛选面板 + 模板卡。
 * 玻璃采集层 = 页面级 pageBackdrop（规范 §2.4-2，同 LibraryScreen）；
 * 滚动内容里的 chips（历史/实体命中）用非玻璃件（采集层内玻璃会自我采样递归）。
 *
 * 状态真源 = repository.search（SearchState）：跨 Tab/进阅读器（overlay）存活。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchScreen(
    repository: LibraryRepository,
    onOpenBook: (BookRecord, ReaderOpenRect?) -> Unit,
    onOpenNovelAt: (BookRecord, Int, Int) -> Unit,
    onOpenCategory: (LibraryTarget) -> Unit,
) {
    val search by repository.search.collectAsStateWithLifecycle()
    val chrome = LocalGlassChrome.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    var input by rememberSaveable { mutableStateOf(search.query) }
    var showFilter by remember { mutableStateOf(false) }
    var saveTemplateCard by remember { mutableStateOf(false) }
    var renameTemplate by remember { mutableStateOf<SearchFilterTemplate?>(null) }
    var templateMenu by remember { mutableStateOf<SearchFilterTemplate?>(null) }
    val presence: GlassOverlayPresence = rememberGlassOverlayPresence()
    val gridState = rememberLazyGridState()
    var booksExpanded by rememberSaveable(search.query) { mutableStateOf(false) }

    // facets 预载（幂等）
    LaunchedEffect(Unit) { repository.ensureSearchFacets() }

    // 输入去抖 250ms 即时搜（正文扫描在 runSearch 内部再静默 400ms）
    LaunchedEffect(input, search.filter) {
        val q = input.trim()
        if (q == search.query) return@LaunchedEffect
        delay(250)
        repository.runSearch(input)
    }

    // 回顶联动：切筛选/新查询（规范 §8-3）
    LaunchedEffect(search.query, search.filter) {
        runCatching { gridState.scrollToItem(0) }
    }

    // 搜索框行滚动隐显（SSMH 同款迟滞）
    var headerVisible by remember { mutableStateOf(true) }
    var lastScrollPos by remember { mutableIntStateOf(0) }
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex * 10000 + gridState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { position ->
                val delta = position - lastScrollPos
                when {
                    delta > 3 -> headerVisible = false
                    delta < -3 -> headerVisible = true
                }
                lastScrollPos = position
            }
    }

    // 系统状态栏随滚动隐显（规范 §3.4 / §12-13）。
    // 退场 show 已移除（GlassImmersiveHeader 同款）：NavHost 转场结束才 dispose，
    // 迟到的 show 会把「切回书架后的隐藏态」持续顶回显示；本页入场由下方
    // headerVisible=true 的 effect 恒 show，离开后交给目标页自己声明。
    val view = LocalView.current
    LaunchedEffect(headerVisible) {
        runCatching {
            val controller = WindowCompat.getInsetsController(view.context.findWindow(), view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (headerVisible) {
                controller.show(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    // 返回语义链：结果态退初始态；初始态由宿主的 Tab BackHandler 回书架
    BackHandler(enabled = search.resultMode) { repository.clearSearch() }

    // 结果态/初始态横向滑动过渡（SSMH 同款）
    val resultMode = search.resultMode
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val panelShift = remember { Animatable(0f) }
    var lastResultMode by remember { mutableStateOf(resultMode) }
    LaunchedEffect(resultMode) {
        if (resultMode != lastResultMode) {
            val enter = if (resultMode) screenWidthPx else -screenWidthPx
            lastResultMode = resultMode
            panelShift.snapTo(enter)
            panelShift.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        }
    }

    // 页面级玻璃采集层（LibraryScreen 同款）
    val pageBackground = MaterialTheme.colorScheme.background
    val pageBackdrop = rememberLayerBackdrop(onDraw = {
        drawRect(pageBackground)
        drawContent()
    })

    // 实体命中派生（书夹名/系列名匹配；系列 chips 带命中书数）
    val q = search.query
    val facets = search.facets
    val categoryHits = remember(q, facets) {
        if (q.isEmpty()) {
            emptyList()
        } else {
            facets?.categories?.filter { it.name.contains(q, ignoreCase = true) }.orEmpty()
        }
    }
    val seriesHits = remember(q, facets) {
        if (q.isEmpty()) {
            emptyList()
        } else {
            facets?.series?.filter { it.contains(q, ignoreCase = true) }.orEmpty()
        }
    }
    val totalBodyHits = remember(search.bodyGroups) { search.bodyGroups.sumOf { it.hits.size } }
    val booksPreview = if (booksExpanded) search.books else search.books.take(8)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop),
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(panelShift.value.roundToInt(), 0) },
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp + chrome.railEndInset,
                    bottom = chrome.contentBottomForPage + GlassDimens.ListBottomExtraScroll,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 顶部占位真实 item（规范 §8-2）：搜索框行 56 + 上下间隙 + 状态栏
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(
                        Modifier.height(
                            64.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                        ),
                    )
                }

                if (!resultMode) {
                    InitialContent(
                        search = search,
                        onUseHistory = { keyword ->
                            input = keyword
                            repository.runSearch(keyword)
                        },
                        onClearHistory = { repository.clearSearchHistory() },
                        onOpenCategory = onOpenCategory,
                    )
                } else {
                    if (categoryHits.isNotEmpty() || seriesHits.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EntityHitSection(
                                categoryHits = categoryHits,
                                seriesHits = seriesHits,
                                seriesCount = { name -> search.books.count { it.series == name } },
                                onOpenCategory = onOpenCategory,
                            )
                        }
                    }

                    if (search.loading && search.books.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "正在搜索…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (search.books.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(
                                title = "书 · ${search.books.size} 本",
                                trailing = {
                                    if (search.books.size > 8) {
                                        TextButton(onClick = { booksExpanded = !booksExpanded }) {
                                            Text(
                                                if (booksExpanded) "收起" else "查看全部 ${search.books.size}",
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        items(booksPreview, key = { it.id }, contentType = { "book" }) { book ->
                            SearchBookCard(
                                book = book,
                                query = q,
                                onClick = { rect -> onOpenBook(book, rect) },
                            )
                        }
                    }

                    if (search.chapterHits.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(title = "章节 · ${search.chapterHits.size}")
                        }
                        items(
                            search.chapterHits,
                            key = { "ch_${it.bookId}_${it.chapterIdx}" },
                            contentType = { "chapter" },
                        ) { row ->
                            ChapterHitRow(
                                row = row,
                                query = q,
                                onClick = {
                                    val book = search.books.firstOrNull { it.id == row.bookId }
                                    if (book != null) {
                                        // 章节命中定位到章首（charPos=0）；正文命中走 charPos=命中偏移
                                        onOpenNovelAt(book, row.chapterIdx, 0)
                                    }
                                },
                            )
                        }
                    }

                    if (search.bodyGroups.isNotEmpty() || search.bodyTotal > 0) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(
                                title = "正文 · $totalBodyHits 处",
                                trailing = {
                                    if (!search.bodyDone) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "扫描中 ${search.bodyScanned}/${search.bodyTotal}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            TextButton(onClick = { repository.stopBodyScan() }) {
                                                Text("停止")
                                            }
                                        }
                                    } else if (search.bodyGlobalLimited) {
                                        Text(
                                            text = "已达上限",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                        if (!search.bodyDone) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "正在扫描《${search.bodyCurrentTitle}》…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        }
                        items(search.bodyGroups, key = { "body_${it.book.id}" }, contentType = { "body" }) { group ->
                            BodyGroupRow(
                                group = group,
                                query = q,
                                bookLimited = group.book.id in search.bodyBookLimitedIds,
                                onOpenHit = { hit -> onOpenNovelAt(hit.book, hit.chapterIdx, hit.charPos) },
                            )
                        }
                    }

                    val nothing = search.books.isEmpty() &&
                        search.chapterHits.isEmpty() &&
                        search.bodyGroups.isEmpty() &&
                        !search.loading
                    if (nothing && (search.bodyDone || search.bodyTotal == 0)) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val filteredEmpty = !search.filter.isEmpty()
                            Column(
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = if (filteredEmpty) {
                                        "当前筛选下没有结果，试试清除筛选"
                                    } else {
                                        "没有找到相关结果"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (filteredEmpty) {
                                    TextButton(onClick = { repository.setSearchFilter(com.inkvol.app.data.model.SearchFilter()) }) {
                                        Text("清除筛选")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 悬浮玻璃搜索框行（滚动隐显；采集层兄弟节点）
        AnimatedVisibility(
            visible = headerVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            SearchBarRow(
                input = input,
                onInputChange = { input = it },
                onSearch = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    repository.runSearch(input)
                },
                onClear = {
                    input = ""
                    repository.clearSearch()
                },
                onOpenFilter = { showFilter = true },
                activeFilters = search.filter.activeCount(),
                backdrop = pageBackdrop,
                // 用户拍板：手机/平板描边统一 Outline（覆盖 SSMH 规范 §2.3 的「手机 Highlight」档）
                edge = GlassEdge.Outline,
                modifier = Modifier
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp + chrome.railEndInset)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    ),
            )
        }

        // 筛选面板（模式 C 大面板变体）
        SearchFilterOverlay(
            visible = showFilter,
            backdrop = pageBackdrop,
            presence = presence,
            state = search,
            onDraft = { repository.setSearchFilter(it) },
            onApply = {
                repository.setSearchFilter(it)
                showFilter = false
            },
            onDismiss = { showFilter = false },
            onOpenSaveTemplate = {
                saveTemplateCard = true
            },
            onApplyTemplate = { template ->
                repository.applySearchTemplate(template.id)
                showFilter = false
            },
            onLongPressTemplate = { templateMenu = it },
        )

        // 模板保存（模式 B 居中卡）
        SearchTemplateCardOverlay(
            visible = saveTemplateCard,
            title = "保存为模板",
            initialName = "筛选 ${search.filter.activeCount()} 项",
            confirmLabel = "保存",
            backdrop = pageBackdrop,
            presence = presence,
            onDismiss = { saveTemplateCard = false },
            onConfirm = { name ->
                val saved = repository.saveSearchTemplate(name, search.filter)
                if (!saved) {
                    Toast.makeText(context, "最多保存 8 个模板", Toast.LENGTH_SHORT).show()
                }
                saveTemplateCard = false
            },
        )

        // 模板长按菜单（模式 B）
        SearchTemplateMenuOverlay(
            visible = templateMenu != null && renameTemplate == null,
            template = templateMenu,
            backdrop = pageBackdrop,
            presence = presence,
            onDismiss = { templateMenu = null },
            onRename = { template ->
                renameTemplate = template
                templateMenu = null
            },
            onDelete = { template ->
                repository.deleteSearchTemplate(template.id)
                templateMenu = null
            },
        )

        // 模板重命名（模式 B）
        SearchTemplateCardOverlay(
            visible = renameTemplate != null,
            title = "重命名模板",
            initialName = renameTemplate?.name.orEmpty(),
            confirmLabel = "保存",
            backdrop = pageBackdrop,
            presence = presence,
            onDismiss = { renameTemplate = null },
            onConfirm = { name ->
                renameTemplate?.let { repository.renameSearchTemplate(it.id, name) }
                renameTemplate = null
            },
        )
    }
}

/** 初始态：最近搜索 + 书夹快捷 chips（滚动内容 → 非玻璃 chips）。LazyGridScope 扩展（内部发 item）。 */
@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.InitialContent(
    search: SearchState,
    onUseHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenCategory: (LibraryTarget) -> Unit,
) {
    val facets = search.facets
    if (search.history.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "最近搜索",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearHistory) { Text("清空") }
                }
                FlowRow(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    search.history.forEach { keyword ->
                        AssistChip(onClick = { onUseHistory(keyword) }, label = { Text(keyword) })
                    }
                }
            }
        }
    }
    if (!facets?.categories.isNullOrEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    text = "书夹",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    facets.categories.forEach { category ->
                        InkPillChip(
                            label = category.name,
                            selected = false,
                            onClick = {
                                LibraryUiState.typeFilter = null
                                onOpenCategory(
                                    LibraryTarget.User(category.id, category.name, category.color),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
    if (search.history.isEmpty() && facets?.categories.isNullOrEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "输入关键词开始搜索",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 结果态实体命中区：书夹/系列 chips（滚动内容 → 非玻璃 chips）。 */
@Composable
private fun EntityHitSection(
    categoryHits: List<com.inkvol.app.data.model.CategoryRecord>,
    seriesHits: List<String>,
    seriesCount: (String) -> Int,
    onOpenCategory: (LibraryTarget) -> Unit,
) {
    Column {
        if (categoryHits.isNotEmpty()) {
            Text(
                text = "书夹",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categoryHits.forEach { category ->
                    InkPillChip(
                        label = category.name,
                        selected = true,
                        onClick = {
                            LibraryUiState.typeFilter = null
                            onOpenCategory(
                                LibraryTarget.User(category.id, category.name, category.color),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (seriesHits.isNotEmpty()) {
            Text(
                text = "系列",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(seriesHits.size, key = { seriesHits[it] }) { index ->
                    val name = seriesHits[index]
                    val count = seriesCount(name)
                    InkPillChip(
                        label = if (count > 0) "$name ×$count" else name,
                        selected = true,
                        onClick = {
                            LibraryUiState.typeFilter = null
                            onOpenCategory(LibraryTarget.Series(name))
                        },
                    )
                }
            }
        }
    }
}

/** 滚动内容内的非玻璃胶囊 chip（采集层内禁玻璃，规范 §2.4-2）。 */
@Composable
private fun InkPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(InkvolRadius.pill)
            .background(if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurface.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 搜索框行（规范 §2.3 搜索框档位：GlassRounded 28 / StrongBlur 12 /
 * 手机 Highlight·平板 Outline / Full；筛选钮 56dp 玻璃胶囊 + 角标外置）。
 */
@Composable
private fun SearchBarRow(
    input: String,
    onInputChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onOpenFilter: () -> Unit,
    activeFilters: Int,
    backdrop: Backdrop?,
    edge: GlassEdge,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassRounded(
            backdrop = backdrop,
            tier = GlassTier.Full,
            radius = 28.dp,
            blurRadius = GlassDimens.StrongBlur,
            edge = edge,
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text("搜索书名、系列、章节、正文") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (input.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Outlined.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = scheme.onSurface,
                    unfocusedTextColor = scheme.onSurface,
                    cursorColor = scheme.primary,
                ),
            )
        }
        // 角标必须放玻璃壳外（玻璃胶囊裁掉越界内容，SSMH 同款）
        Box(modifier = Modifier.height(56.dp).width(56.dp)) {
            GlassCapsule(
                backdrop = backdrop,
                tier = GlassTier.Full,
                blurRadius = GlassDimens.StrongBlur,
                edge = edge,
                // clickable 前先裁圆：涟漪/按压反馈裁到圆形边界，不再出现直角方块
                modifier = Modifier.fillMaxSize().clip(CircleShape).clickable(onClick = onOpenFilter),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = "筛选",
                        tint = scheme.onSurface.copy(alpha = 0.82f),
                        modifier = Modifier.height(20.dp).width(20.dp),
                    )
                }
            }
            if (activeFilters > 0) {
                Text(
                    text = activeFilters.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-5).dp)
                        .background(scheme.primary, RoundedCornerShape(9.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** 从 Compose 的 LocalView 向上找到宿主 Window（状态栏控制用，SSMH 同款）。 */
private fun Context.findWindow(): Window = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findWindow()
    else -> error("无法定位宿主 Window")
}
