package com.kdl.rfidinventory.data.model

data class Basket (
    val uid: String,
    val product: Product?,
    val batch: Batch?,
    val warehouseId: String?,
    val quantity: Int,
    val status: BasketStatus,
    val productionDate: String? = null,
    val expireDate: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val updateBy: String?
)

enum class BasketStatus {
    UNASSIGNED,
    IN_PRODUCTION,
    RECEIVED,
    IN_STOCK,
    SHIPPED,
    SAMPLING
}