package com.inkvol.app.ui.legal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.ui.components.glass.GlassOverlay
import com.inkvol.app.ui.components.glass.GlassOverlayMotion
import com.inkvol.app.ui.components.glass.GlassOverlayPresence
import com.inkvol.app.ui.components.glass.LocalGlassChrome
import com.kyant.backdrop.Backdrop

/** 法律文档单一真源：应用内阅读与公开页面共用同一份内容（assets/legal 目录）。 */
object LegalDocs {
    const val POLICY_VERSION = SettingsKeys.PRIVACY_POLICY_VERSION

    /** GitHub Pages（仓库 /docs 目录）上的公开版本。 */
    const val POLICY_URL = "https://a307608689-afk.github.io/Inkvol/privacy.html"
    const val SOURCE_URL = "https://github.com/a307608689-afk/Inkvol"
    const val ISSUES_URL = "https://github.com/a307608689-afk/Inkvol/issues"

    fun read(context: Context, doc: LegalDoc): String = runCatching {
        context.assets.open(doc.asset).bufferedReader().use { it.readText() }
    }.getOrElse { "文档读取失败：${doc.asset}" }

    /** 用系统浏览器打开链接；无可用浏览器时返回 false。 */
    fun openUrl(context: Context, url: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
}

enum class LegalDoc(val title: String, val asset: String) {
    PRIVACY("隐私政策", "legal/privacy.md"),
    LICENSES("开源许可", "legal/licenses.md"),
    GPL("GPL-3.0 全文", "legal/gpl-3.0.txt"),
}

/** 法律文档阅读层：《设计规范》§5 模式 C-2（GlassOverlay BottomPanel）。 */
@Composable
fun LegalDocOverlay(
    visible: Boolean,
    doc: LegalDoc,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
    presence: GlassOverlayPresence? = null,
) {
    val context = LocalContext.current
    val text = remember(doc) { LegalDocs.read(context, doc) }
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
            .heightIn(max = 420.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Inkvol · 开源阅读器",
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MarkdownBody(text = text, plain = doc == LegalDoc.GPL)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** 极简 Markdown：标题 / 列表 / 表格 / 分隔线 / 段落。故意不引第三方渲染库。 */
@Composable
private fun MarkdownBody(text: String, plain: Boolean = false) {
    if (plain) {
        // 许可证全文必须逐字呈现：单块等宽文本，避免几百个 Text 节点拖慢滚动
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val blocks = remember(text) { parseMarkdown(text) }
    for (block in blocks) {
        when (block) {
            is MdBlock.Spacer -> Spacer(Modifier.height(6.dp))
            is MdBlock.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            is MdBlock.Heading -> Text(
                text = block.text,
                style = when (block.level) {
                    1 -> MaterialTheme.typography.titleLarge
                    2 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.titleSmall
                },
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = if (block.level == 1) 4.dp else 10.dp),
            )
            is MdBlock.Bullet -> Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(
                    text = inlineMarkup(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            is MdBlock.Table -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(8.dp),
                    ),
            ) {
                block.rows.forEachIndexed { index, cells ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    ) {
                        cells.forEachIndexed { cellIndex, cell ->
                            if (cellIndex > 0) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                            Text(
                                text = inlineMarkup(cell),
                                style = if (index == 0) {
                                    MaterialTheme.typography.labelMedium
                                } else {
                                    MaterialTheme.typography.bodySmall
                                },
                                fontWeight = if (index == 0) FontWeight.SemiBold else null,
                                color = if (index == 0) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .weight(if (cells.size == 2) (if (cellIndex == 0) 1f else 1.4f) else 1f)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            is MdBlock.Code -> Text(
                text = block.text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is MdBlock.Paragraph -> Text(
                text = inlineMarkup(block.text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private sealed interface MdBlock {
    data object Spacer : MdBlock
    data object Divider : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
}

/** 行内标记：**加粗** 与 `代码`（去掉标记符号，保留强调）。 */
private fun inlineMarkup(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(text.substring(index))
                    index = text.length
                }
            }
            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text.substring(index))
                    index = text.length
                }
            }
            else -> {
                append(text[index])
                index++
            }
        }
    }
}

private val tableSeparator = Regex("^\\|[\\s:\\-|]+\\|$")

private fun parseMarkdown(text: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = text.lines()
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trimEnd()
        when {
            line.isBlank() -> {
                out += MdBlock.Spacer
                index++
            }
            line.trim() == "---" -> {
                out += MdBlock.Divider
                index++
            }
            line.startsWith("|") -> {
                val rows = ArrayList<List<String>>()
                while (index < lines.size && lines[index].trimStart().startsWith("|")) {
                    val raw = lines[index].trim()
                    if (!tableSeparator.matches(raw)) {
                        rows += raw.trim('|').split('|').map { it.trim() }
                    }
                    index++
                }
                if (rows.isNotEmpty()) out += MdBlock.Table(rows)
            }
            line.startsWith("# ") -> {
                out += MdBlock.Heading(1, line.removePrefix("# ").trim())
                index++
            }
            line.startsWith("## ") -> {
                out += MdBlock.Heading(2, line.removePrefix("## ").trim())
                index++
            }
            line.startsWith("### ") -> {
                out += MdBlock.Heading(3, line.removePrefix("### ").trim())
                index++
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                out += MdBlock.Bullet(line.substring(2).trim())
                index++
            }
            line.startsWith("> ") -> {
                out += MdBlock.Bullet(line.removePrefix("> ").trim())
                index++
            }
            line.startsWith("    ") -> {
                out += MdBlock.Code(line.trim())
                index++
            }
            else -> {
                out += MdBlock.Paragraph(line.trim())
                index++
            }
        }
    }
    return out
}
