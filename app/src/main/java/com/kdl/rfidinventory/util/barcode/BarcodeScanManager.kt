package com.kdl.rfidinventory.util.barcode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.kdl.rfidinventory.util.SoundTool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BarcodeScanManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var isInitialized = false
    private var scanReceiver: BarcodeScanReceiver? = null

    // ⭐ KeyEvent 模式相關
    private val keyEventBuffer = StringBuilder()
    private var lastKeyEventTime = 0L
    private var keyEventCallback: ((String) -> Unit)? = null

    companion object {
        private const val ACTION_BARCODE_SCAN = "com.android.server.scannerservice.broadcast"
        private const val ACTION_BARCODE_DECODED = "android.intent.ACTION_DECODE_DATA"
        private const val ACTION_SCANNER_RESULT = "nlscan.action.SCANNER_RESULT"

        private const val EXTRA_BARCODE_DATA = "barcode"
        private const val EXTRA_BARCODE_STRING = "barocode_string"
        private const val EXTRA_SCAN_RESULT = "SCAN_BARCODE1"
        private const val EXTRA_DECODE_DATA = "barcode_string"

        // ⭐ KeyEvent 超時時間（毫秒）
        private const val KEY_EVENT_TIMEOUT_MS = 100L
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
     * ⭐ 處理 KeyEvent 條碼掃描
     * @return true 表示事件已處理
     */
    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        // ⭐ 只處理 ACTION_DOWN
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        // ⭐ 如果沒有激活的掃描回調，不處理
        if (keyEventCallback == null) {
            return false
        }

        val currentTime = System.currentTimeMillis()

        // ⭐ 檢查是否超時（開始新的掃描）
        if (currentTime - lastKeyEventTime > KEY_EVENT_TIMEOUT_MS) {
            if (keyEventBuffer.isNotEmpty()) {
                Timber.v("⏱️ Scan timeout, clearing buffer: ${keyEventBuffer}")
            }
            keyEventBuffer.clear()
        }
        lastKeyEventTime = currentTime

        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                // ⭐ Enter 鍵表示掃描結束
                if (keyEventBuffer.isNotEmpty()) {
                    val barcode = keyEventBuffer.toString()
                    Timber.i("📱 Barcode scanned via KeyEvent: $barcode")
                    keyEventBuffer.clear()

                    // 觸發回調
                    keyEventCallback?.invoke(barcode)
                    SoundTool.getInstance(context)?.playBeep(1)
                    true  // ⭐ 返回 true，表示事件已處理
                } else {
                    Timber.v("⚠️ ENTER pressed but buffer is empty")
                    false
                }
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                // 數字鍵
                val digit = (keyCode - KeyEvent.KEYCODE_0).toString()
                keyEventBuffer.append(digit)
                Timber.v("📝 Barcode buffer: ${keyEventBuffer}")
                true  // ⭐ 返回 true
            }
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                // 字母鍵
                val char = ('A' + (keyCode - KeyEvent.KEYCODE_A))
                keyEventBuffer.append(char)
                Timber.v("📝 Barcode buffer: ${keyEventBuffer}")
                true  // ⭐ 返回 true
            }
            KeyEvent.KEYCODE_MINUS -> {
                keyEventBuffer.append('-')
                Timber.v("📝 Barcode buffer: ${keyEventBuffer}")
                true
            }
            KeyEvent.KEYCODE_PERIOD -> {
                keyEventBuffer.append('.')
                Timber.v("📝 Barcode buffer: ${keyEventBuffer}")
                true
            }
            KeyEvent.KEYCODE_SPACE -> {
                keyEventBuffer.append(' ')
                Timber.v("📝 Barcode buffer: ${keyEventBuffer}")
                true
            }
            // ⭐ 新增：處理 keyCode=0 (某些掃描器可能發送)
            0 -> {
                Timber.v("⚠️ Received keyCode=0, ignoring")
                false
            }
            else -> {
                // ⭐ 其他按鍵不處理
                Timber.v("⏭️ Unhandled keyCode in barcode scan: $keyCode")
                false
            }
        }
    }

    fun startScan(): Flow<BarcodeData> = callbackFlow {
        if (!isInitialized) {
            Timber.e("BarcodeScanManager not initialized")
            close(IllegalStateException("条码扫描器未初始化"))
            return@callbackFlow
        }

        Timber.d("🔍 Starting barcode scan (Broadcast + KeyEvent mode)...")

        // ⭐ 設置 KeyEvent 回調
        keyEventCallback = { barcode ->
            Timber.d("📦 Barcode received via KeyEvent: $barcode")
            val barcodeData = BarcodeData(
                content = barcode,
                format = detectBarcodeFormat(barcode)
            )
            trySend(barcodeData).isSuccess
        }

        // ⭐ 同時註冊 BroadcastReceiver（備用）
        scanReceiver = BarcodeScanReceiver { barcodeData ->
            Timber.d("📡 Barcode received via Broadcast: ${barcodeData.content}")
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
            Timber.i("✅ Barcode scan receiver registered (Broadcast + KeyEvent)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register barcode receiver")
        }

        awaitClose {
            Timber.d("Closing barcode scan flow...")
            stopScan()
        }
    }

    fun stopScan() {
        try {
            // ⭐ 清除 KeyEvent 回調
            keyEventCallback = null
            keyEventBuffer.clear()
            lastKeyEventTime = 0L

            // 取消註冊 BroadcastReceiver
            scanReceiver?.let {
                context.unregisterReceiver(it)
                scanReceiver = null
            }
            Timber.d("🛑 Barcode scan stopped")
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

    private fun detectBarcodeFormat(barcode: String): BarcodeFormat {
        return when {
            barcode.length == 13 && barcode.all { it.isDigit() } -> BarcodeFormat.EAN_13
            barcode.length == 8 && barcode.all { it.isDigit() } -> BarcodeFormat.EAN_8
            barcode.length == 12 && barcode.all { it.isDigit() } -> BarcodeFormat.UPC_A
            barcode.startsWith("http") || barcode.contains("://") -> BarcodeFormat.QR_CODE
            else -> BarcodeFormat.UNKNOWN
        }
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