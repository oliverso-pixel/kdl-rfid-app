package com.kdl.rfidinventory.data.model

data class Product(
    val id: String,
    val name: String,
    val maxBasketCapacity: Int,
    val description: String? = null
)
