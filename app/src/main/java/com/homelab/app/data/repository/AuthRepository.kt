package com.homelab.app.data.repository

import com.homelab.app.data.security.TokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val tokenStorage: TokenStorage
) {
    companion object {
        private const val TOKEN_ENDPOINT = "https://login.tailscale.com/oauth/token"
    }

    // Plain client — no AuthInterceptor to avoid circular dependency
    private val httpClient = OkHttpClient()

    /** True when a non-expired access token is present (re-authenticates automatically if expired). */
    suspend fun isAuthenticated(): Boolean {
        val hasCredentials = tokenStorage.getClientId() != null &&
                tokenStorage.getClientSecret() != null
        if (!hasCredentials) return false
        if (tokenStorage.isTokenExpired()) return refreshTokenIfNeeded().isSuccess
        return tokenStorage.getAccessToken() != null
    }

    /** True when the user has completed onboarding (both client ID and secret saved). */
    suspend fun hasCredentials(): Boolean =
        tokenStorage.getClientId() != null && tokenStorage.getClientSecret() != null

    /** True when the user has at least entered their client ID (step 1 complete). */
    suspend fun hasClientId(): Boolean = tokenStorage.getClientId() != null

    suspend fun saveClientId(clientId: String) = tokenStorage.saveClientId(clientId)
    suspend fun saveClientSecret(clientSecret: String) = tokenStorage.saveClientSecret(clientSecret)
    suspend fun getClientId(): String? = tokenStorage.getClientId()

    /**
     * Authenticates using the OAuth 2.0 client credentials grant.
     * Tailscale does not support the authorization code / PKCE flow for third-party apps;
     * client credentials (client ID + secret → access token) is the correct mechanism.
     */
    suspend fun authenticate(): Result<Unit> = runCatching {
        val clientId = tokenStorage.getClientId()
            ?: throw IllegalStateException("No client ID saved — complete onboarding first")
        val clientSecret = tokenStorage.getClientSecret()
            ?: throw IllegalStateException("No client secret saved — complete onboarding first")
        fetchAndSaveToken(clientId, clientSecret)
    }

    /** Re-authenticates silently when the access token has expired. */
    suspend fun refreshTokenIfNeeded(): Result<Unit> {
        if (!tokenStorage.isTokenExpired()) return Result.success(Unit)
        return authenticate()
    }

    suspend fun logout() = tokenStorage.clearTokens()

    private suspend fun fetchAndSaveToken(clientId: String, clientSecret: String) =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build()
            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw IOException("Empty response")

            if (!response.isSuccessful) {
                val msg = runCatching {
                    JSONObject(responseBody).optString("message", responseBody)
                }.getOrDefault(responseBody)
                throw IOException("Tailscale auth failed (${response.code}): $msg")
            }

            val json = JSONObject(responseBody)
            val accessToken = json.getString("access_token")
            val expiresIn = json.optLong("expires_in", 3600L)

            tokenStorage.saveTokens(
                accessToken = accessToken,
                refreshToken = null,
                expiresAt = Instant.now().plusSeconds(expiresIn),
                // '-' is Tailscale's wildcard tailnet — resolves to the token's own tailnet
                tailnet = "-"
            )
        }
}
