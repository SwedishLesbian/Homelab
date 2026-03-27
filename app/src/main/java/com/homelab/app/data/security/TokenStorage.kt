package com.homelab.app.data.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
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
        private val ACCESS_TOKEN = stringPreferencesKey("access_token_enc")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token_enc")
        private val EXPIRES_AT = stringPreferencesKey("expires_at")
        private val TAILNET = stringPreferencesKey("tailnet")
    }

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiresAt: Instant,
        tailnet: String
    ) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = accessToken
            refreshToken?.let { prefs[REFRESH_TOKEN] = it }
            prefs[EXPIRES_AT] = expiresAt.toString()
            prefs[TAILNET] = tailnet
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
        dataStore.edit { it.clear() }
    }
}
