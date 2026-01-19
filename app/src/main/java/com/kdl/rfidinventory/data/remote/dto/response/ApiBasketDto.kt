// data/remote/dto/response/ApiBasketDto.kt
package com.kdl.rfidinventory.data.remote.dto.response

data class ApiBasketDto(
    val rfid: String,
    val type: Int,
    val description: String?,
    val bid: Int?,
    val status: String, // "UNASSIGNED", "IN_STOCK" etc.
    val quantity: Int?,
    val warehouseId: String?,
    val product: String?,
    val batch: String?,
    val lastUpdated: String?,
    val updateBy: String?
)