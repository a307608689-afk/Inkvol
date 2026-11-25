package com.inkvol.app.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import com.inkvol.app.data.local.InkvolDatabase
import com.inkvol.app.data.sync.CryptoBox
import com.inkvol.app.data.sync.DavException
import com.inkvol.app.data.sync.SyncFileScope
import com.inkvol.app.data.sync.SyncFiles
import com.inkvol.app.data.sync.SyncRepository
import com.inkvol.app.data.sync.WebDavClient
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkvol.app.data.backup.BackupPreview
import com.inkvol.app.data.backup.BackupRepository
import com.inkvol.app.data.cleanup.CacheCleaner
import com.inkvol.app.data.cleanup.CleanupItem
import com.inkvol.app.data.diagnostics.CrashLogFile
import com.inkvol.app.data.diagnostics.CrashLogStore
import com.inkvol.app.data.backup.BackupRestoreSheet
import com.inkvol.app.data.model.PurifyRuleRecord
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.data.settings.SettingsRepository
import com.inkvol.app.ui.components.InkvolSwitch
import com.inkvol.app.ui.components.LocalGlassNotice
import com.inkvol.app.ui.components.LocalPageOverlayController
import com.inkvol.app.ui.legal.LegalDoc
import com.inkvol.app.ui.legal.LegalDocOverlay
import com.inkvol.app.ui.legal.LegalDocs
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
import com.inkvol.app.ui.theme.ThemeAccent
import com.inkvol.app.ui.theme.onColorFor
import com.inkvol.app.ui.theme.themeAccentOf
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    libraryRepository: LibraryRepository,
    // M4.5 同步（AppContainer 装配；null = 未装配）
    syncRepository: SyncRepository? = null,
    // M6.3-D 收口：设置首页 back = 切回书架 tab
    onBackToLibrary: () -> Unit,
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
    // ---- M4.5 同步状态与弹层 ----
    val syncUi = if (syncRepository != null) {
        syncRepository.state.collectAsStateWithLifecycle(
            initialValue = SyncRepository.SyncUiState(),
        ).value
    } else {
        SyncRepository.SyncUiState()
    }
    var syncConfigOpen by remember { mutableStateOf(false) }
    var syncLogOpen by remember { mutableStateOf(false) }
    var syncDeviceNameOpen by remember { mutableStateOf(false) }
    var syncClearConfirmOpen by remember { mutableStateOf(false) }
    var syncCloudBooksOpen by remember { mutableStateOf(false) }
    var syncScopeOpen by remember { mutableStateOf(false) }
    // 配置保存后自动弹范围面板：等配置面板退场动画结束再开，避免两个玻璃层重叠
    var pendingScopeAfterConfig by remember { mutableStateOf(false) }
    LaunchedEffect(pendingScopeAfterConfig) {
        if (pendingScopeAfterConfig) {
            kotlinx.coroutines.delay(260)
            syncScopeOpen = true
            pendingScopeAfterConfig = false
        }
    }
    // 任一同步面板打开时，返回键归面板（先关面板，不关分组/不退设置页）
    val syncOverlayOpen = syncConfigOpen || syncScopeOpen || syncLogOpen ||
        syncDeviceNameOpen || syncCloudBooksOpen || syncClearConfirmOpen
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
    // 批次 2：隐私政策 / 开源许可 / GPL 全文阅读层（null = 关闭）
    var legalDocOpen by remember { mutableStateOf<LegalDoc?>(null) }
    // 退场动画期间保留最后一帧文档（§5 数据型面板约定）
    var legalDocHeld by remember { mutableStateOf<LegalDoc?>(null) }
    // 崩溃日志面板（只读本机 filesDir/crash，分享走 FileProvider）
    var crashPanelOpen by remember { mutableStateOf(false) }
    var crashLogs by remember { mutableStateOf<List<CrashLogFile>>(emptyList()) }
    var crashRefresh by remember { mutableStateOf(0) }
    // 空间清理面板
    var cleanupPanelOpen by remember { mutableStateOf(false) }
    var cleanupItems by remember { mutableStateOf<List<CleanupItem>>(emptyList()) }
    var cleanupRefresh by remember { mutableStateOf(0) }
    // M6.3-D 收口：back 链——分组详情 → 设置首页 → 书架 tab。
    // 互斥 enabled 避免与 overlay 层（净化/备份）的 BackHandler 抢优先级。
    BackHandler(enabled = selectedGroupId != null && !syncOverlayOpen) { selectedGroupId = null }
    BackHandler(
        enabled = selectedGroupId == null && !purifyPanelOpen && !syncOverlayOpen &&
            legalDocOpen == null && !crashPanelOpen && !cleanupPanelOpen,
    ) {
        onBackToLibrary()
    }

    // M6.1-D 设置页收尾：小说字体管理 / 分格缓存清理
    var panelCacheCount by remember { mutableStateOf(0) }
    var panelCacheRefresh by remember { mutableStateOf(0) }
    LaunchedEffect(panelCacheRefresh) {
        panelCacheCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.inkvol.app.ui.reader.panel.PanelLayoutCache.count(context)
        }
    }
    LaunchedEffect(crashRefresh) {
        crashLogs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CrashLogStore.list(context)
        }
    }
    LaunchedEffect(cleanupRefresh) {
        val ids = libraryRepository.books.value.map { it.id }.toSet()
        cleanupItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CacheCleaner.scan(context, ids)
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
        syncUi = syncUi,
        onOpenSyncConfig = { syncConfigOpen = true },
        onOpenSyncScope = { syncScopeOpen = true },
        onOpenSyncLog = { syncLogOpen = true },
        onOpenSyncDeviceName = { syncDeviceNameOpen = true },
        onClearSyncConfig = { syncClearConfirmOpen = true },
        onOpenCloudBooks = { syncCloudBooksOpen = true },
        onSyncNow = {
            syncRepository?.syncNowAsync("手动")
            glassNotice.show("开始同步…")
        },
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
        onOpenLegalDoc = { legalDocOpen = it },
        onOpenSource = {
            if (!LegalDocs.openUrl(context, LegalDocs.SOURCE_URL)) {
                glassNotice.show("没有可用的浏览器")
            }
        },
        crashLogCount = crashLogs.size,
        onOpenCrashLogs = {
            crashRefresh++
            crashPanelOpen = true
        },
        cleanupSummary = CacheCleaner.formatSize(cleanupItems.sumOf { it.bytes }),
        onOpenCleanup = {
            cleanupRefresh++
            cleanupPanelOpen = true
        },
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
    LaunchedEffect(legalDocOpen) { legalDocOpen?.let { legalDocHeld = it } }

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
            BackHandler(enabled = selectedGroupId != null && !syncOverlayOpen) {
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
                    // 色卡面板：点选后保持展开并刷新选中态（便于连续试色），
                    // 其余选择面板仍是「点选即关」。
                    openChoice = if (spec.accentPicker) spec.copy(value = next) else null
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

        // ---- M4.5 同步弹层 ----
        if (syncRepository != null) {
            SyncConfigDialog(
                visible = syncConfigOpen,
                repository = settingsRepository,
                syncRepository = syncRepository,
                onDismiss = { syncConfigOpen = false },
                onSaved = {
                    syncConfigOpen = false
                    pendingScopeAfterConfig = true
                },
                onNotice = { glassNotice.show(it) },
                presence = overlayPresence,
                backdrop = pageBackdrop,
            )
            SyncScopeDialog(
                visible = syncScopeOpen,
                syncRepository = syncRepository,
                onDismiss = { syncScopeOpen = false },
                presence = overlayPresence,
                backdrop = pageBackdrop,
            )
            SyncLogDialog(
                visible = syncLogOpen,
                syncRepository = syncRepository,
                onDismiss = { syncLogOpen = false },
                presence = overlayPresence,
                backdrop = pageBackdrop,
            )
            SyncDeviceNameDialog(
                visible = syncDeviceNameOpen,
                repository = settingsRepository,
                syncRepository = syncRepository,
                onDismiss = { syncDeviceNameOpen = false },
                presence = overlayPresence,
                backdrop = pageBackdrop,
            )
            SyncCloudBooksDialog(
                visible = syncCloudBooksOpen,
                syncRepository = syncRepository,
                onDismiss = { syncCloudBooksOpen = false },
                onNotice = { glassNotice.show(it) },
                presence = overlayPresence,
                backdrop = pageBackdrop,
            )
            // §5 模式 B 居中卡：同步配置清除确认（原 AlertDialog 违反 §2.4-1）
            SyncClearConfirmOverlay(
                visible = syncClearConfirmOpen,
                presence = overlayPresence,
                backdrop = pageBackdrop,
                onDismiss = { syncClearConfirmOpen = false },
                onConfirm = {
                    syncClearConfirmOpen = false
                    scope.launch {
                        settingsRepository.putString(SettingsKeys.SYNC_SERVER, "")
                        settingsRepository.putString(SettingsKeys.SYNC_ACCOUNT, "")
                        settingsRepository.putString(SettingsKeys.SYNC_PASSWORD_ENC, "")
                        settingsRepository.putString(SettingsKeys.SYNC_DEVICE_NAME, "")
                        settingsRepository.putBoolean(SettingsKeys.SYNC_ENABLED, false)
                        settingsRepository.putString(SettingsKeys.SYNC_LAST_MODIFIED, "")
                        settingsRepository.putString(SettingsKeys.SYNC_FILE_SCOPE, "")
                        settingsRepository.putString(SettingsKeys.SYNC_LAST_RESULT, "")
                        settingsRepository.putLong(SettingsKeys.SYNC_LAST_OK_AT, 0)
                        settingsRepository.putInt(SettingsKeys.SYNC_FAILURES, 0)
                        settingsRepository.putLong(SettingsKeys.SYNC_COOLDOWN_UNTIL, 0)
                        syncRepository.refreshState()
                        glassNotice.show("已清除同步配置")
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

        legalDocHeld?.let { doc ->
            BackHandler(enabled = legalDocOpen != null) { legalDocOpen = null }
            LegalDocOverlay(
                visible = legalDocOpen != null,
                doc = doc,
                backdrop = pageBackdrop,
                onDismiss = { legalDocOpen = null },
                presence = overlayPresence,
            )
        }

        BackHandler(enabled = crashPanelOpen) { crashPanelOpen = false }
        CrashLogPanel(
            visible = crashPanelOpen,
            logs = crashLogs,
            presence = overlayPresence,
            backdrop = pageBackdrop,
            onDismiss = { crashPanelOpen = false },
            onRefresh = { crashRefresh++ },
            onNotice = { glassNotice.show(it) },
        )

        BackHandler(enabled = cleanupPanelOpen) { cleanupPanelOpen = false }
        StorageCleanupPanel(
            visible = cleanupPanelOpen,
            items = cleanupItems,
            validBookIds = libraryRepository.books.value.map { it.id }.toSet(),
            presence = overlayPresence,
            backdrop = pageBackdrop,
            onDismiss = { cleanupPanelOpen = false },
            onRefresh = { cleanupRefresh++ },
            onNotice = { glassNotice.show(it) },
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
                                    InkvolSwitch(
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

        is ChoiceSpec -> {
            // 当前深浅档：主题色行要在值区画出「该档」的实际颜色（深色档是独立亮版）。
            val darkScheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
            SettingRow(
                title = spec.title,
                subtitle = spec.subtitle,
                value = spec.currentLabel,
                enabled = spec.enabled,
                valueSwatch = if (spec.accentPicker) {
                    themeAccentOf(spec.value).swatchFor(darkScheme)
                } else {
                    null
                },
                onClick = {
                    if (!spec.enabled) return@SettingRow
                    if (spec.usesSheet) {
                        onOpenChoice(spec)
                    } else {
                        spec.onSelect(spec.nextValue())
                    }
                },
            )
        }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
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
                }
                // 色卡面板点选后不关闭（便于连续试色），因此另给显式出口；
                // 其它选择面板保持「点选即关」的原有语义。
                if (spec.accentPicker) {
                    TextButton(onClick = onDismiss) {
                        Text("完成")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (spec.accentPicker) {
                // 网格列数自适应：手机可用宽 ~296dp → 3 列（12 色 3×4）；
                // 平板面板 560dp 宽 → 6 列（12 色 6×2），两种形态都刚好排满。
                val darkScheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 80.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(spec.options, key = { it.value }) { option ->
                        AccentSwatchCell(
                            label = option.label,
                            color = themeAccentOf(option.value).swatchFor(darkScheme),
                            selected = option.value == spec.value,
                            onClick = { onSelect(option.value) },
                        )
                    }
                }
            } else {
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
}

/**
 * 主题色色卡：外圈选中环 + 实心圆 + 名称。
 * 圆点统一带 1dp 中性描边——深色档在墨底、浅色档在白底，边缘都不会糊掉。
 * 选中对勾用该色自己的 onPrimary（自动挑白/墨），浅中性色上也看得见。
 */
@Composable
private fun AccentSwatchCell(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                )
            }
            Box(
                modifier = Modifier
                    .size(if (selected) 30.dp else 32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = onColorFor(color),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    value: String,
    enabled: Boolean,
    dimmed: Boolean = !enabled,
    /** 值区前置色点：仅主题色行使用，其它行传 null 保持原样。 */
    valueSwatch: Color? = null,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (valueSwatch != null) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(valueSwatch)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                                shape = CircleShape,
                            ),
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
    syncUi: SyncRepository.SyncUiState,
    onOpenSyncConfig: () -> Unit,
    onOpenSyncScope: () -> Unit,
    onOpenSyncLog: () -> Unit,
    onOpenSyncDeviceName: () -> Unit,
    onClearSyncConfig: () -> Unit,
    onOpenCloudBooks: () -> Unit,
    onSyncNow: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onOpenLibraryManager: () -> Unit,
    onOpenPanelSpike: () -> Unit,
    onImportFont: () -> Unit,
    onOpenChoice: (ChoiceSpec) -> Unit,
    onOpenPurifyRules: () -> Unit,
    onClearPanelCache: () -> Unit,
    onNotice: (String) -> Unit,
    onOpenLegalDoc: (LegalDoc) -> Unit,
    onOpenSource: () -> Unit,
    crashLogCount: Int,
    onOpenCrashLogs: () -> Unit,
    cleanupSummary: String,
    onOpenCleanup: () -> Unit,
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

    // 漫画阅读全局布局默认（reader_global_prefs）：智能分格档的真源。
    // 全局快照未建立时默认「关闭」（省内存）。
    fun globalComicPanelMode(): String = runCatching {
        com.inkvol.app.data.model.decodeBookPrefs(
            stringOf(SettingsKeys.READER_GLOBAL_PREFS, ""),
            SettingsKeys.PANEL_OFF,
        )?.panelMode
    }.getOrNull() ?: SettingsKeys.PANEL_OFF

    fun putGlobalComicPanelMode(mode: String) {
        val current = runCatching {
            com.inkvol.app.data.model.decodeBookPrefs(
                stringOf(SettingsKeys.READER_GLOBAL_PREFS, ""),
                SettingsKeys.PANEL_OFF,
            )
        }.getOrNull() ?: com.inkvol.app.data.model.BookReadingPrefs()
        putString(
            SettingsKeys.READER_GLOBAL_PREFS,
            com.inkvol.app.data.model.encodeBookPrefs(
                current.copy(panelMode = mode, panelModeExplicit = true),
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

    // 动态取色与主题色互斥：动态优先（壁纸主色），主题色行灰化并说明原因。
    val dynamicColorOn = booleanOf(SettingsKeys.DYNAMIC_COLOR, false)

    return listOf(
        SettingsGroupSpec(
            id = "appearance",
            title = "外观",
            subtitle = "主题、主题色、动态取色",
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
                // 自定义主题色：只接管强调色（primary 一族），背景/卡片/语义色不动。
                ChoiceSpec(
                    id = "theme_accent",
                    title = "主题色",
                    subtitle = if (dynamicColorOn) {
                        "动态取色开启时不生效"
                    } else {
                        "只改强调色，背景保持墨黑纸白"
                    },
                    value = stringOf(SettingsKeys.THEME_ACCENT, ThemeAccent.VERMILION.id),
                    options = ThemeAccent.entries.map { SettingChoice(it.id, it.label) },
                    enabled = !dynamicColorOn,
                    accentPicker = true,
                ) { next -> putString(SettingsKeys.THEME_ACCENT, next) },
                toggle(
                    id = "dynamic_color",
                    title = "动态取色",
                    subtitle = "用壁纸主色做强调色（Android 12+）",
                    key = SettingsKeys.DYNAMIC_COLOR,
                    default = false,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                ),
                // 深色跟随：与小说阅读快捷面板「深色自动切换」行同键同步。
                toggle(
                    id = "novel_dark_follow",
                    title = "深色时小说背景自动切换",
                    subtitle = "App 进入深色时小说阅读背景自动切到墨黑，回浅色恢复原档",
                    key = SettingsKeys.NOVEL_DARK_FOLLOW,
                    default = true,
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
                choice(
                    id = "library_shelf_view",
                    title = "书架展示",
                    subtitle = "作用于书夹、系列、平铺的一级页",
                    key = SettingsKeys.LIBRARY_SHELF_VIEW,
                    default = SettingsKeys.SHELF_VIEW_GRID,
                    options = listOf(
                        SettingChoice(SettingsKeys.SHELF_VIEW_GRID, "网格"),
                        SettingChoice(SettingsKeys.SHELF_VIEW_LIST, "列表"),
                    ),
                ),
                choice(
                    id = "library_series_mode",
                    title = "系列结构",
                    subtitle = "只影响「系列」视图，切换不需要重扫",
                    key = SettingsKeys.LIBRARY_SERIES_MODE,
                    default = SettingsKeys.SERIES_MODE_HIERARCHY,
                    options = listOf(
                        SettingChoice(SettingsKeys.SERIES_MODE_HIERARCHY, "层级系列"),
                        SettingChoice(SettingsKeys.SERIES_MODE_FLAT, "扁平系列"),
                    ),
                ),
                choice(
                    id = "library_scan_depth",
                    title = "扫描深度",
                    subtitle = "嵌套文件夹最多下钻几层（默认 3 层）",
                    key = SettingsKeys.LIBRARY_SCAN_DEPTH,
                    default = "3",
                    options = listOf(
                        SettingChoice("1", "1 层"),
                        SettingChoice("2", "2 层"),
                        SettingChoice("3", "3 层"),
                        SettingChoice("4", "4 层"),
                        SettingChoice("5", "5 层"),
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
                // 智能分格默认写进全局阅读快照（与阅读器面板同一真源）；
                // 未单独记忆的书全部跟随它。
                ChoiceSpec(
                    id = "reader_panel_default",
                    title = "智能分格默认",
                    subtitle = "所有未单独记忆的书使用该档位；关闭可省内存",
                    value = globalComicPanelMode(),
                    options = listOf(
                        SettingChoice(SettingsKeys.PANEL_OFF, "关闭"),
                        SettingChoice(SettingsKeys.PANEL_AUTO, "自动"),
                        SettingChoice(SettingsKeys.PANEL_ON, "始终开启"),
                    ),
                ) { next -> putGlobalComicPanelMode(next) },
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
            subtitle = "全局默认排版、字体与背景",
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
                        title = "默认背景",
                        subtitle = "纸白 / 米黄 / 牛皮纸 / 护眼绿 / 雾蓝 / 樱粉 / 墨黑 / 深灰蓝",
                        value = defaults.theme,
                        options = listOf(
                            SettingChoice(SettingsKeys.NOVEL_THEME_PAPER, "纸白"),
                            SettingChoice(SettingsKeys.NOVEL_THEME_SEPIA, "米黄"),
                            SettingChoice(SettingsKeys.NOVEL_THEME_KRAFT, "牛皮纸"),
                            SettingChoice(SettingsKeys.NOVEL_THEME_GREEN, "护眼绿"),
                            SettingChoice(SettingsKeys.NOVEL_THEME_MIST, "雾蓝"),
                            SettingChoice(SettingsKeys.NOVEL_THEME_SAKURA, "樱粉"),
                            SettingChoice(SettingsKeys.NOVEL_THEME_DARK, "墨黑"),
                            SettingChoice(SettingsKeys.NOVEL_THEME_SLATE, "深灰蓝"),
                        ),
                    ) { next -> putGlobal(defaults.copy(theme = next)) },
                    // 深色跟随：与外观组同一全局键（两处入口实时同步）；阅读器排版面板不再提供开关行。
                    toggle(
                        id = "novel_dark_follow_novel",
                        title = "深色时背景自动切换",
                        subtitle = "App 进入深色时小说背景自动切到墨黑，回浅色恢复原档",
                        key = SettingsKeys.NOVEL_DARK_FOLLOW,
                        default = true,
                    ),
                    ChoiceSpec(
                        id = "novel_page_transition",
                        title = "动画",
                        subtitle = "点击翻页的过渡效果，独立于漫画；拖拽与跳页不受影响",
                        value = stringOf(SettingsKeys.NOVEL_PAGE_TRANSITION, SettingsKeys.PAGE_TRANSITION_SLIDE),
                        options = listOf(
                            SettingChoice(SettingsKeys.PAGE_TRANSITION_INSTANT, "无动画"),
                            SettingChoice(SettingsKeys.PAGE_TRANSITION_SLIDE, "平移"),
                            SettingChoice(SettingsKeys.PAGE_TRANSITION_COVER, "覆盖"),
                            SettingChoice(SettingsKeys.PAGE_TRANSITION_CURL, "仿真"),
                        ),
                    ) { next -> putString(SettingsKeys.NOVEL_PAGE_TRANSITION, next) },
                    ChoiceSpec(
                        id = "novel_global_paging",
                        title = "默认方向",
                        subtitle = "翻页方向或纵向连续滚动",
                        value = defaults.pagingMode,
                        options = listOf(
                            SettingChoice(SettingsKeys.NOVEL_PAGE_HORIZONTAL, "左右翻页"),
                            SettingChoice(SettingsKeys.NOVEL_PAGE_VERTICAL, "上下翻页"),
                            SettingChoice(SettingsKeys.NOVEL_PAGE_SCROLL, "纵向连续"),
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
                    toggle(
                        id = "novel_edge_brightness",
                        title = "左缘亮度",
                        subtitle = "屏幕左边缘 20dp 内竖滑调节（纵向连续滚动档不支持）",
                        key = SettingsKeys.NOVEL_EDGE_BRIGHTNESS,
                        default = true,
                    ),
                    toggle(
                        id = "novel_edge_color_temp",
                        title = "右缘色温",
                        subtitle = "屏幕右边缘 20dp 内竖滑调节（纵向连续滚动档不支持）",
                        key = SettingsKeys.NOVEL_EDGE_COLOR_TEMP,
                        default = true,
                    ),
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
            id = "sync",
            title = "同步",
            subtitle = "多设备进度与标注（WebDAV）",
            specs = run {
                val statusText = when {
                    !syncUi.configured -> "未配置"
                    !syncUi.enabled -> "已停用"
                    syncUi.syncing -> "同步中…"
                    syncUi.waitingWifi -> "等待 WiFi"
                    syncUi.cooldownUntil > System.currentTimeMillis() -> "冷却至 " + java.text.SimpleDateFormat(
                        "HH:mm",
                        java.util.Locale.getDefault(),
                    ).format(java.util.Date(syncUi.cooldownUntil))
                    syncUi.lastOkAt > 0L -> "上次 " + java.text.SimpleDateFormat(
                        "MM-dd HH:mm",
                        java.util.Locale.getDefault(),
                    ).format(java.util.Date(syncUi.lastOkAt))
                    else -> "已就绪"
                }
                buildList {
                    add(
                        InfoSpec(
                            id = "sync_status",
                            title = "同步状态",
                            subtitle = syncUi.lastResult.ifBlank {
                                if (syncUi.serverHost.isNotBlank()) syncUi.serverHost else "坚果云或任意 WebDAV 服务器"
                            },
                            value = statusText,
                        ),
                    )
                    add(
                        ActionSpec(
                            id = "sync_server",
                            title = "同步服务配置",
                            subtitle = "服务器、账号、应用密码与同步目录",
                            value = if (syncUi.configured) "修改" else "配置",
                            onAction = onOpenSyncConfig,
                        ),
                    )
                    if (syncUi.configured) {
                        add(
                            toggle(
                                id = "sync_auto",
                                title = "自动同步",
                                subtitle = "保存进度后 2 分钟内自动同步，退后台补同步",
                                key = SettingsKeys.SYNC_AUTO,
                                default = true,
                            ),
                        )
                        add(
                            toggle(
                                id = "sync_wifi_only",
                                title = "仅在 WiFi 下自动同步",
                                subtitle = "移动数据下暂停自动同步，手动同步不受限",
                                key = SettingsKeys.SYNC_WIFI_ONLY,
                                default = true,
                            ),
                        )
                        add(
                            ActionSpec(
                                id = "sync_scope",
                                title = "同步范围",
                                subtitle = "选择哪些书籍文件上传到云端；进度与标注始终同步",
                                value = syncUi.fileScopeSummary.ifBlank { "未选择" },
                                onAction = onOpenSyncScope,
                            ),
                        )
                        add(
                            ActionSpec(
                                id = "sync_cloud_books",
                                title = "云端书籍",
                                subtitle = "查看云端文件，下载本地缺失的书籍",
                                value = "查看",
                                onAction = onOpenCloudBooks,
                            ),
                        )
                        add(
                            toggle(
                                id = "sync_enabled",
                                title = "启用同步",
                                subtitle = "关闭后停止全部自动与手动同步",
                                key = SettingsKeys.SYNC_ENABLED,
                                default = false,
                            ),
                        )
                        add(
                            ActionSpec(
                                id = "sync_now",
                                title = "立即同步",
                                subtitle = "拉取其它设备并推送本机进度（绕过 WiFi 门禁）",
                                value = if (syncUi.syncing) "同步中" else "执行",
                                enabled = !syncUi.syncing && syncUi.enabled,
                                onAction = onSyncNow,
                            ),
                        )
                        add(
                            ActionSpec(
                                id = "sync_device_name",
                                title = "设备名",
                                subtitle = "同步记录里显示的本机名称",
                                value = syncUi.deviceName.ifBlank { "默认" },
                                onAction = onOpenSyncDeviceName,
                            ),
                        )
                        add(
                            ActionSpec(
                                id = "sync_log",
                                title = "同步记录",
                                subtitle = "最近 20 次同步的时间、方向与结果",
                                value = "查看",
                                onAction = onOpenSyncLog,
                            ),
                        )
                        add(
                            ActionSpec(
                                id = "sync_clear",
                                title = "清除同步配置",
                                subtitle = "删除本机配置；可选同时删除云端本机文件",
                                value = "清除",
                                onAction = onClearSyncConfig,
                            ),
                        )
                    }
                }
            },
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
                ActionSpec(
                    id = "storage_cleanup",
                    title = "空间清理",
                    subtitle = "导入缓存 / 分享图 / 分格缓存 / 孤儿封面",
                    value = cleanupSummary,
                    onAction = onOpenCleanup,
                ),
            ),
        ),
        SettingsGroupSpec(
            id = "privacy",
            title = "隐私与安全",
            subtitle = "应用锁与崩溃日志",
            specs = listOf(
                toggle(
                    id = "app_lock",
                    title = "应用锁",
                    subtitle = "离开 30 秒后需验证系统锁屏凭据；未设置锁屏密码时不生效",
                    key = SettingsKeys.APP_LOCK_ENABLED,
                    default = false,
                ),
                NoteSpec(
                    id = "app_lock_hint",
                    title = "验证方式",
                    subtitle = "使用系统锁屏密码 / 图案 / 指纹，应用无法读取这些信息",
                    message = "崩溃日志已移至「关于与诊断」",
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
                ActionSpec(
                    id = "about_crash_logs",
                    title = "崩溃日志",
                    subtitle = "闪退记录仅存本机，可导出后反馈",
                    value = "$crashLogCount 条",
                    onAction = onOpenCrashLogs,
                ),
                ActionSpec(
                    id = "about_privacy",
                    title = "隐私政策",
                    subtitle = "数据仅保存在本机；同步为可选项",
                    value = "查看",
                    onAction = { onOpenLegalDoc(LegalDoc.PRIVACY) },
                ),
                ActionSpec(
                    id = "about_licenses",
                    title = "开源许可",
                    subtitle = "GPL-3.0 与第三方依赖说明",
                    value = "查看",
                    onAction = { onOpenLegalDoc(LegalDoc.LICENSES) },
                ),
                ActionSpec(
                    id = "about_gpl",
                    title = "GPL-3.0 全文",
                    subtitle = "许可证完整文本（分发合规）",
                    value = "查看",
                    onAction = { onOpenLegalDoc(LegalDoc.GPL) },
                ),
                ActionSpec(
                    id = "about_source",
                    title = "源码与反馈",
                    subtitle = "GitHub 仓库，问题可在此提交",
                    value = "打开",
                    onAction = onOpenSource,
                ),
            ),
        ),
    )
}

/** 《设计规范》§5 C-2：底部面板高度上限 = 60% 屏高。 */
@Composable
private fun panelMaxHeight(): Dp =
    (LocalConfiguration.current.screenHeightDp * 0.6f).dp

// ---- M4.5 同步弹层 ----

@Composable
private fun SyncConfigDialog(
    visible: Boolean,
    repository: SettingsRepository,
    syncRepository: SyncRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onNotice: (String) -> Unit,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
) {
    // 系统返回 / 侧滑返回：关面板，不退出设置页（GlassOverlay 只处理遮罩点击）
    BackHandler(enabled = visible) { onDismiss() }
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var basePath by remember { mutableStateOf("/Inkvol/") }
    var allowInsecure by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible && !loaded) {
            val prefs = repository.snapshotPreferences()
            server = repository.stringOf(prefs, SettingsKeys.SYNC_SERVER, "")
                .ifBlank { "https://dav.jianguoyun.com/dav/" }
            account = repository.stringOf(prefs, SettingsKeys.SYNC_ACCOUNT, "")
            basePath = repository.stringOf(prefs, SettingsKeys.SYNC_BASE_PATH, "/Inkvol/")
            allowInsecure = repository.booleanOf(prefs, SettingsKeys.SYNC_ALLOW_INSECURE_TLS, false)
            loaded = true
        }
    }

    fun normalizeBasePath(raw: String): String = raw.trim().let {
        (if (it.startsWith("/")) it else "/$it").let { p -> if (p.endsWith("/")) p else "$p/" }
    }

    fun runTest() {
        val pwd = password
        val acct = account.trim()
        val srv = server.trim()
        if (srv.isBlank() || acct.isBlank()) {
            testResult = "请先填写服务器与账号"
            return
        }
        if (!srv.startsWith("http://") && !srv.startsWith("https://")) {
            testResult = "服务器地址需以 http:// 或 https:// 开头"
            return
        }
        testing = true
        testResult = ""
        val insecure = allowInsecure
        val base = normalizeBasePath(basePath)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val outcome = runCatching {
                val client = WebDavClient(srv, base, acct, pwd, allowInsecureTls = insecure)
                // 第 1 步：读目录（404 / 空目录都算通过——首次启用还没建）
                client.list("")
                // 第 2 步：逐级建目录（幂等；父级缺失也能建）
                client.ensureCollections(SyncRepository.DEVICE_DIR, SyncRepository.BOOK_DIR)
                // 第 3 步：复核目录是否真的生效
                val names = client.list("").map { it.name }.toSet()
                if ("devices" !in names || "books" !in names) {
                    throw DavException("目录创建后未出现在列表中（服务器可能只读）", 0)
                }
                "连接成功 · 路径 $base · 可写"
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                testing = false
                testResult = outcome.fold(
                    onSuccess = { it },
                    onFailure = { e ->
                        when {
                            e is DavException && e.statusCode == 401 -> "账号或应用密码错误（401）"
                            e is DavException && e.statusCode == 403 ->
                                "权限不足（403）：请确认应用密码有读写权限"
                            e is DavException && e.statusCode == 0 -> "连接失败：${e.message ?: ""}"
                            e is DavException -> "失败（HTTP ${e.statusCode}）：${e.message ?: ""}"
                            else -> "失败：${e.message ?: e.javaClass.simpleName}"
                        }
                    },
                )
            }
        }
    }

    val chrome = LocalGlassChrome.current
    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.BottomPanel,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = Alignment.BottomCenter,
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp + chrome.railEndInset,
                bottom = 16.dp,
            )
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(max = panelMaxHeight()),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "同步服务配置",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("服务器 URL（http/https）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (server.trim().startsWith("http://")) {
                    Text(
                        "明文 http 连接，仅建议在局域网内使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("账号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("应用密码") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "坚果云：网页端「账户信息 → 安全选项 → 添加应用密码」生成，勿用登录密码",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = basePath,
                    onValueChange = { basePath = it },
                    label = { Text("同步目录") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "允许自签证书",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "仅对自建 NAS 的 HTTPS 自签证书有效，安全性降低",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    InkvolSwitch(checked = allowInsecure, onCheckedChange = { allowInsecure = it })
                }
                if (testResult.isNotBlank()) {
                    Text(
                        testResult,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testResult.startsWith("连接成功")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                TextButton(onClick = { runTest() }, enabled = !testing) {
                    Text(if (testing) "测试中…" else "测试连接")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    enabled = !testing &&
                        (server.trim().startsWith("https://") || server.trim().startsWith("http://")) &&
                        account.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        scope.launch {
                            repository.putString(SettingsKeys.SYNC_SERVER, server.trim())
                            repository.putString(SettingsKeys.SYNC_ACCOUNT, account.trim())
                            repository.putString(
                                SettingsKeys.SYNC_BASE_PATH,
                                normalizeBasePath(basePath),
                            )
                            repository.putBoolean(SettingsKeys.SYNC_ALLOW_INSECURE_TLS, allowInsecure)
                            if (password.isNotBlank()) {
                                repository.putString(
                                    SettingsKeys.SYNC_PASSWORD_ENC,
                                    CryptoBox.encrypt(password),
                                )
                            }
                            repository.putBoolean(SettingsKeys.SYNC_ENABLED, true)
                            syncRepository.refreshState()
                            onNotice("已保存，请选择同步范围")
                            onSaved()
                        }
                    },
                ) { Text("保存并选择范围") }
            }
        }
    }
}

@Composable
private fun SyncScopeDialog(
    visible: Boolean,
    syncRepository: SyncRepository,
    onDismiss: () -> Unit,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
) {
    BackHandler(enabled = visible) { onDismiss() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var folders by remember { mutableStateOf<List<InkvolDatabase.ScopeFolderRow>>(emptyList()) }
    var singleBooks by remember { mutableStateOf(0) }
    var all by remember { mutableStateOf(false) }
    var single by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        loading = true
        runCatching { syncRepository.loadScopeEditor() }.onSuccess { data ->
            all = data.scope.all
            single = data.scope.single
            selected = data.scope.folderIds.toSet()
            folders = data.folders
            singleBooks = data.singleLibraryBooks
        }
        loading = false
    }

    fun confirm() {
        if (saving) return
        saving = true
        scope.launch {
            syncRepository.saveFileScope(
                SyncFileScope(
                    all = all,
                    single = single,
                    folderIds = selected.toList(),
                ),
            )
            syncRepository.syncNowAsync("首次同步")
            saving = false
            onDismiss()
        }
    }

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
            .heightIn(max = panelMaxHeight()),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "同步范围",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "全部同步",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "所有可单文件传输的书籍都上传；文件夹漫画不参与",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                InkvolSwitch(
                    checked = all,
                    onCheckedChange = { checked ->
                        all = checked
                        if (checked) {
                            single = true
                            selected = folders.map { it.id }.toSet()
                        }
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (loading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        "读取文件夹…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "single") {
                        ScopeRow(
                            title = "单文件导入（散书）",
                            subtitle = "$singleBooks 本",
                            checked = all || single,
                            enabled = !all,
                        ) { single = !single }
                    }
                    items(folders, key = { it.id }) { folder ->
                        val folderOnly = (folder.totalBooks - folder.transferableBooks).coerceAtLeast(0)
                        ScopeRow(
                            title = folder.name.ifBlank { "文件夹 ${folder.id}" },
                            subtitle = buildString {
                                append("${folder.transferableBooks} 本可同步")
                                if (folderOnly > 0) append(" · 其中 $folderOnly 本文件夹漫画不参与")
                                if (folder.libraryName.isNotBlank() && folder.libraryName != folder.name) {
                                    append(" · ${folder.libraryName}")
                                }
                            },
                            checked = all || folder.id in selected,
                            enabled = !all,
                        ) {
                            selected = if (folder.id in selected) {
                                selected - folder.id
                            } else {
                                selected + folder.id
                            }
                        }
                    }
                    if (folders.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "还没有导入任何文件夹",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                TextButton(
                    enabled = !all,
                    onClick = {
                        single = false
                        selected = emptySet()
                    },
                ) { Text("全不选") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("稍后") }
                Button(onClick = { confirm() }, enabled = !saving) {
                    Text(if (saving) "保存中…" else "保存并同步")
                }
            }
        }
    }
}

/** 同步范围单行：复选框 + 标题 + 副标题（书数/文件夹漫画提示）。 */
@Composable
private fun ScopeRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.55f),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { if (enabled) onToggle() },
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SyncLogDialog(
    visible: Boolean,
    syncRepository: SyncRepository,
    onDismiss: () -> Unit,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
) {
    BackHandler(enabled = visible) { onDismiss() }
    var entries by remember { mutableStateOf(syncRepository.syncLog()) }
    LaunchedEffect(visible) {
        if (visible) entries = syncRepository.syncLog()
    }
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
            .heightIn(max = panelMaxHeight()),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "同步记录",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        "暂无同步记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries) { entry ->
                        Text(
                            text = buildString {
                                append(
                                    java.text.SimpleDateFormat(
                                        "MM-dd HH:mm",
                                        java.util.Locale.getDefault(),
                                    ).format(java.util.Date(entry.at)),
                                )
                                append(if (entry.ok) "  成功" else "  失败")
                                if (entry.ok) {
                                    append(" · 拉取 ${entry.pulled} · 推送 ${entry.pushed}")
                                    if (entry.conflicts > 0) append(" · 冲突 ${entry.conflicts}")
                                } else {
                                    append(" · ").append(entry.message ?: entry.errorKind ?: "")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry.ok) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncDeviceNameDialog(
    visible: Boolean,
    repository: SettingsRepository,
    syncRepository: SyncRepository,
    onDismiss: () -> Unit,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
) {
    BackHandler(enabled = visible) { onDismiss() }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    LaunchedEffect(visible) {
        if (visible) {
            name = syncRepository.state.value.deviceName
        }
    }
    val chrome = LocalGlassChrome.current
    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.BottomPanel,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = Alignment.BottomCenter,
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp + chrome.railEndInset,
                bottom = 16.dp,
            )
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(max = panelMaxHeight()),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "设备名",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("留空使用默认（厂商+型号）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = {
                        scope.launch {
                            repository.putString(SettingsKeys.SYNC_DEVICE_NAME, name.trim())
                            syncRepository.refreshState()
                            onDismiss()
                        }
                    },
                ) { Text("保存") }
            }
        }
    }
}

@Composable
private fun SyncCloudBooksDialog(
    visible: Boolean,
    syncRepository: SyncRepository,
    onDismiss: () -> Unit,
    onNotice: (String) -> Unit,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
) {
    BackHandler(enabled = visible) { onDismiss() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var books by remember { mutableStateOf<List<SyncFiles.CloudBookInfo>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        error = ""
        syncRepository.listCloudBooks().fold(
            onSuccess = { books = it },
            onFailure = { error = it.message ?: "读取失败" },
        )
        loading = false
    }

    LaunchedEffect(visible) {
        if (visible) refresh()
    }

    fun downloadMissing() {
        if (downloading) return
        downloading = true
        scope.launch {
            val missing = books.filter { !it.existsLocally }
            var ok = 0
            missing.forEach { info ->
                syncRepository.downloadCloudBook(info).fold(
                    onSuccess = { ok++ },
                    onFailure = { },
                )
            }
            downloading = false
            onNotice("已下载导入 $ok 本")
            refresh()
        }
    }

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
            .heightIn(max = panelMaxHeight()),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "云端书籍",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                val missing = books.count { !it.existsLocally }
                Text(
                    if (loading) "读取中…" else "云端 ${books.size} 本 · 本地缺失 $missing 本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error.isNotBlank()) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(books) { info ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = info.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (info.existsLocally) "本地已有" else "云端独有",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (info.existsLocally) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = books.any { !it.existsLocally } && !downloading && error.isBlank(),
                    onClick = { downloadMissing() },
                ) { Text(if (downloading) "下载中…" else "下载缺失") }
            }
        }
    }
}

/** §5 模式 B：清除同步配置确认（居中玻璃卡，宽 340，避让侧栏）。 */
@Composable
private fun SyncClearConfirmOverlay(
    visible: Boolean,
    presence: GlassOverlayPresence,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val chrome = LocalGlassChrome.current
    BackHandler(enabled = visible) { onDismiss() }
    GlassOverlay(
        visible = visible,
        motion = GlassOverlayMotion.Card,
        onDismiss = onDismiss,
        backdrop = backdrop,
        presence = presence,
        alignment = Alignment.Center,
        modifier = Modifier
            .padding(end = chrome.railEndInset)
            .widthIn(max = 340.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "清除同步配置？",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "将删除本机的服务器、账号与应用密码配置并停用同步。" +
                    "云端数据与其它设备不受影响；重新配置后可重新配对。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("清除")
                }
            }
        }
    }
}
