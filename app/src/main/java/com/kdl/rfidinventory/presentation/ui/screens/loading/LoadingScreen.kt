package com.kdl.rfidinventory.presentation.ui.screens.loading

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar
import com.kdl.rfidinventory.presentation.ui.components.ScanSettingsCard
import com.kdl.rfidinventory.util.ScanMode
import com.kdl.rfidinventory.util.ScanModeAvailability
import com.kdl.rfidinventory.util.ScanState
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadingScreen(
    onNavigateBack: () -> Unit,
    viewModel: LoadingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val networkState by viewModel.networkState.collectAsState()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
                    title = { Text("上貨管理") },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (uiState.currentStep == LoadingStep.SELECT_MODE) {
                                onNavigateBack()
                            } else {
                                viewModel.goBack()
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, "返回")
                        }
                    },
                    actions = {
                        // 完全重置按鈕
                        if (uiState.currentStep != LoadingStep.SELECT_MODE) {
                            IconButton(onClick = { viewModel.reset() }) {
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
            // 確認上貨按鈕（僅掃描步驟且完成時顯示）
            if (uiState.currentStep == LoadingStep.SCANNING && uiState.isComplete) {
                ExtendedFloatingActionButton(
                    text = { Text("確認") },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = { viewModel.confirmCurrentItem() },
                    containerColor = MaterialTheme.colorScheme.secondary
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
            LoadingStepIndicator(currentStep = uiState.currentStep)

            // 主要內容
            when (uiState.currentStep) {
                LoadingStep.SELECT_MODE -> {
                    SelectModeStep(
                        onModeSelected = viewModel::selectLoadingMode
                    )
                }
                LoadingStep.SELECT_WAREHOUSE -> {
                    SelectWarehouseStep(
                        warehouses = uiState.warehouses,
                        isLoading = uiState.isLoadingWarehouses,
                        onWarehouseSelected = viewModel::selectWarehouse
                    )
                }
                LoadingStep.SELECT_ROUTE -> {
                    SelectRouteStep(
                        routes = uiState.routes,
                        selectedWarehouseName = uiState.selectedWarehouseName ?: "",
                        selectedWarehouseId = uiState.selectedWarehouseId,
                        selectedMode = uiState.selectedMode ?: LoadingMode.FULL_BASKETS,
                        warehouseBaskets = uiState.warehouseBaskets,
                        onRouteSelected = viewModel::selectRoute,
                        onViewRouteDetail = viewModel::viewRouteDetail,
                        onResetRoute = { route ->
                            viewModel.resetRouteCompletion(route.id)
                        }
                    )
                }
                LoadingStep.ROUTE_DETAIL -> {
                    uiState.selectedRoute?.let { route ->
                        RouteDetailScreen(
                            route = route,
                            selectedWarehouseName = uiState.selectedWarehouseName ?: "",
                            warehouseBaskets = emptyList(),
                            onSelectModeAndItem = viewModel::selectModeAndItemFromDetail,
                            onBack = { viewModel.setCurrentStep(LoadingStep.SELECT_ROUTE) }
                        )
                    }
                }
                LoadingStep.SELECT_ITEM -> {
                    SelectItemStep(
                        items = uiState.availableItemsWithStock,
                        selectedMode = uiState.selectedMode!!,
                        routeName = uiState.selectedRoute?.name ?: "",
                        onItemSelected = viewModel::selectItem,
                        onSubmit = viewModel::submitLoading
                    )
                }
                LoadingStep.SCANNING -> {
                    ScanningStep(
                        uiState = uiState,
                        selectedMode = uiState.selectedMode!!,
                        scanState = scanState,
                        onModeChange = viewModel::setScanMode,
                        onToggleScan = viewModel::toggleScanFromButton,
                        onRemoveBasket = viewModel::removeScannedBasket,
                        onUpdateQuantity = viewModel::updateLooseQuantity
                    )
                }
                LoadingStep.SUMMARY -> {
                    SummaryStep(
                        uiState = uiState,
                        onReset = viewModel::reset
                    )
                }
            }

            // 加載指示器
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// ==================== 通用組件 ====================

/**
 * 統計項組件 - 可被多處覆用
 */
@Composable
fun StatItem(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.primary,
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

// ==================== 步驟指示器 ====================

@Composable
private fun LoadingStepIndicator(currentStep: LoadingStep) {
    val steps = listOf(
        LoadingStep.SELECT_MODE to "選模式",
        LoadingStep.SELECT_WAREHOUSE to "選倉庫",
        LoadingStep.SELECT_ROUTE to "選路線",
        LoadingStep.SELECT_ITEM to "選貨物",
        LoadingStep.SCANNING to "掃描",
        LoadingStep.SUMMARY to "完成"
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

// ==================== 步驟1：選擇上貨模式 ====================

@Composable
fun SelectModeStep(
    onModeSelected: (LoadingMode) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "請選擇上貨模式",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            ModeCard(
                icon = Icons.Default.Inventory,
                title = "完整籃子模式",
                description = "掃描完整的籃子，每個籃子必須滿格",
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = { onModeSelected(LoadingMode.FULL_BASKETS) }
            )
        }

        item {
            ModeCard(
                icon = Icons.Default.ShoppingBasket,
                title = "散貨模式",
                description = "掃描散貨籃子，可以包含任意數量",
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { onModeSelected(LoadingMode.LOOSE_ITEMS) }
            )
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 步驟2：選擇倉庫 ====================

@Composable
fun SelectWarehouseStep(
    warehouses: List<Warehouse>,
    isLoading: Boolean,
    onWarehouseSelected: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "選擇倉庫",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (isLoading){
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
            items(warehouses) { warehouse ->
                WarehouseCard(
                    warehouse = warehouse,
                    onClick = { onWarehouseSelected(warehouse.id, warehouse.name) }
                )
            }
        }
    }
}

@Composable
private fun WarehouseCard(
    warehouse: Warehouse,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warehouse,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = warehouse.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (warehouse.address.isNotEmpty()) {
                        Text(
                            text = warehouse.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 步驟3：選擇路線 ====================

@Composable
fun SelectRouteStep(
    routes: List<LoadingRoute>,
    selectedWarehouseName: String,
    selectedWarehouseId: String?,
    selectedMode: LoadingMode,
    warehouseBaskets: List<Basket>,
    onRouteSelected: (LoadingRoute) -> Unit,
    onViewRouteDetail: (LoadingRoute) -> Unit,
    onResetRoute: (LoadingRoute) -> Unit
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warehouse,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Column {
                            Text(
                                text = "當前倉庫",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = selectedWarehouseName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedMode == LoadingMode.FULL_BASKETS)
                                Icons.Default.Inventory else Icons.Default.ShoppingBasket,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (selectedMode == LoadingMode.FULL_BASKETS)
                                "完整籃子模式" else "散貨模式",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(16.dp),
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(12.dp)
//                ) {
//                    Icon(
//                        imageVector = Icons.Default.Warehouse,
//                        contentDescription = null,
//                        modifier = Modifier.size(32.dp),
//                        tint = MaterialTheme.colorScheme.onPrimaryContainer
//                    )
//                    Column {
//                        Text(
//                            text = "當前倉庫",
//                            style = MaterialTheme.typography.labelMedium,
//                            color = MaterialTheme.colorScheme.onPrimaryContainer
//                        )
//                        Text(
//                            text = selectedWarehouseName,
//                            style = MaterialTheme.typography.titleMedium,
//                            fontWeight = FontWeight.Bold,
//                            color = MaterialTheme.colorScheme.onPrimaryContainer
//                        )
//                    }
//                }
            }
        }

        item {
            Text(
                text = "選擇路線",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        items(routes) { route ->
            RouteCard(
                route = route,
                selectedMode = selectedMode,
                selectedWarehouseId = selectedWarehouseId,
                warehouseBaskets = warehouseBaskets,
                onClick = { onRouteSelected(route) },
                onViewDetail = { onViewRouteDetail(route) },
                onLongPress = { onResetRoute(route) }
            )
        }
    }
}

@Composable
private fun RouteCard(
    route: LoadingRoute,
    selectedMode: LoadingMode,
    selectedWarehouseId: String?,
    warehouseBaskets: List<Basket>,
    onClick: () -> Unit,
    onViewDetail: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    var showResetDialog by remember { mutableStateOf(false) }

    // 檢查該路線在當前倉庫和模式下是否已完成
    val isCompletedInMode = remember(route, selectedMode, selectedWarehouseId, warehouseBaskets) {
        // 獲取在當前倉庫有庫存的產品
        val itemsInWarehouse = route.items.filter { item ->
            warehouseBaskets.any { basket ->
                basket.product?.itemcode == item.productId &&
                        basket.warehouseId == selectedWarehouseId &&
                        basket.status == BasketStatus.IN_STOCK
            } || when (selectedMode) {
                LoadingMode.FULL_BASKETS -> item.completionStatus.fullBasketsCompleted
                LoadingMode.LOOSE_ITEMS -> item.completionStatus.looseItemsCompleted
            }
        }

        // 如果當前倉庫沒有該路線的任何產品，返回 false
        if (itemsInWarehouse.isEmpty()) {
            false
        } else {
            // 檢查這些產品在當前模式下是否全部完成
            itemsInWarehouse.all { item ->
                when (selectedMode) {
                    LoadingMode.FULL_BASKETS -> {
                        (item.fullTrolley == 0 && item.fullBaskets == 0) ||
                                item.completionStatus.fullBasketsCompleted
                    }
                    LoadingMode.LOOSE_ITEMS -> {
                        item.looseQuantity == 0 ||
                                item.completionStatus.looseItemsCompleted
                    }
                }
            }
        }
    }

    Timber.d(" Screen route ${route.id}: ${route}")
    Timber.d(" Screen selectedWarehouseId: ${selectedWarehouseId}")
    Timber.d(" Screen isCompletedInMode ${route.id}: ${isCompletedInMode}")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    // 只有在 IN_PROGRESS 狀態時才能長按重置
                    if (route.status == LoadingStatus.IN_PROGRESS || route.status == LoadingStatus.COMPLETED) {
                        showResetDialog = true
                    }
                },
                enabled = !isCompletedInMode
            )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCompletedInMode) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
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
                    }

                    if (isCompletedInMode) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "已完成",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        RouteStatusBadge(status = route.status)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatItem(
                        label = "總數量",
                        value = route.totalQuantity.toString(),
                        icon = Icons.Default.Numbers,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(0.5f)
                    )
                    StatItem(
                        label = "籃子數",
                        value = route.totalBaskets.toString(),
                        icon = Icons.Default.Inventory,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(0.5f)
                    )
                    StatItem(
                        label = "品項",
                        value = route.items.size.toString(),
                        icon = Icons.Default.Category,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(0.5f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewDetail,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("查看詳情")
                    }

                    Button(
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                        enabled = !isCompletedInMode && route.status != LoadingStatus.COMPLETED
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isCompletedInMode) "已完成" else "開始上貨")
                    }
                }
            }
        }
    }

    // 重置確認對話框
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置確認") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("確定要重置此路線的所有上貨記錄嗎?")
                    Text(
                        text = "此操作不可撤銷。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLongPress()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = false
                ) {
                    Text("確定重置", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun RouteStatusBadge(status: LoadingStatus) {
    val (text, color) = when (status) {
        LoadingStatus.PENDING -> "待上貨" to MaterialTheme.colorScheme.tertiary
        LoadingStatus.IN_PROGRESS -> "進行中" to MaterialTheme.colorScheme.primary
        LoadingStatus.COMPLETED -> "已完成" to MaterialTheme.colorScheme.secondary
        LoadingStatus.VERIFIED -> "" to MaterialTheme.colorScheme.surface
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

// ==================== 步驟4：選擇貨物 ====================

@Composable
fun SelectItemStep(
    items: List<LoadingItemWithStock>,
    selectedMode: LoadingMode,
    routeName: String,
    onItemSelected: (LoadingItemWithStock) -> Unit,
    onSubmit: () -> Unit
) {
    // 檢查是否全部完成
    val allCompleted = items.all { it.isCompleted }

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Column {
                            Text(
                                text = "當前路線",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = routeName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedMode == LoadingMode.FULL_BASKETS)
                                Icons.Default.Inventory else Icons.Default.ShoppingBasket,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (selectedMode == LoadingMode.FULL_BASKETS)
                                "完整籃子模式" else "散貨模式",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "選擇貨物",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // 顯示完成進度
                val completedCount = items.count { it.isCompleted }
                Text(
                    text = "$completedCount / ${items.size} 完成",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (allCompleted) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(items) { itemWithStock ->
            LoadingItemWithStockCard(
                itemWithStock = itemWithStock,
                selectedMode = selectedMode,
                onClick = { onItemSelected(itemWithStock) }
            )
        }

        // 全部完成時顯示提交按鈕
        if (allCompleted) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("提交上貨", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LoadingItemWithStockCard(
    itemWithStock: LoadingItemWithStock,
    selectedMode: LoadingMode,
    onClick: () -> Unit
) {
    val item = itemWithStock.item
    val isAvailable = itemWithStock.availableBaskets.isNotEmpty()
    val isCompleted = itemWithStock.isCompleted

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = isAvailable && !isCompleted, // ✅ 已完成則不可點擊
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCompleted -> MaterialTheme.colorScheme.secondaryContainer
                isAvailable -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (isCompleted) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // ✅ 已完成標籤
                    if (isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "已完成",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (selectedMode == LoadingMode.FULL_BASKETS) {
                    Text(
                        text = "${item.fullTrolley}車${item.fullBaskets}格",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "總計：${item.totalQuantity}個",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "散貨數量：${item.looseQuantity}個",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 顯示庫存信息
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            isCompleted -> Icons.Default.CheckCircle
                            isAvailable -> Icons.Default.Inventory2
                            else -> Icons.Default.Warning
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when {
                            isCompleted -> MaterialTheme.colorScheme.secondary
                            isAvailable -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = when {
                            isCompleted -> "已完成上貨"
                            isAvailable -> "庫存：${itemWithStock.availableBaskets.size} 籃 • ${itemWithStock.availableQuantity} 個"
                            else -> "無可用庫存"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isCompleted -> MaterialTheme.colorScheme.secondary
                            isAvailable -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        },
                        fontWeight = if (isCompleted || isAvailable) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            if (isAvailable && !isCompleted) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LoadingItemCard(
    item: LoadingItem,
    selectedMode: LoadingMode,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (selectedMode == LoadingMode.FULL_BASKETS) {
                    Text(
                        text = "${item.fullTrolley}車${item.fullBaskets}格",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "總計：${item.totalQuantity}個",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "散貨數量：${item.looseQuantity}個",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 步驟5：掃描上貨 ====================

@Composable
fun ScanningStep(
    uiState: LoadingUiState,
    selectedMode: LoadingMode,
    scanState: ScanState,
    onModeChange: (ScanMode) -> Unit,
    onToggleScan: () -> Unit,
    onRemoveBasket: (String) -> Unit,
    onUpdateQuantity: (String, Int) -> Unit
) {
    val selectedItemWithStock = uiState.selectedItemWithStock
    val selectedItem = selectedItemWithStock?.item

    if (selectedItem == null) {
        // 錯誤處理
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "未選擇貨物",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScanProgressCard(
                selectedMode = uiState.selectedMode!!,
                selectedItem = selectedItem,
                selectedItemWithStock = selectedItemWithStock,
                totalScanned = uiState.totalScanned,
                expectedQuantity = uiState.expectedQuantity,
                scannedCount = uiState.scannedItems.size,
                isComplete = uiState.isComplete
            )
        }

        item {
            ScanSettingsCard(
                scanMode = if (selectedMode == LoadingMode.FULL_BASKETS) ScanMode.CONTINUOUS else ScanMode.SINGLE,
                isScanning = scanState.isScanning,
                scanType = scanState.scanType,
                isValidating = uiState.isValidating,
                onModeChange = {},
                onToggleScan = onToggleScan,
                availability = if (selectedMode == LoadingMode.FULL_BASKETS) ScanModeAvailability.CONTINUOUS_ONLY else ScanModeAvailability.SINGLE_ONLY,
                statisticsContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatItem(
                            label = "已掃",
                            value = uiState.scannedItems.size.toString(),
                            icon = Icons.Default.CheckCircle,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.weight(1f)
                        )
                        StatItem(
                            label = "總數",
                            value = uiState.totalScanned.toString(),
                            icon = Icons.Default.Numbers,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                },
                helpText = when (uiState.scanMode) {
                    ScanMode.SINGLE -> "• RFID：點擊按鈕掃描\n• 條碼：使用實體掃碼槍\n• 單次模式：再按一次可取消"
                    ScanMode.CONTINUOUS -> "• 點擊開始連續掃描\n• 再次點擊或實體按鍵停止"
                }
            )
        }

        if (uiState.scannedItems.isNotEmpty()) {
            item {
                Text(
                    text = "已掃描清單 (${uiState.scannedItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(
                items = uiState.scannedItems,
                key = { it.basket.uid }
            ) { scannedItem ->
                ScannedBasketItem(
                    scannedItem = scannedItem,
                    onRemove = { onRemoveBasket(scannedItem.basket.uid) },
                    onUpdateQuantity = { newQty ->
                        onUpdateQuantity(scannedItem.basket.uid, newQty)
                    }
                )
            }
        }
    }
}

@Composable
private fun ScanProgressCard(
    selectedMode: LoadingMode,
    selectedItem: LoadingItem,
    selectedItemWithStock: LoadingItemWithStock,
    totalScanned: Int,
    expectedQuantity: Int,
    scannedCount: Int,
    isComplete: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isComplete)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
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
                        text = selectedItem.productName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (selectedMode == LoadingMode.FULL_BASKETS)
                            "完整籃子模式" else "散貨模式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "可用庫存：${selectedItemWithStock.availableBaskets.size} 籃 • ${selectedItemWithStock.availableQuantity} 個",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isComplete) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "已掃描：$totalScanned / $expectedQuantity",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${(totalScanned * 100 / expectedQuantity.coerceAtLeast(1))}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (totalScanned.toFloat() / expectedQuantity.coerceAtLeast(1)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "籃子數量：$scannedCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ✅ 顯示剩余可掃描
                val remainingBaskets = selectedItemWithStock.availableBaskets.size - scannedCount
                Text(
                    text = "剩餘可掃：$remainingBaskets 籃",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (remainingBaskets > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ScannedBasketItem(
    scannedItem: LoadingScannedItem,
    onRemove: () -> Unit,
    onUpdateQuantity: (Int) -> Unit
) {
    var showQuantityDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scannedItem.basket.uid,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "數量：${scannedItem.scannedQuantity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (scannedItem.isLoose) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { showQuantityDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "修改",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "刪除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showQuantityDialog) {
        QuantityEditDialog(
            currentQuantity = scannedItem.scannedQuantity,
            onConfirm = { newQty ->
                onUpdateQuantity(newQty)
                showQuantityDialog = false
            },
            onDismiss = { showQuantityDialog = false }
        )
    }
}

@Composable
private fun QuantityEditDialog(
    currentQuantity: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var quantity by remember { mutableStateOf(currentQuantity.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改數量") },
        text = {
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it.filter { char -> char.isDigit() } },
                label = { Text("數量") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    quantity.toIntOrNull()?.let { onConfirm(it) }
                }
            ) {
                Text("確認")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ==================== 步驟6：匯總頁面 ====================

@Composable
fun SummaryStep(
    uiState: LoadingUiState,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "上貨完成",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "總共上貨",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${uiState.totalScanned}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "個產品，${uiState.scannedItems.size} 個籃子",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                // ✅ 顯示詳細信息
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    uiState.selectedRoute?.let { route ->
                        DetailRow(label = "路線", value = route.name)
                    }
                    uiState.selectedWarehouseName?.let { warehouse ->
                        DetailRow(label = "倉庫", value = warehouse)
                    }
                    uiState.selectedItemWithStock?.item?.let { item ->
                        DetailRow(label = "產品", value = item.productName)
                    }
                    uiState.selectedMode?.let { mode ->
                        DetailRow(
                            label = "模式",
                            value = if (mode == LoadingMode.FULL_BASKETS) "完整籃子" else "散貨"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回首頁")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}