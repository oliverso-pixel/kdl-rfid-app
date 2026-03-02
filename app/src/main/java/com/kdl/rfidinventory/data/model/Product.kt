package com.kdl.rfidinventory.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val itemcode: String,
    val barcodeId: Long? = null,
    val qrcodeId: String? = null,
    val name: String,
    val btype: Int,
    val maxBasketCapacity: Int,
    val description: String? = null,
    val imageUrl: String? = null
)
