package com.kdl.rfidinventory.data.remote.dto.request

data class ReceiveBasketsRequest(
    val basketUids: List<String>,
    val timestamp: String
)
