package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.ProductionStartRequest
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
}