package com.kdl.rfidinventory.data.remote.dto.request

data class ShippingRequest(
    val uid: String,
    val destination: String,
    val timestamp: String
)
