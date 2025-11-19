package com.kdl.rfidinventory.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.ReceivingRequest
import com.kdl.rfidinventory.data.remote.dto.request.ShippingRequest
//import com.kdl.rfidinventory.data.remote.dto.response.toRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarehouseRepository @Inject constructor(
    private val apiService: ApiService,
    private val basketDao: BasketDao,
    private val pendingOperationDao: PendingOperationDao
) {

    // ⭐ 新增：獲取倉庫列表
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

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun receiveBaskets(uids: List<String>, isOnline: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

            if (isOnline) {
                uids.forEach { uid ->
                    val request = ReceivingRequest(uid, "default-route", timestamp)
                    val response = apiService.receiveBasket(request)
                    if (!response.success) {
                        return@withContext Result.failure(Exception(response.message ?: "收貨失敗"))
                    }
                }

                uids.forEach { uid ->
                    val entity = basketDao.getBasketByUid(uid)
                    entity?.let {
                        basketDao.updateBasket(
                            it.copy(
                                status = BasketStatus.RECEIVED,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                }
                Result.success(Unit)
            } else {
                uids.forEach { uid ->
                    val operation = PendingOperationEntity(
                        operationType = OperationType.WAREHOUSE_RECEIVE,
                        uid = uid,
                        payload = Json.encodeToString(ReceivingRequest(uid, "default-route", timestamp)),
                        timestamp = System.currentTimeMillis()
                    )
                    pendingOperationDao.insertOperation(operation)

                    val entity = basketDao.getBasketByUid(uid)
                    entity?.let {
                        basketDao.updateBasket(
                            entity.copy(
                                status = BasketStatus.RECEIVED,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

//    @RequiresApi(Build.VERSION_CODES.O)
//    suspend fun receiveBaskets(
//        uids: List<String>,
//        warehouseId: String,  // ⭐ 新增參數
//        isOnline: Boolean
//    ): Result<Unit> = withContext(Dispatchers.IO) {
//        try {
//            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
//
//            // ⭐ 準備提交的數據
//            val receivingData = uids.map { uid ->
//                val basket = basketDao.getBasketByUid(uid)
//                ReceivingData(
//                    uid = uid,
//                    warehouseId = warehouseId,
//                    productId = basket?.productID ?: "UNKNOWN",
//                    batchId = basket?.batchId ?: "UNKNOWN",
//                    quantity = basket?.quantity ?: 0,
//                    timestamp = timestamp
//                )
//            }
//
//            // ⭐ 輸出提交數據到 Log
//            Timber.d("📦 ========== 收貨提交數據 ==========")
//            Timber.d("倉庫ID: $warehouseId")
//            Timber.d("籃子數量: ${receivingData.size}")
//            Timber.d("總數量: ${receivingData.sumOf { it.quantity }}")
//            Timber.d("明細:")
//            receivingData.forEachIndexed { index, data ->
//                Timber.d("  [$index] UID: ${data.uid.takeLast(8)} | 產品: ${data.productId} | 批次: ${data.batchId} | 數量: ${data.quantity}")
//            }
//            Timber.d("=====================================")
//
//            // ⭐ 模擬成功提交
//            delay(1000) // 模擬網絡延遲
//
//            if (isOnline) {
//                // TODO: 真實 API 調用
//                // uids.forEach { uid ->
//                //     val request = ReceivingRequest(uid, warehouseId, timestamp)
//                //     val response = apiService.receiveBasket(request)
//                //     if (!response.success) {
//                //         return@withContext Result.failure(Exception(response.message ?: "收貨失敗"))
//                //     }
//                // }
//
//                // ⭐ 更新本地數據庫狀態
//                uids.forEach { uid ->
//                    val entity = basketDao.getBasketByUid(uid)
//                    entity?.let {
//                        basketDao.updateBasket(
//                            it.copy(
//                                status = BasketStatus.RECEIVED,
//                                warehouseId = warehouseId,  // 更新倉庫ID
//                                lastUpdated = System.currentTimeMillis()
//                            )
//                        )
//                    }
//                }
//                Timber.d("✅ 收貨成功（在線模式）")
//                Result.success(Unit)
//            } else {
//                // 離線模式：保存待同步操作
//                uids.forEach { uid ->
//                    val operation = PendingOperationEntity(
//                        operationType = OperationType.WAREHOUSE_RECEIVE,
//                        uid = uid,
//                        payload = Json.encodeToString(ReceivingRequest(uid, warehouseId, timestamp)),
//                        timestamp = System.currentTimeMillis()
//                    )
//                    pendingOperationDao.insertOperation(operation)
//
//                    val entity = basketDao.getBasketByUid(uid)
//                    entity?.let {
//                        basketDao.updateBasket(
//                            entity.copy(
//                                status = BasketStatus.RECEIVED,
//                                warehouseId = warehouseId,
//                                lastUpdated = System.currentTimeMillis()
//                            )
//                        )
//                    }
//                }
//                Timber.d("✅ 收貨成功（離線模式，已加入待同步隊列）")
//                Result.success(Unit)
//            }
//        } catch (e: Exception) {
//            Timber.e(e, "收貨失敗")
//            Result.failure(e)
//        }
//    }

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

    suspend fun getBasketByUid(uid: String): Result<Basket> = withContext(Dispatchers.IO) {
        try {
            // 先從本地數據庫查詢
            val entity = basketDao.getBasketByUid(uid)
            if (entity != null) {
                return@withContext Result.success(entity.toBasket())
            }

            // 如果本地沒有，使用 Mock 數據
            delay(500) // 模擬網絡延遲
            val mockBasket = generateMockBasket(uid)
            // 儲存到本地數據庫
            basketDao.insertBasket(mockBasket.toEntity())
            Result.success(mockBasket)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
            quantity = if (status != BasketStatus.UNASSIGNED) 60 else 0,
            status = status,
            productionDate = if (status != BasketStatus.UNASSIGNED) "2025-11-18" else null,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun mockWarehouses() = listOf(
        Warehouse(id = "WH-001", name = "GF", address = "", isActive = true),
        Warehouse(id = "WH-002", name = "1F-A", address = "", isActive = true),
        Warehouse(id = "WH-003", name = "1F-B", address = "", isActive = true),
        Warehouse(id = "WH-004", name = "3F", address = "", isActive = false)
    )

    private fun mockRoutes() = listOf(
        Route("R1", "路線 A", "倉庫 A", "true"),
        Route("R2", "路線 B", "倉庫 B", "true"),
        Route("R3", "路線 C", "倉庫 C", "false")
    )
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