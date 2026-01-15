package com.kdl.rfidinventory.data.remote.dto.response

data class RouteResponse(
    val id: String,
    val name: String,
    val destination: String,
    val description: String?,
    val isActive: Boolean
)
