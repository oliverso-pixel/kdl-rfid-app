package com.kdl.rfidinventory.util

import android.os.Build
import androidx.annotation.RequiresApi
import com.kdl.rfidinventory.util.rfid.RFIDManager
import com.kdl.rfidinventory.util.rfid.RFIDTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 統一的掃描管理器
 * 整合 RFID、條碼掃描和實體按鍵事件處理
 */
@Singleton
class ScanManager @Inject constructor(
    private val rfidManager: RFIDManager,
    private val keyEventHandler: KeyEventHandler
) {
    // 掃描狀態
    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // 掃描結果事件流
    private val _scanResults = MutableSharedFlow<ScanResult>()
    val scanResults: SharedFlow<ScanResult> = _scanResults.asSharedFlow()

    // 錯誤事件流
    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    // ⭐ 當前掃描任務
    private var currentScanJob: Job? = null

    /**
     * 初始化掃描管理器
     * @param scope CoroutineScope 用於啟動協程
     * @param scanMode 當前掃描模式
     * @param canStartScan 掃描前置條件檢查
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun initialize(
        scope: CoroutineScope,
        scanMode: StateFlow<ScanMode>,
        canStartScan: () -> Boolean = { true }
    ) {
        Timber.d("🎯 ScanManager initializing")

        // 監聽實體按鍵事件
        scope.launch {
            keyEventHandler.scanTriggerEvents.collect { event ->
                handleKeyEvent(event, scanMode.value, canStartScan)
            }
        }
    }

    /**
     * 處理實體按鍵事件
     */
    private suspend fun handleKeyEvent(
        event: ScanTriggerEvent,
        currentMode: ScanMode,
        canStartScan: () -> Boolean
    ) {
        val currentState = _scanState.value

        Timber.d("🔑 KeyEvent: $event | Mode: $currentMode | Scanning: ${currentState.isScanning} | Type: ${currentState.scanType}")

        when (event) {
            is ScanTriggerEvent.StartScan -> {
                when (currentMode) {
                    ScanMode.SINGLE -> {
                        // ⭐ 單次模式：只有在未掃描時才啟動
                        if (!currentState.isScanning) {
                            // 檢查前置條件
                            if (!canStartScan()) {
                                Timber.w("⚠️ Cannot start scan: preconditions not met")
                                _errors.emit("掃描條件不滿足，請檢查是否已選擇必要信息")
                                return
                            }

                            Timber.d("✅ Single mode: Triggering barcode scan")
                            startBarcodeScan(ScanContext.BASKET_SCAN)
                        } else {
                            Timber.d("⏭️ Single mode: Already scanning (${currentState.scanType}), ignoring StartScan")
                        }
                    }
                    ScanMode.CONTINUOUS -> {
                        // ⭐ 連續模式：未掃描時啟動 RFID
                        if (!currentState.isScanning) {
                            // 檢查前置條件
                            if (!canStartScan()) {
                                Timber.w("⚠️ Cannot start scan: preconditions not met")
                                _errors.emit("掃描條件不滿足，請檢查是否已選擇必要信息")
                                return
                            }

                            Timber.d("✅ Continuous mode: Starting RFID scan")
                            startRfidScan(currentMode)
                        } else {
                            Timber.d("⏭️ Continuous mode: Already scanning, ignoring StartScan")
                        }
                    }
                }
            }

            is ScanTriggerEvent.StopScan -> {
                // ⭐ 單次模式下，如果正在條碼掃描，則取消掃描
                if (currentMode == ScanMode.SINGLE && currentState.isScanning && currentState.scanType == ScanType.BARCODE) {
                    Timber.d("🛑 Single mode: Cancelling barcode scan")
                    stopScanning()
//                    _errors.emit("已取消掃描")
                }
                // ⭐ 連續模式或 RFID 掃描時，停止掃描
                else if (currentState.isScanning) {
                    Timber.d("🛑 Stopping scan (Mode: $currentMode, Type: ${currentState.scanType})")
                    stopScanning()
                } else {
                    Timber.d("⏭️ Not scanning, ignoring StopScan")
                }
            }

            is ScanTriggerEvent.ClearList -> {
                _scanResults.emit(ScanResult.ClearListRequested)
            }
        }
    }

    /**
     * 開始條碼掃描
     * @param context 掃描上下文（用於區分不同場景）
     */
    suspend fun startBarcodeScan(context: ScanContext = ScanContext.BASKET_SCAN) {
        Timber.d("🚀 Starting barcode scan (context: $context)")

        // ⭐ 取消之前的掃描任務
        currentScanJob?.cancel()

        _scanState.update {
            it.copy(
                isScanning = true,
                scanType = ScanType.BARCODE,
                context = context
            )
        }

        currentScanJob = kotlinx.coroutines.GlobalScope.launch {
            try {
                rfidManager.startBarcodeScan()
                    .catch { error ->
                        Timber.e(error, "Barcode scan error")
                        _errors.emit("條碼掃描失敗: ${error.message}")
                        resetScanState()
                    }
                    .collect { barcode ->
                        Timber.d("📦 Barcode scanned: $barcode")

                        // 發送掃描結果
                        _scanResults.emit(
                            ScanResult.BarcodeScanned(
                                barcode = barcode,
                                context = context
                            )
                        )

                        // 條碼掃描後自動停止
                        if (context == ScanContext.BASKET_SCAN) {
                            stopScanning()
                        }
                        // PRODUCT_SEARCH 上下文會保持掃描狀態，等待下一個條碼
                    }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "Failed to start barcode scan")
                    _errors.emit("啟動條碼掃描失敗: ${e.message}")
                    resetScanState()
                } else {
                    Timber.d("Barcode scan cancelled")
                }
            }
        }
    }

    /**
     * 開始 RFID 掃描
     */
    suspend fun startRfidScan(mode: ScanMode) {
        Timber.d("🚀 Starting RFID scan with mode: $mode")

        if (_scanState.value.isScanning) {
            Timber.w("⚠️ Already scanning, stopping first")
            stopScanning()
            kotlinx.coroutines.delay(100)
        }

        // ⭐ 取消之前的掃描任務
        currentScanJob?.cancel()

        _scanState.update {
            it.copy(
                isScanning = true,
                scanType = ScanType.RFID,
                context = ScanContext.BASKET_SCAN
            )
        }

        currentScanJob = kotlinx.coroutines.GlobalScope.launch {
            try {
                rfidManager.startScan(mode)
                    .catch { error ->
                        Timber.e(error, "RFID scan error")
                        _errors.emit("RFID掃描失敗: ${error.message}")
                        resetScanState()
                    }
                    .collect { tag ->
                        Timber.d("📡 RFID Tag scanned: ${tag.uid}")

                        // 發送掃描結果
                        _scanResults.emit(
                            ScanResult.RfidScanned(
                                tag = tag,
                                context = ScanContext.BASKET_SCAN
                            )
                        )

                        // SINGLE 模式自動停止
                        if (mode == ScanMode.SINGLE) {
                            Timber.d("⏹️ SINGLE mode: Auto-stopping after tag read")
                            stopScanning()
                        }
                    }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "Failed to start RFID scan")
                    _errors.emit("啟動RFID掃描失敗: ${e.message}")
                    resetScanState()
                } else {
                    Timber.d("RFID scan cancelled")
                }
            }
        }
    }

    /**
     * 停止掃描
     */
    fun stopScanning() {
        val currentState = _scanState.value
        Timber.d("🛑 Stopping scan (type: ${currentState.scanType})")

        try {
            // ⭐ 取消當前掃描任務
            currentScanJob?.cancel()
            currentScanJob = null

            // 停止 RFIDManager（這會關閉 Flow）
            rfidManager.stopScan()
            Timber.d("✅ RFIDManager stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping scan")
        }

        resetScanState()
    }

    /**
     * 重置掃描狀態
     */
    private fun resetScanState() {
        _scanState.update {
            it.copy(
                isScanning = false,
                scanType = ScanType.NONE,
                context = ScanContext.BASKET_SCAN
            )
        }
    }

    /**
     * 切換掃描模式
     */
    suspend fun changeScanMode(newMode: ScanMode) {
        Timber.d("🔄 Changing scan mode to $newMode")

        if (_scanState.value.isScanning) {
            Timber.d("⏹️ Stopping active scan before mode change")
            stopScanning()
            kotlinx.coroutines.delay(200)
        }
    }
}

/**
 * 掃描狀態
 */
data class ScanState(
    val isScanning: Boolean = false,
    val scanType: ScanType = ScanType.NONE,
    val context: ScanContext = ScanContext.BASKET_SCAN
)

/**
 * 掃描類型
 */
enum class ScanType {
    NONE,
    BARCODE,
    RFID
}

/**
 * 掃描上下文（用於區分不同的掃描場景）
 */
enum class ScanContext {
    BASKET_SCAN,      // 籃子掃描
    PRODUCT_SEARCH    // 產品搜索
}

/**
 * 掃描結果封裝
 */
sealed class ScanResult {
    data class BarcodeScanned(
        val barcode: String,
        val context: ScanContext
    ) : ScanResult()

    data class RfidScanned(
        val tag: RFIDTag,
        val context: ScanContext
    ) : ScanResult()

    object ClearListRequested : ScanResult()
}