package com.inkvol.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val InkOnPrimary = Color(0xFF0B0B0D)
private val LightPrimaryContainer = Color(0xFFF2DED9)
private val LightSurfaceHigh = Color(0xFFE6E1D8)
private val LightSurfaceHighest = Color(0xFFDED8CE)
private val LightOutlineVariant = Color(0xFFEDE8E1)

private val DarkColors = darkColorScheme(
    primary = VermilionBright,
    onPrimary = InkOnPrimary,
    primaryContainer = VermilionDeep,
    onPrimaryContainer = PaperWhite,
    secondary = PaperMuted,
    onSecondary = InkBlack,
    secondaryContainer = InkSurfaceHigh,
    onSecondaryContainer = PaperWhite,
    tertiary = InfoBlue,
    onTertiary = InkBlack,
    background = InkBlack,
    onBackground = PaperWhite,
    surface = InkSurface,
    onSurface = PaperWhite,
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = PaperMuted,
    surfaceContainerLowest = InkBlack,
    surfaceContainerLow = InkSurface,
    surfaceContainer = InkSurfaceHigh,
    surfaceContainerHigh = InkSurfaceHigher,
    surfaceContainerHighest = InkSurfaceHigher,
    outline = InkOutline,
    outlineVariant = InkOutlineSoft,
    error = DangerRed,
    onError = PaperWhite,
    inverseSurface = PaperWhite,
    inverseOnSurface = InkBlack,
    inversePrimary = VermilionDeep,
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = Vermilion,
    onPrimary = SurfaceLight,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = VermilionDeep,
    secondary = PaperDim,
    onSecondary = SurfaceLight,
    secondaryContainer = SurfaceLightHigh,
    onSecondaryContainer = OnLight,
    tertiary = InfoBlue,
    onTertiary = SurfaceLight,
    background = CanvasLight,
    onBackground = OnLight,
    surface = SurfaceLight,
    onSurface = OnLight,
    surfaceVariant = SurfaceLightHigh,
    onSurfaceVariant = PaperDim,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = CanvasLight,
    surfaceContainer = SurfaceLightHigh,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    outline = OutlineLight,
    outlineVariant = LightOutlineVariant,
    error = DangerRed,
    onError = SurfaceLight,
)

/**
 * 智能书夹胶囊卡固定色板：不随动态取色，恒等于默认色板（LightColors/DarkColors）
 * 同名 token 的值；引用同一批常量，动态取色关闭时与现状逐像素一致。
 */
data class FixedFolderColors(
    val card: Color,
    val outline: Color,
    val onCard: Color,
    val onCardMuted: Color,
    val badge: Color,
)

val LocalFixedFolderColors = staticCompositionLocalOf<FixedFolderColors> {
    error("FixedFolderColors not provided")
}

@Composable
fun InkvolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamic.withBrandCanvas(darkTheme)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    // 智能书夹胶囊卡固定色：无论动态取色开关与否，恒取默认色板同名 token 值（仅分深浅）。
    val fixedFolder = if (darkTheme) {
        FixedFolderColors(
            card = InkSurfaceHigher,       // DarkColors.surfaceContainerHigh
            outline = InkOutlineSoft,      // DarkColors.outlineVariant
            onCard = PaperWhite,           // DarkColors.onSurface
            onCardMuted = PaperMuted,      // DarkColors.onSurfaceVariant
            badge = InkOutline,            // 深色 High==Highest 同值，徽章取亮一档的中性层
        )
    } else {
        FixedFolderColors(
            card = LightSurfaceHigh,       // LightColors.surfaceContainerHigh
            outline = LightOutlineVariant, // LightColors.outlineVariant
            onCard = OnLight,              // LightColors.onSurface
            onCardMuted = PaperDim,        // LightColors.onSurfaceVariant
            badge = LightSurfaceHighest,   // LightColors.surfaceContainerHighest
        )
    }

    CompositionLocalProvider(LocalFixedFolderColors provides fixedFolder) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = InkvolTypography,
            shapes = InkvolShapes,
            content = content,
        )
    }
}

/** 响应式断点：≥600dp 视为宽屏（侧栏导航、详情页双栏等），全站唯一来源。 */
object Breakpoints {
    val medium = 600.dp
}

/**
 * 动态取色只接管强调色，背景与层次仍保持品牌墨色，避免整屏被壁纸主色冲淡。
 */
private fun ColorScheme.withBrandCanvas(dark: Boolean): ColorScheme = if (dark) {
    copy(
        background = InkBlack,
        onBackground = PaperWhite,
        surface = InkSurface,
        onSurface = PaperWhite,
        surfaceContainerLowest = InkBlack,
        surfaceContainerLow = InkSurface,
        surfaceContainer = InkSurfaceHigh,
        surfaceContainerHigh = InkSurfaceHigher,
        surfaceContainerHighest = InkSurfaceHigher,
        surfaceVariant = InkSurfaceHigh,
        onSurfaceVariant = PaperMuted,
        outline = InkOutline,
        outlineVariant = InkOutlineSoft,
    )
} else {
    copy(
        background = CanvasLight,
        onBackground = OnLight,
        surface = SurfaceLight,
        onSurface = OnLight,
    )
}
