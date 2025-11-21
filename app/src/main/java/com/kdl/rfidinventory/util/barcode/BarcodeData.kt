package com.kdl.rfidinventory.util.barcode

data class BarcodeData(
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val format: BarcodeFormat = BarcodeFormat.UNKNOWN
)

enum class BarcodeFormat {
    QR_CODE,
    CODE_128,
    CODE_39,
    EAN_13,
    EAN_8,
    UPC_A,
    UPC_E,
    UNKNOWN
}