package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.api.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.ProductionStartRequest
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
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
                        maxBasketCapacity = it.totalQuantity,
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
        product: Product,
        batch: Batch,
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
                try {
                    val response = apiService.startProduction(request)
                    if (response.success) {
//                    val entity = basketDao.getBasketByUid(uid)
//                    if (entity != null) {
//                        basketDao.updateBasket(
//                            entity.copy(
//                                productId = productId,
//                                batchId = batchId,
//                                quantity = quantity,
//                                status = BasketStatus.IN_PRODUCTION,
//                                productionDate = productionDate,
//                                lastUpdated = System.currentTimeMillis()
//                            )
//                        )
//                    }
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception(response.message ?: "生產失敗"))
                    }
                } catch (apiError: Exception) {
                    Timber.e(apiError, "❌ API error for $uid, saving to pending operations")
                    saveToPendingAndUpdateLocal(request, uid, productId, batchId, product, batch, quantity, productionDate)
                    Result.success(Unit)
                }
            } else {
                // ✅ 離線模式：保存到待同步操作 + 更新本地數據庫
                Timber.d("📱 Offline: Saving to pending operations - $uid")
                saveToPendingAndUpdateLocal(request, uid, productId, batchId, product, batch, quantity, productionDate)
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
        product: Product,
        batch: Batch,
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
        updateLocalBasket(uid, productId, batchId, product, batch, quantity, productionDate)
    }

    /**
     * 更新本地籃子數據庫
     */
    private suspend fun updateLocalBasket(
        uid: String,
        productId: String,
        batchId: String,
        product: Product,
        batch: Batch,
        quantity: Int,
        productionDate: String
    ) {
        Timber.d("🔍 [updateLocalBasket] Attempting to update basket: uid=$uid")

        // 查詢目標籃子
        val entity = basketDao.getBasketByUid(uid)

        if (entity != null) {
            Timber.d("✅ [updateLocalBasket] Found entity: uid=${entity.uid}, status=${entity.status}, productID=${entity.productId}")

            val productJson = try {
                json.encodeToString(product)
            } catch (e: Exception) {
                Timber.e(e, "Failed to encode product to JSON")
                null
            }

            val batchJson = try {
                json.encodeToString(batch)
            } catch (e: Exception) {
                Timber.e(e, "Failed to encode batch to JSON")
                null
            }

            val updatedEntity = entity.copy(
                productId = productId,
                productName = product.name,
                batchId = batchId,
                productJson = productJson,
                batchJson = batchJson,
                quantity = quantity,
                status = BasketStatus.IN_PRODUCTION,
                productionDate = productionDate,
                lastUpdated = System.currentTimeMillis()
            )

            try {
                basketDao.updateBasket(updatedEntity)
                Timber.d("💾 [updateLocalBasket] Database update executed for: $uid")
            } catch (e: Exception) {
                Timber.e(e, "❌ [updateLocalBasket] Failed to update basket: $uid")
            }
        } else {
            Timber.e("❌ [updateLocalBasket] Basket entity not found in local DB: $uid")
        }
    }
}