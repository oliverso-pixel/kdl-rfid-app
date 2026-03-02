package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.dto.response.BasketDetailResponse
import com.kdl.rfidinventory.data.remote.dto.response.DailyProductResponse
import com.kdl.rfidinventory.data.remote.dto.response.ProductionBatchResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        btype = this.btype,
        maxBasketCapacity = this.maxBasketCapacity,
        imageUrl = this.imageUrl
    )
}

// ProductionBatchResponse -> Batch
fun ProductionBatchResponse.toBatch(): Batch {
    return Batch(
        batch_code = this.batchCode,
        itemcode = this.itemCode,

        totalQuantity = this.totalQuantity,
        targetQuantity = this.targetQuantity,
        producedQuantity = this.producedQuantity,
        remainingQuantity = this.remainingQuantity,
        status = this.status,
        maxRepairs = this.maxRepairs,

        productionDate = this.productionDate ?: "",
        expireDate = this.expireDate
    )
}

// Basket 轉 BasketEntity
fun Basket.toEntity(): BasketEntity {
    return BasketEntity(
        uid = uid,
        type = type,
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
    val parsedProduct = product?.let { jsonString ->
        if (jsonString.isBlank() || jsonString == "null") return@let null
        try {
            json.decodeFromString<Product>(jsonString)
        } catch (e: Exception) {
            Timber.e("❌ Failed to parse product JSON: $jsonString")
            null
        }
    }

    val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val parsedBatch = batch?.let { jsonString ->
        if (jsonString.isBlank() || jsonString == "null") return@let null
        try {
//            json.decodeFromString<Batch>(jsonString)
            lenientJson.decodeFromString<Batch>(jsonString)
        } catch (e: Exception) {
            try {
                val jsonElement = json.parseToJsonElement(jsonString).jsonObject
                Batch(
                    batch_code = jsonElement["batch_code"]?.jsonPrimitive?.content ?: "",
                    itemcode = jsonElement["itemcode"]?.jsonPrimitive?.content ?: "",
                    totalQuantity = jsonElement["totalQuantity"]?.jsonPrimitive?.intOrNull ?: 0,
                    targetQuantity = jsonElement["targetQuantity"]?.jsonPrimitive?.intOrNull ?: 0,
                    producedQuantity = jsonElement["producedQuantity"]?.jsonPrimitive?.intOrNull ?: 0,
                    remainingQuantity = jsonElement["remainingQuantity"]?.jsonPrimitive?.intOrNull ?: 0,
                    status = jsonElement["status"]?.jsonPrimitive?.contentOrNull ?: "PENDING",
                    maxRepairs = jsonElement["maxRepairs"]?.jsonPrimitive?.intOrNull ?: 1,
                    productionDate = jsonElement["productionDate"]?.jsonPrimitive?.content ?: "",
                    expireDate = jsonElement["expireDate"]?.jsonPrimitive?.contentOrNull
                )
            } catch (e2: Exception) {
                Timber.e(e2, "❌ Failed to parse batch JSON (fallback): $jsonString")
                null
            }
        }
    }

    return Basket(
        uid = rfid,
        type = type,
        product = parsedProduct,
        batch = parsedBatch,
        warehouseId = warehouseId,
        quantity = quantity,
        status = try {
            BasketStatus.valueOf(status)
        } catch (e: Exception) {
            BasketStatus.UNASSIGNED
        },
        productionDate = parsedBatch?.productionDate,
        expireDate = parsedBatch?.expireDate,
        lastUpdated = System.currentTimeMillis(),
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
                imageUrl = null,
                btype = 1
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
                itemcode = productId ?: "",
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
        type = type,
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