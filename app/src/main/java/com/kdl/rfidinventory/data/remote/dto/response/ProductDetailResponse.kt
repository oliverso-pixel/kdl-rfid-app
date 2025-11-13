package com.kdl.rfidinventory.data.remote.dto.response

import com.kdl.rfidinventory.data.model.Product

data class ProductDetailResponse(
    val id: String,
    val name: String,
    val maxBasketCapacity: Int,
    val description: String? = null
)

// 擴展函數
fun ProductDetailResponse.toProduct(): Product {
    return Product(
        id = id,
        name = name,
        maxBasketCapacity = maxBasketCapacity,
        description = description
    )
}