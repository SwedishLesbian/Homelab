package com.homelab.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DevicesResponse(
    @Json(name = "devices") val devices: List<DeviceDto>
)

@JsonClass(generateAdapter = true)
data class DeviceDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "hostname") val hostname: String,
    @Json(name = "addresses") val addresses: List<String>,
    @Json(name = "online") val online: Boolean = false,
    @Json(name = "os") val os: String = "",
    @Json(name = "tags") val tags: List<String>? = null,
    @Json(name = "lastSeen") val lastSeen: String = ""
)
