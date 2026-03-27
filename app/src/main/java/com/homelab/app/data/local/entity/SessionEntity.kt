package com.homelab.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.homelab.app.data.model.Session
import com.homelab.app.data.model.SessionStatus
import java.time.Instant

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val hostId: String,
    val hostName: String,
    val hostIp: String,
    val username: String,
    val status: String,
    val lastActive: Instant,
    val createdAt: Instant
) {
    fun toDomain() = Session(
        id = id, hostId = hostId, hostName = hostName, hostIp = hostIp,
        username = username, status = SessionStatus.valueOf(status),
        lastActive = lastActive, createdAt = createdAt
    )

    companion object {
        fun fromDomain(session: Session) = SessionEntity(
            id = session.id, hostId = session.hostId, hostName = session.hostName,
            hostIp = session.hostIp, username = session.username,
            status = session.status.name, lastActive = session.lastActive,
            createdAt = session.createdAt
        )
    }
}
