package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.database.AppDatabase
import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.remote.api.ApiService
import com.kdl.rfidinventory.data.remote.api.RegisterBasketRequest
import com.kdl.rfidinventory.presentation.ui.screens.admin.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val apiService: ApiService,
    private val database: AppDatabase
) {

    suspend fun getSettings(): Result<AppSettings> = withContext(Dispatchers.IO) {
        try {
            val settings = AppSettings(
                serverUrl = "http://192.9.204.144:8000",
                scanTimeoutSeconds = 30,
                autoSync = true,
                appVersion = "1.0.0",
                databaseVersion = 1
            )
            Result.success(settings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 登記籃子 RFID
     * @param uid 籃子 UID
     * @param isOnline 是否在線
     * @return Result<RegisterBasketResult>
     */
    suspend fun registerBasket(uid: String, isOnline: Boolean): Result<RegisterBasketResult> =
        withContext(Dispatchers.IO) {
            try {
                // 檢查本地是否已存在
                val existingBasket = database.basketDao().getBasketByUid(uid)
                if (existingBasket != null) {
                    return@withContext Result.success(
                        RegisterBasketResult.AlreadyExistsLocally(existingBasket)
                    )
                }

                if (isOnline) {
                    // Online：先檢查 API
                    try {
                        val checkResponse = apiService.checkBasketRegistration(uid)

                        if (checkResponse.success && checkResponse.data != null) {
                            val checkResult = checkResponse.data

//                            if (checkResult.isRegistered) {
//                                // 已在服務器註冊，同步到本地
//                                val basket = BasketEntity(
//                                    uid = uid,
//                                    productID = checkResult.productID,
//                                    productName = checkResult.productName,
//                                    batchId = checkResult.batchId,
//                                    quantity = checkResult.quantity ?: 0,
//                                    status = when (checkResult.status) {
//                                        "UNASSIGNED" -> BasketStatus.UNASSIGNED
//                                        "IN_PRODUCTION" -> BasketStatus.IN_PRODUCTION
//                                        "RECEIVED" -> BasketStatus.RECEIVED
//                                        "IN_STOCK" -> BasketStatus.IN_STOCK
//                                        "SHIPPED" -> BasketStatus.SHIPPED
//                                        "SAMPLING" -> BasketStatus.SAMPLING
//                                        else -> BasketStatus.UNASSIGNED
//                                    },
//                                    productionDate = null,
//                                    lastUpdated = System.currentTimeMillis()
//                                )
//                                database.basketDao().insertBasket(basket)
//
//                                return@withContext Result.success(
//                                    RegisterBasketResult.AlreadyRegisteredOnServer(basket)
//                                )
//                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to check basket on server")
                        // API 調用失敗，降級為離線模式
                        return@withContext registerBasketOffline(uid)
                    }

                    // 未註冊，註冊到服務器
                    try {
                        val registerResponse = apiService.registerBasket(
                            RegisterBasketRequest(
                                uid = uid,
                                registeredAt = System.currentTimeMillis()
                            )
                        )

                        if (registerResponse.success) {
                            // 同時保存到本地
                            val responseData = registerResponse.data
                            val basket = BasketEntity(
                                uid = uid,
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
                                updateBy = null,
                            )
                            database.basketDao().insertBasket(basket)

                            return@withContext Result.success(
                                RegisterBasketResult.RegisteredSuccessfully(basket)
                            )
                        } else {
                            return@withContext Result.failure(
                                Exception("註冊失敗: ${registerResponse.message ?: "未知錯誤"}")
                            )
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to register basket on server")
                        // 註冊失敗，降級為離線模式
                        return@withContext registerBasketOffline(uid)
                    }
                } else {
                    // Offline：直接保存到本地
                    return@withContext registerBasketOffline(uid)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error registering basket")
                Result.failure(e)
            }
        }

    /**
     * 離線模式註冊籃子
     */
    private suspend fun registerBasketOffline(uid: String): Result<RegisterBasketResult> {
        return try {
            val basket = BasketEntity(
                uid = uid,
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
            database.basketDao().insertBasket(basket)

            Result.success(RegisterBasketResult.RegisteredOffline(basket))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 搜索本地籃子
     */
    suspend fun searchLocalBaskets(query: String): Result<List<BasketEntity>> =
        withContext(Dispatchers.IO) {
            try {
                val baskets = if (query.isBlank()) {
                    // 返回所有籃子
                    database.basketDao().getAllBaskets().first()
                } else {
                    // 搜索特定 UID（支持模糊搜索）
                    val allBaskets = database.basketDao().getAllBaskets().first()
                    allBaskets.filter {
                        it.uid.contains(query, ignoreCase = true) ||
                                it.productId?.contains(query, ignoreCase = true) == true ||
                                it.productName?.contains(query, ignoreCase = true) == true
                    }
                }
                Result.success(baskets)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 獲取所有本地籃子（Flow）
     */
    fun getAllLocalBaskets(): Flow<List<BasketEntity>> {
        return database.basketDao().getAllBaskets()
    }

    /**
     * 刪除本地籃子
     */
    suspend fun deleteLocalBasket(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.basketDao().deleteBasket(uid)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncPendingOperations(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val pendingOps = database.pendingOperationDao().getAllPending()

            var syncedCount = 0
            pendingOps.forEach { operation ->
                try {
                    database.pendingOperationDao().delete(operation)
                    syncedCount++
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync operation: ${operation.id}")
                }
            }

            Result.success(syncedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAllData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.clearAllTables()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportLogs(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val filePath = "/storage/emulated/0/Download/rfid_logs.txt"
            Result.success(filePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateServerUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // TODO: 儲存到 SharedPreferences
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateScanTimeout(timeout: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // TODO: 儲存到 SharedPreferences
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleAutoSync(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // TODO: 儲存到 SharedPreferences
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 註冊籃子結果
 */
sealed class RegisterBasketResult {
    data class RegisteredSuccessfully(val basket: BasketEntity) : RegisterBasketResult()
    data class RegisteredOffline(val basket: BasketEntity) : RegisterBasketResult()
    data class AlreadyRegisteredOnServer(val basket: BasketEntity) : RegisterBasketResult()
    data class AlreadyExistsLocally(val basket: BasketEntity) : RegisterBasketResult()
}