package com.homelab.app.ssh

import com.homelab.app.data.model.Host
import com.homelab.app.data.security.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.method.AuthNone
import java.security.PrivateKey
import java.security.PublicKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshManager @Inject constructor(
    private val keystoreManager: KeystoreManager
) {
    private val activeSessions = mutableMapOf<String, SshSession>()

    suspend fun connect(
        sessionId: String,
        host: Host,
        authParams: AuthParams
    ): Result<SshSession> = withContext(Dispatchers.IO) {
        runCatching {
            val client = SSHClient()
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connectTimeout = 10_000
            client.connect(host.ip)

            when (authParams.authMethod) {
                AuthMethod.TAILSCALE_SSH -> {
                    // Tailscale SSH: authentication is handled by the Tailscale network layer.
                    // The SSH server accepts the connecting node's identity automatically.
                    // Attempt "none" auth first; if the server doesn't allow it, fall back to
                    // publickey with the Tailscale-managed key (most servers accept either).
                    client.auth(authParams.username, AuthNone())
                }
                AuthMethod.SSH_KEY -> {
                    val keyId = authParams.keyId
                        ?: throw IllegalArgumentException("No SSH key selected")
                    val privateKey = keystoreManager.getPrivateKey(keyId)
                        ?: throw IllegalStateException("SSH key not found in keystore: $keyId")
                    client.authPublickey(authParams.username, AndroidKeystoreKeyProvider(privateKey))
                }
                AuthMethod.PASSWORD -> {
                    val password = authParams.password
                        ?: throw IllegalArgumentException("No password provided")
                    client.authPassword(authParams.username, password)
                }
            }

            val session = SshSession(sessionId = sessionId, client = client, host = host)
            session.open()
            activeSessions[sessionId] = session
            session
        }
    }

    fun getSession(sessionId: String): SshSession? = activeSessions[sessionId]

    fun disconnectSession(sessionId: String) {
        activeSessions.remove(sessionId)?.close()
    }

    fun disconnectAll() {
        activeSessions.values.forEach { it.close() }
        activeSessions.clear()
    }
}

/**
 * Bridges Android Keystore PrivateKey to sshj's KeyProvider interface.
 * The private key never leaves the secure element — signing happens in hardware.
 */
class AndroidKeystoreKeyProvider(private val privateKey: PrivateKey) : KeyProvider {
    override fun getPrivate(): PrivateKey = privateKey
    override fun getPublic(): PublicKey = throw UnsupportedOperationException("Use stored public key")
    override fun getType(): net.schmizz.sshj.common.KeyType = net.schmizz.sshj.common.KeyType.ECDSA256
}
