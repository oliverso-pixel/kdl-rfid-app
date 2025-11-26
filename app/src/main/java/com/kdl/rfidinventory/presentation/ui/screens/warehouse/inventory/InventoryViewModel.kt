package com.kdl.rfidinventory.presentation.ui.screens.warehouse.inventory

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.model.Product
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.repository.BasketValidationForInventoryResult
import com.kdl.rfidinventory.data.repository.ProductionRepository
import com.kdl.rfidinventory.data.repository.Warehouse
import com.kdl.rfidinventory.data.repository.WarehouseRepository
import com.kdl.rfidinventory.data.repository.getDaysUntilExpiry
import com.kdl.rfidinventory.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.collections.map

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val warehouseRepository: WarehouseRepository,
    private val webSocketManager: WebSocketManager,
    private val pendingOperationDao: PendingOperationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = webSocketManager.isOnline

    val networkState: StateFlow<NetworkState> = combine(
        isOnline,
        pendingOperationDao.getPendingCount()
    ) { online, pendingCount ->
        if (online) NetworkState.Connected
        else NetworkState.Disconnected(pendingCount)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NetworkState.Disconnected(0)
    )

    val scanState = scanManager.scanState

    private var allBaskets: List<Basket> = emptyList()
    private val validatingUids = mutableSetOf<String>()

    init {
        Timber.d("InventoryViewModel initialized")
        loadWarehouses()
        initializeScanManager()
        observeScanResults()
        observeScanErrors()
    }

    private fun loadWarehouses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWarehouses = true) }
            warehouseRepository.getWarehouses()
                .onSuccess { warehouses ->
                    _uiState.update {
                        it.copy(
                            warehouses = warehouses.filter { w -> w.isActive },
                            isLoadingWarehouses = false
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load warehouses")
                    _uiState.update {
                        it.copy(
                            isLoadingWarehouses = false,
                            error = "載入倉庫列表失敗"
                        )
                    }
                }
        }
    }

    fun selectWarehouse(warehouse: Warehouse) {
        _uiState.update {
            it.copy(
                selectedWarehouse = warehouse,
                showWarehouseDialog = false,
                selectedProduct = null,
                scannedBaskets = emptyList()
            )
        }
        loadInventory()
    }

    fun showWarehouseDialog() {
        _uiState.update { it.copy(showWarehouseDialog = true) }
    }

    fun dismissWarehouseDialog() {
        _uiState.update { it.copy(showWarehouseDialog = false) }
    }

    fun loadInventory() {
        val warehouse = _uiState.value.selectedWarehouse
        if (warehouse == null) {
            _uiState.update { it.copy(error = "請先選擇倉庫") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            Timber.d("🔍 ========== Loading Inventory ==========")
            Timber.d("Selected warehouse: ${warehouse.id} - ${warehouse.name}")

            warehouseRepository.getWarehouseBasketsByWarehouse(warehouse.id)
                .onSuccess { baskets ->
                    Timber.d("✅ Received ${baskets.size} baskets from repository")

                    allBaskets = baskets

                    // 🔍 打印所有篮子
                    baskets.forEach { basket ->
                        Timber.d("  Basket: uid=${basket.uid.takeLast(8)}, product=${basket.product?.name}, status=${basket.status}")
                    }

                    val productGroups = groupBasketsByProduct(baskets)

                    Timber.d("📊 Grouped into ${productGroups.size} products")
                    productGroups.forEach { group ->
                        Timber.d("  Product: ${group.product.name}, baskets=${group.totalBaskets}, qty=${group.totalQuantity}")
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            productGroups = productGroups,
                            statistics = calculateStatistics(baskets)
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "❌ Failed to load inventory")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    private fun groupBasketsByProduct(baskets: List<Basket>): List<ProductInventoryGroup> {
        Timber.d("🔍 ========== Grouping Baskets ==========")
        Timber.d("Total baskets to group: ${baskets.size}")

//        val basketsWithProduct = baskets.filter {
//            !it.product.productID.isNullOrBlank() && !it.productName.isNullOrBlank()
//        }
//        Timber.d("Baskets with product: ${basketsWithProduct.size}")
//
//        val grouped = basketsWithProduct
//            .groupBy { it.productID!! }
//            .map { (productId, productBaskets) ->
//                val firstBasket = productBaskets.first()
//
//                val product = Product(
//                    id = productId,
//                    name = firstBasket.productName ?: "未知产品",
//                    maxBasketCapacity = 60,
//                    imageUrl = "img"
//                )
//
//                val expiringCount = productBaskets.count { basket ->
//                    val days = basket.getDaysUntilExpiry()
//                    days != null && days <= 7
//                }
//
//                Timber.d("  Product ${product.name} (ID: $productId): ${productBaskets.size} baskets")
//
//                ProductInventoryGroup(
//                    product = product,
//                    totalBaskets = productBaskets.size,
//                    totalQuantity = productBaskets.sumOf { it.quantity },
//                    expiringCount = expiringCount,
//                    baskets = productBaskets
//                )
//            }
//            .sortedByDescending { it.totalBaskets }

        val basketsWithProduct = baskets.filter { it.product != null }
        Timber.d("Baskets with product: ${basketsWithProduct.size}")

        val grouped = basketsWithProduct
            .groupBy { it.product!! }
            .map { (product, productBaskets) ->
                val expiringCount = productBaskets.count { basket ->
                    val days = basket.getDaysUntilExpiry()
                    days != null && days <= 7
                }

                Timber.d("  Product ${product.name}: ${productBaskets.size} baskets")

                ProductInventoryGroup(
                    product = product,
                    totalBaskets = productBaskets.size,
                    totalQuantity = productBaskets.sumOf { it.quantity },
                    expiringCount = expiringCount,
                    baskets = productBaskets
                )
            }
            .sortedByDescending { it.totalBaskets }

        Timber.d("Final groups: ${grouped.size}")
        return grouped
    }

    private fun calculateStatistics(baskets: List<Basket>): InventoryStatistics {
        val groupedByStatus = baskets.groupBy { it.status }
        val expiringCount = baskets.count { basket ->
            val days = basket.getDaysUntilExpiry()
            days != null && days <= 7
        }

        return InventoryStatistics(
            totalBaskets = baskets.size,
            inStock = groupedByStatus[BasketStatus.IN_STOCK]?.size ?: 0,
            received = groupedByStatus[BasketStatus.RECEIVED]?.size ?: 0,
            totalQuantity = baskets.sumOf { it.quantity },
            expiringCount = expiringCount
        )
    }

    fun selectProduct(product: Product) {
        _uiState.update {
            it.copy(
                selectedProduct = product,
                scannedBaskets = emptyList()
            )
        }
    }

    fun deselectProduct() {
        scanManager.stopScanning()
        _uiState.update {
            it.copy(
                selectedProduct = null,
                scannedBaskets = emptyList()
            )
        }
    }

    private fun initializeScanManager() {
        scanManager.initialize(
            scope = viewModelScope,
            scanMode = _uiState.map { it.scanMode }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                ScanMode.SINGLE
            ),
            canStartScan = {
                _uiState.value.selectedWarehouse != null &&
                        _uiState.value.selectedProduct != null
            }
        )
    }

    private fun observeScanResults() {
        viewModelScope.launch {
            scanManager.scanResults.collect { result ->
                when (result) {
                    is ScanResult.BarcodeScanned -> handleScannedBarcode(result.barcode)
                    is ScanResult.RfidScanned -> handleScannedRfidTag(result.tag.uid)
                    is ScanResult.ClearListRequested -> clearBaskets()
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

    fun setScanMode(mode: ScanMode) {
        viewModelScope.launch {
            scanManager.changeScanMode(mode)
            _uiState.update { it.copy(scanMode = mode) }
        }
    }

    private fun handleScannedBarcode(barcode: String) {
        Timber.d("🔍 Processing barcode: $barcode")
        fetchAndValidateBasket(barcode)
    }

    private fun handleScannedRfidTag(uid: String) {
        Timber.d("🔍 Processing RFID tag: $uid")
        fetchAndValidateBasket(uid)
    }

    fun toggleScanFromButton() {
        if (_uiState.value.selectedWarehouse == null) {
            _uiState.update { it.copy(error = "請先選擇倉庫") }
            return
        }

        if (_uiState.value.selectedProduct == null) {
            _uiState.update { it.copy(error = "請先選擇產品") }
            return
        }

        viewModelScope.launch {
            if (scanManager.scanState.value.isScanning) {
                scanManager.stopScanning()
            } else {
                scanManager.startRfidScan(_uiState.value.scanMode)
            }
        }
    }

    private fun fetchAndValidateBasket(uid: String) {
        val warehouse = _uiState.value.selectedWarehouse
        val product = _uiState.value.selectedProduct

        if (warehouse == null || product == null) {
            _uiState.update { it.copy(error = "請先選擇倉庫和產品") }
            return
        }

        if (validatingUids.contains(uid)) {
            Timber.d("⏭️ Basket $uid is already being validated")
            return
        }

        if (_uiState.value.scannedBaskets.any { it.basket.uid == uid }) {
            _uiState.update { it.copy(error = "籃子已掃描: ${uid.takeLast(8)}") }
            if (_uiState.value.scanMode == ScanMode.SINGLE) {
                scanManager.stopScanning()
            }
            return
        }

        viewModelScope.launch {
            validatingUids.add(uid)
            _uiState.update { it.copy(isValidating = true) }

            val online = isOnline.value
            val result = warehouseRepository.validateBasketForInventory(uid, warehouse.id, online)

            when (result) {
                is BasketValidationForInventoryResult.Valid -> {
                    if (result.basket.product?.id == product.id) {
                        addBasket(result.basket)
                    } else {
                        _uiState.update {
                            it.copy(
                                isValidating = false,
                                error = "籃子 ${uid.takeLast(8)} 是 ${result.basket.product?.name}，不是 ${product.name}"
                            )
                        }
                        if (_uiState.value.scanMode == ScanMode.SINGLE) {
                            scanManager.stopScanning()
                        }
                    }
                }

                is BasketValidationForInventoryResult.NotInWarehouse -> {
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            error = "籃子 ${uid.takeLast(8)} 不在倉庫中"
                        )
                    }
                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }

                is BasketValidationForInventoryResult.WrongWarehouse -> {
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            error = "籃子 ${uid.takeLast(8)} 在其他倉庫"
                        )
                    }
                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }

                is BasketValidationForInventoryResult.InvalidStatus -> {
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            error = "籃子 ${uid.takeLast(8)} 狀態錯誤"
                        )
                    }
                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }

                is BasketValidationForInventoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            error = result.message
                        )
                    }
                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }
            }

            kotlinx.coroutines.delay(300)
            validatingUids.remove(uid)
        }
    }

    private fun addBasket(basket: Basket) {
        val item = ScannedInventoryItem(
            id = UUID.randomUUID().toString(),
            basket = basket
        )

        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets + item,
                isValidating = false,
                successMessage = "✅ 籃子 ${basket.uid.takeLast(8)} 已確認"
            )
        }

        if (_uiState.value.scanMode == ScanMode.SINGLE) {
            scanManager.stopScanning()
        }
    }

    fun removeBasket(uid: String) {
        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets.filter { it.basket.uid != uid }
            )
        }
    }

    fun clearBaskets() {
        scanManager.stopScanning()
        _uiState.update { it.copy(scannedBaskets = emptyList()) }
    }

    fun exportInventory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            kotlinx.coroutines.delay(2000)
            _uiState.update {
                it.copy(
                    isExporting = false,
                    successMessage = "盤點報表已匯出"
                )
            }
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

data class ScannedInventoryItem(
    val id: String,
    val basket: Basket
)

data class ProductInventoryGroup(
    val product: Product,
    val totalBaskets: Int,
    val totalQuantity: Int,
    val expiringCount: Int,
    val baskets: List<Basket>
)

data class InventoryUiState(
    val isLoading: Boolean = false,
    val isValidating: Boolean = false,
    val isExporting: Boolean = false,
    val isLoadingWarehouses: Boolean = false,
    val scanMode: ScanMode = ScanMode.SINGLE,
    val warehouses: List<Warehouse> = emptyList(),
    val selectedWarehouse: Warehouse? = null,
    val showWarehouseDialog: Boolean = false,
    val selectedProduct: Product? = null,
    val productGroups: List<ProductInventoryGroup> = emptyList(),
    val scannedBaskets: List<ScannedInventoryItem> = emptyList(),
    val statistics: InventoryStatistics = InventoryStatistics(),
    val error: String? = null,
    val successMessage: String? = null
)

data class InventoryStatistics(
    val totalBaskets: Int = 0,
    val inStock: Int = 0,
    val received: Int = 0,
    val totalQuantity: Int = 0,
    val expiringCount: Int = 0
)