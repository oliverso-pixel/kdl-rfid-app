package com.kdl.rfidinventory.presentation.ui.screens.shipping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import com.kdl.rfidinventory.data.model.Route
import com.kdl.rfidinventory.presentation.ui.components.BasketListItem
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar
import com.kdl.rfidinventory.presentation.ui.components.ScanModeSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShippingScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShippingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRouteDialog by remember { mutableStateOf(false) }

    // 錯誤提示
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    // 成功提示
    uiState.successMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("出貨模式") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 確認出貨按鈕
                AnimatedVisibility(
                    visible = uiState.scannedBaskets.isNotEmpty() && uiState.selectedRoute != null
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.confirmShipping() },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(Icons.Default.LocalShipping, contentDescription = "確認出貨")
                    }
                }

                // 掃描按鈕
                ExtendedFloatingActionButton(
                    text = { Text(if (uiState.isScanning) "停止" else "掃描") },
                    icon = {
                        Icon(
                            if (uiState.isScanning) Icons.Default.Stop else Icons.Default.QrCodeScanner,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        if (uiState.isScanning) {
                            viewModel.stopScanning()
                        } else {
                            viewModel.startScanning()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 路線選擇卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showRouteDialog = true }
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
                            text = "出貨路線",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = uiState.selectedRoute?.name ?: "請選擇路線",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "選擇路線"
                    )
                }
            }

            // 掃描模式選擇器
            ScanModeSelector(
                currentMode = uiState.scanMode,
                onModeChange = { viewModel.setScanMode(it) }
            )

            // 統計卡片
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "已掃描籃子",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${uiState.scannedBaskets.size}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "總數量",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${uiState.totalQuantity}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 掃描狀態
            if (uiState.isScanning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "正在掃描...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // 籃子列表
            if (uiState.scannedBaskets.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "尚未掃描籃子",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "選擇路線後開始掃描",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.scannedBaskets, key = { it.uid }) { basket ->
                        SwipeToDeleteBasketItem(
                            basket = basket,
                            onDelete = { viewModel.removeBasket(basket.uid) }
                        )
                    }
                }
            }
        }
    }

    // 路線選擇對話框
    if (showRouteDialog) {
        RouteSelectionDialog(
            routes = uiState.routes,
            selectedRoute = uiState.selectedRoute,
            onDismiss = { showRouteDialog = false },
            onRouteSelected = {
                viewModel.selectRoute(it)
                showRouteDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteBasketItem(
    basket: com.kdl.rfidinventory.data.model.Basket,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "刪除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        content = {
            BasketListItem(
                basket = basket,
                onClick = {},
                modifier = modifier
            )
        }
    )
}

@Composable
private fun RouteSelectionDialog(
    routes: List<Route>,
    selectedRoute: Route?,
    onDismiss: () -> Unit,
    onRouteSelected: (Route) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇出貨路線") },
        text = {
            LazyColumn {
                items(routes) { route ->
                    Card(
                        onClick = { onRouteSelected(route) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (route == selectedRoute)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = route.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                route.description?.let { desc ->
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (route == selectedRoute) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已選擇",
                                    tint = MaterialTheme.colorScheme.primary
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