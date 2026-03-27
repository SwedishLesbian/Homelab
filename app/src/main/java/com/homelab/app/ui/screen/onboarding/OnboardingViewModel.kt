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

enum class OnboardingStep { CLIENT_ID, SIGN_IN }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.CLIENT_ID,
    val clientIdInput: String = "",
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
            when {
                authRepository.isAuthenticated() ->
                    _state.update { it.copy(isAuthenticated = true) }
                authRepository.hasClientId() ->
                    _state.update { it.copy(step = OnboardingStep.SIGN_IN) }
            }
        }
    }

    fun onClientIdChanged(value: String) {
        _state.update { it.copy(clientIdInput = value, error = null) }
    }

    fun saveClientId() {
        val id = _state.value.clientIdInput.trim()
        if (id.isBlank()) {
            _state.update { it.copy(error = "Please enter your OAuth client ID") }
            return
        }
        viewModelScope.launch {
            authRepository.saveClientId(id)
            _state.update { it.copy(step = OnboardingStep.SIGN_IN, error = null) }
        }
    }

    fun startAuth() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { authRepository.buildAuthIntent() }
                .onSuccess { intent -> _state.update { it.copy(authIntent = intent, isLoading = false) } }
                .onFailure { e  -> _state.update { it.copy(isLoading = false, error = e.message) } }
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

    fun goBackToClientId() {
        _state.update { it.copy(step = OnboardingStep.CLIENT_ID, error = null) }
    }
}
