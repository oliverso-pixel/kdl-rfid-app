package com.kdl.rfidinventory.data.model

data class ScannedBasket(
    val uid: String,
    val quantity: Int,
    val rssi: Int,
    val scannedAt: Long = System.currentTimeMillis(),
    val scanCount: Int = 1,  // 掃描次數
    val firstScannedTime: Long = System.currentTimeMillis(),  // 首次掃描時間
    val lastScannedTime: Long = System.currentTimeMillis()   // 最後掃描時間
)
