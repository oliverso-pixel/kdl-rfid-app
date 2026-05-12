package com.kdl.rfidinventory.util

import android.os.Build
import androidx.annotation.RequiresApi
import com.kdl.rfidinventory.domain.manager.rfid.RFIDManager
import com.kdl.rfidinventory.domain.manager.rfid.RFIDTag
import com.kdl.rfidinventory.util.scanner.DataWedgeController
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
    private val keyEventHandler: KeyEventHandler,
    private val screenBrightnessManager: ScreenBrightnessManager,
    private val dataWedge: DataWedgeController
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

    // 當前監聽的 Job（可重新訂閱）
    private var keyEventCollectorJob: Job? = null

    // 當前掃描任務
    private var currentScanJob: Job? = null

    // 當前的 canStartScan 檢查函數
    private var currentCanStartScan: (() -> Boolean)? = null

    private var currentScanContextProvider: (() -> ScanContext)? = null

    private var keepBarcodeWarm = false

    private var persistentReceiverInstalled = false

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
        canStartScan: () -> Boolean = { true },
        scanContextProvider: () -> ScanContext = { ScanContext.BASKET_SCAN }
    ) {
        Timber.d("🎯 ScanManager initializing (instance: ${this.hashCode()})")
        Timber.d("📊 Current scanMode: ${scanMode.value}, canStartScan: $canStartScan")

        // 取消之前的監聽器（允許重新訂閱）
        keyEventCollectorJob?.cancel()

        // 更新 canStartScan
        currentCanStartScan = canStartScan
        currentScanContextProvider = scanContextProvider

        // 重新訂閱 KeyEvent + ScanMode
        keyEventCollectorJob = scope.launch {
            keyEventHandler.scanTriggerEvents
                .combine(scanMode) { event, mode -> event to mode }
                .collect { (event, currentMode) ->
                    handleKeyEvent(event, currentMode, canStartScan)
                }
        }

        try {
            dataWedge.stopSoftScan()
            dataWedge.disableScanner()
        } catch (_: Exception) {}

        Timber.d("✅ ScanManager initialized successfully (instance: ${this.hashCode()})")
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

        Timber.d("🔑 ========== handleKeyEvent ==========")
        Timber.d("🔑 Event: $event")
        Timber.d("🔑 Mode: $currentMode")
        Timber.d("🔑 Current scanning: ${currentState.isScanning}")
        Timber.d("🔑 Current scanType: ${currentState.scanType}")
        Timber.d("🔑 canStartScan(): ${canStartScan()}")
        Timber.d("==========================================")

        when (event) {
            is ScanTriggerEvent.StartScan -> {
//                when (currentMode) {
//                    ScanMode.SINGLE -> {
//                        if (!currentState.isScanning) {
//                            // 檢查前置條件
//                            if (!canStartScan()) {
//                                Timber.w("⚠️ Cannot start scan: preconditions not met")
//                                _errors.emit("掃描條件不滿足，請檢查是否已選擇必要信息")
//                                return
//                            }
//
//                            Timber.d("✅ Single mode: Triggering barcode scan")
//                            startBarcodeScan(ScanContext.BASKET_SCAN)
//                        } else {
//                            Timber.d("⏭️ Single mode: Already scanning (${currentState.scanType}), ignoring StartScan")
//                        }
//                    }
//                    ScanMode.CONTINUOUS -> {
//                        // 連續模式：未掃描時啟動 RFID
//                        if (!currentState.isScanning) {
//                            // 檢查前置條件
//                            if (!canStartScan()) {
//                                Timber.w("⚠️ Cannot start scan: preconditions not met")
//                                _errors.emit("掃描條件不滿足，請檢查是否已選擇必要信息")
//                                return
//                            }
//
//                            Timber.d("✅ Continuous mode: Starting RFID scan")
//                            startRfidScan(currentMode)
//                        } else {
//                            Timber.d("⏭️ Continuous mode: Already scanning, ignoring StartScan")
//                        }
//                    }
//                }
                if (currentState.isScanning) {
                    Timber.d("⏭️ Already scanning, ignoring")
                    return
                }
                if (!canStartScan()) {
                    Timber.w("⚠️ Cannot start scan: preconditions not met")
                    _errors.emit("掃描條件不滿足，請檢查是否已選擇必要信息")
                    return
                }

                val ctx = currentScanContextProvider?.invoke() ?: ScanContext.BASKET_SCAN

                // 🆕 搜索類 context 不管 mode，一律條碼掃描
                if (ctx == ScanContext.PRODUCT_SEARCH || ctx == ScanContext.WAREHOUSE_SEARCH) {
                    Timber.d("✅ Search context ($ctx): Triggering barcode scan")
                    startBarcodeScan(ctx)
                    return
                }

                // 一般 BASKET_SCAN 依 mode 決定
                when (currentMode) {
                    ScanMode.SINGLE -> {
                        Timber.d("✅ Single mode: Triggering barcode scan (context=$ctx)")
                        startBarcodeScan(ctx)
                    }
                    ScanMode.CONTINUOUS -> {
                        Timber.d("✅ Continuous mode: Starting RFID scan")
                        startRfidScan(currentMode)
                    }
                }
            }

            is ScanTriggerEvent.StopScan -> {
                // 單次模式下，如果正在條碼掃描，則取消掃描
//                if (currentMode == ScanMode.SINGLE && currentState.isScanning && currentState.scanType == ScanType.BARCODE) {
//                    Timber.d("🛑 Single mode: Cancelling barcode scan")
//                    stopScanning()
//                }
//                // 連續模式或 RFID 掃描時，停止掃描
//                else if (currentState.isScanning) {
//                    Timber.d("🛑 Stopping scan (Mode: $currentMode, Type: ${currentState.scanType})")
//                    stopScanning()
//                } else {
//                    Timber.d("⏭️ Not scanning, ignoring StopScan")
//                }

                if (currentMode == ScanMode.SINGLE &&
                    currentState.isScanning &&
                    currentState.scanType == ScanType.BARCODE) {
                    Timber.d("⏭️ SINGLE+BARCODE: ignoring key-up, waiting for soft scan result or timeout")
                    return
                }
                // 連續模式 RFID 或其他：正常停止
                if (currentState.isScanning) {
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
//     fun startBarcodeScan(context: ScanContext = ScanContext.BASKET_SCAN) {
//        Timber.d("🚀 Starting barcode scan (context: $context)")
//        screenBrightnessManager.lockPowerState()
//
//        // 取消之前的掃描任務
//        currentScanJob?.cancel()
//
//        _scanState.update {
//            it.copy(
//                isScanning = true,
//                scanType = ScanType.BARCODE,
//                context = context
//            )
//        }
//
//        currentScanJob = kotlinx.coroutines.GlobalScope.launch {
//            try {
//                rfidManager.startBarcodeScan()
//                    .catch { error ->
//                        Timber.e(error, "Barcode scan error")
//                        _errors.emit("條碼掃描失敗: ${error.message}")
//                        resetScanState()
//                    }
//                    .collect { barcode ->
//                        Timber.d("📦 Barcode scanned: $barcode")
//
//                        // 發送掃描結果
//                        _scanResults.emit(
//                            ScanResult.BarcodeScanned(
//                                barcode = barcode,
//                                context = context
//                            )
//                        )
//
//                        // 條碼掃描後自動停止
//                        if (context == ScanContext.BASKET_SCAN) {
//                            stopScanning()
//                        }
//                        // PRODUCT_SEARCH 上下文會保持掃描狀態，等待下一個條碼
//                    }
//            } catch (e: Exception) {
//                if (e !is kotlinx.coroutines.CancellationException) {
//                    Timber.e(e, "Failed to start barcode scan")
//                    _errors.emit("啟動條碼掃描失敗: ${e.message}")
//                    resetScanState()
//                } else {
//                    Timber.d("Barcode scan cancelled")
//                }
//            }
//        }
//    }

    fun startBarcodeScan(context: ScanContext = ScanContext.BASKET_SCAN) {
        Timber.d("🚀 Starting barcode scan (context: $context)")
        screenBrightnessManager.lockPowerState()
        currentScanJob?.cancel()

        _scanState.update {
            it.copy(
                isScanning = true,
                scanType = ScanType.BARCODE,
                context = context
            )
        }

        val alreadyWarm = dataWedge.isOpened()

        if (!persistentReceiverInstalled) {
            dataWedge.registerScanReceiver { barcode ->
                kotlinx.coroutines.GlobalScope.launch {
                    _scanResults.emit(ScanResult.BarcodeScanned(barcode = barcode, context = context))
                    stopScanning()
                }
            }
        }

//        dataWedge.registerScanReceiver { barcode ->
//            kotlinx.coroutines.GlobalScope.launch {
//                _scanResults.emit(
//                    ScanResult.BarcodeScanned(barcode = barcode, context = context)
//                )
//                stopScanning()
//            }
//        }

        dataWedge.enableScanner()

        currentScanJob = kotlinx.coroutines.GlobalScope.launch {
            try {
                val warmupMs = if (alreadyWarm) 30L else 200L
                Timber.d("🔥 Warmup delay: ${warmupMs}ms (alreadyWarm=$alreadyWarm)")
                kotlinx.coroutines.delay(warmupMs)

                if (!_scanState.value.isScanning ||
                    _scanState.value.scanType != ScanType.BARCODE) return@launch

                Timber.d("📸 Firing soft scan trigger")
                dataWedge.startSoftScan()

                kotlinx.coroutines.delay(5_000)
                if (_scanState.value.isScanning && _scanState.value.scanType == ScanType.BARCODE) {
                    Timber.w("⏱️ Barcode scan timeout (5s), auto stopping")
                    stopScanning()
                }
            } catch (_: kotlinx.coroutines.CancellationException) { }
        }
    }

    /**
     * 保持條碼掃描頭常開（laser 可即時觸發）
     */
    fun setBarcodeKeepWarm(enabled: Boolean) {
        keepBarcodeWarm = enabled
        if (enabled) {
            Timber.d("🔥 Barcode keep-warm ENABLED (scanner will stay open)")
            dataWedge.cancelPendingDisable()
            dataWedge.enableScanner()
            installPersistentReceiver()
        } else {
            Timber.d("❄️ Barcode keep-warm DISABLED")
            removePersistentReceiver()
            dataWedge.cancelPendingDisable()
            if (!_scanState.value.isScanning) {
                dataWedge.disableScanner()
            }
        }
    }

    /**
     * 安裝常駐 receiver（keep-warm 時使用）
     * 根據當前 context provider 自動路由結果
     */
    private fun installPersistentReceiver() {
        if (persistentReceiverInstalled) return

        dataWedge.registerScanReceiver { barcode ->
            // 優先使用當前掃描狀態的 context，否則用 provider 取得
            val ctx = if (_scanState.value.isScanning) {
                _scanState.value.context
            } else {
                currentScanContextProvider?.invoke() ?: ScanContext.BASKET_SCAN
            }

            kotlinx.coroutines.GlobalScope.launch {
                Timber.d("📦 [persistent] Barcode → context=$ctx")
                _scanResults.emit(ScanResult.BarcodeScanned(barcode, ctx))
                // 若有進行中的 scan job，停掉
                if (_scanState.value.isScanning) {
                    stopScanning()
                }
            }
        }
        persistentReceiverInstalled = true
        Timber.d("📡 Persistent receiver installed")
    }

    private fun removePersistentReceiver() {
        if (!persistentReceiverInstalled) return
        dataWedge.unregisterScanReceiver()
        persistentReceiverInstalled = false
        Timber.d("📡 Persistent receiver removed")
    }

    /**
     * 開始 RFID 掃描
     */
    suspend fun startRfidScan(mode: ScanMode) {
        Timber.d("🚀 Starting RFID scan with mode: $mode")

        screenBrightnessManager.lockPowerState()

        if (_scanState.value.isScanning) {
            Timber.w("⚠️ Already scanning, stopping first")
            stopScanning()
            kotlinx.coroutines.delay(100)
        }

        // 取消之前的掃描任務
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
//        val currentState = _scanState.value
//        Timber.d("🛑 Stopping scan (type: ${currentState.scanType})")
//
//        try {
//            // 取消當前掃描任務
//            currentScanJob?.cancel()
//            currentScanJob = null
//
//            // 停止 RFIDManager（這會關閉 Flow）
////            rfidManager.stopScan()
//            when (currentState.scanType) {
//                ScanType.BARCODE -> {
//                    // 🆕 關閉 DataWedge 掃描頭
//                    dataWedge.stopSoftScan()
//                    dataWedge.disableScanner()
//                    dataWedge.unregisterScanReceiver()
//                }
//                ScanType.RFID -> {
//                    rfidManager.stopScan()
//                }
//                ScanType.NONE -> { /* nothing */ }
//            }
//            Timber.d("✅ RFIDManager stopped")
//        } catch (e: Exception) {
//            Timber.e(e, "Error stopping scan")
//        }
//
//        resetScanState()
//
//        screenBrightnessManager.unlockPowerState()
        val currentState = _scanState.value
        Timber.d("🛑 Stopping scan (type: ${currentState.scanType})")

        try {
            currentScanJob?.cancel()
            currentScanJob = null

            when (currentState.scanType) {
                ScanType.BARCODE -> {
                    dataWedge.stopSoftScan()
//                    dataWedge.unregisterScanReceiver()
//                    if (keepBarcodeWarm) {
//                        dataWedge.cancelPendingDisable()
//                        Timber.d("🔥 keepBarcodeWarm=true, scanner stays open")
//                    } else {
//                        dataWedge.scheduleDisable(10_000)
//                    }
                    if (keepBarcodeWarm) {
                        dataWedge.cancelPendingDisable()
                        Timber.d("🔥 keepBarcodeWarm=true, receiver & scanner stay open")
                    } else {
                        removePersistentReceiver()
                        dataWedge.scheduleDisable(10_000)
                    }
                }
                ScanType.RFID -> rfidManager.stopScan()
                ScanType.NONE -> { }
            }
            Timber.d("✅ Scan stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping scan")
        }

        resetScanState()
        screenBrightnessManager.unlockPowerState()
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

    /**
     * 清理資源（在 ViewModel onCleared 時調用）
     */
    fun cleanup() {
        Timber.d("🧹 ScanManager cleanup")
        keyEventCollectorJob?.cancel()
        keyEventCollectorJob = null
        keepBarcodeWarm = false
        removePersistentReceiver()
        stopScanning()
        dataWedge.disableScanner()
//        dataWedge.unregisterScanReceiver()
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
    PRODUCT_SEARCH,    // 產品搜索
    WAREHOUSE_SEARCH
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