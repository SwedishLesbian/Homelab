package com.homelab.app.data.model

import java.time.Instant

data class Session(
    val id: String,
    val hostId: String,
    val hostName: String,
    val hostIp: String,
    val username: String,
    val status: SessionStatus,
    val lastActive: Instant,
    val createdAt: Instant = Instant.now()
)

enum class SessionStatus {
    CONNECTING, CONNECTED, FAILED, DISCONNECTED, RECONNECTING
}
