package com.kdl.rfidinventory.data.remote.dto.response

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?
)
