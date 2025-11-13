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
import com.kdl.rfidinventory.util.NetworkState
import com.kdl.rfidinventory.util.ScanMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ProductionViewModel @Inject constructor(
    private val rfidManager: RFIDManager,
    private val productionRepository: ProductionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductionUiState())
    val uiState: StateFlow<ProductionUiState> = _uiState.asStateFlow()

    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Connected)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    init {
        loadProducts()
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
                            imageUrl = "https://via.placeholder.com/150"  // Mock 圖片
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
        // 載入該產品的批次
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

    fun startScanning() {
        val selectedProduct = _uiState.value.selectedProduct
        val selectedBatch = _uiState.value.selectedBatch

        if (selectedProduct == null || selectedBatch == null) {
            _uiState.update { it.copy(error = "請先選擇產品和批次") }
            return
        }

        _uiState.update { it.copy(isScanning = true) }

        viewModelScope.launch {
            rfidManager.startScan(_uiState.value.scanMode)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            error = "掃描失敗: ${error.message}",
                            isScanning = false
                        )
                    }
                }
                .collect { tag ->
                    handleScannedTag(tag)
                }
        }
    }

    fun stopScanning() {
        rfidManager.stopScan()
        _uiState.update { it.copy(isScanning = false) }
    }

    private fun handleScannedTag(tag: RFIDTag) {
        val product = _uiState.value.selectedProduct ?: return

        // 檢查是否已掃描過
        val existingBasket = _uiState.value.scannedBaskets.find { it.uid == tag.uid }

        if (existingBasket != null) {
            _uiState.update {
                it.copy(error = "籃子 ${tag.uid} 已掃描過")
            }
        } else {
            val newBasket = ScannedBasket(
                uid = tag.uid,
                quantity = product.maxBasketCapacity,  // 預設最大容量
                rssi = tag.rssi
            )

            _uiState.update { state ->
                state.copy(
                    scannedBaskets = state.scannedBaskets + newBasket
                )
            }

            // 單次掃描模式下停止掃描
            if (_uiState.value.scanMode == ScanMode.SINGLE) {
                stopScanning()
            }
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
        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets.filter { it.uid != uid }
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
                    successMessage = "成功: $successCount 個，失敗: $failCount 個",
                    scannedBaskets = emptyList(),
                    selectedProduct = null,
                    selectedBatch = null
                )
            }
        }
    }

    fun setScanMode(mode: ScanMode) {
        _uiState.update { it.copy(scanMode = mode) }
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
                isScanning = false
            )
        }
    }
}

data class ProductionUiState(
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val scanMode: ScanMode = ScanMode.SINGLE,
    val products: List<Product> = emptyList(),
    val batches: List<Batch> = emptyList(),
    val selectedProduct: Product? = null,
    val selectedBatch: Batch? = null,
    val scannedBaskets: List<ScannedBasket> = emptyList(),
    val showProductDialog: Boolean = false,
    val showBatchDialog: Boolean = false,
    val showConfirmDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)