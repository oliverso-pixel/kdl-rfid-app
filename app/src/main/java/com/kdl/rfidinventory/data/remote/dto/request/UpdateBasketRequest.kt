package com.kdl.rfidinventory.data.remote.dto.request

data class UpdateBasketRequest(
    val productId: String?,
    val batchId: String?,
    val quantity: Int,
    val status: String,
    val productionDate: String?
)
