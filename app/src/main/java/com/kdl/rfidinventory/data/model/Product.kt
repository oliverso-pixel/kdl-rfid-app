package com.kdl.rfidinventory.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String,
    val barcodeId: Long? = null,
    val qrcodeId: String? = null,
    val name: String,
    val maxBasketCapacity: Int,
    val description: String? = null,
    val imageUrl: String? = null
)

data class ProductionOrder(
    val productId: String,
    val barcodeId: Long?,
    val qrcodeId: String?,
    val productName: String,
    val maxBasketCapacity: Int,
    val imageUrl: String?
)

fun mockProductionOrders() = listOf(
    ProductionOrder("MP01L", barcodeId = 4890008589241, "", "大紅", 20, imageUrl = "https://homedelivery.kowloondairy.com/media/catalog/product/k/d/kd-946_800x800_freshmilk_front.png?auto=webp&format=png&width=2560&height=3200&fit=cover"),
    ProductionOrder("MP01S", barcodeId = 4893318633130, "", "細紅", 60, imageUrl = "https://homedelivery.kowloondairy.com/media/catalog/product/k/d/kd-236_800x800_freshmilk_front_-20_.png?auto=webp&format=png&width=2560&height=3200&fit=cover"),
    ProductionOrder("MP04S", barcodeId = 0, "", "特濃朱古力(小)", 60, imageUrl = "https://homedelivery.kowloondairy.com/media/catalog/product/k/d/kd-236_800x800_deluxechoco_front_-20_.png?auto=webp&format=png&width=2560&height=3200&fit=cover"),
    ProductionOrder("MP09L", barcodeId = 0, "4893318633161", "澳洲全脂牛奶(大)", 20, imageUrl = "https://homedelivery.kowloondairy.com/media/catalog/product/k/d/kd-946_800x800_australia_front.png?auto=webp&format=png&width=2560&height=3200&fit=cover")
)
