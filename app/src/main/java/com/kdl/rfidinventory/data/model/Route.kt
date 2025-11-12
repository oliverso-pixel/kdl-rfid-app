package com.kdl.rfidinventory.data.model

data class Route (
    val id: String,
    val name: String,
    val destination: String,
    val isActive: Boolean = true
)