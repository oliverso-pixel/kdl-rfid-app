package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.api.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.BasketIdDto
import com.kdl.rfidinventory.data.remote.dto.request.BulkUpdateRequest
import com.kdl.rfidinventory.data.remote.dto.request.ClearRequest
import com.kdl.rfidinventory.data.remote.dto.request.CommonDataDto
import com.kdl.rfidinventory.data.remote.dto.request.ScanRequest
import com.kdl.rfidinventory.data.remote.dto.request.UpdateBasketRequest
import kotlinx.coroutines.Dispatchers
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
                val response = apiService.getBasketByRfid(uid)

                if (response.isSuccessful && response.body() != null) {
                    val apiBasketDto = response.body()!!
                    // 這裡使用 apiBasketDto (ApiBasketDto) 或 BasketDetailResponse 進行轉換
                    // 假設 ApiService 回傳的是 BasketDetailResponse (根據上面的定義)
                    val basket = apiBasketDto.toBasket()

                    // 更新本地緩存
                    basketDao.insertBasket(basket.toEntity())

                    when (basket.status) {
                        BasketStatus.UNASSIGNED -> {
                            Timber.d("✅ Basket valid: $uid")
                            BasketValidationResult.Valid(basket)
                        }
                        // 如果狀態是 IN_PRODUCTION，代表已被佔用
                        BasketStatus.IN_PRODUCTION -> {
                            Timber.w("⚠️ Basket occupied: $uid")
                            BasketValidationResult.AlreadyInProduction(basket)
                        }
                        else -> {
                            BasketValidationResult.InvalidStatus(basket, basket.status)
                        }
                    }
                } else if (response.code() == 404) {
                    // 404 代表籃子不存在，視為未註冊
                    BasketValidationResult.NotRegistered(uid)
                } else {
                    BasketValidationResult.Error("API Error: ${response.code()}")
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

    /**
     * 通用獲取籃子方法 (Fetch & Sync)
     * 1. 在線模式：從 API 獲取最新資料 -> 解析 JSON -> 更新本地 DB -> 回傳 Basket
     * 2. 離線模式：直接從本地 DB 獲取 -> 回傳 Basket
     */
    suspend fun fetchBasket(uid: String, isOnline: Boolean): Result<Basket> = withContext(Dispatchers.IO) {
        try {
            if (isOnline) {
                // Online: 呼叫 API
                val response = apiService.getBasketByRfid(uid)

                if (response.isSuccessful && response.body() != null) {
                    val apiBasket = response.body()!!
                    val basket = apiBasket.toBasket() // 使用 Extensions.kt 中的解析邏輯

                    // 同步到本地資料庫
                    basketDao.insertBasket(basket.toEntity())

                    Timber.d("✅ Fetch & Sync success: ${basket.uid}")
                    Result.success(basket)
                } else if (response.code() == 404) {
                    // 404 代表籃子未註冊
                    Timber.w("⚠️ Basket not found on server: $uid")
                    Result.failure(Exception("BASKET_NOT_REGISTERED"))
                } else {
                    Result.failure(Exception("API Error: ${response.code()}"))
                }
            } else {
                // Offline: 讀取本地
                val entity = basketDao.getBasketByUid(uid)
                if (entity != null) {
                    Timber.d("📱 Offline fetch success: ${entity.uid}")
                    Result.success(entity.toBasket())
                } else {
                    Timber.w("⚠️ Basket not found locally: $uid")
                    Result.failure(Exception("BASKET_NOT_FOUND_LOCAL"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Fetch basket error")
            Result.failure(e)
        }
    }

    /**
     * 清除籃子配置 (批量)
     */
    suspend fun clearBasketConfiguration(uids: List<String>, isOnline: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. 建構 Request 物件
            val request = BulkUpdateRequest(
                updateType = "Clear",
                commonData = CommonDataDto(), // Clear 模式下 commonData 為空
                baskets = uids.map { BasketIdDto(rfid = it) }
            )

            if (isOnline) {
                // 2. Online: 呼叫批量 API
                val response = apiService.bulkUpdateBaskets(request)

                if (response.isSuccessful) {
                    Timber.d("✅ Bulk clear success: ${response.body()?.updated_count} items")

                    // 成功後，更新本地資料庫
                    clearLocalBaskets(uids)

                    Result.success(Unit)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: response.message()
                    Timber.e("❌ Bulk clear failed: $errorMsg")
                    Result.failure(Exception("清除失敗: $errorMsg"))
                }
            } else {
                // 3. Offline: 儲存到 PendingOperation
                val payloadJson = Json.encodeToString(request)

                // 為了簡化同步邏輯，我們可以將批量請求儲存為單一操作
                // 或者如果後端同步只支援單筆，則需要拆分 (建議後端同步也支援 bulk)
                // 這裡假設同步機制能處理這個 payload
                val operation = PendingOperationEntity(
                    operationType = OperationType.CLEAR_ASSOCIATION, // 需確認此 Enum 是否存在或需新增 BULK_UPDATE
                    uid = "BULK-${System.currentTimeMillis()}", // 批量操作使用特殊 UID
                    payload = payloadJson,
                    timestamp = System.currentTimeMillis()
                )
                pendingOperationDao.insertOperation(operation)

                // 更新本地資料庫
                clearLocalBaskets(uids)

                Timber.d("📱 Offline clear saved for ${uids.size} baskets")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Clear basket configuration error")
            Result.failure(e)
        }
    }

    /**
     * 輔助方法：清除本地籃子資料
     */
    private suspend fun clearLocalBaskets(uids: List<String>) {
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
                        updateBy = null // 清除時也可以記錄 updateBy，視需求而定
                    )
                )
            }
        }
    }

//    suspend fun updateBasket(basket: Basket, isOnline: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
//        try {
//            if (isOnline) {
//                val request = UpdateBasketRequest(
//                    productId = basket.product?.id,
//                    batchId = basket.batch?.id,
//                    quantity = basket.quantity,
//                    status = basket.status.name,
//                    productionDate = basket.productionDate
//                )
//                val response = apiService.updateBasket(basket.uid, request)
//                if (response.success) {
//                    basketDao.updateBasket(basket.toEntity())
//                    Result.success(Unit)
//                } else {
////                    Result.failure(Exception(response.message ?: "更新失敗"))
//                    Result.failure(Exception("更新失敗"))
//                }
//            } else {
//                val operation = PendingOperationEntity(
//                    operationType = OperationType.ADMIN_UPDATE,
//                    uid = basket.uid,
//                    payload = Json.encodeToString(basket),
//                    timestamp = System.currentTimeMillis()
//                )
//                pendingOperationDao.insertOperation(operation)
//                basketDao.updateBasket(basket.toEntity())
//                Result.success(Unit)
//            }
//        } catch (e: Exception) {
//            Result.failure(e)
//        }
//    }

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

//    suspend fun clearBasketConfiguration(uids: List<String>, isOnline: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
//        try {
//            if (isOnline) {
//                val request = ClearRequest(
//                    basketUids = uids,
//                    timestamp = System.currentTimeMillis().toString()
//                )
//                val response = apiService.markForClear(request)
//                if (response.success) {
//                    uids.forEach { uid ->
//                        val entity = basketDao.getBasketByUid(uid)
//                        entity?.let {
//                            basketDao.updateBasket(
//                                it.copy(
//                                    productId = null,
//                                    productName = null,
//                                    batchId = null,
//                                    warehouseId = null,
//                                    productJson = null,
//                                    batchJson = null,
//                                    quantity = 0,
//                                    status = BasketStatus.UNASSIGNED,
//                                    productionDate = null,
//                                    expireDate = null,
//                                    lastUpdated = System.currentTimeMillis(),
//                                    updateBy = null
//                                )
//                            )
//                        }
//                    }
//                    Result.success(Unit)
//                } else {
//                    Result.failure(Exception(response.message ?: "清除失敗"))
//                }
//            } else {
//                uids.forEach { uid ->
//                    val operation = PendingOperationEntity(
//                        operationType = OperationType.CLEAR_ASSOCIATION,
//                        uid = uid,
//                        payload = "",
//                        timestamp = System.currentTimeMillis()
//                    )
//                    pendingOperationDao.insertOperation(operation)
//
//                    val entity = basketDao.getBasketByUid(uid)
//                    entity?.let {
//                        basketDao.updateBasket(
//                            it.copy(
//                                productId = null,
//                                productName = null,
//                                batchId = null,
//                                warehouseId = null,
//                                productJson = null,
//                                batchJson = null,
//                                quantity = 0,
//                                status = BasketStatus.UNASSIGNED,
//                                productionDate = null,
//                                expireDate = null,
//                                lastUpdated = System.currentTimeMillis(),
//                                updateBy = null
//                            )
//                        )
//                    }
//                }
//                Result.success(Unit)
//            }
//        } catch (e: Exception) {
//            Result.failure(e)
//        }
//    }
}