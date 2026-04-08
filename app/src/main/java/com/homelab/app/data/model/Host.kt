package com.homelab.app.data.model

import java.time.Instant

data class Host(
    val id: String,
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
    val sshAuthMethod: String? = null
)
