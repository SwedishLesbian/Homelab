package com.homelab.app.ui.screen.keys

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homelab.app.data.model.SshKey
import com.homelab.app.ui.util.copyAndScheduleClear
import kotlinx.coroutines.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyManagementScreen(
    onBack: () -> Unit,
    viewModel: KeyManagementViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showGenerateDialog by remember { mutableStateOf(false) }
    var newKeyName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copiedKeyId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSH Keys") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showGenerateDialog = true }) {
                        Icon(Icons.Default.Add, "Generate Key")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.keys.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Key, null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No SSH keys yet. Tap + to generate one.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            items(state.keys, key = { it.id }) { key ->
                SshKeyCard(
                    key = key,
                    justCopied = copiedKeyId == key.id,
                    onCopyPublicKey = {
                        context.copyAndScheduleClear(key.publicKey, scope = scope)
                        copiedKeyId = key.id
                    },
                    onDelete = { viewModel.deleteKey(key.id) }
                )
            }
        }
    }

    if (showGenerateDialog) {
        AlertDialog(
            onDismissRequest = { showGenerateDialog = false; newKeyName = "" },
            title = { Text("Generate SSH Key") },
            text = {
                Column {
                    Text(
                        "Creates an ed25519 key stored in the Android Keystore. " +
                        "The private key never leaves your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newKeyName,
                        onValueChange = { newKeyName = it },
                        label = { Text("Key Name") },
                        placeholder = { Text("e.g. My Android Key") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKeyName.isNotBlank()) {
                            viewModel.generateKey(newKeyName.trim())
                            newKeyName = ""
                            showGenerateDialog = false
                        }
                    },
                    enabled = newKeyName.isNotBlank() && !state.isGenerating
                ) {
                    if (state.isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Generate")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateDialog = false; newKeyName = "" }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SshKeyCard(
    key: SshKey,
    justCopied: Boolean,
    onCopyPublicKey: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(key.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${key.keyType.name} • ${key.publicKey.take(28)}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onCopyPublicKey) {
                Icon(
                    if (justCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    "Copy public key",
                    tint = if (justCopied) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete \"${key.name}\"?") },
            text = { Text("This will permanently remove the key from the Keystore. Any hosts using it will need a new key.") },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDeleteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
