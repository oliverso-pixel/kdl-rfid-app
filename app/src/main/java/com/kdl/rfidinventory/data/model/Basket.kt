package com.kdl.rfidinventory.data.model

data class Basket (
    val uid: String,
    val tagCode: String? = null,
    val type: Int? = null,
    val product: Product?,
    val batch: Batch?,
    val warehouseId: String?,
    val quantity: Int,
    val status: BasketStatus,
    val productionDate: String? = null,
    val expireDate: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val updateBy: String?
)

enum class BasketStatus {
    UNASSIGNED,
    IN_PRODUCTION,
    RECEIVED,
    IN_STOCK,
    LOADING,
    SHIPPED,
    DELIVERED,
    RETURNED,
    SAMPLING,
    DAMAGED
}

fun getBasketStatusText(status: BasketStatus): String {
    return when (status) {
        BasketStatus.UNASSIGNED -> "未分配"
        BasketStatus.IN_PRODUCTION -> "生產中"
        BasketStatus.RECEIVED -> "已收貨"
        BasketStatus.IN_STOCK -> "在庫中"
        BasketStatus.LOADING -> "上貨中"
        BasketStatus.SHIPPED -> "已發貨"
        BasketStatus.DELIVERED -> "已送達"
        BasketStatus.RETURNED -> "已退回"
        BasketStatus.SAMPLING -> "抽樣中"
        BasketStatus.DAMAGED -> "已損壞"
//        else -> status.toString()
    }
}

fun getStatusErrorMessage(status: BasketStatus): String {
    return when (status) {
        BasketStatus.UNASSIGNED -> "此籃子尚未配置產品，無法收貨"
        BasketStatus.RECEIVED -> "此籃子已經收貨，請勿重複操作"
        BasketStatus.IN_STOCK -> "此籃子已在庫中，無需再次收貨"
        BasketStatus.SHIPPED -> "此籃子已出貨，無法收貨"
        BasketStatus.SAMPLING -> "此籃子正在抽樣檢驗中"
        else -> "此籃子狀態不符，只能收貨「生產中」的籃子"
    }
}