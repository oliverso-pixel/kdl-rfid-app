package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.ScanRequest
import com.kdl.rfidinventory.data.remote.dto.request.UpdateBasketRequest
import com.kdl.rfidinventory.data.remote.dto.response.toBasket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BasketRepository @Inject constructor(
    private val apiService: ApiService,
    private val basketDao: BasketDao,
    private val pendingOperationDao: PendingOperationDao
) {

    suspend fun scanBasket(uid: String, isOnline: Boolean): Result<Basket> = withContext(Dispatchers.IO) {
        try {
            if (isOnline) {
                val response = apiService.scanBasket(ScanRequest(uid))
                if (response.success && response.data != null) {
                    val basket = response.data.toBasket()
                    basketDao.insertBasket(basket.toEntity())
                    Result.success(basket)
                } else {
                    Result.failure(Exception(response.message ?: "掃描失敗"))
                }
            } else {
                val entity = basketDao.getBasketByUid(uid)
                if (entity != null) {
                    Result.success(entity.toBasket())
                } else {
                    delay(300)
                    val newBasket = Basket(
                        uid = uid,
                        product = null,
                        batch = null,
                        quantity = 0,
                        status = BasketStatus.UNASSIGNED,
                        productionDate = null,
                        lastUpdated = System.currentTimeMillis()
                    )
                    basketDao.insertBasket(newBasket.toEntity())
                    Result.success(newBasket)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBasketByUid(uid: String): Result<Basket> = withContext(Dispatchers.IO) {
        try {
            val entity = basketDao.getBasketByUid(uid)
                ?: return@withContext Result.failure(Exception("籃子不存在"))
            Result.success(entity.toBasket())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBasket(basket: Basket, isOnline: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isOnline) {
                val request = UpdateBasketRequest(
                    productId = basket.product?.id,
                    batchId = basket.batch?.id,
                    quantity = basket.quantity,
                    status = basket.status.name,
                    productionDate = basket.productionDate
                )
                val response = apiService.updateBasket(basket.uid, request)
                if (response.success) {
                    basketDao.updateBasket(basket.toEntity())
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "更新失敗"))
                }
            } else {
                val operation = PendingOperationEntity(
                    operationType = OperationType.ADMIN_UPDATE,
                    uid = basket.uid,
                    payload = Json.encodeToString(basket),
                    timestamp = System.currentTimeMillis()
                )
                pendingOperationDao.insertOperation(operation)
                basketDao.updateBasket(basket.toEntity())
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBasket(uid: String, isOnline: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isOnline) {
                val response = apiService.deleteBasket(uid)
                if (response.success) {
                    basketDao.deleteBasket(uid)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "刪除失敗"))
                }
            } else {
                val operation = PendingOperationEntity(
                    operationType = OperationType.ADMIN_UPDATE,
                    uid = uid,
                    payload = "",
                    timestamp = System.currentTimeMillis()
                )
                pendingOperationDao.insertOperation(operation)
                basketDao.deleteBasket(uid)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllBaskets(): Result<List<Basket>> = withContext(Dispatchers.IO) {
        try {
            val entities = basketDao.getAllBaskets().first()
            val baskets = entities.map { it.toBasket() }
            Result.success(baskets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearBasketConfiguration(uids: List<String>, isOnline: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isOnline) {
                val request = com.kdl.rfidinventory.data.remote.dto.request.ClearRequest(
                    basketUids = uids,
                    timestamp = System.currentTimeMillis().toString()
                )
                val response = apiService.markForClear(request)
                if (response.success) {
                    uids.forEach { uid ->
                        val entity = basketDao.getBasketByUid(uid)
                        entity?.let {
                            basketDao.updateBasket(
                                it.copy(
                                    productID = null,
                                    productName = null,
                                    batchId = null,
                                    quantity = 0,
                                    status = BasketStatus.UNASSIGNED,
                                    lastUpdated = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "清除失敗"))
                }
            } else {
                uids.forEach { uid ->
                    val operation = PendingOperationEntity(
                        operationType = OperationType.CLEAR_ASSOCIATION,
                        uid = uid,
                        payload = "",
                        timestamp = System.currentTimeMillis()
                    )
                    pendingOperationDao.insertOperation(operation)

                    val entity = basketDao.getBasketByUid(uid)
                    entity?.let {
                        basketDao.updateBasket(
                            it.copy(
                                productID = null,
                                productName = null,
                                batchId = null,
                                quantity = 0,
                                status = BasketStatus.UNASSIGNED,
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
}