package com.kdl.rfidinventory.data.remote.dto.response

import kotlinx.serialization.Serializable

/**
 * 上貨路線響應
 */
@Serializable
data class LoadingRouteResponse(
    val id: String,
    val name: String,
    val vehiclePlate: String?,
    val deliveryDate: String,
    val items: List<LoadingItemResponse>,
    val totalQuantity: Int,
    val totalBaskets: Int,
    val status: String
)

@Serializable
data class LoadingItemResponse(
    val productId: String,
    val productName: String,
    val batchId: String?,
    val warehouseId: String,
    val totalQuantity: Int,
    val fullTrolley: Int,
    val fullBaskets: Int,
    val looseQuantity: Int,
    val maxBasketCapacity: Int,
    val basketsPerCar: Int,
    val imageUrl: String?
)