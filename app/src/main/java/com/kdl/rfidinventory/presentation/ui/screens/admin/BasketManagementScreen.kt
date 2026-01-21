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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.getBasketStatusText
import com.kdl.rfidinventory.data.model.getStatusColor_BMS_tag
import com.kdl.rfidinventory.data.model.getStatusColor_BMS
import com.kdl.rfidinventory.presentation.ui.components.BasketCard
import com.kdl.rfidinventory.presentation.ui.components.BasketCardMode
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar
import com.kdl.rfidinventory.presentation.ui.components.ScanSettingsCard
import com.kdl.rfidinventory.util.NetworkState
import com.kdl.rfidinventory.util.ScanMode
import com.kdl.rfidinventory.util.ScanModeAvailability
import timber.log.Timber
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
//    val viewModel: AdminViewModel = hiltViewModel()

    // 添加 log 確認
    LaunchedEffect(Unit) {
        Timber.d("🎯 BasketManagementScreen created, viewModel instance: ${viewModel.hashCode()}")
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val mode by viewModel.basketManagementMode.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    val scannedUids by viewModel.scannedUids.collectAsStateWithLifecycle()
    val queriedBasket by viewModel.queriedBasket.collectAsStateWithLifecycle()
    val localBaskets by viewModel.baskets.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val baskets by viewModel.baskets.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val scanMode by viewModel.scanMode.collectAsStateWithLifecycle()
    val basketManagementMode by viewModel.basketManagementMode.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    var showConfirmDialog by remember { mutableStateOf(false) }
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
//                TopAppBar(
//                    title = {
//                        Column {
//                            Text(
//                                if (isSelectionMode) {
//                                    "已選擇 ${selectedBaskets.size} 個籃子"
//                                } else {
//                                    "籃子管理"
//                                }
//                            )
//                            if (!isSelectionMode) {
//                                Text(
//                                    text = when (basketManagementMode) {
//                                        BasketManagementMode.REGISTER -> "📝 登記模式"
//                                        BasketManagementMode.QUERY -> "🔍 查詢模式"
//                                    },
//                                    style = MaterialTheme.typography.labelMedium,
//                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
//                                )
//                            }
//                        }
//                    },
//                    navigationIcon = {
//                        IconButton(
//                            onClick = {
//                                if (isSelectionMode) {
//                                    // 退出選擇模式
//                                    isSelectionMode = false
//                                    selectedBaskets = emptySet()
//                                } else {
//                                    onNavigateBack()
//                                }
//                            }
//                        ) {
//                            Icon(
//                                if (isSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
//                                contentDescription = if (isSelectionMode) "取消選擇" else "返回"
//                            )
//                        }
//                    },
//                    actions = {
//                        if (isSelectionMode) {
//                            // 選擇模式的操作按鈕
//                            if (selectedBaskets.isNotEmpty()) {
//                                IconButton(
//                                    onClick = {
//                                        showDeleteConfirmDialog = "BATCH_DELETE"
//                                    }
//                                ) {
//                                    Icon(
//                                        Icons.Default.Delete,
//                                        contentDescription = "刪除選中",
//                                        tint = MaterialTheme.colorScheme.error
//                                    )
//                                }
//                            }
//
//                            TextButton(
//                                onClick = {
//                                    selectedBaskets = if (selectedBaskets.size == baskets.size) {
//                                        emptySet()
//                                    } else {
//                                        baskets.map { it.uid }.toSet()
//                                    }
//                                }
//                            ) {
//                                Text(
//                                    if (selectedBaskets.size == baskets.size) "取消全選" else "全選",
//                                    color = MaterialTheme.colorScheme.onPrimary
//                                )
//                            }
//                        } else {
//                            // 正常模式：顯示批量選擇按鈕
//                            if (baskets.isNotEmpty()) {
//                                IconButton(
//                                    onClick = {
//                                        isSelectionMode = true
//                                        selectedBaskets = emptySet()
//                                    },
//                                    enabled = !scanState.isScanning && !uiState.isRegistering
//                                ) {
//                                    Icon(
//                                        Icons.Default.ChecklistRtl,
//                                        contentDescription = "批量選擇",
//                                        tint = MaterialTheme.colorScheme.onPrimary
//                                    )
//                                }
//                            }
//                        }
//                    },
//                    colors = TopAppBarDefaults.topAppBarColors(
//                        containerColor = if (isSelectionMode) {
//                            MaterialTheme.colorScheme.secondaryContainer
//                        } else {
//                            MaterialTheme.colorScheme.primary
//                        },
//                        titleContentColor = if (isSelectionMode) {
//                            MaterialTheme.colorScheme.onSecondaryContainer
//                        } else {
//                            MaterialTheme.colorScheme.onPrimary
//                        },
//                        navigationIconContentColor = if (isSelectionMode) {
//                            MaterialTheme.colorScheme.onSecondaryContainer
//                        } else {
//                            MaterialTheme.colorScheme.onPrimary
//                        },
//                        actionIconContentColor = if (isSelectionMode) {
//                            MaterialTheme.colorScheme.onSecondaryContainer
//                        } else {
//                            MaterialTheme.colorScheme.onPrimary
//                        }
//                    )
//                )
//                ConnectionStatusBar(networkState = networkState)

                TopAppBar(
                    title = { Text("籃子管理") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "返回") }
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
            if (mode == BasketManagementMode.REGISTER && scannedUids.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("提交 (${scannedUids.size})") },
                    icon = { Icon(Icons.Default.Upload, null) },
                    onClick = { showConfirmDialog = true }, // 觸發對話框
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
//            // Tab 切換（選擇模式時隱藏）
//            if (!isSelectionMode) {
//                TabRow(
//                    selectedTabIndex = when (basketManagementMode) {
//                        BasketManagementMode.REGISTER -> 0
//                        BasketManagementMode.QUERY -> 1
//                    },
//                    modifier = Modifier.fillMaxWidth()
//                ) {
//                    Tab(
//                        selected = basketManagementMode == BasketManagementMode.REGISTER,
//                        onClick = { viewModel.setBasketManagementMode(BasketManagementMode.REGISTER) },
//                        enabled = !scanState.isScanning && !uiState.isRegistering,
//                        text = { Text("登記新籃子") },
//                        icon = { Icon(Icons.Default.AddBox, contentDescription = null) }
//                    )
//                    Tab(
//                        selected = basketManagementMode == BasketManagementMode.QUERY,
//                        onClick = { viewModel.setBasketManagementMode(BasketManagementMode.QUERY) },
//                        enabled = !scanState.isScanning && !uiState.isRegistering,
//                        text = { Text("查詢籃子") },
//                        icon = { Icon(Icons.Default.Search, contentDescription = null) }
//                    )
//                }
//            }
//
//            LazyColumn(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .weight(1f),
//                contentPadding = PaddingValues(16.dp),
//                verticalArrangement = Arrangement.spacedBy(16.dp)
//            ) {
//                // ... 搜索欄、掃描設置等（選擇模式時隱藏）
//                if (!isSelectionMode) {
//                    // 搜索欄
//                    if (basketManagementMode == BasketManagementMode.QUERY) {
//                        item {
//                            OutlinedTextField(
//                                value = searchQuery,
//                                onValueChange = { viewModel.searchBaskets(it) },
//                                modifier = Modifier.fillMaxWidth(),
//                                placeholder = { Text("搜索籃子 UID、產品名稱...") },
//                                leadingIcon = {
//                                    Icon(Icons.Default.Search, contentDescription = null)
//                                },
//                                trailingIcon = {
//                                    if (searchQuery.isNotEmpty()) {
//                                        IconButton(onClick = { viewModel.searchBaskets("") }) {
//                                            Icon(Icons.Default.Clear, contentDescription = "清除")
//                                        }
//                                    }
//                                },
//                                singleLine = true,
//                                enabled = !scanState.isScanning && !uiState.isRegistering
//                            )
//                            Row(
//                                modifier = Modifier.fillMaxWidth(),
//                                horizontalArrangement = Arrangement.SpaceBetween,
//                                verticalAlignment = Alignment.CenterVertically
//                            ) {
//                                ScanSettingsCard(
//                                    scanMode = ScanMode.SINGLE,
//                                    isScanning = scanState.isScanning,
//                                    scanType = scanState.scanType,
//                                    onModeChange = {},
//                                    onToggleScan = { viewModel.toggleScan() },
//                                    availability = ScanModeAvailability.SINGLE_ONLY,
//                                    isValidating = uiState.isRegistering,
//                                    statisticsContent = {}
//                                )
//                            }
//                        }
//                    }
//
//                    if (basketManagementMode == BasketManagementMode.REGISTER) {
//                        // 掃描設置卡片
//                        item {
//                            ScanSettingsCard(
//                                scanMode = scanMode,
//                                isScanning = scanState.isScanning,
//                                scanType = scanState.scanType,
//                                onModeChange = { viewModel.setScanMode(it) },
//                                onToggleScan = { viewModel.toggleScan() },
//                                availability = ScanModeAvailability.BOTH,
//                                isValidating = uiState.isRegistering,
//                                statisticsContent = {
//                                    BasketStatistics(
//                                        totalCount = baskets.size,
//                                        networkState = networkState
//                                    )
//                                },
//                                helpText = when (basketManagementMode) {
//                                    BasketManagementMode.REGISTER -> buildString {
//                                        append("📝 登記模式\n")
//                                        append("• RFID：點擊按鈕掃描 RFID 標籤\n")
//                                        append("• 條碼：使用掃碼槍掃描條碼\n")
//                                        when (scanMode) {
//                                            ScanMode.SINGLE -> {
//                                                append("• 單次模式：掃到後自動停止\n")
//                                                append("• 再按一次實體按鍵可取消\n")
//                                            }
//                                            ScanMode.CONTINUOUS -> {
//                                                append("• 連續模式：持續掃描多個籃子\n")
//                                                append("• 點擊停止或按實體按鍵結束\n")
//                                            }
//                                        }
//                                        when (networkState) {
//                                            is NetworkState.Connected -> append("• 在線：將檢查服務器並登記")
//                                            is NetworkState.Disconnected -> append("• 離線：僅保存到本地")
//                                            is NetworkState.Unknown -> append("• 網路狀態未知")
//                                        }
//                                    }
//                                    BasketManagementMode.QUERY -> buildString {
//    //                                    append("🔍 查詢模式\n")
//    //                                    append("• RFID：點擊按鈕掃描 RFID 標籤\n")
//    //                                    append("• 條碼：使用掃碼槍掃描條碼\n")
//    //                                    when (scanMode) {
//    //                                        ScanMode.SINGLE -> {
//    //                                            append("• 單次模式：掃到後自動填入搜索框\n")
//    //                                            append("• 再按一次實體按鍵可取消\n")
//    //                                        }
//    //                                        ScanMode.CONTINUOUS -> {
//    //                                            append("• 連續模式：每次掃描自動搜索\n")
//    //                                            append("• 點擊停止或按實體按鍵結束\n")
//    //                                        }
//    //                                    }
//    //                                    append("• 也可手動輸入關鍵字搜索")
//                                    }
//                                }
//                            )
//                        }
//                    }
//                }
//
//                // 籃子列表標題
//                if (baskets.isNotEmpty()) {
//                    item {
//                        Row(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(vertical = 8.dp),
//                            horizontalArrangement = Arrangement.SpaceBetween,
//                            verticalAlignment = Alignment.CenterVertically
//                        ) {
//                            Text(
//                                text = if (searchQuery.isNotEmpty()) {
//                                    "搜索結果 (${baskets.size})"
//                                } else {
//                                    "所有籃子 (${baskets.size})"
//                                },
//                                style = MaterialTheme.typography.titleMedium
//                            )
//                        }
//                    }
//                }
//
//                // 籃子列表
//                if (uiState.isSearching) {
//                    item {
//                        Box(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(vertical = 32.dp),
//                            contentAlignment = Alignment.Center
//                        ) {
//                            CircularProgressIndicator()
//                        }
//                    }
//                } else if (baskets.isEmpty()) {
//                    item {
//                        Box(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(vertical = 64.dp),
//                            contentAlignment = Alignment.Center
//                        ) {
//                            Column(
//                                horizontalAlignment = Alignment.CenterHorizontally,
//                                verticalArrangement = Arrangement.spacedBy(8.dp)
//                            ) {
//                                Icon(
//                                    imageVector = Icons.Default.Inventory,
//                                    contentDescription = null,
//                                    modifier = Modifier.size(64.dp),
//                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
//                                )
//                                Text(
//                                    text = if (searchQuery.isNotEmpty()) {
//                                        "找不到符合的籃子"
//                                    } else {
//                                        when (basketManagementMode) {
//                                            BasketManagementMode.REGISTER -> "尚無籃子記錄\n開始掃描以登記新籃子"
//                                            BasketManagementMode.QUERY -> "尚無籃子記錄"
//                                        }
//                                    },
//                                    style = MaterialTheme.typography.bodyLarge,
//                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
//                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
//                                )
//                            }
//                        }
//                    }
//                } else {
//                    items(
//                        items = baskets,
//                        key = { it.uid }
//                    ) { basket ->
//                        SelectableBasketListItem(
//                            basket = basket,
//                            dateFormat = dateFormat,
//                            isSelected = selectedBaskets.contains(basket.uid),
//                            isSelectionMode = isSelectionMode,
//                            onToggleSelection = { uid ->
//                                selectedBaskets = if (selectedBaskets.contains(uid)) {
//                                    selectedBaskets - uid
//                                } else {
//                                    selectedBaskets + uid
//                                }
//                            },
//                            onDelete = { showDeleteConfirmDialog = basket.uid },
//                            onItemClick = {
//                                if (isSelectionMode) {
//                                    // 選擇模式：切換選中狀態
//                                    selectedBaskets = if (selectedBaskets.contains(basket.uid)) {
//                                        selectedBaskets - basket.uid
//                                    } else {
//                                        selectedBaskets + basket.uid
//                                    }
//                                } else {
//                                    // 正常模式：導航到詳情頁
//                                    onNavigateToDetail(basket.uid)
//                                }
//                            },
//                            enabled = !scanState.isScanning && !uiState.isRegistering
//                        )
//                    }
//                }
//            }
            // 掃描控制區 (共用)
            if (mode != BasketManagementMode.LOCAL) { // 本地模式主要靠搜尋框
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (mode == BasketManagementMode.REGISTER) "批量掃描模式" else "單筆查詢模式",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (scanState.isScanning) "掃描中..." else "點擊開始掃描",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (scanState.isScanning) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        Button(
                            onClick = { viewModel.toggleScan() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (scanState.isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(if (scanState.isScanning) Icons.Default.Stop else Icons.Default.QrCodeScanner, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (scanState.isScanning) "停止" else "掃描")
                        }
                    }
                }
            }

            // 內容區
            Box(modifier = Modifier.weight(1f)) {
                when (mode) {
                    BasketManagementMode.REGISTER -> {
                        RegisterListContent(
                            uids = scannedUids.toList(),
                            onRemove = { viewModel.removeScannedUid(it) },
                            onClear = { viewModel.clearScannedUids() }
                        )
                    }
                    BasketManagementMode.QUERY -> {
                        QueryContent(
                            basket = queriedBasket,
                            isLoading = uiState.isLoading,
                            onUpdate = { b, s, w -> viewModel.updateBasketInfo(b, s, w, null) }
                        )
                    }
                    BasketManagementMode.LOCAL -> {
                        LocalListContent(
                            baskets = localBaskets,
                            onItemClick = onNavigateToDetail,
                            // ... 搜尋框邏輯 ...
                        )
                    }
                }
            }
        }
    }

    // 批量刪除確認對話框
//    if (showDeleteConfirmDialog == "BATCH_DELETE") {
//        AlertDialog(
//            onDismissRequest = { showDeleteConfirmDialog = null },
//            icon = {
//                Icon(
//                    imageVector = Icons.Default.Warning,
//                    contentDescription = null,
//                    tint = MaterialTheme.colorScheme.error
//                )
//            },
//            title = { Text("批量刪除") },
//            text = {
//                Text("確定要刪除 ${selectedBaskets.size} 個籃子嗎？\n\n此操作僅刪除本地記錄，不影響服務器數據。")
//            },
//            confirmButton = {
//                Button(
//                    onClick = {
//                        viewModel.deleteBatch(selectedBaskets.toList())
//                        showDeleteConfirmDialog = null
//                        isSelectionMode = false
//                        selectedBaskets = emptySet()
//                    },
//                    colors = ButtonDefaults.buttonColors(
//                        containerColor = MaterialTheme.colorScheme.error
//                    )
//                ) {
//                    Text("刪除")
//                }
//            },
//            dismissButton = {
//                TextButton(onClick = { showDeleteConfirmDialog = null }) {
//                    Text("取消")
//                }
//            }
//        )
//    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("確認登記") },
            text = { Text("即將註冊 ${scannedUids.size} 個新籃子，確定提交嗎？") },
            confirmButton = {
                Button(onClick = {
                    viewModel.submitRegistration() // 呼叫 API
                    showConfirmDialog = false
                }) { Text("確認") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("取消") }
            }
        )
    }

    // 單個刪除確認對話框（保持原有邏輯）
//    showDeleteConfirmDialog?.let { uid ->
//        if (uid != "BATCH_DELETE") {
//            AlertDialog(
//                onDismissRequest = { showDeleteConfirmDialog = null },
//                icon = {
//                    Icon(
//                        imageVector = Icons.Default.Warning,
//                        contentDescription = null,
//                        tint = MaterialTheme.colorScheme.error
//                    )
//                },
//                title = { Text("刪除籃子") },
//                text = {
//                    Text("確定要刪除籃子 $uid 嗎？\n\n此操作僅刪除本地記錄，不影響服務器數據。")
//                },
//                confirmButton = {
//                    Button(
//                        onClick = {
//                            viewModel.deleteBasket(uid)
//                            showDeleteConfirmDialog = null
//                        },
//                        colors = ButtonDefaults.buttonColors(
//                            containerColor = MaterialTheme.colorScheme.error
//                        )
//                    ) {
//                        Text("刪除")
//                    }
//                },
//                dismissButton = {
//                    TextButton(onClick = { showDeleteConfirmDialog = null }) {
//                        Text("取消")
//                    }
//                }
//            )
//        }
//    }
}

/**
 * 可選擇的籃子列表項
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
            // 選擇模式：顯示 Checkbox
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
                        color = getStatusColor_BMS_tag(basket.status),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = getBasketStatusText(basket.status),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = getStatusColor_BMS(basket.status)
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
                    if (basket.productId != null) {
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
                                text = basket.productId,
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

            // 正常模式：顯示刪除按鈕
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
                        color = getStatusColor_BMS_tag(basket.status),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = getBasketStatusText(basket.status),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = getStatusColor_BMS(basket.status)
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
                    if (basket.productId != null) {
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
                                text = basket.productId,
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

@Composable
fun RegisterListContent(
    uids: List<String>,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (uids.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已掃描: ${uids.size}", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClear) {
                    Text("全部清除", color = MaterialTheme.colorScheme.error)
                }
            }

            // ✅ 使用 key 幫助 Compose 識別項目變化，解決列表不更新問題
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = uids,
                    key = { it } // 使用 UID 作為唯一鍵值
                ) { uid ->
                    Card {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Nfc, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(uid, style = MaterialTheme.typography.bodyLarge)
                            }
                            IconButton(onClick = { onRemove(uid) }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("請掃描籃子標籤以加入列表", color = Color.Gray)
            }
        }
    }
}

@Composable
fun QueryContent(basket: Basket?, isLoading: Boolean, onUpdate: (Basket, String?, String?) -> Unit) {
    var showEditDialog by remember { mutableStateOf(false) }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (basket != null) {
        Column(modifier = Modifier.padding(16.dp)) {
            BasketCard(
                basket = basket,
                mode = BasketCardMode.INVENTORY, // 借用樣式
                onQuantityChange = {},
                onRemove = {}
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { showEditDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("修改資訊")
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("請掃描籃子以查詢詳情", color = Color.Gray)
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

@Composable
fun EditBasketDialog(basket: Basket, onDismiss: () -> Unit, onConfirm: (String?, String?) -> Unit) {
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
                // 這裡可以用 ExposedDropdownMenuBox 來選狀態，這裡簡化為輸入框
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

@Composable
fun LocalListContent(baskets: List<com.kdl.rfidinventory.data.local.entity.BasketEntity>, onItemClick: (String) -> Unit) {
    // 復用之前的列表邏輯
    // ...
}
