package com.inkvol.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * 自定义主题色（强调色）色板。
 *
 * 边界约束：只接管 Material3 的 primary 一族（primary / onPrimary /
 * primaryContainer / onPrimaryContainer / inversePrimary），背景、卡片、
 * 分隔线、正文与语义色（error / tertiary）一律不动——见 [applyAccent]。
 * 因此每个色只需要两个「主色」值（浅色档 / 深色档），其余 token 由
 * 对比度与混色规则推导；默认档 [VERMILION] 显式给死全部 token，保证
 * 升级后与旧版逐像素一致。
 */
enum class ThemeAccent(
    val id: String,
    val label: String,
    /** 浅色主题下的 primary：整体压暗一档，直接被当文字色用时（设置提示、图标 tint）才有对比度。 */
    val light: Color,
    /** 深色主题下的 primary：独立亮版，墨黑底与漫画阅读器黑底上才看得清。 */
    val dark: Color,
    private val lightOnPrimary: Color? = null,
    private val lightPrimaryContainer: Color? = null,
    private val lightOnPrimaryContainer: Color? = null,
    private val darkOnPrimary: Color? = null,
    private val darkPrimaryContainer: Color? = null,
    private val darkOnPrimaryContainer: Color? = null,
) {
    /** 默认色：全 token 显式给死 = 升级零感知。 */
    VERMILION(
        id = "vermilion",
        label = "朱红",
        light = Vermilion,
        dark = VermilionBright,
        lightOnPrimary = SurfaceLight,
        lightPrimaryContainer = LightPrimaryContainer,
        lightOnPrimaryContainer = VermilionDeep,
        darkOnPrimary = InkOnPrimary,
        darkPrimaryContainer = VermilionDeep,
        darkOnPrimaryContainer = PaperWhite,
    ),
    AMBER("amber", "琥珀", Color(0xFFA86A18), Color(0xFFE8B15C)),
    MOSS("moss", "苔绿", Color(0xFF5E6E2A), Color(0xFFA3B562)),
    PINE("pine", "松绿", Color(0xFF337E5B), Color(0xFF5FBF94)),
    CELADON("celadon", "青瓷", Color(0xFF2A7A80), Color(0xFF4FB8BD)),
    LAKE("lake", "湖蓝", Color(0xFF3868BE), Color(0xFF6E9BE8)),
    INDIGO("indigo", "靛紫", Color(0xFF6455BC), Color(0xFF9A8BE8)),
    ROSE("rose", "玫红", Color(0xFFB04572), Color(0xFFE07BA4)),
    OCHRE("ochre", "赭石", Color(0xFF8E5A38), Color(0xFFC08A5F)),
    INK_BLUE("ink_blue", "墨蓝", Color(0xFF2A4576), Color(0xFF5C82BE)),
    GRAPHITE("graphite", "石墨", Color(0xFF585A62), Color(0xFF9A9CA6)),
    SAKURA("sakura", "绯樱", Color(0xFFA94C5B), Color(0xFFE58A98)),
    ;

    /** 当前深浅档位下的主色。注意形参不能叫 dark——会遮蔽同名属性。 */
    fun primaryFor(darkTheme: Boolean): Color = if (darkTheme) dark else light

    /** 色卡显示色（顶部圆点 / 面板色卡）。 */
    fun swatchFor(darkTheme: Boolean): Color = primaryFor(darkTheme)

    /** 主色上的前景色：显式优先，否则按 WCAG 对比度在纸白/墨黑里挑。 */
    fun onPrimaryFor(darkTheme: Boolean): Color {
        val explicit = if (darkTheme) darkOnPrimary else lightOnPrimary
        return explicit ?: onColorFor(primaryFor(darkTheme))
    }

    /** 强调色容器填充：浅色=淡着色（近白），深色=压暗（近墨）。 */
    fun containerFor(darkTheme: Boolean): Color {
        val explicit = if (darkTheme) darkPrimaryContainer else lightPrimaryContainer
        return explicit ?: if (darkTheme) {
            lerp(primaryFor(true), InkSurface, 0.78f)
        } else {
            lerp(primaryFor(false), Color.White, 0.86f)
        }
    }

    /** 容器上的文字色。 */
    fun onContainerFor(darkTheme: Boolean): Color {
        val explicit = if (darkTheme) darkOnPrimaryContainer else lightOnPrimaryContainer
        return explicit ?: if (darkTheme) PaperWhite else lerp(primaryFor(false), Color.Black, 0.22f)
    }
}

/** 未知/脏值回落默认，防止旧备份或手改配置把界面打空。 */
fun themeAccentOf(id: String?): ThemeAccent =
    ThemeAccent.entries.firstOrNull { it.id == id } ?: ThemeAccent.VERMILION

/**
 * 在给定底色上挑对比度更高的前景色（纸白 or 墨黑）。
 * 同时用于 onPrimary 推导与色卡对勾着色（保证琥珀这类中性亮色上也能看见勾）。
 */
fun onColorFor(background: Color): Color {
    val l = background.luminance()
    val contrastWhite = 1.05f / (l + 0.05f)
    val contrastInk = (l + 0.05f) / (InkOnPrimary.luminance() + 0.05f)
    return if (contrastInk > contrastWhite) InkOnPrimary else Color.White
}

/**
 * 把强调色套到既有色板上：只改 primary 一族，其余 token 原样保留
 * （背景/卡片/描边/语义色一个都不动）。默认档直接原样返回。
 */
internal fun ColorScheme.applyAccent(accent: ThemeAccent, dark: Boolean): ColorScheme {
    if (accent == ThemeAccent.VERMILION) return this
    return copy(
        primary = accent.primaryFor(dark),
        onPrimary = accent.onPrimaryFor(dark),
        primaryContainer = accent.containerFor(dark),
        onPrimaryContainer = accent.onContainerFor(dark),
        // inverseSurface 上的强调色：用浅色档（深底反白时仍是可读的深色）
        inversePrimary = accent.primaryFor(false),
    )
}
