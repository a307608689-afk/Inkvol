package com.inkvol.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkvol.app.ui.components.glass.GlassDimens
import com.inkvol.app.ui.components.glass.GlassEdge
import com.inkvol.app.ui.components.glass.GlassRounded
import com.inkvol.app.ui.components.glass.GlassTier
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * 应用内玻璃通知：
 * 替代系统 Toast（无法定制材质），统一走液态玻璃胶囊，2.6 秒自动消失，可点击关闭。
 */
class GlassNoticeBus {

    data class Notice(val id: Long, val message: String)

    private val nextId = AtomicLong()
    private val _notice = MutableStateFlow<Notice?>(null)
    val notice = _notice.asStateFlow()

    fun show(message: String) {
        _notice.value = Notice(nextId.incrementAndGet(), message)
    }

    fun dismiss(id: Long) {
        if (_notice.value?.id == id) {
            _notice.value = null
        }
    }
}

val LocalGlassNotice = staticCompositionLocalOf { GlassNoticeBus() }

@Composable
fun GlassNoticeHost(
    bus: GlassNoticeBus,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val notice by bus.notice.collectAsStateWithLifecycle()
    var lastMessage by remember { mutableStateOf("") }
    LaunchedEffect(notice) {
        notice?.let { lastMessage = it.message }
    }
    LaunchedEffect(notice?.id) {
        val id = notice?.id ?: return@LaunchedEffect
        delay(2600)
        bus.dismiss(id)
    }

    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn() + slideInVertically { it / 3 },
        exit = fadeOut() + slideOutVertically { it / 3 },
        modifier = modifier,
    ) {
        GlassRounded(
            backdrop = backdrop,
            tier = GlassTier.Full,
            radius = 22.dp,
            blurRadius = GlassDimens.StrongBlur,
            edge = GlassEdge.Outline,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clickable { notice?.let { bus.dismiss(it.id) } },
        ) {
            Text(
                text = lastMessage,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
