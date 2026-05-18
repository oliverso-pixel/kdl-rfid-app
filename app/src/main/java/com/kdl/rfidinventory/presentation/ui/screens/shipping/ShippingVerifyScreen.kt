package com.kdl.rfidinventory.presentation.ui.screens.shipping

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.presentation.ui.components.*
import com.kdl.rfidinventory.domain.model.ScanMode

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShippingVerifyScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShippingVerifyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    // 計算有效籃子數量
    val validBasketCount = remember(uiState.scannedBaskets) {
        uiState.scannedBaskets.count {
            it.basket.status == BasketStatus.IN_STOCK ||
                    it.basket.status == BasketStatus.UNASSIGNED
        }
    }

    // 計算是否可以提交
    val canSubmit = remember(uiState.scannedBaskets) {
        uiState.scannedBaskets.isNotEmpty() &&
                uiState.scannedBaskets.all {
                    it.basket.status == BasketStatus.IN_STOCK ||
                            it.basket.status == BasketStatus.UNASSIGNED
                }
    }

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
                    title = { Text("出貨驗證") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 完全重置按鈕
                        if (uiState.currentStep != ShippingVerifyStep.SELECT_ROUTE) {
                            IconButton(onClick = { viewModel.resetVerification() }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "重新開始",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
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
            if (uiState.scannedBaskets.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = {
                        Text(
                            if (canSubmit) {
                                "確認出貨 (${uiState.scannedBaskets.size})"
                            } else {
                                "無法出貨 (有錯誤狀態)"
                            }
                        )
                    },
                    icon = {
                        Icon(
                            if (canSubmit) Icons.Default.LocalShipping else Icons.Default.Warning,
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
            // 提交按鈕（僅可掃描、有驗證數據且未提交時顯示）
//            if (uiState.currentStep == ShippingVerifyStep.SCANNING &&
//                uiState.canScan &&
//                uiState.statistics.verifiedItems > 0 &&
//                !uiState.hasSubmitted) {
//                ExtendedFloatingActionButton(
//                    text = { Text("提交驗證") },
//                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
//                    onClick = { viewModel.submitVerification() },
//                    containerColor = MaterialTheme.colorScheme.primary
//                )
//            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 提示卡片
            item {
                ShippingInfoCard()
            }

            // 掃描設置
            item {
                ScanSettingsCard(
                    scanMode = uiState.scanMode,
                    isScanning = scanState.isScanning,
                    scanType = scanState.scanType,
                    isValidating = uiState.isValidating,
                    onModeChange = { viewModel.setScanMode(it) },
                    onToggleScan = { viewModel.toggleScanFromButton() },
                    statisticsContent = {
                        ShippingStatistics(
                            basketCount = uiState.scannedBaskets.size,
                            validBasketCount = validBasketCount,
                            isScanning = scanState.isScanning,
                            onClearBaskets = { viewModel.clearBaskets() }
                        )
                    },
                    helpText = "• 只能出貨「在庫」或「未配置」狀態的籃子\n• RFID：點擊按鈕進行掃描\n• 條碼：使用實體掃碼槍"
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
                        mode = BasketCardMode.SHIPPING,
                        isValidStatus = item.basket.status == BasketStatus.IN_STOCK ||
                                item.basket.status == BasketStatus.UNASSIGNED,
                        maxCapacity = item.basket.product?.maxBasketCapacity,
                        onQuantityChange = {},
                        onRemove = { viewModel.removeBasket(item.basket.uid) }
                    )
                }
            }
        }
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(paddingValues)
//        ) {
//            // 步驟指示器
//            ShippingVerifyStepIndicator(currentStep = uiState.currentStep)
//
//            // 主要內容
//            when (uiState.currentStep) {
//                ShippingVerifyStep.SELECT_ROUTE -> {
//                    RouteSelectionStep(
//                        routes = uiState.routes,
//                        isLoading = uiState.isLoadingRoutes,
//                        onSelectRoute = { viewModel.selectRoute(it) }
//                    )
//                }
//
//                ShippingVerifyStep.SCANNING -> {
//                    VerifyScanningStep(
//                        route = uiState.selectedRoute!!,
//                        items = uiState.verifyItems,
//                        statistics = uiState.statistics,
//                        scanMode = uiState.scanMode,
//                        isScanning = scanState.isScanning,
//                        scanType = scanState.scanType,
//                        isValidating = uiState.isValidating,
//                        isSubmitted = uiState.hasSubmitted,
//                        canScan = uiState.canScan,
//                        scanDisabledReason = uiState.scanDisabledReason,
//                        onModeChange = { viewModel.setScanMode(it) },
//                        onToggleScan = { viewModel.toggleScanFromButton() },
//                        onRemoveItem = { viewModel.removeItem(it) },
//                        onBack = { viewModel.resetVerification() }
//                    )
//                }
//
//                else -> {}
//            }
//        }

        // 確認對話框
        if (uiState.showConfirmDialog) {
//            ConfirmShippingDialog(
//                items = uiState.scannedBaskets,
//                onDismiss = { viewModel.dismissConfirmDialog() },
//                onConfirm = { viewModel.confirmShipping() }
//            )
            ConfirmSubmitDialog(
                title = "確認出貨",
                icon = Icons.Default.LocalShipping,
                description = "您即將出貨以下籃子，出貨後將無法撤銷：",
                products = uiState.scannedBaskets.map { it.basket }.toProductSummaries(),
                footerText = "📦 確認後這些籃子將標記為「已出貨」並從庫存中移除",
                confirmText = "確認出貨",
                confirmIcon = Icons.Default.LocalShipping,
                confirmColor = MaterialTheme.colorScheme.primary,
                onDismiss = { viewModel.dismissConfirmDialog() },
                onConfirm = { viewModel.confirmShipping() }
            )
        }
    }
}

@Composable
private fun ShippingInfoCard() {
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocalShipping,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "📦 出貨驗證",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• 掃描籃子後將狀態更新為「已出貨」\n• 只能出貨「在庫」或「未配置」狀態的籃子\n• 出貨後籃子將從庫存中移除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ConfirmShippingDialog(
    items: List<ScannedShippingItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val baskets = items.map { it.basket }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.LocalShipping,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("確認出貨") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "共 ${baskets.size} 個籃子",
                    style = MaterialTheme.typography.titleSmall
                )

                Divider()

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "將出貨的籃子：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        baskets.take(5).forEach { basket ->
                            Text(
                                text = "• ${basket.uid.takeLast(8)} - ${basket.product?.name ?: "未配置"} (${getBasketStatusText(basket.status)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (baskets.size > 5) {
                            Text(
                                text = "... 還有 ${baskets.size - 5} 個籃子",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Divider()

                Text(
                    text = "✅ 確認後這些籃子將標記為「已出貨」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("確認出貨")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ==================== 步驟指示器 ====================

@Composable
private fun ShippingVerifyStepIndicator(currentStep: ShippingVerifyStep) {
    val steps = listOf(
        ShippingVerifyStep.SELECT_ROUTE to "選路線",
        ShippingVerifyStep.SCANNING to "掃描驗證"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 12.dp, horizontal = 8.dp),
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
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
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
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelSmall,
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

// ==================== 步驟 1: 路線選擇 ====================

@Composable
private fun RouteSelectionStep(
    routes: List<LoadingRoute>,
    isLoading: Boolean,
    onSelectRoute: (LoadingRoute) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "出貨驗證",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "選擇已完成上貨的路線進行出貨驗證",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "選擇路線",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
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
        } else if (routes.isEmpty()) {
            item {
                EmptyRouteCard()
            }
        } else {
            items(routes) { route ->
                RouteCard(
                    route = route,
                    onClick = { onSelectRoute(route) }
                )
            }
        }
    }
}

@Composable
private fun EmptyRouteCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "暫無已完成上貨的路線",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RouteCard(
    route: LoadingRoute,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = route.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "車牌：${route.vehiclePlate}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "配送日期：${route.deliveryDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ✅ 根據狀態顯示不同的標記
                RouteStatusBadge(status = route.status)
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatItem(
                    label = "總數量",
                    value = route.totalQuantity.toString(),
                    icon = Icons.Default.Numbers,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "籃子數",
                    value = route.totalBaskets.toString(),
                    icon = Icons.Default.Inventory,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "品項",
                    value = route.items.size.toString(),
                    icon = Icons.Default.Category,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 路線狀態徽章
 */
@Composable
private fun RouteStatusBadge(status: LoadingStatus) {
    val (text, color) = when (status) {
        LoadingStatus.PENDING -> "待上貨" to Color(0xFF9E9E9E)
        LoadingStatus.IN_PROGRESS -> "上貨中" to Color(0xFF2196F3)
        LoadingStatus.COMPLETED -> "已完成上貨" to MaterialTheme.colorScheme.secondary
        LoadingStatus.VERIFIED -> "已驗證" to Color(0xFF4CAF50)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ✅ 已驗證狀態顯示對號圖標
            if (status == LoadingStatus.VERIFIED) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )
            }

            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

// ==================== 步驟 2: 掃描驗證 ====================

@Composable
private fun VerifyScanningStep(
    route: LoadingRoute,
    items: List<VerifyItem>,
    statistics: VerifyStatistics,
    scanMode: ScanMode,
    isScanning: Boolean,
    scanType: com.kdl.rfidinventory.domain.model.ScanType,
    isValidating: Boolean,
    isSubmitted: Boolean,
    canScan: Boolean,
    scanDisabledReason: String?,
    onModeChange: (ScanMode) -> Unit,
    onToggleScan: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 當前路線信息
        item {
            CurrentRouteHeader(
                route = route,
                onBack = onBack
            )
        }

        // 如果不可掃描，顯示只讀模式提示
        if (!canScan) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF9800).copy(alpha = 0.1f)
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
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFFFF9800)
                        )
                        Column {
                            Text(
                                text = "👁️ 只讀模式",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF9800)
                            )
                            Text(
                                text = scanDisabledReason ?: "此路線只能查看，無法掃描驗證",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 已提交提示
        if (isSubmitted) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
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
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Column {
                            Text(
                                text = "✅ 驗證已完成",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                            Text(
                                text = "此路線已完成出貨驗證，可繼續掃描查看但無法重覆提交",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 掃描設置（只有可掃描時才顯示）
        if (canScan) {
            item {
                ScanSettingsCard(
                    scanMode = scanMode,
                    isScanning = isScanning,
                    scanType = scanType,
                    isValidating = isValidating,
                    onModeChange = onModeChange,
                    onToggleScan = onToggleScan,
                    statisticsContent = {
                        VerifyStatisticsCard(statistics = statistics)
                    },
                    helpText = when (scanMode) {
                        ScanMode.SINGLE -> "• RFID：點擊按鈕掃描\n• 條碼：使用實體掃碼槍\n• 單次模式：再按一次可取消"
                        ScanMode.CONTINUOUS -> "• 點擊開始連續掃描\n• 再次點擊或實體按鍵停止"
                    }
                )
            }
        }

        // 統計卡片
        item {
            StatisticsOverviewCard(statistics = statistics)
        }

        // 籃子列表
        if (items.isNotEmpty()) {
            item {
                Text(
                    text = if (canScan) "驗證清單 (${items.size})" else "籃子清單 (${items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(
                items = items,
                key = { it.id }
            ) { item ->
                VerifyItemCard(
                    item = item,
                    canRemove = canScan && !isSubmitted, // 只有可掃描且未提交時才能刪除
                    onRemove = { onRemoveItem(item.id) }
                )
            }
        } else {
            // ✅ 無籃子數據提示
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "暫無籃子數據",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentRouteHeader(
    route: LoadingRoute,
    onBack: () -> Unit
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
                    text = "當前路線",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "車牌：${route.vehiclePlate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun VerifyStatisticsCard(statistics: VerifyStatistics) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 第一行：待驗證的項目統計
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VerifyStatItem(
                label = "總數",
                value = statistics.totalItems.toString(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            VerifyStatItem(
                label = "已驗證",
                value = statistics.verifiedItems.toString(),
                color = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
            VerifyStatItem(
                label = "待驗證",
                value = statistics.pendingItems.toString(),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        // 第二行：額外項和已完成驗證的籃子
        if (statistics.extraItems > 0 || statistics.alreadyVerifiedItems > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (statistics.extraItems > 0) {
                    VerifyStatItem(
                        label = "額外項",
                        value = statistics.extraItems.toString(),
                        color = Color(0xFFFF9800),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (statistics.alreadyVerifiedItems > 0) {
                    VerifyStatItem(
                        label = "已完成驗證",
                        value = statistics.alreadyVerifiedItems.toString(),
                        color = Color(0xFF9C27B0), // 紫色
                        modifier = Modifier.weight(1f)
                    )
                }

                // 占位，保持對齊
                if (statistics.extraItems == 0 || statistics.alreadyVerifiedItems == 0) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun VerifyStatItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
private fun StatisticsOverviewCard(statistics: VerifyStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "驗證進度",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // 進度條
            LinearProgressIndicator(
                progress = {
                    if (statistics.totalItems > 0) {
                        statistics.verifiedItems.toFloat() / statistics.totalItems
                    } else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${statistics.verifiedItems} / ${statistics.totalItems}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val percentage = if (statistics.totalItems > 0) {
                    (statistics.verifiedItems.toFloat() / statistics.totalItems * 100).toInt()
                } else 0

                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ✅ 已完成驗證的籃子提示
            if (statistics.alreadyVerifiedItems > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF9C27B0)
                    )
                    Text(
                        text = "發現 ${statistics.alreadyVerifiedItems} 個已完成驗證的籃子",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9C27B0),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (statistics.extraItems > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFFF9800)
                    )
                    Text(
                        text = "發現 ${statistics.extraItems} 個其他狀態的籃子",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun VerifyItemCard(
    item: VerifyItem,
    canRemove: Boolean = true,
    onRemove: () -> Unit
) {
    // 根據籃子狀態和驗證狀態決定顯示樣式
    val (backgroundColor, borderColor, statusText) = when {
        // 已完成驗證的籃子（SHIPPED 狀態）
        item.basket.status == BasketStatus.SHIPPED -> Triple(
            Color(0xFF9C27B0).copy(alpha = 0.1f),
            Color(0xFF9C27B0),
            "✅ 已完成驗證"
        )
        // 本次掃描驗證的籃子（LOADING → VERIFIED）
        item.basket.status == BasketStatus.LOADING && item.status == VerifyItemStatus.VERIFIED -> Triple(
            Color(0xFF4CAF50).copy(alpha = 0.1f),
            Color(0xFF4CAF50),
            "✅ 本次已驗證"
        )
        // 待驗證的籃子
        item.status == VerifyItemStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.surface,
            Color.Transparent,
            null
        )
        // 額外項
        item.status == VerifyItemStatus.EXTRA -> Triple(
            Color(0xFFFF9800).copy(alpha = 0.1f),
            Color(0xFFFF9800),
            "⚠️ 其他狀態"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surface,
            Color.Transparent,
            null
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (borderColor != Color.Transparent) 2.dp else 0.dp,
            color = borderColor
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
                // UID 和狀態
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 狀態圖標
                    Icon(
                        imageVector = when {
                            item.basket.status == BasketStatus.SHIPPED -> Icons.Default.CheckCircle
                            item.status == VerifyItemStatus.VERIFIED -> Icons.Default.CheckCircle
                            item.status == VerifyItemStatus.PENDING -> Icons.Default.RadioButtonUnchecked
                            item.status == VerifyItemStatus.EXTRA -> Icons.Default.Warning
                            else -> Icons.Default.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = when {
                            item.basket.status == BasketStatus.SHIPPED -> Color(0xFF9C27B0)
                            item.status == VerifyItemStatus.VERIFIED -> Color(0xFF4CAF50)
                            item.status == VerifyItemStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                            item.status == VerifyItemStatus.EXTRA -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )

                    Text(
                        text = "UID: ...${item.basket.uid.takeLast(8)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (item.scanCount > 1) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "×${item.scanCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 產品信息
                item.basket.product?.let { product ->
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // 批次、數量和狀態
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    item.basket.batch?.let { batch ->
                        Text(
                            text = "批次: ${batch.batch_code}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "數量: ${item.basket.quantity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "狀態: ${getBasketStatusText(item.basket.status)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = when (item.basket.status) {
                            BasketStatus.LOADING -> Color(0xFF4CAF50)
                            BasketStatus.SHIPPED -> Color(0xFF9C27B0)
                            BasketStatus.UNASSIGNED -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                // 狀態標記
                if (statusText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = borderColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 操作按鈕（只有額外項且可刪除時才顯示）
            if (item.status == VerifyItemStatus.EXTRA &&
                item.basket.status != BasketStatus.SHIPPED &&
                canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "刪除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}