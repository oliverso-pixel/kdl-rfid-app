package com.kdl.rfidinventory.data.remote.dto.response

data class SettingsResponse(
    val serverUrl: String,
    val scanTimeoutSeconds: Int,
    val autoSync: Boolean,
    val appVersion: String,
    val databaseVersion: Int
)
