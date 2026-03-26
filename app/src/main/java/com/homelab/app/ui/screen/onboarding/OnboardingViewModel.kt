package com.homelab.app.ui.screen.onboarding

import android.content.Intent
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
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val authIntent: Intent? = null,
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

    fun startAuth() {
        _state.update { it.copy(isLoading = true, error = null, authIntent = null) }
        runCatching {
            val intent = authRepository.buildAuthIntent()
            _state.update { it.copy(authIntent = intent, isLoading = false) }
        }.onFailure { e ->
            _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to start sign-in") }
        }
    }

    fun handleAuthResult(data: Intent) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, authIntent = null) }
            authRepository.handleAuthResponse(data)
                .onSuccess { _state.update { it.copy(isAuthenticated = true, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Sign-in failed") } }
        }
    }
}
