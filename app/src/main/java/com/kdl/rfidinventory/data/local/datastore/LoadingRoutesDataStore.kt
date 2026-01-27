package com.kdl.rfidinventory.data.local.datastore

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.kdl.rfidinventory.data.model.LoadingRoute
import com.kdl.rfidinventory.data.model.mockLoadingRoutes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadingRoutesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val loadingRoutesFile: File
        get() = File(context.filesDir, "loading_routes.json")

    /**
     * 初始化 - 如果文件不存在，創建並寫入默認數據
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            if (!loadingRoutesFile.exists()) {
                Timber.d("📝 Initializing loading routes file with default data")
                saveLoadingRoutes(mockLoadingRoutes())
            } else {
                Timber.d("📁 Loading routes file already exists")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize loading routes file")
        }
    }

    /**
     * 讀取所有路線數據
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getLoadingRoutes(): Result<List<LoadingRoute>> = withContext(Dispatchers.IO) {
        try {
            if (!loadingRoutesFile.exists()) {
                Timber.w("⚠️ Loading routes file not found, creating with default data")
                saveLoadingRoutes(mockLoadingRoutes())
                return@withContext Result.success(mockLoadingRoutes())
            }

            val jsonString = loadingRoutesFile.readText()
            val routes = json.decodeFromString<List<LoadingRoute>>(jsonString)

            Timber.d("✅ Loaded ${routes.size} routes from local file")
            Result.success(routes)
        } catch (e: Exception) {
            Timber.e(e, "Failed to read loading routes from file")
            Result.failure(e)
        }
    }

    /**
     * 根據日期篩選路線
     */
    suspend fun getLoadingRoutesByDate(date: String): Result<List<LoadingRoute>> =
        withContext(Dispatchers.IO) {
            try {
                val allRoutes = getLoadingRoutes().getOrThrow()
                val filteredRoutes = allRoutes.filter { it.deliveryDate == date }

                Timber.d("📅 Filtered ${filteredRoutes.size} routes for date: $date")
                Timber.d("📅 Result ${filteredRoutes}")
                Result.success(filteredRoutes)
            } catch (e: Exception) {
                Timber.e(e, "Failed to filter routes by date")
                Result.failure(e)
            }
        }

    /**
     * 獲取單個路線詳情
     */
    suspend fun getRouteById(routeId: String): Result<LoadingRoute> =
        withContext(Dispatchers.IO) {
            try {
                val allRoutes = getLoadingRoutes().getOrThrow()
                val route = allRoutes.find { it.id == routeId }

                if (route != null) {
                    Timber.d("✅ Found route: ${route.name}")
                    Timber.d("✅ route: ${route}")
                    Result.success(route)
                } else {
                    Timber.w("⚠️ Route not found: $routeId")
                    Result.failure(Exception("Route not found: $routeId"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get route by id")
                Result.failure(e)
            }
        }

    /**
     * 保存所有路線數據
     */
    suspend fun saveLoadingRoutes(routes: List<LoadingRoute>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val jsonString = json.encodeToString(routes)
                loadingRoutesFile.writeText(jsonString)

                Timber.d("💾 Saved ${routes.size} routes to local file")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save loading routes to file")
                Result.failure(e)
            }
        }

    /**
     * 更新單個路線
     */
    suspend fun updateRoute(updatedRoute: LoadingRoute): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val allRoutes = getLoadingRoutes().getOrThrow().toMutableList()
                val index = allRoutes.indexOfFirst { it.id == updatedRoute.id }

                if (index != -1) {
                    allRoutes[index] = updatedRoute
                    saveLoadingRoutes(allRoutes)

                    Timber.d("✅ Updated route: ${updatedRoute.name}")
                    Result.success(Unit)
                } else {
                    Timber.w("⚠️ Route not found for update: ${updatedRoute.id}")
                    Result.failure(Exception("Route not found: ${updatedRoute.id}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update route")
                Result.failure(e)
            }
        }

    /**
     * 更新路線的完成狀態
     */
    suspend fun updateRouteCompletionStatus(
        routeId: String,
        updateFn: (LoadingRoute) -> LoadingRoute
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val allRoutes = getLoadingRoutes().getOrThrow().toMutableList()
            val index = allRoutes.indexOfFirst { it.id == routeId }

            if (index != -1) {
                allRoutes[index] = updateFn(allRoutes[index])
                saveLoadingRoutes(allRoutes)

                Timber.d("✅ Updated completion status for route: $routeId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Route not found: $routeId"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update route completion status")
            Result.failure(e)
        }
    }

    /**
     * 更新產品項的完成狀態
     */
    suspend fun updateItemCompletionStatus(
        routeId: String,
        productId: String,
        updateFn: (LoadingRoute) -> LoadingRoute
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val allRoutes = getLoadingRoutes().getOrThrow().toMutableList()
            val routeIndex = allRoutes.indexOfFirst { it.id == routeId }

            if (routeIndex != -1) {
                allRoutes[routeIndex] = updateFn(allRoutes[routeIndex])
                saveLoadingRoutes(allRoutes)

                Timber.d("✅ Updated item completion status for route: $routeId, product: $productId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Route not found: $routeId"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update item completion status")
            Result.failure(e)
        }
    }

    /**
     * 添加新路線
     */
    suspend fun addRoute(route: LoadingRoute): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val allRoutes = getLoadingRoutes().getOrThrow().toMutableList()

                // 檢查是否已存在
                if (allRoutes.any { it.id == route.id }) {
                    Timber.w("⚠️ Route already exists: ${route.id}")
                    return@withContext Result.failure(Exception("Route already exists"))
                }

                allRoutes.add(route)
                saveLoadingRoutes(allRoutes)

                Timber.d("✅ Added new route: ${route.name}")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to add route")
                Result.failure(e)
            }
        }

    /**
     * 刪除路線
     */
    suspend fun deleteRoute(routeId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val allRoutes = getLoadingRoutes().getOrThrow().toMutableList()
                val removed = allRoutes.removeIf { it.id == routeId }

                if (removed) {
                    saveLoadingRoutes(allRoutes)
                    Timber.d("✅ Deleted route: $routeId")
                    Result.success(Unit)
                } else {
                    Timber.w("⚠️ Route not found for deletion: $routeId")
                    Result.failure(Exception("Route not found"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete route")
                Result.failure(e)
            }
        }

    /**
     * 清空所有數據
     */
    suspend fun clearAll(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (loadingRoutesFile.exists()) {
                loadingRoutesFile.delete()
                Timber.d("🗑️ Cleared all loading routes data")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear loading routes")
            Result.failure(e)
        }
    }

    /**
     * 重置為默認數據
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun resetToDefault(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            saveLoadingRoutes(mockLoadingRoutes())
            Timber.d("🔄 Reset to default loading routes data")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to reset loading routes")
            Result.failure(e)
        }
    }

    /**
     * 從 API 同步數據
     */
    suspend fun syncFromApi(apiRoutes: List<LoadingRoute>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // 保存從 API 獲取的數據
                saveLoadingRoutes(apiRoutes)
                Timber.d("🔄 Synced ${apiRoutes.size} routes from API")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync routes from API")
                Result.failure(e)
            }
        }
}