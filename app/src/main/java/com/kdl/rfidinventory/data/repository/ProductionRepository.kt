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
                        barcodeID = it.barcodeID,
                        qrcodeID = it.qrcodeID,
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

    private fun mockProductionOrders() = listOf(
        ProductionOrder("MP01L", barcodeID = 4890008589241, "", "大紅", 250, imageUrl = "https://homedelivery.kowloondairy.com/media/catalog/product/k/d/kd-946_800x800_freshmilk_front.png?auto=webp&format=png&width=2560&height=3200&fit=cover"),
        ProductionOrder("MP01S", barcodeID = 4893318633130, "", "細紅", 180, imageUrl = "https://homedelivery.kowloondairy.com/media/catalog/product/k/d/kd-236_800x800_freshmilk_front_-20_.png?auto=webp&format=png&width=2560&height=3200&fit=cover"),
        ProductionOrder("MP04S", barcodeID = 0, "", "特濃朱古力(小)", 180, imageUrl = "https://homedelivery.kowloondairy.com/media/catalog/product/k/d/kd-236_800x800_deluxechoco_front_-20_.png?auto=webp&format=png&width=2560&height=3200&fit=cover"),
        ProductionOrder("MP09L", barcodeID = 0, "4893318633161", "澳洲全脂牛奶(大)", 180, imageUrl = "https://homedelivery.kowloondairy.com/media/catalog/product/k/d/kd-946_800x800_australia_front.png?auto=webp&format=png&width=2560&height=3200&fit=cover")
    )
}

data class ProductionOrder(
    val productId: String,
    val barcodeID: Long?,
    val qrcodeID: String?,
    val productName: String,
    val totalQuantity: Int,
    val imageUrl: String?
)