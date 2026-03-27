package com.homelab.app.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.homelab.app.data.model.KeyType
import com.homelab.app.data.model.SshKey
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreManager @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }

    /**
     * Generates a new SSH key pair backed by Android Keystore (TEE).
     * The private key never leaves the secure element.
     */
    fun generateSshKey(name: String, keyType: KeyType = KeyType.ED25519): SshKey {
        val alias = "homelab_ssh_${UUID.randomUUID()}"
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setIsStrongBoxBacked(isStrongBoxAvailable())
            .build()

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
        )
        generator.initialize(spec)
        val keyPair = generator.generateKeyPair()

        val publicKeyEncoded = android.util.Base64.encodeToString(
            keyPair.public.encoded, android.util.Base64.NO_WRAP
        )
        val opensshPublicKey = "ecdsa-sha2-nistp256 $publicKeyEncoded homelab@android"

        return SshKey(
            id = alias,
            name = name,
            publicKey = opensshPublicKey,
            keyType = keyType,
            createdAt = Instant.now()
        )
    }

    fun getPrivateKey(keyId: String): PrivateKey? =
        runCatching { keyStore.getKey(keyId, null) as? PrivateKey }.getOrNull()

    fun deleteKey(keyId: String) {
        if (keyStore.containsAlias(keyId)) keyStore.deleteEntry(keyId)
    }

    fun hasKey(keyId: String): Boolean = keyStore.containsAlias(keyId)

    private fun isStrongBoxAvailable(): Boolean = runCatching {
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        true
    }.getOrDefault(false)
}
