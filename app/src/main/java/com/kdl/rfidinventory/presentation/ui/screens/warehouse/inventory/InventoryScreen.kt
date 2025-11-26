package com.kdl.rfidinventory.presentation.ui.screens.warehouse.inventory

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
import com.kdl.rfidinventory.data.repository.Warehouse
import com.kdl.rfidinventory.data.repository.getDaysUntilExpiry
import com.kdl.rfidinventory.presentation.ui.components.BasketCard
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar
import com.kdl.rfidinventory.presentation.ui.components.InventoryStatistics
import com.kdl.rfidinventory.presentation.ui.components.ScanSettingsCard

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

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
                    title = { Text("倉庫盤點") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (uiState.selectedProduct != null && uiState.scannedBaskets.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearBaskets() },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "清空掃描列表",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        IconButton(onClick = { viewModel.exportInventory() }) {
                            Icon(Icons.Default.Download, contentDescription = "匯出")
                        }

                        IconButton(onClick = { viewModel.loadInventory() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "重新整理")
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
                // 倉庫選擇
                item {
                    WarehouseSelectionCard(
                        selectedWarehouse = uiState.selectedWarehouse,
                        onSelectWarehouse = { viewModel.showWarehouseDialog() }
                    )
                }

                // 統計
                if (uiState.selectedWarehouse != null) {
                    item {
                        StatisticsCard(statistics = uiState.statistics)
                    }
                }

                // 產品列表或掃描界面
                if (uiState.selectedProduct == null) {
                    // 產品列表
                    if (uiState.productGroups.isNotEmpty()) {
                        item {
                            Text(
                                text = "產品列表 (點擊進行盤點)",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(
                            items = uiState.productGroups,
                            key = { it.product.id }
                        ) { group ->
                            ProductGroupCard(
                                group = group,
                                onClick = { viewModel.selectProduct(group.product) }
                            )
                        }
                    }
                } else {
                    // 掃描界面
                    item {
                        SelectedProductCard(
                            product = uiState.selectedProduct!!,
                            onDeselect = { viewModel.deselectProduct() }
                        )
                    }

                    item {
                        ScanSettingsCard(
                            scanMode = uiState.scanMode,
                            isScanning = scanState.isScanning,
                            scanType = scanState.scanType,
                            isValidating = uiState.isValidating,
                            onModeChange = { viewModel.setScanMode(it) },
                            onToggleScan = { viewModel.toggleScanFromButton() },
                            statisticsContent = {
                                InventoryStatistics(
                                    scannedCount = uiState.scannedBaskets.size,
                                    isScanning = scanState.isScanning,
                                    onClearBaskets = { viewModel.clearBaskets() }
                                )
                            },
                            helpText = "• 掃描以確認籃子在庫\n• RFID：點擊按鈕掃描\n• 條碼：使用實體掃碼槍\n• 單次模式：再按一次可取消"
                        )
                    }

                    if (uiState.scannedBaskets.isNotEmpty()) {
                        item {
                            Text(
                                text = "已確認籃子 (${uiState.scannedBaskets.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(
                            items = uiState.scannedBaskets,
                            key = { it.id }
                        ) { item ->
                            BasketCard(
                                basket = item.basket,
                                maxCapacity = item.basket.product?.maxBasketCapacity,
                                onQuantityChange = {},
                                onRemove = { viewModel.removeBasket(item.basket.uid) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showWarehouseDialog) {
        WarehouseSelectionDialog(
            warehouses = uiState.warehouses,
            selectedWarehouse = uiState.selectedWarehouse,
            onSelect = { viewModel.selectWarehouse(it) },
            onDismiss = { viewModel.dismissWarehouseDialog() }
        )
    }
}

@Composable
private fun StatisticsCard(statistics: InventoryStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatisticItem(
                    label = "總籃數",
                    value = statistics.totalBaskets.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatisticItem(
                    label = "總數量",
                    value = statistics.totalQuantity.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            if (statistics.expiringCount > 0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "${statistics.expiringCount} 個籃子即將到期（7天內）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun ProductGroupCard(
    group: ProductInventoryGroup,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
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
                    text = group.product.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${group.totalBaskets} 籃 • ${group.totalQuantity} 個",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (group.expiringCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "${group.expiringCount} 即將到期",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectedProductCard(
    product: com.kdl.rfidinventory.data.model.Product,
    onDeselect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
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
                    text = "當前盤點產品",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            IconButton(onClick = onDeselect) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "返回產品列表",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
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
                        text = "盤點倉庫",
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
        title = { Text("選擇盤點倉庫") },
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
                                Text(
                                    text = warehouse.name,
                                    style = MaterialTheme.typography.titleMedium
                                )

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