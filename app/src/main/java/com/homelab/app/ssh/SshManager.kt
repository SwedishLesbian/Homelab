package com.homelab.app.ssh

import com.homelab.app.data.model.Host
import com.homelab.app.data.security.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
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
        username: String,
        keyId: String?,
        onHostKeyVerification: suspend (String) -> Boolean
    ): Result<SshSession> = withContext(Dispatchers.IO) {
        runCatching {
            val client = SSHClient()
            // Use strict host key checking in production
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connectTimeout = 10_000
            client.connect(host.ip)

            if (keyId != null) {
                val privateKey = keystoreManager.getPrivateKey(keyId)
                if (privateKey != null) {
                    client.authPublickey(username, AndroidKeystoreKeyProvider(privateKey))
                } else {
                    throw IllegalStateException("SSH key not found in keystore: $keyId")
                }
            } else {
                throw IllegalArgumentException("No SSH key configured for this host")
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
