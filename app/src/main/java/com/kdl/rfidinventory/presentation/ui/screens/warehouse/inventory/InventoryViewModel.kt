package com.kdl.rfidinventory.presentation.ui.screens.warehouse.inventory

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.model.Batch
import com.kdl.rfidinventory.data.model.Product
import com.kdl.rfidinventory.data.model.Warehouse
import com.kdl.rfidinventory.data.remote.dto.request.BasketUpdateItemDto
import com.kdl.rfidinventory.data.remote.dto.request.CommonDataDto
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.repository.AuthRepository
import com.kdl.rfidinventory.data.repository.BasketRepository
import com.kdl.rfidinventory.data.repository.BasketValidationForInventoryResult
import com.kdl.rfidinventory.data.repository.WarehouseRepository
import com.kdl.rfidinventory.data.repository.json
import com.kdl.rfidinventory.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlin.collections.filter
import kotlin.collections.first

/**
 * 盤點模式
 */
enum class InventoryMode {
    FULL,           // 全部盤點
    BY_PRODUCT      // 按貨盤點
}

/**
 * 盤點步驟
 */
enum class InventoryStep {
    SELECT_WAREHOUSE,       // 步驟 1: 選擇倉庫
    SELECT_MODE,           // 步驟 2: 選擇盤點模式
    SELECT_PRODUCT,        // 步驟 3: 選擇產品（僅按貨盤點）
    SELECT_BATCH,          // 步驟 4: 選擇批次（僅按貨盤點）
    SCANNING,              // 步驟 5: 掃描盤點
    SUBMIT                 // 步驟 6: 提交數據
}

/**
 * 盤點項狀態
 */
enum class InventoryItemStatus {
    PENDING,        // 待盤點（灰色）
    SCANNED,        // 已盤點（綠色）
    EXTRA           // 額外項（橙色 - 不在列表但實際存在）
}

/**
 * 盤點項
 */
data class InventoryItem(
    val id: String = UUID.randomUUID().toString(),
    val basket: Basket,
    val status: InventoryItemStatus,
    val scanCount: Int = 0  // 掃描次數（用於追蹤重複掃描）
)

/**
 * 批次分組
 */
data class BatchGroup(
    val batch: Batch,
    val baskets: List<Basket>,
    val totalQuantity: Int,
    val expiryDate: String?,
    val isScanned: Boolean = false  // 是否完成盤點
)

/**
 * 產品分組（含批次）
 */
data class ProductGroup(
    val product: Product,
    val batches: List<BatchGroup>,
    val totalBaskets: Int,
    val totalQuantity: Int,
    val isCompleted: Boolean = false  // 是否完成所有批次盤點
)

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val warehouseRepository: WarehouseRepository,
    private val basketRepository: BasketRepository,
    private val webSocketManager: WebSocketManager,
    private val pendingOperationDao: PendingOperationDao,
    private val authRepository: AuthRepository
) : ViewModel() {

    val _uiState = MutableStateFlow(InventoryUiState())
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

    private var allWarehouseBaskets: List<Basket> = emptyList()
    private val validatingUids = mutableSetOf<String>()

    init {
        Timber.d("📦 InventoryViewModel initialized")
        loadWarehouses()
        initializeScanManager()
        observeScanResults()
        observeScanErrors()
    }

    // ==================== 額外項編輯功能 ====================

    /**
     * 顯示編輯額外項對話框
     */
    fun showEditExtraItemDialog(item: InventoryItem) {
        if (item.status != InventoryItemStatus.EXTRA) {
            _uiState.update { it.copy(error = "只能編輯額外項") }
            return
        }

        _uiState.update {
            it.copy(
                editingItem = item,
                showEditDialog = true
            )
        }

        Timber.d("📝 Show edit dialog for basket: ${item.basket.uid}")
    }

    /**
     * 關閉編輯對話框
     */
    fun dismissEditDialog() {
        _uiState.update {
            it.copy(
                editingItem = null,
                showEditDialog = false
            )
        }
    }

    /**
     * 更新額外項的產品信息
     */
    fun updateExtraItem(
        item: InventoryItem,
        product: Product,
        batch: Batch,
        quantity: Int
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }

            try {
                val warehouse = _uiState.value.selectedWarehouse!!
                val currentUser = authRepository.getCurrentUser()?.username ?: "admin"

                // ✅ 驗證籃子類型
                if (item.basket.type != null && item.basket.type != product.btype) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = "⚠️ 籃子類型不符！\n" +
                                    "產品要求類型 ${product.btype}，" +
                                    "但籃子為類型 ${item.basket.type}"
                        )
                    }
                    return@launch
                }

                // ✅ 準備更新數據（與 Production 相同格式）
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

                val commonData = CommonDataDto(
                    product = productJson,
                    batch = batchJson,
                    warehouseId = warehouse.id,
                    updateBy = currentUser,
                    status = "IN_STOCK"  // 盤點中的籃子應該是 IN_STOCK
                )

                val items = listOf(
                    BasketUpdateItemDto(
                        rfid = item.basket.uid,
                        quantity = quantity
                    )
                )

                basketRepository.updateBasket(
                    updateType = "Receiving",
                    commonData = commonData,
                    items = items,
                    isOnline = isOnline.value
                ).onSuccess {
                    val updatedBasket = item.basket.copy(
                        product = product,
                        batch = batch,
                        warehouseId = warehouse.id,
                        quantity = quantity,
                        status = BasketStatus.IN_STOCK
                    )

                    _uiState.update { state ->
                        val updatedItems = state.inventoryItems.map { existingItem ->
                            if (existingItem.id == item.id) {
                                existingItem.copy(
                                    basket = updatedBasket,
                                    status = InventoryItemStatus.SCANNED
                                )
                            } else {
                                existingItem
                            }
                        }

                        // 重新排序
                        val sortedItems = updatedItems.sortedBy {
                            when (it.status) {
                                InventoryItemStatus.EXTRA -> 0
                                InventoryItemStatus.PENDING -> 1
                                InventoryItemStatus.SCANNED -> 2
                            }
                        }

                        state.copy(
                            inventoryItems = sortedItems,
                            statistics = calculateStatistics(sortedItems),
                            isSubmitting = false,
                            showEditDialog = false,
                            editingItem = null,
                            selectedProductForEdit = null,
                            selectedBatchForEdit = null,
                            availableBatches = emptyList(),
                            successMessage = "✅ 籃子信息已更新"
                        )
                    }

                    // 更新全局籃子列表
                    allWarehouseBaskets = allWarehouseBaskets.map { basket ->
                        if (basket.uid == updatedBasket.uid) {
                            updatedBasket
                        } else {
                            basket
                        }
                    }

                    Timber.d("✅ Extra item updated: ${item.basket.uid}")
                    Timber.d("   Product: ${product.name}")
                    Timber.d("   Batch: ${batch.batch_code}")
                    Timber.d("   Quantity: $quantity")

                }.onFailure { error ->
                    Timber.e(error, "Failed to update extra item")
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = "更新失敗: ${error.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update extra item")
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        error = "更新失敗: ${e.message}"
                    )
                }
            }
        }
    }

    // ==================== 步驟 1: 倉庫選擇 ====================

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

                    if (_uiState.value.currentStep == InventoryStep.SELECT_WAREHOUSE) {
                        scanManager.startBarcodeScan(ScanContext.WAREHOUSE_SEARCH)
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load warehouses")
                    _uiState.update {
                        it.copy(
                            isLoadingWarehouses = false,
                            error = "載入倉庫列表失敗: ${error.message}"
                        )
                    }
                }
        }
    }

    fun selectWarehouse(warehouse: Warehouse) {
        Timber.d("📍 Selected warehouse: ${warehouse.name}")

        scanManager.stopScanning()

        _uiState.update {
            it.copy(
                selectedWarehouse = warehouse,
                showWarehouseDialog = false,
                currentStep = InventoryStep.SELECT_MODE
            )
        }

        loadWarehouseData(warehouse.id)
    }

    /**
     * 加載倉庫數據（用於後續步驟）
     */
    private fun loadWarehouseData(warehouseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            warehouseRepository.getWarehouseBasketsByWarehouse(warehouseId)
                .onSuccess { baskets ->
                    Timber.d("✅ Loaded ${baskets.size} baskets from warehouse")

                    allWarehouseBaskets = baskets

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            totalWarehouseBaskets = baskets.size
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load warehouse baskets")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "載入倉庫數據失敗"
                        )
                    }
                }
        }
    }

    // ==================== 步驟 2: 模式選擇 ====================

    fun selectInventoryMode(mode: InventoryMode) {
        Timber.d("📋 Selected inventory mode: $mode")

        _uiState.update {
            it.copy(
                inventoryMode = mode,
                currentStep = when (mode) {
                    InventoryMode.FULL -> InventoryStep.SCANNING
                    InventoryMode.BY_PRODUCT -> InventoryStep.SELECT_PRODUCT
                }
            )
        }

        when (mode) {
            InventoryMode.FULL -> prepareFullInventory()
            InventoryMode.BY_PRODUCT -> prepareProductGroups()
        }
    }

    /**
     * 準備全部盤點
     */
    private fun prepareFullInventory() {
        viewModelScope.launch {
            val items = allWarehouseBaskets.map { basket ->
                InventoryItem(
                    basket = basket,
                    status = InventoryItemStatus.PENDING
                )
            }

            _uiState.update {
                it.copy(
                    inventoryItems = items,
                    statistics = calculateStatistics(items)
                )
            }

            Timber.d("📊 Full inventory prepared: ${items.size} items")
        }
    }

    /**
     * 準備按貨盤點 - 產品分組
     */
    private fun prepareProductGroups() {
        viewModelScope.launch {
            val groups = groupBasketsByProductAndBatch(allWarehouseBaskets)

            _uiState.update {
                it.copy(productGroups = groups)
            }

            Timber.d("📊 Product groups prepared: ${groups.size} products")
        }
    }

    /**
     * 按產品和批次分組
     */
    private fun groupBasketsByProductAndBatch(baskets: List<Basket>): List<ProductGroup> {
        val basketsWithProduct = baskets.filter { it.product != null && it.batch != null }

        return basketsWithProduct
            .groupBy { it.product!!.itemcode }
            .map { (_, productBaskets) ->
                val product = productBaskets.first().product!!

                // 按批次分組
                val batchGroups = productBaskets
                    .groupBy { it.batch!!.batch_code }
                    .map { (_, batchBaskets) ->
                        val batch = batchBaskets.first().batch!!

                        BatchGroup(
                            batch = batch,
                            baskets = batchBaskets,
                            totalQuantity = batchBaskets.sumOf { it.quantity },
                            expiryDate = calculateExpiryDate(batch.productionDate),
                            isScanned = false
                        )
                    }
                    .sortedBy { it.batch.productionDate }  // 由舊到新排序

                ProductGroup(
                    product = product,
                    batches = batchGroups,
                    totalBaskets = productBaskets.size,
                    totalQuantity = productBaskets.sumOf { it.quantity },
                    isCompleted = false
                )
            }
            .sortedByDescending { it.totalBaskets }
    }

    /**
     * 計算到期日（假設保質期 30 天）
     */
    private fun calculateExpiryDate(productionDate: String): String? {
        return try {
            val production = LocalDate.parse(productionDate, DateTimeFormatter.ISO_DATE)
            val expiry = production.plusDays(30)
            expiry.format(DateTimeFormatter.ISO_DATE)
        } catch (e: Exception) {
            null
        }
    }

    // ==================== 步驟 3: 產品選擇（按貨盤點） ====================

    fun selectProduct(product: Product) {
        Timber.d("🎯 Selected product: ${product.name}")

        _uiState.update {
            it.copy(
                selectedProduct = product,
                currentStep = InventoryStep.SELECT_BATCH
            )
        }
    }

    fun deselectProduct() {
        scanManager.stopScanning()

        _uiState.update {
            it.copy(
                selectedProduct = null,
                selectedBatch = null,
                currentStep = InventoryStep.SELECT_PRODUCT,
                inventoryItems = emptyList()
            )
        }
    }

    // ==================== 步驟 4: 批次選擇（按貨盤點） ====================

    fun selectBatch(batch: Batch) {
        Timber.d("📦 Selected batch: ${batch.batch_code}")

        val product = _uiState.value.selectedProduct!!

        // 找到該批次的所有籃子
        val batchBaskets = allWarehouseBaskets.filter {
            it.product?.itemcode == product.itemcode && it.batch?.batch_code == batch.batch_code
        }

        val items = batchBaskets.map { basket ->
            InventoryItem(
                basket = basket,
                status = InventoryItemStatus.PENDING
            )
        }

        _uiState.update {
            it.copy(
                selectedBatch = batch,
                currentStep = InventoryStep.SCANNING,
                inventoryItems = items,
                statistics = calculateStatistics(items)
            )
        }

        Timber.d("📊 Batch inventory prepared: ${items.size} items")
    }

    fun deselectBatch() {
        scanManager.stopScanning()

        _uiState.update {
            it.copy(
                selectedBatch = null,
                currentStep = InventoryStep.SELECT_BATCH,
                inventoryItems = emptyList()
            )
        }
    }

    // ==================== 步驟 5: 掃描盤點 ====================

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
                _uiState.value.currentStep == InventoryStep.SCANNING || _uiState.value.showProductDialog
            }
        )
    }

    private fun observeScanResults() {
        viewModelScope.launch {
            scanManager.scanResults.collect { result ->
                when (result) {
                    is ScanResult.BarcodeScanned -> {

                        val resultContext = result.context
                        Timber.d("resultContext : $resultContext")

                        when (result.context) {
                            ScanContext.WAREHOUSE_SEARCH -> {
                                handleWarehouseSelection(result.barcode)
                            }
                            ScanContext.PRODUCT_SEARCH -> {
                                updateProductSearchQuery(result.barcode)
                            }
//                            ScanContext.BASKET_SCAN -> {
//                                processScannedBasket(result.barcode)
//                            }
                            else -> processScannedBasket(result.barcode)
                        }
                    }
                    is ScanResult.RfidScanned -> handleScannedRfidTag(result.tag.uid)
                    is ScanResult.ClearListRequested -> {}
                }
            }
        }
    }

    // ==================== 額外項 ====================

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateProductSearchQuery(query: String) {
        _uiState.update { it.copy(productSearchQuery = query) }

        if (query.length >= 6) {
            val filteredProducts = filterProducts(_uiState.value.products, query)

            if (filteredProducts.size == 1) {
                Timber.d("🎯 Auto-selecting product: ${filteredProducts.first().name}")
                viewModelScope.launch {
                    delay(300)
                    if (_uiState.value.showEditDialog) {
                        _uiState.update {
                            it.copy(selectedProductForEdit = filteredProducts.first())
                        }
                        dismissProductDialog()
                    } else {
                        selectProduct(filteredProducts.first())
                    }
                }
            }
        }
    }

    private fun filterProducts(products: List<Product>, query: String): List<Product> {
        if (query.isBlank()) {
            return products
        }

        val lowerQuery = query.trim().lowercase()
        return products.filter { product ->
            product.itemcode.lowercase().contains(lowerQuery) ||
                    product.name.lowercase().contains(lowerQuery) ||
                    (product.barcodeId?.toString()?.contains(lowerQuery) == true) ||
                    (product.qrcodeId?.lowercase()?.contains(lowerQuery) == true)
        }
    }

    fun showProductDialog() {
        loadProducts()
        _uiState.update {
            it.copy(
                showProductDialog = true,
                productSearchQuery = ""
            )
        }

        // 延遲啟動產品搜索掃描
        viewModelScope.launch {
            delay(300)
            if (_uiState.value.showProductDialog) {
                scanManager.startBarcodeScan(ScanContext.PRODUCT_SEARCH)
            }
        }
    }

    fun dismissProductDialog() {
        scanManager.stopScanning()
        _uiState.update {
            it.copy(
                showProductDialog = false,
                productSearchQuery = ""
            )
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            warehouseRepository.getProducts()
                .onSuccess { products ->
                    _uiState.update { it.copy(products = products) }
                    Timber.d("✅ Loaded ${products.size} products")
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = "載入產品列表失敗") }
                }
        }
    }

    /**
     * 根據產品和過期日期查詢 Batch
     */
    fun loadBatchesForProductAndExpiry(product: Product, expiryDate: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            warehouseRepository.getBatchesByProductAndExpiry(
                itemcode = product.itemcode,
                expireDate = expiryDate,
                isOnline = isOnline.value
            )
                .onSuccess { batches ->
                    _uiState.update {
                        it.copy(
                            availableBatches = batches,
                            isLoading = false
                        )
                    }

                    Timber.d("✅ Loaded ${batches.size} batches")
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            availableBatches = emptyList(),
                            isLoading = false,
                            error = "獲取批次失敗: ${error.message}"
                        )
                    }
                }
        }
    }

    /**
     * 選擇編輯用的 Batch
     */
    fun selectBatchForEdit(batch: Batch) {
        _uiState.update {
            it.copy(selectedBatchForEdit = batch)
        }
        Timber.d("✅ Selected batch for edit: ${batch.batch_code}")
    }

    /**
     * 清除編輯用的 Batch 選擇
     */
    fun clearBatchSelection() {
        _uiState.update {
            it.copy(
                selectedBatchForEdit = null,
                availableBatches = emptyList()
            )
        }
    }

    // ==================== 其他 ====================
    /**
     * 處理倉庫選擇（通過 QR 碼）
     */
    private fun handleWarehouseSelection(scannedId: String) {
        val matchedWarehouse = _uiState.value.warehouses.find { warehouse ->
            warehouse.id.equals(scannedId, ignoreCase = true)
        }

        if (matchedWarehouse != null) {
            Timber.d("🎯 Auto-selecting warehouse: ${matchedWarehouse.name}")
            selectWarehouse(matchedWarehouse)
        } else {
            Timber.w("⚠️ No warehouse found with ID: $scannedId")
            _uiState.update {
                it.copy(error = "找不到倉庫 ID: $scannedId")
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
        processScannedBasket(barcode)
    }

    private fun handleScannedRfidTag(uid: String) {
        Timber.d("🔍 Processing RFID tag: $uid")
        processScannedBasket(uid)
    }

    fun toggleScanFromButton() {
        if (_uiState.value.currentStep != InventoryStep.SCANNING) {
            _uiState.update { it.copy(error = "請先選擇盤點項目") }
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

    /**
     * 處理掃描的籃子
     */
    private fun processScannedBasket(uid: String) {
        if (validatingUids.contains(uid)) {
            Timber.d("⏭️ Basket $uid is already being validated")
            return
        }

        viewModelScope.launch {
            validatingUids.add(uid)
            _uiState.update { it.copy(isValidating = true) }

            val currentItems = _uiState.value.inventoryItems
            val existingItem = currentItems.find { it.basket.uid == uid }

            if (existingItem != null) {
                when (existingItem.status) {
                    InventoryItemStatus.SCANNED -> {
                        // 重覆掃描已確認的籃子
                        _uiState.update {
                            it.copy(
                                isValidating = false,
                                error = "籃子 ${uid.takeLast(8)} 已掃描（重覆掃描 ${existingItem.scanCount + 1} 次）"
                            )
                        }
                        // 增加掃描計數但不改變狀態
                        incrementScanCount(uid)
                    }

                    InventoryItemStatus.EXTRA -> {
                        // 額外項再次掃描 - 保持 EXTRA 狀態，不轉為 SCANNED
                        _uiState.update {
                            it.copy(
                                isValidating = false,
                                error = "⚠️ 籃子 ${uid.takeLast(8)} 是額外項，請先編輯分配產品"
                            )
                        }
                        // 增加掃描計數但不改變狀態
                        incrementScanCount(uid)
                    }

                    InventoryItemStatus.PENDING -> {
                        // 首次掃描待盤點項目
                        updateItemStatus(uid, InventoryItemStatus.SCANNED)
                        _uiState.update {
                            it.copy(
                                isValidating = false,
                                successMessage = "✅ 籃子 ${uid.takeLast(8)} 已確認"
                            )
                        }
                    }
                }
            } else {
                // 籃子不在列表中 - 檢查是否在倉庫中
                checkExtraBasket(uid)
            }

            kotlinx.coroutines.delay(300)
            validatingUids.remove(uid)

            if (_uiState.value.scanMode == ScanMode.SINGLE) {
                scanManager.stopScanning()
            }
        }
    }

    /**
     * 只增加掃描計數，不改變狀態
     */
    private fun incrementScanCount(uid: String) {
        _uiState.update { state ->
            val updatedItems = state.inventoryItems.map { item ->
                if (item.basket.uid == uid) {
                    item.copy(scanCount = item.scanCount + 1)
                } else {
                    item
                }
            }

            state.copy(inventoryItems = updatedItems)
        }
    }

    /**
     * 檢查額外籃子（不在列表但在倉庫中）
     */
    private suspend fun checkExtraBasket(uid: String) {
        val warehouse = _uiState.value.selectedWarehouse!!
        val online = isOnline.value

        val result = warehouseRepository.validateBasketForInventory(uid, warehouse.id, online)

        when (result) {
            is BasketValidationForInventoryResult.Valid -> {
                val basket = result.basket
                val mode = _uiState.value.inventoryMode

                // 按貨盤點模式：檢查產品和批次是否匹配
                if (mode == InventoryMode.BY_PRODUCT) {
                    val selectedProduct = _uiState.value.selectedProduct
                    val selectedBatch = _uiState.value.selectedBatch

                    // 檢查產品是否匹配
                    if (basket.product?.itemcode != selectedProduct?.itemcode) {
                        _uiState.update {
                            it.copy(
                                isValidating = false,
                                error = buildString {
                                    append("❌ 籃子 ${uid.takeLast(8)} ")
                                    basket.product?.let { product ->
                                        append("屬於產品「${product.name}」")
                                    } ?: append("無產品信息")
                                    append("，不在當前盤點範圍")
                                }
                            )
                        }
                        Timber.w("⚠️ Basket $uid belongs to different product: ${basket.product?.name}")
                        return
                    }

                    // 檢查批次是否匹配
                    if (basket.batch?.batch_code != selectedBatch?.batch_code) {
                        _uiState.update {
                            it.copy(
                                isValidating = false,
                                error = buildString {
                                    append("❌ 籃子 ${uid.takeLast(8)} ")
                                    basket.batch?.let { batch ->
                                        append("屬於批次「${batch.batch_code}」")
                                    } ?: append("無批次信息")
                                    append("，不在當前盤點範圍")
                                }
                            )
                        }
                        Timber.w("⚠️ Basket $uid belongs to different batch: ${basket.batch?.batch_code}")
                        return
                    }
                }

                // 全部盤點模式 或 按貨盤點且匹配：作為額外項加入
                val statusDesc = when (basket.status) {
                    BasketStatus.UNASSIGNED -> "未分配"
                    BasketStatus.IN_PRODUCTION -> "生產中"
                    BasketStatus.RECEIVED -> "已收貨"
                    BasketStatus.IN_STOCK -> "在庫中"
                    BasketStatus.SHIPPED -> "已發貨"
                    BasketStatus.DAMAGED -> "已損壞"
                    else -> basket.status.toString()
                }

                addExtraItem(basket)

                _uiState.update {
                    it.copy(
                        isValidating = false,
                        successMessage = buildString {
                            append("⚠️ 額外項: ${uid.takeLast(8)}")
                            basket.product?.let { product ->
                                append(" (${product.name})")
                            }
                            append(" [$statusDesc]")
                        }
                    )
                }

                Timber.d("📦 Extra basket added: $uid, status=${basket.status}, product=${basket.product?.name}, batch=${basket.batch?.batch_code}")
            }

            is BasketValidationForInventoryResult.NotInWarehouse -> {
                _uiState.update {
                    it.copy(
                        isValidating = false,
                        error = "❌ 籃子 ${uid.takeLast(8)} 未注冊"
                    )
                }
            }

            is BasketValidationForInventoryResult.WrongWarehouse -> {
                _uiState.update {
                    it.copy(
                        isValidating = false,
                        error = "❌ 籃子 ${uid.takeLast(8)} 屬於倉庫 ${result.basket.warehouseId}"
                    )
                }
            }

            is BasketValidationForInventoryResult.InvalidStatus -> {
                // 兼容舊版本
                addExtraItem(result.basket)

                _uiState.update {
                    it.copy(
                        isValidating = false,
                        successMessage = "⚠️ 額外項: ${uid.takeLast(8)} (狀態異常)"
                    )
                }
            }

            is BasketValidationForInventoryResult.Error -> {
                _uiState.update {
                    it.copy(
                        isValidating = false,
                        error = "❌ 驗證失敗: ${result.message}"
                    )
                }
            }
        }
//        basketRepository.fetchBasket(uid, online)
//            .onSuccess { basket ->
//                // 驗證倉庫
//                if (basket.warehouseId != warehouse.id) {
//                    _uiState.update {
//                        it.copy(
//                            isValidating = false,
//                            error = "❌ 籃子 ${uid.takeLast(8)} 屬於倉庫 ${basket.warehouseId}"
//                        )
//                    }
//                    return
//                }
//
//                val mode = _uiState.value.inventoryMode
//
//                // 按貨盤點模式：檢查產品和批次是否匹配
//                if (mode == InventoryMode.BY_PRODUCT) {
//                    val selectedProduct = _uiState.value.selectedProduct
//                    val selectedBatch = _uiState.value.selectedBatch
//
//                    // 檢查產品是否匹配
//                    if (basket.product?.itemcode != selectedProduct?.itemcode) {
//                        _uiState.update {
//                            it.copy(
//                                isValidating = false,
//                                error = buildString {
//                                    append("❌ 籃子 ${uid.takeLast(8)} ")
//                                    basket.product?.let { product ->
//                                        append("屬於產品「${product.name}」")
//                                    } ?: append("無產品信息")
//                                    append("，不在當前盤點範圍")
//                                }
//                            )
//                        }
//                        return
//                    }
//
//                    // 檢查批次是否匹配
//                    if (basket.batch?.batch_code != selectedBatch?.batch_code) {
//                        _uiState.update {
//                            it.copy(
//                                isValidating = false,
//                                error = buildString {
//                                    append("❌ 籃子 ${uid.takeLast(8)} ")
//                                    basket.batch?.let { batch ->
//                                        append("屬於批次「${batch.batch_code}」")
//                                    } ?: append("無批次信息")
//                                    append("，不在當前盤點範圍")
//                                }
//                            )
//                        }
//                        return
//                    }
//                }
//
//                // 全部通過：作為額外項加入
//                val statusDesc = when (basket.status) {
//                    BasketStatus.UNASSIGNED -> "未分配"
//                    BasketStatus.IN_PRODUCTION -> "生產中"
//                    BasketStatus.RECEIVED -> "已收貨"
//                    BasketStatus.IN_STOCK -> "在庫中"
//                    BasketStatus.SHIPPED -> "已發貨"
//                    BasketStatus.DAMAGED -> "已損壞"
//                    else -> basket.status.toString()
//                }
//
//                addExtraItem(basket)
//
//                _uiState.update {
//                    it.copy(
//                        isValidating = false,
//                        successMessage = buildString {
//                            append("⚠️ 額外項: ${uid.takeLast(8)}")
//                            basket.product?.let { product ->
//                                append(" (${product.name})")
//                            }
//                            append(" [$statusDesc]")
//                        }
//                    )
//                }
//
//                Timber.d("📦 Extra basket added: $uid")
//            }
//            .onFailure { error ->
//                val msg = if (error.message == "BASKET_NOT_REGISTERED" ||
//                    error.message == "BASKET_NOT_FOUND_LOCAL") {
//                    "❌ 籃子 ${uid.takeLast(8)} 未注冊"
//                } else {
//                    "❌ 驗證失敗: ${error.message}"
//                }
//
//                _uiState.update {
//                    it.copy(
//                        isValidating = false,
//                        error = msg
//                    )
//                }
//            }
    }

    /**
     * 更新項目狀態
     */
    private fun updateItemStatus(uid: String, status: InventoryItemStatus) {
        _uiState.update { state ->
            val updatedItems = state.inventoryItems.map { item ->
                if (item.basket.uid == uid) {
                    item.copy(
                        status = status,
                        scanCount = item.scanCount + 1
                    )
                } else {
                    item
                }
            }

            // 排序：已掃描放最後，額外項放最前
            val sortedItems = updatedItems.sortedBy {
                when (it.status) {
                    InventoryItemStatus.EXTRA -> 0
                    InventoryItemStatus.PENDING -> 1
                    InventoryItemStatus.SCANNED -> 2
                }
            }

            state.copy(
                inventoryItems = sortedItems,
                statistics = calculateStatistics(sortedItems)
            )
        }
    }

    /**
     * 添加額外項
     */
    private fun addExtraItem(basket: Basket) {
        val item = InventoryItem(
            basket = basket,
            status = InventoryItemStatus.EXTRA,
            scanCount = 1
        )

        _uiState.update { state ->
            val updatedItems = listOf(item) + state.inventoryItems

            state.copy(
                inventoryItems = updatedItems,
                statistics = calculateStatistics(updatedItems)
            )
        }
    }

    /**
     * 刪除項目（僅額外項可刪除）
     */
    fun removeItem(itemId: String) {
        _uiState.update { state ->
            val updatedItems = state.inventoryItems.filter { it.id != itemId }

            state.copy(
                inventoryItems = updatedItems,
                statistics = calculateStatistics(updatedItems)
            )
        }
    }

    /**
     * 計算統計數據
     */
    private fun calculateStatistics(items: List<InventoryItem>): InventoryStatistics {
        return InventoryStatistics(
            totalItems = items.count { it.status != InventoryItemStatus.EXTRA },
            scannedItems = items.count { it.status == InventoryItemStatus.SCANNED },
            extraItems = items.count { it.status == InventoryItemStatus.EXTRA },
            pendingItems = items.count { it.status == InventoryItemStatus.PENDING }
        )
    }

    // ==================== 步驟 6: 提交數據 ====================

    /**
     * 完成當前批次/產品盤點
     */
    fun completeCurrent() {
        val mode = _uiState.value.inventoryMode

        when (mode) {
            InventoryMode.FULL -> {
                // 全部盤點 - 直接提交
                submitInventory()
            }
            InventoryMode.BY_PRODUCT -> {
                // 按貨盤點 - 標記批次完成
                markBatchAsCompleted()

                // 返回批次選擇
                _uiState.update {
                    it.copy(
                        selectedBatch = null,
                        currentStep = InventoryStep.SELECT_BATCH,
                        inventoryItems = emptyList()
                    )
                }
            }

            else -> {}
        }
    }

    /**
     * 標記批次為已完成
     */
    private fun markBatchAsCompleted() {
        val product = _uiState.value.selectedProduct!!
        val batch = _uiState.value.selectedBatch!!

        _uiState.update { state ->
            val updatedGroups = state.productGroups.map { group ->
                if (group.product.itemcode == product.itemcode) {
                    val updatedBatches = group.batches.map { batchGroup ->
                        if (batchGroup.batch.batch_code == batch.batch_code) {
                            batchGroup.copy(isScanned = true)
                        } else {
                            batchGroup
                        }
                    }

                    val allScanned = updatedBatches.all { it.isScanned }

                    group.copy(
                        batches = updatedBatches,
                        isCompleted = allScanned
                    )
                } else {
                    group
                }
            }

            state.copy(
                productGroups = updatedGroups,
                successMessage = "✅ 批次 ${batch.batch_code} 盤點完成"
            )
        }
    }

    /**
     * 提交盤點數據
     */
    fun submitInventory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }

            // TODO: 實現提交邏輯
            delay(1000)

            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    successMessage = "✅ 盤點數據已提交"
                )
            }

            // 重置狀態
            resetInventory()
        }
    }

    /**
     * 檢查是否可以提交（所有產品都完成盤點）
     */
    fun canSubmitByProductInventory(): Boolean {
        return _uiState.value.productGroups.all { it.isCompleted }
    }

    /**
     * 提交按貨盤點數據
     */
    fun submitByProductInventory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }

            try {
                // 收集盤點數據
                val inventoryData = collectInventoryData()

                Timber.d("📦 ========== 提交盤點數據 ==========")
                Timber.d("倉庫: ${_uiState.value.selectedWarehouse?.name}")
                Timber.d("產品數: ${inventoryData.productSummaries.size}")
                Timber.d("總籃子數: ${inventoryData.totalBaskets}")
                Timber.d("總數量: ${inventoryData.totalQuantity}")

                // TODO: 實現真實的 API 調用
                // val response = apiService.submitInventory(inventoryData)
                // if (!response.success) {
                //     throw Exception(response.message ?: "提交失敗")
                // }

                // 模擬網絡請求
                kotlinx.coroutines.delay(1500)

                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        successMessage = "✅ 盤點數據已成功提交"
                    )
                }

                // 延遲後重置狀態
                kotlinx.coroutines.delay(1000)
                resetInventory()

            } catch (e: Exception) {
                Timber.e(e, "提交盤點數據失敗")
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        error = "提交失敗: ${e.message}"
                    )
                }
            }
        }
    }

    fun showSubmitConfirmDialog() {
        if (!canSubmitByProductInventory()) {
            _uiState.update {
                it.copy(error = "請先完成所有產品的盤點")
            }
            return
        }
        _uiState.update { it.copy(showSubmitDialog = true) }
    }

    fun dismissSubmitDialog() {
        _uiState.update { it.copy(showSubmitDialog = false) }
    }

    fun confirmSubmit() {
        _uiState.update { it.copy(showSubmitDialog = false) }
        submitByProductInventory()
    }

    /**
     * 收集盤點數據
     */
    private fun collectInventoryData(): InventorySubmitData {
        val productGroups = _uiState.value.productGroups
        val warehouse = _uiState.value.selectedWarehouse!!

        val productSummaries = productGroups.map { group ->
            ProductInventorySummary(
                productId = group.product.itemcode,
                productName = group.product.name,
                batches = group.batches.map { batchGroup ->
                    BatchInventorySummary(
                        batchId = batchGroup.batch.batch_code,
                        basketCount = batchGroup.baskets.size,
                        totalQuantity = batchGroup.totalQuantity,
                        isScanned = batchGroup.isScanned
                    )
                },
                totalBaskets = group.totalBaskets,
                totalQuantity = group.totalQuantity,
                isCompleted = group.isCompleted
            )
        }

        return InventorySubmitData(
            warehouseId = warehouse.id,
            warehouseName = warehouse.name,
            productSummaries = productSummaries,
            totalBaskets = productSummaries.sumOf { it.totalBaskets },
            totalQuantity = productSummaries.sumOf { it.totalQuantity },
            timestamp = System.currentTimeMillis()
        )
    }

    fun dismissDialog() {
        scanManager.stopScanning()
        _uiState.update {
            it.copy(
                showProductDialog = false
            )
        }
    }

    /**
     * 重置盤點
     */
    fun resetInventory() {
        scanManager.stopScanning()

        _uiState.update {
            InventoryUiState(
                warehouses = it.warehouses,
                currentStep = InventoryStep.SELECT_WAREHOUSE
            )
        }

        allWarehouseBaskets = emptyList()
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
        validatingUids.clear()
    }
}

// ==================== UI State ====================

data class InventoryUiState(
    val isLoading: Boolean = false,
    val isValidating: Boolean = false,
    val isSubmitting: Boolean = false,
    val isLoadingWarehouses: Boolean = false,

    // 步驟控制
    val currentStep: InventoryStep = InventoryStep.SELECT_WAREHOUSE,
    val inventoryMode: InventoryMode? = null,

    // 掃描設置
    val scanMode: ScanMode = ScanMode.SINGLE,

    // 倉庫選擇
    val warehouses: List<Warehouse> = emptyList(),
    val selectedWarehouse: Warehouse? = null,
    val showWarehouseDialog: Boolean = false,
    val totalWarehouseBaskets: Int = 0,

    // 按貨盤點
    val productGroups: List<ProductGroup> = emptyList(),
    val selectedProduct: Product? = null,
    val selectedBatch: Batch? = null,

    // 盤點項目
    val inventoryItems: List<InventoryItem> = emptyList(),
    val statistics: InventoryStatistics = InventoryStatistics(),

    val showSubmitDialog: Boolean = false,

    // 額外項編輯
    val showEditDialog: Boolean = false,
    val editingItem: InventoryItem? = null,

    // 供 ProductSelectionDialog 使用
    val showProductDialog: Boolean = false,
    val productSearchQuery: String = "",
    val selectedProductForEdit: Product? = null,
    val products: List<Product> = emptyList(),

    val showBatchDialog: Boolean = false,
    val selectedBatchForEdit: Batch? = null,
    val availableBatches: List<Batch> = emptyList(),

    // 消息
    val error: String? = null,
    val successMessage: String? = null
)

data class InventoryStatistics(
    val totalItems: Int = 0,        // 總項目數（不含額外項）
    val scannedItems: Int = 0,      // 已掃描
    val extraItems: Int = 0,        // 額外項
    val pendingItems: Int = 0       // 待掃描
)

// ==================== 提交數據模型 ====================

/**
 * 盤點提交數據
 */
data class InventorySubmitData(
    val warehouseId: String,
    val warehouseName: String,
    val productSummaries: List<ProductInventorySummary>,
    val totalBaskets: Int,
    val totalQuantity: Int,
    val timestamp: Long
)

/**
 * 產品盤點匯總
 */
data class ProductInventorySummary(
    val productId: String,
    val productName: String,
    val batches: List<BatchInventorySummary>,
    val totalBaskets: Int,
    val totalQuantity: Int,
    val isCompleted: Boolean
)

/**
 * 批次盤點匯總
 */
data class BatchInventorySummary(
    val batchId: String,
    val basketCount: Int,
    val totalQuantity: Int,
    val isScanned: Boolean
)
