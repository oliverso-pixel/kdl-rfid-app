package com.kdl.rfidinventory.data.barcode

interface BarcodeScanCallback {
    fun onBarcodeScanned(barcode: String)
    fun onScanError(error: String)
}