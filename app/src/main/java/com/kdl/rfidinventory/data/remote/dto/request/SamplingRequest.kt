package com.kdl.rfidinventory.data.remote.dto.request

data class SamplingRequest(
    val basketUids: List<String>,
    val sampleQuantity: Int,
    val remarks: String,
    val timestamp: String
)
