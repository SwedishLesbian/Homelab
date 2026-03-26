package com.homelab.app.data.local.dao

import androidx.room.*
import com.homelab.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY lastActive DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE status IN ('CONNECTING','CONNECTED','RECONNECTING') ORDER BY lastActive DESC")
    fun getActiveSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: String): SessionEntity?

    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Query("UPDATE sessions SET status = :status, lastActive = :lastActive WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, lastActive: java.time.Instant)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM sessions WHERE status = 'DISCONNECTED' AND lastActive < :before")
    suspend fun pruneOldSessions(before: java.time.Instant)
}
