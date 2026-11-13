package com.seerflix.tv.feature.setup

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.seerflix.tv.core.model.DiscoveredServer
import com.seerflix.tv.core.network.QrCodeGenerator
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
        onJellyfinLocalUrlChange = viewModel::updateJellyfinLocalUrl,
        onSeerrUrlChange = viewModel::updateSeerrUrl,
        onSeerrLocalUrlChange = viewModel::updateSeerrLocalUrl,
        onToggleJellyfinOnly = viewModel::toggleJellyfinOnlyMode,
        onToggleSelfSigned = viewModel::toggleTrustSelfSigned,
        onScanNetwork = viewModel::scanNetwork,
        onSelectDiscovered = viewModel::selectDiscoveredServer,
        onConnectClick = { viewModel.connectAndContinue(onSetupComplete) }
    )
}

@Composable
fun SetupContent(
    state: SetupUiState,
    onJellyfinUrlChange: (String) -> Unit,
    onJellyfinLocalUrlChange: (String) -> Unit = {},
    onSeerrUrlChange: (String) -> Unit,
    onSeerrLocalUrlChange: (String) -> Unit = {},
    onToggleJellyfinOnly: () -> Unit = {},
    onToggleSelfSigned: () -> Unit = {},
    onScanNetwork: () -> Unit = {},
    onSelectDiscovered: (DiscoveredServer) -> Unit = {},
    onConnectClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val dialogCorner = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val buttonCorner = RoundedCornerShape(spec.buttonCornerRadiusDp.dp)
    val qrBitmap = remember(state.companionUrl) {
        state.companionUrl?.let { QrCodeGenerator.generate(it, 200) }
    }

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
        Row(
            modifier = Modifier
                .width(980.dp)
                .clip(dialogCorner)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogCorner)
                .padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Left Column: Server URLs & Options
            Column(
                modifier = Modifier
                    .weight(1.35f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "LUMEN",
                            color = colors.accent,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "TV Server Setup",
                            color = colors.textSecondary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    state.updateInfo?.let { upd ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.matchGreen.copy(alpha = 0.18f))
                                .border(1.dp, colors.matchGreen, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Update ${upd.latestVersion} Ready",
                                color = colors.matchGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = "Add your Jellyfin server (and optional Seerr v3+ request server). Local LAN URLs auto-failover to remote URLs when away from home.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                SetupUrlField(
                    value = state.jellyfinUrl,
                    onValueChange = onJellyfinUrlChange,
                    label = "Jellyfin Server URL (Primary / Remote)",
                    placeholder = "https://jellyfin.example.com or http://192.168.1.100:8096"
                )

                SetupUrlField(
                    value = state.jellyfinLocalUrl,
                    onValueChange = onJellyfinLocalUrlChange,
                    label = "Jellyfin Local LAN URL (Optional Dual Failover)",
                    placeholder = "http://192.168.1.100:8096"
                )

                SetupSwitchTile(
                    title = "Jellyfin-Only Mode",
                    subtitle = "Skip Seerr/Jellyseerr and browse your Jellyfin library directly",
                    checked = state.jellyfinOnlyMode,
                    onToggle = onToggleJellyfinOnly
                )

                if (!state.jellyfinOnlyMode) {
                    SetupUrlField(
                        value = state.seerrUrl,
                        onValueChange = onSeerrUrlChange,
                        label = "Seerr v3+ / Jellyseerr URL (Primary / Remote)",
                        placeholder = "http://192.168.1.100:5055"
                    )
                    SetupUrlField(
                        value = state.seerrLocalUrl,
                        onValueChange = onSeerrLocalUrlChange,
                        label = "Seerr Local LAN URL (Optional Dual Failover)",
                        placeholder = "http://192.168.1.100:5055"
                    )
                }

                SetupSwitchTile(
                    title = "Trust Self-Signed HTTPS Certificates",
                    subtitle = "Useful for homelab reverse proxies using local CA certs",
                    checked = state.trustSelfSignedCerts,
                    onToggle = onToggleSelfSigned,
                    leadingIcon = Icons.Filled.Security
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

                var btnFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
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
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text = "Connect & Continue",
                            color = colors.white,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Right Column: LAN Auto-Discovery + Mobile Companion QR
            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.badgeBorder, RoundedCornerShape(10.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.WifiFind,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "LAN Auto-Discovery",
                            color = colors.white,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ScanButton(isScanning = state.isScanning, onClick = onScanNetwork)
                }

                if (state.discoveredServers.isEmpty()) {
                    Text(
                        text = if (state.isScanning) {
                            "Broadcasting Jellyfin discovery on UDP 7359…"
                        } else {
                            "No servers auto-discovered yet. Press Scan to search your LAN."
                        },
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.discoveredServers.forEach { server ->
                            DiscoveredServerChip(
                                server = server,
                                selected = state.jellyfinUrl == server.address || state.jellyfinLocalUrl == server.address,
                                onClick = { onSelectDiscovered(server) }
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.badgeBorder)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.QrCode2,
                        contentDescription = null,
                        tint = colors.white,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Phone Companion Setup",
                        color = colors.white,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Hate typing URLs with a TV remote? Scan with your phone on the same Wi-Fi to paste URLs directly:",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.white)
                                    .padding(8.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Setup Companion QR Code",
                                    modifier = Modifier.size(144.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.companionUrl.orEmpty(),
                                color = colors.matchGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Text(
                            text = "Connect TV to Wi-Fi / Ethernet to start mobile companion web setup.",
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    val colors = SeerflixTokens.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(placeholder, fontSize = 12.sp) },
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
}

@Composable
private fun SetupSwitchTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) colors.surfaceElevated else colors.surface)
            .then(if (focused) Modifier.border(2.dp, colors.focusBorder, RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            Column {
                Text(title, color = colors.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = colors.textMuted, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedThumbColor = colors.white, checkedTrackColor = colors.accent)
        )
    }
}

@Composable
private fun ScanButton(isScanning: Boolean, onClick: () -> Unit) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) colors.surfaceElevated else colors.dialogSurface)
            .border(1.dp, if (focused) colors.focusBorder else colors.badgeBorder, RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(enabled = !isScanning, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Scan Network",
            tint = colors.white,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isScanning) "Scanning…" else "Scan",
            color = colors.white,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DiscoveredServerChip(
    server: DiscoveredServer,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected -> colors.accent.copy(alpha = 0.22f)
                    focused -> colors.surfaceElevated
                    else -> colors.dialogSurface
                }
            )
            .border(
                1.5.dp,
                when {
                    focused -> colors.focusBorder
                    selected -> colors.accent
                    else -> colors.badgeBorder
                },
                RoundedCornerShape(6.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(server.name, color = colors.white, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(server.address, color = colors.textSecondary, fontSize = 11.sp)
    }
}

