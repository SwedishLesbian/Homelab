package com.homelab.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.homelab.app.data.model.KeyType
import com.homelab.app.data.model.SshKey
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.StringReader
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }

    private val importedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "homelab_imported_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Generates an SSH key pair backed by Android Keystore (TEE).
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

    /**
     * Imports a private key from PEM text (OpenSSH, RSA PKCS1, EC SEC1, or PKCS8 with public key).
     * Stores both keys in EncryptedSharedPreferences (software-backed).
     * Returns the SshKey record; caller should save it to the DB.
     *
     * Supported formats (output of ssh-keygen):
     *   -----BEGIN OPENSSH PRIVATE KEY-----  (most common, includes public key)
     *   -----BEGIN RSA PRIVATE KEY-----
     *   -----BEGIN EC PRIVATE KEY-----
     */
    fun importFromPem(name: String, pemContent: String): SshKey {
        val alias = "homelab_imported_${UUID.randomUUID()}"

        val pemObject = PEMParser(StringReader(pemContent.trim())).readObject()
            ?: throw IllegalArgumentException("Could not parse PEM — make sure you pasted the full private key")

        val converter = JcaPEMKeyConverter().setProvider("BC")
        val keyPair = when (pemObject) {
            is PEMKeyPair -> converter.getKeyPair(pemObject)
            is PrivateKeyInfo -> {
                // PKCS8 bare private key — no public key embedded. Require full keypair PEM.
                throw IllegalArgumentException(
                    "Please paste the full OpenSSH private key file " +
                    "(-----BEGIN OPENSSH PRIVATE KEY-----), not a bare PKCS8 key."
                )
            }
            else -> throw IllegalArgumentException(
                "Unrecognised PEM block: ${pemObject.javaClass.simpleName}"
            )
        }

        // Persist in EncryptedSharedPreferences
        importedPrefs.edit()
            .putString("priv_$alias", android.util.Base64.encodeToString(
                keyPair.private.encoded, android.util.Base64.NO_WRAP))
            .putString("pub_$alias", android.util.Base64.encodeToString(
                keyPair.public.encoded, android.util.Base64.NO_WRAP))
            .putString("algo_$alias", keyPair.private.algorithm)
            .apply()

        val keyType = when (keyPair.private.algorithm.uppercase()) {
            "RSA" -> KeyType.RSA
            "ED25519", "EDDSA" -> KeyType.ED25519
            else -> KeyType.ECDSA
        }

        // Build a minimal OpenSSH-format public key string for display
        val pubB64 = android.util.Base64.encodeToString(
            keyPair.public.encoded, android.util.Base64.NO_WRAP)
        val opensshPubKey = "${keyType.name.lowercase()} $pubB64 imported"

        return SshKey(
            id = alias,
            name = name,
            publicKey = opensshPubKey,
            keyType = keyType,
            createdAt = Instant.now()
        )
    }

    fun getPrivateKey(keyId: String): PrivateKey? {
        // Keystore-generated keys
        val ksKey = runCatching { keyStore.getKey(keyId, null) as? PrivateKey }.getOrNull()
        if (ksKey != null) return ksKey

        // Imported keys
        val privB64 = importedPrefs.getString("priv_$keyId", null) ?: return null
        val algo = importedPrefs.getString("algo_$keyId", "EC") ?: "EC"
        val der = android.util.Base64.decode(privB64, android.util.Base64.NO_WRAP)
        return runCatching {
            KeyFactory.getInstance(algo, "BC").generatePrivate(PKCS8EncodedKeySpec(der))
        }.getOrNull()
    }

    fun getPublicKey(keyId: String): PublicKey? {
        // Keystore-generated: read from the stored certificate
        val entry = runCatching {
            keyStore.getEntry(keyId, null) as? KeyStore.PrivateKeyEntry
        }.getOrNull()
        if (entry != null) return entry.certificate.publicKey

        // Imported keys
        val pubB64 = importedPrefs.getString("pub_$keyId", null) ?: return null
        val algo = importedPrefs.getString("algo_$keyId", "EC") ?: "EC"
        val der = android.util.Base64.decode(pubB64, android.util.Base64.NO_WRAP)
        return runCatching {
            KeyFactory.getInstance(algo, "BC").generatePublic(X509EncodedKeySpec(der))
        }.getOrNull()
    }

    fun deleteKey(keyId: String) {
        if (keyStore.containsAlias(keyId)) keyStore.deleteEntry(keyId)
        importedPrefs.edit()
            .remove("priv_$keyId")
            .remove("pub_$keyId")
            .remove("algo_$keyId")
            .apply()
    }

    fun hasKey(keyId: String): Boolean =
        keyStore.containsAlias(keyId) || importedPrefs.contains("priv_$keyId")

    private fun isStrongBoxAvailable(): Boolean = runCatching {
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        true
    }.getOrDefault(false)
}
