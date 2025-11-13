package com.kdl.rfidinventory.data.remote.dto.response

data class ProductionOrderResponse(
    val id: String,
    val productId: String,
    val productName: String,
    val totalQuantity: Int,
    val remainingQuantity: Int,
    val startDate: String?,
    val dueDate: String?
)
