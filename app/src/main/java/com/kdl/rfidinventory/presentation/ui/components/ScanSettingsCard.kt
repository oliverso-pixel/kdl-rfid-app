package com.kdl.rfidinventory.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kdl.rfidinventory.presentation.ui.theme.ScanModeColors
import com.kdl.rfidinventory.util.ScanMode
import com.kdl.rfidinventory.util.ScanModeAvailability
import com.kdl.rfidinventory.util.ScanType

/**
 * 統一的掃描設置卡片
 * 用於生產模式和收貨模式
 */
@Composable
fun ScanSettingsCard(
    scanMode: ScanMode,
    isScanning: Boolean,
    scanType: ScanType,
    onModeChange: (ScanMode) -> Unit,
    onToggleScan: () -> Unit,
    modifier: Modifier = Modifier,
    isValidating: Boolean = false,
    availability: ScanModeAvailability = ScanModeAvailability.BOTH,
    statisticsContent: @Composable (() -> Unit)? = null,
    helpText: String = "• RFID：點擊按鈕進行掃描\n• 條碼：使用實體掃碼槍觸發\n• 單次模式：再按一次實體按鍵可取消"
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isScanning && scanType == ScanType.BARCODE ->
                    ScanModeColors.getBarcodeContainerColor()
                isScanning && scanType == ScanType.RFID ->
                    ScanModeColors.getRFIDContainerColor()
                else -> MaterialTheme.colorScheme.surface
            }
        ),
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

            // 掃描模式選擇器
            ScanModeSelector(
                currentMode = scanMode,
                onModeChange = onModeChange,
                enabled = !isScanning,
                availability = availability
            )

            // 掃描狀態和控制
            ScanStatusAndControls(
                isScanning = isScanning,
                isValidating = isValidating,
                scanType = scanType,
                scanMode = scanMode,
                onToggleScan = onToggleScan,
                helpText = helpText
            )

            // 統計信息（由調用方提供）
            statisticsContent?.invoke()
        }
    }
}

/**
 * 掃描狀態和控制按鈕
 */
@Composable
private fun ScanStatusAndControls(
    isScanning: Boolean,
    isValidating: Boolean,
    scanType: ScanType,
    scanMode: ScanMode,
    onToggleScan: () -> Unit,
    helpText: String
) {
    when {
        isValidating -> {
            // 驗證中
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = "驗證籃子狀態...",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        isScanning && scanType == ScanType.BARCODE -> {
            // 條碼掃描中 - 顯示取消按鈕
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "等待條碼掃描...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "請使用掃碼槍掃描QR/條碼",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // 取消按鈕
                OutlinedButton(
                    onClick = onToggleScan,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("取消掃描（或再按一次實體按鍵）")
                }
            }
        }
        isScanning && scanType == ScanType.RFID -> {
            // RFID 掃描中
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RFID 掃描中...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = when (scanMode) {
                                ScanMode.SINGLE -> "近距離掃描（掃到後自動停止）"
                                ScanMode.CONTINUOUS -> "連續掃描中"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // 停止按鈕
                Button(
                    onClick = onToggleScan,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("停止掃描")
                }
            }
        }
        else -> {
            // 未掃描狀態：顯示操作提示和掃描按鈕
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // RFID 掃描按鈕
                Button(
                    onClick = onToggleScan,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = when (scanMode) {
                            ScanMode.SINGLE -> Icons.Default.Nfc
                            ScanMode.CONTINUOUS -> Icons.Default.Radar
                        },
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when (scanMode) {
                            ScanMode.SINGLE -> "RFID 掃描（近距離）"
                            ScanMode.CONTINUOUS -> "開始連續掃描"
                        }
                    )
                }
            }
        }
    }
}

/**
 * 生產模式統計信息
 */
@Composable
fun ProductionStatistics(
    totalScanCount: Int,
    basketCount: Int,
    isScanning: Boolean,
    onClearBaskets: () -> Unit
) {
    if (totalScanCount > 0 || basketCount > 0) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "籃子數量",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "$basketCount 個",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Divider(
                        modifier = Modifier
                            .height(40.dp)
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "掃描次數",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "$totalScanCount 次",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (basketCount > 0) {
                    IconButton(
                        onClick = onClearBaskets,
                        enabled = !isScanning
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "清空列表",
                            tint = if (isScanning) {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 收貨模式統計信息
 */
@Composable
fun ReceivingStatistics(
    basketCount: Int,
    totalQuantity: Int,
    validBasketCount: Int,
    isScanning: Boolean,
    onClearBaskets: () -> Unit
) {
    if (basketCount > 0 || totalQuantity > 0) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "有效籃子",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (validBasketCount == basketCount) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            text = "$validBasketCount / $basketCount",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (validBasketCount == basketCount) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }

                    Divider(
                        modifier = Modifier
                            .height(40.dp)
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )

                    Column {
                        Text(
                            text = "總數量",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$totalQuantity",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (basketCount > 0) {
                    IconButton(
                        onClick = onClearBaskets,
                        enabled = !isScanning
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "清空列表",
                            tint = if (isScanning) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
        }
    }
}