package com.kdl.rfidinventory.presentation.ui.screens.shipping

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.repository.BasketValidationForInventoryResult
import com.kdl.rfidinventory.data.repository.LoadingRepository
import com.kdl.rfidinventory.data.repository.WarehouseRepository
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
 * 出貨驗證步驟
 */
enum class ShippingVerifyStep {
    SELECT_ROUTE,       // 步驟 1: 選擇路線
    SCANNING,          // 步驟 2: 掃描驗證
    SUBMIT             // 步驟 3: 提交
}

/**
 * 驗證項狀態
 */
enum class VerifyItemStatus {
    PENDING,        // 待驗證（灰色）
    VERIFIED,       // 已驗證（綠色）
    EXTRA           // 額外項（橙色 - 不在列表但實際存在）
}

/**
 * 驗證項
 */
data class VerifyItem(
    val id: String = UUID.randomUUID().toString(),
    val basket: Basket,
    val status: VerifyItemStatus,
    val scanCount: Int = 0  // 掃描次數（用於追蹤重覆掃描）
)

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class ShippingVerifyViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val loadingRepository: LoadingRepository,
    private val warehouseRepository: WarehouseRepository,
    private val pendingOperationDao: PendingOperationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShippingVerifyUiState())
    val uiState: StateFlow<ShippingVerifyUiState> = _uiState.asStateFlow()

    val networkState: StateFlow<NetworkState> = pendingOperationDao.getPendingCount()
        .map { count ->
            if (count > 0) NetworkState.Disconnected(count)
            else NetworkState.Connected
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NetworkState.Connected
        )

    val scanState = scanManager.scanState

    private var expectedBaskets: List<Basket> = emptyList()
    private val validatingUids = mutableSetOf<String>()

    init {
        Timber.d("📦 ShippingVerifyViewModel initialized")
        loadRoutes()
        initializeScanManager()
        observeScanResults()
        observeScanErrors()
    }

    // ==================== 步驟 1: 加載路線 ====================

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadRoutes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRoutes = true) }

            val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            val isOnline = networkState.value is NetworkState.Connected

            loadingRepository.getLoadingRoutes(today, isOnline)
                .onSuccess { allRoutes ->
                    // 顯示所有路線（不過濾）
                    _uiState.update {
                        it.copy(
                            routes = allRoutes,
                            isLoadingRoutes = false
                        )
                    }

                    Timber.d("✅ Loaded ${allRoutes.size} routes")
                    allRoutes.forEach { route ->
                        Timber.d("  - ${route.name}: ${route.status}")
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load routes")
                    _uiState.update {
                        it.copy(
                            isLoadingRoutes = false,
                            error = "加載路線失敗: ${error.message}"
                        )
                    }
                }
        }
    }

    fun selectRoute(route: LoadingRoute) {
        Timber.d("🚚 Selected route: ${route.name}")

        _uiState.update {
            it.copy(
                selectedRoute = route,
                currentStep = ShippingVerifyStep.SCANNING
            )
        }

        prepareRouteVerification(route)
    }

    /**
     * 準備路線驗證
     */
    private fun prepareRouteVerification(route: LoadingRoute) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // ✅ 檢查路線狀態
                val canScan = checkRouteCanScan(route)

                if (!canScan.first) {
                    // ✅ 不可掃描，只能查看
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            canScan = false,
                            scanDisabledReason = canScan.second
                        )
                    }

                    Timber.d("⚠️ Route ${route.name} is view-only: ${canScan.second}")

                    // 仍然加載籃子數據用於查看
                    loadRouteBaskets(route, canScan = false)
                    return@launch
                }

                // ✅ 可以掃描驗證
                loadRouteBaskets(route, canScan = true)

            } catch (e: Exception) {
                Timber.e(e, "Failed to prepare route verification")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "準備驗證失敗: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 檢查路線是否可以掃描
     * @return Pair<可以掃描, 不可掃描原因>
     */
    private suspend fun checkRouteCanScan(route: LoadingRoute): Pair<Boolean, String?> {
        // ✅ 檢查 1: 路線狀態必須為 COMPLETED
        if (route.status != LoadingStatus.COMPLETED) {
            val reason = when (route.status) {
                LoadingStatus.PENDING -> "路線尚未開始上貨"
                LoadingStatus.IN_PROGRESS -> "路線上貨未完成"
                LoadingStatus.VERIFIED -> "路線已完成驗證"
                else -> "路線狀態不正確"
            }
            return false to reason
        }

        // ✅ 檢查 2: 所有籃子狀態必須為 LOADING
        val routeBaskets = warehouseRepository.getBasketsByRouteId(route.id)
            .getOrNull() ?: emptyList()

        val invalidBaskets = routeBaskets.filter { it.status != BasketStatus.LOADING }

        if (invalidBaskets.isNotEmpty()) {
            Timber.w("⚠️ Found ${invalidBaskets.size} baskets with invalid status:")
            invalidBaskets.forEach { basket ->
                Timber.w("  - ${basket.uid}: ${basket.status}")
            }

            return false to "路線中有籃子狀態異常（非 LOADING 狀態）"
        }

        return true to null
    }

    /**
     * 加載路線籃子數據
     */
    private suspend fun loadRouteBaskets(route: LoadingRoute, canScan: Boolean) {
        // 獲取所有籃子（不過濾狀態）
        val allBaskets = warehouseRepository.getBasketsByRouteId(route.id)
            .getOrThrow()

        Timber.d("📦 Total baskets for route ${route.name}: ${allBaskets.size}")

        // 統計不同狀態的籃子
        val basketsByStatus = allBaskets.groupBy { it.status }
        basketsByStatus.forEach { (status, baskets) ->
            Timber.d("  - ${getBasketStatusText(status)}: ${baskets.size} baskets")
        }

        // 如果可以掃描，expectedBaskets 只包含 LOADING 狀態的籃子
        // 否則為空（只讀模式不需要驗證）
        expectedBaskets = if (canScan) {
            allBaskets.filter { it.status == BasketStatus.LOADING }
        } else {
            emptyList()
        }

        // 創建驗證項（顯示所有籃子）
        val items = allBaskets.map { basket ->
            // 根據籃子狀態決定初始驗證狀態
            val initialStatus = when (basket.status) {
                BasketStatus.SHIPPED -> VerifyItemStatus.VERIFIED // SHIPPED = 已完成驗證
                BasketStatus.LOADING -> VerifyItemStatus.PENDING  // LOADING = 待驗證
                else -> VerifyItemStatus.EXTRA                    // 其他 = 額外項
            }

            VerifyItem(
                basket = basket,
                status = initialStatus
            )
        }

        // 排序：已驗證放最後，額外項放最前，待驗證在中間
        val sortedItems = items.sortedBy {
            when (it.status) {
                VerifyItemStatus.EXTRA -> 0
                VerifyItemStatus.PENDING -> 1
                VerifyItemStatus.VERIFIED -> 2
            }
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                canScan = canScan,
                verifyItems = sortedItems,
                statistics = calculateStatistics(sortedItems)
            )
        }

        Timber.d("📊 Route verification prepared:")
        Timber.d("  - Total baskets: ${sortedItems.size}")
        Timber.d("  - PENDING: ${sortedItems.count { it.status == VerifyItemStatus.PENDING }}")
        Timber.d("  - VERIFIED: ${sortedItems.count { it.status == VerifyItemStatus.VERIFIED }}")
        Timber.d("  - EXTRA: ${sortedItems.count { it.status == VerifyItemStatus.EXTRA }}")
        Timber.d("  - canScan: $canScan")
    }

    // ==================== 步驟 2: 掃描驗證 ====================

    private fun initializeScanManager() {
        scanManager.initialize(
            scope = viewModelScope,
            scanMode = _uiState.map { it.scanMode }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                ScanMode.SINGLE
            ),
            canStartScan = {
                _uiState.value.currentStep == ShippingVerifyStep.SCANNING
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
        if (_uiState.value.currentStep != ShippingVerifyStep.SCANNING) {
            _uiState.update { it.copy(error = "請先選擇路線") }
            return
        }

        // 檢查是否可以掃描
        if (!_uiState.value.canScan) {
            _uiState.update {
                it.copy(error = _uiState.value.scanDisabledReason ?: "此路線無法掃描驗證")
            }
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

            val currentItems = _uiState.value.verifyItems
            val existingItem = currentItems.find { it.basket.uid == uid }

            if (existingItem != null) {
                // 籃子在列表中 - 標記為已驗證
                if (existingItem.status == VerifyItemStatus.VERIFIED) {
                    // 重覆掃描
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            error = "籃子 ${uid.takeLast(8)} 已驗證（重覆掃描 ${existingItem.scanCount + 1} 次）"
                        )
                    }
                } else {
                    // 首次掃描
                    updateItemStatus(uid, VerifyItemStatus.VERIFIED)

                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            successMessage = "✅ 籃子 ${uid.takeLast(8)} 已確認"
                        )
                    }
                }
            } else {
                // 籃子不在列表中 - 檢查是否為額外項
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
     * 檢查額外籃子（不在列表但實際存在）
     */
    private suspend fun checkExtraBasket(uid: String) {
        val route = _uiState.value.selectedRoute!!

        val result = warehouseRepository.validateBasketForInventory(
            uid = uid,
            warehouseId = route.id,
            isOnline = networkState.value is NetworkState.Connected
        )

        when (result) {
            is BasketValidationForInventoryResult.Valid -> {
                val basket = result.basket

                // ✅ 檢查狀態：允許 LOADING 和 UNASSIGNED
                if (basket.status != BasketStatus.LOADING &&
                    basket.status != BasketStatus.UNASSIGNED) {
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            error = "❌ 籃子 ${uid.takeLast(8)} 狀態錯誤（${getBasketStatusText(basket.status)}）"
                        )
                    }
                    return
                }

                // ✅ 作為額外項加入
                addExtraItem(basket)

                val statusNote = if (basket.status == BasketStatus.UNASSIGNED) {
                    "未分配籃子"
                } else {
                    ""
                }

                _uiState.update {
                    it.copy(
                        isValidating = false,
                        successMessage = buildString {
                            append("⚠️ 額外項: ${uid.takeLast(8)}")
                            basket.product?.let { product ->
                                append(" (${product.name})")
                            }
                            if (statusNote.isNotEmpty()) {
                                append(" - $statusNote")
                            }
                        }
                    )
                }

                Timber.d("📦 Extra basket added: $uid, status=${basket.status}, product=${basket.product?.name}")
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
                // ✅ 如果是 UNASSIGNED，也允許作為額外項
                if (result.basket.status == BasketStatus.UNASSIGNED) {
                    addExtraItem(result.basket)

                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            successMessage = "⚠️ 額外項: ${uid.takeLast(8)} (未分配籃子)"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            error = "❌ 籃子 ${uid.takeLast(8)} 不屬於此路線（屬於 ${result.basket.warehouseId}）"
                        )
                    }
                }
            }

            is BasketValidationForInventoryResult.InvalidStatus -> {
                _uiState.update {
                    it.copy(
                        isValidating = false,
                        error = "❌ 籃子 ${uid.takeLast(8)} 狀態異常"
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
    private fun updateItemStatus(uid: String, status: VerifyItemStatus) {
        _uiState.update { state ->
            val updatedItems = state.verifyItems.map { item ->
                if (item.basket.uid == uid) {
                    item.copy(
                        status = status,
                        scanCount = item.scanCount + 1
                    )
                } else {
                    item
                }
            }

            // 排序：已驗證放最後，額外項放最前
            val sortedItems = updatedItems.sortedBy {
                when (it.status) {
                    VerifyItemStatus.EXTRA -> 0
                    VerifyItemStatus.PENDING -> 1
                    VerifyItemStatus.VERIFIED -> 2
                }
            }

            state.copy(
                verifyItems = sortedItems,
                statistics = calculateStatistics(sortedItems)
            )
        }
    }

    /**
     * 添加額外項
     */
    private fun addExtraItem(basket: Basket) {
        val item = VerifyItem(
            basket = basket,
            status = VerifyItemStatus.EXTRA,
            scanCount = 1
        )

        _uiState.update { state ->
            val updatedItems = listOf(item) + state.verifyItems

            state.copy(
                verifyItems = updatedItems,
                statistics = calculateStatistics(updatedItems)
            )
        }
    }

    /**
     * 刪除項目（僅額外項可刪除）
     */
    fun removeItem(itemId: String) {
        _uiState.update { state ->
            val updatedItems = state.verifyItems.filter { it.id != itemId }

            state.copy(
                verifyItems = updatedItems,
                statistics = calculateStatistics(updatedItems)
            )
        }
    }

    /**
     * 計算統計數據
     */
    private fun calculateStatistics(items: List<VerifyItem>): VerifyStatistics {
        // ✅ 區分：
        // - LOADING 狀態的籃子 → 需要驗證的項目
        // - SHIPPED 狀態的籃子 → 已完成驗證的項目
        // - 其他狀態 → 額外項

        val loadingBaskets = items.filter { it.basket.status == BasketStatus.LOADING }
        val shippedBaskets = items.filter { it.basket.status == BasketStatus.SHIPPED }
        val otherBaskets = items.filter {
            it.basket.status != BasketStatus.LOADING &&
                    it.basket.status != BasketStatus.SHIPPED
        }

        return VerifyStatistics(
            totalItems = loadingBaskets.size, // 總項目數（需要驗證的）
            verifiedItems = loadingBaskets.count { it.status == VerifyItemStatus.VERIFIED }, // 已驗證數
            extraItems = otherBaskets.size, // 額外項（非 LOADING/SHIPPED）
            pendingItems = loadingBaskets.count { it.status == VerifyItemStatus.PENDING }, // 待驗證
            alreadyVerifiedItems = shippedBaskets.size // 已完成驗證的籃子數（SHIPPED 狀態）
        )
    }

    // ==================== 步驟 3: 提交驗證 ====================

    fun submitVerification() {
        // ✅ 防止重覆提交
        if (_uiState.value.hasSubmitted) {
            _uiState.update {
                it.copy(error = "此路線已完成驗證，無法重覆提交")
            }
            return
        }

        viewModelScope.launch {
            val route = _uiState.value.selectedRoute
            if (route == null) {
                _uiState.update { it.copy(error = "缺少路線信息") }
                return@launch
            }

            _uiState.update { it.copy(isSubmitting = true) }

            try {
                val verifyData = collectVerificationData()

                Timber.d("📦 ========== 提交出貨驗證數據 ==========")
                Timber.d("路線: ${route.name}")
                Timber.d("總籃子數: ${verifyData.totalBaskets}")
                Timber.d("總數量: ${verifyData.totalQuantity}")
                Timber.d("已驗證: ${verifyData.verifiedCount}")
                Timber.d("額外項: ${verifyData.extraCount}")

                val isOnline = networkState.value is NetworkState.Connected

                loadingRepository.submitShippingVerify(
                    routeId = route.id,
                    routeName = route.name,
                    deliveryDate = route.deliveryDate,
                    scannedBaskets = _uiState.value.verifyItems
                        .filter { it.status != VerifyItemStatus.PENDING }
                        .map { it.basket },
                    expectedTotal = route.totalQuantity,
                    isOnline = isOnline
                ).onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            hasSubmitted = true, // ✅ 標記為已提交
                            successMessage = "✅ 出貨驗證數據已成功提交"
                        )
                    }

                    Timber.d("✅ Shipping verification submitted successfully")
                }.onFailure { error ->
                    Timber.e(error, "提交出貨驗證失敗")
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = "提交失敗: ${error.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "提交出貨驗證數據失敗")
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        error = "提交失敗: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 收集驗證數據
     */
    private fun collectVerificationData(): VerificationSubmitData {
        val route = _uiState.value.selectedRoute!!
        val items = _uiState.value.verifyItems

        return VerificationSubmitData(
            routeId = route.id,
            routeName = route.name,
            deliveryDate = route.deliveryDate,
            totalBaskets = items.count { it.status != VerifyItemStatus.PENDING },
            totalQuantity = items
                .filter { it.status != VerifyItemStatus.PENDING }
                .sumOf { it.basket.quantity },
            verifiedCount = items.count { it.status == VerifyItemStatus.VERIFIED },
            extraCount = items.count { it.status == VerifyItemStatus.EXTRA },
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * 重置驗證
     */
    fun resetVerification() {
        scanManager.stopScanning()

        _uiState.update {
            ShippingVerifyUiState()
        }

        expectedBaskets = emptyList()
        loadRoutes()
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

data class ShippingVerifyUiState(
    val isLoading: Boolean = false,
    val isValidating: Boolean = false,
    val isSubmitting: Boolean = false,
    val isLoadingRoutes: Boolean = false,
    val hasSubmitted: Boolean = false,
    val canScan: Boolean = true,
    val scanDisabledReason: String? = null,

    // 步驟控制
    val currentStep: ShippingVerifyStep = ShippingVerifyStep.SELECT_ROUTE,

    // 掃描設置
    val scanMode: ScanMode = ScanMode.SINGLE,

    // 路線選擇
    val routes: List<LoadingRoute> = emptyList(),
    val selectedRoute: LoadingRoute? = null,

    // 驗證項目
    val verifyItems: List<VerifyItem> = emptyList(),
    val statistics: VerifyStatistics = VerifyStatistics(),

    // 消息
    val error: String? = null,
    val successMessage: String? = null
)

data class VerifyStatistics(
    val totalItems: Int = 0,        // 總項目數（不含額外項）
    val verifiedItems: Int = 0,     // 已驗證
    val extraItems: Int = 0,        // 額外項
    val pendingItems: Int = 0,       // 待驗證
    val alreadyVerifiedItems: Int = 0 // 已完成驗證的籃子（SHIPPED 狀態）
)

// ==================== 提交數據模型 ====================

data class VerificationSubmitData(
    val routeId: String,
    val routeName: String,
    val deliveryDate: String,
    val totalBaskets: Int,
    val totalQuantity: Int,
    val verifiedCount: Int,
    val extraCount: Int,
    val timestamp: Long
)