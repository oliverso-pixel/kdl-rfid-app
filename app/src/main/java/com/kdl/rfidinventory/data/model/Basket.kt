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