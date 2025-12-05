package com.kdl.rfidinventory.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PendingOperation(
    val id: Int = 0,
    val operationType: OperationType,
    val uid: String,
    val payload: String,
    val timestamp: Long,
    val retryCount: Int = 0
)

enum class OperationType {
    PRODUCTION_START,
    WAREHOUSE_RECEIVE,
    SHIPPING_SHIP,
    SHIPPING_VERIFY,
    SAMPLING,
    CLEAR_ASSOCIATION,
    ADMIN_UPDATE
}