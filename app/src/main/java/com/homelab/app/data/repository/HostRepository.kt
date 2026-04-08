package com.homelab.app.data.repository

import com.homelab.app.data.local.dao.HostDao
import com.homelab.app.data.local.entity.HostEntity
import com.homelab.app.data.model.Host
import com.homelab.app.data.remote.TailscaleApiService
import com.homelab.app.data.security.TokenStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostRepository @Inject constructor(
    private val hostDao: HostDao,
    private val apiService: TailscaleApiService,
    private val tokenStorage: TokenStorage
) {
    fun getAllHosts(): Flow<List<Host>> = hostDao.getAllHosts().map { it.map(HostEntity::toDomain) }
    fun getFavorites(): Flow<List<Host>> = hostDao.getFavoriteHosts().map { it.map(HostEntity::toDomain) }
    fun getRecents(): Flow<List<Host>> = hostDao.getRecentHosts().map { it.map(HostEntity::toDomain) }
    fun searchHosts(query: String): Flow<List<Host>> = hostDao.searchHosts(query).map { it.map(HostEntity::toDomain) }

    suspend fun refreshHosts(): Result<Unit> = runCatching {
        val tailnet = tokenStorage.getTailnet() ?: throw IllegalStateException("Not authenticated")
        val response = apiService.getDevices(tailnet)

        // Preserve user-configured fields (credentials, favorites, lastConnected)
        // that would otherwise be wiped by the upsert from API data.
        val existingById = hostDao.getAllHosts().first().associateBy { it.id }

        val entities = response.devices.map { dto ->
            val existing = existingById[dto.id]
            HostEntity(
                id = dto.id,
                name = dto.name.substringBefore("."),
                hostname = dto.hostname,
                ip = dto.addresses.firstOrNull { it.contains("100.") } ?: dto.addresses.firstOrNull() ?: "",
                isOnline = dto.online,
                os = dto.os,
                tags = dto.tags ?: emptyList(),
                lastSeen = parseInstant(dto.lastSeen),
                isFavorite = existing?.isFavorite ?: false,
                sshUsername = existing?.sshUsername,
                sshKeyId = existing?.sshKeyId,
                lastConnected = existing?.lastConnected
            )
        }
        hostDao.upsertHosts(entities)
        if (entities.isNotEmpty()) {
            hostDao.deleteStaleHosts(entities.map { it.id })
        }
    }

    suspend fun setFavorite(hostId: String, isFavorite: Boolean) =
        hostDao.setFavorite(hostId, isFavorite)

    suspend fun updateSshConfig(hostId: String, username: String?, keyId: String?) =
        hostDao.updateSshConfig(hostId, username, keyId)

    suspend fun updateLastConnected(hostId: String) =
        hostDao.updateLastConnected(hostId, Instant.now())

    private fun parseInstant(value: String): Instant = try {
        Instant.parse(value)
    } catch (e: DateTimeParseException) {
        Instant.now()
    }
}
