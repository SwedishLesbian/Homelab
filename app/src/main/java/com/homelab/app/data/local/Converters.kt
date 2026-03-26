package com.homelab.app.data.local

import androidx.room.TypeConverter
import java.time.Instant

class Converters {
    @TypeConverter fun fromInstant(value: Instant?): Long? = value?.epochSecond
    @TypeConverter fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochSecond(it) }
    @TypeConverter fun fromStringList(value: List<String>?): String? = value?.joinToString(",")
    @TypeConverter fun toStringList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split(",")
}
