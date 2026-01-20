// data/remote/dto/response/ProductionDtos.kt
package com.kdl.rfidinventory.data.remote.dto.response

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * 對應 GET /api/v1/production/daily-products
 */
@Serializable
data class DailyProductResponse(
    @SerializedName("itemcode") val itemCode: String, // 對應 Product.id
    val barcodeId: String?,
    val qrcodeId: String?,
    val name: String,
    val maxBasketCapacity: Int,
    val imageUrl: String?
)

/**
 * 對應 GET /api/v1/production/app-list
 */
@Serializable
data class ProductionBatchResponse(
    @SerializedName("batch_code") val batchCode: String, // 對應 Batch.id
    @SerializedName("itemcode") val itemCode: String,    // 對應 Product.id
    val totalQuantity: Int,
    val remainingQuantity: Int,
    val productionDate: String?,
    val expireDate: String?
)