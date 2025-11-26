package com.kdl.rfidinventory.presentation.ui.screens.production

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.repository.BasketRepository
import com.kdl.rfidinventory.data.repository.BasketValidationResult
import com.kdl.rfidinventory.data.repository.ProductionRepository
import com.kdl.rfidinventory.util.rfid.RFIDTag
import com.kdl.rfidinventory.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class ProductionViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val productionRepository: ProductionRepository,
    private val basketRepository: BasketRepository,
    private val webSocketManager: WebSocketManager,
    private val pendingOperationDao: PendingOperationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductionUiState())
    val uiState: StateFlow<ProductionUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = webSocketManager.isOnline

    // 綜合網絡狀態（結合 isOnline 和待同步數量）
    val networkState: StateFlow<NetworkState> = combine(
        isOnline,
        pendingOperationDao.getPendingCount()
    ) { online, pendingCount ->
        if (online) {
            NetworkState.Connected
        } else {
            NetworkState.Disconnected(pendingCount)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NetworkState.Disconnected(0)
    )

    val scanState = scanManager.scanState

    private val validatingUids = mutableSetOf<String>()

    init {
        Timber.d("ProductionViewModel initialized")
        loadProducts()
        initializeScanManager()
        observeScanResults()
        observeScanErrors()
        observeNetworkState()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            productionRepository.getProductionOrders()
                .onSuccess { orders ->
                    val products = orders.map { order ->
                        Product(
                            id = order.productId,
                            barcodeId = order.barcodeId,
                            qrcodeId = order.qrcodeId,
                            name = order.productName,
                            maxBasketCapacity = 60,
                            imageUrl = order.imageUrl
                        )
                    }
                    _uiState.update { it.copy(products = products, isLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initializeScanManager() {
        scanManager.initialize(
            scope = viewModelScope,
            scanMode = _uiState.map { it.scanMode }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                ScanMode.SINGLE
            ),
            canStartScan = {
                // 檢查是否已選擇產品和批次（除了產品搜索場景）
                _uiState.value.showProductDialog || (_uiState.value.selectedProduct != null && _uiState.value.selectedBatch != null)
            }
        )
    }

    private fun observeScanResults() {
        viewModelScope.launch {
            scanManager.scanResults.collect { result ->
                when (result) {
                    is ScanResult.BarcodeScanned -> {
                        when (result.context) {
                            ScanContext.PRODUCT_SEARCH -> {
                                // 產品搜索
                                updateProductSearchQuery(result.barcode)
                            }
                            ScanContext.BASKET_SCAN -> {
                                // 籃子掃描
                                handleScannedBarcode(result.barcode)
                            }
                        }
                    }
                    is ScanResult.RfidScanned -> {
                        handleScannedRfidTag(result.tag)
                    }
                    is ScanResult.ClearListRequested -> {
                        clearBaskets()
                    }
                }
            }
        }
    }

    private fun observeScanErrors() {
        viewModelScope.launch {
            scanManager.errors.collect { error ->
                _uiState.update { it.copy(error = error) }
            }
        }
    }

    private fun observeNetworkState() {
        viewModelScope.launch {
            networkState.collect { state ->
                Timber.d("📡 Production - Network state: $state")
            }
        }
    }

    fun setScanMode(mode: ScanMode) {
        viewModelScope.launch {
            if (scanManager.scanState.value.isScanning) {
                scanManager.stopScanning()
            }
            scanManager.changeScanMode(mode)
            _uiState.update { it.copy(scanMode = mode) }
        }
    }

    private fun handleScannedBarcode(barcode: String) {
        Timber.d("🔍 Processing barcode: $barcode")
        //addOrUpdateBasket(barcode, rssi = 0)
        validateAndAddBasket(barcode, rssi = 0)

    }

    private fun handleScannedRfidTag(tag: RFIDTag) {
        Timber.d("🔍 Processing RFID tag: ${tag.uid}")
        //addOrUpdateBasket(tag.uid, rssi = tag.rssi)
        validateAndAddBasket(tag.uid, rssi = tag.rssi)
    }

    fun clearBaskets() {
        scanManager.stopScanning()
        _uiState.update {
            it.copy(
                scannedBaskets = emptyList(),
                totalScanCount = 0
            )
        }
    }

    fun toggleScanFromButton() {
        viewModelScope.launch {
            if (_uiState.value.selectedProduct == null || _uiState.value.selectedBatch == null) {
                _uiState.update { it.copy(error = "請先選擇產品和批次") }
                return@launch
            }
            if (scanManager.scanState.value.isScanning) {
                scanManager.stopScanning()
            } else {
                scanManager.startRfidScan(_uiState.value.scanMode)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateProductSearchQuery(query: String) {
        _uiState.update { it.copy(productSearchQuery = query) }

        if (query.length >= 6) {
            val filteredProducts = _uiState.value.products.filter { product ->
                product.id.lowercase().contains(query.lowercase()) ||
                        product.name.lowercase().contains(query.lowercase()) ||
                        (product.barcodeId?.toString()?.contains(query) == true) ||
                        (product.qrcodeId?.lowercase()?.contains(query.lowercase()) == true)
            }

            if (filteredProducts.size == 1) {
                Timber.d("🎯 Auto-selecting product: ${filteredProducts.first().name}")
                viewModelScope.launch {
                    kotlinx.coroutines.delay(300)
                    selectProduct(filteredProducts.first())
                }
            }
        }
    }

    fun clearProductSearchQuery() {
        _uiState.update { it.copy(productSearchQuery = "") }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun selectProduct(product: Product) {
        Timber.d("✅ Product selected: ${product.name}")

        _uiState.update {
            it.copy(
                selectedProduct = product,
                showProductDialog = false,
                productSearchQuery = ""
            )
        }

        // 停止產品搜索掃描
        scanManager.stopScanning()

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
            )
        )
        _uiState.update { it.copy(batches = mockBatches, showBatchDialog = true) }
    }

    fun selectBatch(batch: Batch) {
        _uiState.update { it.copy(selectedBatch = batch, showBatchDialog = false) }
    }

    /**
     *  驗證並添加籃子
     */
    private fun validateAndAddBasket(uid: String, rssi: Int) {
        // 防止重複驗證
        if (validatingUids.contains(uid)) {
            Timber.d("⏭️ Basket $uid is already being validated, skipping...")
            return
        }

        // 檢查是否已經在列表中
        val existingBasketIndex = _uiState.value.scannedBaskets.indexOfFirst { it.uid == uid }
        if (existingBasketIndex != -1) {
            // 重複掃描：更新計數
            updateExistingBasket(existingBasketIndex, uid, rssi)
            return
        }

        // 開始驗證
        viewModelScope.launch {
            validatingUids.add(uid)
            _uiState.update { it.copy(isValidating = true) }

            val online = isOnline.value
            Timber.d("🌐 Validating basket - isOnline: $online")
            val validationResult = basketRepository.validateBasketForProduction(uid, online)

            when (validationResult) {
                is BasketValidationResult.Valid -> {
                    Timber.d("✅ Basket validated successfully: $uid")
                    addNewBasket(uid, rssi, validationResult.basket)
                }

                is BasketValidationResult.NotRegistered -> {
                    Timber.w("⚠️ Basket not registered: $uid")
                    _uiState.update {
                        it.copy(
                            error = "籃子 ${uid.takeLast(8)} 尚未登記，請先在「籃子管理」中登記此籃子",
                            isValidating = false
                        )
                    }
                    // 單次模式停止掃描
                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }

                is BasketValidationResult.InvalidStatus -> {
                    Timber.w("⚠️ Basket has invalid status: $uid (${validationResult.currentStatus})")
                    val statusText = when (validationResult.currentStatus) {
                        BasketStatus.RECEIVED -> "已收貨"
                        BasketStatus.IN_STOCK -> "在庫中"
                        BasketStatus.SHIPPED -> "已出貨"
                        BasketStatus.SAMPLING -> "抽樣中"
                        BasketStatus.IN_PRODUCTION -> "生產中"
                        BasketStatus.UNASSIGNED -> "未分配"
                    }
                    _uiState.update {
                        it.copy(
                            error = "籃子 ${uid.takeLast(8)} 狀態為「${statusText}」，無法用於生產",
                            isValidating = false
                        )
                    }
                    // 單次模式停止掃描
                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }

                is BasketValidationResult.AlreadyInProduction -> {
                    Timber.w("⚠️ Basket is already in production: $uid")
                    _uiState.update {
                        it.copy(
                            error = "籃子 ${uid.takeLast(8)} 已在生產中，無法重複使用",
                            isValidating = false
                        )
                    }
                    // 單次模式停止掃描
                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }

                is BasketValidationResult.Error -> {
                    Timber.e("❌ Basket validation error: $uid - ${validationResult.message}")
                    _uiState.update {
                        it.copy(
                            error = "驗證籃子失敗: ${validationResult.message}",
                            isValidating = false
                        )
                    }
                    // 單次模式停止掃描
                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }
            }

            // 移除驗證標記
            kotlinx.coroutines.delay(500)
            validatingUids.remove(uid)
        }
    }

    /**
     * 更新現有籃子（重複掃描）
     */
    private fun updateExistingBasket(index: Int, uid: String, rssi: Int) {
        val existingBasket = _uiState.value.scannedBaskets[index]
        val updatedBasket = existingBasket.copy(
            scanCount = existingBasket.scanCount + 1,
            lastScannedTime = System.currentTimeMillis(),
            rssi = rssi
        )

        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets.toMutableList().apply {
                    set(index, updatedBasket)
                },
                totalScanCount = state.totalScanCount + 1,
                successMessage = "籃子 ${uid.takeLast(8)} 重複掃描 (第 ${updatedBasket.scanCount} 次)"
            )
        }
    }

    /**
     * 添加新籃子
     */
    private fun addNewBasket(uid: String, rssi: Int, basket: Basket) {
        val product = _uiState.value.selectedProduct ?: return

        val newBasket = ScannedBasket(
            uid = uid,
            quantity = product.maxBasketCapacity,
            rssi = rssi,
            scanCount = 1,
            firstScannedTime = System.currentTimeMillis(),
            lastScannedTime = System.currentTimeMillis()
        )

        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets + newBasket,
                totalScanCount = state.totalScanCount + 1,
                isValidating = false,
                successMessage = "✅ 籃子 ${uid.takeLast(8)} 已添加"
            )
        }

        // 單次掃描模式：掃描成功後自動停止
        if (_uiState.value.scanMode == ScanMode.SINGLE) {
            scanManager.stopScanning()
        }
    }

    private fun addOrUpdateBasket(uid: String, rssi: Int) {
        val product = _uiState.value.selectedProduct ?: return
        val existingBasketIndex = _uiState.value.scannedBaskets.indexOfFirst { it.uid == uid }

        if (existingBasketIndex != -1) {
            // 重複掃描
            val existingBasket = _uiState.value.scannedBaskets[existingBasketIndex]
            val updatedBasket = existingBasket.copy(
                scanCount = existingBasket.scanCount + 1,
                lastScannedTime = System.currentTimeMillis(),
                rssi = rssi
            )

            _uiState.update { state ->
                state.copy(
                    scannedBaskets = state.scannedBaskets.toMutableList().apply {
                        set(existingBasketIndex, updatedBasket)
                    },
                    totalScanCount = state.totalScanCount + 1,
                    successMessage = "籃子 ${uid.takeLast(8)} 重複掃描 (第 ${updatedBasket.scanCount} 次)"
                )
            }
        } else {
            // 新籃子
            val newBasket = ScannedBasket(
                uid = uid,
                quantity = product.maxBasketCapacity,
                rssi = rssi,
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
        }
    }

    fun updateBasketQuantity(uid: String, newQuantity: Int) {
        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets.map { basket ->
                    if (basket.uid == uid) basket.copy(quantity = newQuantity) else basket
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
                    } else basket
                },
                totalScanCount = (state.totalScanCount - scanCountDifference).coerceAtLeast(state.scannedBaskets.size)
            )
        }
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

            val online = isOnline.value
            Timber.d("📤 Submitting production - isOnline: $online")
            var successCount = 0
            var failCount = 0

            baskets.forEach { basket ->
                productionRepository.startProduction(
                    uid = basket.uid,
                    productId = product.id,
                    batchId = batch.id,
                    quantity = basket.quantity,
                    productionDate = batch.productionDate,
                    isOnline = online
                ).onSuccess {
                    successCount++
                }.onFailure {
                    failCount++
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = "✅ 成功: $successCount 個，失敗: $failCount 個",
                    scannedBaskets = emptyList(),
                    totalScanCount = 0
                )
            }
        }
    }

    fun showProductDialog() {
        _uiState.update { it.copy(showProductDialog = true, productSearchQuery = "") }

        // 延遲啟動產品搜索掃描
        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            if (_uiState.value.showProductDialog) {
                scanManager.startBarcodeScan(ScanContext.PRODUCT_SEARCH)
            }
        }
    }

    fun dismissDialog() {
        scanManager.stopScanning()
        _uiState.update {
            it.copy(
                showProductDialog = false,
                showBatchDialog = false,
                showConfirmDialog = false,
                productSearchQuery = ""
            )
        }
    }

    fun showConfirmDialog() {
        if (_uiState.value.scannedBaskets.isEmpty()) {
            _uiState.update { it.copy(error = "請至少掃描一個籃子") }
            return
        }
        _uiState.update { it.copy(showConfirmDialog = true) }
    }

    fun resetAll() {
        scanManager.stopScanning()
        _uiState.update {
            it.copy(
                selectedProduct = null,
                selectedBatch = null,
                scannedBaskets = emptyList(),
                totalScanCount = 0
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        scanManager.cleanup()
    }
}

data class ProductionUiState(
    val isLoading: Boolean = false,
    val isValidating: Boolean = false,
    val scanMode: ScanMode = ScanMode.SINGLE,
    val products: List<Product> = emptyList(),
    val batches: List<Batch> = emptyList(),
    val productSearchQuery: String = "",
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