package com.seerflix.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.i18n.UiStrings
import com.seerflix.tv.core.model.UiPreset
import com.seerflix.tv.ui.theme.SeerflixTokens

@Composable
fun UiPresetPickerDialog(
    profileName: String,
    currentPreset: UiPreset,
    onSelectPreset: (UiPreset) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        UiPresetPickerCard(
            profileName = profileName,
            currentPreset = currentPreset,
            onSelectPreset = onSelectPreset,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun UiPresetPickerCard(
    profileName: String,
    currentPreset: UiPreset,
    onSelectPreset: (UiPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val dialogCorner = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val selectedPresetFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { selectedPresetFocusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .width(560.dp)
            .clip(dialogCorner)
            .background(colors.dialogSurface)
            .border(1.dp, colors.badgeBorder, dialogCorner)
            .padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.2f))
                    .border(1.dp, colors.accent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = strings.presetPickerTitle,
                    color = colors.white,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.presetPickerSubtitle.format(profileName),
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        UiPreset.entries.forEach { preset ->
            UiPresetOptionCard(
                preset = preset,
                selected = preset == currentPreset,
                onClick = { onSelectPreset(preset) },
                modifier = if (preset == currentPreset) Modifier.focusRequester(selectedPresetFocusRequester) else Modifier
            )
        }

        Spacer(Modifier.height(2.dp))

        var closeFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(spec.buttonCornerRadiusDp.dp))
                .background(if (closeFocused) colors.secondaryButtonFocusedBg else colors.surfaceElevated)
                .then(
                    if (closeFocused) Modifier.border(2.dp, colors.focusBorder, RoundedCornerShape(spec.buttonCornerRadiusDp.dp))
                    else Modifier
                )
                .onFocusChanged { closeFocused = it.isFocused || it.hasFocus }
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = strings.commonClose,
                color = colors.white,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun UiPresetOptionCard(
    preset: UiPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val strings = AppStrings.current
    var focused by remember { mutableStateOf(false) }
    val corner = RoundedCornerShape(10.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(corner)
            .background(
                when {
                    focused -> colors.surfaceElevated
                    selected -> colors.surface.copy(alpha = 0.95f)
                    else -> colors.surface.copy(alpha = 0.65f)
                }
            )
            .then(
                when {
                    focused -> Modifier.border(2.dp, colors.focusBorder, corner)
                    selected -> Modifier.border(1.5.dp, colors.accent, corner)
                    else -> Modifier.border(1.dp, colors.badgeBorder.copy(alpha = 0.3f), corner)
                }
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        UiPresetMiniPreview(preset = preset)

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = preset.displayName,
                    color = colors.white,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (selected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accent.copy(alpha = 0.22f))
                            .border(1.dp, colors.accent.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = strings.commonActive,
                            color = colors.white,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = preset.localizedSubtitle(strings),
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun UiPresetMiniPreview(preset: UiPreset) {
    val bg = Color(preset.backgroundHex)
    val accent = Color(preset.accentHex)
    val secondary = Color(preset.secondaryAccentHex)

    Box(
        modifier = Modifier
            .width(86.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(6.dp))
            .padding(5.dp)
    ) {
        when (preset) {
            UiPreset.NETFLIX -> {
                Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF0A0A0A))
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp, start = 2.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent.copy(alpha = 0.28f))
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(17.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF2B2B2B))
                                )
                            }
                        }
                    }
                }
            }
            UiPreset.APPLE_TV -> {
                Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(46.dp)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x55FFFFFF))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(secondary.copy(alpha = 0.25f))
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(13.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF24242A))
                            )
                        }
                    }
                }
            }
            UiPreset.DISNEY_PLUS -> {
                Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent)
                        )
                        Box(
                            modifier = Modifier
                                .width(26.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(secondary.copy(alpha = 0.7f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(17.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent.copy(alpha = 0.35f))
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(15.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF1F263D))
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun UiPreset.localizedSubtitle(s: UiStrings): String = when (this) {
    UiPreset.NETFLIX -> s.presetNetflixDesc
    UiPreset.APPLE_TV -> s.presetAppleTvDesc
    UiPreset.DISNEY_PLUS -> s.presetDisneyDesc
}
