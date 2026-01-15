package com.kdl.rfidinventory.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.api.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.ReceivingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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

    /**
     * 驗證籃子是否可用於收貨
     * 只有「生產中」(IN_PRODUCTION) 狀態的籃子才能收貨
     */
    suspend fun validateBasketForReceiving(uid: String, isOnline: Boolean): BasketValidationForReceivingResult =
        withContext(Dispatchers.IO) {
            try {
                if (isOnline) {
                    // 在線：從服務器檢查
                    Timber.d("🌐 Online: Validating basket for receiving from server: $uid")

                    // TODO: 替換為真實 API 調用
                    // val response = apiService.scanBasket(ScanRequest(uid))

                    // 暫時使用本地數據庫
                    val entity = basketDao.getBasketByUid(uid)

                    if (entity != null) {
                        val basket = entity.toBasket()

                        when (basket.status) {
                            BasketStatus.IN_PRODUCTION -> {
                                Timber.d("✅ Basket is valid for receiving: $uid (IN_PRODUCTION)")
                                BasketValidationForReceivingResult.Valid(basket)
                            }
                            else -> {
                                Timber.w("⚠️ Basket has invalid status for receiving: $uid (${basket.status})")
                                BasketValidationForReceivingResult.InvalidStatus(basket, basket.status)
                            }
                        }
                    } else {
                        Timber.w("⚠️ Basket not registered: $uid")
                        BasketValidationForReceivingResult.NotRegistered(uid)
                    }
                } else {
                    // 離線：從本地數據庫檢查
                    Timber.d("📱 Offline: Validating basket for receiving from local database: $uid")

                    val entity = basketDao.getBasketByUid(uid)

                    if (entity != null) {
                        val basket = entity.toBasket()

                        when (basket.status) {
                            BasketStatus.IN_PRODUCTION -> {
                                Timber.d("✅ Basket is valid for receiving (local): $uid (IN_PRODUCTION)")
                                BasketValidationForReceivingResult.Valid(basket)
                            }
                            else -> {
                                Timber.w("⚠️ Basket has invalid status for receiving (local): $uid (${basket.status})")
                                BasketValidationForReceivingResult.InvalidStatus(basket, basket.status)
                            }
                        }
                    } else {
                        Timber.w("⚠️ Basket not registered locally: $uid")
                        BasketValidationForReceivingResult.NotRegistered(uid)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Error validating basket for receiving: $uid")
                BasketValidationForReceivingResult.Error(e.message ?: "驗證失敗")
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
    suspend fun getWarehouseBasketsByWarehouse(warehouseId: String): Result<List<Basket>> =
        withContext(Dispatchers.IO) {
            try {
                val allBaskets = basketDao.getAllBaskets().first()

                // 調試：打印所有籃子的狀態
                Timber.d("📦 ========== Warehouse Baskets Debug ==========")
                Timber.d("Total baskets in DB: ${allBaskets.size}")

                allBaskets.forEach { entity ->
                    Timber.d("Basket ${entity.uid.takeLast(8)}: warehouse=${entity.warehouseId}, status=${entity.status}")
                }

                val warehouseBaskets = allBaskets
                    .filter { entity ->
                        // 修改過濾條件：只檢查 warehouseId
                        val matchWarehouse = entity.warehouseId == warehouseId
                        val isValidStatus = entity.status == BasketStatus.RECEIVED ||
                                entity.status == BasketStatus.IN_STOCK

                        Timber.d("Basket ${entity.uid.takeLast(8)}: matchWarehouse=$matchWarehouse, isValidStatus=$isValidStatus")

                        // 臨時調試：先只檢查 warehouse，忽略狀態
                        matchWarehouse
                    }
                    .map { it.toBasket() }

                Timber.d("📦 Found ${warehouseBaskets.size} baskets in warehouse $warehouseId")

                // 打印每個籃子的詳細信息
                warehouseBaskets.forEach { basket ->
                    Timber.d("  - ${basket.uid.takeLast(8)}: ${basket.product?.name}, status=${basket.status}, qty=${basket.quantity}")
                }

                Result.success(warehouseBaskets)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get warehouse baskets")
                Result.failure(e)
            }
        }

    // 獲取倉庫列表
    suspend fun getWarehouses(): Result<List<Warehouse>> {
        return try {
            // TODO: 替換為真實 API 調用
            // val response = apiService.getWarehouses()
            // if (response.success && response.data != null) {
            //     Result.success(response.data.map { it.toWarehouse() })
            // } else {
            //     Result.failure(Exception(response.message ?: "獲取倉庫列表失敗"))
            // }

            delay(500) // 模擬網絡延遲
            Result.success(mockWarehouses())
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch warehouses")
            delay(500)
            Result.success(mockWarehouses())
        }
    }

    /**
     *  收貨籃子
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun receiveBaskets(
        items: List<ReceivingItem>,
        warehouseId: String,
        isOnline: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

            Timber.d("📦 ========== 收貨提交數據 ==========")
            Timber.d("倉庫ID: $warehouseId")
            Timber.d("籃子數量: ${items.size}")
            Timber.d("在線狀態: $isOnline")

            if (isOnline) {
                // 在線模式：提交到 API
                Timber.d("🌐 Online: Submitting to API")

                // TODO: 真實 API 調用
                // items.forEach { item ->
                //     val request = ReceivingRequest(
                //         uid = item.uid,
                //         warehouseId = warehouseId,
                //         quantity = item.quantity,
                //         timestamp = timestamp
                //     )
                //     val response = apiService.receiveBasket(request)
                //     if (!response.success) {
                //         return@withContext Result.failure(Exception(response.message ?: "收貨失敗"))
                //     }
                // }

                // 模擬 API 成功
                delay(500)

                // 更新本地數據庫
                updateBasketsToReceived(items, warehouseId)

                Timber.d("✅ 收貨成功（在線模式）")
                Result.success(Unit)
            } else {
                // 離線模式：保存待同步 + 更新本地
                Timber.d("📱 Offline: Saving to pending operations")

                // 保存待同步操作（暫時跳過）
                // items.forEach { item ->
                //     val operation = PendingOperationEntity(
                //         operationType = OperationType.WAREHOUSE_RECEIVE,
                //         uid = item.uid,
                //         payload = Json.encodeToString(
                //             ReceivingRequest(
                //                 uid = item.uid,
                //                 warehouseId = warehouseId,
                //                 quantity = item.quantity,
                //                 timestamp = timestamp
                //             )
                //         ),
                //         timestamp = System.currentTimeMillis()
                //     )
                //     pendingOperationDao.insertOperation(operation)
                // }

                // 更新本地數據庫
                updateBasketsToReceived(items, warehouseId)

                Timber.d("✅ 收貨成功（離線模式）")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "收貨失敗")
            Result.failure(e)
        }
    }

    /**
     * 更新籃子為已收貨狀態
     */
    private suspend fun updateBasketsToReceived(items: List<ReceivingItem>, warehouseId: String) {
        items.forEach { item ->
            val entity = basketDao.getBasketByUid(item.uid)
            if (entity != null) {
                val updatedEntity = entity.copy(
                    status = BasketStatus.IN_STOCK                                                                                                                                                           ,
                    warehouseId = warehouseId,
                    quantity = item.quantity,
                    lastUpdated = System.currentTimeMillis()
                )
                basketDao.updateBasket(updatedEntity)
                Timber.d("💾 Updated basket to RECEIVED: ${item.uid} -> Warehouse: $warehouseId, Quantity: ${item.quantity}, Product: ${entity.productId}")
            } else {
                Timber.w("⚠️ Basket not found in local DB: ${item.uid}")
            }
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
                        id = it.productId,
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

/**
 * 計算到期天數
 */
@RequiresApi(Build.VERSION_CODES.O)
fun Basket.getDaysUntilExpiry(): Long? {
    val expireDate = this.expireDate ?: return null
    return try {
        val expire = LocalDate.parse(expireDate)
        val today = LocalDate.now()
        ChronoUnit.DAYS.between(today, expire)
    } catch (e: Exception) {
        null
    }
}

data class ReceivingItem(
    val uid: String,
    val quantity: Int
)