package com.homelab.app.ssh

import com.homelab.app.data.model.Host
import com.homelab.app.data.security.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.method.AuthNone
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECKey
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
            // Android's BouncyCastle may still lack some kex algorithms even after replacing
            // the provider — filter Curve25519 as belt-and-suspenders.
            val config = DefaultConfig()
            config.keyExchangeFactories = config.keyExchangeFactories.filter { kex ->
                !kex.name.contains("curve25519", ignoreCase = true)
            }

            val client = SSHClient(config)
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connectTimeout = 10_000
            client.connect(host.ip)

            when (authParams.authMethod) {
                AuthMethod.TAILSCALE_SSH -> {
                    // Tailscale SSH authenticates at the network layer via WireGuard identity.
                    // The SSH server grants access based on the connecting Tailscale node.
                    client.auth(authParams.username, AuthNone())
                }
                AuthMethod.SSH_KEY -> {
                    val keyId = authParams.keyId
                        ?: throw IllegalArgumentException("No SSH key selected")
                    val privateKey = keystoreManager.getPrivateKey(keyId)
                        ?: throw IllegalStateException("SSH key not found: $keyId")
                    val publicKey = keystoreManager.getPublicKey(keyId)
                        ?: throw IllegalStateException("Public key not found: $keyId")
                    client.authPublickey(
                        authParams.username,
                        KeystoreKeyProvider(privateKey, publicKey)
                    )
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
 * Bridges a PrivateKey + PublicKey pair (Android Keystore or imported) to sshj's KeyProvider.
 * Detects key type from the actual key object so RSA/EC/Ed25519 all work correctly.
 */
class KeystoreKeyProvider(
    private val privateKey: PrivateKey,
    private val publicKey: PublicKey
) : KeyProvider {
    override fun getPrivate(): PrivateKey = privateKey
    override fun getPublic(): PublicKey = publicKey
    override fun getType(): KeyType = when {
        privateKey.algorithm.equals("RSA", ignoreCase = true) -> KeyType.RSA
        privateKey.algorithm.equals("Ed25519", ignoreCase = true) ||
            privateKey.algorithm.equals("EdDSA", ignoreCase = true) -> KeyType.ED25519
        privateKey is ECKey || privateKey.algorithm.equals("EC", ignoreCase = true) -> {
            // Detect ECDSA curve from key size
            val bitLength = runCatching { (publicKey as ECKey).params.order.bitLength() }.getOrDefault(256)
            when {
                bitLength >= 500 -> KeyType.ECDSA521
                bitLength >= 370 -> KeyType.ECDSA384
                else -> KeyType.ECDSA256
            }
        }
        else -> KeyType.ECDSA256
    }
}
