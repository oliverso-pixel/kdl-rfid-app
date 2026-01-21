package com.kdl.rfidinventory.data.remote.api

import com.kdl.rfidinventory.data.remote.dto.request.BulkCreateRequest
import com.kdl.rfidinventory.data.remote.dto.request.BulkUpdateRequest
import com.kdl.rfidinventory.data.remote.dto.request.CreateBasketRequest
import com.kdl.rfidinventory.data.remote.dto.request.SamplingRequest
import com.kdl.rfidinventory.data.remote.dto.request.ShipBasketsRequest
import com.kdl.rfidinventory.data.remote.dto.request.UpdateBasketRequest
import com.kdl.rfidinventory.data.remote.dto.request.UpdateBasketStatusRequest
import com.kdl.rfidinventory.data.remote.dto.response.ApiResponse
import com.kdl.rfidinventory.data.remote.dto.response.BasketDetailResponse
import com.kdl.rfidinventory.data.remote.dto.response.BulkCreateResponse
import com.kdl.rfidinventory.data.remote.dto.response.BulkUpdateResponse
import com.kdl.rfidinventory.data.remote.dto.response.DailyProductResponse
import com.kdl.rfidinventory.data.remote.dto.response.GenericResponse
import com.kdl.rfidinventory.data.remote.dto.response.ProductionBatchResponse
import com.kdl.rfidinventory.data.remote.dto.response.WarehouseResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    // ==================== Production API ====================
    // 獲取每日產品列表
    @GET("production/daily-products")
    suspend fun getDailyProducts(): retrofit2.Response<List<DailyProductResponse>>

    // 獲取生產批次列表
    @GET("production/app-list")
    suspend fun getProductionBatches(
        @Query("target_date") targetDate: String
    ): retrofit2.Response<List<ProductionBatchResponse>>

    // ==================== Warehouse API ====================
    // 獲取倉庫列表
    @GET("warehouses/")
    suspend fun getWarehouses(): retrofit2.Response<List<WarehouseResponse>>

    // 更新籃子 (通用 PUT) - 用於收貨、轉換等
    @PUT("baskets/{uid}")
    suspend fun updateBasket(
        @Path("uid") uid: String,
        @Body request: UpdateBasketStatusRequest
    ): retrofit2.Response<GenericResponse>

    // ==================== Shipping API ====================
    // ==================== Sampling API ====================
    // ==================== Clear API ====================
    // ==================== Admin API ====================
    // 籃子查詢：GET /api/v1/baskets/{rfid}
    // 注意：這裡不使用 ApiResponse 包裝，因為 curl 顯示直接回傳 JSON 物件或 {"detail":...}
    @GET("baskets/{rfid}")
    suspend fun getBasketByRfid(@Path("rfid") rfid: String): retrofit2.Response<BasketDetailResponse>

    // 籃子註冊：POST /api/v1/baskets/
    @POST("baskets/")
    suspend fun createBasket(@Body request: CreateBasketRequest): retrofit2.Response<GenericResponse>

    // ==================== baskets API ====================
    // 批量註冊籃子
    @POST("baskets/bulk")
    suspend fun createBasketsBulk(
        @Body request: BulkCreateRequest
    ): retrofit2.Response<BulkCreateResponse>

    // 通用批量更新接口 (用於 Clear, Transfer, Production 等)
    @PUT("baskets/bulk-update")
    suspend fun bulkUpdateBaskets(
        @Body request: BulkUpdateRequest
    ): retrofit2.Response<BulkUpdateResponse>



    @GET("baskets/{uid}")
    suspend fun getBasketByUid(@Path("uid") uid: String): ApiResponse<BasketDetailResponse>

    @PUT("baskets/{uid}")
    suspend fun updateBasket(
        @Path("uid") uid: String,
        @Body request: UpdateBasketRequest
    ): ApiResponse<BasketDetailResponse>

    @DELETE("baskets/{uid}")
    suspend fun deleteBasket(@Path("uid") uid: String): ApiResponse<Unit>

    // 出貨相關
    @POST("shipping/ship")
    suspend fun shipBaskets(@Body request: ShipBasketsRequest): ApiResponse<Unit>

    // 抽樣相關
    @POST("sampling/mark")
    suspend fun markForSampling(@Body request: SamplingRequest): ApiResponse<Unit>
}