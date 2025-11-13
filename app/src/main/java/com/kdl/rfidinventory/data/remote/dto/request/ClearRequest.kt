package com.kdl.rfidinventory.data.remote.dto.request

data class ClearRequest(
    val basketUids: List<String>,
    val timestamp: String
)
