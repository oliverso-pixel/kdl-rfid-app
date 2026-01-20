package com.kdl.rfidinventory.data.remote.dto.response

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class WarehouseResponse(
    @SerializedName("warehouseId")
    val id: String,
    val name: String,
    @SerializedName("address")
    val location: String? = null,
    val isActive: Boolean = true
)