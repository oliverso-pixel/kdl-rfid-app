package com.kdl.rfidinventory.presentation.ui.screens.production

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.model.Batch
import com.kdl.rfidinventory.data.model.Product
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
                            maxBasketCapacity = 60  // Mock
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

    fun startScanning() {
        viewModelScope.launch {
            rfidManager.startScan(_uiState.value.scanMode)
                .catch { error ->
                    _uiState.update { it.copy(error = "掃描失敗: ${error.message}") }
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
        _uiState.update {
            it.copy(
                scannedUid = tag.uid,
                showProductDialog = true,
                isScanning = false
            )
        }
        stopScanning()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun selectProduct(product: Product) {
        _uiState.update {
            it.copy(
                selectedProduct = product,
                showProductDialog = false,
                showBatchDialog = true
            )
        }
        // Load batches for the selected product
        loadBatchesForProduct(product.id)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadBatchesForProduct(productId: String) {
        // Mock batches
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
        _uiState.update { it.copy(batches = mockBatches) }
    }

    fun selectBatch(batch: Batch) {
        _uiState.update {
            it.copy(
                selectedBatch = batch,
                showBatchDialog = false,
                showQuantityDialog = true
            )
        }
    }

    fun confirmQuantity(quantity: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            val uid = state.scannedUid ?: return@launch
            val product = state.selectedProduct ?: return@launch
            val batch = state.selectedBatch ?: return@launch

            _uiState.update { it.copy(isLoading = true) }

            val isOnline = _networkState.value is NetworkState.Connected

            productionRepository.startProduction(
                uid = uid,
                productId = product.id,
                batchId = batch.id,
                quantity = quantity,
                productionDate = batch.productionDate,
                isOnline = isOnline
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showQuantityDialog = false,
                        successMessage = "生產記錄已保存",
                        scannedUid = null,
                        selectedProduct = null,
                        selectedBatch = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
            }
        }
    }

    fun setScanMode(mode: ScanMode) {
        _uiState.update { it.copy(scanMode = mode) }
    }

    fun dismissDialog() {
        _uiState.update {
            it.copy(
                showProductDialog = false,
                showBatchDialog = false,
                showQuantityDialog = false
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}

data class ProductionUiState(
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val scanMode: ScanMode = ScanMode.SINGLE,
    val scannedUid: String? = null,
    val products: List<Product> = emptyList(),
    val batches: List<Batch> = emptyList(),
    val selectedProduct: Product? = null,
    val selectedBatch: Batch? = null,
    val showProductDialog: Boolean = false,
    val showBatchDialog: Boolean = false,
    val showQuantityDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)