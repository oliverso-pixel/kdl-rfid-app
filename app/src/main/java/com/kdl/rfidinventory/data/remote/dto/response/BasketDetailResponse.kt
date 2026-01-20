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

//    val rfid: String,
//    val type: Int,
//    val description: String?,
//    val bid: Int?,
//    val status: String,
//    val quantity: Int,
//    val warehouseId: String?,
//    val product: String?,
//    val batch: String?,
//    val lastUpdated: String?,
//    val updateBy: String?
)