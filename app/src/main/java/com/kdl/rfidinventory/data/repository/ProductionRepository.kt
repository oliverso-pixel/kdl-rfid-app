package com.kdl.rfidinventory.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.api.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.BindBasketRequest
import com.kdl.rfidinventory.data.remote.dto.request.ProductionStartRequest
import com.kdl.rfidinventory.data.remote.dto.response.DailyProductResponse
import com.kdl.rfidinventory.data.remote.dto.response.ProductionBatchResponse
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductionRepository @Inject constructor(
    private val apiService: ApiService,
    private val basketDao: BasketDao,
    private val pendingOperationDao: PendingOperationDao,
    private val json: Json
) {

//    suspend fun getProductionOrders(): Result<List<ProductionOrder>> {
//        return try {
//            val response = apiService.getProductionOrders()
//            if (response.success && response.data != null) {
//                val orders = response.data.map {
//                    ProductionOrder(
//                        productId = it.productId,
//                        barcodeId = it.barcodeId,
//                        qrcodeId = it.qrcodeId,
//                        productName = it.productName,
//                        maxBasketCapacity = it.totalQuantity,
//                        imageUrl = it.imageUrl
//                    )
//                }
//                Result.success(orders)
//            } else {
//                delay(500)
//                Result.success(mockProductionOrders())
//            }
//        } catch (e: Exception) {
//            delay(500)
//            Result.success(mockProductionOrders())
//        }
//    }
    suspend fun getProductionOrders(): Result<List<Product>> {
        return try {
            val response = apiService.getDailyProducts()
            if (response.isSuccessful && response.body() != null) {
                val products = response.body()!!.map { it.toProduct() }
                Result.success(products)
            } else {
                Result.failure(Exception("獲取產品失敗: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "getProductionOrders error")
            // 如果 API 失敗，暫時回傳 mock 數據或空列表，視您的需求而定
            Result.failure(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getBatchesForDate(date: String = LocalDate.now().toString()): Result<List<Batch>> {
        return try {
            val response = apiService.getProductionBatches(date)
            if (response.isSuccessful && response.body() != null) {
                val batches = response.body()!!.map { it.toBatch() }
                Result.success(batches)
            } else {
                Result.failure(Exception("獲取批次失敗: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "getBatchesForDate error")
            Result.failure(e)
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
//        return try {
//            val request = ProductionStartRequest(
//                uid = uid,
//                productId = productId,
//                batchId = batchId,
//                quantity = quantity,
//                productionDate = productionDate
//            )
//
//            if (isOnline) {
//                try {
//                    val response = apiService.startProduction(request)
//                    if (response.success) {
//                        Result.success(Unit)
//                    } else {
//                        Result.failure(Exception(response.message ?: "生產失敗"))
//                    }
//                } catch (apiError: Exception) {
//                    Timber.e(apiError, "❌ API error for $uid, saving to pending operations")
//                    saveToPendingAndUpdateLocal(request, uid, productId, batchId, product, batch, quantity, productionDate)
//                    Result.success(Unit)
//                }
//            } else {
//                // ✅ 離線模式：保存到待同步操作 + 更新本地數據庫
//                Timber.d("📱 Offline: Saving to pending operations - $uid")
//                saveToPendingAndUpdateLocal(request, uid, productId, batchId, product, batch, quantity, productionDate)
//                Result.success(Unit)
//            }
//        } catch (e: Exception) {
//            Result.failure(e)
//        }
        return try {
            val productDto = DailyProductResponse(
                itemCode = product.id,
                barcodeId = product.barcodeId?.toString() ?: "",
                qrcodeId = product.qrcodeId ?: "",
                name = product.name,
                maxBasketCapacity = product.maxBasketCapacity,
                imageUrl = product.imageUrl
            )

            val batchDto = ProductionBatchResponse(
                batchCode = batch.id,
                itemCode = product.id,
                totalQuantity = batch.totalQuantity,
                remainingQuantity = batch.remainingQuantity,
                productionDate = batch.productionDate,
                expireDate = null // 這裡根據您的 Batch Model 可能需要調整
            )

            // 將 DTO 序列化為 JSON String
            val productJsonString = json.encodeToString(productDto)
            val batchJsonString = json.encodeToString(batchDto)

            if (isOnline) {
                // Online: 呼叫 PUT API
                val request = BindBasketRequest(
                    quantity = quantity,
                    product = productJsonString,
                    batch = batchJsonString
                )

                val response = apiService.bindBasket(uid, request)

                if (response.isSuccessful) {
                    // 更新本地資料庫
                    updateLocalBasket(uid, productId, batchId, product, batch, quantity, productionDate)
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Result.failure(Exception("API 錯誤: ${response.code()} - $errorBody"))
                }
            } else {
                // Offline: 目前先保留更新本地，後續再處理同步機制
                Timber.d("📱 Offline: Updating local DB only")
                updateLocalBasket(uid, productId, batchId, product, batch, quantity, productionDate)

                // TODO: 將 BindBasketRequest 存入 pending_operations

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "startProduction error")
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