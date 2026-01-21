package com.kdl.rfidinventory.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.api.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.BasketUpdateItemDto
import com.kdl.rfidinventory.data.remote.dto.request.BulkCreateRequest
import com.kdl.rfidinventory.data.remote.dto.request.BulkUpdateRequest
import com.kdl.rfidinventory.data.remote.dto.request.CommonDataDto
import com.kdl.rfidinventory.data.remote.dto.request.CreateBasketItemDto
import com.kdl.rfidinventory.data.remote.dto.response.BulkCreateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 籃子驗證結果
 */
//sealed class BasketValidationResult {
//    data class Error(val message: String) : BasketValidationResult()
//}

@Singleton
class BasketRepository @Inject constructor(
    private val apiService: ApiService,
    private val basketDao: BasketDao,
    private val pendingOperationDao: PendingOperationDao
) {
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
     * 統一提交籃子更新 (Production, Receiving, Transfer, Clear)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun updateBasket(
        updateType: String,
        commonData: CommonDataDto,
        items: List<BasketUpdateItemDto>,
        isOnline: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = BulkUpdateRequest(
                updateType = updateType,
                commonData = commonData,
                baskets = items
            )

            if (isOnline) {
                // Online: 呼叫 API
                val response = apiService.bulkUpdateBaskets(request)

                if (response.isSuccessful) {
                    Timber.d("✅ Bulk update ($updateType) success: ${items.size} items")
                    // 更新本地 DB
                    updateLocalDatabase(updateType, commonData, items)
                    Result.success(Unit)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: response.message()
                    Result.failure(Exception("提交失敗: $errorMsg"))
                }
            } else {
                // Offline: 存入 PendingOperation
                val payloadJson = Json.encodeToString(request)
                val operation = PendingOperationEntity(
                    operationType = OperationType.valueOf(updateType.uppercase()), // 確保 Enum 存在
                    uid = "BULK-${System.currentTimeMillis()}",
                    payload = payloadJson,
                    timestamp = System.currentTimeMillis()
                )
                pendingOperationDao.insertOperation(operation)

                // 更新本地 DB
                updateLocalDatabase(updateType, commonData, items)

                Timber.d("📱 Offline bulk update ($updateType) saved")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Submit bulk update error")
            Result.failure(e)
        }
    }

    /**
     * 根據 updateType 和優先級更新本地資料庫
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun updateLocalDatabase(
        updateType: String,
        common: CommonDataDto,
        items: List<BasketUpdateItemDto>
    ) {
        val currentTime = System.currentTimeMillis()
        val today = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE)

        items.forEach { item ->
            val entity = basketDao.getBasketByUid(item.rfid) ?: return@forEach

            // 1. 決定 Status (Item > Common > Default)
            val targetStatusStr = item.status ?: common.status ?: when (updateType) {
                "Production" -> "IN_PRODUCTION"
                "Receiving", "Transfer" -> "IN_STOCK"
                "Clear" -> "UNASSIGNED"
                else -> entity.status.name
            }
            val newStatus = try { BasketStatus.valueOf(targetStatusStr) } catch (e: Exception) { entity.status }

            // 2. 決定 Warehouse (Item > Common > Original)
            val newWarehouseId = item.warehouseId ?: common.warehouseId ?: entity.warehouseId

            // 3. 決定 Quantity (Item > Common > Original)
            val newQuantity = item.quantity ?: common.quantity ?: entity.quantity

            // 4. 決定 UpdateBy
            val newUpdateBy = common.updateBy ?: entity.updateBy

            // 5. 根據 Type 處理特定邏輯
            val updatedEntity = when (updateType) {
                "Production" -> {
                    // 生產模式：更新產品、批次
                    // 注意：這裡假設 commonData.product 是 JSON String
                    entity.copy(
                        status = newStatus,
                        quantity = newQuantity,
                        productJson = common.product, // 生產通常是同一產品
                        batchJson = common.batch,
                        // 這裡為了效能，我們可能需要解析 JSON 來填入 productId/batchId 扁平欄位
                        // 暫時簡化，實作時建議這裡做解析
                        lastUpdated = currentTime,
                        updateBy = newUpdateBy,
                        productionDate = today // 或從 batch 解析
                    )
                }
                "Clear" -> {
                    // 清除模式
                    entity.copy(
                        status = BasketStatus.UNASSIGNED,
                        quantity = 0,
                        productId = null, productName = null, batchId = null,
                        productJson = null, batchJson = null,
                        warehouseId = null,
                        lastUpdated = currentTime,
                        updateBy = newUpdateBy
                    )
                }
                else -> {
                    // Receiving, Transfer, 一般更新
                    entity.copy(
                        status = newStatus,
                        quantity = newQuantity,
                        warehouseId = newWarehouseId,
                        lastUpdated = currentTime,
                        updateBy = newUpdateBy
                    )
                }
            }
            basketDao.updateBasket(updatedEntity)
        }
    }

    /**
     * 批量註冊籃子
     */
    suspend fun bulkRegisterBaskets(uids: List<String>, isOnline: Boolean): Result<BulkCreateResult> = withContext(Dispatchers.IO) {
        try {
            if (isOnline) {
                // Online: 直接呼叫批量建立 API，不需預先過濾本地資料
                val request = BulkCreateRequest(
                    items = uids.map { CreateBasketItemDto(rfid = it, type = 1) }
                )

                // 發送請求
                val response = apiService.createBasketsBulk(request)

                if (response.isSuccessful && response.body() != null) {
                    val results = response.body()!!.results

                    // 1. 處理註冊成功的 ("Success") -> 寫入本地
                    results.filter { it.success }.forEach { item ->
                        saveLocalBasket(item.rfid)
                    }

                    // 2. 處理已存在的 ("Already exists") -> 確保本地也有這筆資料 (同步)
                    results.filter { !it.success && it.message.contains("exists", ignoreCase = true) }.forEach { item ->
                        // 這裡可以選擇不操作（因為已存在），或者為了保險起見，檢查本地是否缺失並補上
                        saveLocalBasket(item.rfid)
                    }

                    Result.success(BulkCreateResult(
                        successCount = results.count { it.success },
                        totalCount = uids.size,
                        details = results
                    ))
                } else {
                    Result.failure(Exception("API 錯誤: ${response.code()}"))
                }
            } else {
                // Offline: 直接寫入本地
                uids.forEach { uid -> saveLocalBasket(uid) }

                // TODO: PendingOperation 的邏輯以支援稍後同步

                Result.success(BulkCreateResult(
                    successCount = uids.size,
                    totalCount = uids.size,
                    details = emptyList(),
                    isOffline = true
                ))
            }
        } catch (e: Exception) {
            Timber.e(e, "Bulk register error")
            Result.failure(e)
        }
    }

    private suspend fun saveLocalBasket(uid: String) {
        if (basketDao.getBasketByUid(uid) == null) {
            val basket = BasketEntity(
                uid = uid,
                productId = null, productName = null, batchId = null, warehouseId = null,
                productJson = null, batchJson = null,
                quantity = 0,
                status = BasketStatus.UNASSIGNED,
                productionDate = null, expireDate = null,
                lastUpdated = System.currentTimeMillis(),
                updateBy = null
            )
            basketDao.insertBasket(basket)
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

}
