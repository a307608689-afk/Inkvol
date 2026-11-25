package com.inkvol.app.ui.legal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.data.settings.SettingsRepository
import com.inkvol.app.ui.components.glass.GlassOverlay
import com.inkvol.app.ui.components.glass.GlassOverlayMotion
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

/**
 * 首次启动隐私同意：《设计规范》§5 模式 C-2（GlassOverlay BottomPanel + 页面 backdrop）。
 * 未同意不进入主界面，也不启动任何联网初始化（SyncScheduler 侧有同源门禁）。
 * 面板不可点外部关闭：必须明确选择同意或退出。
 */
@Composable
fun PrivacyConsentScreen(
    settingsRepository: SettingsRepository,
    onDecline: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var agreeing by remember { mutableStateOf(false) }
    var policyOpen by remember { mutableStateOf(false) }
    val background = MaterialTheme.colorScheme.background

    // 页面采集层：玻璃面板必须有页面级 backdrop（禁止用宿主 LocalGlassBackdrop）
    val pageBackdrop = rememberLayerBackdrop(onDraw = {
        drawRect(background)
        drawContent()
    })

    // 返回键不绕过：必须明确选择同意或退出
    BackHandler(enabled = true) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(pageBackdrop))

        GlassOverlay(
            visible = true,
            motion = GlassOverlayMotion.BottomPanel,
            onDismiss = { },
            backdrop = pageBackdrop,
            alignment = Alignment.BottomCenter,
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = 420.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "欢迎使用 Inkvol",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "本地漫画与小说阅读器",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ConsentBullet(
                        title = "不收集个人信息",
                        body = "没有账号，没有广告、统计或崩溃上报 SDK；你的书库、进度、书签与笔记只保存在本机。",
                    )
                    ConsentBullet(
                        title = "默认不联网",
                        body = "只有你自己填写并开启 WebDAV 同步后，应用才会连接你指定的那台服务器；同步默认关闭。",
                    )
                    ConsentBullet(
                        title = "文件访问由你授权",
                        body = "应用不申请存储权限，只读取你在系统文件选择器中明确授权的文件夹或文件。",
                    )
                    ConsentBullet(
                        title = "系统云备份",
                        body = "若系统云备份开启，书库索引与阅读进度可能随系统备份保存到你的云账号；封面、图片缓存与崩溃日志不参与备份，可在系统设置中关闭。",
                    )
                    ConsentBullet(
                        title = "崩溃日志只在本机",
                        body = "闪退记录最多保留 5 份，仅写入应用私有目录，需要你主动导出才会离开设备。",
                    )
                    TextButton(
                        onClick = { policyOpen = true },
                        modifier = Modifier.padding(horizontal = 0.dp),
                    ) {
                        Text("阅读完整《隐私政策》")
                    }
                    Text(
                        text = "政策版本 v${LegalDocs.POLICY_VERSION} · 2026-11-23",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Button(
                        onClick = {
                            if (agreeing) return@Button
                            agreeing = true
                            scope.launch {
                                settingsRepository.putString(
                                    SettingsKeys.PRIVACY_ACCEPTED_VERSION,
                                    SettingsKeys.PRIVACY_POLICY_VERSION,
                                )
                            }
                        },
                        enabled = !agreeing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Text("同意并继续")
                    }
                    TextButton(
                        onClick = { if (!agreeing) onDecline() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("不同意并退出")
                    }
                }
            }
        }

        LegalDocOverlay(
            visible = policyOpen,
            doc = LegalDoc.PRIVACY,
            backdrop = pageBackdrop,
            onDismiss = { policyOpen = false },
        )
    }
}

@Composable
private fun ConsentBullet(title: String, body: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
