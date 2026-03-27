package com.homelab.app.data.repository

import android.content.Context
import android.content.Intent
import com.homelab.app.data.security.TokenStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openid.appauth.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStorage: TokenStorage
) {
    companion object {
        private const val AUTH_ENDPOINT  = "https://login.tailscale.com/oauth/authorize"
        private const val TOKEN_ENDPOINT = "https://login.tailscale.com/oauth/token"
        private const val REDIRECT_URI   = "com.homelab.app://oauth"
    }

    private val authService = AuthorizationService(context)

    /** Returns true only when both a client ID and a valid access token are present. */
    suspend fun isAuthenticated(): Boolean =
        tokenStorage.getClientId() != null && tokenStorage.getAccessToken() != null

    /** Returns true when a client ID has been saved (user completed onboarding step 1). */
    suspend fun hasClientId(): Boolean = tokenStorage.getClientId() != null

    suspend fun saveClientId(clientId: String) = tokenStorage.saveClientId(clientId)

    suspend fun getClientId(): String? = tokenStorage.getClientId()

    /**
     * Builds the OAuth PKCE intent using the client ID the user provided.
     * Throws [IllegalStateException] if no client ID has been saved yet.
     */
    suspend fun buildAuthIntent(): Intent {
        val clientId = tokenStorage.getClientId()
            ?: throw IllegalStateException("No OAuth client ID set. Complete onboarding first.")
        val config = AuthorizationServiceConfiguration(
            android.net.Uri.parse(AUTH_ENDPOINT),
            android.net.Uri.parse(TOKEN_ENDPOINT)
        )
        val request = AuthorizationRequest.Builder(
            config,
            clientId,
            ResponseTypeValues.CODE,
            android.net.Uri.parse(REDIRECT_URI)
        )
            .setScope("devices:read")
            .build()
        return authService.getAuthorizationRequestIntent(request)
    }

    suspend fun handleAuthResponse(intent: Intent): Result<Unit> = runCatching {
        val response = AuthorizationResponse.fromIntent(intent)
            ?: throw IllegalArgumentException("No auth response in intent")
        val tokenResponse = exchangeCode(response)
        val tailnet = extractTailnet(tokenResponse.accessToken ?: "")
        tokenStorage.saveTokens(
            accessToken = tokenResponse.accessToken ?: "",
            refreshToken = tokenResponse.refreshToken,
            expiresAt = tokenResponse.accessTokenExpirationTime
                ?.let { Instant.ofEpochMilli(it) } ?: Instant.now().plusSeconds(3600),
            tailnet = tailnet
        )
    }

    suspend fun refreshTokenIfNeeded(): Result<Unit> {
        if (!tokenStorage.isTokenExpired()) return Result.success(Unit)
        return runCatching {
            val clientId = tokenStorage.getClientId()
                ?: throw IllegalStateException("No client ID")
            val refreshToken = tokenStorage.getRefreshToken()
                ?: throw IllegalStateException("No refresh token")
            val config = AuthorizationServiceConfiguration(
                android.net.Uri.parse(AUTH_ENDPOINT),
                android.net.Uri.parse(TOKEN_ENDPOINT)
            )
            val tokenRequest = TokenRequest.Builder(config, clientId)
                .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                .setRefreshToken(refreshToken)
                .build()
            val tokenResponse = suspendCoroutine { cont ->
                authService.performTokenRequest(tokenRequest) { resp, ex ->
                    if (resp != null) cont.resume(resp)
                    else cont.resumeWithException(ex ?: RuntimeException("Refresh failed"))
                }
            }
            val tailnet = tokenStorage.getTailnet() ?: "-"
            tokenStorage.saveTokens(
                accessToken  = tokenResponse.accessToken ?: "",
                refreshToken = tokenResponse.refreshToken ?: refreshToken,
                expiresAt    = tokenResponse.accessTokenExpirationTime
                    ?.let { Instant.ofEpochMilli(it) } ?: Instant.now().plusSeconds(3600),
                tailnet      = tailnet
            )
        }
    }

    suspend fun logout() {
        authService.dispose()
        tokenStorage.clearTokens()   // keeps client ID so user skips step 1 next time
    }

    private suspend fun exchangeCode(response: AuthorizationResponse): TokenResponse =
        suspendCoroutine { cont ->
            authService.performTokenRequest(response.createTokenExchangeRequest()) { resp, ex ->
                if (resp != null) cont.resume(resp)
                else cont.resumeWithException(ex ?: RuntimeException("Token exchange failed"))
            }
        }

    private fun extractTailnet(token: String): String = runCatching {
        val payload = token.split(".")[1]
        val decoded = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))
        org.json.JSONObject(decoded).optString("tailnet", "-")
    }.getOrDefault("-")
}
