package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.api.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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

    // 1. 獲取倉庫列表
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
//                Result.success(mockProductionOrders().map {
//                    Product(it.productId, it.barcodeId, it.qrcodeId, it.productName, 1, it.maxBasketCapacity, it.imageUrl)
//                })
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get products")
            Result.failure(e)
        }
    }

    /**
     * 根據產品和過期日期獲取 Batch 列表
     */
    suspend fun getBatchesByProductAndExpiry(
        itemcode: String,
        expireDate: String,
        isOnline: Boolean
    ): Result<List<Batch>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getBatchesByProductAndExpiry(itemcode, expireDate)
            if (response.isSuccessful && response.body() != null) {
                val batches = response.body()!!.map { it.toBatch() }
                Result.success(batches)
            } else {
//                Timber.w("⚠️ Offline mode: Cannot fetch batches")
//                Result.success(emptyList())
                Result.failure(Exception("獲取批次失敗: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch batches")
            Result.failure(e)
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