package com.seerflix.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.seerflix.tv.core.model.UiPreset

enum class NavLayoutStyle {
    LEFT_RAIL,
    TOP_GLASS_PILL,
    TOP_HEADER_BAR
}

enum class CardPresentationStyle {
    PORTRAIT_2_3,
    WIDESCREEN_16_9
}

enum class Top10Style {
    OUTLINED_NUMERAL,
    CORNER_PILL_BADGE,
    SAPPHIRE_BADGE
}

@Immutable
data class SeerflixColors(
    val background: Color,
    val backgroundGlow: Color,
    val deepBlack: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val dialogSurface: Color,
    val accent: Color,
    val accentDark: Color,
    val primaryButtonBg: Color,
    val primaryButtonFg: Color,
    val primaryButtonFocusBorder: Color,
    val secondaryButtonBg: Color,
    val secondaryButtonFocusedBg: Color,
    val white: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val matchGreen: Color,
    val downloadingBlue: Color,
    val pendingAmber: Color,
    val badgeBorder: Color,
    val navBarBackground: Color,
    val navBarBorder: Color,
    val focusBorder: Color,
    val progressFill: Color
)

@Immutable
data class SeerflixUiSpec(
    val preset: UiPreset,
    val navStyle: NavLayoutStyle,
    val cardStyle: CardPresentationStyle,
    val top10Style: Top10Style,
    val heroHeightDp: Int,
    val cardCornerRadiusDp: Int,
    val buttonCornerRadiusDp: Int,
    val dialogCornerRadiusDp: Int,
    val showBrandMicroMark: Boolean,
    val contentStartPaddingDp: Int
)

val NetflixPresetColors = SeerflixColors(
    background = Color(0xFF141414),
    backgroundGlow = Color(0xFF2B0609),
    deepBlack = Color(0xFF0A0A0A),
    surface = Color(0xFF1F1F1F),
    surfaceElevated = Color(0xFF2B2B2B),
    dialogSurface = Color(0xFF1A1A1A),
    accent = Color(0xFFE50914),
    accentDark = Color(0xFFB20710),
    primaryButtonBg = Color(0xFFFFFFFF),
    primaryButtonFg = Color(0xFF0A0A0A),
    primaryButtonFocusBorder = Color(0xFFE50914),
    secondaryButtonBg = Color(0x99383838),
    secondaryButtonFocusedBg = Color(0xCC555555),
    white = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFF5F5F5),
    textSecondary = Color(0xFFB3B3B3),
    textMuted = Color(0xFF808080),
    matchGreen = Color(0xFF46D369),
    downloadingBlue = Color(0xFF2B8AFF),
    pendingAmber = Color(0xFFF5A623),
    badgeBorder = Color(0x55FFFFFF),
    navBarBackground = Color(0xE60B0B0B),
    navBarBorder = Color(0x22FFFFFF),
    focusBorder = Color(0xFFFFFFFF),
    progressFill = Color(0xFFE50914)
)

val AppleTvPresetColors = SeerflixColors(
    background = Color(0xFF050507),
    backgroundGlow = Color(0xFF131824),
    deepBlack = Color(0xFF000000),
    surface = Color(0xFF141418),
    surfaceElevated = Color(0xFF222228),
    dialogSurface = Color(0xEE18181D),
    accent = Color(0xFF0A84FF),
    accentDark = Color(0xFF0060DF),
    primaryButtonBg = Color(0xFFF5F5F7),
    primaryButtonFg = Color(0xFF050507),
    primaryButtonFocusBorder = Color(0xFF0A84FF),
    secondaryButtonBg = Color(0x662C2C32),
    secondaryButtonFocusedBg = Color(0xB3464652),
    white = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFA1A1AA),
    textMuted = Color(0xFF6E6E73),
    matchGreen = Color(0xFF30D158),
    downloadingBlue = Color(0xFF0A84FF),
    pendingAmber = Color(0xFFFFD60A),
    badgeBorder = Color(0x44FFFFFF),
    navBarBackground = Color(0xCC16161B),
    navBarBorder = Color(0x38FFFFFF),
    focusBorder = Color(0xFFF5F5F7),
    progressFill = Color(0xFFF5F5F7)
)

val DisneyPlusPresetColors = SeerflixColors(
    background = Color(0xFF0B0D17),
    backgroundGlow = Color(0xFF102454),
    deepBlack = Color(0xFF06080F),
    surface = Color(0xFF141827),
    surfaceElevated = Color(0xFF1F263D),
    dialogSurface = Color(0xF0121626),
    accent = Color(0xFF0063E5),
    accentDark = Color(0xFF004BB5),
    primaryButtonBg = Color(0xFF0063E5),
    primaryButtonFg = Color(0xFFFFFFFF),
    primaryButtonFocusBorder = Color(0xFF00D2FF),
    secondaryButtonBg = Color(0x991E2438),
    secondaryButtonFocusedBg = Color(0xCC2C3654),
    white = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFF9FAFB),
    textSecondary = Color(0xFFA8B3CF),
    textMuted = Color(0xFF6C7896),
    matchGreen = Color(0xFF34D399),
    downloadingBlue = Color(0xFF00D2FF),
    pendingAmber = Color(0xFFFBBF24),
    badgeBorder = Color(0x5575A8FF),
    navBarBackground = Color(0xEB0B0E1A),
    navBarBorder = Color(0x3360A5FA),
    focusBorder = Color(0xFF67E8F9),
    progressFill = Color(0xFF00D2FF)
)

fun colorsForPreset(preset: UiPreset): SeerflixColors = when (preset) {
    UiPreset.NETFLIX -> NetflixPresetColors
    UiPreset.APPLE_TV -> AppleTvPresetColors
    UiPreset.DISNEY_PLUS -> DisneyPlusPresetColors
}

fun specForPreset(preset: UiPreset): SeerflixUiSpec = when (preset) {
    UiPreset.NETFLIX -> SeerflixUiSpec(
        preset = UiPreset.NETFLIX,
        navStyle = NavLayoutStyle.LEFT_RAIL,
        cardStyle = CardPresentationStyle.PORTRAIT_2_3,
        top10Style = Top10Style.OUTLINED_NUMERAL,
        heroHeightDp = 285,
        cardCornerRadiusDp = 6,
        buttonCornerRadiusDp = 4,
        dialogCornerRadiusDp = 10,
        showBrandMicroMark = true,
        contentStartPaddingDp = 24
    )
    UiPreset.APPLE_TV -> SeerflixUiSpec(
        preset = UiPreset.APPLE_TV,
        navStyle = NavLayoutStyle.TOP_GLASS_PILL,
        cardStyle = CardPresentationStyle.WIDESCREEN_16_9,
        top10Style = Top10Style.CORNER_PILL_BADGE,
        heroHeightDp = 360,
        cardCornerRadiusDp = 12,
        buttonCornerRadiusDp = 24,
        dialogCornerRadiusDp = 18,
        showBrandMicroMark = false,
        contentStartPaddingDp = 44
    )
    UiPreset.DISNEY_PLUS -> SeerflixUiSpec(
        preset = UiPreset.DISNEY_PLUS,
        navStyle = NavLayoutStyle.TOP_HEADER_BAR,
        cardStyle = CardPresentationStyle.PORTRAIT_2_3,
        top10Style = Top10Style.SAPPHIRE_BADGE,
        heroHeightDp = 325,
        cardCornerRadiusDp = 10,
        buttonCornerRadiusDp = 8,
        dialogCornerRadiusDp = 14,
        showBrandMicroMark = false,
        contentStartPaddingDp = 44
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
private fun tvColorSchemeFor(colors: SeerflixColors): ColorScheme = darkColorScheme(
    primary = colors.accent,
    onPrimary = colors.white,
    secondary = colors.white,
    onSecondary = colors.deepBlack,
    background = colors.background,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceElevated,
    onSurfaceVariant = colors.textSecondary
)

val LocalSeerflixColors = staticCompositionLocalOf { NetflixPresetColors }
val LocalSeerflixSpec = staticCompositionLocalOf { specForPreset(UiPreset.NETFLIX) }

object SeerflixTokens {
    val colors: SeerflixColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSeerflixColors.current

    val spec: SeerflixUiSpec
        @Composable
        @ReadOnlyComposable
        get() = LocalSeerflixSpec.current
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeerflixPresetTheme(
    preset: UiPreset = UiPreset.NETFLIX,
    content: @Composable () -> Unit
) {
    val colors = colorsForPreset(preset)
    val spec = specForPreset(preset)
    CompositionLocalProvider(
        LocalSeerflixColors provides colors,
        LocalSeerflixSpec provides spec
    ) {
        MaterialTheme(
            colorScheme = tvColorSchemeFor(colors),
            typography = SeerflixTypography,
            content = content
        )
    }
}
