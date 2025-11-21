package com.kdl.rfidinventory.util.rfid

data class RFIDTag(
    val uid: String,
    val rssi: Int = 0,  // 信號強度
    val timestamp: Long = System.currentTimeMillis()
)
