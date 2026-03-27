package com.homelab.app.data.repository

import com.homelab.app.data.local.dao.SessionDao
import com.homelab.app.data.local.entity.SessionEntity
import com.homelab.app.data.model.Session
import com.homelab.app.data.model.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(private val sessionDao: SessionDao) {

    fun getAllSessions(): Flow<List<Session>> =
        sessionDao.getAllSessions().map { it.map(SessionEntity::toDomain) }

    fun getActiveSessions(): Flow<List<Session>> =
        sessionDao.getActiveSessions().map { it.map(SessionEntity::toDomain) }

    suspend fun saveSession(session: Session) =
        sessionDao.upsertSession(SessionEntity.fromDomain(session))

    suspend fun updateStatus(sessionId: String, status: SessionStatus) =
        sessionDao.updateStatus(sessionId, status.name, Instant.now())

    suspend fun deleteSession(sessionId: String) = sessionDao.deleteSession(sessionId)

    suspend fun pruneOldSessions() =
        sessionDao.pruneOldSessions(Instant.now().minusSeconds(7 * 24 * 3600))
}
