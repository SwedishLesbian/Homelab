package com.homelab.app.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Session", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

            Spacer(Modifier.height(8.dp))

            // Session timeout
            ListItem(
                headlineContent = { Text("Session Timeout") },
                supportingContent = { Text("${state.sessionTimeoutMinutes} minutes") },
                trailingContent = {
                    Slider(
                        value = state.sessionTimeoutMinutes.toFloat(),
                        onValueChange = { viewModel.setSessionTimeout(it.toInt()) },
                        valueRange = 5f..120f,
                        steps = 23,
                        modifier = Modifier.width(120.dp)
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Security", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

            Spacer(Modifier.height(8.dp))

            // Clipboard auto-clear
            ListItem(
                headlineContent = { Text("Clipboard Auto-Clear") },
                supportingContent = { Text("Clear clipboard 30s after copying") },
                trailingContent = {
                    Switch(
                        checked = state.clipboardAutoClear,
                        onCheckedChange = viewModel::setClipboardAutoClear
                    )
                }
            )

            // Screenshot protection
            ListItem(
                headlineContent = { Text("Screenshot Protection") },
                supportingContent = { Text("Block screenshots in terminal") },
                trailingContent = {
                    Switch(
                        checked = state.screenshotProtection,
                        onCheckedChange = viewModel::setScreenshotProtection
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Logout
            ListItem(
                headlineContent = { Text("Sign Out", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Clear all tokens and session data") },
                leadingContent = {
                    Icon(Icons.Default.ExitToApp, null, tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.padding(0.dp)
            )
            TextButton(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Sign Out") }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("This will clear all tokens and SSH session data. Continue?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.logout(); onBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }
}
