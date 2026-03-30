package com.homelab.app.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val clientIdInput: String = "",
    val clientSecretInput: String = "",
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (authRepository.isAuthenticated()) {
                _state.update { it.copy(isAuthenticated = true) }
            }
        }
    }

    fun onClientIdChanged(value: String) {
        _state.update { it.copy(clientIdInput = value, error = null) }
    }

    fun onClientSecretChanged(value: String) {
        _state.update { it.copy(clientSecretInput = value, error = null) }
    }

    fun connect() {
        val id = _state.value.clientIdInput.trim()
        val secret = _state.value.clientSecretInput.trim()
        if (id.isBlank() || secret.isBlank()) {
            _state.update { it.copy(error = "Please enter both your client ID and secret") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.saveClientId(id)
            authRepository.saveClientSecret(secret)
            authRepository.authenticate()
                .onSuccess { _state.update { it.copy(isAuthenticated = true, isLoading = false) } }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Connection failed") }
                }
        }
    }
}
