package com.kdl.rfidinventory.presentation.ui.screens.production

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.model.*
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
    private val productionRepository: ProductionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductionUiState())
    val uiState: StateFlow<ProductionUiState> = _uiState.asStateFlow()

    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Connected)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    val scanState = scanManager.scanState

    init {
        Timber.d("ProductionViewModel initialized")
        loadProducts()
        initializeScanManager()
        observeScanResults()
        observeScanErrors()
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
                val state = _uiState.value
                state.showProductDialog || (state.selectedProduct != null && state.selectedBatch != null)
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

    fun toggleScanFromButton() {
        viewModelScope.launch {
            val isScanning = scanManager.scanState.value.isScanning

            if (_uiState.value.selectedProduct == null || _uiState.value.selectedBatch == null) {
                _uiState.update { it.copy(error = "請先選擇產品和批次") }
                return@launch
            }

            if (isScanning) {
                scanManager.stopScanning()
            } else {
                scanManager.startRfidScan(_uiState.value.scanMode)
            }
        }
    }

    private fun handleScannedBarcode(barcode: String) {
        Timber.d("🔍 Processing barcode: $barcode")
        addOrUpdateBasket(barcode, rssi = 0)
    }

    private fun handleScannedRfidTag(tag: RFIDTag) {
        Timber.d("🔍 Processing RFID tag: ${tag.uid}")
        addOrUpdateBasket(tag.uid, rssi = tag.rssi)
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
                    successMessage = "✅ 成功: $successCount 個，失敗: $failCount 個",
                    scannedBaskets = emptyList(),
                    totalScanCount = 0
                )
            }
        }
    }

    fun setScanMode(mode: ScanMode) {
        viewModelScope.launch {
            scanManager.changeScanMode(mode)
            _uiState.update { it.copy(scanMode = mode) }
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
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

    fun clearBaskets() {
        scanManager.stopScanning()
        _uiState.update {
            it.copy(
                scannedBaskets = emptyList(),
                totalScanCount = 0
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanManager.stopScanning()
    }
}

data class ProductionUiState(
    val isLoading: Boolean = false,
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