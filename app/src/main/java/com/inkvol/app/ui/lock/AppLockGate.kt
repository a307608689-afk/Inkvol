package com.inkvol.app.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.inkvol.app.data.settings.SettingsKeys
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock

/**
 * 应用锁：离开超过 [SettingsKeys.APP_LOCK_GRACE_MS] 回前台、或冷启动时上锁。
 *
 * 解锁走系统设备凭据（KeyguardManager.createConfirmDeviceCredentialIntent），
 * 不新增依赖、不接触任何生物特征数据；设备未设置锁屏密码时本功能自动不生效。
 */
@Composable
fun AppLockGate(
    enabled: Boolean,
    onExit: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val keyguard = remember(context) {
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }
    // 不缓存：用户在本次会话里刚设置锁屏密码时，回前台要及时生效
    var deviceSecure by remember(keyguard) { mutableStateOf(keyguard?.isDeviceSecure == true) }
    val effectiveEnabled = enabled && deviceSecure

    // 冷启动即锁；开关变化时重置
    var locked by remember(effectiveEnabled) { mutableStateOf(effectiveEnabled) }
    var backgroundAt by remember { mutableLongStateOf(0L) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) locked = false
    }

    fun requestUnlock() {
        val intent = runCatching {
            keyguard?.createConfirmDeviceCredentialIntent(
                "解锁 Inkvol",
                "验证系统锁屏凭据后继续阅读",
            )
        }.getOrNull()
        if (intent == null) {
            // 设备没有可用凭据：不把用户锁死在门内
            locked = false
            return
        }
        runCatching { launcher.launch(intent) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, effectiveEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgroundAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_START -> {
                    deviceSecure = keyguard?.isDeviceSecure == true
                    val since = backgroundAt
                    if (enabled && deviceSecure && since > 0L &&
                        SystemClock.elapsedRealtime() - since >= SettingsKeys.APP_LOCK_GRACE_MS
                    ) {
                        locked = true
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(locked) {
        if (locked) requestUnlock()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (locked) {
            BackHandler(enabled = true) { }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Inkvol 已锁定",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "为保护书库与阅读记录，需要验证系统锁屏密码或指纹。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { requestUnlock() },
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("解锁")
                    }
                    TextButton(onClick = onExit) { Text("退出应用") }
                }
            }
        }
    }
}
