package com.kdl.rfidinventory.data.model

data class Batch(
    val id: String,
    val productId: String,
    val totalQuantity: Int,
    val remainingQuantity: Int,
    val productionDate: String,
)
