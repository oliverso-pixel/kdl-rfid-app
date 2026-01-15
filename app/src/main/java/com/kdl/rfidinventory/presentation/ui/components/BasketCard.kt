package com.kdl.rfidinventory.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.model.ScannedBasket
import com.kdl.rfidinventory.data.model.getBasketStatusText
import com.kdl.rfidinventory.data.model.getStatusErrorMessage
import com.kdl.rfidinventory.presentation.ui.screens.warehouse.inventory.InventoryItemStatus
import com.kdl.rfidinventory.presentation.ui.theme.BasketStatusColors
import kotlinx.coroutines.delay

/**
 * 卡片顯示模式
 */
enum class BasketCardMode {
    PRODUCTION,   // 生產模式：顯示掃描次數、RSSI、時間
    RECEIVING,    // 收貨模式：顯示狀態驗證、錯誤提示
    INVENTORY,     // 盤點模式：顯示 EXTRA/SCANNED 狀態、編輯/追蹤按鈕
    CLEAR
}

/**
 * 統一的籃子卡片組件
 *
 * 使用場景配置：
 * - 生產模式：showScannedInfo = true, showStatus = false
 * - 收貨模式：showScannedInfo = false, showStatus = true, isValidStatus = ?
 * - 盤點模式：showScannedInfo = false, showStatus = true, actions.showEdit = true
 */
@Composable
fun BasketCard(
    basket: Basket,
    modifier: Modifier = Modifier,
    mode: BasketCardMode = BasketCardMode.RECEIVING,

    // 生產模式專用
    scannedBasket: ScannedBasket? = null,
    onResetCount: (() -> Unit)? = null,

    // 收貨模式專用
    isValidStatus: Boolean = true,

    // 盤點模式專用
    inventoryStatus: InventoryItemStatus? = null,
    scanCount: Int = 0,
    onEdit: (() -> Unit)? = null,
    onTrack: (() -> Unit)? = null,

    // 共用
    rssi: Int? = scannedBasket?.rssi,
    firstScannedTime: Long? = scannedBasket?.firstScannedTime,
    lastScannedTime: Long? = scannedBasket?.lastScannedTime,
    maxCapacity: Int? = null,
    onQuantityChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    var quantityText by remember(basket.quantity) { mutableStateOf(basket.quantity.toString()) }
    var showError by remember { mutableStateOf(false) }

    // 延遲提交數量更新
    LaunchedEffect(quantityText) {
        if (quantityText != basket.quantity.toString()) {
            delay(300)
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

    // 計算數量輸入框的 enabled 狀態
    val quantityEnabled = when (mode) {
        BasketCardMode.PRODUCTION -> true
        BasketCardMode.RECEIVING -> isValidStatus
        BasketCardMode.INVENTORY -> inventoryStatus != InventoryItemStatus.EXTRA
        BasketCardMode.CLEAR -> false
    }

    // 計算數量輸入框的 visible 狀態
    val quantityVisible = when (mode) {
        BasketCardMode.PRODUCTION -> true
        BasketCardMode.RECEIVING -> true
        BasketCardMode.INVENTORY -> inventoryStatus != InventoryItemStatus.EXTRA
        BasketCardMode.CLEAR -> true
    }

    // 根據模式決定卡片顏色
    val cardColors = when (mode) {
        BasketCardMode.PRODUCTION -> {
            val count = scannedBasket?.scanCount ?: 1
            if (count > 1) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            } else {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            }
        }
        BasketCardMode.RECEIVING -> {
            if (!isValidStatus) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            } else {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        BasketCardMode.INVENTORY -> {
            when (inventoryStatus) {
                InventoryItemStatus.SCANNED -> CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                )
                InventoryItemStatus.EXTRA -> CardDefaults.cardColors(
                    containerColor = Color(0xFFFF9800).copy(alpha = 0.1f)
                )
                else -> CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }
        }
        BasketCardMode.CLEAR -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    }

    // 邊框顏色
    val borderColor = when (mode) {
        BasketCardMode.RECEIVING -> {
            if (!isValidStatus) MaterialTheme.colorScheme.error else Color.Transparent
        }
        BasketCardMode.INVENTORY -> {
            when (inventoryStatus) {
                InventoryItemStatus.SCANNED -> Color(0xFF4CAF50)
                InventoryItemStatus.EXTRA -> Color(0xFFFF9800)
                else -> Color.Transparent
            }
        }
        else -> Color.Transparent
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors,
        border = if (borderColor != Color.Transparent) {
            androidx.compose.foundation.BorderStroke(width = 2.dp, color = borderColor)
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 收貨模式：狀態錯誤橫幅
            if (mode == BasketCardMode.RECEIVING && !isValidStatus) {
                StatusErrorBanner(status = basket.status)
            }

            // 生產模式：重複掃描提示
            if (mode == BasketCardMode.PRODUCTION && (scannedBasket?.scanCount ?: 1) > 1) {
                DuplicateScanBanner(scanCount = scannedBasket!!.scanCount)
            }

            // 盤點模式：額外項提示
            if (mode == BasketCardMode.INVENTORY && inventoryStatus == InventoryItemStatus.EXTRA) {
                ExtraItemBanner()
            }

            // 標題行：UID、徽章和操作按鈕
            BasketHeaderRow(
                uid = basket.uid,
                mode = mode,
                scanCount = when (mode) {
                    BasketCardMode.PRODUCTION -> scannedBasket?.scanCount
                    BasketCardMode.INVENTORY -> if (scanCount > 1) scanCount else null
                    else -> null
                },
                inventoryStatus = inventoryStatus,
                onResetCount = if (mode == BasketCardMode.PRODUCTION && (scannedBasket?.scanCount
                        ?: 1) > 1
                ) {
                    onResetCount
                } else null,
                onEdit = onEdit,
                onTrack = onTrack,
                onRemove = onRemove
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 產品信息
            basket.product?.let { product ->
                InfoRow(
                    label = "產品",
                    value = product.name,
                    valueColor = MaterialTheme.colorScheme.primary
                )
            } ?: run {
                if (mode == BasketCardMode.INVENTORY) {
                    InfoRow(
                        label = "產品",
                        value = "未分配產品",
                        valueColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 批次信息
            basket.batch?.let { batch ->
                InfoRow(label = "批次", value = batch.id)
            }

            // 生產日期
            basket.productionDate?.let { date ->
                InfoRow(label = "生產日期", value = date)
            }

            //  過期日期

            // 收貨/盤點模式：狀態顯示
            if (mode == BasketCardMode.RECEIVING ||
                mode == BasketCardMode.INVENTORY ||
                mode == BasketCardMode.CLEAR
            ) {
                StatusRow(status = basket.status)
            }

            // 數量輸入區域
            if (quantityVisible) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 數量輸入區域
                QuantityInputSection(
                    quantity = basket.quantity,
                    maxCapacity = maxCapacity,
                    quantityText = quantityText,
                    showError = showError,
                    enabled = quantityEnabled,
                    rssi = rssi,
                    onQuantityTextChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            quantityText = newValue
                        }
                    }
                )
            }

            // 時間信息（生產模式 或 盤點模式有掃描次數時）⚠️
            if ((mode == BasketCardMode.PRODUCTION && scannedBasket != null) ||
                (mode == BasketCardMode.INVENTORY && scanCount > 1 && firstScannedTime != null && lastScannedTime != null)) {
                TimeInfoSection(
                    firstScannedTime = firstScannedTime ?: scannedBasket!!.firstScannedTime,
                    lastScannedTime = lastScannedTime ?: scannedBasket!!.lastScannedTime,
                    scanCount = scanCount.takeIf { it > 0 } ?: scannedBasket!!.scanCount
                )
            }

            if (rssi != null) {
                RSSISection(rssi)
            }
        }
    }
}
// ========== 橫幅組件 ==========

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
 * 額外項橫幅（盤點模式）
 */
@Composable
private fun ExtraItemBanner() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF9800).copy(alpha = 0.2f)
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
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "額外項（不在原清單中）",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFF9800)
            )
        }
    }
}

/**
 * 籃子標題行 - 支持3種模式
 */
@Composable
private fun BasketHeaderRow(
    uid: String,
    mode: BasketCardMode,
    scanCount: Int?,
    inventoryStatus: InventoryItemStatus?,
    onResetCount: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onTrack: (() -> Unit)?,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // UID 和徽章
        Column(modifier = Modifier.weight(1f)) {
            // 操作按鈕組
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 掃描次數徽章（生產/盤點模式）
                if (scanCount != null && scanCount > 0) {
                    ScanCountBadge(count = scanCount)
                }

                // 盤點狀態徽章
                if (mode == BasketCardMode.INVENTORY && inventoryStatus != null) {
                    InventoryStatusBadge(status = inventoryStatus)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "籃子 UID",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 編輯按鈕（僅盤點模式的額外項）
                if (mode == BasketCardMode.INVENTORY &&
                    inventoryStatus == InventoryItemStatus.EXTRA &&
                    onEdit != null) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "編輯",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // TODO 追蹤按鈕（僅盤點模式，預留功能）
                if (mode == BasketCardMode.INVENTORY && onTrack != null) {
                    IconButton(
                        onClick = onTrack,
                        modifier = Modifier.size(36.dp),
                        enabled = false  // 暫時禁用
                    ) {
                        Icon(
                            Icons.Default.GpsFixed,
                            contentDescription = "追蹤",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

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

                // 刪除按鈕（所有模式）
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = uid,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 盤點狀態徽章
 */
@Composable
private fun InventoryStatusBadge(status: InventoryItemStatus) {
    val (text, color) = when (status) {
        InventoryItemStatus.PENDING -> "待掃" to MaterialTheme.colorScheme.onSurfaceVariant
        InventoryItemStatus.SCANNED -> "已掃" to Color(0xFF4CAF50)
        InventoryItemStatus.EXTRA -> "額外" to Color(0xFFFF9800)
    }

    Box(
        modifier = Modifier
            .height(20.dp)
            .background(
                color = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
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
                text = getBasketStatusText(status),
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
                    keyboardType = KeyboardType.Number  // 顯示數字鍵盤
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
    }
}

@Composable
private fun RSSISection(rssi: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "信號強度",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$rssi dBm",
                style = MaterialTheme.typography.bodySmall
            )
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
    valueColor: Color = MaterialTheme.colorScheme.onSurface
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

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}