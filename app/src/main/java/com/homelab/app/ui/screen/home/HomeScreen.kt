package com.homelab.app.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homelab.app.data.model.Host
import com.homelab.app.ui.component.HostCard
import com.homelab.app.ui.component.StatusDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onConnectToHost: (sessionId: String, hostId: String) -> Unit,
    onNavigateToKeys: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

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
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
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
                                onConnect = { onConnectToHost(viewModel.prepareSession(host), host.id) },
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
                                onConnect = { onConnectToHost(viewModel.prepareSession(host), host.id) },
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
                                onConnect = { onConnectToHost(viewModel.prepareSession(host), host.id) },
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
                                onConnect = { onConnectToHost(viewModel.prepareSession(host), host.id) },
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
