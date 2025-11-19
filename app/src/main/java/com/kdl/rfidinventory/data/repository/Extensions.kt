package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.model.*

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
        quantity = quantity,
        status = status,
        productionDate = productionDate,
        lastUpdated = lastUpdated
    )
}

// Basket 轉 BasketEntity
fun Basket.toEntity(): BasketEntity {
    return BasketEntity(
        uid = uid,
        productID = product?.id,
        productName = product?.name,
        batchId = batch?.id,
        quantity = quantity,
        status = status,
        productionDate = productionDate,
        lastUpdated = lastUpdated
    )
}

// API Response 轉 Basket
fun com.kdl.rfidinventory.data.remote.dto.response.BasketResponse.toBasket(): Basket {
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
        quantity = quantity,
        status = try {
            BasketStatus.valueOf(status)
        } catch (e: Exception) {
            BasketStatus.UNASSIGNED
        },
        productionDate = productionDate,
        lastUpdated = lastUpdated
    )
}

// ProductionOrderResponse 轉 ProductionOrder
fun com.kdl.rfidinventory.data.remote.dto.response.ProductionOrderResponse.toProductionOrder(): ProductionOrder {
    return ProductionOrder(
        productId = productId,
        barcodeID = barcodeID,
        qrcodeID = qrcodeID,
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