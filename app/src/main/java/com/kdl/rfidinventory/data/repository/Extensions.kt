package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.remote.dto.response.BasketResponse
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.dto.response.BasketDetailResponse

// BasketEntity 轉 Basket
fun BasketEntity.toBasket(): Basket {
    return Basket(
        uid = uid,
        product = if (productID != null && productName != null) {
            Product(
                id = productID,
                name = productName,
                maxBasketCapacity = 60 // 預設值，實際應從產品資料取得
            )
        } else null,
        batch = batchId?.let {
            Batch(
                id = it,
                productId = productID ?: "",
                totalQuantity = 0,
                remainingQuantity = 0,
                productionDate = productionDate ?: ""
            )
        },
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
        productID = product?.id,
        productName = product?.name,
        batchId = batch?.id,
        warehouseId = warehouseId,
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
    return Basket(
        uid = uid,
        product = if (productId != null && productName != null) {
            Product(
                id = productId,
                name = productName,
                maxBasketCapacity = 60
            )
        } else null,
        batch = batchId?.let {
            Batch(
                id = it,
                productId = productId ?: "",
                totalQuantity = quantity,
                remainingQuantity = quantity,
                productionDate = productionDate ?: ""
            )
        },
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
    return Basket(
        uid = uid,
        product = if (productId != null && productName != null) {
            Product(
                id = productId,
                name = productName,
                maxBasketCapacity = 60
            )
        } else null,
        batch = batchId?.let {
            Batch(
                id = it,
                productId = productId ?: "",
                totalQuantity = quantity,
                remainingQuantity = quantity,
                productionDate = productionDate ?: ""
            )
        },
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
        totalQuantity = totalQuantity,
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