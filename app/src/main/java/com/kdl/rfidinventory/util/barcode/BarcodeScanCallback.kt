package com.kdl.rfidinventory.util.barcode

interface BarcodeScanCallback {
    fun onBarcodeScanned(barcode: String)
    fun onScanError(error: String)
}