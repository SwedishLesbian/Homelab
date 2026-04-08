package com.homelab.app.ssh

import javax.inject.Inject
import javax.inject.Singleton

enum class AuthMethod {
    TAILSCALE_SSH,
    SSH_KEY,
    PASSWORD
}

data class AuthParams(
    val username: String,
    val authMethod: AuthMethod,
    val keyId: String? = null,
    val password: String? = null,
    val deployKeyId: String? = null
)

@Singleton
class AuthParamsStore @Inject constructor() {
    // Keyed by sessionId — used by TerminalViewModel to pick up creds
    private val bySession = mutableMapOf<String, AuthParams>()
    // Keyed by hostId — used to restore last-used creds when re-opening the bottom sheet
    private val byHost = mutableMapOf<String, AuthParams>()

    fun put(sessionId: String, authParams: AuthParams) {
        bySession[sessionId] = authParams
    }

    fun get(sessionId: String): AuthParams? = bySession[sessionId]

    fun remove(sessionId: String) {
        bySession.remove(sessionId)
    }

    fun putForHost(hostId: String, authParams: AuthParams) {
        byHost[hostId] = authParams
    }

    fun getForHost(hostId: String): AuthParams? = byHost[hostId]
}
