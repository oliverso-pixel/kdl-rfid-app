package com.kdl.rfidinventory.data.model

data class Basket (
    val uid: String,
    val product: Product?,
    val batch: Batch?,
    val quantity: Int,
    val status: BasketStatus,
    val productionDate: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

enum class BasketStatus {
    UNASSIGNED,
    IN_PRODUCTION,
    RECEIVED,
    IN_STOCK,
    SHIPPED,
    SAPLING,
    SAMPLING
}