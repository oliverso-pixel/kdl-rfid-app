package com.kdl.rfidinventory.util.rfid

sealed class ScanResult {
    data class RFIDScan(val tag: RFIDTag) : ScanResult()
    data class BarcodeScan(val barcode: String, val format: String = "UNKNOWN") : ScanResult()
}