package com.kdl.rfidinventory.data.remote.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class BulkUpdateResponse(
    val message: String,
    val updated_count: Int,
    val update_type: String
)