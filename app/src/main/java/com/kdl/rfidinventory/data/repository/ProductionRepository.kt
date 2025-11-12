package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.api.ProductionApi
import com.kdl.rfidinventory.data.remote.dto.request.ProductionStartRequest
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductionRepository @Inject constructor(
    private val productionApi: ProductionApi,
    private val basketDao: BasketDao,
    private val pendingOperationDao: PendingOperationDao
) {

    /**
     * 獲取待生產訂單列表 (Mock 數據)
     */
    suspend fun getProductionOrders(): Result<List<ProductionOrder>> {
        return try {
            // Mock 數據
            delay(500)
            Result.success(mockProductionOrders())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 獲取產品詳情
     */
    suspend fun getProductById(productId: String): Result<Product> {
        return try {
            delay(300)
            Result.success(mockProducts().find { it.id == productId }!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 開始生產（綁定籃子）
     */
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
                // 線上模式：直接呼叫 API
                delay(500)  // Mock API 延遲
                // val response = productionApi.startProduction(request)
                Result.success(Unit)
            } else {
                // 離線模式：加入待辦隊列
                val operation = PendingOperationEntity(
                    operationType = OperationType.PRODUCTION_START,
                    uid = uid,
                    payload = Json.encodeToString(request),
                    timestamp = System.currentTimeMillis()
                )
                pendingOperationDao.insertOperation(operation)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Mock 數據
    private fun mockProductionOrders() = listOf(
        ProductionOrder("P001", "產品 A", 250),
        ProductionOrder("P002", "產品 B", 180),
        ProductionOrder("P003", "產品 C", 120)
    )

    private fun mockProducts() = listOf(
        Product("P001", "產品 A", 60),
        Product("P002", "產品 B", 50),
        Product("P003", "產品 C", 40)
    )
}

data class ProductionOrder(
    val productId: String,
    val productName: String,
    val totalQuantity: Int
)