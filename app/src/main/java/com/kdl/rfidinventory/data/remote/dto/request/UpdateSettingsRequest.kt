package com.kdl.rfidinventory.data.remote.dto.request

data class UpdateSettingsRequest(
    val serverUrl: String?,
    val scanTimeout: Int?,
    val autoSync: Boolean?
)
