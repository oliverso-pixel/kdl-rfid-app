package com.kdl.rfidinventory.data.model

data class Warehouse(
    val id: String,
    val name: String,
    val address: String,
    val isActive: Boolean
)

fun mockWarehouses() = listOf(
    Warehouse(id = "WH-001", name = "GF", address = "", isActive = true),
    Warehouse(id = "WH-002", name = "1F-A", address = "", isActive = true),
    Warehouse(id = "WH-003", name = "1F-B", address = "", isActive = true),
    Warehouse(id = "WH-004", name = "3F", address = "", isActive = false)
)