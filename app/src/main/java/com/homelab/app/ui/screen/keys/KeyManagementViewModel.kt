package com.homelab.app.ui.screen.keys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.model.SshKey
import com.homelab.app.data.repository.SshKeyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KeyManagementState(
    val keys: List<SshKey> = emptyList(),
    val isGenerating: Boolean = false,
    val isImporting: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class KeyManagementViewModel @Inject constructor(
    private val sshKeyRepository: SshKeyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(KeyManagementState())
    val state: StateFlow<KeyManagementState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sshKeyRepository.getAllKeys().collect { keys ->
                _state.update { it.copy(keys = keys) }
            }
        }
    }

    fun generateKey(name: String) {
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, error = null) }
            runCatching { sshKeyRepository.generateKey(name) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            _state.update { it.copy(isGenerating = false) }
        }
    }

    fun importKey(name: String, pemContent: String) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null) }
            runCatching { sshKeyRepository.importKeyFromPem(name, pemContent) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            _state.update { it.copy(isImporting = false) }
        }
    }

    fun deleteKey(keyId: String) {
        viewModelScope.launch {
            runCatching { sshKeyRepository.deleteKey(keyId) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
