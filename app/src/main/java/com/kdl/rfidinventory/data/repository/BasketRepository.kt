package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.api.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.ClearRequest
import com.kdl.rfidinventory.data.remote.dto.request.ScanRequest
import com.kdl.rfidinventory.data.remote.dto.request.UpdateBasketRequest
import com.kdl.rfidinventory.data.remote.dto.response.BasketResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 籃子驗證結果
 */
sealed class BasketValidationResult {
    data class Valid(val basket: Basket) : BasketValidationResult()
    data class NotRegistered(val uid: String) : BasketValidationResult()
    data class InvalidStatus(val basket: Basket, val currentStatus: BasketStatus) : BasketValidationResult()
    data class AlreadyInProduction(val basket: Basket) : BasketValidationResult()
    data class Error(val message: String) : BasketValidationResult()
}

@Singleton
class BasketRepository @Inject constructor(
    private val apiService: ApiService,
    private val basketDao: BasketDao,
    private val pendingOperationDao: PendingOperationDao
) {
    /**
     * 檢查籃子是否可用於生產
     * @param uid 籃子 UID
     * @param isOnline 是否在線
     * @return 驗證結果
     */
    suspend fun validateBasketForProduction(uid: String, isOnline: Boolean): BasketValidationResult = withContext(Dispatchers.IO) {
        try {
            if (isOnline) {
                // 在線：從服務器檢查
                Timber.d("🌐 Online: Validating basket from server: $uid")
                val response = apiService.scanBasket(ScanRequest(uid))

                if (response.success && response.data != null) {
                    val basket = response.data.toBasket()

                    // 檢查狀態
                    when (basket.status) {
                        BasketStatus.UNASSIGNED -> {
                            Timber.d("✅ Basket is valid: $uid (UNASSIGNED)")
                            // 更新本地數據庫
                            basketDao.insertBasket(basket.toEntity())
                            BasketValidationResult.Valid(basket)
                        }
                        BasketStatus.IN_PRODUCTION -> {
                            Timber.w("⚠️ Basket is already in production: $uid")
                            basketDao.insertBasket(basket.toEntity())
                            BasketValidationResult.AlreadyInProduction(basket)
                        }
                        else -> {
                            Timber.w("⚠️ Basket has invalid status: $uid (${basket.status})")
                            basketDao.insertBasket(basket.toEntity())
                            BasketValidationResult.InvalidStatus(basket, basket.status)
                        }
                    }
                } else {
                    // 服務器沒有這個籃子記錄
                    Timber.w("⚠️ Basket not registered on server: $uid")
                    BasketValidationResult.NotRegistered(uid)
                }
            } else {
                // 離線：從本地數據庫檢查
                Timber.d("📱 Offline: Validating basket from local database: $uid")
                val entity = basketDao.getBasketByUid(uid)

                if (entity != null) {
                    val basket = entity.toBasket()

                    // 檢查狀態
                    when (basket.status) {
                        BasketStatus.UNASSIGNED -> {
                            Timber.d("✅ Basket is valid (local): $uid (UNASSIGNED)")
                            BasketValidationResult.Valid(basket)
                        }
                        BasketStatus.IN_PRODUCTION -> {
                            Timber.w("⚠️ Basket is already in production (local): $uid")
                            BasketValidationResult.AlreadyInProduction(basket)
                        }
                        else -> {
                            Timber.w("⚠️ Basket has invalid status (local): $uid (${basket.status})")
                            BasketValidationResult.InvalidStatus(basket, basket.status)
                        }
                    }
                } else {
                    // 本地沒有這個籃子記錄
                    Timber.w("⚠️ Basket not registered locally: $uid")
                    BasketValidationResult.NotRegistered(uid)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error validating basket: $uid")
            BasketValidationResult.Error(e.message ?: "驗證失敗")
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
                val request = ClearRequest(
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
                                    productId = null,
                                    productName = null,
                                    batchId = null,
                                    warehouseId = null,
                                    productJson = null,
                                    batchJson = null,
                                    quantity = 0,
                                    status = BasketStatus.UNASSIGNED,
                                    productionDate = null,
                                    expireDate = null,
                                    lastUpdated = System.currentTimeMillis(),
                                    updateBy = null
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
                                productId = null,
                                productName = null,
                                batchId = null,
                                warehouseId = null,
                                productJson = null,
                                batchJson = null,
                                quantity = 0,
                                status = BasketStatus.UNASSIGNED,
                                productionDate = null,
                                expireDate = null,
                                lastUpdated = System.currentTimeMillis(),
                                updateBy = null
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