// ========== ProductSelectionCard.kt ==========

package com.kdl.rfidinventory.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kdl.rfidinventory.data.model.Product
import com.kdl.rfidinventory.util.Constants
import timber.log.Timber

/**
 * 產品選擇卡片
 *
 * @param selectedProduct 當前選中的產品
 * @param onSelectProduct 點擊卡片時的回調
 */
@Composable
fun ProductSelectionCard(
    selectedProduct: Product?,
    onSelectProduct: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onSelectProduct,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedProduct != null) {
                val imageUrl = selectedProduct.imageUrl?.let { url ->
                    if (url.startsWith("http")) url else "${Constants.SERVER_URL}$url"
                }

                // 已選擇產品
                AsyncImage(
                    model = imageUrl,
                    contentDescription = selectedProduct.name,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedProduct.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "最大容量: ${selectedProduct.maxBasketCapacity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    Icons.Default.Edit,
                    contentDescription = "更改",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                // 未選擇產品
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "點擊選擇產品",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 產品選擇對話框
 *
 * @param products 產品列表
 * @param searchQuery 搜索關鍵字
 * @param onSearchQueryChange 搜索關鍵字變化回調
 * @param onDismiss 關閉對話框回調
 * @param onProductSelected 選中產品回調
 */
@Composable
fun ProductSelectionDialog(
    products: List<Product>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onProductSelected: (Product) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var isDialogReady by remember { mutableStateOf(false) }

    // 初始化焦點
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            focusRequester.requestFocus()
            isDialogReady = true
            Timber.d("✅ Product dialog ready, focus requested")
        } catch (e: Exception) {
            Timber.e(e, "Failed to request focus")
        }
    }

    val filteredProducts = remember(products, searchQuery) {
        filterProducts(products, searchQuery)
    }

    val onEnterPressed = {
        if (filteredProducts.size == 1) {
            Timber.d("⌨️ Enter pressed, selecting product: ${filteredProducts.first().name}")
            onProductSelected(filteredProducts.first())
        }
    }

    AlertDialog(
        onDismissRequest = {
            Timber.d("🚪 Dialog dismissed")
            onSearchQueryChange("")
            onDismiss()
        },
        title = {
            ProductDialogTitle(
                searchQuery = searchQuery,
                filteredProducts = filteredProducts,
                isDialogReady = isDialogReady,
                onSearchQueryChange = onSearchQueryChange,
                onEnterPressed = onEnterPressed,
                focusRequester = focusRequester,
                onClearQuery = {
                    onSearchQueryChange("")
                }
            )
        },
        text = {
            ProductDialogContent(
                filteredProducts = filteredProducts,
                searchQuery = searchQuery,
                onProductSelected = onProductSelected
            )
        },
        confirmButton = {
            if (filteredProducts.size == 1) {
                Button(
                    onClick = {
                        Timber.d("✅ Confirm button clicked")
                        onProductSelected(filteredProducts.first())
                    },
                    enabled = isDialogReady
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("選擇此產品")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    Timber.d("❌ Cancel button clicked")
                    onSearchQueryChange("")
                    onDismiss()
                }
            ) {
                Text("取消")
            }
        }
    )
}

/**
 * 對話框標題部分（包含搜索框）
 */
@Composable
private fun ProductDialogTitle(
    searchQuery: String,
    filteredProducts: List<Product>,
    isDialogReady: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onEnterPressed: () -> Unit,
    focusRequester: FocusRequester,
    onClearQuery: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 標題欄
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("選擇產品")

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isDialogReady) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = if (isDialogReady) "可用掃碼槍" else "準備中...",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDialogReady) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        // 搜索框
        ProductSearchField(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onEnterPressed = onEnterPressed,
            focusRequester = focusRequester,
            onClearQuery = onClearQuery,
            isEnabled = isDialogReady
        )

        // 結果數量提示
        if (searchQuery.isNotEmpty()) {
            ProductSearchResultInfo(
                resultCount = filteredProducts.size
            )
        }
    }
}

/**
 * 產品搜索輸入框
 */
@Composable
private fun ProductSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEnterPressed: () -> Unit,
    focusRequester: FocusRequester,
    onClearQuery: () -> Unit,
    isEnabled: Boolean
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { newValue ->
            Timber.d("📝 Search query changed: '$newValue'")
            onSearchQueryChange(newValue)
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onKeyEvent { keyEvent ->
                when {
                    keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown -> {
                        onEnterPressed()
                        true
                    }
                    else -> false
                }
            },
        placeholder = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text("搜索或掃描條碼/QR碼")
            }
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "搜索")
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = {
                    Timber.d("🗑️ Clearing search query")
                    onClearQuery()
                }) {
                    Icon(Icons.Default.Clear, contentDescription = "清除")
                }
            }
        },
        singleLine = true,
        enabled = isEnabled,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                Timber.d("🔍 Search action triggered")
                onEnterPressed()
            }
        )
    )
}

/**
 * 搜索結果信息
 */
@Composable
private fun ProductSearchResultInfo(
    resultCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "找到 $resultCount 個結果",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (resultCount == 1) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "精確匹配",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * 對話框內容部分（產品列表或空狀態）
 */
@Composable
private fun ProductDialogContent(
    filteredProducts: List<Product>,
    searchQuery: String,
    onProductSelected: (Product) -> Unit
) {
    if (filteredProducts.isEmpty()) {
        ProductEmptyState(searchQuery = searchQuery)
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredProducts) { product ->
                ProductItem(
                    product = product,
                    searchQuery = searchQuery,
                    onClick = {
                        Timber.d("🖱️ Product clicked: ${product.name}")
                        onProductSelected(product)
                    }
                )
            }
        }
    }
}

/**
 * 產品列表為空時的狀態
 */
@Composable
private fun ProductEmptyState(
    searchQuery: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "找不到符合條件的產品",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (searchQuery.isNotEmpty()) {
                Text(
                    text = "請檢查條碼是否正確或嘗試其他關鍵字",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 產品列表項
 */
@Composable
private fun ProductItem(
    product: Product,
    searchQuery: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imageUrl = product.imageUrl?.let { url ->
                if (url.startsWith("http")) url else "${Constants.SERVER_URL}$url"
            }

            // 產品圖片
            AsyncImage(
                model = imageUrl,
                contentDescription = product.name,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            // 產品信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 產品名稱（高亮匹配文字）
                HighlightedText(
                    text = product.name,
                    highlight = searchQuery,
                    style = MaterialTheme.typography.titleMedium
                )

                // 產品 ID
                ProductInfoRow(
                    label = "ID:",
                    value = product.itemcode,
                    highlight = searchQuery
                )

                // Barcode（如果有）
                product.barcodeId?.let { barcode ->
                    ProductInfoRow(
                        label = "條碼:",
                        value = barcode.toString(),
                        highlight = searchQuery,
                        icon = Icons.Default.QrCode
                    )
                }

                // QR Code（如果有）
                product.qrcodeId?.let { qrcode ->
                    ProductInfoRow(
                        label = "QR:",
                        value = qrcode,
                        highlight = searchQuery,
                        icon = Icons.Default.QrCode2
                    )
                }

                // 容量
                Text(
                    text = "最大容量: ${product.maxBasketCapacity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 產品信息行（帶圖標和高亮）
 */
@Composable
private fun ProductInfoRow(
    label: String,
    value: String,
    highlight: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HighlightedText(
            text = value,
            highlight = highlight,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * 高亮顯示文本中匹配的部分
 */
@Composable
fun HighlightedText(
    text: String,
    highlight: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    if (highlight.isBlank()) {
        Text(
            text = text,
            style = style,
            modifier = modifier
        )
        return
    }

    val highlightColor = MaterialTheme.colorScheme.primary
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        val lowerText = text.lowercase()
        val lowerHighlight = highlight.lowercase()

        while (currentIndex < text.length) {
            val startIndex = lowerText.indexOf(lowerHighlight, currentIndex)

            if (startIndex == -1) {
                // 沒有找到更多匹配，添加剩余文字
                append(text.substring(currentIndex))
                break
            }

            // 添加匹配前的文字
            if (startIndex > currentIndex) {
                append(text.substring(currentIndex, startIndex))
            }

            // 添加高亮文字
            val endIndex = startIndex + lowerHighlight.length
            withStyle(
                style = SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.Bold,
                    background = highlightColor.copy(alpha = 0.2f)
                )
            ) {
                append(text.substring(startIndex, endIndex))
            }

            currentIndex = endIndex
        }
    }

    Text(
        text = annotatedString,
        style = style,
        modifier = modifier
    )
}

/**
 * 過濾產品列表
 */
private fun filterProducts(products: List<Product>, query: String): List<Product> {
    if (query.isBlank()) {
        return products
    }

    val lowerQuery = query.trim().lowercase()
    return products.filter { product ->
        product.itemcode.lowercase().contains(lowerQuery) ||
                product.name.lowercase().contains(lowerQuery) ||
                (product.barcodeId?.toString()?.contains(lowerQuery) == true) ||
                (product.qrcodeId?.lowercase()?.contains(lowerQuery) == true)
    }
}