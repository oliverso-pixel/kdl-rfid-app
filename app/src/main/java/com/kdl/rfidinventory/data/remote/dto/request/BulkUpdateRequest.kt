package com.kdl.rfidinventory.data.remote.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class BulkUpdateRequest(
    val updateType: String, // "Clear", "Production", "Receiving", "Transfer"
    val commonData: CommonDataDto,
    val baskets: List<BasketUpdateItemDto>
)

@Serializable
data class CommonDataDto(
    // 這些欄位是共用的，若 baskets 內的項目沒填，就會使用這裡的值
    val warehouseId: String? = null,
    val updateBy: String? = null,
    val status: String? = null,
    val quantity: Int? = null,

    // 生產模式專用 (JSON String)
    val product: String? = null,
    val batch: String? = null
)

@Serializable
data class BasketUpdateItemDto(
    val rfid: String,

    // 以下為可選，若有值則覆蓋 commonData
    val status: String? = null,
    val quantity: Int? = null,
    val warehouseId: String? = null,
    val description: String? = null
)

