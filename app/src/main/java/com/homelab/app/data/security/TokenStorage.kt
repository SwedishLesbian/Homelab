package com.homelab.app.data.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStorage @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val ACCESS_TOKEN  = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val EXPIRES_AT    = stringPreferencesKey("expires_at")
        private val TAILNET       = stringPreferencesKey("tailnet")
        // User-provided at onboarding — never comes from the build
        val CLIENT_ID         = stringPreferencesKey("oauth_client_id")
    }

    suspend fun saveClientId(clientId: String) {
        dataStore.edit { it[CLIENT_ID] = clientId.trim() }
    }

    suspend fun getClientId(): String? =
        dataStore.data.map { it[CLIENT_ID] }.firstOrNull()?.takeIf { it.isNotBlank() }

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiresAt: Instant,
        tailnet: String
    ) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = accessToken
            refreshToken?.let { prefs[REFRESH_TOKEN] = it }
            prefs[EXPIRES_AT]   = expiresAt.toString()
            prefs[TAILNET]      = tailnet
        }
    }

    suspend fun getAccessToken(): String? =
        dataStore.data.map { it[ACCESS_TOKEN] }.firstOrNull()

    suspend fun getRefreshToken(): String? =
        dataStore.data.map { it[REFRESH_TOKEN] }.firstOrNull()

    suspend fun getTailnet(): String? =
        dataStore.data.map { it[TAILNET] }.firstOrNull()

    suspend fun getExpiresAt(): Instant? =
        dataStore.data.map { it[EXPIRES_AT] }.firstOrNull()
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }

    suspend fun isTokenExpired(): Boolean {
        val expiresAt = getExpiresAt() ?: return true
        return Instant.now().isAfter(expiresAt.minusSeconds(60))
    }

    suspend fun clearTokens() {
        dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN)
            prefs.remove(REFRESH_TOKEN)
            prefs.remove(EXPIRES_AT)
            prefs.remove(TAILNET)
            // Intentionally keep CLIENT_ID so user doesn't have to re-enter it on next login
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
