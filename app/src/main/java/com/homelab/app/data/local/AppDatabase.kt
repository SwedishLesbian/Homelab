package com.homelab.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.homelab.app.data.local.dao.HostDao
import com.homelab.app.data.local.dao.SessionDao
import com.homelab.app.data.local.dao.SshKeyDao
import com.homelab.app.data.local.entity.HostEntity
import com.homelab.app.data.local.entity.SessionEntity
import com.homelab.app.data.local.entity.SshKeyEntity

@Database(
    entities = [HostEntity::class, SessionEntity::class, SshKeyEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun sessionDao(): SessionDao
    abstract fun sshKeyDao(): SshKeyDao
}
