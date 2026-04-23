package com.kdl.rfidinventory.presentation.ui.screens.production

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.dto.request.BasketUpdateItemDto
import com.kdl.rfidinventory.data.remote.dto.request.CommonDataDto
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.repository.AuthRepository
import com.kdl.rfidinventory.data.repository.BasketRepository
import com.kdl.rfidinventory.data.repository.ProductionRepository
import com.kdl.rfidinventory.domain.manager.rfid.RFIDTag
import com.kdl.rfidinventory.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class ProductionViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val productionRepository: ProductionRepository,
    private val basketRepository: BasketRepository,
    private val webSocketManager: WebSocketManager,
    private val pendingOperationDao: PendingOperationDao,
    private val authRepository: AuthRepository,
    private val json: Json
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductionUiState())
    val uiState: StateFlow<ProductionUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = webSocketManager.isOnline

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
                            itemcode = order.itemcode,
                            barcodeId = order.barcodeId,
                            qrcodeId = order.qrcodeId,
                            name = order.name,
                            maxBasketCapacity = order.maxBasketCapacity,
                            imageUrl = order.imageUrl,
                            btype = order.btype
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
                product.itemcode.lowercase().contains(query.lowercase()) ||
                        product.name.lowercase().contains(query.lowercase()) ||
                        (product.barcodeId?.toString()?.contains(query) == true) ||
                        (product.qrcodeId?.lowercase()?.contains(query.lowercase()) == true)
            }

            if (filteredProducts.size == 1) {
                Timber.d("🎯 Auto-selecting product: ${filteredProducts.first().name}")
                viewModelScope.launch {
                    delay(300)
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

        loadBatchesForProduct(product.itemcode)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadBatchesForProduct(productId: String) {
        viewModelScope.launch {
            productionRepository.getBatchesForDate()
                .onSuccess { allBatches ->
                    val filteredBatches = allBatches.filter { it.itemcode == productId }

                    _uiState.update {
                        it.copy(
                            batches = filteredBatches,
                            showBatchDialog = true
                        )
                    }

                    if (filteredBatches.size == 1) {
                        selectBatch(filteredBatches.first())
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = "獲取批次失敗: ${error.message}") }
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
            updateExistingBasket(existingBasketIndex, uid, rssi)
            return
        }

        viewModelScope.launch {
            validatingUids.add(uid)
            _uiState.update { it.copy(isValidating = true) }

            basketRepository.fetchBasket(uid, isOnline.value)
                .onSuccess { basket ->
                    when (basket.status) {
                        BasketStatus.UNASSIGNED -> {
                            Timber.d("✅ Basket valid for production: $uid")

                            val selectedProduct = _uiState.value.selectedProduct
                            if (selectedProduct != null) {
                                // 比對 basket.type 與 product.btype
                                // 注意：你需要在 Basket 中添加 type 字段
                                // 如果 API 回傳的 basket 沒有 type，可能需要從 BasketDetailResponse 中補上

                                // 暫時假設從 basket.bid 欄位判斷類型
                                val basketType = basket.type ?: 0  // 需要在 Basket 模型中添加 type 欄位

                                if (basketType != selectedProduct.btype) {
                                    _uiState.update {
                                        it.copy(
                                            error = "⚠️ 籃子類型不符！\n" +
                                                    "產品要求類型 ${selectedProduct.btype}，" +
                                                    "但籃子為類型 $basketType\n" +
                                                    "請使用正確的籃子款式"
                                        )
                                    }
//                                    return@onSuccess
                                } else {
                                    addNewBasket(uid, rssi, basket)
                                }
                            }
                        }
                        BasketStatus.IN_PRODUCTION -> {
                            _uiState.update {
                                it.copy(error = "籃子已在生產中 (批次: ${basket.batch?.batch_code})")
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
            tagCode = basket.tagCode,
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
//                successMessage = " 籃子 ${uid.takeLast(8)} 已添加"
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
            val product = _uiState.value.selectedProduct ?: return@launch
            val batch = _uiState.value.selectedBatch ?: return@launch
            val baskets = _uiState.value.scannedBaskets
            if (baskets.isEmpty()) return@launch

            _uiState.update { it.copy(isLoading = true, showConfirmDialog = false) }

            val currentUser = authRepository.getCurrentUser()?.username ?: "admin"

            val simplifiedBatch = mapOf(
                "batch_code" to batch.batch_code,
                "itemcode" to batch.itemcode,
                "productionDate" to batch.productionDate,
                "expireDate" to batch.expireDate
            )
            val batchJson = json.encodeToString(simplifiedBatch)

            val simplifiedProduct = Product(
                itemcode = product.itemcode,
                barcodeId = product.barcodeId,
                qrcodeId = product.qrcodeId,
                name = product.name,
                btype = product.btype,
                maxBasketCapacity = product.maxBasketCapacity,
                imageUrl = product.imageUrl
            )
            val productJson = json.encodeToString(simplifiedProduct)

//            val productJson = json.encodeToString(product)
//            val batchJson = json.encodeToString(batch)

            val commonData = CommonDataDto(
                product = productJson,
                batch = batchJson,
                updateBy = currentUser,
                status = "IN_PRODUCTION"
            )

            val items = baskets.map {
                BasketUpdateItemDto(
                    rfid = it.uid,
                    quantity = it.quantity
                )
            }

            basketRepository.updateBasket(
                updateType = "Production",
                commonData = commonData,
                items = items,
                isOnline = isOnline.value
            ).onSuccess {

                if (isOnline.value) {
                    refreshBatchAfterSubmit(batch.batch_code, baskets.size)
                } else {
                    updateBatchLocally(batch, baskets.sumOf { it.quantity })
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        scannedBaskets = emptyList(),
                        totalScanCount = 0,
                        successMessage = "✅ 生產提交成功"
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }

    /**
     * 提交後刷新 Batch 數據（在線模式）
     */
    private suspend fun refreshBatchAfterSubmit(batchCode: String, submittedCount: Int) {
        Timber.d("🔄 Refreshing batch data: $batchCode")

        productionRepository.getBatchDetail(batchCode)
            .onSuccess { updatedBatch ->
                // 更新 UI State 中的 selectedBatch
                _uiState.update {
                    it.copy(
                        selectedBatch = updatedBatch,
                        successMessage = buildString {
                            append("✅ 生產提交成功！\n")
                            append("已生產: ${updatedBatch.producedQuantity} / ${updatedBatch.targetQuantity}\n")

                            val remaining = updatedBatch.getRemainingTarget()
                            if (remaining > 0) {
                                append("剩餘: $remaining 個")
                            } else {
                                append("已達成目標！")
                            }
                        }
                    )
                }

                Timber.d("✅ Batch refreshed:")
                Timber.d("   Target: ${updatedBatch.targetQuantity}")
                Timber.d("   Produced: ${updatedBatch.producedQuantity}")
                Timber.d("   Remaining: ${updatedBatch.getRemainingTarget()}")

                // 如果達成目標，可以自動切換到下一個批次
                if (updatedBatch.isTargetReached()) {
                    handleBatchTargetReached(updatedBatch)
                }
            }
            .onFailure { error ->
                Timber.e(error, "Failed to refresh batch data")
                // 不顯示錯誤，因為提交已經成功
            }
    }

    /**
     * 離線模式：手動更新本地 Batch 數據
     */
    private fun updateBatchLocally(batch: Batch, submittedQuantity: Int) {
        val updatedBatch = batch.copy(
            producedQuantity = batch.producedQuantity + submittedQuantity
        )

        _uiState.update {
            it.copy(
                selectedBatch = updatedBatch,
                successMessage = buildString {
                    append(" 生產提交成功（離線）\n")
                    append("已生產: ${updatedBatch.producedQuantity} / ${updatedBatch.targetQuantity}")
                }
            )
        }

        Timber.d("📱 Batch updated locally: producedQuantity=${updatedBatch.producedQuantity}")
    }

    /**
     * 處理批次目標達成
     */
    private suspend fun handleBatchTargetReached(batch: Batch) {
        Timber.d("🎯 Batch target reached: ${batch.batch_code}")

        // 延遲 2 秒後顯示提示
        delay(2000)

        _uiState.update {
            it.copy(
                successMessage = "🎉 批次 ${batch.batch_code} 已達成目標！\n" +
                        "請選擇下一個批次繼續生產"
            )
        }

        // 可選：自動重置選擇（讓用戶選擇新批次）
        // resetBatchSelection()
    }

    /**
     *  重置批次選擇（保留產品選擇）
     */
    private fun resetBatchSelection() {
        scanManager.stopScanning()

        _uiState.update {
            it.copy(
                selectedBatch = null,
                scannedBaskets = emptyList(),
                totalScanCount = 0
            )
        }

        // 重新加載批次列表
        _uiState.value.selectedProduct?.let { product ->
            loadBatchesForProduct(product.itemcode)
        }
    }

    fun showProductDialog() {
        _uiState.update { it.copy(showProductDialog = true, productSearchQuery = "") }

        // 延遲啟動產品搜索掃描
        viewModelScope.launch {
            delay(300)
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