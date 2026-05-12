package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.database.AppDatabase
import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.local.preferences.PreferencesManager
import com.kdl.rfidinventory.data.remote.api.ApiService
import com.kdl.rfidinventory.presentation.ui.screens.admin.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val apiService: ApiService,
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager
) {

    /**
     * 取得整體設定（從 SharedPreferences 讀）
     * WebSocket 相關由 AdminViewModel 從 WebSocketManager 覆寫填入
     */
    suspend fun getSettings(): Result<AppSettings> = withContext(Dispatchers.IO) {
        try {
            val settings = AppSettings(
                serverUrl = preferencesManager.getServerUrl(),
                websocketUrl = preferencesManager.getWebSocketUrl(),
                websocketEnabled = preferencesManager.isWebSocketEnabled(),
                scanTimeoutSeconds = preferencesManager.getScanTimeout(),
                maxBasketsPerScan = preferencesManager.getMaxBasketsPerScan(),
                autoSync = preferencesManager.isAutoSyncEnabled(),
                appVersion = "1.0.0",
                databaseVersion = 1
            )
            Result.success(settings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Basket ====================

    fun getAllLocalBaskets(): Flow<List<BasketEntity>> =
        database.basketDao().getAllBaskets()

    suspend fun deleteLocalBasket(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.basketDao().deleteBasket(uid)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Pending / Sync ====================

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
            val filePath = "/sdcard/Download/rfid_logs.txt"
            Result.success(filePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Settings Persistence ====================

    suspend fun updateServerUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            preferencesManager.setServerUrl(url)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateScanTimeout(timeout: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            preferencesManager.setScanTimeout(timeout)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 每次掃描籃子上限
     */
    suspend fun updateMaxBasketsPerScan(value: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            preferencesManager.setMaxBasketsPerScan(value)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleAutoSync(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            preferencesManager.setAutoSyncEnabled(enabled)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}