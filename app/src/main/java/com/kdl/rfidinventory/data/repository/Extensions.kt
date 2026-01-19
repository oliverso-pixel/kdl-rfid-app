package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.remote.dto.response.BasketResponse
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.dto.response.ApiBasketDto
import com.kdl.rfidinventory.data.remote.dto.response.BasketDetailResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun ApiBasketDto.toEntity(): BasketEntity {
    return BasketEntity(
        uid = rfid,
        productId = product,
        productName = null, // API 目前可能只回傳 ID
        batchId = batch,
        warehouseId = warehouseId,
        productJson = null,
        batchJson = null,
        quantity = quantity ?: 0,
        status = try {
            BasketStatus.valueOf(status)
        } catch (e: Exception) {
            BasketStatus.UNASSIGNED
        },
        productionDate = null,
        expireDate = null,
        lastUpdated = System.currentTimeMillis(),
        updateBy = updateBy
    )
}

// BasketEntity 轉 Basket
fun BasketEntity.toBasket(): Basket {
    // 优先从 JSON 反序列化（获取完整信息）
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
                id = productId,
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
                id = batchId,
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

// Basket 轉 BasketEntity
fun Basket.toEntity(): BasketEntity {
    return BasketEntity(
        uid = uid,
        productId = product?.id,
        productName = product?.name,
        batchId = batch?.id,
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

// BasketDetailResponse 轉 Basket
fun BasketDetailResponse.toBasket(): Basket {
    val product = if (productId != null && productName != null) {
        Product(
            id = productId,
            name = productName,
            maxBasketCapacity = 60,
            imageUrl = null
        )
    } else null

    val batch = if (batchId != null && productionDate != null) {
        Batch(
            id = batchId,
            productId = productId ?: "",
            totalQuantity = quantity,
            remainingQuantity = quantity,
            productionDate = productionDate
        )
    } else null

    return Basket(
        uid = uid,
        product = product,
        batch = batch,
        warehouseId = warehouseId,
        quantity = quantity,
        status = try {
            BasketStatus.valueOf(status)
        } catch (e: Exception) {
            BasketStatus.UNASSIGNED
        },
        productionDate = productionDate,
        expireDate = expireDate,
        lastUpdated = lastUpdated,
        updateBy = updateBy
    )
}

// API Response 轉 Basket
fun BasketResponse.toBasket(): Basket {
    val product = if (productId != null && productName != null) {
        Product(
            id = productId,
            name = productName,
            maxBasketCapacity = 60,
            imageUrl = null
        )
    } else null

    val batch = if (batchId != null && productionDate != null) {
        Batch(
            id = batchId,
            productId = productId ?: "",
            totalQuantity = quantity,
            remainingQuantity = quantity,
            productionDate = productionDate
        )
    } else null

    return Basket(
        uid = uid,
        product = product,
        batch = batch,
        warehouseId = warehouseId,
        quantity = quantity,
        status = try {
            BasketStatus.valueOf(status)
        } catch (e: Exception) {
            BasketStatus.UNASSIGNED
        },
        productionDate = productionDate,
        expireDate = null,
        lastUpdated = lastUpdated,
        updateBy = null
    )
}

// ProductionOrderResponse 轉 ProductionOrder
fun com.kdl.rfidinventory.data.remote.dto.response.ProductionOrderResponse.toProductionOrder(): ProductionOrder {
    return ProductionOrder(
        productId = productId,
        barcodeId = barcodeId,
        qrcodeId = qrcodeId,
        productName = productName,
        maxBasketCapacity = totalQuantity,
        imageUrl = imageUrl
    )
}

// RouteResponse 轉 Route
fun com.kdl.rfidinventory.data.remote.dto.response.RouteResponse.toRoute(): Route {
    return Route(
        id = id,
        name = name,
        destination = destination,
        isActive = isActive
    )
}