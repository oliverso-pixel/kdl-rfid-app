package com.kdl.rfidinventory.presentation.ui.screens.admin

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.presentation.ui.components.BasketCard
import com.kdl.rfidinventory.presentation.ui.components.BasketCardMode
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar
import com.kdl.rfidinventory.presentation.ui.components.ScanSettingsCard
import com.kdl.rfidinventory.util.NetworkState
import com.kdl.rfidinventory.util.ScanMode
import com.kdl.rfidinventory.util.ScanModeAvailability

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasketManagementScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val mode by viewModel.basketManagementMode.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val scanMode by viewModel.scanMode.collectAsStateWithLifecycle()

    val scannedUids by viewModel.scannedUids.collectAsStateWithLifecycle()
    val queriedBasket by viewModel.queriedBasket.collectAsStateWithLifecycle()
    val localBaskets by viewModel.baskets.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showConfirmDialog by remember { mutableStateOf(false) }

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
                    title = { Text("籃子管理") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                ConnectionStatusBar(networkState = networkState)

                // Tabs
                TabRow(selectedTabIndex = mode.ordinal) {
                    Tab(
                        selected = mode == BasketManagementMode.REGISTER,
                        onClick = { viewModel.setBasketManagementMode(BasketManagementMode.REGISTER) },
                        text = { Text("批量登記") },
                        icon = { Icon(Icons.Default.AddBox, null) }
                    )
                    Tab(
                        selected = mode == BasketManagementMode.QUERY,
                        onClick = { viewModel.setBasketManagementMode(BasketManagementMode.QUERY) },
                        text = { Text("查詢修改") },
                        icon = { Icon(Icons.Default.Search, null) }
                    )
                    Tab(
                        selected = mode == BasketManagementMode.LOCAL,
                        onClick = { viewModel.setBasketManagementMode(BasketManagementMode.LOCAL) },
                        text = { Text("本地管理") },
                        icon = { Icon(Icons.Default.Storage, null) }
                    )
                }
            }
        },
        floatingActionButton = {
            // 僅在登記模式且有資料時顯示
            if (mode == BasketManagementMode.REGISTER && scannedUids.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("提交 (${scannedUids.size})") },
                    icon = { Icon(Icons.Default.Upload, null) },
                    onClick = { showConfirmDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 掃描設置區
            when (mode) {
                BasketManagementMode.REGISTER -> {
                    item {
                        ScanSettingsCard(
                            scanMode = scanMode,
                            isScanning = scanState.isScanning,
                            scanType = scanState.scanType,
                            onModeChange = { viewModel.setScanMode(it) },
                            onToggleScan = { viewModel.toggleScan() },
                            availability = ScanModeAvailability.BOTH,
                            statisticsContent = {
                                BasketStatisticsCard(
                                    totalCount = scannedUids.size,
                                    networkState = networkState
                                )
                            },
                            helpText = when (scanMode) {
                                ScanMode.SINGLE -> "• 單次模式：掃描一個籃子後自動停止\n• RFID：點擊按鈕掃描\n• 條碼：使用掃碼槍"
                                ScanMode.CONTINUOUS -> "• 連續模式：持續掃描多個籃子\n• 點擊按鈕開始/停止\n• 也可使用實體按鍵控制"
                            }
                        )
                    }
                }
                BasketManagementMode.QUERY -> {
                    item {
                        ScanSettingsCard(
                            scanMode = ScanMode.SINGLE,
                            isScanning = scanState.isScanning,
                            scanType = scanState.scanType,
                            onModeChange = {},
                            onToggleScan = { viewModel.toggleScan() },
                            availability = ScanModeAvailability.SINGLE_ONLY,
                            helpText = "• 單筆查詢模式\n• RFID：點擊按鈕掃描\n• 條碼：使用掃碼槍\n• 掃描後立即查詢籃子詳情"
                        )
                    }
                }
                BasketManagementMode.LOCAL -> {
                    // 本地模式：不顯示掃描設置
                }
            }

            // 內容區
            when (mode) {
                BasketManagementMode.REGISTER -> {
                    // 登記模式：列表內容
                    if (scannedUids.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "已掃描: ${scannedUids.size}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                TextButton(onClick = { viewModel.clearScannedUids() }) {
                                    Text("全部清除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        items(
                            items = scannedUids.toList(),
                            key = { it }
                        ) { uid ->
                            Card {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Nfc,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(uid, style = MaterialTheme.typography.bodyLarge)
                                    }
                                    IconButton(onClick = { viewModel.removeScannedUid(uid) }) {
                                        Icon(Icons.Default.Close, null)
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.QrCodeScanner,
                                        null,
                                        modifier = Modifier.size(64.dp),
                                        tint = Color.Gray
                                    )
                                    Text("請掃描籃子標籤以加入列表", color = Color.Gray)
                                }
                            }
                        }
                    }
                }
                BasketManagementMode.QUERY -> {
                    // 查詢模式：顯示查詢結果
                    item {
                        QueryContentCard(
                            basket = queriedBasket,
                            isLoading = uiState.isLoading,
                            onUpdate = { b, s, w ->
                                viewModel.updateBasketInfo(b, s, w, null)
                            }
                        )
                    }
                }
                BasketManagementMode.LOCAL -> {
                    // 本地模式：搜尋框 + 列表
                    item {
                        var searchQuery by remember { mutableStateOf("") }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchBaskets(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索籃子 UID、產品名稱...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        viewModel.searchBaskets("")
                                    }) {
                                        Icon(Icons.Default.Clear, "清除")
                                    }
                                }
                            },
                            singleLine = true
                        )
                    }

                    if (localBaskets.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Inventory,
                                        null,
                                        modifier = Modifier.size(64.dp),
                                        tint = Color.Gray
                                    )
                                    Text("尚無籃子記錄", color = Color.Gray)
                                }
                            }
                        }
                    } else {
                        items(
                            items = localBaskets,
                            key = { it.uid }
                        ) { basket ->
                            BasketListItem(
                                basket = basket,
                                onClick = { onNavigateToDetail(basket.uid) }
                            )
                        }
                    }
                }
            }
        }
    }

    // 提交確認對話框
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("確認登記") },
            text = { Text("即將註冊 ${scannedUids.size} 個新籃子，確定提交嗎？") },
            confirmButton = {
                Button(onClick = {
                    viewModel.submitRegistration()
                    showConfirmDialog = false
                }) { Text("確認") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 掃描控制卡片
 */
@Composable
private fun BasketStatisticsCard(
    totalCount: Int,
    networkState: NetworkState
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "已掃描籃子",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "$totalCount 個",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // 網路狀態標籤
            Surface(
                color = when (networkState) {
                    is NetworkState.Connected -> MaterialTheme.colorScheme.primaryContainer
                    is NetworkState.Disconnected -> MaterialTheme.colorScheme.errorContainer
                    is NetworkState.Unknown -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (networkState) {
                            is NetworkState.Connected -> Icons.Default.CloudDone
                            is NetworkState.Disconnected -> Icons.Default.CloudOff
                            is NetworkState.Unknown -> Icons.Default.CloudQueue
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when (networkState) {
                            is NetworkState.Connected -> MaterialTheme.colorScheme.onPrimaryContainer
                            is NetworkState.Disconnected -> MaterialTheme.colorScheme.onErrorContainer
                            is NetworkState.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = when (networkState) {
                            is NetworkState.Connected -> "在線"
                            is NetworkState.Disconnected -> "離線"
                            is NetworkState.Unknown -> "未知"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (networkState) {
                            is NetworkState.Connected -> MaterialTheme.colorScheme.onPrimaryContainer
                            is NetworkState.Disconnected -> MaterialTheme.colorScheme.onErrorContainer
                            is NetworkState.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

/**
 * 查詢模式內容卡片
 */
@Composable
private fun QueryContentCard(
    basket: Basket?,
    isLoading: Boolean,
    onUpdate: (Basket, String?, String?) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        basket != null -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BasketCard(
                    basket = basket,
                    mode = BasketCardMode.INVENTORY,
                    onQuantityChange = {},
                    onRemove = {}
                )
                Button(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("修改資訊")
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Text("請掃描籃子以查詢詳情", color = Color.Gray)
                }
            }
        }
    }

    if (showEditDialog && basket != null) {
        EditBasketDialog(
            basket = basket,
            onDismiss = { showEditDialog = false },
            onConfirm = { s, w ->
                onUpdate(basket, s, w)
                showEditDialog = false
            }
        )
    }
}

/**
 * 籃子列表項目
 */
@Composable
private fun BasketListItem(
    basket: BasketEntity,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Nfc,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(basket.uid, style = MaterialTheme.typography.bodyLarge)
                if (basket.productName != null) {
                    Text(
                        basket.productName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
        }
    }
}

@Composable
fun EditBasketDialog(
    basket: Basket,
    onDismiss: () -> Unit,
    onConfirm: (String?, String?) -> Unit
) {
    var status by remember { mutableStateOf(basket.status.name) }
    var warehouseId by remember { mutableStateOf(basket.warehouseId ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改籃子資訊") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = warehouseId,
                    onValueChange = { warehouseId = it },
                    label = { Text("倉庫 ID") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it },
                    label = { Text("狀態 (IN_STOCK, DAMAGED...)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(status, warehouseId.ifBlank { null }) }) {
                Text("更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
