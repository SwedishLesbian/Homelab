package com.homelab.app.data.model

import java.time.Instant

data class AuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Instant,
    val tailnet: String
) {
    val isExpired: Boolean get() = Instant.now().isAfter(expiresAt.minusSeconds(60))
}
