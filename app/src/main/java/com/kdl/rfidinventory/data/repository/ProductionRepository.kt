package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.ProductionStartRequest
import com.kdl.rfidinventory.data.remote.dto.response.toProduct
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
                        productName = it.productName,
                        totalQuantity = it.totalQuantity
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

    suspend fun getProductById(productId: String): Result<Product> {
        return try {
            val response = apiService.getProductById(productId)
            if (response.success && response.data != null) {
                val product = response.data.toProduct()
                Result.success(product)
            } else {
                delay(300)
                Result.success(mockProducts().find { it.id == productId }!!)
            }
        } catch (e: Exception) {
            delay(300)
            Result.success(mockProducts().find { it.id == productId }!!)
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
            } else {
                val operation = PendingOperationEntity(
                    operationType = OperationType.PRODUCTION_START,
                    uid = uid,
                    payload = Json.encodeToString(request),
                    timestamp = System.currentTimeMillis()
                )
                pendingOperationDao.insertOperation(operation)

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
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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