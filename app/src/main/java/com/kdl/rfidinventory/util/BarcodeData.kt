package com.kdl.rfidinventory.util

/**
 * 條碼數據模型
 */
data class BarcodeData(
    val content: String,
    val format: BarcodeFormat
)

/**
 * 條碼格式枚舉
 */
enum class BarcodeFormat {
    EAN_13,
    EAN_8,
    UPC_A,
    QR_CODE,
    UNKNOWN
}