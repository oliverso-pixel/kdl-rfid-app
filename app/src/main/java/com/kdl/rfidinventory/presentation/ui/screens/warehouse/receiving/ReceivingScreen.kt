package com.kdl.rfidinventory.presentation.ui.screens.warehouse.receiving

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.repository.Warehouse
import com.kdl.rfidinventory.presentation.ui.components.BasketCard
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar
import com.kdl.rfidinventory.presentation.ui.components.ReceivingStatistics
import com.kdl.rfidinventory.presentation.ui.components.ScanSettingsCard
import com.kdl.rfidinventory.util.ScanMode
import com.kdl.rfidinventory.util.ScanType

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivingScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReceivingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    // ⭐ 計算是否可以提交
    val canSubmit = remember(uiState.scannedBaskets) {
        uiState.scannedBaskets.isNotEmpty() &&
                uiState.scannedBaskets.all { it.basket.status == BasketStatus.IN_PRODUCTION }
    }

    // 錯誤提示
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    // 成功提示
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("倉庫收貨") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 清空籃子列表按鈕
                        if (uiState.scannedBaskets.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearBaskets() },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "清空籃子列表",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                ConnectionStatusBar(networkState = networkState)
            }
        },
        floatingActionButton = {
            if (uiState.scannedBaskets.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = {
                        Text(
                            if (canSubmit) {
                                "確認收貨 (${uiState.scannedBaskets.size})"
                            } else {
                                "無法收貨 (有錯誤狀態)"
                            }
                        )
                    },
                    icon = {
                        Icon(
                            if (canSubmit) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = null
                        )
                    },
                    onClick = { viewModel.showConfirmDialog() },
                    containerColor = if (canSubmit) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (canSubmit) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    WarehouseSelectionCard(
                        selectedWarehouse = uiState.selectedWarehouse,
                        onSelectWarehouse = { viewModel.showWarehouseDialog() }
                    )
                }

                // 掃描設置
                item {
                    ScanSettingsCard(
                        scanMode = uiState.scanMode,
                        isScanning = scanState.isScanning,  // ⭐ 從 scanState 獲取
                        scanType = scanState.scanType,
                        isValidating = uiState.isValidating,
                        basketCount = uiState.scannedBaskets.size,
                        totalQuantity = uiState.totalQuantity,
                        validBasketCount = uiState.scannedBaskets.count { it.basket.status == BasketStatus.IN_PRODUCTION },
                        onModeChange = { viewModel.setScanMode(it) },
                        onToggleScan = { viewModel.toggleScanFromButton() },
                        onClearBaskets = { viewModel.clearBaskets() }
                    )
                }

                // 已掃描籃子列表
                if (uiState.scannedBaskets.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "已掃描籃子 (${uiState.scannedBaskets.size})",
                                style = MaterialTheme.typography.titleMedium
                            )

                            // ⭐ 狀態統計
                            val validCount = uiState.scannedBaskets.count {
                                it.basket.status == BasketStatus.IN_PRODUCTION
                            }
                            val invalidCount = uiState.scannedBaskets.size - validCount

                            if (invalidCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "$invalidCount 個錯誤",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ⭐ 使用唯一 ID 作為 key
                    items(
                        items = uiState.scannedBaskets,
                        key = { item -> item.id }
                    ) { item ->
                        BasketCard(
                            basket = item.basket,
                            isValidStatus = item.basket.status == BasketStatus.IN_PRODUCTION,
                            onQuantityChange = { newQuantity ->
                                viewModel.updateBasketQuantity(item.basket.uid, newQuantity)
                            },
                            onRemove = { viewModel.removeBasket(item.basket.uid) }
                        )
                    }
                }
            }
        }
    }

    // 倉庫選擇對話框
    if (uiState.showWarehouseDialog) {
        WarehouseSelectionDialog(
            warehouses = uiState.warehouses,
            selectedWarehouse = uiState.selectedWarehouse,
            onSelect = { viewModel.selectWarehouse(it) },
            onDismiss = { viewModel.dismissWarehouseDialog() }
        )
    }

    // 確認對話框
    if (uiState.showConfirmDialog) {
        ConfirmReceivingDialog(
            items = uiState.scannedBaskets,
            warehouse = uiState.selectedWarehouse!!,  // ⭐ 添加倉庫信息
            onDismiss = { viewModel.dismissConfirmDialog() },
            onConfirm = { viewModel.confirmReceiving() }
        )
    }
}

@Composable
private fun ScanSettingsCard(
    scanMode: ScanMode,
    isScanning: Boolean,
    scanType: ScanType,
    isValidating: Boolean,
    basketCount: Int,
    totalQuantity: Int,
    validBasketCount: Int,
    onModeChange: (ScanMode) -> Unit,
    onToggleScan: () -> Unit,
    onClearBaskets: () -> Unit
) {
    ScanSettingsCard(
        scanMode = scanMode,
        isScanning = isScanning,
        scanType = scanType,
        isValidating = isValidating,
        onModeChange = onModeChange,
        onToggleScan = onToggleScan,
        statisticsContent = {
            ReceivingStatistics(
                basketCount = basketCount,
                totalQuantity = totalQuantity,
                validBasketCount = validBasketCount,
                isScanning = isScanning,
                onClearBaskets = onClearBaskets
            )
        },
        helpText = "• 只能收貨「生產中」狀態的籃子\n• RFID：點擊按鈕進行掃描\n• 條碼：使用實體掃碼槍觸發\n• 單次模式：再按一次實體按鍵可取消"
    )
}

@Composable
private fun ConfirmReceivingDialog(
    items: List<ScannedBasketItem>,
    warehouse: Warehouse,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val baskets = items.map { it.basket }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("確認收貨") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ⭐ 倉庫信息
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warehouse,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Column {
                            Text(
                                text = warehouse.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = warehouse.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Text(
                    "共 ${baskets.size} 個籃子",
                    style = MaterialTheme.typography.titleSmall
                )

                val totalQuantity = baskets.sumOf { it.quantity }
                Text(
                    "總數量: $totalQuantity",
                    style = MaterialTheme.typography.bodyMedium
                )

                Divider()

                Text(
                    "收貨後籃子狀態將變更為「在庫中」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("確認收貨")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun WarehouseSelectionCard(
    selectedWarehouse: Warehouse?,
    onSelectWarehouse: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selectedWarehouse != null) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warehouse,
                        contentDescription = null,
                        tint = if (selectedWarehouse != null) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = "收貨倉庫",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selectedWarehouse != null) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (selectedWarehouse != null) {
                    Text(
                        text = selectedWarehouse.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = selectedWarehouse.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = "請選擇倉庫位置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Button(
                onClick = onSelectWarehouse,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedWarehouse != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            ) {
                Text(if (selectedWarehouse != null) "更換" else "選擇")
            }
        }
    }
}

@Composable
private fun WarehouseSelectionDialog(
    warehouses: List<Warehouse>,
    selectedWarehouse: Warehouse?,
    onSelect: (Warehouse) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warehouse,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("選擇收貨倉庫") },
        text = {
            if (warehouses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(warehouses) { warehouse ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelect(warehouse) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (warehouse.id == selectedWarehouse?.id) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = warehouse.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = warehouse.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (warehouse.id == selectedWarehouse?.id) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "已選擇",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}
