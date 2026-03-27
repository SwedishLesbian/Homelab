package com.homelab.app.data.local.dao

import androidx.room.*
import com.homelab.app.data.local.entity.SshKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SshKeyDao {
    @Query("SELECT * FROM ssh_keys ORDER BY createdAt DESC")
    fun getAllKeys(): Flow<List<SshKeyEntity>>

    @Query("SELECT * FROM ssh_keys WHERE id = :id")
    suspend fun getKeyById(id: String): SshKeyEntity?

    @Upsert
    suspend fun upsertKey(key: SshKeyEntity)

    @Query("DELETE FROM ssh_keys WHERE id = :id")
    suspend fun deleteKey(id: String)
}
