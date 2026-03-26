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
        private const val AUTH_ENDPOINT = "https://login.tailscale.com/oauth/authorize"
        private const val TOKEN_ENDPOINT = "https://login.tailscale.com/oauth/token"
        private const val CLIENT_ID = BuildConfig.TAILSCALE_CLIENT_ID
        private const val REDIRECT_URI = "com.homelab.app://oauth"
    }

    private val authService = AuthorizationService(context)

    fun buildAuthIntent(): Intent {
        val config = AuthorizationServiceConfiguration(
            android.net.Uri.parse(AUTH_ENDPOINT),
            android.net.Uri.parse(TOKEN_ENDPOINT)
        )
        val request = AuthorizationRequest.Builder(
            config,
            CLIENT_ID,
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
            val refreshToken = tokenStorage.getRefreshToken()
                ?: throw IllegalStateException("No refresh token")
            // Perform token refresh via AppAuth
        }
    }

    suspend fun isAuthenticated(): Boolean = tokenStorage.getAccessToken() != null

    suspend fun logout() = tokenStorage.clearTokens()

    private suspend fun exchangeCode(response: AuthorizationResponse): TokenResponse =
        suspendCoroutine { cont ->
            authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResp, ex ->
                if (tokenResp != null) cont.resume(tokenResp)
                else cont.resumeWithException(ex ?: RuntimeException("Token exchange failed"))
            }
        }

    private fun extractTailnet(token: String): String {
        // JWT middle part contains tailnet claim; fallback to "-"
        return runCatching {
            val payload = token.split(".")[1]
            val decoded = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))
            val json = org.json.JSONObject(decoded)
            json.optString("tailnet", "-")
        }.getOrDefault("-")
    }
}

// Placeholder for BuildConfig — generated at compile time
private object BuildConfig {
    const val TAILSCALE_CLIENT_ID = ""
}
