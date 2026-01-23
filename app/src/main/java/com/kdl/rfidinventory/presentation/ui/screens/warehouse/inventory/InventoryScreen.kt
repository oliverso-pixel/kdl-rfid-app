package com.kdl.rfidinventory.presentation.ui.screens.warehouse.inventory

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
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
import coil.compose.AsyncImage
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.model.Batch
import com.kdl.rfidinventory.data.model.Product
import com.kdl.rfidinventory.data.model.Warehouse
import com.kdl.rfidinventory.presentation.ui.components.BasketCard
import com.kdl.rfidinventory.presentation.ui.components.BasketCardMode
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar
import com.kdl.rfidinventory.presentation.ui.components.ScanSettingsCard
import com.kdl.rfidinventory.presentation.ui.components.WarehouseSelectionCard
import com.kdl.rfidinventory.util.Constants
import com.kdl.rfidinventory.util.ScanMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
                    title = { Text("倉庫盤點") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 完全重置按鈕
                        if (uiState.currentStep != InventoryStep.SELECT_WAREHOUSE) {
                            IconButton(onClick = { viewModel.resetInventory() }) {
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
            // 完成/提交按鈕（僅掃描步驟顯示）
            if (uiState.currentStep == InventoryStep.SCANNING &&
                uiState.statistics.scannedItems > 0) {
                ExtendedFloatingActionButton(
                    text = {
                        Text(
                            when (uiState.inventoryMode) {
                                InventoryMode.FULL -> "提交盤點"
                                InventoryMode.BY_PRODUCT -> "完成此批次"
                                else -> "完成"
                            }
                        )
                    },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = { viewModel.completeCurrent() },
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
            InventoryStepIndicator(currentStep = uiState.currentStep)

            // 主要內容
            when (uiState.currentStep) {
                InventoryStep.SELECT_WAREHOUSE -> {
                    WarehouseSelectionStep(
                        warehouses = uiState.warehouses,
                        selectedWarehouse = uiState.selectedWarehouse,
                        isLoading = uiState.isLoadingWarehouses,
                        onSelectWarehouse = { viewModel.selectWarehouse(it) }
                    )
                }

                InventoryStep.SELECT_MODE -> {
                    ModeSelectionStep(
                        totalBaskets = uiState.totalWarehouseBaskets,
                        onSelectMode = { viewModel.selectInventoryMode(it) }
                    )
                }

                InventoryStep.SELECT_PRODUCT -> {
                    ProductSelectionStep(
                        productGroups = uiState.productGroups,
                        onSelectProduct = { viewModel.selectProduct(it) },
                        onSubmit = { viewModel.submitByProductInventory() },
                        canSubmit = viewModel.canSubmitByProductInventory(),
                        isSubmitting = uiState.isSubmitting,
                        showSubmitDialog = uiState.showSubmitDialog,
                        onShowSubmitDialog = { viewModel.showSubmitConfirmDialog() },
                        onDismissDialog = { viewModel.dismissSubmitDialog() },
                        onConfirmSubmit = { viewModel.confirmSubmit() }
                    )
                }

                InventoryStep.SELECT_BATCH -> {
                    BatchSelectionStep(
                        product = uiState.selectedProduct!!,
                        batches = uiState.productGroups
                            .find { it.product.itemcode == uiState.selectedProduct?.itemcode }
                            ?.batches ?: emptyList(),
                        onSelectBatch = { viewModel.selectBatch(it) },
                        onBack = { viewModel.deselectProduct() }
                    )
                }

                InventoryStep.SCANNING -> {
                    ScanningStep(
                        mode = uiState.inventoryMode!!,
                        product = uiState.selectedProduct,
                        batch = uiState.selectedBatch,
                        items = uiState.inventoryItems,
                        statistics = uiState.statistics,
                        scanMode = uiState.scanMode,
                        isScanning = scanState.isScanning,
                        scanType = scanState.scanType,
                        isValidating = uiState.isValidating,
                        onModeChange = { viewModel.setScanMode(it) },
                        onToggleScan = { viewModel.toggleScanFromButton() },
                        onRemoveItem = { viewModel.removeItem(it) },
                        onEditItem = { viewModel.showEditExtraItemDialog(it) },
                        onBack = {
                            when (uiState.inventoryMode) {
                                InventoryMode.FULL -> viewModel.resetInventory()
                                InventoryMode.BY_PRODUCT -> viewModel.deselectBatch()
                                else -> {}
                            }
                        }
                    )
                }

                else -> {}
            }
        }
    }

    if (uiState.showEditDialog && uiState.editingItem != null) {
        EditExtraItemDialog(
            item = uiState.editingItem!!,
            onDismiss = { viewModel.dismissEditDialog() },
            onConfirm = { product, quantity ->
                viewModel.updateExtraItem(
                    item = uiState.editingItem!!,
                    product = product,
                    quantity = quantity
                )
            },
            isSubmitting = uiState.isSubmitting,
            viewModel = viewModel
        )
    }
}

// ==================== 步驟指示器 ====================

@Composable
private fun InventoryStepIndicator(currentStep: InventoryStep) {
    val steps = listOf(
        InventoryStep.SELECT_WAREHOUSE to "選倉庫",
        InventoryStep.SELECT_MODE to "選模式",
        InventoryStep.SELECT_PRODUCT to "選產品",
        InventoryStep.SELECT_BATCH to "選批次",
        InventoryStep.SCANNING to "掃描"
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

// ==================== 步驟 1: 倉庫選擇 ====================

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
                        text = "請選擇盤點倉庫",
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
                    text = "請選擇籃子將要盤點的倉庫位置",
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
//            items(warehouses) { warehouse ->
//                WarehouseCard(
//                    warehouse = warehouse,
//                    onClick = { onSelectWarehouse(warehouse) }
//                )
//            }
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

// ==================== 步驟 2: 模式選擇 ====================

@Composable
private fun ModeSelectionStep(
    totalBaskets: Int,
    onSelectMode: (InventoryMode) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "選擇盤點模式",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "倉庫共有 $totalBaskets 個籃子",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            ModeCard(
                icon = Icons.Default.Inventory,
                title = "全部盤點",
                description = "盤點倉庫中的所有籃子\n適合完整盤點作業",
                onClick = { onSelectMode(InventoryMode.FULL) }
            )
        }

        item {
            ModeCard(
                icon = Icons.Default.Category,
                title = "按貨盤點",
                description = "按產品和批次分類盤點\n適合分批盤點作業",
                onClick = { onSelectMode(InventoryMode.BY_PRODUCT) }
            )
        }
    }
}

@Composable
private fun ModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
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

// ==================== 步驟 3: 產品選擇 ====================

@Composable
private fun ProductSelectionStep(
    productGroups: List<ProductGroup>,
    onSelectProduct: (Product) -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    showSubmitDialog: Boolean,
    onShowSubmitDialog: () -> Unit,
    onDismissDialog: () -> Unit,
    onConfirmSubmit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "選擇產品進行盤點",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // 顯示完成進度
                val completedProducts = productGroups.count { it.isCompleted }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (canSubmit) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (canSubmit) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "已完成: $completedProducts / ${productGroups.size} 產品",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (canSubmit) {
                            Color(0xFF4CAF50)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (canSubmit) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        items(productGroups) { group ->
            ProductGroupCard(
                group = group,
                onClick = { onSelectProduct(group.product) }
            )
        }

        // 提交按鈕
        item {
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onShowSubmitDialog,  // 改為顯示對話框
                enabled = canSubmit && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canSubmit) {
                        Color(0xFF4CAF50)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "提交中...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (canSubmit) {
                            "提交盤點數據"
                        } else {
                            "請完成所有產品盤點"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 提示信息
            if (!canSubmit) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
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
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "請完成所有產品的所有批次盤點後再提交",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // 確認對話框
    if (showSubmitDialog) {
        SubmitConfirmDialog(
            productGroups = productGroups,
            onDismiss = onDismissDialog,
            onConfirm = onConfirmSubmit
        )
    }
}

// ==================== 提交確認對話框 ====================

@Composable
private fun SubmitConfirmDialog(
    productGroups: List<ProductGroup>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "確認提交盤點數據",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "您即將提交以下盤點數據，提交後將無法修改：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 盤點匯總
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 匯總信息
                        val totalBaskets = productGroups.sumOf { it.totalBaskets }
                        val totalQuantity = productGroups.sumOf { it.totalQuantity }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "產品數量:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${productGroups.size} 種",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "籃子總數:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "$totalBaskets 籃",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "商品總數:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "$totalQuantity 個",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 產品列表
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "產品明細:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        productGroups.forEach { group ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        group.product.name,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    "${group.totalBaskets} 籃 • ${group.totalQuantity} 個",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "確認提交",
                    fontWeight = FontWeight.Bold
                )
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
private fun ProductGroupCard(
    group: ProductGroup,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(2.dp, Color.Black),
        colors = CardDefaults.cardColors(
            containerColor = if (group.isCompleted) {
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            val imageUrl = group.product.imageUrl?.let { url ->
                if (url.startsWith("http")) url else "${Constants.SERVER_URL}$url"
            }

            // 產品圖片
            AsyncImage(
                model = imageUrl,
                contentDescription = group.product.name,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            // 產品信息
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (group.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "已完成",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${group.batches.size} 批次 • ${group.totalBaskets} 籃 • ${group.totalQuantity} 個",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 完成進度
                val completedBatches = group.batches.count { it.isScanned }
                if (completedBatches > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "已完成: $completedBatches / ${group.batches.size} 批次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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

// ==================== 步驟 4: 批次選擇 ====================

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun BatchSelectionStep(
    product: Product,
    batches: List<BatchGroup>,
    onSelectBatch: (Batch) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SelectedProductHeader(
                product = product,
                onBack = onBack
            )
        }

        item {
            Text(
                text = "選擇批次進行盤點",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(batches) { batchGroup ->
            BatchGroupCard(
                batchGroup = batchGroup,
                onClick = { onSelectBatch(batchGroup.batch) }
            )
        }
    }
}

@Composable
private fun SelectedProductHeader(
    product: Product,
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
                    text = "當前產品",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun BatchGroupCard(
    batchGroup: BatchGroup,
    onClick: () -> Unit
) {
    val expiryDays = batchGroup.expiryDate?.let { expiryDate ->
        try {
            val expiry = LocalDate.parse(expiryDate, DateTimeFormatter.ISO_DATE)
            val today = LocalDate.now()
            ChronoUnit.DAYS.between(today, expiry)
        } catch (e: Exception) {
            null
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(2.dp, Color.Black),
        colors = CardDefaults.cardColors(
            containerColor = if (batchGroup.isScanned) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = batchGroup.batch.batch_code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (batchGroup.isScanned) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "已盤點",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 籃子數量
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${batchGroup.baskets.size} 籃 • ${batchGroup.totalQuantity} 個",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 生產日期
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "生產: ${batchGroup.batch.productionDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 到期日期
                if (batchGroup.expiryDate != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (expiryDays != null && expiryDays <= 7) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = "到期: ${batchGroup.expiryDate}" +
                                    if (expiryDays != null) " ($expiryDays 天)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (expiryDays != null && expiryDays <= 7) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
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

// ==================== 步驟 5: 掃描盤點 ====================

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun ScanningStep(
    mode: InventoryMode,
    product: Product?,
    batch: Batch?,
    items: List<InventoryItem>,
    statistics: InventoryStatistics,
    scanMode: ScanMode,
    isScanning: Boolean,
    scanType: com.kdl.rfidinventory.util.ScanType,
    isValidating: Boolean,
    onModeChange: (ScanMode) -> Unit,
    onToggleScan: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onEditItem: (InventoryItem) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 當前盤點信息
        item {
            CurrentInventoryHeader(
                mode = mode,
                product = product,
                batch = batch,
                onBack = onBack
            )
        }

        // 掃描設置
        item {
            ScanSettingsCard(
                scanMode = scanMode,
                isScanning = isScanning,
                scanType = scanType,
                isValidating = isValidating,
                onModeChange = onModeChange,
                onToggleScan = onToggleScan,
                statisticsContent = {
                    InventoryStatisticsCard(statistics = statistics)
                },
                helpText = when (scanMode) {
                    ScanMode.SINGLE -> "• RFID：點擊按鈕掃描\n• 條碼：使用實體掃碼槍\n• 單次模式：再按一次可取消"
                    ScanMode.CONTINUOUS -> "• 點擊開始連續掃描\n• 再次點擊或實體按鍵停止"
                }
            )
        }

        // 統計卡片
        item {
            StatisticsOverviewCard(statistics = statistics)
        }

        // 籃子列表
        if (items.isNotEmpty()) {
            item {
                Text(
                    text = "盤點清單 (${items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(
                items = items,
                key = { it.id }
            ) { item ->
                InventoryItemCard(
                    item = item,
                    onRemove = { onRemoveItem(item.id) },
                    onEdit = { onEditItem(item) }
                )
            }
        }
    }
}

@Composable
private fun CurrentInventoryHeader(
    mode: InventoryMode,
    product: Product?,
    batch: Batch?,
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
                    text = when (mode) {
                        InventoryMode.FULL -> "全部盤點"
                        InventoryMode.BY_PRODUCT -> "按貨盤點"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                product?.let {
                    Text(
                        text = it.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                batch?.let {
                    Text(
                        text = "批次: ${it.batch_code}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
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
private fun InventoryStatisticsCard(statistics: InventoryStatistics) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatItem(
            label = "總數",
            value = statistics.totalItems.toString(),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        StatItem(
            label = "已掃",
            value = statistics.scannedItems.toString(),
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f)
        )
        StatItem(
            label = "待掃",
            value = statistics.pendingItems.toString(),
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
        if (statistics.extraItems > 0) {
            StatItem(
                label = "額外",
                value = statistics.extraItems.toString(),
                color = Color(0xFFFF9800),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatItem(
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
private fun StatisticsOverviewCard(statistics: InventoryStatistics) {
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
                text = "盤點進度",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // 進度條
            LinearProgressIndicator(
                progress = if (statistics.totalItems > 0) {
                    statistics.scannedItems.toFloat() / statistics.totalItems
                } else 0f,
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
                    text = "${statistics.scannedItems} / ${statistics.totalItems}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val percentage = if (statistics.totalItems > 0) {
                    (statistics.scannedItems.toFloat() / statistics.totalItems * 100).toInt()
                } else 0

                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun InventoryItemCard(
    item: InventoryItem,
    onRemove: () -> Unit,
    onEdit: () -> Unit
) {
    BasketCard(
        basket = item.basket,
        mode = BasketCardMode.INVENTORY,
        inventoryStatus = item.status,
        scanCount = item.scanCount,
        maxCapacity = item.basket.product?.maxBasketCapacity,
        onQuantityChange = {},
        onRemove = onRemove,
        onEdit = onEdit,
        onTrack = {}
    )
}