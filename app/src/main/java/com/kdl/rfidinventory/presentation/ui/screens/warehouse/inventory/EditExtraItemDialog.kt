package com.kdl.rfidinventory.presentation.ui.screens.warehouse.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kdl.rfidinventory.data.model.Product
import com.kdl.rfidinventory.data.model.mockProductionOrders

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExtraItemDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    onConfirm: (product: Product, quantity: Int) -> Unit,
    isSubmitting: Boolean
) {
    val products = remember { mockProductionOrders() }

    var selectedProductId by remember { mutableStateOf<String?>(null) }
    var quantity by remember { mutableStateOf(item.basket.quantity.toString()) }
    var showProductPicker by remember { mutableStateOf(false) }

    val selectedProduct = products.find { it.productId == selectedProductId }
    val isValid = selectedProductId != null && quantity.toIntOrNull() != null && quantity.toInt() > 0

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
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

                    if (selectedProduct != null) {
                        // 顯示已選產品
                        Card(
                            onClick = { showProductPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = selectedProduct.imageUrl,
                                    contentDescription = selectedProduct.productName,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedProduct.productName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "最大容量: ${selectedProduct.maxBasketCapacity}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        // 選擇產品按鈕
                        OutlinedButton(
                            onClick = { showProductPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("選擇產品")
                        }
                    }
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
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            if (isValid && selectedProduct != null) {
                                onConfirm(
                                    Product(
                                        id = selectedProduct.productId,
                                        barcodeId = selectedProduct.barcodeId,
                                        qrcodeId = selectedProduct.qrcodeId,
                                        name = selectedProduct.productName,
                                        maxBasketCapacity = selectedProduct.maxBasketCapacity,
                                        imageUrl = selectedProduct.imageUrl
                                    ),
                                    quantity.toInt()
                                )
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

    // 產品選擇器對話框
    if (showProductPicker) {
        ProductPickerDialog(
            products = products,
            onDismiss = { showProductPicker = false },
            onSelect = { product ->
                selectedProductId = product.productId
                quantity = product.maxBasketCapacity.toString() // 默認最大容量
                showProductPicker = false
            }
        )
    }
}

@Composable
private fun ProductPickerDialog(
    products: List<com.kdl.rfidinventory.data.model.ProductionOrder>,
    onDismiss: () -> Unit,
    onSelect: (com.kdl.rfidinventory.data.model.ProductionOrder) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "選擇產品",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                products.forEach { product ->
                    Card(
                        onClick = { onSelect(product) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = product.imageUrl,
                                contentDescription = product.productName,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = product.productName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "最大容量: ${product.maxBasketCapacity}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}