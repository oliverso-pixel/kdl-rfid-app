package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.api.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 籃子驗證結果（用於收貨）
 */
sealed class BasketValidationForReceivingResult {
    data class Valid(val basket: Basket) : BasketValidationForReceivingResult()
    data class NotRegistered(val uid: String) : BasketValidationForReceivingResult()
    data class InvalidStatus(val basket: Basket, val currentStatus: BasketStatus) : BasketValidationForReceivingResult()
    data class Error(val message: String) : BasketValidationForReceivingResult()
}

/**
 * 籃子驗證結果（用於盤點）
 */
sealed class BasketValidationForInventoryResult {
    data class Valid(val basket: Basket) : BasketValidationForInventoryResult()
    data class NotInWarehouse(val uid: String) : BasketValidationForInventoryResult()
    data class WrongWarehouse(val basket: Basket, val expectedWarehouse: String) : BasketValidationForInventoryResult()
    data class InvalidStatus(val basket: Basket) : BasketValidationForInventoryResult()
//    data class ExtraItem(val basket: Basket) : BasketValidationForInventoryResult()
    data class Error(val message: String) : BasketValidationForInventoryResult()
}

@Singleton
class WarehouseRepository @Inject constructor(
    private val apiService: ApiService,
    private val basketDao: BasketDao,
    private val pendingOperationDao: PendingOperationDao
) {

    // 1. 獲取倉庫列表 (API 接駁)
    suspend fun getWarehouses(): Result<List<Warehouse>> {
        return try {
            val response = apiService.getWarehouses()
            if (response.isSuccessful && response.body() != null) {
                val warehouses = response.body()!!.map { dto ->
                    Warehouse(
                        id = dto.id,
                        name = dto.name,
                        address = dto.location ?: "",
                        isActive = dto.isActive
                    )
                }
                Result.success(warehouses)
            } else {
                Timber.e("Fetch warehouses failed: ${response.code()}")
                Result.failure(Exception("獲取倉庫列表失敗01"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Fetch warehouses error")
            Result.failure(Exception("獲取倉庫列表失敗02"))
        }
    }

    /**
     * 驗證籃子是否在指定倉庫中（用於盤點）
     *
     * 修改邏輯：
     * 1. 如果籃子在正確的倉庫且狀態正常 → Valid
     * 2. 如果籃子在正確的倉庫但狀態異常 → ExtraItem（額外項）
     * 3. 如果籃子不在倉庫中 → NotInWarehouse
     * 4. 如果籃子在錯誤的倉庫 → WrongWarehouse
     */
    suspend fun validateBasketForInventory(
        uid: String,
        warehouseId: String,
        isOnline: Boolean
    ): BasketValidationForInventoryResult = withContext(Dispatchers.IO) {
        try {
            val entity = basketDao.getBasketByUid(uid)

            if (entity != null) {
                val basket = entity.toBasket()

                // 只檢查倉庫ID，不檢查狀態
                if (basket.warehouseId == warehouseId) {
                    Timber.d("✅ Basket is in warehouse: $uid (status=${basket.status})")
                    BasketValidationForInventoryResult.Valid(basket)
                } else if (basket.warehouseId.isNullOrEmpty()) {
                    // 籃子未分配倉庫，但實際在此倉庫中
                    Timber.w("⚠️ Basket not assigned to any warehouse: $uid")
                    BasketValidationForInventoryResult.Valid(basket)
                } else {
                    // 籃子屬於其他倉庫
                    Timber.w("⚠️ Basket belongs to different warehouse: $uid")
                    BasketValidationForInventoryResult.WrongWarehouse(basket, warehouseId)
                }
            } else {
                Timber.w("⚠️ Basket not found in database: $uid")
                BasketValidationForInventoryResult.NotInWarehouse(uid)
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error validating basket for inventory: $uid")
            BasketValidationForInventoryResult.Error(e.message ?: "驗證失敗")
        }
    }

    /**
     * 獲取指定倉庫的所有籃子（按產品分類）
     */
//    suspend fun getWarehouseBasketsByWarehouse(warehouseId: String): Result<List<Basket>> =
//        withContext(Dispatchers.IO) {
//            try {
//                val allBaskets = basketDao.getAllBaskets().first()
//
//                // 調試：打印所有籃子的狀態
//                Timber.d("📦 ========== Warehouse Baskets Debug ==========")
//                Timber.d("Total baskets in DB: ${allBaskets.size}")
//
//                allBaskets.forEach { entity ->
//                    Timber.d("Basket ${entity.uid.takeLast(8)}: warehouse=${entity.warehouseId}, status=${entity.status}")
//                }
//
//                val warehouseBaskets = allBaskets
//                    .filter { entity ->
//                        // 修改過濾條件：只檢查 warehouseId
//                        val matchWarehouse = entity.warehouseId == warehouseId
//                        val isValidStatus = entity.status == BasketStatus.RECEIVED ||
//                                entity.status == BasketStatus.IN_STOCK
//
//                        Timber.d("Basket ${entity.uid.takeLast(8)}: matchWarehouse=$matchWarehouse, isValidStatus=$isValidStatus")
//
//                        // 臨時調試：先只檢查 warehouse，忽略狀態
//                        matchWarehouse
//                    }
//                    .map { it.toBasket() }
//
//                Timber.d("📦 Found ${warehouseBaskets.size} baskets in warehouse $warehouseId")
//
//                // 打印每個籃子的詳細信息
//                warehouseBaskets.forEach { basket ->
//                    Timber.d("  - ${basket.uid.takeLast(8)}: ${basket.product?.name}, status=${basket.status}, qty=${basket.quantity}")
//                }
//
//                Result.success(warehouseBaskets)
//            } catch (e: Exception) {
//                Timber.e(e, "Failed to get warehouse baskets")
//                Result.failure(e)
//            }
//        }
    suspend fun getWarehouseBasketsByWarehouse(warehouseId: String): Result<List<Basket>> =
        withContext(Dispatchers.IO) {
            try {
                // Online: 呼叫 API
                val response = apiService.getWarehouseBaskets(warehouseId)

                if (response.isSuccessful && response.body() != null) {
                    val dtos = response.body()!!

                    // 使用擴充函數轉換 DTO -> Domain Model
                    // 注意：toBasket() 已經包含了 JSON String 的解析邏輯
                    val baskets = dtos.map { it.toBasket() }

                    // 可選：同步到本地資料庫 (視需求而定，盤點通常需要最新數據)
                    // basketDao.insertBaskets(baskets.map { it.toEntity() })

                    Timber.d("✅ Loaded ${baskets.size} baskets from warehouse $warehouseId (API)")
                    Result.success(baskets)
                } else {
                    // API 失敗，回退到本地資料庫 (Offline Support)
                    Timber.w("⚠️ API failed: ${response.code()}, falling back to local DB")
                    val localEntities = basketDao.getBasketsByWarehouse(
                        warehouseId = warehouseId,
                        statuses = BasketStatus.IN_STOCK // 假設盤點只看在庫
                    )
                    Result.success(localEntities.map { it.toBasket() })
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get warehouse baskets")
                Result.failure(e)
            }
        }

    // 2. 獲取產品列表
    suspend fun getProducts(): Result<List<Product>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getProducts(isActive = true)

            if (response.isSuccessful && response.body() != null) {
                val listResponse = response.body()!!
                // 重用 DailyProductResponse 的擴充函數 toProduct()
                val products = listResponse.items.map { it.toProduct() }

                Timber.d("✅ Loaded ${products.size} products from API")
                Result.success(products)
            } else {
                // API 失敗，回退到 Mock 數據 (防止空列表導致無法操作)
                Timber.w("⚠️ API failed")
                Result.success(mockProductionOrders().map {
                    Product(it.productId, it.barcodeId, it.qrcodeId, it.productName, it.maxBasketCapacity, it.imageUrl)
                })
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get products")
            Result.failure(e)
        }
    }

    /**
     * 更新籃子信息（用於盤點額外項）
     */
    suspend fun updateBasketInfo(
        uid: String,
        productId: String,
        warehouseId: String,
        quantity: Int,
        isOnline: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("📦 Updating basket info: $uid -> Product: $productId, Warehouse: $warehouseId, Qty: $quantity")

            if (isOnline) {
                // 在線模式：提交到 API
                Timber.d("🌐 Online: Submitting to API")

                // TODO: 真實 API 調用
                // val request = UpdateBasketRequest(
                //     uid = uid,
                //     productId = productId,
                //     warehouseId = warehouseId,
                //     quantity = quantity,
                //     status = BasketStatus.IN_STOCK
                // )
                // val response = apiService.updateBasket(request)
                // if (!response.success) {
                //     return@withContext Result.failure(Exception(response.message ?: "更新失敗"))
                // }

                // 模擬 API 成功
                delay(500)

                // 更新本地數據庫
                updateBasketLocally(uid, productId, warehouseId, quantity)

                Timber.d("✅ Basket updated successfully (online mode)")
                Result.success(Unit)
            } else {
                // 離線模式：保存待同步 + 更新本地
                Timber.d("📱 Offline: Saving to pending operations")

                // TODO: 保存待同步操作
                // val operation = PendingOperationEntity(
                //     operationType = OperationType.UPDATE_BASKET,
                //     uid = uid,
                //     payload = Json.encodeToString(
                //         UpdateBasketRequest(
                //             uid = uid,
                //             productId = productId,
                //             warehouseId = warehouseId,
                //             quantity = quantity,
                //             status = BasketStatus.IN_STOCK
                //         )
                //     ),
                //     timestamp = System.currentTimeMillis()
                // )
                // pendingOperationDao.insertOperation(operation)

                // 更新本地數據庫
                updateBasketLocally(uid, productId, warehouseId, quantity)

                Timber.d("✅ Basket updated successfully (offline mode)")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update basket")
            Result.failure(e)
        }
    }

    /**
     * 本地更新籃子信息
     */
    private suspend fun updateBasketLocally(
        uid: String,
        productId: String,
        warehouseId: String,
        quantity: Int
    ) {
        val entity = basketDao.getBasketByUid(uid)

        if (entity != null) {
            // 獲取產品信息（從 mock 數據）
            val product = mockProductionOrders().find { it.productId == productId }

            val productJson = product?.let {
                Json.encodeToString(
                    Product(
                        itemcode = it.productId,
                        barcodeId = it.barcodeId,
                        qrcodeId = it.qrcodeId,
                        name = it.productName,
                        maxBasketCapacity = it.maxBasketCapacity,
                        imageUrl = it.imageUrl
                    )
                )
            }

            val updatedEntity = entity.copy(
                productId = productId,
                productName = product?.productName,
                productJson = productJson,
                warehouseId = warehouseId,
                quantity = quantity,
                status = BasketStatus.IN_STOCK,
                lastUpdated = System.currentTimeMillis()
            )

            basketDao.updateBasket(updatedEntity)

            Timber.d("💾 Basket updated locally: $uid -> Product: $productId, Qty: $quantity")
        } else {
            Timber.w("⚠️ Basket not found in local DB: $uid")
        }
    }

    // ==================== Shipping ====================
    /**
     * 根據路線 ID 獲取籃子
     */
    suspend fun getBasketsByRouteId(routeId: String): Result<List<Basket>> =
        withContext(Dispatchers.IO) {
            try {
                val allBaskets = basketDao.getAllBaskets().first()

                val basketEntities = allBaskets.filter { entity ->
                    entity.warehouseId == routeId
                }

                val baskets = basketEntities.map { it.toBasket() }

                Timber.d("📦 Loaded ${baskets.size} baskets from route $routeId (all statuses)")

                // 打印每個狀態的籃子數量
                val statusGroups = baskets.groupBy { it.status }
                statusGroups.forEach { (status, statusBaskets) ->
                    Timber.d("  - ${status}: ${statusBaskets.size} baskets")
                }

                Result.success(baskets)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load route baskets")
                Result.failure(e)
            }
        }
}