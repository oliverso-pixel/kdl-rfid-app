// LoadingRepository.kt
package com.kdl.rfidinventory.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.datastore.LoadingRoutesDataStore
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.api.LoadingApiService
import com.kdl.rfidinventory.data.remote.dto.request.LoadingItemRequest
import com.kdl.rfidinventory.data.remote.dto.request.LoadingRequest
import com.kdl.rfidinventory.data.remote.dto.request.ShippingVerifyRequest
import com.kdl.rfidinventory.data.remote.dto.response.LoadingItemResponse
import com.kdl.rfidinventory.data.remote.dto.response.LoadingRouteResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadingRepository @Inject constructor(
    private val loadingApiService: LoadingApiService,
    private val basketDao: BasketDao,
    private val pendingOperationDao: PendingOperationDao,
    private val loadingRoutesDataStore: LoadingRoutesDataStore
) {

    /**
     * 初始化 - 確保本地數據文件存在
     */
    suspend fun initialize() {
        loadingRoutesDataStore.initialize()
    }

    /**
     * 獲取上貨路線列表
     */
    suspend fun getLoadingRoutes(date: String, isOnline: Boolean): Result<List<LoadingRoute>> =
        withContext(Dispatchers.IO) {
            try {
                if (isOnline) {
                    try {
                        // 嘗試從 API 獲取數據
                        val response = loadingApiService.getLoadingRoutes(date)
                        if (response.success && response.data != null) {
                            val routes = response.data.map { it.toLoadingRoute() }

                            // 同步到本地存儲
                            loadingRoutesDataStore.syncFromApi(routes)

                            Timber.d("✅ Loaded ${routes.size} routes from API and synced to local")
                            return@withContext Result.success(routes)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "⚠️ Failed to fetch from API, falling back to local data")
                    }
                }

                //  在線模式失敗或離線模式：從本地讀取
                loadingRoutesDataStore.getLoadingRoutesByDate(date)
//                Result.success(mockLoadingRoutes())
            } catch (e: Exception) {
                Timber.e(e, "Failed to get loading routes")
                Result.failure(e)
            }
        }

    /**
     * 獲取路線詳情
     */
    suspend fun getRouteDetail(routeId: String, isOnline: Boolean): Result<LoadingRoute> =
        withContext(Dispatchers.IO) {
            try {
                if (isOnline) {
                    try {
                        val response = loadingApiService.getRouteDetail(routeId)
                        if (response.success && response.data != null) {
                            val route = response.data.toLoadingRoute()

                            // ✅ 更新本地數據
                            loadingRoutesDataStore.updateRoute(route)

                            return@withContext Result.success(route)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "⚠️ Failed to fetch route detail from API")
                    }
                }

                // ✅ 從本地讀取
                loadingRoutesDataStore.getRouteById(routeId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get route detail")
                Result.failure(e)
            }
        }

    /**
     * 獲取倉庫列表
     */
    suspend fun getWarehouses(): Result<List<Warehouse>> {
        return try {
            // TODO: 替換為真實 API 調用
            delay(500)
            Result.success(mockWarehouses())
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch warehouses")
            delay(500)
            Result.success(mockWarehouses())
        }
    }

    /**
     * 根據產品和倉庫獲取可用籃子
     */
    suspend fun getAvailableBaskets(
        productId: String,
        warehouseId: String,
        requiredQuantity: Int
    ): Result<List<Basket>> = withContext(Dispatchers.IO) {
        try {
            val basketEntities = basketDao.getBasketsByProductAndWarehouse(
                productId = productId,
                warehouseId = warehouseId,
                status = BasketStatus.IN_STOCK
            )

            val baskets = basketEntities.map { it.toBasket() }
                .sortedByDescending { it.quantity }

            Result.success(baskets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 根據 UID 獲取籃子信息
     */
    suspend fun getBasketByUid(uid: String): Result<Basket> = withContext(Dispatchers.IO) {
        try {
            val entity = basketDao.getBasketByUid(uid)
            if (entity != null) {
                Result.success(entity.toBasket())
            } else {
                Result.failure(Exception("籃子不存在: $uid"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 提交上貨數據
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun submitLoading(
        routeId: String,
        routeName: String,
        deliveryDate: String,
        warehouseId: String,
        mode: LoadingMode,
        scannedItems: List<LoadingScannedItem>,
        isOnline: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

            val itemRequests = scannedItems.groupBy { it.loadingItem.productId }.map { (_, items) ->
                val first = items.first()
                LoadingItemRequest(
                    productId = first.loadingItem.productId,
                    productName = first.loadingItem.productName,
                    batchId = first.basket.batch?.id,
                    basketUids = items.map { it.basket.uid },
                    scannedQuantity = items.sumOf { it.scannedQuantity },
                    expectedQuantity = items.sumOf { it.expectedQuantity },
                    isLoose = first.isLoose
                )
            }

            val request = LoadingRequest(
                routeId = routeId,
                routeName = routeName,
                deliveryDate = deliveryDate,
                warehouseId = warehouseId,
                mode = mode.name,
                items = itemRequests,
                totalScanned = scannedItems.sumOf { it.scannedQuantity },
                timestamp = timestamp
            )

            if (isOnline) {
                try {
                    val response = loadingApiService.submitLoading(request)

                    if (response.success) {
                        updateBasketsStatusToLoaded(routeId, scannedItems)

                        // 檢查路線完成狀態
                        val route = loadingRoutesDataStore.getRouteById(routeId).getOrNull()
                        if (route != null) {
                            checkAndUpdateRouteCompletion(route)
                        }

                        return@withContext Result.success(Unit)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "⚠️ Failed to submit to API, saving to pending queue")
                }
            }

            // 在線失敗或離線模式：保存到待同步隊列
            val operation = PendingOperationEntity(
                operationType = OperationType.SHIPPING_SHIP,
                uid = routeId,
                payload = Json.encodeToString(request),
                timestamp = System.currentTimeMillis()
            )
            pendingOperationDao.insertOperation(operation)

            updateBasketsStatusToLoaded(routeId, scannedItems)

            // 檢查路線完成狀態
            val route = loadingRoutesDataStore.getRouteById(routeId).getOrNull()
            if (route != null) {
                checkAndUpdateRouteCompletion(route)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 提交出貨驗證數據
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun submitShippingVerify(
        routeId: String,
        routeName: String,
        deliveryDate: String,
        scannedBaskets: List<Basket>,
        expectedTotal: Int,
        isOnline: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            val totalScanned = scannedBaskets.sumOf { it.quantity }

            val request = ShippingVerifyRequest(
                routeId = routeId,
                routeName = routeName,
                deliveryDate = deliveryDate,
                basketUids = scannedBaskets.map { it.uid },
                totalScanned = totalScanned,
                totalExpected = expectedTotal,
                isComplete = totalScanned == expectedTotal,
                timestamp = timestamp
            )

            if (isOnline) {
                try {
                    val response = loadingApiService.submitShippingVerify(request)

                    if (response.success) {
                        // 更新籃子狀態
                        updateBasketsStatusToShipped(routeId, scannedBaskets)

                        // 更新路線狀態為 VERIFIED
                        updateRouteStatusToVerified(routeId)

                        return@withContext Result.success(Unit)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "⚠️ Failed to submit verification to API")
                }
            }

            // 保存到待同步隊列
            val operation = PendingOperationEntity(
                operationType = OperationType.SHIPPING_VERIFY,
                uid = routeId,
                payload = Json.encodeToString(request),
                timestamp = System.currentTimeMillis()
            )
            pendingOperationDao.insertOperation(operation)

            // 更新籃子狀態
            updateBasketsStatusToShipped(routeId, scannedBaskets)

            // 更新路線狀態為 VERIFIED
            updateRouteStatusToVerified(routeId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 更新籃子狀態為已出貨（包含額外項處理）
     */
    private suspend fun updateBasketsStatusToShipped(
        routeId: String,
        baskets: List<Basket>
    ) {
        baskets.forEach { basket ->
            basketDao.getBasketByUid(basket.uid)?.let { entity ->
                // 如果是 UNASSIGNED 狀態，更新 warehouseId
                val newWarehouseId = if (entity.status == BasketStatus.UNASSIGNED) {
                    routeId
                } else {
                    entity.warehouseId
                }

                basketDao.updateBasket(
                    entity.copy(
                        status = BasketStatus.SHIPPED,
                        warehouseId = newWarehouseId,
                        lastUpdated = System.currentTimeMillis()
                    )
                )

                Timber.d("✅ Updated basket ${basket.uid}: status=SHIPPED, warehouseId=$newWarehouseId")
            }
        }
    }

    /**
     * 更新路線狀態為已驗證
     */
    private suspend fun updateRouteStatusToVerified(routeId: String) {
        loadingRoutesDataStore.updateRouteCompletionStatus(routeId) { route ->
            route.copy(
                status = LoadingStatus.VERIFIED,
                completionStatus = route.completionStatus.copy(
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }

        Timber.d("✅ Route $routeId status updated to VERIFIED")
    }

    /**
     * 更新籃子狀態為已上貨
     */
//    private suspend fun updateBasketsStatusToLoaded(uids: List<String>) {
//        uids.forEach { uid ->
//            basketDao.getBasketByUid(uid)?.let { entity ->
//                basketDao.updateBasket(
//                    entity.copy(
//                        status = BasketStatus.LOADING,
//                        lastUpdated = System.currentTimeMillis()
//                    )
//                )
//            }
//        }
//    }
    private suspend fun updateBasketsStatusToLoaded(
        routeId: String,
        scannedItems: List<LoadingScannedItem>
    ) {
        scannedItems.forEach { scannedItem ->
            basketDao.getBasketByUid(scannedItem.basket.uid)?.let { entity ->
                basketDao.updateBasket(
                    entity.copy(
                        status = BasketStatus.LOADING,
                        warehouseId = routeId,
                        quantity = scannedItem.scannedQuantity,
                        lastUpdated = System.currentTimeMillis()
                    )
                )

                Timber.d("✅ Updated basket ${scannedItem.basket.uid}: warehouseId=$routeId, quantity=${scannedItem.scannedQuantity}")
            }
        }
    }

    /**
     * 獲取倉庫在庫籃子
     */
    suspend fun getWarehouseBaskets(warehouseId: String): Result<List<Basket>> =
        withContext(Dispatchers.IO) {
            try {
                val basketEntities = basketDao.getBasketsByWarehouse(
                    warehouseId = warehouseId,
                    statuses = BasketStatus.IN_STOCK
                )

                val baskets = basketEntities.map { it.toBasket() }

                Timber.d("📦 Loaded ${baskets.size} baskets from warehouse $warehouseId")
                Timber.d("📦 baskets ${baskets}")
                Result.success(baskets)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load warehouse baskets")
                Result.failure(e)
            }
        }

    /**
     * 監聽路線實時狀態（從本地讀取）
     */
    suspend fun observeRouteStatus(routeId: String): Flow<LoadingRoute> = flow {
        while (true) {
            try {
                val route = loadingRoutesDataStore.getRouteById(routeId).getOrThrow()
                emit(route)
                delay(2000) // 每 2 秒刷新一次
            } catch (e: Exception) {
                Timber.e(e, "Failed to observe route status")
                delay(5000)
            }
        }
    }

    /**
     * 更新產品項完成狀態
     */
    suspend fun updateItemCompletionStatus(
        routeId: String,
        productId: String,
        mode: LoadingMode,
        scannedBaskets: List<LoadingScannedItem>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            loadingRoutesDataStore.updateItemCompletionStatus(routeId, productId) { route ->
                val updatedItems = route.items.map { item ->
                    if (item.productId == productId) {
                        // 更新該產品的完成狀態
                        val newStatus = when (mode) {
                            LoadingMode.FULL_BASKETS -> item.completionStatus.copy(
                                fullBasketsCompleted = true,
                                fullBasketsScanned = scannedBaskets.size,
                                scannedBasketUids = item.completionStatus.scannedBasketUids +
                                        scannedBaskets.map { it.basket.uid }
                            )
                            LoadingMode.LOOSE_ITEMS -> item.completionStatus.copy(
                                looseItemsCompleted = true,
                                looseItemsScanned = scannedBaskets.sumOf { it.scannedQuantity },
                                scannedBasketUids = item.completionStatus.scannedBasketUids +
                                        scannedBaskets.map { it.basket.uid }
                            )
                        }
                        item.copy(completionStatus = newStatus)
                    } else {
                        item
                    }
                }

                // 檢查所有產品的完成狀態
                val allFullBasketsCompleted = updatedItems.all {
                    (it.fullTrolley == 0 && it.fullBaskets == 0) || it.completionStatus.fullBasketsCompleted
                }
                val allLooseItemsCompleted = updatedItems.all {
                    it.looseQuantity == 0 || it.completionStatus.looseItemsCompleted
                }

                // 更新路線整體完成狀態
                val newRouteStatus = route.completionStatus.copy(
                    fullBasketsCompleted = allFullBasketsCompleted,
                    looseItemsCompleted = allLooseItemsCompleted,
                    fullBasketsScannedCount = updatedItems.sumOf { it.completionStatus.fullBasketsScanned },
                    looseItemsScannedCount = updatedItems.sumOf { it.completionStatus.looseItemsScanned },
                    lastUpdated = System.currentTimeMillis()
                )

                // 如果完整籃子和散貨都完成，更新路線狀態為 COMPLETED
                val newLoadingStatus = if (allFullBasketsCompleted && allLooseItemsCompleted) {
                    LoadingStatus.COMPLETED
                } else {
                    LoadingStatus.IN_PROGRESS
                }

                Timber.d("📊 Route completion check:")
                Timber.d("  - All full baskets completed: $allFullBasketsCompleted")
                Timber.d("  - All loose items completed: $allLooseItemsCompleted")
                Timber.d("  - New route status: $newLoadingStatus")

                route.copy(
                    items = updatedItems,
                    completionStatus = newRouteStatus,
                    status = newLoadingStatus
                )
            }

            Timber.d("✅ Updated item completion status: route=$routeId, product=$productId, mode=$mode")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update item completion status")
            Result.failure(e)
        }
    }

    /**
     * 重置路線數據為默認值
     */
    suspend fun resetRoutesData(): Result<Unit> {
        return loadingRoutesDataStore.resetToDefault()
    }

    /**
     * 清空所有路線數據
     */
    suspend fun clearRoutesData(): Result<Unit> {
        return loadingRoutesDataStore.clearAll()
    }

    /**
     * 檢查並更新路線完成狀態（內部方法）
     */
    private suspend fun checkAndUpdateRouteCompletion(route: LoadingRoute) {
        val allFullBasketsCompleted = route.items.all {
            (it.fullTrolley == 0 && it.fullBaskets == 0) || it.completionStatus.fullBasketsCompleted
        }
        val allLooseItemsCompleted = route.items.all {
            it.looseQuantity == 0 || it.completionStatus.looseItemsCompleted
        }

        // 如果狀態需要更新
        val shouldBeCompleted = allFullBasketsCompleted && allLooseItemsCompleted
        if (shouldBeCompleted && route.status != LoadingStatus.COMPLETED) {
            loadingRoutesDataStore.updateRouteCompletionStatus(route.id) { existingRoute ->
                val newRouteStatus = existingRoute.completionStatus.copy(
                    fullBasketsCompleted = allFullBasketsCompleted,
                    looseItemsCompleted = allLooseItemsCompleted,
                    lastUpdated = System.currentTimeMillis()
                )

                Timber.d("📊 Auto-updating route status to COMPLETED:")
                Timber.d("  - Route: ${existingRoute.name}")
                Timber.d("  - All full baskets: $allFullBasketsCompleted")
                Timber.d("  - All loose items: $allLooseItemsCompleted")

                existingRoute.copy(
                    completionStatus = newRouteStatus,
                    status = LoadingStatus.COMPLETED
                )
            }
        }
    }

    /**
     * 根據 ID 獲取單個路線
     */
    suspend fun getRouteById(routeId: String, isOnline: Boolean): Result<LoadingRoute> =
        withContext(Dispatchers.IO) {
            try {
                if (isOnline) {
                    try {
                        val response = loadingApiService.getRouteDetail(routeId)
                        if (response.success && response.data != null) {
                            val route = response.data.toLoadingRoute()
                            loadingRoutesDataStore.updateRoute(route)
                            return@withContext Result.success(route)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "⚠️ Failed to fetch route from API")
                    }
                }

                // 從本地讀取
                val routeResult = loadingRoutesDataStore.getRouteById(routeId)

                // 檢查並更新路線完成狀態
                if (routeResult.isSuccess) {
                    val route = routeResult.getOrThrow()
                    checkAndUpdateRouteCompletion(route)

                    // 重新讀取更新後的路線
                    return@withContext loadingRoutesDataStore.getRouteById(routeId)
                }

                routeResult
            } catch (e: Exception) {
                Timber.e(e, "Failed to get route by id")
                Result.failure(e)
            }
        }

    /**
     * 重置路線完成狀態
     */
    suspend fun resetRouteCompletion(routeId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                loadingRoutesDataStore.updateRouteCompletionStatus(routeId) { route ->
                    route.copy(
                        status = LoadingStatus.PENDING,
                        items = route.items.map { item ->
                            item.copy(
                                completionStatus = ItemCompletionStatus(
                                    fullBasketsCompleted = false,
                                    looseItemsCompleted = false,
                                    fullBasketsScanned = 0,
                                    looseItemsScanned = 0,
                                    scannedBasketUids = emptyList()
                                )
                            )
                        },
                        completionStatus = RouteCompletionStatus(
                            fullBasketsCompleted = false,
                            looseItemsCompleted = false,
                            fullBasketsScannedCount = 0,
                            looseItemsScannedCount = 0,
                            lastUpdated = 0,
                            updatedBy = null
                        )
                    )
                }

                Timber.d("✅ Reset route completion: $routeId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to reset route completion")
                Result.failure(e)
            }
        }
    }

    /**
     * 更新路線狀態
     */
    suspend fun updateRouteStatus(routeId: String, status: LoadingStatus): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                loadingRoutesDataStore.updateRouteCompletionStatus(routeId) { route ->
                    route.copy(status = status)
                }

                Timber.d("✅ Updated route status: $routeId -> $status")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update route status")
                Result.failure(e)
            }
        }
    }

    /**
     * 重置單個產品的完成狀態
     */
    suspend fun resetItemCompletionStatus(
        routeId: String,
        productId: String,
        mode: LoadingMode
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                loadingRoutesDataStore.updateItemCompletionStatus(routeId, productId) { route ->
                    route.copy(
                        items = route.items.map { item ->
                            if (item.productId == productId) {
                                item.copy(
                                    completionStatus = when (mode) {
                                        LoadingMode.FULL_BASKETS -> item.completionStatus.copy(
                                            fullBasketsCompleted = false,
                                            fullBasketsScanned = 0,
                                            scannedBasketUids = item.completionStatus.scannedBasketUids.filter { uid ->
                                                // 移除完整籃子模式的 UID
                                                false // 這裡需要更複雜的邏輯來區分
                                            }
                                        )
                                        LoadingMode.LOOSE_ITEMS -> item.completionStatus.copy(
                                            looseItemsCompleted = false,
                                            looseItemsScanned = 0,
                                            scannedBasketUids = item.completionStatus.scannedBasketUids.filter { uid ->
                                                // 移除散貨模式的 UID
                                                false // 這裡需要更複雜的邏輯來區分
                                            }
                                        )
                                    }
                                )
                            } else {
                                item
                            }
                        },
                        // ✅ 更新路線整體狀態
                        status = LoadingStatus.IN_PROGRESS
                    )
                }

                Timber.d("✅ Reset item completion status: route=$routeId, product=$productId, mode=$mode")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to reset item completion status")
                Result.failure(e)
            }
        }
    }

    /**
     * 將籃子狀態重置為 IN_STOCK
     */
    suspend fun resetBasketsToInStock(uids: List<String>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                uids.forEach { uid ->
                    basketDao.getBasketByUid(uid)?.let { entity ->
                        basketDao.updateBasket(
                            entity.copy(
                                status = BasketStatus.IN_STOCK,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                }
                Timber.d("✅ Reset ${uids.size} baskets to IN_STOCK")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to reset baskets status")
                Result.failure(e)
            }
        }
}

/**
 * 擴展函數：將響應 DTO 轉換為領域模型
 */
private fun LoadingRouteResponse.toLoadingRoute(): LoadingRoute {
    return LoadingRoute(
        id = this.id,
        name = this.name,
        vehiclePlate = this.vehiclePlate,
        deliveryDate = this.deliveryDate,
        items = this.items.map { it.toLoadingItem() },
        totalQuantity = this.totalQuantity,
        totalBaskets = this.totalBaskets,
        status = LoadingStatus.valueOf(this.status)
    )
}

private fun LoadingItemResponse.toLoadingItem(): LoadingItem {
    return LoadingItem(
        productId = this.productId,
        productName = this.productName,
        totalQuantity = this.totalQuantity,
        fullTrolley = this.fullTrolley,
        fullBaskets = this.fullBaskets,
        looseQuantity = this.looseQuantity
    )
}