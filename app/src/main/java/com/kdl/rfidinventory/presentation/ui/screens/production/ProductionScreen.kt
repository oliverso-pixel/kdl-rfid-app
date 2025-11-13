package com.kdl.rfidinventory.presentation.ui.screens.production

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kdl.rfidinventory.data.model.Batch
import com.kdl.rfidinventory.data.model.Product
import com.kdl.rfidinventory.data.model.ScannedBasket
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar
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
                    title = { Text("生產模式") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (uiState.selectedProduct != null || uiState.scannedBaskets.isNotEmpty()) {
                            IconButton(onClick = { viewModel.resetAll() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "重置")
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
                            isScanning = uiState.isScanning,
                            onModeChange = { viewModel.setScanMode(it) },
                            onStartScan = { viewModel.startScanning() },
                            onStopScan = { viewModel.stopScanning() }
                        )
                    }
                }

                // 步驟 4: 已掃描籃子列表
                if (uiState.scannedBaskets.isNotEmpty()) {
                    item {
                        Text(
                            text = "已掃描籃子 (${uiState.scannedBaskets.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(
                        items = uiState.scannedBaskets,
                        key = { it.uid }
                    ) { basket ->
                        ScannedBasketCard(
                            basket = basket,
                            maxCapacity = uiState.selectedProduct?.maxBasketCapacity ?: 0,
                            onQuantityChange = { newQuantity ->
                                viewModel.updateBasketQuantity(basket.uid, newQuantity)
                            },
                            onRemove = { viewModel.removeBasket(basket.uid) }
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
private fun ProductSelectionCard(
    selectedProduct: Product?,
    onSelectProduct: () -> Unit
) {
    Card(
        onClick = onSelectProduct,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedProduct != null) {
                AsyncImage(
                    model = selectedProduct.imageUrl,
                    contentDescription = selectedProduct.name,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedProduct.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "最大容量: ${selectedProduct.maxBasketCapacity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.Edit, contentDescription = "更改")
            } else {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "點擊選擇產品",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
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
    scanMode: com.kdl.rfidinventory.util.ScanMode,
    isScanning: Boolean,
    onModeChange: (com.kdl.rfidinventory.util.ScanMode) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isScanning) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScanModeSelector(
                currentMode = scanMode,
                onModeChange = onModeChange,
                enabled = !isScanning
            )

            if (isScanning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = "正在掃描中...",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onStopScan) {
                        Text("停止")
                    }
                }
            } else {
                Button(
                    onClick = onStartScan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("開始掃描")
                }
            }
        }
    }
}

@Composable
private fun ScannedBasketCard(
    basket: ScannedBasket,
    maxCapacity: Int,
    onQuantityChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    var quantityText by remember(basket.quantity) { mutableStateOf(basket.quantity.toString()) }
    var showError by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "籃子 UID",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = basket.uid,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "移除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "數量",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { newValue ->
                            quantityText = newValue
                            val newQuantity = newValue.toIntOrNull()
                            if (newQuantity != null && newQuantity in 1..maxCapacity) {
                                onQuantityChange(newQuantity)
                                showError = false
                            } else {
                                showError = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = showError,
                        supportingText = if (showError) {
                            { Text("請輸入 1-$maxCapacity 之間的數字") }
                        } else null,
                        singleLine = true
                    )

                    Column {
                        Text(
                            text = "信號強度",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${basket.rssi} dBm",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
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
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(products) { product ->
                    Card(
                        onClick = { onProductSelected(product) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = product.imageUrl,
                                contentDescription = product.name,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column {
                                Text(
                                    text = product.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "容量: ${product.maxBasketCapacity}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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