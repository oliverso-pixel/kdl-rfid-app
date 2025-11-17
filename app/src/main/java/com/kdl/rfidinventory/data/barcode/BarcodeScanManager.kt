package com.kdl.rfidinventory.data.barcode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.kdl.rfidinventory.util.SoundTool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 條碼掃描管理器
 * 通過 BroadcastReceiver 接收系統條碼掃描事件
 */
@Singleton
class BarcodeScanManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var isInitialized = false
    private var scanReceiver: BarcodeScanReceiver? = null

    companion object {
        // 常見的條碼掃描廣播 Action（根據設備廠商可能不同）
        private const val ACTION_BARCODE_SCAN = "com.android.server.scannerservice.broadcast"
        private const val ACTION_BARCODE_DECODED = "android.intent.ACTION_DECODE_DATA"
        private const val ACTION_SCANNER_RESULT = "nlscan.action.SCANNER_RESULT"

        // 數據字段（根據設備廠商可能不同）
        private const val EXTRA_BARCODE_DATA = "barcode"
        private const val EXTRA_BARCODE_STRING = "barocode_string"
        private const val EXTRA_SCAN_RESULT = "SCAN_BARCODE1"
        private const val EXTRA_DECODE_DATA = "barcode_string"
    }

    init {
        initialize()
    }

    private fun initialize() {
        try {
            Timber.d("Initializing BarcodeScanManager...")
            isInitialized = true
            Timber.i("✅ BarcodeScanManager initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize BarcodeScanManager")
        }
    }

    /**
     * 開始條碼掃描
     * 返回一個 Flow，每次掃描到條碼時發送數據
     */
    fun startScan(): Flow<BarcodeData> = callbackFlow {
        if (!isInitialized) {
            Timber.e("BarcodeScanManager not initialized")
            close(IllegalStateException("条码扫描器未初始化"))
            return@callbackFlow
        }

        Timber.d("🔍 Starting barcode scan...")

        scanReceiver = BarcodeScanReceiver { barcodeData ->
            Timber.d("📱 Barcode received: ${barcodeData.content}")
            trySend(barcodeData).isSuccess
            SoundTool.getInstance(context)?.playBeep(1)
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_BARCODE_SCAN)
            addAction(ACTION_BARCODE_DECODED)
            addAction(ACTION_SCANNER_RESULT)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    scanReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                ContextCompat.registerReceiver(
                    context,
                    scanReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
            Timber.i("✅ Barcode scan receiver registered")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register barcode receiver")
            close(e)
        }

        awaitClose {
            Timber.d("Closing barcode scan flow...")
            stopScan()
        }
    }

    fun stopScan() {
        try {
            scanReceiver?.let {
                context.unregisterReceiver(it)
                scanReceiver = null
                Timber.d("🛑 Barcode scan receiver unregistered")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error stopping barcode scan")
        }
    }

    fun release() {
        Timber.i("Releasing BarcodeScanManager resources")
        stopScan()
        isInitialized = false
        Timber.i("✅ BarcodeScanManager resources released")
    }

    private inner class BarcodeScanReceiver(
        private val onBarcodeScanned: (BarcodeData) -> Unit
    ) : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            Timber.d("📡 Broadcast received: action=${intent.action}")
            val barcode = extractBarcodeFromIntent(intent)

            if (!barcode.isNullOrEmpty()) {
                Timber.d("✅ Barcode extracted: $barcode")
                val barcodeData = BarcodeData(
                    content = barcode,
                    format = detectBarcodeFormat(barcode)
                )
                onBarcodeScanned(barcodeData)
            } else {
                Timber.w("⚠️ No barcode data found in intent")
                logIntentExtras(intent)
            }
        }


        private fun extractBarcodeFromIntent(intent: Intent): String? {
            val possibleKeys = listOf(
                EXTRA_BARCODE_DATA,
                EXTRA_BARCODE_STRING,
                EXTRA_SCAN_RESULT,
                EXTRA_DECODE_DATA,
                "data",
                "scannerdata"
            )

            for (key in possibleKeys) {
                val value = intent.getStringExtra(key)
                if (!value.isNullOrEmpty()) {
                    Timber.d("Found barcode in key: $key = $value")
                    return value.trim()
                }
            }

            // 尝试从 byte array 提取
            try {
                val bytes = intent.getByteArrayExtra("data")
                if (bytes != null && bytes.isNotEmpty()) {
                    val barcode = String(bytes).trim()
                    Timber.d("Found barcode in byte array: $barcode")
                    return barcode
                }
            } catch (e: Exception) {
                Timber.e(e, "Error extracting barcode from byte array")
            }

            return null
        }

        /**
         * 檢測條碼格式
         */
        private fun detectBarcodeFormat(barcode: String): BarcodeFormat {
            return when {
                barcode.length == 13 && barcode.all { it.isDigit() } -> BarcodeFormat.EAN_13
                barcode.length == 8 && barcode.all { it.isDigit() } -> BarcodeFormat.EAN_8
                barcode.length == 12 && barcode.all { it.isDigit() } -> BarcodeFormat.UPC_A
                barcode.startsWith("http") || barcode.contains("://") -> BarcodeFormat.QR_CODE
                else -> BarcodeFormat.UNKNOWN
            }
        }

        /**
         * 記錄 Intent 的所有 extras（調試用）
         */
        private fun logIntentExtras(intent: Intent) {
            val extras = intent.extras
            if (extras != null) {
                Timber.d("Intent extras:")
                for (key in extras.keySet()) {
                    val value = extras.get(key)
                    Timber.d("  $key = $value (${value?.javaClass?.simpleName})")
                }
            } else {
                Timber.d("Intent has no extras")
            }
        }
    }
}