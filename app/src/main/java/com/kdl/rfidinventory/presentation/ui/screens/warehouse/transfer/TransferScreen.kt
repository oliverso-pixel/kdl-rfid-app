package com.kdl.rfidinventory.presentation.ui.screens.warehouse.transfer

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdl.rfidinventory.data.model.Warehouse
import com.kdl.rfidinventory.presentation.ui.components.*
import com.kdl.rfidinventory.util.ScanMode

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransferViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 錯誤提示
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(message = error, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    // 成功提示
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("倉庫轉換") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (uiState.currentStep == TransferStep.SCANNING) {
                            // 更換倉庫按鈕
                            IconButton(onClick = { viewModel.resetToWarehouseSelection() }) {
                                Icon(Icons.Default.Edit, "更換倉庫", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            // 清空按鈕
                            if (uiState.scannedBaskets.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearBaskets() }) {
                                    Icon(Icons.Default.Delete, "清空", tint = MaterialTheme.colorScheme.onPrimary)
                                }
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

                // 步驟指示器
                TransferStepIndicator(currentStep = uiState.currentStep)
            }
        },
        floatingActionButton = {
            if (uiState.currentStep == TransferStep.SCANNING && uiState.scannedBaskets.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("確認轉換 (${uiState.scannedBaskets.size})") },
                    icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                    onClick = { viewModel.showConfirmDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AnimatedContent(
                targetState = uiState.currentStep,
                label = "transfer_step"
            ) { step ->
                when (step) {
                    TransferStep.SELECT_TARGET_WAREHOUSE -> {
                        WarehouseSelectionContent(
                            warehouses = uiState.warehouses,
                            isLoading = uiState.isLoadingWarehouses,
                            onSelect = { viewModel.selectTargetWarehouse(it) }
                        )
                    }
                    TransferStep.SCANNING -> {
                        ScanningContent(
                            uiState = uiState,
                            scanState = scanState,
                            onScanModeChange = { viewModel.setScanMode(it) },
                            onToggleScan = { viewModel.toggleScanFromButton() },
                            onRemoveItem = { uid -> viewModel.removeBasket(uid) }
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (uiState.showConfirmDialog) {
        ConfirmTransferDialog(
            count = uiState.scannedBaskets.size,
            targetWarehouseName = uiState.selectedWarehouse?.name ?: "",
            onDismiss = { viewModel.dismissConfirmDialog() },
            onConfirm = { viewModel.submitTransfer() }
        )
    }
}

@Composable
private fun TransferStepIndicator(currentStep: TransferStep) {
    val steps = listOf(
        TransferStep.SELECT_TARGET_WAREHOUSE to "選擇目標倉庫",
        TransferStep.SCANNING to "掃描轉換"
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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WarehouseSelectionContent(
    warehouses: List<Warehouse>,
    isLoading: Boolean,
    onSelect: (Warehouse) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "請選擇目標倉庫",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "籃子將被轉移到此倉庫，並標記為「在庫中」",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            item {
                WarehouseSelectionCard(
                    warehouses = warehouses,
                    onWarehouseSelected = onSelect
                )
            }
        }
    }
}

@Composable
private fun ScanningContent(
    uiState: TransferUiState,
    scanState: com.kdl.rfidinventory.util.ScanState,
    onScanModeChange: (ScanMode) -> Unit,
    onToggleScan: () -> Unit,
    onRemoveItem: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 目標倉庫資訊
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "目標倉庫",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = uiState.selectedWarehouse?.name ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // 掃描設定
        item {
            ScanSettingsCard(
                scanMode = uiState.scanMode,
                isScanning = scanState.isScanning,
                scanType = scanState.scanType,
                isValidating = uiState.isValidating,
                onModeChange = onScanModeChange,
                onToggleScan = onToggleScan,
                statisticsContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("已掃描數量:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${uiState.scannedBaskets.size} 籃",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                helpText = "• 掃描在庫的籃子進行轉移\n• 再次點擊或按實體鍵停止"
            )
        }

        // 列表
        items(uiState.scannedBaskets, key = { it.id }) { item ->
            BasketCard(
                basket = item.basket,
                mode = BasketCardMode.RECEIVING, // 重用樣式
                isValidStatus = true, // 假設已在 VM 過濾
                onQuantityChange = {}, // 轉換不改數量
                onRemove = { onRemoveItem(item.basket.uid) }
            )
        }
    }
}

@Composable
private fun ConfirmTransferDialog(
    count: Int,
    targetWarehouseName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SwapHoriz, null) },
        title = { Text("確認轉換倉庫") },
        text = {
            Text("確定將 $count 個籃子轉移到「$targetWarehouseName」嗎？\n\n這些籃子的狀態將更新為「在庫中」。")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("確認轉換")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}