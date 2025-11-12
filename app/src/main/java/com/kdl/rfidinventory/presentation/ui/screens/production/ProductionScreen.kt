package com.kdl.rfidinventory.presentation.ui.screens.production

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdl.rfidinventory.data.model.Batch
import com.kdl.rfidinventory.data.model.Product
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar
import com.kdl.rfidinventory.presentation.ui.components.QuantityInputDialog
import com.kdl.rfidinventory.presentation.ui.components.ScanModeSelector

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProductionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()

    // 錯誤提示
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // 顯示 Snackbar
            viewModel.clearError()
        }
    }

    // 成功提示
    uiState.successMessage?.let { message ->
        LaunchedEffect(message) {
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("生產模式") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                ConnectionStatusBar(networkState = networkState)
            }
        },
        floatingActionButton = {
            if (!uiState.isScanning) {
                ExtendedFloatingActionButton(
                    text = { Text("開始掃描") },
                    icon = {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    },
                    onClick = { viewModel.startScanning() }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 掃描模式選擇器
            ScanModeSelector(
                currentMode = uiState.scanMode,
                onModeChange = { viewModel.setScanMode(it) }
            )

            // 掃描狀態顯示
            if (uiState.isScanning) {
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
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "正在掃描...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.stopScanning() }) {
                            Text("停止")
                        }
                    }
                }
            }

            // 已掃描的 UID
            uiState.scannedUid?.let { uid ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "已掃描籃子",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = uid,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // 說明文字
            Text(
                text = "1. 選擇掃描模式\n2. 點擊「開始掃描」按鈕\n3. 掃描籃子 RFID 標籤\n4. 選擇產品和批次\n5. 輸入數量完成配置",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 產品選擇對話框
    if (uiState.showProductDialog) {
        ProductSelectionDialog(
            products = uiState.products,
            onDismiss = { viewModel.dismissDialog() },
            onProductSelected = { viewModel.selectProduct(it) }
        )
    }

    // 批次選擇對話框
    if (uiState.showBatchDialog) {
        BatchSelectionDialog(
            batches = uiState.batches,
            onDismiss = { viewModel.dismissDialog() },
            onBatchSelected = { viewModel.selectBatch(it) }
        )
    }

    // 數量輸入對話框
    if (uiState.showQuantityDialog) {
        val maxQuantity = uiState.selectedProduct?.maxBasketCapacity ?: 0
        QuantityInputDialog(
            title = "輸入數量",
            maxQuantity = maxQuantity,
            onDismiss = { viewModel.dismissDialog() },
            onConfirm = { viewModel.confirmQuantity(it) }
        )
    }
}

@Composable
private fun ProductSelectionDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onProductSelected: (Product) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇產品") },
        text = {
            LazyColumn {
                items(products) { product ->
                    Card(
                        onClick = { onProductSelected(product) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "最大容量: ${product.maxBasketCapacity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun BatchSelectionDialog(
    batches: List<Batch>,
    onDismiss: () -> Unit,
    onBatchSelected: (Batch) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇批次") },
        text = {
            LazyColumn {
                items(batches) { batch ->
                    Card(
                        onClick = { onBatchSelected(batch) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = batch.id,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "生產日期: ${batch.productionDate}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "剩餘: ${batch.remainingQuantity} / ${batch.totalQuantity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}