package com.seerflix.tv.feature.profiles

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.ui.theme.NetflixColors

@Composable
fun ProfilePickerScreen(
    onProfileSelected: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfilePickerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    ProfilePickerContent(
        state = state,
        onOpenSettings = onOpenSettings,
        onSelectProfile = { profile -> viewModel.selectProfile(profile, onProfileSelected) },
        onOpenAddProfile = { viewModel.openAddProfile(onProfileSelected) },
        onCloseAuthDialog = viewModel::closeAuthDialog,
        onUpdateUsername = viewModel::updateUsername,
        onUpdatePassword = viewModel::updatePassword,
        onSignInWithPassword = { viewModel.signInWithPassword(onProfileSelected) },
        onToggleAuthMode = { usePassword -> viewModel.toggleAuthMode(usePassword, onProfileSelected) }
    )
}

@Composable
fun ProfilePickerContent(
    state: ProfilePickerUiState,
    onOpenSettings: () -> Unit,
    onSelectProfile: (UserProfile) -> Unit,
    onOpenAddProfile: () -> Unit,
    onCloseAuthDialog: () -> Unit,
    onUpdateUsername: (String) -> Unit,
    onUpdatePassword: (String) -> Unit,
    onSignInWithPassword: () -> Unit,
    onToggleAuthMode: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixColors.Background)
    ) {
        // Top-right Server Settings button
        var settingsFocused by remember { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (settingsFocused) Color(0xFF333333) else Color(0xFF1F1F1F))
                .then(if (settingsFocused) Modifier.border(2.dp, NetflixColors.White, RoundedCornerShape(6.dp)) else Modifier)
                .onFocusChanged { settingsFocused = it.isFocused }
                .focusable()
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Server Settings", tint = NetflixColors.TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Servers", color = NetflixColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Who's watching?",
                color = NetflixColors.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(40.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.profiles.forEach { profile ->
                    NetflixAvatarTile(
                        profile = profile,
                        onClick = { onSelectProfile(profile) }
                    )
                }

                AddProfileTile(
                    onClick = onOpenAddProfile
                )
            }
        }
    }

    if (state.showAuthDialog) {
        Dialog(onDismissRequest = onCloseAuthDialog) {
            Column(
                modifier = Modifier
                    .width(480.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF181818))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (state.usePasswordMode) "Sign In with Password" else "Quick Connect",
                    color = NetflixColors.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                if (!state.usePasswordMode) {
                    Text(
                        text = "Open Jellyfin or Seerr on your phone/computer → Quick Connect, and enter this code:",
                        color = NetflixColors.TextSecondary,
                        fontSize = 14.sp
                    )

                    if (state.quickConnectCode != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF262626))
                                .border(1.dp, NetflixColors.NetflixRed, RoundedCornerShape(8.dp))
                                .padding(horizontal = 32.dp, vertical = 18.dp)
                        ) {
                            Text(
                                text = state.quickConnectCode!!,
                                color = NetflixColors.White,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 8.sp
                            )
                        }
                        Text(
                            text = "Waiting for approval…",
                            color = NetflixColors.MatchGreen,
                            fontSize = 13.sp
                        )
                    } else if (state.isAuthenticating) {
                        CircularProgressIndicator(color = NetflixColors.NetflixRed)
                    }
                } else {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = onUpdateUsername,
                        label = { Text("Jellyfin Username") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NetflixColors.White,
                            unfocusedTextColor = NetflixColors.TextPrimary,
                            focusedBorderColor = NetflixColors.NetflixRed,
                            unfocusedBorderColor = Color(0x55FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onUpdatePassword,
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NetflixColors.White,
                            unfocusedTextColor = NetflixColors.TextPrimary,
                            focusedBorderColor = NetflixColors.NetflixRed,
                            unfocusedBorderColor = Color(0x55FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DialogButton(
                        text = if (state.isAuthenticating) "Signing in..." else "Sign In",
                        isPrimary = true,
                        onClick = onSignInWithPassword,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                state.errorMessage?.let { err ->
                    Text(text = err, color = NetflixColors.NetflixRed, fontSize = 13.sp)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DialogButton(
                        text = if (state.usePasswordMode) "Use Quick Connect" else "Use Password",
                        isPrimary = false,
                        onClick = { onToggleAuthMode(!state.usePasswordMode) },
                        modifier = Modifier.weight(1f)
                    )
                    DialogButton(
                        text = "Cancel",
                        isPrimary = false,
                        onClick = onCloseAuthDialog,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NetflixAvatarTile(
    profile: UserProfile,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "avatarScale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(profile.avatarColorHex))
                .then(
                    if (focused) Modifier.border(3.dp, NetflixColors.White, RoundedCornerShape(8.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!profile.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = profile.name.take(1).uppercase(),
                    color = NetflixColors.White,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = profile.name,
            color = if (focused) NetflixColors.White else NetflixColors.TextSecondary,
            fontSize = 16.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun AddProfileTile(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "addProfileScale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (focused) Color(0xFF3A3A3A) else Color(0xFF242424))
                .then(
                    if (focused) Modifier.border(3.dp, NetflixColors.White, RoundedCornerShape(8.dp))
                    else Modifier.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add Profile",
                tint = NetflixColors.TextSecondary,
                modifier = Modifier.size(52.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Add Profile",
            color = if (focused) NetflixColors.White else NetflixColors.TextSecondary,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun DialogButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isPrimary -> NetflixColors.NetflixRed
                    focused -> Color(0xFF3E3E3E)
                    else -> Color(0xFF2B2B2B)
                }
            )
            .then(if (focused) Modifier.border(2.dp, NetflixColors.White, RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = NetflixColors.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
