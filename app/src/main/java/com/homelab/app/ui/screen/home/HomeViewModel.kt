package com.homelab.app.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.model.Host
import com.homelab.app.data.repository.AuthRepository
import com.homelab.app.data.repository.HostRepository
import com.homelab.app.data.repository.SessionRepository
import com.homelab.app.service.HostSyncWorker
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
    val connectingHostId: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val hostRepository: HostRepository,
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository
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

    fun prepareSession(host: Host): String {
        return UUID.randomUUID().toString()
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
