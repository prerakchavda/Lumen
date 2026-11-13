package com.seerflix.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Typography
import com.seerflix.tv.core.model.UiPreset

object NetflixColors {
    val Background = Color(0xFF141414)
    val DeepBlack = Color(0xFF0A0A0A)
    val Surface = Color(0xFF1F1F1F)
    val SurfaceElevated = Color(0xFF2B2B2B)
    val NetflixRed = Color(0xFFE50914)
    val NetflixRedDark = Color(0xFFB20710)
    val White = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextMuted = Color(0xFF808080)
    val MatchGreen = Color(0xFF46D369)
    val DownloadingBlue = Color(0xFF2B8AFF)
    val PendingAmber = Color(0xFFF5A623)
    val BadgeBorder = Color(0x55FFFFFF)
    val RailBackground = Color(0xE60B0B0B)
}

@OptIn(ExperimentalTvMaterial3Api::class)
val SeerflixTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun SeerflixTheme(
    preset: UiPreset = UiPreset.NETFLIX,
    uiLanguage: String = "en",
    content: @Composable () -> Unit
) {
    SeerflixPresetTheme(preset = preset, uiLanguage = uiLanguage, content = content)
}
