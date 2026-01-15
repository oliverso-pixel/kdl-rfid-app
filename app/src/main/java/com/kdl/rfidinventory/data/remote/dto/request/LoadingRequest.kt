package com.kdl.rfidinventory.data.remote.dto.request

import kotlinx.serialization.Serializable

/**
 * 上貨請求
 */
@Serializable
data class LoadingRequest(
    val routeId: String,
    val routeName: String,
    val deliveryDate: String,
    val warehouseId: String,
    val mode: String,                    // FULL_BASKETS 或 LOOSE_ITEMS
    val items: List<LoadingItemRequest>,
    val totalScanned: Int,
    val timestamp: String
)

@Serializable
data class LoadingItemRequest(
    val productId: String,
    val productName: String,
    val batchId: String?,
    val basketUids: List<String>,        // 掃描的籃子 UID
    val scannedQuantity: Int,
    val expectedQuantity: Int,
    val isLoose: Boolean
)

/**
 * 出貨驗證請求
 */
@Serializable
data class ShippingVerifyRequest(
    val routeId: String,
    val routeName: String,
    val deliveryDate: String,
    val basketUids: List<String>,
    val totalScanned: Int,
    val totalExpected: Int,
    val isComplete: Boolean,
    val timestamp: String
)