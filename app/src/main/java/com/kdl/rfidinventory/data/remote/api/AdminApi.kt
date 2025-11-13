package com.kdl.rfidinventory.data.remote.api

import com.kdl.rfidinventory.data.remote.dto.request.SyncRequest
import com.kdl.rfidinventory.data.remote.dto.request.UpdateSettingsRequest
import com.kdl.rfidinventory.data.remote.dto.response.ApiResponse
import com.kdl.rfidinventory.data.remote.dto.response.SettingsResponse
import com.kdl.rfidinventory.data.remote.dto.response.SyncResponse
import retrofit2.http.*

interface AdminApi {

    @GET("admin/settings")
    suspend fun getSettings(): ApiResponse<SettingsResponse>

    @PUT("admin/settings")
    suspend fun updateSettings(@Body request: UpdateSettingsRequest): ApiResponse<Unit>

    @POST("admin/sync")
    suspend fun syncOperations(@Body request: SyncRequest): ApiResponse<SyncResponse>

    @DELETE("admin/data")
    suspend fun clearAllData(): ApiResponse<Unit>

    @GET("admin/export-logs")
    suspend fun exportLogs(): ApiResponse<String>
}