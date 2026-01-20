package com.kdl.rfidinventory.presentation.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kdl.rfidinventory.data.remote.websocket.WebSocketState
import com.kdl.rfidinventory.presentation.navigation.Screen
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    onLogout: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val webSocketState by viewModel.webSocketState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingOperationsCount.collectAsStateWithLifecycle()
    val webSocketEnabled by viewModel.webSocketEnabled.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val showLogoutDialog by viewModel.showLogoutDialog.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("RFID 庫存管理系統")
                            currentUser?.let { user ->
                                Text(
                                    text = "${user.name} (${user.role})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                )
                            }
                        }
                    },
                    actions = {
                        // 連接狀態指示器
                        WebSocketStatusIndicator(
                            webSocketState = webSocketState,
                            isOnline = isOnline,
                            pendingCount = pendingCount,
                            webSocketEnabled = webSocketEnabled,
                            onReconnect = { viewModel.reconnectWebSocket() },
                            onSync = { viewModel.syncPendingOperations() }
                        )

                        IconButton(onClick = { viewModel.showLogoutDialog() }) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = "登出",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 生產區
            SectionCard(
                title = "生產管理",
                items = listOf(
                    MenuItem(
                        title = "生產模式",
                        description = "掃描籃子並配置產品、批次、數量",
                        icon = Icons.Default.Build,
                        route = Screen.Production.route
                    )
                ),
                navController = navController
            )

            // 倉庫區
            SectionCard(
                title = "倉庫管理",
                items = listOf(
                    MenuItem(
                        title = "倉庫收貨",
                        description = "掃描籃子進行收貨登記",
                        icon = Icons.Default.Inventory2,
                        route = Screen.Receiving.route
                    ),
                    MenuItem(
                        title = "倉庫盤點",
                        description = "查看和管理倉庫庫存",
                        icon = Icons.Default.Assignment,
                        route = Screen.Inventory.route
                    ),
                    MenuItem(
                        title = "倉庫轉換",
                        description = "進行籃子倉庫籃子",
                        icon = Icons.Default.Cached,
                        route = ""
                    )
                ),
                navController = navController
            )

            // 出貨區
            SectionCard(
                title = "出貨管理",
                items = listOf(
                    MenuItem(
                        title = "上貨管理",
                        description = "掃描籃子進行上貨操作",
                        icon = Icons.Default.Upload,
                        route = Screen.Loading.route
                    ),
                    MenuItem(
                        title = "出貨驗證",
                        description = "驗證出貨數量與清單",
                        icon = Icons.Default.CheckCircle,
                        route = Screen.ShippingVerify.route
                    )
                ),
                navController = navController
            )

            SectionCard(
                title = "返回管理",
                items = listOf(
                    MenuItem(
                        title = "清除配置",
                        description = "清除籃子的產品、批次配置",
                        icon = Icons.Default.Clear,
                        route = Screen.Clear.route
                    ),
//                    MenuItem(
//                        title = "回貨配置",
//                        description = "回貨籃子的產品配置",
//                        icon = Icons.Default.AssignmentReturned,
//                        route = "",
//                    )
                ),
                navController = navController
            )

            // 其他功能
            SectionCard(
                title = "其他功能",
                items = listOf(

//                    MenuItem(
//                        title = "抽樣檢驗",
//                        description = "標記籃子進行抽樣檢驗",
//                        icon = Icons.Default.Science,
//                        route = Screen.Sampling.route
//                    ),
                    MenuItem(
                        title = "管理員設定",
                        description = "系統設定和權限管理",
                        icon = Icons.Default.Settings,
                        route = Screen.Admin.route
                    )
                ),
                navController = navController
            )
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLogoutDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("確認登出") },
            text = {
                Text("確定要登出嗎？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logout()
                        onLogout()
                    }
                ) {
                    Text("確認")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLogoutDialog() }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun WebSocketStatusIndicator(
    webSocketState: WebSocketState,
    isOnline: Boolean,
    pendingCount: Int,
    webSocketEnabled: Boolean,
    onReconnect: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { showMenu = true }) {
            when {
                !webSocketEnabled -> {
                    // WebSocket 禁用時顯示特殊圖標
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "WebSocket 已禁用",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                isOnline -> {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "在線",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                webSocketState is WebSocketState.Connecting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                }
                else -> {
                    BadgedBox(
                        badge = {
                            if (pendingCount > 0) {
                                Badge { Text(pendingCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "離線",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            // 狀態顯示
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = when {
                                !webSocketEnabled -> "WebSocket 已禁用"
                                webSocketState is WebSocketState.Connected -> "已連接"
                                webSocketState is WebSocketState.Connecting -> "連接中..."
                                webSocketState is WebSocketState.Disconnected ->
                                    "已斷開: ${webSocketState.reason ?: "未知原因"}"
                                webSocketState is WebSocketState.Error ->
                                    "錯誤: ${webSocketState.error}"
                                else -> "未知狀態"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (pendingCount > 0) {
                            Text(
                                text = "待同步操作: $pendingCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                onClick = { }
            )

            Divider()

            // 重新連接（只在啟用且未連接時顯示）
            if (webSocketEnabled && !isOnline) {
                DropdownMenuItem(
                    text = { Text("重新連接") },
                    onClick = {
                        onReconnect()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                )
            }

            // 同步數據
            if (pendingCount > 0 && isOnline) {
                DropdownMenuItem(
                    text = { Text("同步數據 ($pendingCount)") },
                    onClick = {
                        onSync()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Sync, contentDescription = null)
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    items: List<MenuItem>,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            items.forEach { item ->
                MenuItemCard(
                    item = item,
                    onClick = { navController.navigate(item.route) },
                )
                if (item != items.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MenuItemCard(
    item: MenuItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "前往",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class MenuItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String
)