package com.inkvol.app.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.model.SearchFacets
import com.inkvol.app.data.model.SearchFilter
import com.inkvol.app.data.model.SearchFilterTemplate
import com.inkvol.app.data.model.SearchState
import com.inkvol.app.data.model.TriStateSet
import com.inkvol.app.ui.components.glass.GlassCapsule
import com.inkvol.app.ui.components.glass.GlassDimens
import com.inkvol.app.ui.components.glass.GlassEdge
import com.inkvol.app.ui.components.glass.GlassOverlay
import com.inkvol.app.ui.components.glass.GlassOverlayMotion
import com.inkvol.app.ui.components.glass.GlassOverlayPresence
import com.inkvol.app.ui.components.glass.GlassTier
import com.inkvol.app.ui.components.glass.LocalGlassChrome
import com.kyant.backdrop.Backdrop

/**
 * 筛选面板 = 设计规范 §5「模式 C 大面板变体」：
 * BottomCenter 锚定、widthIn(max=360).fillMaxWidth()、heightIn(屏高 72%)+verticalScroll、
 * 玻璃 Full/StrongBlur 12/Outline/默认 tint、透明关闭层、无 scrim、
 * end=railEndInset、bottom=contentBottom+16 让位、GlassOverlay.BottomPanel 动效（220ms 进 + 40dp 上浮）。
 * 面板内全 chips 化零键盘（系列/标签/作者 distinct 列表；超大集合靠面板滚动）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchFilterOverlay(
    visible: Boolean,
    backdrop: Backdrop,
    presence: GlassOverlayPresence,
    state: SearchState,
    /** 面板内 chips 草稿：即时生效但不关面板。 */
    onDraft: (SearchFilter) -> Unit,
    /** 完成/清除筛选：生效并关闭面板。 */
    onApply: (SearchFilter) -> Unit,
    onDismiss: () -> Unit,
    onOpenSaveTemplate: () -> Unit,
    onApplyTemplate: (SearchFilterTemplate) -> Unit,
    onLongPressTemplate: (SearchFilterTemplate) -> Unit,
) {
    val chrome = LocalGlassChrome.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val filter = state.filter
    // 形态分档：手机 360/两列，平板 Rail 480/三列（格子大小两态接近，密度协调）
    val panelMaxWidth = if (chrome.railMode) 480.dp else 360.dp
    val panelColumns = if (chrome.railMode) 3 else 2

    fun toggle(set: TriStateSet, id: String): TriStateSet =
        set.with(id, triStateOf(id, set).next())

    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.BottomPanel,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = Alignment.BottomCenter,
        radius = 16.dp,
        modifier = Modifier
            .widthIn(max = panelMaxWidth)
            .fillMaxWidth()
            .padding(end = chrome.railEndInset)
            .padding(bottom = chrome.contentBottom + 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.72f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "筛选",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.padding(top = 4.dp))
            Text(
                text = "点一下「只看」，再点一下「屏蔽」，第三下取消。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 模板行（最近使用排序 + 保存当前筛选；长按菜单）
            Spacer(Modifier.padding(top = 12.dp))
            Text(
                text = "模板",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.padding(top = 6.dp))
            LazyRow(horizontalArrangement = ChipFlowSpacing) {
                items(state.templates, key = { it.id }) { template ->
                    TemplateChip(
                        name = template.name,
                        selected = template.id == state.activeTemplateId,
                        onClick = { onApplyTemplate(template) },
                        onLongClick = { onLongPressTemplate(template) },
                    )
                }
                item(key = "save_template") {
                    TemplateChip(
                        name = "＋ 保存当前筛选",
                        selected = false,
                        onClick = onOpenSaveTemplate,
                        onLongClick = null,
                        emphasized = true,
                    )
                }
            }

            val facets = state.facets
            if (facets != null) {
                FilterGroup("类型") {
                    val chipAll: @Composable () -> Unit = {
                        GlassPillChip(
                            label = "全部",
                            selected = filter.type == null,
                            onClick = { onDraft(filter.copy(type = null)) },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val chipComic: @Composable () -> Unit = {
                        GlassPillChip(
                            label = "漫画",
                            selected = filter.type == "COMIC",
                            onClick = { onDraft(filter.copy(type = "COMIC")) },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val chipNovel: @Composable () -> Unit = {
                        GlassPillChip(
                            label = "小说",
                            selected = filter.type == "NOVEL",
                            onClick = { onDraft(filter.copy(type = "NOVEL")) },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ChipGridTwo(listOf(chipAll, chipComic, chipNovel), panelColumns)
                }
                FilterGroup("阅读状态") {
                    val statusChips = ArrayList<@Composable () -> Unit>()
                    listOf(
                        null to "全部",
                        BookRecord.READ_UNREAD to "未读",
                        BookRecord.READ_READING to "在读",
                        BookRecord.READ_FINISHED to "读完",
                    ).forEach { (value, label) ->
                        val chip: @Composable () -> Unit = {
                            GlassPillChip(
                                label = label,
                                selected = filter.readStatus == value,
                                onClick = { onDraft(filter.copy(readStatus = value)) },
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        statusChips.add(chip)
                    }
                    ChipGridTwo(statusChips, panelColumns)
                }
                FilterGroup("书夹") {
                    val categoryChips = ArrayList<@Composable () -> Unit>()
                    facets.categories.forEach { category ->
                        val chip: @Composable () -> Unit = {
                            TriStateChip(
                                label = category.name,
                                tri = triStateOf(category.id.toString(), filter.categories),
                                onClick = { onDraft(filter.copy(categories = toggle(filter.categories, category.id.toString()))) },
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        categoryChips.add(chip)
                    }
                    val unclassifiedChip: @Composable () -> Unit = {
                        TriStateChip(
                            label = "未分类",
                            tri = triStateOf(SearchFilter.UNCLASSIFIED_ID, filter.categories),
                            onClick = { onDraft(filter.copy(categories = toggle(filter.categories, SearchFilter.UNCLASSIFIED_ID))) },
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    categoryChips.add(unclassifiedChip)
                    ChipGridTwo(categoryChips, panelColumns)
                }
                FilterGroup("文件夹") {
                    val folderChips = ArrayList<@Composable () -> Unit>()
                    facets.folders.forEach { folder ->
                        val id = folder.id.toString()
                        val chip: @Composable () -> Unit = {
                            TriStateChip(
                                label = folder.name,
                                tri = triStateOf(id, filter.folders),
                                onClick = { onDraft(filter.copy(folders = toggle(filter.folders, id))) },
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        folderChips.add(chip)
                    }
                    ChipGridTwo(folderChips, panelColumns)
                }
                if (facets.series.isNotEmpty()) {
                    FilterGroup("系列") {
                        val seriesChips = ArrayList<@Composable () -> Unit>()
                        facets.series.forEach { name ->
                            val chip: @Composable () -> Unit = {
                                TriStateChip(
                                    label = name,
                                    tri = triStateOf(name, filter.series),
                                    onClick = { onDraft(filter.copy(series = toggle(filter.series, name))) },
                                    backdrop = backdrop,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            seriesChips.add(chip)
                        }
                        ChipGridTwo(seriesChips, panelColumns)
                    }
                }
                if (facets.tags.isNotEmpty()) {
                    FilterGroup("标签") {
                        val tagChips = ArrayList<@Composable () -> Unit>()
                        facets.tags.forEach { tag ->
                            val chip: @Composable () -> Unit = {
                                TriStateChip(
                                    label = tag.name,
                                    tri = triStateOf(tag.name, filter.tags),
                                    onClick = { onDraft(filter.copy(tags = toggle(filter.tags, tag.name))) },
                                    backdrop = backdrop,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            tagChips.add(chip)
                        }
                        ChipGridTwo(tagChips, panelColumns)
                    }
                }
                if (facets.writers.isNotEmpty()) {
                    FilterGroup("作者") {
                        val writerChips = ArrayList<@Composable () -> Unit>()
                        facets.writers.forEach { name ->
                            val chip: @Composable () -> Unit = {
                                TriStateChip(
                                    label = name,
                                    tri = triStateOf(name, filter.writers),
                                    onClick = { onDraft(filter.copy(writers = toggle(filter.writers, name))) },
                                    backdrop = backdrop,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            writerChips.add(chip)
                        }
                        ChipGridTwo(writerChips, panelColumns)
                    }
                }
                FilterGroup("格式") {
                    val formatChips = ArrayList<@Composable () -> Unit>()
                    SearchFilter.FORMAT_OPTIONS.forEach { format ->
                        val chip: @Composable () -> Unit = {
                            GlassPillChip(
                                label = formatDisplayName(format),
                                selected = format in filter.formats,
                                onClick = {
                                    val next = if (format in filter.formats) filter.formats - format else filter.formats + format
                                    onDraft(filter.copy(formats = next))
                                },
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        formatChips.add(chip)
                    }
                    ChipGridTwo(formatChips, panelColumns)
                }
            } else {
                Spacer(Modifier.padding(top = 20.dp))
                Text(
                    text = "正在载入筛选项…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.padding(top = 20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onApply(SearchFilter()) }) { Text("清除筛选") }
                Spacer(Modifier.padding(horizontal = 4.dp))
                Button(onClick = { onApply(filter) }) { Text("完成") }
            }
        }
    }
}

private fun formatDisplayName(format: String): String =
    if (format == "FOLDER") "文件夹" else format

/** 等宽 chips 网格（手机两列 / 平板三列分档）；末行补空位补齐到满列保持对齐；横纵间距统一 8dp。 */
@Composable
private fun ChipGridTwo(chips: List<@Composable () -> Unit>, columns: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.chunked(columns).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { chip ->
                    Box(modifier = Modifier.weight(1f)) { chip() }
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 筛选面板内的分组（标签 + chips FlowRow）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(label: String, chips: @Composable () -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = ChipFlowSpacing) { chips() }
    }
}

/** 模板 chip：应用 + 长按菜单（长按带触觉）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TemplateChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    emphasized: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    GlassCapsule(
        backdrop = null,
        tier = GlassTier.Full,
        blurRadius = GlassDimens.StrongBlur,
        edge = GlassEdge.Outline,
        modifier = Modifier.heightIn(min = 32.dp),
    ) {
        val container = when {
            selected -> scheme.primary
            emphasized -> scheme.surfaceContainerHigh
            else -> Color.Transparent
        }
        val content = when {
            selected -> scheme.onPrimary
            emphasized -> scheme.onSurface
            else -> scheme.onSurface.copy(alpha = 0.82f)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(container)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (onLongClick != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        }
                    },
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
            )
        }
    }
}

/**
 * 模板保存/重命名卡 = 设计规范 §5「模式 B 居中卡」：
 * 居中 + widthIn(max=340).fillMaxWidth() 钳制、GlassOverlay.Card 动效（scale 0.94）、
 * 端=railEndInset 避让、imePadding（居中卡键盘避让实测生效）。
 */
@Composable
fun SearchTemplateCardOverlay(
    visible: Boolean,
    title: String,
    initialName: String,
    confirmLabel: String,
    backdrop: Backdrop,
    presence: GlassOverlayPresence,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val chrome = LocalGlassChrome.current
    var name by remember(visible) { mutableStateOf(initialName) }
    LaunchedEffect(visible) {
        if (visible) name = initialName
    }
    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.Card,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = Alignment.Center,
        radius = 16.dp,
        modifier = Modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp + chrome.railEndInset)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.padding(top = 12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("模板名称") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.padding(top = 14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.padding(horizontal = 4.dp))
                Button(onClick = { onConfirm(name.trim()) }) { Text(confirmLabel) }
            }
        }
    }
}

/** 模板长按菜单卡（重命名 / 删除；模式 B）。 */
@Composable
fun SearchTemplateMenuOverlay(
    visible: Boolean,
    template: SearchFilterTemplate?,
    backdrop: Backdrop,
    presence: GlassOverlayPresence,
    onDismiss: () -> Unit,
    onRename: (SearchFilterTemplate) -> Unit,
    onDelete: (SearchFilterTemplate) -> Unit,
) {
    val chrome = LocalGlassChrome.current
    GlassOverlay(
        visible = visible && template != null,
        motion = GlassOverlayMotion.Card,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = Alignment.Center,
        radius = 16.dp,
        modifier = Modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp + chrome.railEndInset),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            template?.let { current ->
                Text(
                    text = current.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.padding(top = 4.dp))
                TextButton(
                    onClick = { onRename(current) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("重命名") }
                TextButton(
                    onClick = { onDelete(current) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
