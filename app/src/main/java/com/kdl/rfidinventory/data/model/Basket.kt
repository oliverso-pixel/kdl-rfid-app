package com.kdl.rfidinventory.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class Basket (
    val uid: String,
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

@Composable
fun getStatusColor(status: BasketStatus): Color {
    return when (status) {
        BasketStatus.UNASSIGNED -> MaterialTheme.colorScheme.outline
        BasketStatus.IN_PRODUCTION -> Color(0xFFFF9800)
        BasketStatus.RECEIVED -> Color(0xFF2196F3)
        BasketStatus.IN_STOCK -> Color(0xFF4CAF50)
        BasketStatus.LOADING -> Color(0xFF9C27B0)
        BasketStatus.SHIPPED -> Color(0xFF9C27B0)
        BasketStatus.DELIVERED -> Color(0xFFFFEB3B)
        BasketStatus.RETURNED -> Color(0xFFFFEB3B)
        BasketStatus.SAMPLING -> Color(0xFFFFEB3B)
        BasketStatus.DAMAGED -> Color(0xFF656565)
    }
}

@Composable
fun getStatusColor_BMS_tag(status: BasketStatus): Color {
    return when (status) {
        BasketStatus.UNASSIGNED -> MaterialTheme.colorScheme.secondaryContainer
        BasketStatus.IN_PRODUCTION -> MaterialTheme.colorScheme.primaryContainer
        BasketStatus.RECEIVED -> MaterialTheme.colorScheme.tertiaryContainer
        BasketStatus.IN_STOCK -> MaterialTheme.colorScheme.tertiaryContainer
        BasketStatus.SHIPPED -> MaterialTheme.colorScheme.surfaceVariant
        BasketStatus.SAMPLING -> MaterialTheme.colorScheme.errorContainer
        BasketStatus.DAMAGED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.outline
    }
}

@Composable
fun getStatusColor_BMS(status: BasketStatus): Color {
    return when (status) {
        BasketStatus.UNASSIGNED -> MaterialTheme.colorScheme.onSecondaryContainer
        BasketStatus.IN_PRODUCTION -> MaterialTheme.colorScheme.onPrimaryContainer
        BasketStatus.RECEIVED -> MaterialTheme.colorScheme.onTertiaryContainer
        BasketStatus.IN_STOCK -> MaterialTheme.colorScheme.onTertiaryContainer
        BasketStatus.SHIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        BasketStatus.SAMPLING -> MaterialTheme.colorScheme.onErrorContainer
        BasketStatus.DAMAGED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.outline
    }
}

fun getStatusIcon(status: BasketStatus): ImageVector {
    return when (status) {
        BasketStatus.UNASSIGNED -> Icons.Default.Help
        BasketStatus.IN_PRODUCTION -> Icons.Default.Build
        BasketStatus.RECEIVED -> Icons.Default.Check
        BasketStatus.IN_STOCK -> Icons.Default.Inventory
        BasketStatus.SHIPPED -> Icons.Default.LocalShipping
        BasketStatus.SAMPLING -> Icons.Default.Science
        BasketStatus.DAMAGED -> Icons.Default.Dangerous
        else -> Icons.Default.Abc
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

private fun generateMockBasket(uid: String): Basket {
    // 根據 UID 生成不同狀態的 Mock 數據
    val random = uid.hashCode()
    val status = when (random % 4) {
        0 -> BasketStatus.IN_PRODUCTION  // 50% 機率是生產中
        1 -> BasketStatus.IN_PRODUCTION
        2 -> BasketStatus.UNASSIGNED     // 25% 未配置
        else -> BasketStatus.IN_STOCK    // 25% 已在庫
    }

    return Basket(
        uid = uid,
        product = if (status != BasketStatus.UNASSIGNED) {
            Product(
                id = "P001",
                name = "大紅",
                maxBasketCapacity = 60,
                imageUrl = "https://homedelivery.kowloondairy.com/media/catalog/product/k/d/kd-946_800x800_freshmilk_front.png"
            )
        } else null,
        batch = if (status != BasketStatus.UNASSIGNED) {
            Batch(
                id = "BATCH-2025-001",
                productId = "P001",
                totalQuantity = 1000,
                remainingQuantity = 500,
                productionDate = "2025-11-18"
            )
        } else null,
        warehouseId = null,
        quantity = if (status != BasketStatus.UNASSIGNED) 60 else 0,
        status = status,
        productionDate = if (status != BasketStatus.UNASSIGNED) "2025-11-18" else null,
        expireDate = null,
        lastUpdated = System.currentTimeMillis(),
        updateBy = null
    )
}