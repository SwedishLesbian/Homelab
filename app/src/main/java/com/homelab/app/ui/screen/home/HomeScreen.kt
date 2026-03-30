package com.homelab.app.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homelab.app.data.tailscale.TailscaleState
import com.homelab.app.ssh.AuthMethod
import com.homelab.app.ui.component.HostCard
import com.homelab.app.ui.theme.HomelabWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onConnectToHost: (sessionId: String, hostId: String) -> Unit,
    onNavigateToKeys: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(Unit) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(state.isRefreshing) {
        if (!state.isRefreshing) {
            pullRefreshState.endRefresh()
        }
    }

    // Pre-connect bottom sheet
    if (state.pendingConnectHost != null) {
        ConnectBottomSheet(
            state = state,
            onDismiss = viewModel::onSheetDismissed,
            onUsernameChanged = viewModel::onSheetUsernameChanged,
            onAuthMethodChanged = viewModel::onSheetAuthMethodChanged,
            onKeySelected = viewModel::onSheetKeySelected,
            onPasswordChanged = viewModel::onSheetPasswordChanged,
            onDeployKeyChanged = viewModel::onSheetDeployKeyChanged,
            onConnect = {
                viewModel.confirmConnect()?.let { (sessionId, hostId) ->
                    onConnectToHost(sessionId, hostId)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Homelab") },
                actions = {
                    IconButton(onClick = onNavigateToKeys) {
                        Icon(Icons.Default.Key, "SSH Keys")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Tailscale banner
                if (state.tailscaleState != TailscaleState.CONNECTED) {
                    item {
                        TailscaleBanner(
                            tailscaleState = state.tailscaleState,
                            onConnect = viewModel::connectTailscale,
                            onOpenApp = viewModel::openTailscaleApp,
                            onGetTailscale = viewModel::getTailscalePlayStoreIntent
                        )
                    }
                }

                // Search
                item {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        placeholder = { Text("Search hosts...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Clear, "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Search results
                val displayHosts = state.searchResults
                if (displayHosts != null) {
                    if (displayHosts.isEmpty()) {
                        item { NoHostsPlaceholder("No hosts match your search") }
                    } else {
                        items(displayHosts, key = { it.id }) { host ->
                            HostCard(
                                host = host,
                                onConnect = { viewModel.onConnectTapped(host) },
                                onFavoriteToggle = { viewModel.toggleFavorite(host) }
                            )
                        }
                    }
                } else {
                    // Favorites
                    if (state.favorites.isNotEmpty()) {
                        item { SectionHeader("Favorites") }
                        items(state.favorites, key = { "fav_${it.id}" }) { host ->
                            HostCard(
                                host = host,
                                onConnect = { viewModel.onConnectTapped(host) },
                                onFavoriteToggle = { viewModel.toggleFavorite(host) }
                            )
                        }
                    }

                    // Recents
                    if (state.recents.isNotEmpty()) {
                        item { SectionHeader("Recent") }
                        items(state.recents, key = { "rec_${it.id}" }) { host ->
                            HostCard(
                                host = host,
                                onConnect = { viewModel.onConnectTapped(host) },
                                onFavoriteToggle = { viewModel.toggleFavorite(host) }
                            )
                        }
                    }

                    // All hosts
                    item { SectionHeader("All Hosts (${state.allHosts.size})") }
                    if (state.allHosts.isEmpty()) {
                        item { NoHostsPlaceholder("No hosts found. Pull down to refresh.") }
                    } else {
                        items(state.allHosts, key = { it.id }) { host ->
                            HostCard(
                                host = host,
                                onConnect = { viewModel.onConnectTapped(host) },
                                onFavoriteToggle = { viewModel.toggleFavorite(host) }
                            )
                        }
                    }
                }

                state.error?.let { error ->
                    item {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun TailscaleBanner(
    tailscaleState: TailscaleState,
    onConnect: () -> Unit,
    onOpenApp: () -> Unit,
    onGetTailscale: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = HomelabWarning.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = HomelabWarning,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))

            when (tailscaleState) {
                TailscaleState.NOT_INSTALLED -> {
                    Text(
                        "Tailscale is not installed",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onGetTailscale) {
                        Text("Get Tailscale")
                    }
                }
                TailscaleState.DISCONNECTED -> {
                    Text(
                        "Tailscale not connected",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onConnect) {
                        Text("Connect")
                    }
                    TextButton(onClick = onOpenApp) {
                        Text("Open App")
                    }
                }
                TailscaleState.CONNECTED -> { /* no banner */ }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectBottomSheet(
    state: HomeState,
    onDismiss: () -> Unit,
    onUsernameChanged: (String) -> Unit,
    onAuthMethodChanged: (AuthMethod) -> Unit,
    onKeySelected: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onDeployKeyChanged: (Boolean) -> Unit,
    onConnect: () -> Unit
) {
    val host = state.pendingConnectHost ?: return
    var passwordVisible by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Connect to ${host.name}",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = host.ip,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            // Username
            OutlinedTextField(
                value = state.sheetUsername,
                onValueChange = onUsernameChanged,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Auth method selector
            Text(
                "Authentication",
                style = MaterialTheme.typography.labelLarge
            )
            Column(Modifier.selectableGroup()) {
                AuthMethod.entries.forEach { method ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.sheetAuthMethod == method,
                                onClick = { onAuthMethodChanged(method) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.sheetAuthMethod == method,
                            onClick = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (method) {
                                AuthMethod.TAILSCALE_SSH -> "Tailscale SSH"
                                AuthMethod.SSH_KEY -> "SSH Key"
                                AuthMethod.PASSWORD -> "Password"
                            }
                        )
                    }
                }
            }

            // SSH Key picker
            if (state.sheetAuthMethod == AuthMethod.SSH_KEY) {
                if (state.availableKeys.isEmpty()) {
                    Text(
                        "No SSH keys found. Generate one in SSH Keys.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    var keyDropdownExpanded by remember { mutableStateOf(false) }
                    val selectedKey = state.availableKeys.find { it.id == state.sheetSelectedKeyId }

                    ExposedDropdownMenuBox(
                        expanded = keyDropdownExpanded,
                        onExpandedChange = { keyDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedKey?.name ?: "Select a key",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = keyDropdownExpanded,
                            onDismissRequest = { keyDropdownExpanded = false }
                        ) {
                            state.availableKeys.forEach { key ->
                                DropdownMenuItem(
                                    text = { Text(key.name) },
                                    onClick = {
                                        onKeySelected(key.id)
                                        keyDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Password field
            if (state.sheetAuthMethod == AuthMethod.PASSWORD) {
                OutlinedTextField(
                    value = state.sheetPassword,
                    onValueChange = onPasswordChanged,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide" else "Show"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                // Deploy key option
                if (state.availableKeys.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = state.sheetDeployKey,
                            onCheckedChange = onDeployKeyChanged
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Deploy SSH public key after connecting",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (state.sheetDeployKey) {
                        var keyDropdownExpanded by remember { mutableStateOf(false) }
                        val selectedKey = state.availableKeys.find { it.id == state.sheetSelectedKeyId }

                        ExposedDropdownMenuBox(
                            expanded = keyDropdownExpanded,
                            onExpandedChange = { keyDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedKey?.name ?: "Select key to deploy",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Key to deploy") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = keyDropdownExpanded,
                                onDismissRequest = { keyDropdownExpanded = false }
                            ) {
                                state.availableKeys.forEach { key ->
                                    DropdownMenuItem(
                                        text = { Text(key.name) },
                                        onClick = {
                                            onKeySelected(key.id)
                                            keyDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Connect button
            val canConnect = when (state.sheetAuthMethod) {
                AuthMethod.TAILSCALE_SSH -> state.sheetUsername.isNotBlank()
                AuthMethod.SSH_KEY -> state.sheetUsername.isNotBlank() && state.sheetSelectedKeyId != null
                AuthMethod.PASSWORD -> state.sheetUsername.isNotBlank() && state.sheetPassword.isNotBlank()
            }

            Button(
                onClick = onConnect,
                enabled = canConnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Terminal, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Connect")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun NoHostsPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}
