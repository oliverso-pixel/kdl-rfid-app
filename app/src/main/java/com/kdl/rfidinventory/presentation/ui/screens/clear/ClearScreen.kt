package com.kdl.rfidinventory.presentation.ui.screens.clear

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
import com.kdl.rfidinventory.presentation.ui.components.*

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearScreen(
    onNavigateBack: () -> Unit,
    viewModel: ClearViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    // 計算有效籃子數量（非 UNASSIGNED）
    val validBasketCount = remember(uiState.scannedBaskets) {
        uiState.scannedBaskets.count { it.basket.status != BasketStatus.UNASSIGNED }
    }

    // 計算是否可以提交
    val canSubmit = remember(uiState.scannedBaskets) {
        uiState.scannedBaskets.isNotEmpty() &&
                uiState.scannedBaskets.all { it.basket.status != BasketStatus.UNASSIGNED }
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
                    title = { Text("清除配置") },
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
                                "確認清除 (${uiState.scannedBaskets.size})"
                            } else {
                                "無法清除 (有錯誤狀態)"
                            }
                        )
                    },
                    icon = {
                        Icon(
                            if (canSubmit) Icons.Default.Clear else Icons.Default.Warning,
                            contentDescription = null
                        )
                    },
                    onClick = { viewModel.showConfirmDialog() },
                    containerColor = if (canSubmit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (canSubmit) {
                        MaterialTheme.colorScheme.onError
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
                    WarningCard()
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
                            ClearStatistics(
                                basketCount = uiState.scannedBaskets.size,
                                validBasketCount = validBasketCount,
                                isScanning = scanState.isScanning,
                                onClearBaskets = { viewModel.clearBaskets() }
                            )
                        },
                        helpText = "• 只能清除「已配置」狀態的籃子\n• RFID：點擊按鈕進行掃描\n• 條碼：使用實體掃碼槍觸發\n• 單次模式：再按一次實體按鍵可取消"
                    )
                }

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

                            val invalidCount = uiState.scannedBaskets.size - validBasketCount

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

                    items(
                        items = uiState.scannedBaskets,
                        key = { item -> item.id }
                    ) { item ->
                        BasketCard(
                            basket = item.basket,
                            mode = BasketCardMode.CLEAR,
                            isValidStatus = item.basket.status != BasketStatus.UNASSIGNED,
                            maxCapacity = item.basket.product?.maxBasketCapacity,
                            onQuantityChange = {},
                            onRemove = {
                                viewModel.removeBasket(item.basket.uid)
                            }
                        )
                    }
                }
            }
        }
    }

    if (uiState.showConfirmDialog) {
        val productSummaries = remember(uiState.scannedBaskets) {
            uiState.scannedBaskets
                .groupBy { it.basket.product?.name ?: "未配置產品" }
                .map { (productName, items) ->
                    ConfirmProductSummary(
                        productName = productName,
                        basketCount = items.size,
                        totalQuantity = items.sumOf { it.basket.quantity ?: 0 },
                        baskets = items.map { scanned ->
                            ConfirmBasketItem(
                                uid = scanned.basket.uid,
                                tagCode = scanned.basket.tagCode,
                                status = scanned.basket.status,
                                quantity = scanned.basket.quantity ?: 0
                            )
                        }
                    )
                }
        }

        ConfirmSubmitDialog(
            title = "確認清除配置",
            icon = Icons.Default.DeleteSweep,
            iconTint = MaterialTheme.colorScheme.error,
            description = "您即將清除以下籃子的配置，清除後：\n• 籃子狀態變為「未配置」\n• 產品、批次、數量將被移除",
            contextInfo = ConfirmContextInfo(
                icon = Icons.Default.Warning,
                label = "操作類型",
                value = "清除配置（無法撤銷）"
            ),
            products = productSummaries,
            footerText = "⚠️ 此操作無法撤銷，請確認無誤後再提交",
            confirmText = "確認清除",
            confirmIcon = Icons.Default.Clear,
            confirmColor = MaterialTheme.colorScheme.error,
            isSubmitting = uiState.isLoading,
            onDismiss = { viewModel.dismissConfirmDialog() },
            onConfirm = { viewModel.confirmClear() }
        )
    }
}

@Composable
private fun WarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "⚠️ 重要提醒",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• 只能清除「已配置」狀態的籃子\n• 清除後，籃子將變為「未配置」狀態\n• 產品、批次和數量信息將被移除\n• 此操作無法撤銷",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
