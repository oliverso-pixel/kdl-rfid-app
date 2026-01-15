package com.kdl.rfidinventory.data.remote.dto.response

data class BasketResponse(
    val uid: String,
    val productId: String?,
    val productName: String?,
    val warehouseId: String?,
    val batchId: String?,
    val quantity: Int,
    val status: String,
    val productionDate: String?,
    val lastUpdated: Long
)
