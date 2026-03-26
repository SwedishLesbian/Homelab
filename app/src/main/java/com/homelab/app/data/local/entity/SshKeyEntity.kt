package com.homelab.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.homelab.app.data.model.KeyType
import com.homelab.app.data.model.SshKey
import java.time.Instant

@Entity(tableName = "ssh_keys")
data class SshKeyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val publicKey: String,
    val keyType: String,
    val createdAt: Instant,
    val associatedHostIds: List<String>
) {
    fun toDomain() = SshKey(
        id = id, name = name, publicKey = publicKey,
        keyType = KeyType.valueOf(keyType), createdAt = createdAt,
        associatedHostIds = associatedHostIds
    )

    companion object {
        fun fromDomain(key: SshKey) = SshKeyEntity(
            id = key.id, name = key.name, publicKey = key.publicKey,
            keyType = key.keyType.name, createdAt = key.createdAt,
            associatedHostIds = key.associatedHostIds
        )
    }
}
