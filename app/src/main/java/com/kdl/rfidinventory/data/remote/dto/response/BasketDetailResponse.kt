package com.kdl.rfidinventory.data.remote.dto.response
data class BasketDetailResponse(
    val uid: String,
    val productId: String?,
    val productName: String?,
    val batchId: String?,
    val warehouseId: String?,
    val quantity: Int,
    val status: String,
    val productionDate: String?,
    val expireDate: String?,
    val lastUpdated: Long,
    val updateBy: String?
)