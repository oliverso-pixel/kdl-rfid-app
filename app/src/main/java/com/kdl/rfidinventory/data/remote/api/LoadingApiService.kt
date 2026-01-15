package com.kdl.rfidinventory.data.remote.api

import com.kdl.rfidinventory.data.remote.dto.request.LoadingRequest
import com.kdl.rfidinventory.data.remote.dto.request.ShippingVerifyRequest
import com.kdl.rfidinventory.data.remote.dto.response.ApiResponse
import com.kdl.rfidinventory.data.remote.dto.response.LoadingRouteResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 上貨/出貨 API 接口
 */
interface LoadingApiService {

    /**
     * 獲取上貨路線列表
     */
    @GET("loading/routes")
    suspend fun getLoadingRoutes(
        @Query("date") date: String
    ): ApiResponse<List<LoadingRouteResponse>>

    /**
     * 獲取路線詳情
     */
    @GET("loading/route")
    suspend fun getRouteDetail(
        @Query("routeId") routeId: String
    ): ApiResponse<LoadingRouteResponse>

    /**
     * 提交上貨數據
     */
    @POST("loading/submit")
    suspend fun submitLoading(
        @Body request: LoadingRequest
    ): ApiResponse<Unit>

    /**
     * 提交出貨驗證數據
     */
    @POST("shipping/verify")
    suspend fun submitShippingVerify(
        @Body request: ShippingVerifyRequest
    ): ApiResponse<Unit>

    /**
     * 更新籃子狀態為已上貨
     */
    @POST("loading/update-basket-status")
    suspend fun updateBasketStatusToLoaded(
        @Body basketUids: List<String>
    ): ApiResponse<Unit>

    /**
     * 更新籃子狀態為已出貨
     */
    @POST("shipping/update-basket-status")
    suspend fun updateBasketStatusToShipped(
        @Body basketUids: List<String>
    ): ApiResponse<Unit>
}