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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
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
                            id = order.id,
                            barcodeId = order.barcodeId,
                            qrcodeId = order.qrcodeId,
                            name = order.name,
                            maxBasketCapacity = order.maxBasketCapacity,
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
//                            ScanContext.BASKET_SCAN -> {
//                                // 籃子掃描
//                                handleScannedBarcode(result.barcode)
//                            }
                            else -> handleScannedBarcode(result.barcode)
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
        validateAndAddBasket(barcode, rssi = 0)
    }

    private fun handleScannedRfidTag(tag: RFIDTag) {
        Timber.d("🔍 Processing RFID tag: ${tag.uid}")
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
        // 使用 mockBatches() 函數並過濾對應的 productId
//        val allBatches = mockBatches(productId)
//        val filteredBatches = allBatches.filter { it.productId == productId }
//
//        Timber.d("📦 Loading batches for product: $productId, found: ${filteredBatches.size}")
//
//        _uiState.update {
//            it.copy(
//                batches = filteredBatches,
//                showBatchDialog = true
//            )
//        }
//
//        // 如果只有一個批次，自動選擇
//        if (filteredBatches.size == 1) {
//            Timber.d("🎯 Auto-selecting single batch: ${filteredBatches.first().id}")
//            viewModelScope.launch {
//                kotlinx.coroutines.delay(300)
//                selectBatch(filteredBatches.first())
//            }
//        }
        viewModelScope.launch {
            // 使用 API 獲取當日批次
            productionRepository.getBatchesForDate()
                .onSuccess { allBatches ->
                    // 過濾出當前產品的批次
                    val filteredBatches = allBatches.filter { it.productId == productId }

                    _uiState.update {
                        it.copy(
                            batches = filteredBatches,
                            showBatchDialog = true
                        )
                    }

                    // 自動選擇邏輯...
                    if (filteredBatches.size == 1) {
                        selectBatch(filteredBatches.first())
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(error = "獲取批次失敗: ${it}") }
                }
        }
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

        viewModelScope.launch {
            validatingUids.add(uid)
            _uiState.update { it.copy(isValidating = true) }

//            val online = isOnline.value
//            Timber.d("🌐 Validating basket - isOnline: $online")
//            val validationResult = basketRepository.validateBasketForProduction(uid, online)
//
//            when (validationResult) {
//                is BasketValidationResult.Valid -> {
//                    Timber.d("✅ Basket validated successfully: $uid")
//                    addNewBasket(uid, rssi, validationResult.basket)
//                }
//
//                is BasketValidationResult.NotRegistered -> {
//                    Timber.w("⚠️ Basket not registered: $uid")
//                    _uiState.update {
//                        it.copy(
//                            error = "籃子 ${uid.takeLast(8)} 尚未登記，請先在「籃子管理」中登記此籃子",
//                            isValidating = false
//                        )
//                    }
//                    // 單次模式停止掃描
//                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
//                        scanManager.stopScanning()
//                    }
//                }
//
//                is BasketValidationResult.InvalidStatus -> {
//                    Timber.w("⚠️ Basket has invalid status: $uid (${validationResult.currentStatus})")
//                    val statusText = getBasketStatusText(validationResult.currentStatus)
//                    _uiState.update {
//                        it.copy(
//                            error = "籃子 ${uid.takeLast(8)} 狀態為「${statusText}」，無法用於生產",
//                            isValidating = false
//                        )
//                    }
//                    // 單次模式停止掃描
//                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
//                        scanManager.stopScanning()
//                    }
//                }
//
//                is BasketValidationResult.AlreadyInProduction -> {
//                    Timber.w("⚠️ Basket is already in production: $uid")
//                    _uiState.update {
//                        it.copy(
//                            error = "籃子 ${uid.takeLast(8)} 已在生產中，無法重複使用",
//                            isValidating = false
//                        )
//                    }
//                    // 單次模式停止掃描
//                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
//                        scanManager.stopScanning()
//                    }
//                }
//
//                is BasketValidationResult.Error -> {
//                    Timber.e("❌ Basket validation error: $uid - ${validationResult.message}")
//                    _uiState.update {
//                        it.copy(
//                            error = "驗證籃子失敗: ${validationResult.message}",
//                            isValidating = false
//                        )
//                    }
//                    // 單次模式停止掃描
//                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
//                        scanManager.stopScanning()
//                    }
//                }
//            }
//
//            // 移除驗證標記
//            kotlinx.coroutines.delay(500)
//            validatingUids.remove(uid)

            basketRepository.fetchBasket(uid, isOnline.value)
                .onSuccess { basket ->
                    when (basket.status) {
                        BasketStatus.UNASSIGNED -> {
                            Timber.d("✅ Basket valid for production: $uid")
                            addNewBasket(uid, rssi, basket)
                        }
                        BasketStatus.IN_PRODUCTION -> {
                            _uiState.update {
                                it.copy(error = "籃子已在生產中 (批次: ${basket.batch?.id})")
                            }
                        }
                        else -> {
                            val statusText = getBasketStatusText(basket.status)
                            _uiState.update {
                                it.copy(error = "籃子狀態錯誤: $statusText (需為未配置)")
                            }
                        }
                    }
                }
                .onFailure { error ->
                    val msg = if (error.message == "BASKET_NOT_REGISTERED" ||
                        error.message == "BASKET_NOT_FOUND_LOCAL") {
                        "籃子尚未登記，請先至管理介面登記"
                    } else {
                        "讀取失敗: ${error.message}"
                    }
                    _uiState.update { it.copy(error = msg) }
                }

            _uiState.update { it.copy(isValidating = false) }
            delay(300)
            validatingUids.remove(uid)

            if (_uiState.value.scanMode == ScanMode.SINGLE) {
                scanManager.stopScanning()
            }
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
                    product = product,
                    batch = batch,
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