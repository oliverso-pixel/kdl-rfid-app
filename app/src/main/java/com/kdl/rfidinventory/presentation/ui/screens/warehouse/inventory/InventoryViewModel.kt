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
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.repository.BasketValidationForInventoryResult
import com.kdl.rfidinventory.data.repository.WarehouseRepository
import com.kdl.rfidinventory.data.repository.getDaysUntilExpiry
import com.kdl.rfidinventory.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

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

    private var allWarehouseBaskets: List<Basket> = emptyList()
    private val validatingUids = mutableSetOf<String>()

    private val _showSubmitDialog = MutableStateFlow(false)
    val showSubmitDialog: StateFlow<Boolean> = _showSubmitDialog.asStateFlow()

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
        quantity: Int
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }

            try {
                val warehouse = _uiState.value.selectedWarehouse!!

                // 更新籃子信息
                val result = warehouseRepository.updateBasketInfo(
                    uid = item.basket.uid,
                    productId = product.id,
                    warehouseId = warehouse.id,
                    quantity = quantity,
                    isOnline = isOnline.value
                )

                result
                    .onSuccess {
                        // 更新本地列表
                        val updatedBasket = item.basket.copy(
                            product = product,
                            warehouseId = warehouse.id,
                            quantity = quantity,
                            status = BasketStatus.IN_STOCK
                        )

                        _uiState.update { state ->
                            val updatedItems = state.inventoryItems.map { existingItem ->
                                if (existingItem.id == item.id) {
                                    existingItem.copy(
                                        basket = updatedBasket,
                                        status = InventoryItemStatus.SCANNED // 轉為已掃描狀態
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

                        Timber.d("✅ Basket updated: ${item.basket.uid} -> Product: ${product.name}, Qty: $quantity")
                    }
                    .onFailure { error ->
                        Timber.e(error, "Failed to update basket")
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

    fun showWarehouseDialog() {
        _uiState.update { it.copy(showWarehouseDialog = true) }
    }

    fun dismissWarehouseDialog() {
        _uiState.update { it.copy(showWarehouseDialog = false) }
    }

    fun selectWarehouse(warehouse: Warehouse) {
        Timber.d("📍 Selected warehouse: ${warehouse.name}")

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
            .groupBy { it.product!!.id }
            .map { (_, productBaskets) ->
                val product = productBaskets.first().product!!

                // 按批次分組
                val batchGroups = productBaskets
                    .groupBy { it.batch!!.id }
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
        Timber.d("📦 Selected batch: ${batch.id}")

        val product = _uiState.value.selectedProduct!!

        // 找到該批次的所有籃子
        val batchBaskets = allWarehouseBaskets.filter {
            it.product?.id == product.id && it.batch?.id == batch.id
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

    private fun initializeScanManager() {
        scanManager.initialize(
            scope = viewModelScope,
            scanMode = _uiState.map { it.scanMode }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                ScanMode.SINGLE
            ),
            canStartScan = {
                _uiState.value.currentStep == InventoryStep.SCANNING
            }
        )
    }

    private fun observeScanResults() {
        viewModelScope.launch {
            scanManager.scanResults.collect { result ->
                when (result) {
                    is ScanResult.BarcodeScanned -> handleScannedBarcode(result.barcode)
                    is ScanResult.RfidScanned -> handleScannedRfidTag(result.tag.uid)
                    is ScanResult.ClearListRequested -> {}
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

    private fun handleScannedBarcode(barcode: String) {
        Timber.d("🔍 Processing barcode: $barcode")
        processScannedBasket(barcode)
    }

    private fun handleScannedRfidTag(uid: String) {
        Timber.d("🔍 Processing RFID tag: $uid")
        processScannedBasket(uid)
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
                // ✅ 籃子在列表中 - 標記為已掃描
                if (existingItem.status == InventoryItemStatus.SCANNED) {
                    // 重複掃描
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            error = "籃子 ${uid.takeLast(8)} 已掃描（重複掃描 ${existingItem.scanCount + 1} 次）"
                        )
                    }
                } else {
                    // 首次掃描
                    updateItemStatus(uid, InventoryItemStatus.SCANNED)

                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            successMessage = "✅ 籃子 ${uid.takeLast(8)} 已確認"
                        )
                    }
                }
            } else {
                // ⚠️ 籃子不在列表中 - 檢查是否在倉庫中
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

                // ✅ 按貨盤點模式：檢查產品和批次是否匹配
                if (mode == InventoryMode.BY_PRODUCT) {
                    val selectedProduct = _uiState.value.selectedProduct
                    val selectedBatch = _uiState.value.selectedBatch

                    // 檢查產品是否匹配
                    if (basket.product?.id != selectedProduct?.id) {
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
                    if (basket.batch?.id != selectedBatch?.id) {
                        _uiState.update {
                            it.copy(
                                isValidating = false,
                                error = buildString {
                                    append("❌ 籃子 ${uid.takeLast(8)} ")
                                    basket.batch?.let { batch ->
                                        append("屬於批次「${batch.id}」")
                                    } ?: append("無批次信息")
                                    append("，不在當前盤點範圍")
                                }
                            )
                        }
                        Timber.w("⚠️ Basket $uid belongs to different batch: ${basket.batch?.id}")
                        return
                    }
                }

                // ✅ 全部盤點模式 或 按貨盤點且匹配：作為額外項加入
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

                Timber.d("📦 Extra basket added: $uid, status=${basket.status}, product=${basket.product?.name}, batch=${basket.batch?.id}")
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
                if (group.product.id == product.id) {
                    val updatedBatches = group.batches.map { batchGroup ->
                        if (batchGroup.batch.id == batch.id) {
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
                successMessage = "✅ 批次 ${batch.id} 盤點完成"
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
            kotlinx.coroutines.delay(1000)

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
                productId = group.product.id,
                productName = group.product.name,
                batches = group.batches.map { batchGroup ->
                    BatchInventorySummary(
                        batchId = batchGroup.batch.id,
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