package com.kdl.rfidinventory.presentation.ui.screens.production

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.presentation.ui.components.BasketCard
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar
import com.kdl.rfidinventory.presentation.ui.components.ProductionStatistics
import com.kdl.rfidinventory.presentation.ui.components.ScanSettingsCard
import com.kdl.rfidinventory.util.ScanMode
import com.kdl.rfidinventory.util.ScanType
import timber.log.Timber

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
                                        text = "總計: ${uiState.totalScanCount} 次",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
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
private fun ProductSelectionDialog(
    products: List<Product>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onProductSelected: (Product) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var isDialogReady by remember { mutableStateOf(false) }

    // 監聽 TextField 輸入的條碼數據
    var previousQuery by remember { mutableStateOf("") }

    // 監聽 searchQuery 變化，檢測條碼掃描
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty() && searchQuery != previousQuery) {
            val queryLength = searchQuery.length
            val previousLength = previousQuery.length

            //  檢測快速輸入（條碼掃描的特徵）
            if (queryLength - previousLength >= 5) {  // 一次性輸入 5+ 個字符
                Timber.d("📦 Barcode detected via TextField: $searchQuery")

                // 自動搜索並選擇
                kotlinx.coroutines.delay(100)
                val filteredProducts = products.filter { product ->
                    product.id.lowercase().contains(searchQuery.lowercase()) ||
                            product.name.lowercase().contains(searchQuery.lowercase()) ||
                            (product.barcodeId?.toString()?.contains(searchQuery) == true) ||
                            (product.qrcodeId?.lowercase()?.contains(searchQuery.lowercase()) == true)
                }

                if (filteredProducts.size == 1) {
                    Timber.d("🎯 Auto-selecting product: ${filteredProducts.first().name}")
                    onProductSelected(filteredProducts.first())
                }
            }

            previousQuery = searchQuery
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            focusRequester.requestFocus()
            isDialogReady = true
            Timber.d("✅ Product dialog ready, focus requested")
        } catch (e: Exception) {
            Timber.e(e, "Failed to request focus")
        }
    }

    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) {
            products
        } else {
            val query = searchQuery.trim().lowercase()
            products.filter { product ->
                product.id.lowercase().contains(query) ||
                        product.name.lowercase().contains(query) ||
                        (product.barcodeId?.toString()?.contains(query) == true) ||
                        (product.qrcodeId?.lowercase()?.contains(query) == true)
            }
        }
    }

    val onEnterPressed = {
        if (filteredProducts.size == 1) {
            Timber.d("⌨️ Enter pressed, selecting product: ${filteredProducts.first().name}")
            onProductSelected(filteredProducts.first())
        }
    }

    AlertDialog(
        onDismissRequest = {
            Timber.d("🚪 Dialog dismissed")
            onSearchQueryChange("")
            onDismiss()
        },
        title = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("選擇產品")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isDialogReady) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = if (isDialogReady) "可用掃碼槍" else "準備中...",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDialogReady) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // ⭐ 搜索框 - 關鍵修改
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { newValue ->
                        Timber.d("📝 Search query changed: '$newValue' (prev: '$previousQuery')")
                        onSearchQueryChange(newValue)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onKeyEvent { keyEvent ->
                            when {
                                keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown -> {
                                    onEnterPressed()
                                    true
                                }
                                else -> false
                            }
                        },
                    placeholder = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("搜索或掃描條碼/QR碼")
                        }
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                Timber.d("🗑️ Clearing search query")
                                onSearchQueryChange("")
                                previousQuery = ""  // ⭐ 重置 previousQuery
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    enabled = isDialogReady,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            Timber.d("🔍 Search action triggered")
                            onEnterPressed()
                        }
                    )
                )

                // ⭐ 結果數量提示和匹配狀態
                if (searchQuery.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "找到 ${filteredProducts.size} 個結果",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (filteredProducts.size == 1) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "精確匹配",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        text = {
            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "找不到符合條件的產品",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                text = "請檢查條碼是否正確或嘗試其他關鍵字",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredProducts) { product ->
                        ProductItem(
                            product = product,
                            searchQuery = searchQuery,
                            onClick = {
                                Timber.d("🖱️ Product clicked: ${product.name}")
                                onProductSelected(product)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (filteredProducts.size == 1) {
                Button(
                    onClick = {
                        Timber.d("✅ Confirm button clicked")
                        onProductSelected(filteredProducts.first())
                    },
                    enabled = isDialogReady
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("選擇此產品")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    Timber.d("❌ Cancel button clicked")
                    onSearchQueryChange("")
                    onDismiss()
                }
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ProductItem(
    product: Product,
    searchQuery: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 產品圖片
            AsyncImage(
                model = product.imageUrl,
                contentDescription = product.name,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            // 產品信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 產品名稱（高亮匹配文字）
                HighlightedText(
                    text = product.name,
                    highlight = searchQuery,
                    style = MaterialTheme.typography.titleMedium
                )

                // 產品 ID
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ID:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HighlightedText(
                        text = product.id,
                        highlight = searchQuery,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Barcode（如果有）
                product.barcodeId?.let { barcode ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "條碼:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HighlightedText(
                            text = barcode.toString(),
                            highlight = searchQuery,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // QR Code（如果有）
                product.qrcodeId?.let { qrcode ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "QR:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HighlightedText(
                            text = qrcode,
                            highlight = searchQuery,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // 容量
                Text(
                    text = "最大容量: ${product.maxBasketCapacity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HighlightedText(
    text: String,
    highlight: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    if (highlight.isBlank()) {
        Text(
            text = text,
            style = style,
            modifier = modifier
        )
        return
    }

    val highlightColor = MaterialTheme.colorScheme.primary
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        val lowerText = text.lowercase()
        val lowerHighlight = highlight.lowercase()

        while (currentIndex < text.length) {
            val startIndex = lowerText.indexOf(lowerHighlight, currentIndex)

            if (startIndex == -1) {
                // 沒有找到更多匹配，添加剩餘文字
                append(text.substring(currentIndex))
                break
            }

            // 添加匹配前的文字
            if (startIndex > currentIndex) {
                append(text.substring(currentIndex, startIndex))
            }

            // 添加高亮文字
            val endIndex = startIndex + lowerHighlight.length
            withStyle(
                style = SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.Bold,
                    background = highlightColor.copy(alpha = 0.2f)
                )
            ) {
                append(text.substring(startIndex, endIndex))
            }

            currentIndex = endIndex
        }
    }

    Text(
        text = annotatedString,
        style = style,
        modifier = modifier
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