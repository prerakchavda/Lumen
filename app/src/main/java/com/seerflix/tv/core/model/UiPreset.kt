package com.seerflix.tv.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class UiPreset(
    val displayName: String,
    val shortLabel: String,
    val subtitle: String,
    val accentHex: Long,
    val secondaryAccentHex: Long,
    val backgroundHex: Long
) {
    NETFLIX(
        displayName = "Netflix",
        shortLabel = "Netflix",
        subtitle = "Left icon rail • Vertical posters • Top 10 numerals • Crimson accent",
        accentHex = 0xFFE50914L,
        secondaryAccentHex = 0xFFFFFFFFL,
        backgroundHex = 0xFF141414L
    ),
    APPLE_TV(
        displayName = "Apple TV+",
        shortLabel = "Apple TV+",
        subtitle = "Floating glass bar • Tall full-bleed hero • 16:9 widescreen cards",
        accentHex = 0xFFFFFFFFL,
        secondaryAccentHex = 0xFF0A84FFL,
        backgroundHex = 0xFF050507L
    ),
    DISNEY_PLUS(
        displayName = "Disney+ / Max",
        shortLabel = "Disney+",
        subtitle = "Top header bar • Midnight navy canvas • Electric blue glow",
        accentHex = 0xFF0063E5L,
        secondaryAccentHex = 0xFF00D2FFL,
        backgroundHex = 0xFF0B0D17L
    )
}
