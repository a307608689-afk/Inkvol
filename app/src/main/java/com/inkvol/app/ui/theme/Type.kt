package com.inkvol.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private val Base = TextStyle(
    fontFamily = FontFamily.Default,
    lineHeightStyle = TightLineHeight,
)

val InkvolTypography = Typography(
    displayLarge = Base.copy(fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = Base.copy(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    displaySmall = Base.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineLarge = Base.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineMedium = Base.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = Base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = Base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall = Base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    bodyLarge = Base.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.15.sp),
    bodyMedium = Base.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.15.sp),
    bodySmall = Base.copy(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp),
    labelLarge = Base.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = Base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = Base.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)
