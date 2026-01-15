package com.kdl.rfidinventory.data.remote.dto.request

data class ProductionStartRequest(
    val uid: String,
    val productId: String,
    val batchId: String,
    val quantity: Int,
    val productionDate: String
)
