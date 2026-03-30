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
    private val params = mutableMapOf<String, AuthParams>()

    fun put(sessionId: String, authParams: AuthParams) {
        params[sessionId] = authParams
    }

    fun get(sessionId: String): AuthParams? = params[sessionId]

    fun remove(sessionId: String) {
        params.remove(sessionId)
    }
}
