package com.kdl.rfidinventory.presentation.ui.screens.loading

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.repository.LoadingRepository
import com.kdl.rfidinventory.data.repository.WarehouseRepository
import com.kdl.rfidinventory.data.remote.model.NetworkState
import com.kdl.rfidinventory.domain.manager.ScanManager
import com.kdl.rfidinventory.domain.model.ScanMode
import com.kdl.rfidinventory.domain.manager.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class LoadingViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val loadingRepository: LoadingRepository,
    private val warehouseRepository: WarehouseRepository,
    private val webSocketManager: WebSocketManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoadingUiState())
    val uiState: StateFlow<LoadingUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = webSocketManager.isOnline

    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Connected)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    val scanState = scanManager.scanState

    // 當前產品的掃描數據
    private val scannedBaskets = mutableMapOf<String, LoadingScannedItem>()

    // 所有已完成產品的掃描數據
    private val allCompletedScans = mutableMapOf<String, List<LoadingScannedItem>>()

    private var warehouseBaskets: List<Basket> = emptyList()

    // 記錄已完成的產品
    private val completedItems = mutableSetOf<String>()

    private val validatingUids = mutableSetOf<String>()

    init {
        loadRoutes()
        initializeScanManager()
        observeScanResults()
        observeScanErrors()
    }

    /**
     * 初始化掃描管理器
     */
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
                _uiState.value.currentStep == LoadingStep.SCANNING
            }
        )
    }

    /**
     * 監聽掃描結果
     */
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

    /**
     * 監聽掃描錯誤
     */
    private fun observeScanErrors() {
        viewModelScope.launch {
            scanManager.errors.collect { error ->
                _uiState.update { it.copy(error = error) }
            }
        }
    }

    /**
     * 設置掃描模式
     */
    fun setScanMode(mode: ScanMode) {
        viewModelScope.launch {
            scanManager.changeScanMode(mode)
            _uiState.update { it.copy(scanMode = mode) }
        }
    }

    // ==================== 步驟 1 ====================

    /**
     * 查看路線詳情
     */
    fun viewRouteDetail(route: LoadingRoute) {
        _uiState.update {
            it.copy(
                selectedRoute = route,
                currentStep = LoadingStep.ROUTE_DETAIL
            )
        }
        // 開始監聽路線實時狀態
//        observeRouteStatus(route.id)
    }

    /**
     * 監聽路線實時狀態 (通過 WebSocket 或定期查詢)
     */
    private fun observeRouteStatus(routeId: String) {
        viewModelScope.launch {
            loadingRepository.observeRouteStatus(routeId)
                .collect { updatedRoute ->
                    _uiState.update {
                        it.copy(selectedRoute = updatedRoute)
                    }
                }
        }
    }

    /**
     * 從路線詳情頁選擇模式和產品
     */
    fun selectModeAndItemFromDetail(mode: LoadingMode, item: LoadingItem) {
        _uiState.update {
            it.copy(selectedMode = mode)
        }

        val route = _uiState.value.selectedRoute ?: return
        val warehouseId = _uiState.value.selectedWarehouseId ?: return

        // 獲取該產品的可用庫存
        val availableBaskets = getAvailableBasketsForItem(item, warehouseId, mode)

        val itemWithStock = LoadingItemWithStock(
            item = item,
            availableBaskets = availableBaskets,
            availableQuantity = availableBaskets.sumOf { it.quantity },
            isCompleted = when (mode) {
                LoadingMode.FULL_BASKETS -> item.completionStatus.fullBasketsCompleted
                LoadingMode.LOOSE_ITEMS -> item.completionStatus.looseItemsCompleted
            }
        )

        _uiState.update {
            it.copy(
                selectedItemWithStock = itemWithStock,
                currentStep = LoadingStep.SCANNING
            )
        }
    }

    /**
     * 加載上貨路線
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun loadRoutesAsync(): Result<List<LoadingRoute>> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val isOnline = _networkState.value is NetworkState.Connected

        return loadingRepository.getLoadingRoutes(today, isOnline)
    }

    /**
     * 加載上貨路線（UI 調用）
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadRoutes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            loadRoutesAsync()
                .onSuccess { routes ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            routes = routes
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "加載路線失敗: ${error.message}"
                        )
                    }
                }
        }
    }

    /**
     * 設置當前步驟
     */
    fun setCurrentStep(step: LoadingStep) {
        Timber.d("🚚 current Step: ${step}")
        _uiState.update { it.copy(currentStep = step) }
    }

    /**
     * 選擇上貨模式
     */
    fun selectLoadingMode(mode: LoadingMode) {
        Timber.d("🚚 Selected Mode: ${mode}")
        _uiState.update { it.copy(selectedMode = mode) }
        loadWarehouses()
        setCurrentStep(LoadingStep.SELECT_WAREHOUSE)
    }

    /**
     * 加載倉庫
     */
    fun loadWarehouses() {
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

    /**
     * 選擇倉庫
     */
    fun selectWarehouse(warehouseId: String, warehouseName: String) {
        _uiState.update {
            it.copy(
                selectedWarehouseId = warehouseId,
                selectedWarehouseName = warehouseName
            )
        }
        loadWarehouseBaskets(warehouseId)
        setCurrentStep(LoadingStep.SELECT_ROUTE)
    }

    /**
     * 加載倉庫在庫籃子
     */
    private fun loadWarehouseBaskets(warehouseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            loadingRepository.getWarehouseBaskets(warehouseId)
                .onSuccess { baskets ->
                    warehouseBaskets = baskets

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            warehouseBaskets = baskets
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load warehouse baskets")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "加載倉庫數據失敗: ${error.message}"
                        )
                    }
                }
        }
    }

    /**
     * 選擇路線
     */
    fun selectRoute(route: LoadingRoute) {
        Timber.d("🚚 Selected route: ${route.name}")

        val selectedWarehouseId = _uiState.value.selectedWarehouseId
        val selectedMode = _uiState.value.selectedMode

        // 根據模式和倉庫庫存，處理可用項目
        val availableItems = route.items.mapNotNull { item ->
            // 檢查該產品在當前模式下是否已完成
            val isCompletedInCurrentMode = when (selectedMode) {
                LoadingMode.FULL_BASKETS -> item.completionStatus.fullBasketsCompleted
                LoadingMode.LOOSE_ITEMS -> item.completionStatus.looseItemsCompleted
                else -> false
            }

            val availableBaskets = getAvailableBasketsForItem(item, selectedWarehouseId, selectedMode)

            // 只有滿足以下條件之一才包含該產品：
            // 1. 在當前倉庫有可用庫存 且 未完成
            // 2. 已完成（用於顯示狀態）
            when {
                // 有庫存且未完成 - 可以繼續上貨
                availableBaskets.isNotEmpty() && !isCompletedInCurrentMode -> {
                    LoadingItemWithStock(
                        item = item,
                        availableBaskets = availableBaskets,
                        availableQuantity = availableBaskets.sumOf { it.quantity },
                        isCompleted = false
                    )
                }
                // 已完成 - 僅顯示狀態，不可再次上貨
                isCompletedInCurrentMode -> {
                    LoadingItemWithStock(
                        item = item,
                        availableBaskets = emptyList(),
                        availableQuantity = 0,
                        isCompleted = true
                    )
                }
                // 無庫存且未完成 - 不顯示
                else -> null
            }
        }

        Timber.d("📊 Available items in warehouse ${selectedWarehouseId}: ${availableItems.size}")
        availableItems.forEach { itemWithStock ->
            Timber.d("  - ${itemWithStock.item.productName}: ${itemWithStock.availableBaskets.size} baskets, ${itemWithStock.availableQuantity} qty, completed=${itemWithStock.isCompleted}")
        }

        _uiState.update {
            it.copy(
                selectedRoute = route,
                availableItemsWithStock = availableItems
            )
        }

        setCurrentStep(LoadingStep.SELECT_ITEM)
    }

    /**
     * 獲取可用於該項目的籃子
     */
    private fun getAvailableBasketsForItem(
        item: LoadingItem,
        warehouseId: String?,
        mode: LoadingMode?
    ): List<Basket> {
        return warehouseBaskets.filter { basket ->
            val basicMatch = basket.product?.itemcode == item.productId &&
                    basket.status == BasketStatus.IN_STOCK &&
                    basket.warehouseId == warehouseId

            when (mode) {
                LoadingMode.LOOSE_ITEMS -> {
                    basicMatch
                }
                LoadingMode.FULL_BASKETS -> {
                    basicMatch && basket.product?.let { product ->
                        basket.quantity == product.maxBasketCapacity
                    } ?: false
                }
                else -> false
            }
        }.sortedByDescending { it.quantity }
    }

    /**
     * 選擇貨物
     */
    fun selectItem(itemWithStock: LoadingItemWithStock) {
        Timber.d("🎯 Selected item: ${itemWithStock.item.productName}")

        _uiState.update {
            it.copy(
                selectedItemWithStock = itemWithStock
            )
        }
        setCurrentStep(LoadingStep.SCANNING)
    }

    /**
     * 開始掃描（通過按鈕）
     */
    fun toggleScanFromButton() {
        if (_uiState.value.currentStep != LoadingStep.SCANNING) {
            _uiState.update { it.copy(error = "請先選擇貨物") }
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
     * 處理條碼掃描
     */
    private fun handleScannedBarcode(barcode: String) {
        Timber.d("🔍 Processing barcode: $barcode")
        processScannedBasket(barcode)
    }

    /**
     * 處理 RFID 掃描
     */
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

            val selectedItemWithStock = _uiState.value.selectedItemWithStock
            val selectedItem = selectedItemWithStock?.item
            val selectedMode = _uiState.value.selectedMode

            if (selectedItem == null || selectedMode == null) {
                _uiState.update {
                    it.copy(
                        isValidating = false,
                        error = "請先選擇貨物"
                    )
                }
                validatingUids.remove(uid)
                return@launch
            }

            // 檢查是否已掃描
            if (scannedBaskets.containsKey(uid)) {
                _uiState.update {
                    it.copy(
                        isValidating = false,
                        error = "籃子已掃描: ${uid.takeLast(8)}"
                    )
                }
                validatingUids.remove(uid)

                if (_uiState.value.scanMode == ScanMode.SINGLE) {
                    scanManager.stopScanning()
                }
                return@launch
            }

            // 從可用籃子中查找
            val basket = selectedItemWithStock.availableBaskets.find { it.uid == uid }

            if (basket == null) {
                loadingRepository.getBasketByUid(uid)
                    .onSuccess { dbBasket ->
                        val reason = when {
                            dbBasket.product?.itemcode != selectedItem.productId ->
                                "產品不匹配 (${dbBasket.product?.name ?: "未知"})"
                            dbBasket.warehouseId != _uiState.value.selectedWarehouseId ->
                                "倉庫不匹配"
                            dbBasket.status != BasketStatus.IN_STOCK ->
                                "狀態錯誤 (${getBasketStatusText(dbBasket.status)})"
                            selectedMode == LoadingMode.FULL_BASKETS &&
                                    dbBasket.quantity < (dbBasket.product?.maxBasketCapacity ?: 0) ->
                                "籃子未滿格 (${dbBasket.quantity}/${dbBasket.product?.maxBasketCapacity})"
                            else -> "不符合條件"
                        }

                        _uiState.update {
                            it.copy(
                                isValidating = false,
                                error = "❌ 籃子 ${uid.takeLast(8)}: $reason"
                            )
                        }
                    }
                    .onFailure {
                        _uiState.update {
                            it.copy(
                                isValidating = false,
                                error = "❌ 籃子 ${uid.takeLast(8)} 未注冊"
                            )
                        }
                    }

                validatingUids.remove(uid)

                if (_uiState.value.scanMode == ScanMode.SINGLE) {
                    scanManager.stopScanning()
                }
                return@launch
            }

            // 驗證通過，添加到掃描列表
            val scannedQuantity = if (selectedMode == LoadingMode.LOOSE_ITEMS) {
                // 散貨模式：自動填入 looseQuantity
                selectedItem.looseQuantity
            } else {
                // 完整籃子模式：使用籃子實際數量
                basket.quantity
            }

            val scannedItem = LoadingScannedItem(
                basket = basket,
                loadingItem = selectedItem,
                isLoose = selectedMode == LoadingMode.LOOSE_ITEMS,
                scannedQuantity = scannedQuantity,
                expectedQuantity = if (selectedMode == LoadingMode.LOOSE_ITEMS)
                    selectedItem.looseQuantity
                else
                    basket.product?.maxBasketCapacity ?: 0
            )

            scannedBaskets[uid] = scannedItem
            updateScannedSummary()

            _uiState.update {
                it.copy(
                    isValidating = false,
                    successMessage = "✅ 籃子 ${uid.takeLast(8)} 已確認 (${scannedQuantity}個)"
                )
            }

            Timber.d("✅ Basket scanned: $uid, qty=$scannedQuantity")

            kotlinx.coroutines.delay(300)
            validatingUids.remove(uid)

            if (_uiState.value.scanMode == ScanMode.SINGLE) {
                scanManager.stopScanning()
            }
        }
    }

    /**
     * 更新掃描匯總
     */
    private fun updateScannedSummary() {
        val selectedItemWithStock = _uiState.value.selectedItemWithStock ?: return
        val selectedItem = selectedItemWithStock.item
        val selectedMode = _uiState.value.selectedMode ?: return

        val items = scannedBaskets.values.toList()
        val totalScanned = items.sumOf { it.scannedQuantity }

        val expectedQuantity = when (selectedMode) {
            LoadingMode.FULL_BASKETS -> {
                val maxCapacity = selectedItemWithStock.availableBaskets
                    .firstOrNull()?.product?.maxBasketCapacity ?: 20
                (selectedItem.fullTrolley * 5 + selectedItem.fullBaskets) * maxCapacity
            }
            LoadingMode.LOOSE_ITEMS -> selectedItem.looseQuantity
        }

        val isComplete = when (selectedMode) {
            LoadingMode.FULL_BASKETS -> {
                val expectedBaskets = selectedItem.fullTrolley * 5 + selectedItem.fullBaskets
                items.size >= expectedBaskets && totalScanned >= expectedQuantity
            }
            LoadingMode.LOOSE_ITEMS -> totalScanned >= selectedItem.looseQuantity
        }

        _uiState.update {
            it.copy(
                scannedItems = items,
                totalScanned = totalScanned,
                expectedQuantity = expectedQuantity,
                isComplete = isComplete
            )
        }

        Timber.d("📊 Scan summary updated: ${items.size} baskets, $totalScanned/$expectedQuantity qty, complete=$isComplete")
    }

    /**
     * 移除掃描的籃子
     */
    fun removeScannedBasket(uid: String) {
        scannedBaskets.remove(uid)
        updateScannedSummary()
    }

    /**
     * 更新散貨數量
     */
    fun updateLooseQuantity(uid: String, newQuantity: Int) {
        scannedBaskets[uid]?.let { item ->
            if (item.isLoose) {
                scannedBaskets[uid] = item.copy(scannedQuantity = newQuantity)
                updateScannedSummary()
            }
        }
    }

    /**
     *  確認當前產品（保存掃描數據，不提交）
     */
    fun confirmCurrentItem() {
        val selectedItemWithStock = _uiState.value.selectedItemWithStock
        val selectedRoute = _uiState.value.selectedRoute
        val selectedMode = _uiState.value.selectedMode

        if (selectedItemWithStock == null || selectedRoute == null || selectedMode == null) {
            _uiState.update { it.copy(error = "缺少必要信息") }
            return
        }

        if (scannedBaskets.isEmpty()) {
            _uiState.update { it.copy(error = "沒有掃描任何籃子") }
            return
        }

        val productId = selectedItemWithStock.item.productId
        val scannedItemsList = scannedBaskets.values.toList()

        allCompletedScans[productId] = scannedItemsList
        completedItems.add(productId)

        viewModelScope.launch {
            // 更新到數據庫
            loadingRepository.updateItemCompletionStatus(
                routeId = selectedRoute.id,
                productId = productId,
                mode = selectedMode,
                scannedBaskets = scannedItemsList
            )

            // 更新路線狀態為 IN_PROGRESS
            updateRouteStatusToInProgress(selectedRoute.id)

            Timber.d("✅ Item completed and saved: ${selectedItemWithStock.item.productName}")
            Timber.d("  - Mode: $selectedMode")
            Timber.d("  - Scanned items saved: ${scannedItemsList.size}")
            Timber.d("  - Total scanned quantity: ${scannedItemsList.sumOf { it.scannedQuantity }}")

            _uiState.update {
                it.copy(successMessage = "✅ ${selectedItemWithStock.item.productName} 已確認")
            }

            clearCurrentScannedData()

            //  重新從數據庫加載最新的路線數據（等待完成）
            loadingRepository.getRouteById(selectedRoute.id, false)
                .onSuccess { updatedRoute ->
                    // 同時更新全局路線列表
                    _uiState.update { currentState ->
                        currentState.copy(
                            routes = currentState.routes.map { route ->
                                if (route.id == updatedRoute.id) updatedRoute else route
                            }
                        )
                    }

                    // 再調用 selectRoute 更新 availableItemsWithStock
                    selectRoute(updatedRoute)

                    Timber.d("🔄 Route updated: ${updatedRoute.name}")
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to reload route")
                    // 如果加載失敗，使用舊數據
                    selectRoute(selectedRoute)
                }
        }
    }

    /**
     * 檢查當前倉庫是否全部完成
     * 只檢查在當前倉庫有庫存的產品
     */
    private fun isWarehouseComplete(): Boolean {
        val availableItems = _uiState.value.availableItemsWithStock

        // 過濾出在當前倉庫有庫存的產品
        val itemsNeedingCompletion = availableItems.filter {
            it.availableBaskets.isNotEmpty() || it.isCompleted
        }

        if (itemsNeedingCompletion.isEmpty()) {
            Timber.w("⚠️ No items need completion in current warehouse")
            return false
        }

        val allCompleted = itemsNeedingCompletion.all { it.isCompleted }

        Timber.d("📊 Warehouse completion check:")
        Timber.d("  - Total items in warehouse: ${itemsNeedingCompletion.size}")
        Timber.d("  - Completed items: ${itemsNeedingCompletion.count { it.isCompleted }}")
        Timber.d("  - All completed: $allCompleted")

        return allCompleted
    }

    /**
     * 提交上貨（使用所有已完成產品的掃描數據）
     */
    fun submitLoading() {
        viewModelScope.launch {
            val selectedRoute = _uiState.value.selectedRoute
            val selectedWarehouseId = _uiState.value.selectedWarehouseId
            val selectedMode = _uiState.value.selectedMode

            if (selectedRoute == null || selectedWarehouseId == null || selectedMode == null) {
                _uiState.update { it.copy(error = "缺少必要信息") }
                return@launch
            }

            if (!isWarehouseComplete()) {
                _uiState.update { it.copy(error = "還有產品未完成") }
                return@launch
            }

            val allScannedItems = allCompletedScans.values.flatten()

            if (allScannedItems.isEmpty()) {
                _uiState.update { it.copy(error = "沒有掃描數據") }
                return@launch
            }

            Timber.d("📦 Submitting loading with all scanned items:")
            Timber.d("  - Total completed products: ${completedItems.size}")
            Timber.d("  - Total baskets scanned: ${allScannedItems.size}")
            Timber.d("  - Total quantity: ${allScannedItems.sumOf { it.scannedQuantity }}")

            allCompletedScans.forEach { (productId, items) ->
                val productName = items.firstOrNull()?.loadingItem?.productName ?: productId
                Timber.d("    • $productName: ${items.size} baskets, ${items.sumOf { it.scannedQuantity }} qty")
            }

            _uiState.update { it.copy(isLoading = true) }

            val isOnline = _networkState.value is NetworkState.Connected

            loadingRepository.submitLoading(
                routeId = selectedRoute.id,
                routeName = selectedRoute.name,
                deliveryDate = selectedRoute.deliveryDate,
                warehouseId = selectedWarehouseId,
                mode = selectedMode,
                scannedItems = allScannedItems,
                isOnline = isOnline
            ).onSuccess {
                Timber.d("✅ Loading submitted successfully")

                // 清空所有數據
                completedItems.clear()
                allCompletedScans.clear()
                clearCurrentScannedData()

                // 先顯示成功消息
                _uiState.update {
                    it.copy(successMessage = "✅ 上貨提交成功")
                }

                // 等待重新加載路線數據
                loadRoutesAsync()
                    .onSuccess { routes ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                routes = routes
                            )
                        }
                        Timber.d("🔄 Routes reloaded successfully: ${routes.size} routes")

                        // 返回選路線頁面
                        setCurrentStep(LoadingStep.SELECT_ROUTE)
                    }
                    .onFailure { error ->
                        Timber.e(error, "Failed to reload routes")
                        _uiState.update {
                            it.copy(isLoading = false)
                        }
                        // 即使重新加載失敗，也返回選路線頁面
                        setCurrentStep(LoadingStep.SELECT_ROUTE)
                    }
            }.onFailure { error ->
                Timber.e(error, "Failed to submit loading")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "上貨失敗: ${error.message}"
                    )
                }
            }
        }
    }

    /**
     * 重置指定產品的完成狀態
     */
    fun resetProductCompletion(productId: String, mode: LoadingMode) {
        viewModelScope.launch {
            val selectedRoute = _uiState.value.selectedRoute ?: return@launch

            // 從已完成集合中移除
            completedItems.remove(productId)
            allCompletedScans.remove(productId)

            // 更新路線數據
            loadingRepository.resetItemCompletionStatus(
                routeId = selectedRoute.id,
                productId = productId,
                mode = mode
            )

            _uiState.update {
                it.copy(successMessage = "✅ 已重置產品完成狀態")
            }

            // 重新加載路線
            loadRoutes()
        }
    }

    /**
     * 更新路線狀態為 IN_PROGRESS
     */
    private suspend fun updateRouteStatusToInProgress(routeId: String) {
        loadingRepository.updateRouteStatus(routeId, LoadingStatus.IN_PROGRESS)
    }

    /**
     * 重置整條路線的完成狀態
     */
    fun resetRouteCompletion(routeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            loadingRepository.resetRouteCompletion(routeId)
                .onSuccess {
                    // 清空所有已完成的數據
                    completedItems.clear()
                    allCompletedScans.clear()

                    resetBasketsStatus(routeId)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "✅ 路線已重置"
                        )
                    }

                    // 重新加載路線
                    loadRoutes()
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to reset route")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "重置失敗: ${error.message}"
                        )
                    }
                }
        }
    }

    /**
     * 重置籃子狀態（從 SHIPPED 改回 IN_STOCK）
     */
    private suspend fun resetBasketsStatus(routeId: String) {
        try {
            val route = loadingRepository.getRouteById(routeId, false).getOrNull() ?: return

            // 獲取所有已掃描的籃子 UID
            val scannedUids = route.items.flatMap { it.completionStatus.scannedBasketUids }

            // 重置這些籃子的狀態
            loadingRepository.resetBasketsToInStock(scannedUids)

            Timber.d("✅ Reset ${scannedUids.size} baskets to IN_STOCK")
        } catch (e: Exception) {
            Timber.e(e, "Failed to reset baskets status")
        }
    }

    /**
     * 清空當前產品的掃描數據（不清空已保存的數據）
     */
    private fun clearCurrentScannedData() {
        scannedBaskets.clear()
        _uiState.update {
            it.copy(
                scannedItems = emptyList(),
                totalScanned = 0,
                expectedQuantity = 0,
                isComplete = false
            )
        }
    }

    /**
     * 清空所有掃描數據（包括已保存的）
     */
    private fun clearAllScannedData() {
        scannedBaskets.clear()
        allCompletedScans.clear()
        completedItems.clear()
        _uiState.update {
            it.copy(
                scannedItems = emptyList(),
                totalScanned = 0,
                expectedQuantity = 0,
                isComplete = false
            )
        }
    }

    /**
     * 重置狀態
     */
    fun reset() {
        scanManager.stopScanning()
        clearAllScannedData()
        _uiState.update { LoadingUiState() }
        loadRoutes()
    }

    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 清除成功消息
     */
    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    /**
     * 返回上一步
     */
    fun goBack() {
        val currentStep = _uiState.value.currentStep
        when (currentStep) {
            LoadingStep.SELECT_WAREHOUSE -> setCurrentStep(LoadingStep.SELECT_MODE)
            LoadingStep.SELECT_ROUTE -> setCurrentStep(LoadingStep.SELECT_WAREHOUSE)
            LoadingStep.ROUTE_DETAIL -> setCurrentStep(LoadingStep.SELECT_ROUTE)
            LoadingStep.SELECT_ITEM -> setCurrentStep(LoadingStep.SELECT_ROUTE)
            LoadingStep.SCANNING -> {
                scanManager.stopScanning()
                _uiState.value.selectedRoute?.let { selectRoute(it) }
            }
            LoadingStep.SUMMARY -> reset()
            else -> {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanManager.cleanup()
        validatingUids.clear()
    }
}

/**
 * 上貨 UI 狀態
 */
data class LoadingUiState(
    val isLoading: Boolean = false,
    val isValidating: Boolean = false,
    val isScanning: Boolean = false,
    val currentStep: LoadingStep = LoadingStep.SELECT_MODE,
    val isLoadingWarehouses: Boolean = false,

    val scanMode: ScanMode = ScanMode.SINGLE,

    // 選擇的數據
    val selectedMode: LoadingMode? = null,
    val selectedWarehouseId: String? = null,
    val selectedWarehouseName: String? = null,
    val selectedRoute: LoadingRoute? = null,
    val selectedItemWithStock: LoadingItemWithStock? = null,

    // 可用數據
    val routes: List<LoadingRoute> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),
    val availableItemsWithStock: List<LoadingItemWithStock> = emptyList(),
    val warehouseBaskets: List<Basket> = emptyList(),

    // 掃描數據（當前產品）
    val scannedItems: List<LoadingScannedItem> = emptyList(),
    val totalScanned: Int = 0,
    val expectedQuantity: Int = 0,
    val isComplete: Boolean = false,

    // 消息
    val error: String? = null,
    val successMessage: String? = null
)

data class LoadingItemWithStock(
    val item: LoadingItem,
    val availableBaskets: List<Basket>,
    val availableQuantity: Int,
    val isCompleted: Boolean = false
)