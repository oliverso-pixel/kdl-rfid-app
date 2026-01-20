// data/remote/dto/request/BindBasketRequest.kt
package com.kdl.rfidinventory.data.remote.dto.request

import kotlinx.serialization.Serializable

/**
 * 對應 PUT /api/v1/baskets/{uid}
 * 用於生產模式綁定籃子
 */
@Serializable
data class BindBasketRequest(
    val status: String = "IN_PRODUCTION",
    val quantity: Int,
    val product: String, // 注意：這是一個 JSON String
    val batch: String    // 注意：這是一個 JSON String
)