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
import com.kdl.rfidinventory.presentation.navigation.Screen
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel()
) {
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("RFID 庫存管理系統") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
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
                    )
                ),
                navController = navController
            )

            // 出貨區
            SectionCard(
                title = "出貨管理",
                items = listOf(
                    MenuItem(
                        title = "出貨模式",
                        description = "掃描籃子並選擇路線進行出貨",
                        icon = Icons.Default.LocalShipping,
                        route = Screen.Shipping.route
                    )
                ),
                navController = navController
            )

            // 其他功能
            SectionCard(
                title = "其他功能",
                items = listOf(
                    MenuItem(
                        title = "清除配置",
                        description = "清除籃子的產品、批次配置",
                        icon = Icons.Default.Clear,
                        route = Screen.Clear.route
                    ),
                    MenuItem(
                        title = "抽樣檢驗",
                        description = "標記籃子進行抽樣檢驗",
                        icon = Icons.Default.Science,
                        route = Screen.Sampling.route
                    ),
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
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                    onClick = { navController.navigate(item.route) }
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
            verticalAlignment = Alignment.CenterVertically
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