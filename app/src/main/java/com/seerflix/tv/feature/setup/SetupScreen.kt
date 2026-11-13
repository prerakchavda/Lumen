package com.seerflix.tv.feature.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.seerflix.tv.ui.theme.SeerflixTokens

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    SetupContent(
        state = state,
        onJellyfinUrlChange = viewModel::updateJellyfinUrl,
        onSeerrUrlChange = viewModel::updateSeerrUrl,
        onConnectClick = { viewModel.connectAndContinue(onSetupComplete) }
    )
}

@Composable
fun SetupContent(
    state: SetupUiState,
    onJellyfinUrlChange: (String) -> Unit,
    onSeerrUrlChange: (String) -> Unit,
    onConnectClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val dialogCorner = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val buttonCorner = RoundedCornerShape(spec.buttonCornerRadiusDp.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(colors.backgroundGlow, colors.background),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(dialogCorner)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogCorner)
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SEERFLIX",
                    color = colors.accent,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Google TV Setup",
                    color = colors.textSecondary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = "Connect your Jellyfin media server and Seerr v3+ request server. Both will work together seamlessly from one unified TV interface.",
                color = colors.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            OutlinedTextField(
                value = state.jellyfinUrl,
                onValueChange = onJellyfinUrlChange,
                label = { Text("Jellyfin Server URL") },
                placeholder = { Text("http://192.168.1.100:8096") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.white,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.badgeBorder,
                    focusedLabelColor = colors.white,
                    unfocusedLabelColor = colors.textSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.seerrUrl,
                onValueChange = onSeerrUrlChange,
                label = { Text("Seerr v3+ / Jellyseerr URL") },
                placeholder = { Text("http://192.168.1.100:5055") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.white,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.badgeBorder,
                    focusedLabelColor = colors.white,
                    unfocusedLabelColor = colors.textSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            state.errorMessage?.let { err ->
                Text(
                    text = err,
                    color = colors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            state.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = colors.matchGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(4.dp))

            var btnFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(buttonCorner)
                    .background(if (btnFocused) colors.accentDark else colors.accent)
                    .then(if (btnFocused) Modifier.border(2.dp, colors.focusBorder, buttonCorner) else Modifier)
                    .onFocusChanged { btnFocused = it.isFocused }
                    .focusable()
                    .clickable(enabled = !state.isTesting, onClick = onConnectClick),
                contentAlignment = Alignment.Center
            ) {
                if (state.isTesting) {
                    CircularProgressIndicator(
                        color = colors.white,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.height(22.dp).width(22.dp)
                    )
                } else {
                    Text(
                        text = "Connect & Continue",
                        color = colors.white,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
