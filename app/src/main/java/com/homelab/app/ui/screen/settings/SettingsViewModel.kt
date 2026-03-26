package com.homelab.app.ui.screen.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val sessionTimeoutMinutes: Int = 30,
    val clipboardAutoClear: Boolean = true,
    val screenshotProtection: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        val SESSION_TIMEOUT = intPreferencesKey("session_timeout_minutes")
        val CLIPBOARD_AUTO_CLEAR = booleanPreferencesKey("clipboard_auto_clear")
        val SCREENSHOT_PROTECTION = booleanPreferencesKey("screenshot_protection")
    }

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _state.update {
                    it.copy(
                        sessionTimeoutMinutes = prefs[SESSION_TIMEOUT] ?: 30,
                        clipboardAutoClear = prefs[CLIPBOARD_AUTO_CLEAR] ?: true,
                        screenshotProtection = prefs[SCREENSHOT_PROTECTION] ?: true
                    )
                }
            }
        }
    }

    fun setSessionTimeout(minutes: Int) {
        viewModelScope.launch {
            dataStore.edit { it[SESSION_TIMEOUT] = minutes }
        }
    }

    fun setClipboardAutoClear(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[CLIPBOARD_AUTO_CLEAR] = enabled }
        }
    }

    fun setScreenshotProtection(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[SCREENSHOT_PROTECTION] = enabled }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
