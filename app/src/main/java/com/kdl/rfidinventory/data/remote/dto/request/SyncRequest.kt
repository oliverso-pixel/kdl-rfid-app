package com.kdl.rfidinventory.data.remote.dto.request

data class SyncRequest(
    val operations: List<PendingOperationDto>
)

data class PendingOperationDto(
    val operationType: String,
    val uid: String,
    val payload: String,
    val timestamp: Long
)
