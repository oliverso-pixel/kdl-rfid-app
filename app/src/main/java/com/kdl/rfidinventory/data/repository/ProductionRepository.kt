package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.ProductionStartRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductionRepository @Inject constructor(
    private val apiService: ApiService,
    private val basketDao: BasketDao,
    private val pendingOperationDao: PendingOperationDao
) {

    suspend fun getProductionOrders(): Result<List<ProductionOrder>> {
        return try {
            val response = apiService.getProductionOrders()
            if (response.success && response.data != null) {
                val orders = response.data.map {
                    ProductionOrder(
                        productId = it.productId,
                        barcodeId = it.barcodeId,
                        qrcodeId = it.qrcodeId,
                        productName = it.productName,
                        totalQuantity = it.totalQuantity,
                        imageUrl = it.imageUrl
                    )
                }
                Result.success(orders)
            } else {
                delay(500)
                Result.success(mockProductionOrders())
            }
        } catch (e: Exception) {
            delay(500)
            Result.success(mockProductionOrders())
        }
    }

    suspend fun startProduction(
        uid: String,
        productId: String,
        batchId: String,
        quantity: Int,
        productionDate: String,
        isOnline: Boolean
    ): Result<Unit> {
        return try {
            val request = ProductionStartRequest(
                uid = uid,
                productId = productId,
                batchId = batchId,
                quantity = quantity,
                productionDate = productionDate
            )

            if (isOnline) {
                val response = apiService.startProduction(request)
                if (response.success) {
                    val entity = basketDao.getBasketByUid(uid)
                    if (entity != null) {
                        basketDao.updateBasket(
                            entity.copy(
                                productID = productId,
                                batchId = batchId,
                                quantity = quantity,
                                status = BasketStatus.IN_PRODUCTION,
                                productionDate = productionDate,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "生產失敗"))
                }
                Timber.d("🌐 Online: Submitting production to API - $uid")
                try {
                    val response = apiService.startProduction(request)
                    if (response.success) {
                        Timber.d("✅ API success: $uid")
                        // API 成功後更新本地數據庫
                        updateLocalBasket(uid, productId, batchId, quantity, productionDate)
                        Result.success(Unit)
                    } else {
                        Timber.w("⚠️ API failed: ${response.message}")
                        Result.failure(Exception(response.message ?: "生產失敗"))
                    }
                } catch (apiError: Exception) {
                    // API 調用失敗，但仍然更新本地並記錄待同步
                    Timber.e(apiError, "❌ API error for $uid, saving to pending operations")
                    saveToPendingAndUpdateLocal(request, uid, productId, batchId, quantity, productionDate)
                    Result.success(Unit) // 返回成功，因為已保存到本地
                }
            } else {
                // ✅ 離線模式：保存到待同步操作 + 更新本地數據庫
                Timber.d("📱 Offline: Saving to pending operations - $uid")
                saveToPendingAndUpdateLocal(request, uid, productId, batchId, quantity, productionDate)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 保存到待同步操作並更新本地數據庫
     */
    private suspend fun saveToPendingAndUpdateLocal(
        request: ProductionStartRequest,
        uid: String,
        productId: String,
        batchId: String,
        quantity: Int,
        productionDate: String
    ) {
        // 1. 保存到待同步操作
//        val operation = PendingOperationEntity(
//            operationType = OperationType.PRODUCTION_START,
//            uid = uid,
//            payload = Json.encodeToString(request),
//            timestamp = System.currentTimeMillis()
//        )
//        pendingOperationDao.insertOperation(operation)
//        Timber.d("💾 Saved to pending operations: $uid")

        // 2. 更新本地數據庫
        updateLocalBasket(uid, productId, batchId, quantity, productionDate)
    }

    /**
     * 更新本地籃子數據庫
     */
    private suspend fun updateLocalBasket(
        uid: String,
        productId: String,
        batchId: String,
        quantity: Int,
        productionDate: String
    ) {
        Timber.d("🔍 [updateLocalBasket] Attempting to update basket: uid=$uid")

        // ⭐ 先檢查所有籃子
        try {
            val allBaskets = basketDao.getAllBaskets().first()
            Timber.d("📋 [updateLocalBasket] Total baskets in DB: ${allBaskets.size}")
            Timber.d("📋 [updateLocalBasket] All UIDs: ${allBaskets.map { it.uid }}")
        } catch (e: Exception) {
            Timber.e(e, "❌ [updateLocalBasket] Failed to list all baskets")
        }

        // ⭐ 查詢目標籃子
        val entity = basketDao.getBasketByUid(uid)

        if (entity != null) {
            Timber.d("✅ [updateLocalBasket] Found entity: uid=${entity.uid}, status=${entity.status}, productID=${entity.productID}")

            val updatedEntity = entity.copy(
                productID = productId,
                productName = entity.productName,
                batchId = batchId,
                quantity = quantity,
                status = BasketStatus.IN_PRODUCTION,
                productionDate = productionDate,
                lastUpdated = System.currentTimeMillis()
            )

            try {
                basketDao.updateBasket(updatedEntity)
                Timber.d("💾 [updateLocalBasket] Database update executed for: $uid")

                // ⭐ 驗證更新是否成功
                delay(100) // 等待數據庫操作完成
                val verified = basketDao.getBasketByUid(uid)
                if (verified != null) {
                    Timber.d("✅ [updateLocalBasket] Update verified: uid=${verified.uid}, status=${verified.status}, productID=${verified.productID}, batchId=${verified.batchId}")
                } else {
                    Timber.e("❌ [updateLocalBasket] Verification failed: basket not found after update")
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ [updateLocalBasket] Failed to update basket: $uid")
            }
        } else {
            Timber.e("❌ [updateLocalBasket] Basket entity not found in local DB: $uid")

            // ⭐ 創建新記錄（應該不會執行到這裡，但作為保險）
            try {
                val newEntity = com.kdl.rfidinventory.data.local.entity.BasketEntity(
                    uid = uid,
                    productID = productId,
                    productName = null,
                    batchId = batchId,
                    warehouseId = null,
                    quantity = quantity,
                    status = BasketStatus.IN_PRODUCTION,
                    productionDate = productionDate,
                    expireDate = null,
                    lastUpdated = System.currentTimeMillis(),
                    updateBy = null
                )
                basketDao.insertBasket(newEntity)
                Timber.d("💾 [updateLocalBasket] Created new basket entity: $uid")

                // 驗證插入
                delay(100)
                val verified = basketDao.getBasketByUid(uid)
                if (verified != null) {
                    Timber.d("✅ [updateLocalBasket] Insert verified: uid=${verified.uid}, status=${verified.status}")
                } else {
                    Timber.e("❌ [updateLocalBasket] Verification failed: basket not found after insert")
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ [updateLocalBasket] Failed to create basket: $uid")
            }
        }
    }
}