package com.kdl.rfidinventory.data.remote.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class BulkCreateRequest(
    val items: List<CreateBasketItemDto>
)

@Serializable
data class CreateBasketItemDto(
    val rfid: String,
    val type: Int = 1,
    val description: String = ""
)