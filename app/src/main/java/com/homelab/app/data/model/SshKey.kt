package com.homelab.app.data.model

import java.time.Instant

data class SshKey(
    val id: String,
    val name: String,
    val publicKey: String,
    val keyType: KeyType,
    val createdAt: Instant,
    val associatedHostIds: List<String> = emptyList()
)

enum class KeyType { ED25519, RSA, ECDSA }
