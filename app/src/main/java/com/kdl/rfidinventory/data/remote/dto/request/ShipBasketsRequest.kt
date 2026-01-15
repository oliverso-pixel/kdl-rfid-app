package com.kdl.rfidinventory.data.remote.dto.request

data class ShipBasketsRequest(
    val basketUids: List<String>,
    val routeId: String,
    val timestamp: String
)
