package com.kdl.rfidinventory.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.api.ApiService
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductionRepository @Inject constructor(
    private val apiService: ApiService,
) {
    suspend fun getProductionOrders(): Result<List<Product>> {
        return try {
            val response = apiService.getDailyProducts()
            if (response.isSuccessful && response.body() != null) {
                val products = response.body()!!.map { it.toProduct() }
                Result.success(products)
            } else {
                Result.failure(Exception("獲取產品失敗: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "getProductionOrders error")
            Result.failure(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getBatchesForDate(date: String = LocalDate.now().toString()): Result<List<Batch>> {
        return try {
            val response = apiService.getProductionBatches(date)
            if (response.isSuccessful && response.body() != null) {
                val batches = response.body()!!.map { it.toBatch() }
                Result.success(batches)
            } else {
                Result.failure(Exception("獲取批次失敗: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "getBatchesForDate error")
            Result.failure(e)
        }
    }

}