package com.kdl.rfidinventory.data.model

data class ScannedBasket(
    val uid: String,
    val quantity: Int,
    val rssi: Int,
    val scannedAt: Long = System.currentTimeMillis()
)
