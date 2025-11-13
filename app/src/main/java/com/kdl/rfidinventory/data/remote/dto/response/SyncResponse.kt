package com.kdl.rfidinventory.data.remote.dto.response

data class SyncResponse(
    val syncedCount: Int,
    val failedOperations: List<String>
)
