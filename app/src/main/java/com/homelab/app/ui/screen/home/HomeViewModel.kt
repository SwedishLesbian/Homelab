package com.homelab.app.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.model.Host
import com.homelab.app.data.model.SshKey
import com.homelab.app.data.repository.AuthRepository
import com.homelab.app.data.repository.HostRepository
import com.homelab.app.data.repository.SessionRepository
import com.homelab.app.data.repository.SshKeyRepository
import com.homelab.app.data.tailscale.TailscaleState
import com.homelab.app.data.tailscale.TailscaleVpnManager
import com.homelab.app.ssh.AuthMethod
import com.homelab.app.ssh.AuthParams
import com.homelab.app.ssh.AuthParamsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class HomeState(
    val favorites: List<Host> = emptyList(),
    val recents: List<Host> = emptyList(),
    val allHosts: List<Host> = emptyList(),
    val searchResults: List<Host>? = null,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val tailscaleState: TailscaleState = TailscaleState.DISCONNECTED,
    val availableKeys: List<SshKey> = emptyList(),
    val pendingConnectHost: Host? = null,
    val sheetUsername: String = "root",
    val sheetAuthMethod: AuthMethod = AuthMethod.TAILSCALE_SSH,
    val sheetSelectedKeyId: String? = null,
    val sheetPassword: String = "",
    val sheetDeployKey: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val hostRepository: HostRepository,
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val sshKeyRepository: SshKeyRepository,
    private val tailscaleVpnManager: TailscaleVpnManager,
    private val authParamsStore: AuthParamsStore
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            combine(
                hostRepository.getFavorites(),
                hostRepository.getRecents(),
                hostRepository.getAllHosts()
            ) { favorites, recents, all ->
                Triple(favorites, recents, all)
            }.collect { (favorites, recents, all) ->
                _state.update { it.copy(favorites = favorites, recents = recents, allHosts = all) }
            }
        }

        viewModelScope.launch {
            _searchQuery.debounce(300)
                .flatMapLatest { query ->
                    if (query.isBlank()) flowOf(null)
                    else hostRepository.searchHosts(query)
                }
                .collect { results ->
                    _state.update { it.copy(searchResults = results) }
                }
        }

        viewModelScope.launch {
            tailscaleVpnManager.state.collect { tsState ->
                val prev = _state.value.tailscaleState
                _state.update { it.copy(tailscaleState = tsState) }
                // Auto-refresh hosts when Tailscale connects
                if (prev != TailscaleState.CONNECTED && tsState == TailscaleState.CONNECTED) {
                    refresh()
                }
            }
        }

        viewModelScope.launch {
            sshKeyRepository.getAllKeys().collect { keys ->
                _state.update { it.copy(availableKeys = keys) }
            }
        }

        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            hostRepository.refreshHosts()
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _state.update { it.copy(searchQuery = query) }
    }

    fun toggleFavorite(host: Host) {
        viewModelScope.launch {
            hostRepository.setFavorite(host.id, !host.isFavorite)
        }
    }

    // --- Bottom sheet ---

    fun onConnectTapped(host: Host) {
        _state.update {
            it.copy(
                pendingConnectHost = host,
                sheetUsername = host.sshUsername ?: "root",
                sheetAuthMethod = AuthMethod.TAILSCALE_SSH,
                sheetSelectedKeyId = host.sshKeyId ?: it.availableKeys.firstOrNull()?.id,
                sheetPassword = "",
                sheetDeployKey = false
            )
        }
    }

    fun onSheetDismissed() {
        _state.update { it.copy(pendingConnectHost = null) }
    }

    fun onSheetUsernameChanged(value: String) {
        _state.update { it.copy(sheetUsername = value) }
    }

    fun onSheetAuthMethodChanged(method: AuthMethod) {
        _state.update { it.copy(sheetAuthMethod = method) }
    }

    fun onSheetKeySelected(keyId: String) {
        _state.update { it.copy(sheetSelectedKeyId = keyId) }
    }

    fun onSheetPasswordChanged(value: String) {
        _state.update { it.copy(sheetPassword = value) }
    }

    fun onSheetDeployKeyChanged(deploy: Boolean) {
        _state.update { it.copy(sheetDeployKey = deploy) }
    }

    fun confirmConnect(): Pair<String, String>? {
        val host = _state.value.pendingConnectHost ?: return null
        val sessionId = UUID.randomUUID().toString()

        val authParams = AuthParams(
            username = _state.value.sheetUsername,
            authMethod = _state.value.sheetAuthMethod,
            keyId = if (_state.value.sheetAuthMethod == AuthMethod.SSH_KEY) _state.value.sheetSelectedKeyId else null,
            password = if (_state.value.sheetAuthMethod == AuthMethod.PASSWORD) _state.value.sheetPassword else null,
            deployKeyId = if (_state.value.sheetAuthMethod == AuthMethod.PASSWORD && _state.value.sheetDeployKey)
                _state.value.sheetSelectedKeyId else null
        )

        authParamsStore.put(sessionId, authParams)

        // Persist username and key for next time
        viewModelScope.launch {
            hostRepository.updateSshConfig(
                host.id,
                authParams.username,
                authParams.keyId
            )
        }

        _state.update { it.copy(pendingConnectHost = null) }
        return Pair(sessionId, host.id)
    }

    // --- Tailscale controls ---

    fun connectTailscale() {
        tailscaleVpnManager.connect()
    }

    fun openTailscaleApp() {
        tailscaleVpnManager.openApp()
    }

    fun getTailscalePlayStoreIntent() {
        tailscaleVpnManager.openPlayStore()
    }

    fun prepareSession(host: Host): String {
        return UUID.randomUUID().toString()
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
