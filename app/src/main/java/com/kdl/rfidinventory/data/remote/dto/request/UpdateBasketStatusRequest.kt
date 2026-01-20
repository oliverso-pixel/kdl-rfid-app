package com.kdl.rfidinventory.data.remote.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateBasketStatusRequest(
    val status: String,
    val quantity: Int? = null,
    val warehouseId: String? = null,
    val updateBy: String? = null
)