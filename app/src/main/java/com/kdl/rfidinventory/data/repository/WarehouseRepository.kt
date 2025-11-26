package com.kdl.rfidinventory.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.ApiService
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

                // 檢查狀態
                if (basket.status != BasketStatus.RECEIVED && basket.status != BasketStatus.IN_STOCK) {
                    return@withContext BasketValidationForInventoryResult.InvalidStatus(basket)
                }

                // 檢查倉庫
                if (basket.warehouseId != warehouseId) {
                    return@withContext BasketValidationForInventoryResult.WrongWarehouse(
                        basket,
                        warehouseId
                    )
                }

                Timber.d("✅ Basket is in warehouse: $uid")
                BasketValidationForInventoryResult.Valid(basket)
            } else {
                Timber.w("⚠️ Basket not in warehouse: $uid")
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

                // 🔍 调试：打印所有篮子的状态
                Timber.d("📦 ========== Warehouse Baskets Debug ==========")
                Timber.d("Total baskets in DB: ${allBaskets.size}")

                allBaskets.forEach { entity ->
                    Timber.d("Basket ${entity.uid.takeLast(8)}: warehouse=${entity.warehouseId}, status=${entity.status}")
                }

                val warehouseBaskets = allBaskets
                    .filter { entity ->
                        // 修改过滤条件：只检查 warehouseId
                        val matchWarehouse = entity.warehouseId == warehouseId
                        val isValidStatus = entity.status == BasketStatus.RECEIVED ||
                                entity.status == BasketStatus.IN_STOCK

                        Timber.d("Basket ${entity.uid.takeLast(8)}: matchWarehouse=$matchWarehouse, isValidStatus=$isValidStatus")

                        // 临时调试：先只检查 warehouse，忽略状态
                        matchWarehouse
                    }
                    .map { it.toBasket() }

                Timber.d("📦 Found ${warehouseBaskets.size} baskets in warehouse $warehouseId")

                // 🔍 打印每个篮子的详细信息
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
    suspend fun getWarehouses(): Result<List<Warehouse>> = withContext(Dispatchers.IO) {
        try {
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
                // ⭐ 在線模式：提交到 API
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
                // ⭐ 離線模式：保存待同步 + 更新本地
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
                // ⭐ 保留原有的产品和批次信息
                val updatedEntity = entity.copy(
                    status = BasketStatus.RECEIVED,
                    warehouseId = warehouseId,
                    quantity = item.quantity,
                    lastUpdated = System.currentTimeMillis()
                )
                basketDao.updateBasket(updatedEntity)
                Timber.d("💾 Updated basket to RECEIVED: ${item.uid} -> Warehouse: $warehouseId, Quantity: ${item.quantity}, Product: ${entity.productID}")
            } else {
                Timber.w("⚠️ Basket not found in local DB: ${item.uid}")
            }
        }
    }

    suspend fun getWarehouseBaskets(): Result<List<Basket>> = withContext(Dispatchers.IO) {
        try {
            val allBaskets = basketDao.getAllBaskets().first()
            val warehouseBaskets = allBaskets.filter {
                it.status == BasketStatus.RECEIVED || it.status == BasketStatus.IN_STOCK
            }
            Result.success(warehouseBaskets.map { it.toBasket() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

//    suspend fun getBasketByUid(uid: String): Result<Basket> = withContext(Dispatchers.IO) {
//        try {
//            // 先從本地數據庫查詢
//            val entity = basketDao.getBasketByUid(uid)
//            if (entity != null) {
//                return@withContext Result.success(entity.toBasket())
//            }
//
//            // 如果本地沒有，使用 Mock 數據
//            delay(500) // 模擬網絡延遲
//            val mockBasket = generateMockBasket(uid)
//            // 儲存到本地數據庫
//            basketDao.insertBasket(mockBasket.toEntity())
//            Result.success(mockBasket)
//        } catch (e: Exception) {
//            Result.failure(e)
//        }
//    }

    private fun generateMockBasket(uid: String): Basket {
        // 根據 UID 生成不同狀態的 Mock 數據
        val random = uid.hashCode()
        val status = when (random % 4) {
            0 -> BasketStatus.IN_PRODUCTION  // 50% 機率是生產中
            1 -> BasketStatus.IN_PRODUCTION
            2 -> BasketStatus.UNASSIGNED     // 25% 未配置
            else -> BasketStatus.IN_STOCK    // 25% 已在庫
        }

        return Basket(
            uid = uid,
            product = if (status != BasketStatus.UNASSIGNED) {
                Product(
                    id = "P001",
                    name = "大紅",
                    maxBasketCapacity = 60,
                    imageUrl = "https://homedelivery.kowloondairy.com/media/catalog/product/k/d/kd-946_800x800_freshmilk_front.png"
                )
            } else null,
            batch = if (status != BasketStatus.UNASSIGNED) {
                Batch(
                    id = "BATCH-2025-001",
                    productId = "P001",
                    totalQuantity = 1000,
                    remainingQuantity = 500,
                    productionDate = "2025-11-18"
                )
            } else null,
            warehouseId = null,
            quantity = if (status != BasketStatus.UNASSIGNED) 60 else 0,
            status = status,
            productionDate = if (status != BasketStatus.UNASSIGNED) "2025-11-18" else null,
            expireDate = null,
            lastUpdated = System.currentTimeMillis(),
            updateBy = null
        )
    }

    private fun mockWarehouses() = listOf(
        Warehouse(id = "WH-001", name = "GF", address = "", isActive = true),
        Warehouse(id = "WH-002", name = "1F-A", address = "", isActive = true),
        Warehouse(id = "WH-003", name = "1F-B", address = "", isActive = true),
        Warehouse(id = "WH-004", name = "3F", address = "", isActive = false)
    )
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

data class Warehouse(
    val id: String,
    val name: String,
    val address: String,
    val isActive: Boolean
)

data class ReceivingData(
    val uid: String,
    val warehouseId: String,
    val productId: String,
    val batchId: String,
    val quantity: Int,
    val timestamp: String
)

data class ReceivingItem(
    val uid: String,
    val quantity: Int
)