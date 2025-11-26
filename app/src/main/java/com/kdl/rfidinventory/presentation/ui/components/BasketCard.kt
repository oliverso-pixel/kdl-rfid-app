package com.kdl.rfidinventory.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.model.ScannedBasket
import com.kdl.rfidinventory.presentation.ui.theme.BasketStatusColors
import kotlinx.coroutines.delay

/**
 * 統一的籃子卡片組件
 * 支持兩種模式：
 * 1. 生產模式：顯示掃描次數、RSSI、時間信息
 * 2. 收貨模式：顯示狀態驗證、錯誤提示
 */
@Composable
fun BasketCard(
    basket: Basket,
    modifier: Modifier = Modifier,
    // 生產模式專用
    scannedBasket: ScannedBasket? = null,
    onResetCount: (() -> Unit)? = null,
    // 收貨模式專用
    isValidStatus: Boolean = true,
    // 共用
    maxCapacity: Int? = null,
    onQuantityChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    var quantityText by remember(basket.quantity) { mutableStateOf(basket.quantity.toString()) }
    var showError by remember { mutableStateOf(false) }

    val isProductionMode = scannedBasket != null
    val scanCount = scannedBasket?.scanCount ?: 1

    // 延遲提交數量更新（避免 ANR）
    LaunchedEffect(quantityText) {
        if (quantityText != basket.quantity.toString()) {
            delay(300)  // 延遲 300ms
            val newQuantity = quantityText.toIntOrNull()
            val max = maxCapacity ?: Int.MAX_VALUE
            if (newQuantity != null && newQuantity in 1..max && newQuantity != basket.quantity) {
                onQuantityChange(newQuantity)
                showError = false
            } else if (quantityText.isNotEmpty()) {
                showError = true
            }
        }
    }

    // 卡片顏色邏輯
    val cardColors = when {
        // 生產模式：根據掃描次數決定
        isProductionMode && scanCount > 1 -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
        isProductionMode -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
        // 收貨模式：根據狀態決定
        !isValidStatus -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
        else -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors,
        border = if (!isValidStatus && !isProductionMode) {
            androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.error
            )
        } else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 收貨模式：狀態錯誤橫幅
            if (!isProductionMode && !isValidStatus) {
                StatusErrorBanner(status = basket.status)
            }

            // 生產模式：重複掃描提示
            if (isProductionMode && scanCount > 1) {
                DuplicateScanBanner(scanCount = scanCount)
            }

            // 標題行：UID、徽章和操作按鈕
            BasketHeaderRow(
                uid = basket.uid,
                scanCount = if (isProductionMode) scanCount else null,
                onResetCount = if (isProductionMode && scanCount > 1) onResetCount else null,
                onRemove = onRemove
            )

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // 產品信息
            basket.product?.let { product ->
                InfoRow(
                    label = "產品",
                    value = product.name,
                    valueColor = MaterialTheme.colorScheme.primary
                )
            }

            // 批次信息
            basket.batch?.let { batch ->
                InfoRow(
                    label = "批次",
                    value = batch.id
                )
            }

            // 生產日期
            basket.productionDate?.let { date ->
                InfoRow(
                    label = "生產日期",
                    value = date
                )
            }

            // 收貨模式：狀態顯示
            if (!isProductionMode) {
                StatusRow(status = basket.status)
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // 數量輸入區域（只接受數字）
            QuantityInputSection(
                quantity = basket.quantity,
                maxCapacity = maxCapacity,
                quantityText = quantityText,
                showError = showError,
                enabled = isValidStatus || isProductionMode,
                rssi = scannedBasket?.rssi,
                onQuantityTextChange = { newValue ->
                    // 只允許數字輸入
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        quantityText = newValue
                    }
                }
            )

            // 生產模式：時間信息
            if (isProductionMode && scannedBasket != null) {
                TimeInfoSection(
                    firstScannedTime = scannedBasket.firstScannedTime,
                    lastScannedTime = scannedBasket.lastScannedTime,
                    scanCount = scanCount
                )
            }
        }
    }
}

/**
 * 狀態錯誤橫幅（收貨模式）
 */
@Composable
private fun StatusErrorBanner(status: BasketStatus) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "狀態錯誤",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = getStatusErrorMessage(status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * 重複掃描橫幅（生產模式）
 */
@Composable
private fun DuplicateScanBanner(scanCount: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "重複掃描 $scanCount 次",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "請確認是否為同一個籃子",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * 籃子標題行
 */
@Composable
private fun BasketHeaderRow(
    uid: String,
    scanCount: Int?,
    onResetCount: (() -> Unit)?,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "籃子 UID",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 掃描次數徽章（生產模式）
                if (scanCount != null) {
                    ScanCountBadge(count = scanCount)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = uid,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 重置次數按鈕（生產模式，重複掃描時）
            if (onResetCount != null) {
                IconButton(
                    onClick = onResetCount,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "重置次數",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // 刪除按鈕
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 掃描次數徽章
 */
@Composable
private fun ScanCountBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (count > 1) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        modifier = Modifier.height(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (count > 1) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onPrimary
                }
            )
            Text(
                text = "${count}次",
                style = MaterialTheme.typography.labelSmall,
                color = if (count > 1) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onPrimary
                }
            )
        }
    }
}

/**
 * 狀態行（收貨模式）
 */
@Composable
private fun StatusRow(status: BasketStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "狀態",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BasketStatusColors.getStatusColor(status)
        ) {
            Text(
                text = getStatusText(status),
                style = MaterialTheme.typography.labelMedium,
                color = BasketStatusColors.getStatusTextColor(status),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * 數量輸入區域
 */
@Composable
private fun QuantityInputSection(
    quantity: Int,
    maxCapacity: Int?,
    quantityText: String,
    showError: Boolean,
    enabled: Boolean,
    rssi: Int?,
    onQuantityTextChange: (String) -> Unit
) {
    // 使用本地狀態緩存輸入
//    var localQuantityText by remember(quantity) { mutableStateOf(quantity.toString()) }
//    var localShowError by remember { mutableStateOf(false) }
//
//    // 延遲提交更新
//    LaunchedEffect(localQuantityText) {
//        delay(500) // 等待 500ms 用戶停止輸入
//
//        val newQuantity = localQuantityText.toIntOrNull()
//        val max = maxCapacity ?: Int.MAX_VALUE
//
//        if (newQuantity != null && newQuantity in 1..max) {
//            if (newQuantity != quantity) {
//                onQuantityTextChange(localQuantityText)
//            }
//            localShowError = false
//        } else {
//            localShowError = true
//        }
//    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "數量",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = quantityText,
                onValueChange = onQuantityTextChange,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number  // ⭐ 顯示數字鍵盤
                ),
                isError = showError,
                supportingText = if (showError) {
                    {
                        Text(
                            if (maxCapacity != null) {
                                "請輸入 1-$maxCapacity 之間的數字"
                            } else {
                                "請輸入大於 0 的數字"
                            }
                        )
                    }
                } else null,
                singleLine = true,
                enabled = enabled
            )
        }

        if (rssi != null) {
            Column {
                Text(
                    text = "信號強度",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$rssi dBm",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * 時間信息區域（生產模式）
 */
@Composable
private fun TimeInfoSection(
    firstScannedTime: Long,
    lastScannedTime: Long,
    scanCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "首次掃描",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTimestamp(firstScannedTime),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (scanCount > 1) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "最後掃描",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTimestamp(lastScannedTime),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * 通用信息行
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
    }
}

// ========== 輔助函數 ==========

private fun getStatusText(status: BasketStatus): String {
    return when (status) {
        BasketStatus.UNASSIGNED -> "未配置"
        BasketStatus.IN_PRODUCTION -> "生產中"
        BasketStatus.RECEIVED -> "已收貨"
        BasketStatus.IN_STOCK -> "在庫中"
        BasketStatus.SHIPPED -> "已出貨"
        BasketStatus.SAMPLING -> "抽樣中"
    }
}

private fun getStatusErrorMessage(status: BasketStatus): String {
    return when (status) {
        BasketStatus.UNASSIGNED -> "此籃子尚未配置產品，無法收貨"
        BasketStatus.RECEIVED -> "此籃子已經收貨，請勿重複操作"
        BasketStatus.IN_STOCK -> "此籃子已在庫中，無需再次收貨"
        BasketStatus.SHIPPED -> "此籃子已出貨，無法收貨"
        BasketStatus.SAMPLING -> "此籃子正在抽樣檢驗中"
        else -> "此籃子狀態不符，只能收貨「生產中」的籃子"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}