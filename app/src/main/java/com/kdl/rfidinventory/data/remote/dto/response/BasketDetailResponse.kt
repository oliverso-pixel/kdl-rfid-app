package com.kdl.rfidinventory.data.remote.dto.response

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class BasketDetailResponse(
    @SerializedName("rfid")
    val rfid: String,
    val type: Int,
    val description: String?,
    val bid: Int?,
    val status: String,
    val quantity: Int,
    val warehouseId: String?,
    val product: String?,
    val batch: String?,
    val lastUpdated: String?,
    val updateBy: String?
)