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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar
import com.kdl.rfidinventory.presentation.ui.components.ScanSettingsCard
import com.kdl.rfidinventory.util.NetworkState
import com.kdl.rfidinventory.util.ScanMode
import com.kdl.rfidinventory.util.ScanType
import java.text.SimpleDateFormat
import java.util.*

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
    val baskets by viewModel.baskets.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val scanMode by viewModel.scanMode.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val basketManagementMode by viewModel.basketManagementMode.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedBaskets by remember { mutableStateOf<Set<String>>(emptySet()) }

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
                    title = {
                        Column {
                            Text(
                                if (isSelectionMode) {
                                    "已選擇 ${selectedBaskets.size} 個籃子"
                                } else {
                                    "籃子管理"
                                }
                            )
                            if (!isSelectionMode) {
                                Text(
                                    text = when (basketManagementMode) {
                                        BasketManagementMode.REGISTER -> "📝 登記模式"
                                        BasketManagementMode.QUERY -> "🔍 查詢模式"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (isSelectionMode) {
                                    // 退出選擇模式
                                    isSelectionMode = false
                                    selectedBaskets = emptySet()
                                } else {
                                    onNavigateBack()
                                }
                            }
                        ) {
                            Icon(
                                if (isSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                                contentDescription = if (isSelectionMode) "取消選擇" else "返回"
                            )
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            // ⭐ 選擇模式的操作按鈕
                            if (selectedBaskets.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        showDeleteConfirmDialog = "BATCH_DELETE"
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "刪除選中",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    selectedBaskets = if (selectedBaskets.size == baskets.size) {
                                        emptySet()
                                    } else {
                                        baskets.map { it.uid }.toSet()
                                    }
                                }
                            ) {
                                Text(
                                    if (selectedBaskets.size == baskets.size) "取消全選" else "全選",
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        } else {
                            // ⭐ 正常模式：顯示批量選擇按鈕
                            if (baskets.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        isSelectionMode = true
                                        selectedBaskets = emptySet()
                                    },
                                    enabled = !scanState.isScanning && !uiState.isRegistering
                                ) {
                                    Icon(
                                        Icons.Default.ChecklistRtl,
                                        contentDescription = "批量選擇",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isSelectionMode) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        titleContentColor = if (isSelectionMode) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                        navigationIconContentColor = if (isSelectionMode) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                        actionIconContentColor = if (isSelectionMode) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    )
                )
                ConnectionStatusBar(networkState = networkState)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ⭐ Tab 切換（選擇模式時隱藏）
            if (!isSelectionMode) {
                TabRow(
                    selectedTabIndex = when (basketManagementMode) {
                        BasketManagementMode.REGISTER -> 0
                        BasketManagementMode.QUERY -> 1
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = basketManagementMode == BasketManagementMode.REGISTER,
                        onClick = { viewModel.setBasketManagementMode(BasketManagementMode.REGISTER) },
                        enabled = !scanState.isScanning && !uiState.isRegistering,
                        text = { Text("登記新籃子") },
                        icon = { Icon(Icons.Default.AddBox, contentDescription = null) }
                    )
                    Tab(
                        selected = basketManagementMode == BasketManagementMode.QUERY,
                        onClick = { viewModel.setBasketManagementMode(BasketManagementMode.QUERY) },
                        enabled = !scanState.isScanning && !uiState.isRegistering,
                        text = { Text("查詢籃子") },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ... 搜索欄、掃描設置等（選擇模式時隱藏）
                if (!isSelectionMode) {
                    // 搜索欄
                    if (basketManagementMode == BasketManagementMode.QUERY) {
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.searchBaskets(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("搜索籃子 UID、產品名稱...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.searchBaskets("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "清除")
                                        }
                                    }
                                },
                                singleLine = true,
                                enabled = !scanState.isScanning && !uiState.isRegistering
                            )
                        }
                    }

                    // 掃描設置卡片
                    item {
                        ScanSettingsCard(
                            scanMode = scanMode,
                            isScanning = scanState.isScanning,
                            scanType = scanState.scanType,
                            onModeChange = { viewModel.setScanMode(it) },
                            onToggleScan = { viewModel.toggleScan() },
                            isValidating = uiState.isRegistering,
                            statisticsContent = {
                                BasketStatistics(
                                    totalCount = baskets.size,
                                    networkState = networkState
                                )
                            },
                            helpText = when (basketManagementMode) {
                                BasketManagementMode.REGISTER -> buildString {
                                    append("📝 登記模式\n")
                                    append("• RFID：點擊按鈕或按實體按鍵掃描\n")
                                    append("• 條碼：使用掃碼槍觸發\n")
                                    append("• 單次模式：掃到後自動停止\n")
                                    append("• 連續模式：持續掃描多個籃子\n")
                                    when (networkState) {
                                        is NetworkState.Connected -> append("• 在線：將檢查服務器並登記")
                                        is NetworkState.Disconnected -> append("• 離線：僅保存到本地")
                                        is NetworkState.Unknown -> append("• 網路狀態未知")
                                    }
                                }
                                BasketManagementMode.QUERY -> buildString {
                                    append("🔍 查詢模式\n")
                                    append("• RFID：點擊按鈕或按實體按鍵掃描\n")
                                    append("• 條碼：使用掃碼槍觸發\n")
                                    append("• 掃描後自動填入搜索框\n")
                                    append("• 也可手動輸入關鍵字搜索")
                                }
                            }
                        )
                    }
                }

                // 籃子列表標題
                if (baskets.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) {
                                    "搜索結果 (${baskets.size})"
                                } else {
                                    "所有籃子 (${baskets.size})"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                // 籃子列表
                if (uiState.isSearching) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (baskets.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = if (searchQuery.isNotEmpty()) {
                                        "找不到符合的籃子"
                                    } else {
                                        when (basketManagementMode) {
                                            BasketManagementMode.REGISTER -> "尚無籃子記錄\n開始掃描以登記新籃子"
                                            BasketManagementMode.QUERY -> "尚無籃子記錄"
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = baskets,
                        key = { it.uid }
                    ) { basket ->
                        SelectableBasketListItem(
                            basket = basket,
                            dateFormat = dateFormat,
                            isSelected = selectedBaskets.contains(basket.uid),
                            isSelectionMode = isSelectionMode,
                            onToggleSelection = { uid ->
                                selectedBaskets = if (selectedBaskets.contains(uid)) {
                                    selectedBaskets - uid
                                } else {
                                    selectedBaskets + uid
                                }
                            },
                            onDelete = { showDeleteConfirmDialog = basket.uid },
                            onItemClick = {
                                if (isSelectionMode) {
                                    // 選擇模式：切換選中狀態
                                    selectedBaskets = if (selectedBaskets.contains(basket.uid)) {
                                        selectedBaskets - basket.uid
                                    } else {
                                        selectedBaskets + basket.uid
                                    }
                                } else {
                                    // 正常模式：導航到詳情頁
                                    onNavigateToDetail(basket.uid)
                                }
                            },
                            enabled = !scanState.isScanning && !uiState.isRegistering
                        )
                    }
                }
            }
        }
    }

    // ⭐ 批量刪除確認對話框
    if (showDeleteConfirmDialog == "BATCH_DELETE") {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("批量刪除") },
            text = {
                Text("確定要刪除 ${selectedBaskets.size} 個籃子嗎？\n\n此操作僅刪除本地記錄，不影響服務器數據。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBatch(selectedBaskets.toList())
                        showDeleteConfirmDialog = null
                        isSelectionMode = false
                        selectedBaskets = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 單個刪除確認對話框（保持原有邏輯）
    showDeleteConfirmDialog?.let { uid ->
        if (uid != "BATCH_DELETE") {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("刪除籃子") },
                text = {
                    Text("確定要刪除籃子 $uid 嗎？\n\n此操作僅刪除本地記錄，不影響服務器數據。")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteBasket(uid)
                            showDeleteConfirmDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("刪除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = null }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

/**
 * ⭐ 可選擇的籃子列表項
 */
@Composable
private fun SelectableBasketListItem(
    basket: BasketEntity,
    dateFormat: SimpleDateFormat,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelection: (String) -> Unit,
    onDelete: () -> Unit,
    onItemClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        onClick = if (enabled) onItemClick else ({})
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // ⭐ 選擇模式：顯示 Checkbox
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection(basket.uid) },
                    enabled = enabled
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // UID（主要信息）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = basket.uid,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 狀態和產品信息
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 狀態標籤
                    Surface(
                        color = when (basket.status) {
                            BasketStatus.UNASSIGNED -> MaterialTheme.colorScheme.secondaryContainer
                            BasketStatus.IN_PRODUCTION -> MaterialTheme.colorScheme.primaryContainer
                            BasketStatus.RECEIVED -> MaterialTheme.colorScheme.tertiaryContainer
                            BasketStatus.IN_STOCK -> MaterialTheme.colorScheme.tertiaryContainer
                            BasketStatus.SHIPPED -> MaterialTheme.colorScheme.surfaceVariant
                            BasketStatus.SAMPLING -> MaterialTheme.colorScheme.errorContainer
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = when (basket.status) {
                                BasketStatus.UNASSIGNED -> "未分配"
                                BasketStatus.IN_PRODUCTION -> "生產中"
                                BasketStatus.RECEIVED -> "已收貨"
                                BasketStatus.IN_STOCK -> "在庫"
                                BasketStatus.SHIPPED -> "已出貨"
                                BasketStatus.SAMPLING -> "抽樣"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = when (basket.status) {
                                BasketStatus.UNASSIGNED -> MaterialTheme.colorScheme.onSecondaryContainer
                                BasketStatus.IN_PRODUCTION -> MaterialTheme.colorScheme.onPrimaryContainer
                                BasketStatus.RECEIVED -> MaterialTheme.colorScheme.onTertiaryContainer
                                BasketStatus.IN_STOCK -> MaterialTheme.colorScheme.onTertiaryContainer
                                BasketStatus.SHIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                                BasketStatus.SAMPLING -> MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }

                    // 產品名稱
                    if (basket.productName != null) {
                        Text(
                            text = basket.productName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 額外信息
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 產品 ID
                    if (basket.productID != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "產品:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = basket.productID,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 批次 ID
                    if (basket.batchId != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "批次:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = basket.batchId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 數量
                    if (basket.quantity > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "數量:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${basket.quantity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 更新時間
                    Text(
                        text = "更新: ${dateFormat.format(Date(basket.lastUpdated))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // ⭐ 正常模式：顯示刪除按鈕
            if (!isSelectionMode) {
                IconButton(
                    onClick = onDelete,
                    enabled = enabled,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "刪除",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 籃子統計信息
 */
@Composable
private fun BasketStatistics(
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
                        text = "本機籃子總數",
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

            // 網路狀態說明
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
 * 籃子列表項
 */
@Composable
private fun BasketListItem(
    basket: BasketEntity,
    dateFormat: SimpleDateFormat,
    onDelete: () -> Unit,
    onItemClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        onClick = if (enabled) onItemClick else ({})
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // UID（主要信息）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = basket.uid,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 狀態和產品信息
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 狀態標籤
                    Surface(
                        color = when (basket.status) {
                            BasketStatus.UNASSIGNED -> MaterialTheme.colorScheme.secondaryContainer
                            BasketStatus.IN_PRODUCTION -> MaterialTheme.colorScheme.primaryContainer
                            BasketStatus.RECEIVED -> MaterialTheme.colorScheme.tertiaryContainer
                            BasketStatus.IN_STOCK -> MaterialTheme.colorScheme.tertiaryContainer
                            BasketStatus.SHIPPED -> MaterialTheme.colorScheme.surfaceVariant
                            BasketStatus.SAMPLING -> MaterialTheme.colorScheme.errorContainer
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = when (basket.status) {
                                BasketStatus.UNASSIGNED -> "未分配"
                                BasketStatus.IN_PRODUCTION -> "生產中"
                                BasketStatus.RECEIVED -> "已收貨"
                                BasketStatus.IN_STOCK -> "在庫"
                                BasketStatus.SHIPPED -> "已出貨"
                                BasketStatus.SAMPLING -> "抽樣"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = when (basket.status) {
                                BasketStatus.UNASSIGNED -> MaterialTheme.colorScheme.onSecondaryContainer
                                BasketStatus.IN_PRODUCTION -> MaterialTheme.colorScheme.onPrimaryContainer
                                BasketStatus.RECEIVED -> MaterialTheme.colorScheme.onTertiaryContainer
                                BasketStatus.IN_STOCK -> MaterialTheme.colorScheme.onTertiaryContainer
                                BasketStatus.SHIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                                BasketStatus.SAMPLING -> MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }

                    // 產品名稱
                    if (basket.productName != null) {
                        Text(
                            text = basket.productName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 額外信息
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 產品 ID
                    if (basket.productID != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "產品:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = basket.productID,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 批次 ID
                    if (basket.batchId != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "批次:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = basket.batchId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 數量
                    if (basket.quantity > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "數量:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${basket.quantity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 更新時間
                    Text(
                        text = "更新: ${dateFormat.format(Date(basket.lastUpdated))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // 刪除按鈕
            IconButton(
                onClick = onDelete,
                enabled = enabled,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "刪除",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}