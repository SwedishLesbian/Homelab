package com.homelab.app.data.local.dao

import androidx.room.*
import com.homelab.app.data.local.entity.HostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY isFavorite DESC, name ASC")
    fun getAllHosts(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteHosts(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts ORDER BY lastConnected DESC LIMIT 5")
    fun getRecentHosts(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun getHostById(id: String): HostEntity?

    @Query("SELECT * FROM hosts WHERE name LIKE '%' || :query || '%' OR hostname LIKE '%' || :query || '%'")
    fun searchHosts(query: String): Flow<List<HostEntity>>

    @Upsert
    suspend fun upsertHosts(hosts: List<HostEntity>)

    @Upsert
    suspend fun upsertHost(host: HostEntity)

    @Query("UPDATE hosts SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE hosts SET lastConnected = :time WHERE id = :id")
    suspend fun updateLastConnected(id: String, time: java.time.Instant)

    @Query("UPDATE hosts SET sshUsername = :username, sshKeyId = :keyId, sshAuthMethod = :authMethod WHERE id = :id")
    suspend fun updateSshConfig(id: String, username: String?, keyId: String?, authMethod: String?)

    @Query("DELETE FROM hosts WHERE id NOT IN (:activeIds)")
    suspend fun deleteStaleHosts(activeIds: List<String>)
}
