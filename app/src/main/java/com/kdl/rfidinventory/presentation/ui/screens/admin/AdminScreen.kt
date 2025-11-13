package com.kdl.rfidinventory.presentation.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdl.rfidinventory.presentation.ui.components.ConnectionStatusBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
                    title = { Text("管理員設定") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 同步設定區
            SettingsSection(title = "資料同步") {
                // 待同步記錄
                SettingItem(
                    icon = Icons.Default.CloudOff,
                    title = "待同步記錄",
                    subtitle = "${uiState.pendingOperationsCount} 筆",
                    onClick = null
                )

                // 立即同步按鈕
                SettingItem(
                    icon = Icons.Default.Sync,
                    title = "立即同步",
                    subtitle = if (uiState.isSyncing) "同步中..." else "同步待上傳的資料",
                    onClick = { viewModel.syncPendingOperations() },
                    enabled = !uiState.isSyncing && uiState.pendingOperationsCount > 0
                )

                // 自動同步開關
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "自動同步",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "網路連線時自動上傳資料",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = uiState.settings.autoSync,
                        onCheckedChange = { viewModel.toggleAutoSync(it) }
                    )
                }
            }

            // 伺服器設定區
            SettingsSection(title = "伺服器設定") {
                SettingItem(
                    icon = Icons.Default.Storage,
                    title = "伺服器地址",
                    subtitle = uiState.settings.serverUrl,
                    onClick = { viewModel.showServerUrlDialog() }
                )

                SettingItem(
                    icon = Icons.Default.Timer,
                    title = "掃描逾時",
                    subtitle = "${uiState.settings.scanTimeoutSeconds} 秒",
                    onClick = { viewModel.showScanTimeoutDialog() }
                )
            }

            // 資料管理區
            SettingsSection(title = "資料管理") {
                SettingItem(
                    icon = Icons.Default.Download,
                    title = "匯出日誌",
                    subtitle = if (uiState.isExporting) "匯出中..." else "匯出系統操作日誌",
                    onClick = { viewModel.exportLogs() },
                    enabled = !uiState.isExporting
                )

                SettingItem(
                    icon = Icons.Default.DeleteForever,
                    title = "清除所有資料",
                    subtitle = "刪除本地所有資料（不可恢復）",
                    onClick = { viewModel.showClearDataDialog() },
                    textColor = MaterialTheme.colorScheme.error
                )
            }

            // 系統資訊區
            SettingsSection(title = "系統資訊") {
                InfoItem(
                    title = "應用程式版本",
                    value = uiState.settings.appVersion
                )

                InfoItem(
                    title = "資料庫版本",
                    value = uiState.settings.databaseVersion.toString()
                )
            }
        }
    }

    // 清除資料確認對話框
    if (uiState.showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearDataDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("清除所有資料") },
            text = {
                Text("此操作將刪除所有本地資料，包括籃子記錄、待同步資料等。\n\n此操作無法撤銷，確定要繼續嗎？")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearAllData() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("確認清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearDataDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // 伺服器地址設定對話框
    if (uiState.showServerUrlDialog) {
        ServerUrlDialog(
            currentUrl = uiState.settings.serverUrl,
            onDismiss = { viewModel.dismissServerUrlDialog() },
            onConfirm = { url ->
                viewModel.updateServerUrl(url)
                viewModel.dismissServerUrlDialog()
            }
        )
    }

    // 掃描逾時設定對話框
    if (uiState.showScanTimeoutDialog) {
        ScanTimeoutDialog(
            currentTimeout = uiState.settings.scanTimeoutSeconds,
            onDismiss = { viewModel.dismissScanTimeoutDialog() },
            onConfirm = { timeout ->
                viewModel.updateScanTimeout(timeout)
                viewModel.dismissScanTimeoutDialog()
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            Divider()
            content()
        }
    }
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    val modifier = if (onClick != null) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    }

    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) textColor else textColor.copy(alpha = 0.38f)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
        ) {
            content()
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}

@Composable
private fun InfoItem(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ServerUrlDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "伺服器地址",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        errorMessage = null
                    },
                    label = { Text("伺服器 URL") },
                    placeholder = { Text("http://192.168.1.100:8080") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            when {
                                url.isBlank() -> errorMessage = "請輸入伺服器地址"
                                !url.startsWith("http://") && !url.startsWith("https://") ->
                                    errorMessage = "URL 必須以 http:// 或 https:// 開頭"
                                else -> onConfirm(url.trim())
                            }
                        }
                    ) {
                        Text("確認")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanTimeoutDialog(
    currentTimeout: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var timeout by remember { mutableStateOf(currentTimeout.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "掃描逾時設定",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = timeout,
                    onValueChange = {
                        timeout = it
                        errorMessage = null
                    },
                    label = { Text("逾時時間（秒）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "建議設定 10-60 秒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            val value = timeout.toIntOrNull()
                            when {
                                value == null -> errorMessage = "請輸入有效數字"
                                value < 5 -> errorMessage = "逾時時間不能少於 5 秒"
                                value > 300 -> errorMessage = "逾時時間不能超過 300 秒"
                                else -> onConfirm(value)
                            }
                        }
                    ) {
                        Text("確認")
                    }
                }
            }
        }
    }
}