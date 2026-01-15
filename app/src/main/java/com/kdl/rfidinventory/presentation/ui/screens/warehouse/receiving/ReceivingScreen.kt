package com.kdl.rfidinventory.presentation.ui.screens.warehouse.receiving

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.model.Warehouse
import com.kdl.rfidinventory.presentation.ui.components.*
import com.kdl.rfidinventory.util.ScanMode
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivingScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReceivingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Timber.d("uiState: $uiState")
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

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
                ReceivingTopAppBar(
                    currentStep = uiState.currentStep,
                    selectedWarehouse = uiState.selectedWarehouse,
                    basketCount = uiState.scannedBaskets.size,
                    onNavigateBack = onNavigateBack,
                    onResetToWarehouseSelection = { viewModel.resetToWarehouseSelection() },
                    onClearBaskets = { viewModel.clearBaskets() }
                )
                ConnectionStatusBar(networkState = networkState)

                // 步驟指示器
                ReceivingStepIndicator(currentStep = uiState.currentStep)
            }
        },
        floatingActionButton = {
            if (uiState.currentStep == ReceivingStep.SCANNING && uiState.scannedBaskets.isNotEmpty()) {
                val canSubmit = uiState.scannedBaskets.all {
                    it.basket.status == BasketStatus.IN_PRODUCTION
                }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    fadeIn() + slideInHorizontally { it } togetherWith
                            fadeOut() + slideOutHorizontally { -it }
                },
                label = "step_transition"
            ) { step ->
                when (step) {
                    ReceivingStep.SELECT_WAREHOUSE -> {
                        WarehouseSelectionStep(
                            warehouses = uiState.warehouses,
                            selectedWarehouse = uiState.selectedWarehouse,
                            isLoading = uiState.isLoadingWarehouses,
                            onSelectWarehouse = { viewModel.selectWarehouse(it) }
                        )
                    }

                    ReceivingStep.SCANNING -> {
                        // 添加空值檢查
                        val selectedWarehouse = uiState.selectedWarehouse

                        if (selectedWarehouse != null) {
                            ScanningStep(
                                uiState = uiState,
                                scanState = scanState,
                                onScanModeChange = { viewModel.setScanMode(it) },
                                onToggleScan = { viewModel.toggleScanFromButton() },
                                onClearBaskets = { viewModel.clearBaskets() },
                                onUpdateQuantity = { uid, quantity ->
                                    viewModel.updateBasketQuantity(uid, quantity)
                                },
                                onRemoveBasket = { uid -> viewModel.removeBasket(uid) }
                            )
                        } else {
                            // 倉庫為空時顯示錯誤
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "倉庫信息丟失",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Button(
                                        onClick = { viewModel.resetToWarehouseSelection() }
                                    ) {
                                        Text("重新選擇倉庫")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 確認收貨對話框
    if (uiState.showConfirmDialog) {
        ConfirmReceivingDialog(
            items = uiState.scannedBaskets,
            warehouse = uiState.selectedWarehouse!!,
            onDismiss = { viewModel.dismissConfirmDialog() },
            onConfirm = { viewModel.confirmReceiving() }
        )
    }
}

// ==================== 步驟指示器 ====================

@Composable
private fun ReceivingStepIndicator(currentStep: ReceivingStep) {
    val steps = listOf(
        ReceivingStep.SELECT_WAREHOUSE to "選擇倉庫",
        ReceivingStep.SCANNING to "掃描籃子"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        steps.forEachIndexed { index, (step, label) ->
            val isActive = currentStep.ordinal >= step.ordinal
            val isComplete = currentStep.ordinal > step.ordinal

            StepItem(
                number = index + 1,
                label = label,
                isActive = isActive,
                isComplete = isComplete
            )
        }
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
                    fontWeight = FontWeight.Bold,
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

// ==================== 頂部導航欄 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceivingTopAppBar(
    currentStep: ReceivingStep,
    selectedWarehouse: Warehouse?,
    basketCount: Int,
    onNavigateBack: () -> Unit,
    onResetToWarehouseSelection: () -> Unit,
    onClearBaskets: () -> Unit
) {
    TopAppBar(
        title = {
            Text("倉庫收貨")
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            when (currentStep) {
                ReceivingStep.SCANNING -> {
                    // 更換倉庫按鈕（圖3紅圈位置）
                    IconButton(
                        onClick = onResetToWarehouseSelection,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "更換倉庫",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    // 清空籃子列表按鈕
                    if (basketCount > 0) {
                        IconButton(
                            onClick = onClearBaskets,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "清空籃子列表",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                else -> {}
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

// ==================== 步驟 1：倉庫選擇 ====================

@Composable
private fun WarehouseSelectionStep(
    warehouses: List<Warehouse>,
    selectedWarehouse: Warehouse?,
    isLoading: Boolean,
    onSelectWarehouse: (Warehouse) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "選擇收貨倉庫",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // QR 碼提示
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "可用掃碼槍",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "請選擇籃子將要收貨的倉庫位置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            item {
                WarehouseSelectionCard(
                    warehouses = warehouses,
                    selectedWarehouse = selectedWarehouse,
                    onWarehouseSelected = onSelectWarehouse
                )
            }
        }
    }
}

// ==================== 步驟 2：掃描收貨 ====================

@Composable
private fun ScanningStep(
    uiState: ReceivingUiState,
    scanState: com.kdl.rfidinventory.util.ScanState,
    onScanModeChange: (ScanMode) -> Unit,
    onToggleScan: () -> Unit,
    onClearBaskets: () -> Unit,
    onUpdateQuantity: (String, Int) -> Unit,
    onRemoveBasket: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 當前倉庫信息
        item {
            SelectedWarehouseCard(
                warehouse = uiState.selectedWarehouse!!
            )
        }

        // 掃描設置
        item {
            ScanSettingsCard(
                scanMode = uiState.scanMode,
                isScanning = scanState.isScanning,
                scanType = scanState.scanType,
                isValidating = uiState.isValidating,
                onModeChange = onScanModeChange,
                onToggleScan = onToggleScan,
                statisticsContent = {
                    ReceivingStatistics(
                        basketCount = uiState.scannedBaskets.size,
                        totalQuantity = uiState.totalQuantity,
                        validBasketCount = uiState.scannedBaskets.count {
                            it.basket.status == BasketStatus.IN_PRODUCTION
                        },
                        isScanning = scanState.isScanning,
                        onClearBaskets = onClearBaskets
                    )
                },
                helpText = "• 只能收貨「生產中」狀態的籃子\n• RFID：點擊按鈕進行掃描\n• 條碼：使用實體掃碼槍觸發\n• 單次模式：再按一次實體按鍵可取消"
            )
        }

        // 已掃描籃子列表
        if (uiState.scannedBaskets.isNotEmpty()) {
            item {
                BasketListHeader(
                    basketCount = uiState.scannedBaskets.size,
                    invalidCount = uiState.scannedBaskets.count {
                        it.basket.status != BasketStatus.IN_PRODUCTION
                    }
                )
            }

            items(
                items = uiState.scannedBaskets,
                key = { item -> item.id }
            ) { item ->
                BasketCard(
                    basket = item.basket,
                    mode = BasketCardMode.RECEIVING,
                    isValidStatus = item.basket.status == BasketStatus.IN_PRODUCTION,
                    maxCapacity = item.basket.product?.maxBasketCapacity,
                    onQuantityChange = { newQuantity ->
                        onUpdateQuantity(item.basket.uid, newQuantity)
                    },
                    onRemove = {
                        onRemoveBasket(item.basket.uid)
                    }
                )
            }
        }
    }
}

// ==================== 組件：已選倉庫卡片 ====================

@Composable
private fun SelectedWarehouseCard(
    warehouse: Warehouse
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warehouse,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "收貨倉庫",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = warehouse.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (warehouse.address.isNotEmpty()) {
                    Text(
                        text = warehouse.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "已選擇",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ==================== 組件：籃子列表標題 ====================

@Composable
private fun BasketListHeader(
    basketCount: Int,
    invalidCount: Int
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

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

// ==================== 確認收貨對話框 ====================

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
                // 倉庫信息
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

                HorizontalDivider()

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