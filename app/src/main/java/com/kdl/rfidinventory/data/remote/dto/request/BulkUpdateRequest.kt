package com.kdl.rfidinventory.data.remote.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class BulkUpdateRequest(
    val updateType: String, // "Clear", "Production", "Transfer" 等
    val commonData: CommonDataDto,
    val baskets: List<BasketIdDto>
)

@Serializable
data class CommonDataDto(
    val warehouseId: String? = null,
    val updateBy: String? = null,
    val status: String? = null
)

@Serializable
data class BasketIdDto(
    val rfid: String
)