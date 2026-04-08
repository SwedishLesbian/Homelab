package com.homelab.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.homelab.app.data.model.Host
import java.time.Instant

@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey val id: String,
    val name: String,
    val hostname: String,
    val ip: String,
    val isOnline: Boolean,
    val os: String,
    val tags: List<String>,
    val lastSeen: Instant,
    val isFavorite: Boolean = false,
    val sshUsername: String? = null,
    val sshKeyId: String? = null,
    val sshAuthMethod: String? = null,
    val lastConnected: Instant? = null
) {
    fun toDomain() = Host(
        id = id, name = name, hostname = hostname, ip = ip,
        isOnline = isOnline, os = os, tags = tags, lastSeen = lastSeen,
        isFavorite = isFavorite, sshUsername = sshUsername, sshKeyId = sshKeyId,
        sshAuthMethod = sshAuthMethod
    )

    companion object {
        fun fromDomain(host: Host) = HostEntity(
            id = host.id, name = host.name, hostname = host.hostname, ip = host.ip,
            isOnline = host.isOnline, os = host.os, tags = host.tags, lastSeen = host.lastSeen,
            isFavorite = host.isFavorite, sshUsername = host.sshUsername, sshKeyId = host.sshKeyId
        )
    }
}
