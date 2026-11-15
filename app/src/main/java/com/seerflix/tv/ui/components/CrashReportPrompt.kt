package com.seerflix.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.ui.theme.SeerflixTokens
import kotlinx.coroutines.delay

/** Shown once after a crash: store the report on the user's own Jellyfin server, or drop it. */
@Composable
fun CrashReportPrompt(onSend: () -> Unit, onDismiss: () -> Unit) {
    val colors = SeerflixTokens.colors
    val strings = AppStrings.current
    val sendFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80L)
        runCatching { sendFocus.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(SeerflixTokens.spec.dialogCornerRadiusDp.dp)
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .background(colors.dialogSurface, shape)
                .padding(28.dp)
        ) {
            Text(strings.crashPromptTitle, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(strings.crashPromptBody, color = colors.textSecondary, fontSize = 16.sp)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PromptButton(strings.crashPromptSend, primary = true, onClick = onSend, modifier = Modifier.focusRequester(sendFocus))
                PromptButton(strings.crashPromptDismiss, primary = false, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun PromptButton(label: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SeerflixTokens.colors
    val shape = RoundedCornerShape(SeerflixTokens.spec.buttonCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .background(
                when {
                    focused -> colors.white
                    primary -> colors.primaryButtonBg
                    else -> colors.secondaryButtonBg
                },
                shape
            )
            .border(2.dp, if (focused) colors.primaryButtonFocusBorder else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp)
    ) {
        Text(
            label,
            color = if (focused) colors.deepBlack else if (primary) colors.primaryButtonFg else colors.white,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
