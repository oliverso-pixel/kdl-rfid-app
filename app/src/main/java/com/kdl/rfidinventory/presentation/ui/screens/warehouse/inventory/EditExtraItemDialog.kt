package com.kdl.rfidinventory.presentation.ui.screens.warehouse.inventory

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kdl.rfidinventory.data.model.Batch
import com.kdl.rfidinventory.data.model.Product
import com.kdl.rfidinventory.presentation.ui.components.ProductSelectionCard
import com.kdl.rfidinventory.presentation.ui.components.ProductSelectionDialog
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExtraItemDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    onConfirm: (product: Product, batch: Batch, quantity: Int) -> Unit,
    isSubmitting: Boolean,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedProduct = uiState.selectedProductForEdit
    val selectedBatch = uiState.selectedBatchForEdit
    val availableBatches = uiState.availableBatches

    var quantity by remember { mutableStateOf(item.basket.quantity.toString()) }
    var expiryDate by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(selectedProduct) {
        selectedProduct?.let { product ->
            quantity = product.maxBasketCapacity.toString()
        }
    }

    LaunchedEffect(selectedProduct, expiryDate) {
        if (selectedProduct != null && expiryDate.isNotBlank()) {
            Timber.d("🔍 Querying batches for ${selectedProduct.itemcode}, expiry=$expiryDate")
            viewModel.loadBatchesForProductAndExpiry(selectedProduct, expiryDate)
        } else {
            viewModel.clearBatchSelection()
        }
    }

    val isValid = selectedProduct != null &&
            selectedBatch != null &&
            quantity.toIntOrNull() != null &&
            quantity.toInt() > 0 &&
            (item.basket.type == null || item.basket.type == selectedProduct.btype)

    val typeWarning = if (selectedProduct != null &&
        item.basket.type != null &&
        item.basket.type != selectedProduct.btype) {
        "⚠️ 籃子類型 ${item.basket.type} 不符合產品要求 ${selectedProduct.btype}"
    } else null

//    AlertDialog(
//        onDismissRequest = {
//            if (!isSubmitting) {
//                viewModel.dismissProductDialog()
//                onDismiss()
//            }
//        },
//        modifier = Modifier.fillMaxWidth(0.95f)
//    ) {
//        Surface(
//            shape = RoundedCornerShape(24.dp),
//            color = MaterialTheme.colorScheme.surface,
//            tonalElevation = 6.dp
//        ) {
//            Column(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(24.dp),
//                verticalArrangement = Arrangement.spacedBy(16.dp)
//            ) {
//                // 標題
//                Row(
//                    horizontalArrangement = Arrangement.spacedBy(12.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Icon(
//                        imageVector = Icons.Default.Edit,
//                        contentDescription = null,
//                        tint = MaterialTheme.colorScheme.primary,
//                        modifier = Modifier.size(28.dp)
//                    )
//                    Text(
//                        text = "編輯籃子信息",
//                        style = MaterialTheme.typography.titleLarge,
//                        fontWeight = FontWeight.Bold
//                    )
//                }
//
//                HorizontalDivider()
//
//                // 籃子 UID
//                Card(
//                    modifier = Modifier.fillMaxWidth(),
//                    colors = CardDefaults.cardColors(
//                        containerColor = MaterialTheme.colorScheme.surfaceVariant
//                    )
//                ) {
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(12.dp),
//                        horizontalArrangement = Arrangement.spacedBy(8.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Icon(
//                            imageVector = Icons.Default.Inventory,
//                            contentDescription = null,
//                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
//                            modifier = Modifier.size(20.dp)
//                        )
//                        Column {
//                            Text(
//                                text = "籃子 UID",
//                                style = MaterialTheme.typography.labelSmall,
//                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                            )
//                            Text(
//                                text = item.basket.uid,
//                                style = MaterialTheme.typography.bodyMedium,
//                                fontWeight = FontWeight.Bold
//                            )
//                        }
//                    }
//                }
//
//                // 產品選擇
//                Column(
//                    modifier = Modifier.fillMaxWidth(),
//                    verticalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    Text(
//                        text = "選擇產品 *",
//                        style = MaterialTheme.typography.labelMedium,
//                        fontWeight = FontWeight.Bold
//                    )
//
//                    // 使用 ProductSelectionCard 顯示選中的產品
//                    ProductSelectionCard(
//                        selectedProduct = selectedProduct,
//                        onSelectProduct = {
//                            Timber.d("🖱️ Opening product selection dialog")
//                            viewModel.showProductDialog()
//                        }
//                    )
//                }
//
//                // 數量輸入
//                Column(
//                    modifier = Modifier.fillMaxWidth(),
//                    verticalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    Text(
//                        text = "數量 *",
//                        style = MaterialTheme.typography.labelMedium,
//                        fontWeight = FontWeight.Bold
//                    )
//
//                    OutlinedTextField(
//                        value = quantity,
//                        onValueChange = { quantity = it },
//                        modifier = Modifier.fillMaxWidth(),
//                        label = { Text("輸入數量") },
//                        leadingIcon = {
//                            Icon(Icons.Default.Numbers, contentDescription = null)
//                        },
//                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
//                        singleLine = true,
//                        isError = quantity.toIntOrNull() == null || quantity.toInt() <= 0,
//                        supportingText = {
//                            if (selectedProduct != null) {
//                                val qtyInt = quantity.toIntOrNull() ?: 0
//                                val max = selectedProduct.maxBasketCapacity
//                                if (qtyInt > max) {
//                                    Text(
//                                        "⚠️ 超過最大容量 ($max)",
//                                        color = Color(0xFFFF9800)
//                                    )
//                                }
//                            }
//                        }
//                    )
//                }
//
//                HorizontalDivider()
//
//                // 按鈕
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.spacedBy(12.dp)
//                ) {
//                    OutlinedButton(
//                        onClick = {
//                            viewModel.dismissProductDialog()
//                            onDismiss()
//                        },
//                        enabled = !isSubmitting,
//                        modifier = Modifier.weight(1f)
//                    ) {
//                        Text("取消")
//                    }
//
//                    Button(
//                        onClick = {
//                            if (isValid && selectedProduct != null) {
//                                onConfirm(selectedProduct, quantity.toInt())
//                            }
//                        },
//                        enabled = isValid && !isSubmitting,
//                        modifier = Modifier.weight(1f)
//                    ) {
//                        if (isSubmitting) {
//                            CircularProgressIndicator(
//                                modifier = Modifier.size(20.dp),
//                                color = Color.White,
//                                strokeWidth = 2.dp
//                            )
//                        } else {
//                            Icon(Icons.Default.Check, contentDescription = null)
//                            Spacer(modifier = Modifier.width(8.dp))
//                            Text("確認")
//                        }
//                    }
//                }
//            }
//        }
//
//        if (uiState.showProductDialog) {
//            ProductSelectionDialog(
//                products = uiState.products,
//                searchQuery = uiState.productSearchQuery,
//                onSearchQueryChange = { query ->
//                    Timber.d("📝 Search query changed in dialog: $query")
//                    viewModel.updateProductSearchQuery(query)
//                },
//                onDismiss = {
//                    viewModel.dismissDialog()
//                },
//                onProductSelected = { product ->
//                    viewModel._uiState.update {
//                        it.copy(selectedProductForEdit = product)
//                    }
//                    viewModel.dismissProductDialog()
//                }
//            )
//        }
//    }
    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                viewModel.dismissProductDialog()
                viewModel.clearBatchSelection()
                onDismiss()
            }
        },
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 固定標題區（不滾動）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "編輯籃子信息",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider()

                // 可滾動的內容區
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 籃子 UID 和類型
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Inventory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "籃子 UID",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
//                                            text = item.basket.uid,
                                            text = item.basket.tagCode ?: item.basket.uid,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                item.basket.type?.let { type ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Category,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "籃子類型",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
//                                                text = "類型 $type",
                                                text = if (type == 1) "類型 樽格" else "類型 花格",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 產品選擇
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "選擇產品 *",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )

                            ProductSelectionCard(
                                selectedProduct = selectedProduct,
                                onSelectProduct = {
                                    viewModel.showProductDialog()
                                }
                            )

                            typeWarning?.let { warning ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFFF9800).copy(alpha = 0.1f)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFFF9800))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFFF9800),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = warning,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFFF9800)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 過期日期選擇
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "過期日期 *",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedCard(
                                onClick = {
                                    if (selectedProduct != null) {
                                        showDatePicker = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedProduct != null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Event,
                                            contentDescription = null,
                                            tint = if (expiryDate.isNotBlank()) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        Text(
                                            text = if (expiryDate.isNotBlank()) {
                                                expiryDate
                                            } else {
                                                if (selectedProduct != null) {
                                                    "選擇過期日期"
                                                } else {
                                                    "請先選擇產品"
                                                }
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (expiryDate.isNotBlank()) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
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
                    }

                    // Batch 選擇
                    if (expiryDate.isNotBlank() && selectedProduct != null) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "選擇批次 *",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                if (uiState.isLoading) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = "查詢批次中...",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                } else if (availableBatches.isEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "無可用批次，請確認過期日期或產品",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ✅ 批次列表
                        items(availableBatches) { batch ->
                            BatchSelectionCard(
                                batch = batch,
                                isSelected = selectedBatch?.batch_code == batch.batch_code,
                                onClick = {
                                    viewModel.selectBatchForEdit(batch)
                                }
                            )
                        }
                    }

                    // 數量輸入
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "數量 *",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = quantity,
                                onValueChange = { quantity = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("輸入數量") },
                                leadingIcon = {
                                    Icon(Icons.Default.Numbers, contentDescription = null)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = quantity.toIntOrNull() == null || quantity.toInt() <= 0,
                                supportingText = {
                                    selectedProduct?.let { product ->
                                        val qtyInt = quantity.toIntOrNull() ?: 0
                                        val max = product.maxBasketCapacity
                                        when {
                                            qtyInt > max -> Text(
                                                "⚠️ 超過最大容量 ($max)",
                                                color = Color(0xFFFF9800)
                                            )
                                            qtyInt > 0 -> Text(
                                                "✓ 最大容量: $max",
                                                color = Color(0xFF4CAF50)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // 驗證提示
                    if (!isValid && selectedProduct != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "請完成以下步驟：",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = buildString {
                                                if (selectedProduct == null) append("• 選擇產品\n")
                                                if (expiryDate.isBlank()) append("• 選擇過期日期\n")
                                                if (selectedBatch == null) append("• 選擇批次\n")
                                                if (quantity.toIntOrNull() == null || quantity.toInt() <= 0) {
                                                    append("• 輸入有效數量")
                                                }
                                            }.trim(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ✅ 固定底部按鈕區（不滾動）
                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.dismissProductDialog()
                            viewModel.clearBatchSelection()
                            onDismiss()
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            if (isValid && selectedProduct != null && selectedBatch != null) {
                                onConfirm(selectedProduct, selectedBatch, quantity.toInt())
                            }
                        },
                        enabled = isValid && !isSubmitting,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("確認")
                        }
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
            onSearchQueryChange = { query ->
                viewModel.updateProductSearchQuery(query)
            },
            onDismiss = {
                viewModel.dismissDialog()
            },
            onProductSelected = { product ->
                viewModel._uiState.update {
                    it.copy(selectedProductForEdit = product)
                }
                viewModel.dismissProductDialog()
            }
        )
    }

    // 日期選擇器
    if (showDatePicker) {
        DatePickerDialog(
            selectedDate = expiryDate,
            onDateSelected = { date ->
                expiryDate = date
                showDatePicker = false
                Timber.d("📅 Expiry date selected: $date")
            },
            onDismiss = {
                showDatePicker = false
            }
        )
    }
}

/**
 *  Batch 選擇卡片
 */
@Composable
private fun BatchSelectionCard(
    batch: Batch,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ✅ 選中指示器
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = batch.batch_code,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "生產: ${batch.productionDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                batch.expireDate?.let { expiry ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "到期: ${expiry.substringBefore("T")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * ✅ 日期選擇器對話框
 */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (selectedDate.isNotBlank()) {
            try {
                val date = LocalDate.parse(selectedDate, DateTimeFormatter.ISO_DATE)
                date.toEpochDay() * 24 * 60 * 60 * 1000
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        } else {
            System.currentTimeMillis()
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val epochDay = millis / (24 * 60 * 60 * 1000)
                        val date = LocalDate.ofEpochDay(epochDay)
                        val formattedDate = date.format(DateTimeFormatter.ISO_DATE)
                        onDateSelected(formattedDate)
                    }
                }
            ) {
                Text("確認")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
