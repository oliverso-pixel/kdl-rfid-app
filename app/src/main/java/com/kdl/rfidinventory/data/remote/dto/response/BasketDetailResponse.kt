package com.kdl.rfidinventory.data.remote.dto.response

import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.model.Batch
import com.kdl.rfidinventory.data.model.Product

data class BasketDetailResponse(
    val uid: String,
    val productId: String?,
    val productName: String?,
    val batchId: String?,
    val quantity: Int,
    val status: String,
    val productionDate: String?,
    val lastUpdated: Long
)

// 擴展函數
//fun BasketDetailResponse.toBasket(): Basket {
//    return Basket(
//        uid = uid,
//        product = if (productId != null && productName != null) {
//            Product(
//                id = productId,
//                name = productName,
//                maxBasketCapacity = 60
//            )
//        } else null,
//        batch = batchId?.let {
//            Batch(
//                id = it,
//                productId = productId ?: "",
//                totalQuantity = quantity,
//                remainingQuantity = quantity,
//                productionDate = productionDate ?: ""
//            )
//        },
//        quantity = quantity,
//        status = try {
//            BasketStatus.valueOf(status)
//        } catch (e: Exception) {
//            BasketStatus.UNASSIGNED
//        },
//        productionDate = productionDate,
//        lastUpdated = lastUpdated
//    )
//}