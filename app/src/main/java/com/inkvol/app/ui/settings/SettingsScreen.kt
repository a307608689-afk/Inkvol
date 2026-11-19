package com.inkvol.app.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkvol.app.data.backup.BackupPreview
import com.inkvol.app.data.backup.BackupRepository
import com.inkvol.app.data.backup.BackupRestoreSheet
import com.inkvol.app.data.model.PurifyRuleRecord
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.data.settings.SettingsRepository
import com.inkvol.app.ui.components.LocalGlassNotice
import com.inkvol.app.ui.components.LocalPageOverlayController
import com.inkvol.app.ui.components.TabPage
import com.inkvol.app.ui.components.glass.GlassDimens
import com.inkvol.app.ui.components.glass.GlassEdge
import com.inkvol.app.ui.components.glass.GlassOverlay
import com.inkvol.app.ui.components.glass.GlassOverlayMotion
import com.inkvol.app.ui.components.glass.GlassOverlayPresence
import com.inkvol.app.ui.components.glass.GlassRounded
import com.inkvol.app.ui.components.glass.GlassTier
import com.inkvol.app.ui.components.glass.LocalGlassChrome
import com.inkvol.app.ui.components.glass.rememberGlassOverlayPresence
import com.inkvol.app.ui.theme.InkvolRadius
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    libraryRepository: LibraryRepository,
    onOpenLibraryManager: () -> Unit,
    onOpenPanelSpike: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val glassNotice = LocalGlassNotice.current
    val pageBackground = MaterialTheme.colorScheme.background
    val pageBackdrop = rememberLayerBackdrop(onDraw = {
        drawRect(pageBackground)
        drawContent()
    })
    val backupRepository = remember(libraryRepository, settingsRepository) {
        BackupRepository(context, libraryRepository, settingsRepository)
    }
    var backupBusy by remember { mutableStateOf(false) }
    var restorePreview by remember { mutableStateOf<BackupPreview?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupRepository.MIME_TYPE),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupBusy = true
        scope.launch {
            val result = backupRepository.export(uri)
            backupBusy = false
            glassNotice.show(
                result.fold(
                    onSuccess = { "备份已导出" },
                    onFailure = { "备份失败：${it.message ?: "未知错误"}" },
                ),
            )
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupBusy = true
        scope.launch {
            val result = backupRepository.inspect(uri)
            backupBusy = false
            result.fold(
                onSuccess = { preview ->
                    if (preview.isValid) {
                        pendingRestoreUri = uri
                        restorePreview = preview
                    } else {
                        glassNotice.show("备份版本不支持")
                    }
                },
                onFailure = { glassNotice.show("无法读取备份：${it.message ?: "未知错误"}") },
            )
        }
    }

    val prefs by settingsRepository.preferences.collectAsStateWithLifecycle(initialValue = null)
    val savedCount = prefs?.asMap()?.size ?: 0
    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    // 分组页「点入回顶」标志：仅从设置首页点入分组时置 true，回顶后立即复位。
    // 用普通 remember（不进保存通道）：Tab 切走组合销毁时归 false——切回来不回顶，
    // 保住恢复出来的列表位置与头部折叠态。
    var pendingGroupTopAlign by remember { mutableStateOf(false) }
    var openChoice by remember { mutableStateOf<ChoiceSpec?>(null) }
    // M6.3-C：净化规则面板
    var purifyPanelOpen by remember { mutableStateOf(false) }

    // M6.1-D 设置页收尾：小说字体管理 / 分格缓存清理
    var panelCacheCount by remember { mutableStateOf(0) }
    var panelCacheRefresh by remember { mutableStateOf(0) }
    LaunchedEffect(panelCacheRefresh) {
        panelCacheCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.inkvol.app.ui.reader.panel.PanelLayoutCache.count(context)
        }
    }
    val fontImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = com.inkvol.app.ui.novel.NovelFontRegistry.import(context, uri, settingsRepository)
                glassNotice.show(
                    if (imported != null) "字体已导入：${imported.name}" else "字体导入失败",
                )
            }
        }
    }
    val groups = buildSettingsGroups(
        repository = settingsRepository,
        libraryRepository = libraryRepository,
        prefs = prefs,
        scope = scope,
        context = context,
        savedCount = savedCount,
        panelCacheCount = panelCacheCount,
        onExportBackup = { if (!backupBusy) exportLauncher.launch("inkvol-backup.json") },
        onRestoreBackup = {
            if (!backupBusy) {
                restoreLauncher.launch(arrayOf(BackupRepository.MIME_TYPE, "application/octet-stream", "*/*"))
            }
        },
        onOpenLibraryManager = onOpenLibraryManager,
        onOpenPanelSpike = onOpenPanelSpike,
        onImportFont = {
            fontImporter.launch(
                arrayOf(
                    "application/x-font-ttf",
                    "application/x-font-otf",
                    "font/ttf",
                    "font/otf",
                ),
            )
        },
        onOpenChoice = { openChoice = it },
        onOpenPurifyRules = { purifyPanelOpen = true },
        onClearPanelCache = {
            scope.launch {
                val removed = com.inkvol.app.ui.reader.panel.PanelLayoutCache.clearAll(context)
                panelCacheRefresh++
                glassNotice.show("已清理 $removed 条分格缓存")
            }
        },
        onNotice = { glassNotice.show(it) },
    )
    val overlayController = LocalPageOverlayController.current
    val showBackupSheet = restorePreview != null && pendingRestoreUri != null

    // 覆盖层退场期间 chrome 要等它退完再回来；数据型面板保留最后一帧数据
    val overlayPresence = rememberGlassOverlayPresence()
    var choiceHeld by remember { mutableStateOf<ChoiceSpec?>(null) }
    var previewHeld by remember { mutableStateOf<BackupPreview?>(null) }
    var restoreUriHeld by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(openChoice) { openChoice?.let { choiceHeld = it } }
    LaunchedEffect(restorePreview) { restorePreview?.let { previewHeld = it } }
    LaunchedEffect(pendingRestoreUri) { pendingRestoreUri?.let { restoreUriHeld = it } }

    val overlayShowing = openChoice != null || showBackupSheet || overlayPresence.present
    LaunchedEffect(overlayShowing) {
        overlayController.setActive(overlayShowing)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop),
        ) {
            // 二级导航方向滑动：进二级从右滑入，返回从左滑入，
            // 参数与顶层 Tab 切换转场一致（280ms FastOutSlowIn）。
            BackHandler(enabled = selectedGroupId != null) {
                selectedGroupId = null
            }
            AnimatedContent(
                targetState = selectedGroupId,
                transitionSpec = {
                    val forward = when {
                        initialState == null -> true
                        targetState == null -> false
                        else -> {
                            val from = groups.indexOfFirst { it.id == initialState }
                            val to = groups.indexOfFirst { it.id == targetState }
                            to >= from
                        }
                    }
                    val spec = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                    slideInHorizontally(spec) { full -> if (forward) full else -full } togetherWith
                        slideOutHorizontally(spec) { full -> if (forward) -full else full }
                },
                label = "settingsGroupNav",
            ) { groupId ->
                val group = groups.firstOrNull { it.id == groupId }
                if (group == null) {
                    SettingsHome(
                        groups = groups,
                        onOpenGroup = {
                            pendingGroupTopAlign = true
                            selectedGroupId = it.id
                        },
                    )
                } else {
                    SettingsGroupDetail(
                        group = group,
                        alignTopOnEntry = pendingGroupTopAlign,
                        onTopAligned = { pendingGroupTopAlign = false },
                        onBack = { selectedGroupId = null },
                        onOpenChoice = { openChoice = it },
                    )
                }
            }
        }

        choiceHeld?.let { spec ->
            BackHandler(enabled = openChoice != null) {
                openChoice = null
            }
            SettingChoiceSheet(
                visible = openChoice != null,
                presence = overlayPresence,
                backdrop = pageBackdrop,
                spec = spec,
                onDismiss = { openChoice = null },
                onSelect = { next ->
                    spec.onSelect(next)
                    openChoice = null
                },
            )
        }

        val preview = previewHeld
        val restoreUri = restoreUriHeld
        if (preview != null && restoreUri != null) {
            BackHandler(enabled = showBackupSheet) {
                restorePreview = null
                pendingRestoreUri = null
            }
            BackupRestoreSheet(
                visible = showBackupSheet,
                presence = overlayPresence,
                backdrop = pageBackdrop,
                preview = preview,
                busy = backupBusy,
                onDismiss = {
                    restorePreview = null
                    pendingRestoreUri = null
                },
                onRestore = {
                    backupBusy = true
                    scope.launch {
                        val result = backupRepository.restore(restoreUri)
                        backupBusy = false
                        restorePreview = null
                        pendingRestoreUri = null
                        glassNotice.show(
                            result.fold(
                                onSuccess = { restored ->
                                    "恢复完成 · ${restored.restoredBooks} 书 · " +
                                        "${restored.restoredPreferences} 设置"
                                },
                                onFailure = { "恢复失败：${it.message ?: "未知错误"}" },
                            ),
                        )
                    }
                },
            )
        }

        // M6.3-C：净化规则管理面板（遮罩点击关闭，无涟漪）
        BackHandler(enabled = purifyPanelOpen) { purifyPanelOpen = false }
        val purifyRules by libraryRepository.purifyRules.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { libraryRepository.refreshPurifyRules() }
        PurifyRulesPanel(
            visible = purifyPanelOpen,
            rules = purifyRules,
            onAdd = { name, pattern -> libraryRepository.addPurifyRule(name, pattern) },
            onDelete = { libraryRepository.deletePurifyRule(it) },
            onToggle = { id, enabled -> libraryRepository.setPurifyRuleEnabled(id, enabled) },
            onDismiss = { purifyPanelOpen = false },
        )
    }
}

/** M6.3-C：净化规则管理面板（遮罩 + 底部卡 + 规则列表 + 添加行）。 */
@Composable
private fun PurifyRulesPanel(
    visible: Boolean,
    rules: List<PurifyRuleRecord>,
    onAdd: (String, String) -> Unit,
    onDelete: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var newPattern by remember { mutableStateOf("") }
    var patternError by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 } +
            scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(120, easing = FastOutLinearInEasing)) +
            slideOutVertically(tween(160)) { it / 3 } + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { onDismiss() }
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "净化规则",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${rules.size} 条 · 对新导入的书籍生效",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "关闭",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    ) {
                        items(rules, key = { it.id }) { rule ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rule.name + if (rule.builtin) " · 内置" else "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = rule.pattern,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { onToggle(rule.id, it) },
                                    )
                                    IconButton(onClick = { onDelete(rule.id) }, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it; patternError = false },
                                label = { Text("规则名称") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = newPattern,
                                onValueChange = { newPattern = it; patternError = false },
                                label = { Text("正则") },
                                singleLine = true,
                                isError = patternError,
                                supportingText = if (patternError) {
                                    { Text("正则语法无效") }
                                } else null,
                                modifier = Modifier.weight(1.4f),
                                textStyle = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            Text(
                                text = "命中的行会在导入时整行移除",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    val name = newName.trim()
                                    val pattern = newPattern.trim()
                                    if (name.isEmpty() || pattern.isEmpty()) return@TextButton
                                    val valid = runCatching { Regex(pattern) }.isSuccess
                                    if (!valid) {
                                        patternError = true
                                        return@TextButton
                                    }
                                    onAdd(name, pattern)
                                    newName = ""
                                    newPattern = ""
                                },
                            ) {
                                Text("添加规则")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHome(
    groups: List<SettingsGroupSpec>,
    onOpenGroup: (SettingsGroupSpec) -> Unit,
) {
    val chrome = LocalGlassChrome.current
    // 回顶总线由 TabPage 的折叠分支统一接管（滚到顶 + 头部滑回展开）
    val listState = rememberLazyListState()

    TabPage(
        title = "设置",
        subtitle = "选择设置分组",
        collapsible = true,
        scrollState = listState,
        // 与书架一致：滚动时状态栏跟着收起，下滑 / 回顶唤出
        statusBarAutoHide = true,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                bottom = chrome.contentBottomForPage + GlassDimens.ListBottomExtraScroll,
            ),
        ) {
            items(groups, key = { it.id }) { group ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(InkvolRadius.card)
                        .clickable { onOpenGroup(group) },
                    shape = InkvolRadius.card,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = group.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${group.specs.size} 项设置",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupDetail(
    group: SettingsGroupSpec,
    /** 仅「从设置首页点入」为 true：列表回顶一次并回调复位；Tab 切回恒 false，照恢复态落位 */
    alignTopOnEntry: Boolean,
    onTopAligned: () -> Unit,
    onBack: () -> Unit,
    onOpenChoice: (ChoiceSpec) -> Unit,
) {
    val chrome = LocalGlassChrome.current
    val overlayController = LocalPageOverlayController.current
    val listState = rememberLazyListState()

    DisposableEffect(group.id) {
        onDispose { overlayController.setActive(false) }
    }

    // 回顶只在「点入分组」发生：Tab 切走再切回（alignTopOnEntry=false）不动恢复出的位置
    LaunchedEffect(group.id, alignTopOnEntry) {
        if (alignTopOnEntry) {
            listState.scrollToItem(0)
            onTopAligned()
        }
    }

    TabPage(
        title = group.title,
        subtitle = group.subtitle,
        collapsible = true,
        scrollState = listState,
        headerResetKey = group.id,
        // 与书架一致：滚动时状态栏跟着收起，下滑 / 回顶唤出
        statusBarAutoHide = true,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                bottom = chrome.contentBottomForPage + GlassDimens.ListBottomExtraScroll,
            ),
        ) {
            item(key = "back") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(InkvolRadius.card)
                        .clickable(onClick = onBack),
                    shape = InkvolRadius.card,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = "返回设置目录",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
            items(group.specs, key = { it.id }) { spec ->
                SettingSpecRow(
                    spec = spec,
                    onOpenChoice = onOpenChoice,
                )
            }
        }
    }
}

@Composable
private fun SettingSpecRow(
    spec: SettingSpec,
    onOpenChoice: (ChoiceSpec) -> Unit,
) {
    when (spec) {
        is ToggleSpec -> SettingRow(
            title = spec.title,
            subtitle = spec.subtitle,
            value = if (spec.value) "开" else "关",
            enabled = spec.enabled,
            onClick = { spec.onToggle(!spec.value) },
        )

        is ChoiceSpec -> SettingRow(
            title = spec.title,
            subtitle = spec.subtitle,
            value = spec.currentLabel,
            enabled = spec.enabled,
            onClick = {
                if (!spec.enabled) return@SettingRow
                if (spec.usesSheet) {
                    onOpenChoice(spec)
                } else {
                    spec.onSelect(spec.nextValue())
                }
            },
        )

        is InfoSpec -> SettingRow(
            title = spec.title,
            subtitle = spec.subtitle,
            value = spec.value,
            enabled = false,
            dimmed = false,
            onClick = {},
        )

        is ActionSpec -> SettingRow(
            title = spec.title,
            subtitle = spec.subtitle,
            value = spec.value,
            enabled = spec.enabled,
            onClick = spec.onAction,
        )

        is NoteSpec -> Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = InkvolRadius.card,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                spec.subtitle?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = spec.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SettingChoiceSheet(
    visible: Boolean,
    presence: GlassOverlayPresence,
    backdrop: com.kyant.backdrop.Backdrop,
    spec: ChoiceSpec,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val chrome = LocalGlassChrome.current

    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.BottomPanel,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = Alignment.BottomCenter,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp + chrome.railEndInset,
                bottom = 16.dp,
            )
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(max = 460.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            spec.subtitle?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(spec.options, key = { it.value }) { option ->
                    val selected = option.value == spec.value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(option.value) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "当前选择",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    value: String,
    enabled: Boolean,
    dimmed: Boolean = !enabled,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (dimmed) 0.42f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun buildSettingsGroups(
    repository: SettingsRepository,
    libraryRepository: LibraryRepository,
    prefs: Preferences?,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    savedCount: Int,
    panelCacheCount: Int,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onOpenLibraryManager: () -> Unit,
    onOpenPanelSpike: () -> Unit,
    onImportFont: () -> Unit,
    onOpenChoice: (ChoiceSpec) -> Unit,
    onOpenPurifyRules: () -> Unit,
    onClearPanelCache: () -> Unit,
    onNotice: (String) -> Unit,
): List<SettingsGroupSpec> {
    fun stringOf(key: String, default: String): String =
        prefs?.let { repository.stringOf(it, key, default) } ?: default

    fun booleanOf(key: String, default: Boolean): Boolean =
        prefs?.let { repository.booleanOf(it, key, default) } ?: default

    fun putString(key: String, value: String) {
        scope.launch { repository.putString(key, value) }
    }

    fun putBoolean(key: String, value: Boolean) {
        scope.launch { repository.putBoolean(key, value) }
    }

    // M6.1-D：小说阅读全局默认（per-book 未设置时回退到它）
    fun globalPrefs(): com.inkvol.app.data.model.NovelReadingPrefs = runCatching {
        kotlinx.serialization.json.Json.decodeFromString(
            com.inkvol.app.data.model.NovelReadingPrefs.serializer(),
            stringOf(SettingsKeys.NOVEL_GLOBAL_PREFS, ""),
        )
    }.getOrDefault(com.inkvol.app.data.model.NovelReadingPrefs())

    fun putGlobal(next: com.inkvol.app.data.model.NovelReadingPrefs) {
        putString(
            SettingsKeys.NOVEL_GLOBAL_PREFS,
            kotlinx.serialization.json.Json.encodeToString(
                com.inkvol.app.data.model.NovelReadingPrefs.serializer(),
                next,
            ),
        )
    }

    val importedFonts = runCatching {
        kotlinx.serialization.json.Json.decodeFromString<List<com.inkvol.app.data.model.NovelFontRecord>>(
            stringOf(SettingsKeys.NOVEL_FONTS, "[]"),
        )
    }.getOrDefault(emptyList())

    fun choice(
        id: String,
        title: String,
        subtitle: String?,
        key: String,
        default: String,
        options: List<SettingChoice>,
        enabled: Boolean = true,
    ): ChoiceSpec = ChoiceSpec(
        id = id,
        title = title,
        subtitle = subtitle,
        value = stringOf(key, default),
        options = options,
        enabled = enabled,
    ) { next -> putString(key, next) }

    fun toggle(
        id: String,
        title: String,
        subtitle: String?,
        key: String,
        default: Boolean,
        enabled: Boolean = true,
    ): ToggleSpec = ToggleSpec(
        id = id,
        title = title,
        subtitle = subtitle,
        value = booleanOf(key, default),
        enabled = enabled,
    ) { next -> putBoolean(key, next) }

    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    return listOf(
        SettingsGroupSpec(
            id = "appearance",
            title = "外观",
            subtitle = "主题、动态取色",
            specs = listOf(
                choice(
                    id = "theme_mode",
                    title = "主题",
                    subtitle = "跟随系统、浅色或深色",
                    key = SettingsKeys.APP_THEME,
                    default = SettingsKeys.THEME_SYSTEM,
                    options = listOf(
                        SettingChoice(SettingsKeys.THEME_SYSTEM, "跟随系统"),
                        SettingChoice(SettingsKeys.THEME_LIGHT, "浅色"),
                        SettingChoice(SettingsKeys.THEME_DARK, "深色"),
                    ),
                ),
                toggle(
                    id = "dynamic_color",
                    title = "动态取色",
                    subtitle = "使用 Material You 壁纸配色（Android 12+）",
                    key = SettingsKeys.DYNAMIC_COLOR,
                    default = false,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                ),
            ),
        ),
        SettingsGroupSpec(
            id = "library",
            title = "书架",
            subtitle = "视图与入口",
            specs = listOf(
                choice(
                    id = "library_view",
                    title = "默认视图",
                    subtitle = "书夹、系列或平铺",
                    key = SettingsKeys.LIBRARY_VIEW,
                    default = SettingsKeys.VIEW_FOLDER,
                    options = listOf(
                        SettingChoice(SettingsKeys.VIEW_FOLDER, "书夹"),
                        SettingChoice(SettingsKeys.VIEW_SERIES, "系列"),
                        SettingChoice(SettingsKeys.VIEW_FLAT, "平铺"),
                    ),
                ),
            ),
        ),
        SettingsGroupSpec(
            id = "reader_comic",
            title = "阅读器（漫画）",
            subtitle = "速度、手势与翻页",
            specs = listOf(
                choice(
                    id = "reader_open_speed",
                    title = "一镜到底速度",
                    subtitle = "打开 / 返回动画时长",
                    key = SettingsKeys.READER_OPEN_SPEED,
                    default = SettingsKeys.OPEN_SPEED_FAST,
                    options = listOf(
                        SettingChoice(SettingsKeys.OPEN_SPEED_FASTER, "极快 (240ms)"),
                        SettingChoice(SettingsKeys.OPEN_SPEED_FAST, "快 (260ms)"),
                        SettingChoice(SettingsKeys.OPEN_SPEED_DEFAULT, "适中 (280ms)"),
                        SettingChoice(SettingsKeys.OPEN_SPEED_SLOW, "慢 (320ms)"),
                        SettingChoice(SettingsKeys.OPEN_SPEED_SLOWER, "更慢 (360ms)"),
                    ),
                ),
                choice(
                    id = "reader_page_transition",
                    title = "翻页动画",
                    subtitle = "点击 / 按键翻页的过渡；拖拽与跳页不变，横读生效",
                    key = SettingsKeys.READER_PAGE_TRANSITION,
                    default = SettingsKeys.PAGE_TRANSITION_SLIDE,
                    options = listOf(
                        SettingChoice(SettingsKeys.PAGE_TRANSITION_INSTANT, "无动画"),
                        SettingChoice(SettingsKeys.PAGE_TRANSITION_SLIDE, "平移"),
                        SettingChoice(SettingsKeys.PAGE_TRANSITION_COVER, "覆盖"),
                    ),
                ),
                choice(
                    id = "reader_double_page",
                    title = "双页模式",
                    subtitle = "横屏自动并排双页；宽图不参与配对",
                    key = SettingsKeys.READER_DOUBLE_PAGE,
                    default = SettingsKeys.DOUBLE_PAGE_AUTO,
                    options = listOf(
                        SettingChoice(SettingsKeys.DOUBLE_PAGE_AUTO, "宽屏自动"),
                        SettingChoice(SettingsKeys.DOUBLE_PAGE_ALWAYS, "始终双页"),
                        SettingChoice(SettingsKeys.DOUBLE_PAGE_OFF, "关闭"),
                    ),
                ),
                choice(
                    id = "reader_panel_default",
                    title = "智能分格默认",
                    subtitle = "新书与未单独设置过的书使用该档位；关闭可省内存",
                    key = SettingsKeys.READER_PANEL_DEFAULT,
                    default = SettingsKeys.PANEL_OFF,
                    options = listOf(
                        SettingChoice(SettingsKeys.PANEL_OFF, "关闭"),
                        SettingChoice(SettingsKeys.PANEL_AUTO, "自动"),
                        SettingChoice(SettingsKeys.PANEL_ON, "始终开启"),
                    ),
                ),
                InfoSpec(
                    id = "panel_cache_count",
                    title = "分格缓存",
                    subtitle = "已检测分格布局的磁盘缓存条数",
                    value = "$panelCacheCount",
                ),
                ActionSpec(
                    id = "panel_cache_clear",
                    title = "清理分格缓存",
                    subtitle = "删除全部已缓存分格布局，下次阅读重新检测",
                    value = "清理",
                    onAction = onClearPanelCache,
                ),
                InfoSpec(
                    id = "panel_editor_hint",
                    title = "手动分格编辑",
                    subtitle = "在阅读器快捷面板打开，仅当前页可编辑",
                    value = "阅读器内",
                ),
                toggle(
                    id = "reader_gestures_master",
                    title = "手势总开关",
                    subtitle = "关闭后禁用全部阅读器手势",
                    key = SettingsKeys.READER_GESTURES_MASTER,
                    default = true,
                ),
                toggle(
                    id = "reader_tap_paging",
                    title = "单击翻页",
                    subtitle = "左 / 右区单击翻页",
                    key = SettingsKeys.READER_TAP_PAGING,
                    default = true,
                ),
                toggle(
                    id = "reader_double_tap_zoom",
                    title = "双击缩放",
                    subtitle = "中区双击在 2x 与 1x 间切换",
                    key = SettingsKeys.READER_DOUBLE_TAP_ZOOM,
                    default = true,
                ),
                toggle(
                    id = "reader_pinch_zoom",
                    title = "捏合缩放",
                    subtitle = "双指缩放并拖动",
                    key = SettingsKeys.READER_PINCH_ZOOM,
                    default = true,
                ),
                choice(
                    id = "reader_long_press",
                    title = "长按",
                    subtitle = "选择长按手势行为",
                    key = SettingsKeys.READER_LONG_PRESS,
                    default = SettingsKeys.LONG_PRESS_MAGNIFIER,
                    options = listOf(
                        SettingChoice(SettingsKeys.LONG_PRESS_MAGNIFIER, "放大镜"),
                        SettingChoice(SettingsKeys.LONG_PRESS_QUICK_PANEL, "快捷面板"),
                        SettingChoice(SettingsKeys.LONG_PRESS_NONE, "无"),
                    ),
                ),
                choice(
                    id = "reader_magnifier_orientation",
                    title = "放大镜形状",
                    subtitle = "横向覆盖一整行对白，竖向覆盖一段竖排文字或高瘦分格",
                    key = SettingsKeys.READER_MAGNIFIER_ORIENTATION,
                    default = SettingsKeys.MAGNIFIER_LANDSCAPE,
                    options = listOf(
                        SettingChoice(SettingsKeys.MAGNIFIER_LANDSCAPE, "横向"),
                        SettingChoice(SettingsKeys.MAGNIFIER_PORTRAIT, "竖向"),
                    ),
                ),
                choice(
                    id = "reader_magnifier_size",
                    title = "放大镜尺寸",
                    subtitle = "透镜长边占页宽比例，切换形状只改朝向、可见面积不变",
                    key = SettingsKeys.READER_MAGNIFIER_SIZE,
                    default = SettingsKeys.MAGNIFIER_STANDARD,
                    options = listOf(
                        SettingChoice(SettingsKeys.MAGNIFIER_COMPACT, "紧凑"),
                        SettingChoice(SettingsKeys.MAGNIFIER_STANDARD, "标准"),
                        SettingChoice(SettingsKeys.MAGNIFIER_WIDE, "宽幅"),
                    ),
                ),
                choice(
                    id = "reader_magnifier_zoom",
                    title = "放大镜倍率",
                    subtitle = "倍率越高字越大，镜内可见范围越小",
                    key = SettingsKeys.READER_MAGNIFIER_ZOOM,
                    default = "2.0",
                    options = listOf(
                        SettingChoice("1.8", "1.8x"),
                        SettingChoice("2.0", "2.0x"),
                        SettingChoice("2.5", "2.5x"),
                    ),
                ),
                toggle(
                    id = "reader_edge_brightness",
                    title = "左缘亮度",
                    subtitle = "屏幕左边缘 20dp 内竖滑调节",
                    key = SettingsKeys.READER_EDGE_BRIGHTNESS,
                    default = true,
                ),
                toggle(
                    id = "reader_edge_color_temp",
                    title = "右缘色温",
                    subtitle = "屏幕右边缘 20dp 内竖滑调节",
                    key = SettingsKeys.READER_EDGE_COLOR_TEMP,
                    default = true,
                ),
                toggle(
                    id = "reader_volume_keys",
                    title = "音量键翻页",
                    subtitle = "音量上下一页",
                    key = SettingsKeys.READER_VOLUME_KEYS,
                    default = true,
                ),
            ),
        ),
        SettingsGroupSpec(
            id = "novel",
            title = "小说阅读",
            subtitle = "全局默认排版、字体与主题",
            specs = run {
                val defaults = globalPrefs()
                val fontOptions = listOf(
                    SettingChoice("system", "系统默认"),
                    SettingChoice("serif", "衬线"),
                    SettingChoice("sans", "黑体"),
                    SettingChoice("mono", "等宽"),
                ) + importedFonts.map { SettingChoice(it.id, it.name) }

                val deleteFontsSpec = ChoiceSpec(
                    id = "novel_global_font_delete",
                    title = "删除导入字体",
                    subtitle = "点击一项即删除该字体文件与记录",
                    value = "",
                    options = importedFonts.map { SettingChoice(it.id, it.name) },
                ) { id ->
                    scope.launch {
                        val record = importedFonts.firstOrNull { it.id == id }
                        if (record != null) {
                            com.inkvol.app.ui.novel.NovelFontRegistry.delete(context, record, repository)
                            onNotice("已删除字体：${record.name}")
                        }
                    }
                }

                fun fontSizeChoice(): ChoiceSpec = ChoiceSpec(
                    id = "novel_global_font_size",
                    title = "默认字号",
                    subtitle = "新小说与未单独设置过的书使用",
                    value = "${defaults.fontSize}",
                    options = listOf(16, 18, 20, 22, 24).map {
                        SettingChoice("$it", "${it}sp")
                    },
                ) { next ->
                    putGlobal(defaults.copy(fontSize = next.toIntOrNull() ?: defaults.fontSize))
                }

                listOf(
                    ChoiceSpec(
                        id = "novel_global_font",
                        title = "默认字体",
                        subtitle = "新小说与未单独设置过的书使用",
                        value = defaults.fontId,
                        options = fontOptions,
                    ) { next -> putGlobal(defaults.copy(fontId = next)) },
                    ActionSpec(
                        id = "novel_global_font_import",
                        title = "导入字体",
                        subtitle = "通过系统文件选择器导入 TTF / OTF",
                        value = "选择文件",
                        onAction = onImportFont,
                    ),
                    ActionSpec(
                        id = "novel_global_font_delete",
                        title = "删除导入字体",
                        subtitle = "点击一项即删除该字体文件与记录",
                        value = "管理",
                        enabled = importedFonts.isNotEmpty(),
                        onAction = { onOpenChoice(deleteFontsSpec) },
                    ),
                    fontSizeChoice(),
                    ChoiceSpec(
                        id = "novel_global_line_height",
                        title = "默认行距",
                        subtitle = "行高为字号的倍数",
                        value = "%.2f".format(defaults.lineHeightRatio),
                        options = listOf(1.2f, 1.35f, 1.5f, 1.65f, 1.8f).map {
                            SettingChoice("%.2f".format(it), "${"%.2f".format(it)}×")
                        },
                    ) { next ->
                        putGlobal(defaults.copy(lineHeightRatio = next.toFloatOrNull() ?: defaults.lineHeightRatio))
                    },
                    ChoiceSpec(
                        id = "novel_global_letter_spacing",
                        title = "默认字距",
                        subtitle = "字符间距",
                        value = "${defaults.letterSpacing}",
                        options = listOf(0, 1, 2, 4).map { SettingChoice("$it", "${it}sp") },
                    ) { next ->
                        putGlobal(defaults.copy(letterSpacing = next.toIntOrNull() ?: defaults.letterSpacing))
                    },
                    ChoiceSpec(
                        id = "novel_global_paragraph",
                        title = "默认段距",
                        subtitle = "段落之间的间距",
                        value = "${defaults.paragraphSpacing}",
                        options = listOf(4, 8, 12, 16, 20).map { SettingChoice("$it", "${it}dp") },
                    ) { next ->
                        putGlobal(defaults.copy(paragraphSpacing = next.toIntOrNull() ?: defaults.paragraphSpacing))
                    },
                    ChoiceSpec(
                        id = "novel_global_indent",
                        title = "默认首行缩进",
                        subtitle = "每段首行缩进的字符数",
                        value = "${defaults.indentChars}",
                        options = listOf(0, 1, 2, 3, 4).map { SettingChoice("$it", "$it 字") },
                    ) { next ->
                        putGlobal(defaults.copy(indentChars = next.toIntOrNull() ?: defaults.indentChars))
                    },
                    ChoiceSpec(
                        id = "novel_global_padding",
                        title = "默认页边距",
                        subtitle = "正文左右边距",
                        value = "${defaults.pagePadding}",
                        options = listOf(16, 24, 32, 40).map { SettingChoice("$it", "${it}dp") },
                    ) { next ->
                        putGlobal(defaults.copy(pagePadding = next.toIntOrNull() ?: defaults.pagePadding))
                    },
                    ChoiceSpec(
                        id = "novel_global_theme",
                        title = "默认主题",
                        subtitle = "纸白 / 米黄 / 护眼绿 / 墨黑",
                        value = defaults.theme,
                        options = listOf(
                            SettingChoice(SettingsKeys.NOVEL_THEME_PAPER, "纸白"),
                            SettingChoice(SettingsKeys.NOVEL_THEME_SEPIA, "米黄"),
                            SettingChoice(SettingsKeys.NOVEL_THEME_GREEN, "护眼绿"),
                            SettingChoice(SettingsKeys.NOVEL_THEME_DARK, "墨黑"),
                        ),
                    ) { next -> putGlobal(defaults.copy(theme = next)) },
                    ChoiceSpec(
                        id = "novel_global_paging",
                        title = "默认翻页方式",
                        subtitle = "左右翻页或上下翻页",
                        value = defaults.pagingMode,
                        options = listOf(
                            SettingChoice(SettingsKeys.NOVEL_PAGE_HORIZONTAL, "左右翻页"),
                            SettingChoice(SettingsKeys.NOVEL_PAGE_VERTICAL, "上下翻页"),
                        ),
                    ) { next -> putGlobal(defaults.copy(pagingMode = next)) },
                    ChoiceSpec(
                        id = "novel_double_page",
                        title = "双页模式",
                        subtitle = "横屏自动并排双页；仅左右翻页生效，独立于漫画双页",
                        value = stringOf(SettingsKeys.NOVEL_DOUBLE_PAGE, SettingsKeys.DOUBLE_PAGE_AUTO),
                        options = listOf(
                            SettingChoice(SettingsKeys.DOUBLE_PAGE_AUTO, "宽屏自动"),
                            SettingChoice(SettingsKeys.DOUBLE_PAGE_ALWAYS, "始终双页"),
                            SettingChoice(SettingsKeys.DOUBLE_PAGE_OFF, "关闭"),
                        ),
                    ) { next -> putString(SettingsKeys.NOVEL_DOUBLE_PAGE, next) },
                    ActionSpec(
                        id = "novel_global_apply",
                        title = "应用到所有小说",
                        subtitle = "把以上默认排版写入每一本小说，覆盖本书设置",
                        value = "执行",
                        onAction = {
                            scope.launch {
                                val json = kotlinx.serialization.json.Json.encodeToString(
                                    com.inkvol.app.data.model.NovelReadingPrefs.serializer(),
                                    defaults,
                                )
                                val ids = libraryRepository.novelBookIds()
                                ids.forEach { repository.putString(SettingsKeys.novelBookKey(it), json) }
                                onNotice("已应用到 ${ids.size} 本小说")
                            }
                        },
                    ),
                )
            },
        ),
        SettingsGroupSpec(
            id = "import",
            title = "导入与书库",
            subtitle = "导入方式与入口",
            specs = listOf(
                choice(
                    id = "import_plus_action",
                    title = "加号默认动作",
                    subtitle = "顶栏加号点击后的默认行为",
                    key = SettingsKeys.PLUS_ACTION,
                    default = SettingsKeys.PLUS_ASK,
                    options = listOf(
                        SettingChoice(SettingsKeys.PLUS_ASK, "询问"),
                        SettingChoice(SettingsKeys.PLUS_FOLDER, "直接导入文件夹"),
                        SettingChoice(SettingsKeys.PLUS_FILE, "直接导入文件"),
                        SettingChoice(SettingsKeys.PLUS_RESCAN, "直接重新扫描"),
                    ),
                ),
                ActionSpec(
                    id = "library_manager",
                    title = "书库管理",
                    subtitle = "多书库 / 隐藏书库 / 回收站 / 重复检测",
                    value = "打开",
                    onAction = onOpenLibraryManager,
                ),
                ActionSpec(
                    id = "rescan_all",
                    title = "重新扫描全书",
                    subtitle = "按已添加书库全量重扫，新增 TXT / 漫画会被导入",
                    value = "扫描",
                    onAction = {
                        libraryRepository.rescanAll()
                        onNotice("已开始重新扫描")
                    },
                ),
                ActionSpec(
                    id = "panel_spike",
                    title = "分格检测实验",
                    subtitle = "M5-A 投影法 spike，不接入阅读器",
                    value = "打开",
                    onAction = onOpenPanelSpike,
                ),
            ),
        ),
        SettingsGroupSpec(
            id = "storage",
            title = "存储与备份",
            subtitle = "空间、备份恢复、清理",
            specs = listOf(
                InfoSpec(
                    id = "backup_summary",
                    title = "当前偏好条目",
                    subtitle = "导出时会包含全部应用设置与每本阅读偏好",
                    value = "$savedCount",
                ),
                ActionSpec(
                    id = "backup_export",
                    title = "导出备份",
                    subtitle = "书夹 / 进度 / 统计 / 设置，不复制源文件",
                    value = "SAF",
                    onAction = onExportBackup,
                ),
                ActionSpec(
                    id = "backup_restore",
                    title = "恢复备份",
                    subtitle = "先预检可匹配书，确认后合并恢复",
                    value = "SAF",
                    onAction = onRestoreBackup,
                ),
                ActionSpec(
                    id = "purify_rules",
                    title = "净化规则",
                    subtitle = "导入小说时按行过滤广告/水印/推广内容",
                    value = "管理",
                    onAction = onOpenPurifyRules,
                ),
                NoteSpec(
                    id = "storage_cleanup",
                    title = "空间清理",
                    subtitle = "导入缓存随系统与扫描自动收敛",
                    message = "手动清理策略随 M4-E 接入",
                ),
            ),
        ),
        SettingsGroupSpec(
            id = "privacy",
            title = "隐私与安全",
            subtitle = "应用锁、无痕阅读、崩溃日志",
            specs = listOf(
                NoteSpec(
                    id = "privacy_placeholder",
                    title = "隐私与安全",
                    subtitle = "当前为纯本地阅读，不采集数据",
                    message = "应用锁与诊断策略后续接入",
                ),
            ),
        ),
        SettingsGroupSpec(
            id = "about",
            title = "关于与诊断",
            subtitle = "版本、构建与偏好摘要",
            specs = listOf(
                InfoSpec(
                    id = "about_version",
                    title = "应用版本",
                    value = versionName,
                ),
                InfoSpec(
                    id = "about_build",
                    title = "构建类型",
                    value = if (com.inkvol.app.BuildConfig.DEBUG) "Debug" else "Release",
                ),
                InfoSpec(
                    id = "about_prefs",
                    title = "已保存偏好",
                    subtitle = "DataStore 当前键值数量",
                    value = "$savedCount 项",
                ),
            ),
        ),
    )
}
