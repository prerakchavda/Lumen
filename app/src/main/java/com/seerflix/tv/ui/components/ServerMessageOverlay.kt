package com.seerflix.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seerflix.tv.ui.theme.SeerflixTokens
import kotlinx.coroutines.delay

/** A message sent from the Jellyfin dashboard or another app ("Send message" on this session). */
@Composable
fun ServerMessageOverlay(
    header: String,
    text: String,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    timeoutMs: Long = 6_000L
) {
    val colors = SeerflixTokens.colors
    LaunchedEffect(header, text) {
        delay(timeoutMs)
        onTimeout()
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .padding(top = 32.dp)
            .widthIn(max = 640.dp)
            .background(colors.dialogSurface.copy(alpha = 0.96f), shape)
            .border(1.dp, colors.badgeBorder, shape)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (header.isNotBlank()) {
            Text(header, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, color = colors.textSecondary, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
