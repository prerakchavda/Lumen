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
import com.seerflix.tv.ui.theme.NetflixColors

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2B0609), NetflixColors.Background),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xE61A1A1A))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SEERFLIX",
                    color = NetflixColors.NetflixRed,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Google TV Setup",
                    color = NetflixColors.TextSecondary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = "Connect your Jellyfin media server and Seerr v3+ request server. Both will work together seamlessly from one Netflix-style interface.",
                color = NetflixColors.TextSecondary,
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
                    focusedTextColor = NetflixColors.White,
                    unfocusedTextColor = NetflixColors.TextPrimary,
                    focusedBorderColor = NetflixColors.NetflixRed,
                    unfocusedBorderColor = Color(0x55FFFFFF),
                    focusedLabelColor = NetflixColors.White,
                    unfocusedLabelColor = NetflixColors.TextSecondary
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
                    focusedTextColor = NetflixColors.White,
                    unfocusedTextColor = NetflixColors.TextPrimary,
                    focusedBorderColor = NetflixColors.NetflixRed,
                    unfocusedBorderColor = Color(0x55FFFFFF),
                    focusedLabelColor = NetflixColors.White,
                    unfocusedLabelColor = NetflixColors.TextSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            state.errorMessage?.let { err ->
                Text(
                    text = err,
                    color = NetflixColors.NetflixRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            state.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = NetflixColors.MatchGreen,
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
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (btnFocused) Color(0xFFFF1F2B) else NetflixColors.NetflixRed)
                    .then(if (btnFocused) Modifier.border(2.dp, NetflixColors.White, RoundedCornerShape(6.dp)) else Modifier)
                    .onFocusChanged { btnFocused = it.isFocused }
                    .focusable()
                    .clickable(enabled = !state.isTesting, onClick = onConnectClick),
                contentAlignment = Alignment.Center
            ) {
                if (state.isTesting) {
                    CircularProgressIndicator(
                        color = NetflixColors.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.height(22.dp).width(22.dp)
                    )
                } else {
                    Text(
                        text = "Connect & Continue",
                        color = NetflixColors.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
