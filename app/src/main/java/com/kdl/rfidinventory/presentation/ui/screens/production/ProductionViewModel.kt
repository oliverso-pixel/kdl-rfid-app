package com.kdl.rfidinventory.presentation.ui.screens.production

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.model.Batch
import com.kdl.rfidinventory.data.model.Product
import com.kdl.rfidinventory.data.model.ScannedBasket
import com.kdl.rfidinventory.data.repository.ProductionRepository
import com.kdl.rfidinventory.data.rfid.RFIDManager
import com.kdl.rfidinventory.data.rfid.RFIDTag
import com.kdl.rfidinventory.util.KeyEventHandler
import com.kdl.rfidinventory.util.NetworkState
import com.kdl.rfidinventory.util.ScanMode
import com.kdl.rfidinventory.util.ScanTriggerEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ProductionViewModel @Inject constructor(
    private val rfidManager: RFIDManager,
    private val productionRepository: ProductionRepository,
    private val keyEventHandler: KeyEventHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductionUiState())
    val uiState: StateFlow<ProductionUiState> = _uiState.asStateFlow()

    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Connected)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    init {
        Timber.d("ProductionViewModel initialized")
        loadProducts()
        observeKeyEvents()
    }

    /**
     * 監聽實體按鍵事件
     * 單次掃描模式：按鍵觸發 QR/Barcode 掃描
     * 連續掃描模式：按下開始 RFID 掃描，放開停止掃描
     */
    private fun observeKeyEvents() {
        Timber.d("Setting up key event observer")

        viewModelScope.launch {
            keyEventHandler.scanTriggerEvents
                .onStart { Timber.d("Key event flow started") }
                .onCompletion { Timber.d("Key event flow completed") }
                .catch { e -> Timber.e(e, "Key event flow error") }
                .collect { event ->
                    // ⭐ 獲取當前狀態的快照
                    val currentState = _uiState.value
                    val currentMode = currentState.scanMode
                    val isCurrentlyScanning = currentState.isScanning
                    val currentScanType = currentState.scanType

                    Timber.d("🔑 KeyEvent: $event | Mode: $currentMode | Scanning: $isCurrentlyScanning | Type: $currentScanType")

                    when (event) {
                        is ScanTriggerEvent.StartScan -> {
                            Timber.d("📱 Key trigger: Start scan")

                            // 檢查是否已選擇產品和批次
                            if (currentState.selectedProduct == null || currentState.selectedBatch == null) {
                                Timber.w("⚠️ Cannot scan: Product or batch not selected")
                                _uiState.update { it.copy(error = "請先選擇產品和批次") }
                                return@collect
                            }

                            when (currentMode) {
                                ScanMode.SINGLE -> {
                                    // ⭐ 單次掃描模式：實體按鍵觸發 QR/Barcode 掃描
                                    if (!isCurrentlyScanning) {
                                        Timber.d("✅ Single mode: Triggering barcode scan")
                                        startBarcodeScan()
                                    } else {
                                        Timber.d("⏭️ Single mode: Already scanning ($currentScanType), ignoring")
                                    }
                                }
                                ScanMode.CONTINUOUS -> {
                                    // 連續掃描模式：按下開始 RFID 掃描
                                    if (!isCurrentlyScanning) {
                                        Timber.d("✅ Continuous mode: Starting RFID scan")
                                        startRfidScan()
                                    } else {
                                        Timber.d("⏭️ Continuous mode: Already scanning ($currentScanType)")
                                    }
                                }
                            }
                        }

                        is ScanTriggerEvent.StopScan -> {
                            Timber.d("🛑 Key trigger: Stop scan")

                            when (currentMode) {
                                ScanMode.SINGLE -> {
                                    Timber.d("⏭️ Single mode: Key release ignored (auto-stop)")
                                }
                                ScanMode.CONTINUOUS -> {
                                    // ⭐ 連續模式：強制停止，不管當前是什麼類型的掃描
                                    if (isCurrentlyScanning) {
                                        Timber.d("✅ Continuous mode: Stopping scan (type was: $currentScanType)")
                                        stopScanning()

                                        // ⭐ 雙重確認機制
                                        viewModelScope.launch {
                                            kotlinx.coroutines.delay(150)
                                            val stillScanning = _uiState.value.isScanning
                                            if (stillScanning) {
                                                Timber.w("⚠️ Still scanning after stop! Type: ${_uiState.value.scanType}")
                                                Timber.w("🔧 Forcing complete stop...")
                                                rfidManager.stopScan()
                                                _uiState.update {
                                                    it.copy(
                                                        isScanning = false,
                                                        scanType = ScanType.NONE
                                                    )
                                                }
                                            } else {
                                                Timber.d("✅ Scan successfully stopped and verified")
                                            }
                                        }
                                    } else {
                                        Timber.d("⏭️ Not scanning, ignoring stop trigger")
                                    }
                                }
                            }
                        }

                        is ScanTriggerEvent.ClearList -> {
                            Timber.d("🗑️ Key trigger: Clear list")
                            clearBaskets()
                        }
                    }
                }
        }

        Timber.d("Key event observer setup complete")
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            productionRepository.getProductionOrders()
                .onSuccess { orders ->
                    val products = orders.map { order ->
                        Product(
                            id = order.productId,
                            name = order.productName,
                            maxBasketCapacity = 60,
                            imageUrl = "https://via.placeholder.com/150"
                        )
                    }
                    _uiState.update {
                        it.copy(
                            products = products,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            error = error.message,
                            isLoading = false
                        )
                    }
                }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun selectProduct(product: Product) {
        _uiState.update {
            it.copy(
                selectedProduct = product,
                showProductDialog = false
            )
        }
        loadBatchesForProduct(product.id)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadBatchesForProduct(productId: String) {
        val mockBatches = listOf(
            Batch(
                id = "BATCH-2025-001",
                productId = productId,
                totalQuantity = 1000,
                remainingQuantity = 500,
                productionDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            ),
            Batch(
                id = "BATCH-2025-002",
                productId = productId,
                totalQuantity = 800,
                remainingQuantity = 300,
                productionDate = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_DATE)
            )
        )
        _uiState.update { it.copy(batches = mockBatches, showBatchDialog = true) }
    }

    fun selectBatch(batch: Batch) {
        _uiState.update {
            it.copy(
                selectedBatch = batch,
                showBatchDialog = false
            )
        }
    }

    /**
     * ⭐ UI 按鈕觸發的掃描（支持兩種模式）
     * - SINGLE 模式：點擊開始 RFID 近距離掃描，掃到後自動停止
     * - CONTINUOUS 模式：點擊開始連續 RFID 掃描，再次點擊停止
     */
    fun toggleScanFromButton() {
        val selectedProduct = _uiState.value.selectedProduct
        val selectedBatch = _uiState.value.selectedBatch

        if (selectedProduct == null || selectedBatch == null) {
            Timber.w("Cannot start scan: Product or batch not selected")
            _uiState.update { it.copy(error = "請先選擇產品和批次") }
            return
        }

        if (_uiState.value.isScanning) {
            // 如果正在掃描，則停止（適用於連續模式）
            Timber.d("🛑 Stopping scan from UI button")
            stopScanning()
        } else {
            // 根據當前模式開始掃描
            when (_uiState.value.scanMode) {
                ScanMode.SINGLE -> {
                    Timber.d("🚀 Starting RFID scan from UI button (SINGLE mode - short range)")
                    startRfidScan()
                }
                ScanMode.CONTINUOUS -> {
                    Timber.d("🚀 Starting RFID scan from UI button (CONTINUOUS mode)")
                    startRfidScan()
                }
            }
        }
    }

    /**
     * ⭐ 開始條碼掃描（QR/Barcode）
     * 由實體按鍵觸發（僅在 SINGLE 模式）
     */
    private fun startBarcodeScan() {
        Timber.d("🚀 Starting barcode scan (triggered by physical button)")
        _uiState.update { it.copy(isScanning = true, scanType = ScanType.BARCODE) }

        viewModelScope.launch {
            try {
                rfidManager.startBarcodeScan()
                    .catch { error ->
                        Timber.e(error, "Barcode scan error")
                        _uiState.update {
                            it.copy(
                                error = "條碼掃描失敗: ${error.message}",
                                isScanning = false,
                                scanType = ScanType.NONE
                            )
                        }
                    }
                    .collect { barcode ->
                        Timber.d("📦 Barcode scanned: $barcode")
                        handleScannedBarcode(barcode)

                        // ⭐ 條碼掃描後自動停止並確保狀態清理
                        Timber.d("⏹️ Barcode scan complete, stopping...")
                        _uiState.update {
                            it.copy(
                                isScanning = false,
                                scanType = ScanType.NONE
                            )
                        }

                        // ⭐ 確保 RFIDManager 也停止
                        rfidManager.stopScan()
                        Timber.d("✅ Barcode scan fully stopped")
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start barcode scan")
                _uiState.update {
                    it.copy(
                        error = "啟動條碼掃描失敗: ${e.message}",
                        isScanning = false,
                        scanType = ScanType.NONE
                    )
                }
            }
        }
    }

    /**
     * ⭐ 開始 RFID 掃描
     * 支持兩種觸發方式：
     * 1. UI 按鈕觸發（SINGLE 和 CONTINUOUS 模式）
     * 2. 實體按鍵觸發（僅 CONTINUOUS 模式）
     */
    private fun startRfidScan() {
        val mode = _uiState.value.scanMode
        val currentScanType = _uiState.value.scanType

        Timber.d("🚀 Starting RFID scan with mode: $mode (current scanType: $currentScanType)")

        // ⭐ 如果已經在掃描，先停止
        if (_uiState.value.isScanning) {
            Timber.w("⚠️ Already scanning, stopping previous scan first")
            stopScanning()
            // 等待一小段時間
            viewModelScope.launch {
                kotlinx.coroutines.delay(100)
                actuallyStartRfidScan(mode)
            }
        } else {
            actuallyStartRfidScan(mode)
        }
    }

    private fun actuallyStartRfidScan(mode: ScanMode) {
        Timber.d("🔄 Actually starting RFID scan with mode: $mode")
        _uiState.update { it.copy(isScanning = true, scanType = ScanType.RFID) }

        viewModelScope.launch {
            try {
                rfidManager.startScan(mode)
                    .catch { error ->
                        Timber.e(error, "RFID scan error")
                        _uiState.update {
                            it.copy(
                                error = "RFID掃描失敗: ${error.message}",
                                isScanning = false,
                                scanType = ScanType.NONE
                            )
                        }
                    }
                    .collect { tag ->
                        Timber.d("📡 RFID Tag scanned: ${tag.uid}")
                        handleScannedRfidTag(tag)

                        // ⭐ SINGLE 模式下 RFID 掃描後自動停止
                        if (mode == ScanMode.SINGLE) {
                            Timber.d("⏹️ SINGLE mode: Auto-stopping RFID scan after tag read")
                            stopScanning()
                        }
                        // CONTINUOUS 模式會持續掃描，直到手動停止
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start RFID scan")
                _uiState.update {
                    it.copy(
                        error = "啟動RFID掃描失敗: ${e.message}",
                        isScanning = false,
                        scanType = ScanType.NONE
                    )
                }
            }
        }
    }

    fun stopScanning() {
        val currentScanType = _uiState.value.scanType
        val wasScanning = _uiState.value.isScanning
        val currentMode = _uiState.value.scanMode

        Timber.d("🛑 Stopping scan (mode: $currentMode, type: $currentScanType, wasScanning: $wasScanning)")

        // ⭐ 停止 RFID Manager
        try {
            rfidManager.stopScan()
            Timber.d("✅ RFIDManager.stopScan() called")
        } catch (e: Exception) {
            Timber.e(e, "❌ Error stopping RFID scan")
        }

        // ⭐ 立即更新 UI 狀態
        _uiState.update {
            it.copy(
                isScanning = false,
                scanType = ScanType.NONE
            )
        }

        Timber.d("✅ Scan stopped, UI state updated (isScanning: ${_uiState.value.isScanning}, scanType: ${_uiState.value.scanType})")
    }

    /**
     * ⭐ 處理掃描到的條碼
     */
    private fun handleScannedBarcode(barcode: String) {
        Timber.d("🔍 Processing barcode: $barcode")

        val product = _uiState.value.selectedProduct ?: return

        // 檢查是否重複掃描
        val existingBasketIndex = _uiState.value.scannedBaskets.indexOfFirst { it.uid == barcode }

        if (existingBasketIndex != -1) {
            // 重複掃描
            val existingBasket = _uiState.value.scannedBaskets[existingBasketIndex]
            val updatedBasket = existingBasket.copy(
                scanCount = existingBasket.scanCount + 1,
                lastScannedTime = System.currentTimeMillis()
            )

            _uiState.update { state ->
                state.copy(
                    scannedBaskets = state.scannedBaskets.toMutableList().apply {
                        set(existingBasketIndex, updatedBasket)
                    },
                    totalScanCount = state.totalScanCount + 1
                )
            }

            Timber.d("🔁 Duplicate barcode scan: $barcode, count: ${updatedBasket.scanCount}")
            _uiState.update {
                it.copy(
                    successMessage = "籃子 ${barcode.takeLast(8)} 重複掃描 (第 ${updatedBasket.scanCount} 次)"
                )
            }
        } else {
            // 新條碼
            val newBasket = ScannedBasket(
                uid = barcode,
                quantity = product.maxBasketCapacity,
                rssi = 0,  // 條碼沒有信號強度
                scanCount = 1,
                firstScannedTime = System.currentTimeMillis(),
                lastScannedTime = System.currentTimeMillis()
            )

            _uiState.update { state ->
                state.copy(
                    scannedBaskets = state.scannedBaskets + newBasket,
                    totalScanCount = state.totalScanCount + 1
                )
            }

            Timber.d("✨ New barcode scanned: $barcode")
        }
    }

    /**
     * ⭐ 處理掃描到的 RFID 標籤
     */
    private fun handleScannedRfidTag(tag: RFIDTag) {
        Timber.d("🔍 Processing RFID tag: ${tag.uid}")

        val product = _uiState.value.selectedProduct ?: return

        val existingBasketIndex = _uiState.value.scannedBaskets.indexOfFirst { it.uid == tag.uid }

        if (existingBasketIndex != -1) {
            // 重複掃描
            val existingBasket = _uiState.value.scannedBaskets[existingBasketIndex]
            val updatedBasket = existingBasket.copy(
                scanCount = existingBasket.scanCount + 1,
                lastScannedTime = System.currentTimeMillis(),
                rssi = tag.rssi  // 更新信號強度
            )

            _uiState.update { state ->
                state.copy(
                    scannedBaskets = state.scannedBaskets.toMutableList().apply {
                        set(existingBasketIndex, updatedBasket)
                    },
                    totalScanCount = state.totalScanCount + 1
                )
            }

            Timber.d("🔁 Duplicate RFID scan: ${tag.uid}, count: ${updatedBasket.scanCount}")
            _uiState.update {
                it.copy(
                    successMessage = "籃子 ${tag.uid.takeLast(8)} 重複掃描 (第 ${updatedBasket.scanCount} 次)"
                )
            }
        } else {
            // 新標籤
            val newBasket = ScannedBasket(
                uid = tag.uid,
                quantity = product.maxBasketCapacity,
                rssi = tag.rssi,
                scanCount = 1,
                firstScannedTime = System.currentTimeMillis(),
                lastScannedTime = System.currentTimeMillis()
            )

            _uiState.update { state ->
                state.copy(
                    scannedBaskets = state.scannedBaskets + newBasket,
                    totalScanCount = state.totalScanCount + 1
                )
            }

            Timber.d("✨ New RFID tag scanned: ${tag.uid}")
        }
    }

    fun updateBasketQuantity(uid: String, newQuantity: Int) {
        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets.map { basket ->
                    if (basket.uid == uid) {
                        basket.copy(quantity = newQuantity)
                    } else {
                        basket
                    }
                }
            )
        }
    }

    fun removeBasket(uid: String) {
        val basketToRemove = _uiState.value.scannedBaskets.find { it.uid == uid }
        val scanCountToRemove = basketToRemove?.scanCount ?: 0

        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets.filter { it.uid != uid },
                totalScanCount = (state.totalScanCount - scanCountToRemove).coerceAtLeast(0)
            )
        }

        Timber.d("🗑️ Basket removed: $uid, removed $scanCountToRemove scans")
    }

    fun resetBasketScanCount(uid: String) {
        val basket = _uiState.value.scannedBaskets.find { it.uid == uid }
        val oldScanCount = basket?.scanCount ?: 1
        val scanCountDifference = oldScanCount - 1

        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets.map { basket ->
                    if (basket.uid == uid) {
                        basket.copy(
                            scanCount = 1,
                            firstScannedTime = System.currentTimeMillis(),
                            lastScannedTime = System.currentTimeMillis()
                        )
                    } else {
                        basket
                    }
                },
                totalScanCount = (state.totalScanCount - scanCountDifference).coerceAtLeast(state.scannedBaskets.size)
            )
        }

        Timber.d("🔄 Basket scan count reset: $uid")
    }

    fun submitProduction() {
        viewModelScope.launch {
            val state = _uiState.value
            val product = state.selectedProduct ?: return@launch
            val batch = state.selectedBatch ?: return@launch
            val baskets = state.scannedBaskets

            if (baskets.isEmpty()) {
                _uiState.update { it.copy(error = "請至少掃描一個籃子") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, showConfirmDialog = false) }

            val isOnline = _networkState.value is NetworkState.Connected
            var successCount = 0
            var failCount = 0

            baskets.forEach { basket ->
                productionRepository.startProduction(
                    uid = basket.uid,
                    productId = product.id,
                    batchId = batch.id,
                    quantity = basket.quantity,
                    productionDate = batch.productionDate,
                    isOnline = isOnline
                ).onSuccess {
                    successCount++
                }.onFailure {
                    failCount++
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = "✅ 成功提交: $successCount 個，失敗: $failCount 個\n可繼續掃描下一批籃子",
                    scannedBaskets = emptyList(),
                    totalScanCount = 0,
                )
            }

            Timber.d("📤 Production submitted: success=$successCount, fail=$failCount")
        }
    }

    fun setScanMode(mode: ScanMode) {
        val oldMode = _uiState.value.scanMode

        Timber.d("🔄 Changing scan mode from $oldMode to $mode")

        // ⭐ 如果正在掃描，先停止
        if (_uiState.value.isScanning) {
            Timber.d("⏹️ Stopping active scan before mode change")
            stopScanning()
        }

        // ⭐ 等待一小段時間確保完全停止
        viewModelScope.launch {
            kotlinx.coroutines.delay(200)

            // ⭐ 完全重置掃描相關狀態
            _uiState.update {
                it.copy(
                    scanMode = mode,
                    isScanning = false,
                    scanType = ScanType.NONE
                )
            }

            Timber.d("✅ Scan mode changed to: $mode, state reset complete")
        }
    }

    fun showProductDialog() {
        _uiState.update { it.copy(showProductDialog = true) }
    }

    fun showConfirmDialog() {
        if (_uiState.value.scannedBaskets.isEmpty()) {
            _uiState.update { it.copy(error = "請至少掃描一個籃子") }
            return
        }
        _uiState.update { it.copy(showConfirmDialog = true) }
    }

    fun dismissDialog() {
        _uiState.update {
            it.copy(
                showProductDialog = false,
                showBatchDialog = false,
                showConfirmDialog = false
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun resetAll() {
        stopScanning()
        _uiState.update {
            it.copy(
                selectedProduct = null,
                selectedBatch = null,
                scannedBaskets = emptyList(),
                isScanning = false,
                scanType = ScanType.NONE,
                totalScanCount = 0
            )
        }
        Timber.d("🔄 All reset")
    }

    fun clearBaskets() {
        stopScanning()
        _uiState.update {
            it.copy(
                scannedBaskets = emptyList(),
                totalScanCount = 0,
                isScanning = false,
                scanType = ScanType.NONE
            )
        }
        Timber.d("🗑️ Baskets cleared")
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("ProductionViewModel cleared")
        stopScanning()
    }
}

/**
 * ⭐ 掃描類型枚舉
 */
enum class ScanType {
    NONE,       // 未掃描
    BARCODE,    // 條碼掃描中
    RFID        // RFID掃描中
}

data class ProductionUiState(
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val scanMode: ScanMode = ScanMode.SINGLE,
    val scanType: ScanType = ScanType.NONE,  // ⭐ 當前掃描類型
    val products: List<Product> = emptyList(),
    val batches: List<Batch> = emptyList(),
    val selectedProduct: Product? = null,
    val selectedBatch: Batch? = null,
    val scannedBaskets: List<ScannedBasket> = emptyList(),
    val totalScanCount: Int = 0,
    val showProductDialog: Boolean = false,
    val showBatchDialog: Boolean = false,
    val showConfirmDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)