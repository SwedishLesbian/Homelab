package com.homelab.app.data.repository

import com.homelab.app.data.local.dao.SshKeyDao
import com.homelab.app.data.local.entity.SshKeyEntity
import com.homelab.app.data.model.KeyType
import com.homelab.app.data.model.SshKey
import com.homelab.app.data.security.KeystoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshKeyRepository @Inject constructor(
    private val sshKeyDao: SshKeyDao,
    private val keystoreManager: KeystoreManager
) {
    fun getAllKeys(): Flow<List<SshKey>> =
        sshKeyDao.getAllKeys().map { it.map(SshKeyEntity::toDomain) }

    suspend fun generateKey(name: String): SshKey {
        val key = keystoreManager.generateSshKey(name, KeyType.ED25519)
        sshKeyDao.upsertKey(SshKeyEntity.fromDomain(key))
        return key
    }

    /**
     * Imports a key from an OpenSSH/PEM private key file.
     * Supports -----BEGIN OPENSSH PRIVATE KEY-----, RSA PKCS1, and EC SEC1 formats.
     */
    suspend fun importKeyFromPem(name: String, pemContent: String): SshKey {
        val key = keystoreManager.importFromPem(name, pemContent)
        sshKeyDao.upsertKey(SshKeyEntity.fromDomain(key))
        return key
    }

    suspend fun deleteKey(keyId: String) {
        keystoreManager.deleteKey(keyId)
        sshKeyDao.deleteKey(keyId)
    }

    private fun detectKeyType(publicKey: String): KeyType = when {
        publicKey.startsWith("ssh-ed25519") -> KeyType.ED25519
        publicKey.startsWith("ecdsa-") -> KeyType.ECDSA
        else -> KeyType.RSA
    }
}
