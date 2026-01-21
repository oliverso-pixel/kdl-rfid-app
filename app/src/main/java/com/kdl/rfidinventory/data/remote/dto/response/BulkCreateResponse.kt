package com.kdl.rfidinventory.data.remote.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class BulkCreateResponse(
    val results: List<BulkCreateResultDto>
)

@Serializable
data class BulkCreateResultDto(
    val rfid: String,
    val success: Boolean,
    val message: String
)

data class BulkCreateResult(
    val successCount: Int,
    val totalCount: Int,
    val details: List<BulkCreateResultDto>,
    val isOffline: Boolean = false
)