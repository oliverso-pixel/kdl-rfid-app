package com.kdl.rfidinventory.presentation.ui.screens.production

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.presentation.ui.components.BasketCard
import com.kdl.rfidinventory.presentation.ui.components.BasketCardMode
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar
import com.kdl.rfidinventory.presentation.ui.components.ProductSelectionCard
import com.kdl.rfidinventory.presentation.ui.components.ProductSelectionDialog
import com.kdl.rfidinventory.presentation.ui.components.ProductionStatistics
import com.kdl.rfidinventory.presentation.ui.components.ScanSettingsCard
import com.kdl.rfidinventory.util.ScanMode
import com.kdl.rfidinventory.util.ScanModeAvailability
import com.kdl.rfidinventory.util.ScanType

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProductionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    // 錯誤提示
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
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
                    title = { Text("生產模式") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 清空籃子列表按鈕（只在有籃子時顯示）
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

                        // 完全重置按鈕（產品或籃子存在時顯示）
                        if (uiState.selectedProduct != null || uiState.scannedBaskets.isNotEmpty()) {
                            IconButton(onClick = { viewModel.resetAll() }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "完全重置",
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
                    text = { Text("確認提交 (${uiState.scannedBaskets.size})") },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = { viewModel.showConfirmDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 步驟指示器
            ProductionStepIndicator(
                currentStep = when {
                    uiState.selectedProduct == null -> 1
                    uiState.selectedBatch == null -> 2
                    uiState.scannedBaskets.isEmpty() -> 3
                    else -> 4
                }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 步驟 1: 選擇產品
                item {
                    ProductSelectionCard(
                        selectedProduct = uiState.selectedProduct,
                        onSelectProduct = { viewModel.showProductDialog() }
                    )
                }

                // 步驟 2: 選擇批次
                if (uiState.selectedProduct != null) {
                    item {
                        BatchSelectionCard(
                            selectedBatch = uiState.selectedBatch,
                            onSelectBatch = {
                                // 批次會自動在選擇產品後彈出
                            }
                        )
                    }
                }

                // 步驟 3: 掃描設置
                if (uiState.selectedProduct != null && uiState.selectedBatch != null) {
                    item {
                        ScanSettingsCard(
                            scanMode = uiState.scanMode,
                            isScanning = scanState.isScanning,
                            scanType = scanState.scanType,
                            totalScanCount = uiState.totalScanCount,
                            basketCount = uiState.scannedBaskets.size,
                            onModeChange = { viewModel.setScanMode(it) },
                            onToggleScan = { viewModel.toggleScanFromButton() },
                            onClearBaskets = { viewModel.clearBaskets() }
                        )
                    }
                }

                // 步驟 4: 已掃描籃子列表
                if (uiState.scannedBaskets.isNotEmpty()) {
                    item {
                        BasketListHeader(
                            basketCount = uiState.scannedBaskets.size,
                            totalScanCount = uiState.totalScanCount
                        )
                    }

                    items(
                        items = uiState.scannedBaskets,
                        key = { it.uid }
                    ) { scannedBasket ->
                        BasketCard(
                            basket = Basket(
                                uid = scannedBasket.uid,
                                product = uiState.selectedProduct,
                                batch = uiState.selectedBatch,
                                warehouseId = null,
                                quantity = scannedBasket.quantity,
                                productionDate = uiState.selectedBatch?.productionDate,
                                expireDate = null,
                                status = BasketStatus.IN_PRODUCTION,
                                updateBy = null
                            ),
                            mode = BasketCardMode.PRODUCTION,
                            scannedBasket = scannedBasket,
                            maxCapacity = uiState.selectedProduct?.maxBasketCapacity,
                            onQuantityChange = { newQuantity ->
                                viewModel.updateBasketQuantity(scannedBasket.uid, newQuantity)
                            },
                            onRemove = { viewModel.removeBasket(scannedBasket.uid) },
                            onResetCount = { viewModel.resetBasketScanCount(scannedBasket.uid) }
                        )
                    }
                }
            }
        }
    }

    // 產品選擇對話框
    if (uiState.showProductDialog) {
        ProductSelectionDialog(
            products = uiState.products,
            searchQuery = uiState.productSearchQuery,
            onSearchQueryChange = { viewModel.updateProductSearchQuery(it) },
            onDismiss = {
                viewModel.clearProductSearchQuery()
                viewModel.dismissDialog()
            },
            onProductSelected = {
                viewModel.clearProductSearchQuery()
                viewModel.selectProduct(it)
            }
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

    // 確認對話框
    if (uiState.showConfirmDialog) {
        ConfirmProductionDialog(
            product = uiState.selectedProduct,
            batch = uiState.selectedBatch,
            baskets = uiState.scannedBaskets,
            onDismiss = { viewModel.dismissDialog() },
            onConfirm = { viewModel.submitProduction() }
        )
    }
}

@Composable
private fun ProductionStepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StepItem(number = 1, label = "選擇產品", isActive = currentStep >= 1, isComplete = currentStep > 1)
        StepItem(number = 2, label = "選擇批次", isActive = currentStep >= 2, isComplete = currentStep > 2)
        StepItem(number = 3, label = "掃描籃子", isActive = currentStep >= 3, isComplete = currentStep > 3)
        StepItem(number = 4, label = "確認提交", isActive = currentStep >= 4, isComplete = false)
    }
}

@Composable
private fun StepItem(
    number: Int,
    label: String,
    isActive: Boolean,
    isComplete: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        isComplete -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isComplete) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BasketListHeader(
    basketCount: Int,
    totalScanCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "已掃描籃子 ($basketCount)",
            style = MaterialTheme.typography.titleMedium
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "總計: $totalScanCount 次",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun BatchSelectionCard(
    selectedBatch: Batch?,
    onSelectBatch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedBatch != null) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedBatch.id,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "生產日期: ${selectedBatch.productionDate}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "剩餘: ${selectedBatch.remainingQuantity} / ${selectedBatch.totalQuantity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "等待選擇批次...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ScanSettingsCard(
    scanMode: ScanMode,
    isScanning: Boolean,
    scanType: ScanType,
    totalScanCount: Int,
    basketCount: Int,
    onModeChange: (ScanMode) -> Unit,
    onToggleScan: () -> Unit,
    onClearBaskets: () -> Unit
) {
    ScanSettingsCard(
        scanMode = scanMode,
        isScanning = isScanning,
        scanType = scanType,
        onModeChange = onModeChange,
        onToggleScan = onToggleScan,
        availability = ScanModeAvailability.BOTH,
        statisticsContent = {
            ProductionStatistics(
                totalScanCount = totalScanCount,
                basketCount = basketCount,
                isScanning = isScanning,
                onClearBaskets = onClearBaskets
            )
        },
        helpText = when (scanMode) {
            ScanMode.SINGLE -> "• RFID：點擊按鈕進行近距離掃描\n• 條碼：使用實體掃碼槍觸發\n• 單次模式：再按一次實體按鍵可取消"
            ScanMode.CONTINUOUS -> "• 點擊按鈕開始連續掃描\n• 再次點擊或使用實體按鍵停止\n• 也可使用實體按鍵控制"
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
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(batches) { batch ->
                    Card(
                        onClick = { onBatchSelected(batch) },
                        modifier = Modifier.fillMaxWidth()
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
                            Spacer(modifier = Modifier.height(4.dp))
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

@Composable
private fun ConfirmProductionDialog(
    product: Product?,
    batch: Batch?,
    baskets: List<ScannedBasket>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("確認生產配置") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                product?.let {
                    Text("產品: ${it.name}", style = MaterialTheme.typography.bodyLarge)
                }
                batch?.let {
                    Text("批次: ${it.id}", style = MaterialTheme.typography.bodyMedium)
                }
                Divider()
                Text(
                    "共 ${baskets.size} 個籃子",
                    style = MaterialTheme.typography.titleSmall
                )
                val totalQuantity = baskets.sumOf { it.quantity }
                Text(
                    "總數量: $totalQuantity",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("確認提交")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}