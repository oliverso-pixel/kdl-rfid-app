package com.kdl.rfidinventory.presentation.ui.screens.warehouse.inventory

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
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
import com.kdl.rfidinventory.data.model.Product
import com.kdl.rfidinventory.presentation.ui.components.ProductSelectionCard
import com.kdl.rfidinventory.presentation.ui.components.ProductSelectionDialog
import kotlinx.coroutines.flow.update
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExtraItemDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    onConfirm: (product: Product, quantity: Int) -> Unit,
    isSubmitting: Boolean,
    viewModel: InventoryViewModel = hiltViewModel()
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 使用 ViewModel 的狀態管理選中的產品
    val selectedProduct = uiState.selectedProductForEdit
    var quantity by remember { mutableStateOf(item.basket.quantity.toString()) }

    // 監聽選中產品的變化，更新數量
    LaunchedEffect(selectedProduct) {
        selectedProduct?.let { product ->
            quantity = product.maxBasketCapacity.toString()
        }
    }

    val isValid = selectedProduct != null &&
            quantity.toIntOrNull() != null &&
            quantity.toInt() > 0

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                viewModel.dismissProductDialog()
                onDismiss()
            }
        },
        modifier = Modifier.fillMaxWidth(0.95f)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 標題
                Row(
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

                // 籃子 UID
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                                text = item.basket.uid,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 產品選擇
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "選擇產品 *",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // 使用 ProductSelectionCard 顯示選中的產品
                    ProductSelectionCard(
                        selectedProduct = selectedProduct,
                        onSelectProduct = {
                            Timber.d("🖱️ Opening product selection dialog")
                            viewModel.showProductDialog()
                        }
                    )
                }

                // 數量輸入
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
                            if (selectedProduct != null) {
                                val qtyInt = quantity.toIntOrNull() ?: 0
                                val max = selectedProduct.maxBasketCapacity
                                if (qtyInt > max) {
                                    Text(
                                        "⚠️ 超過最大容量 ($max)",
                                        color = Color(0xFFFF9800)
                                    )
                                }
                            }
                        }
                    )
                }

                HorizontalDivider()

                // 按鈕
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.dismissProductDialog()
                            onDismiss()
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            if (isValid && selectedProduct != null) {
                                onConfirm(selectedProduct, quantity.toInt())
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

        if (uiState.showProductDialog) {
            ProductSelectionDialog(
                products = uiState.products,
                searchQuery = uiState.productSearchQuery,
                onSearchQueryChange = { query ->
                    Timber.d("📝 Search query changed in dialog: $query")
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
    }
}