package com.kdl.rfidinventory.data.model

data class Route (
    val id: String,
    val name: String,
    val destination: String,
    val description: String? = null,
    val isActive: Boolean = true
)