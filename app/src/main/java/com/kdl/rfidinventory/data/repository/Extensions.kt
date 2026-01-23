package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.dto.response.BasketDetailResponse
import com.kdl.rfidinventory.data.remote.dto.response.DailyProductResponse
import com.kdl.rfidinventory.data.remote.dto.response.ProductionBatchResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

// DailyProductResponse -> Product
fun DailyProductResponse.toProduct(): Product {
    return Product(
        itemcode = this.itemCode,
        barcodeId = this.barcodeId?.toLongOrNull(),
        qrcodeId = this.qrcodeId,
        name = this.name,
        maxBasketCapacity = this.maxBasketCapacity,
        imageUrl = this.imageUrl
    )
}

// ProductionBatchResponse -> Batch
fun ProductionBatchResponse.toBatch(): Batch {
    return Batch(
        batch_code = this.batchCode,
        productId = this.itemCode,
        totalQuantity = this.totalQuantity,
        remainingQuantity = this.remainingQuantity,
        productionDate = this.productionDate ?: "",
        expireDate = this.expireDate
    )
}

// Basket 轉 BasketEntity
fun Basket.toEntity(): BasketEntity {
    return BasketEntity(
        uid = uid,
        productId = product?.itemcode,
        productName = product?.name,
        batchId = batch?.batch_code,
        warehouseId = warehouseId,
        productJson = product?.let {
            try {
                json.encodeToString(it)
            } catch (e: Exception) {
                Timber.e(e, "Failed to encode product to JSON")
                null
            }
        },
        batchJson = batch?.let {
            try {
                json.encodeToString(it)
            } catch (e: Exception) {
                Timber.e(e, "Failed to encode batch to JSON")
                null
            }
        },
        quantity = quantity,
        status = status,
        productionDate = productionDate,
        expireDate = expireDate,
        lastUpdated = lastUpdated,
        updateBy = updateBy
    )
}

fun BasketDetailResponse.toBasket(): Basket {
    // 1. 解析 Product (JSON String -> Product Object)
    val parsedProduct = product?.let { jsonString ->
        if (jsonString.isBlank() || jsonString == "null") return@let null
        try {
            // 這裡假設後端 JSON String 的結構對應 DailyProductResponse
//            val dto = json.decodeFromString<DailyProductResponse>(jsonString)
//            dto.toProduct()
            json.decodeFromString<Product>(jsonString)
        } catch (e: Exception) {
            Timber.e("❌ Failed to parse product JSON: $jsonString")
            null
        }
    }

    // 2. 解析 Batch (JSON String -> Batch Object)
    val parsedBatch = batch?.let { jsonString ->
        if (jsonString.isBlank() || jsonString == "null") return@let null
        try {
//            val dto = json.decodeFromString<ProductionBatchResponse>(jsonString)
//            dto.toBatch()
            json.decodeFromString<Batch>(jsonString)
        } catch (e: Exception) {
            Timber.e("❌ Failed to parse batch JSON: $jsonString")
            null
        }
    }

    // 3. 建立 Basket 物件
    // 注意：這裡回傳的是 Domain Model，它的結構應該要乾淨易用
    return Basket(
        uid = rfid,
        product = parsedProduct, // 這裡已經是完整的 Product 物件
        batch = parsedBatch,     // 這裡已經是完整的 Batch 物件
        warehouseId = warehouseId,
        quantity = quantity,
        status = try {
            BasketStatus.valueOf(status)
        } catch (e: Exception) {
            BasketStatus.UNASSIGNED
        },
        productionDate = parsedBatch?.productionDate, // 從解析後的 Batch 獲取日期
        expireDate = parsedBatch?.expireDate,
        lastUpdated = System.currentTimeMillis(), // 暫時使用當前時間，或是解析 lastUpdated 字串
        updateBy = updateBy
    )
}

//＋＋＋＋＋＋＋＋＋

// BasketEntity 轉 Basket
fun BasketEntity.toBasket(): Basket {
    val product = productJson?.let {
        try {
            json.decodeFromString<Product>(it)
        } catch (e: Exception) {
            Timber.w("Failed to decode productJson: ${e.message}")
            null
        }
    } ?: run {
        // 降级：使用扁平字段构建 Product 对象
        if (!productId.isNullOrBlank() && !productName.isNullOrBlank()) {
            Timber.d("🔄 Using flat fields for product: $productId - $productName")
            Product(
                itemcode = productId,
                name = productName,
                maxBasketCapacity = 60,
                imageUrl = null
            )
        } else {
            Timber.w("⚠️ No product data available for basket: $uid")
            null
        }
    }

    val batch = batchJson?.let {
        try {
            json.decodeFromString<Batch>(it)
        } catch (e: Exception) {
            Timber.w("Failed to decode batchJson: ${e.message}")
            null
        }
    } ?: run {
        // 降级：使用扁平字段构建 Batch 对象
        if (!batchId.isNullOrBlank() && !productionDate.isNullOrBlank()) {
            Timber.d("🔄 Using flat fields for batch: $batchId")
            Batch(
                batch_code = batchId,
                productId = productId ?: "",
                totalQuantity = quantity,
                remainingQuantity = quantity,
                productionDate = productionDate
            )
        } else {
            null
        }
    }

    return Basket(
        uid = uid,
        product = product,
        batch = batch,
        warehouseId = warehouseId,
        quantity = quantity,
        status = status,
        productionDate = productionDate,
        expireDate = expireDate,
        lastUpdated = lastUpdated,
        updateBy = updateBy
    )
}