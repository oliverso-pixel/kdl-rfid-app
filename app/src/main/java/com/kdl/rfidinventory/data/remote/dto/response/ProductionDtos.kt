package com.kdl.rfidinventory.data.remote.dto.response

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * 對應 GET /api/v1/production/daily-products
 */
@Serializable
data class DailyProductResponse(
    @SerializedName("itemcode")
    val itemCode: String,
    val barcodeId: String?,
    val qrcodeId: String?,
    val name: String,
    val btype: Int,
    val maxBasketCapacity: Int,
    val imageUrl: String?
)

/**
 * 對應 GET /api/v1/production/app-list
 */
@Serializable
data class ProductionBatchResponse(
    @SerializedName("batch_code")
    val batchCode: String,
    @SerializedName("itemcode")
    val itemCode: String,

    val totalQuantity: Int,
    val targetQuantity: Int,
    val producedQuantity: Int,
    val remainingQuantity: Int,
    val status: String,
    val maxRepairs: Int,

    val productionDate: String?,
    val expireDate: String?
)