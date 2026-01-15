// RouteDetailScreen.kt
package com.kdl.rfidinventory.presentation.ui.screens.loading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kdl.rfidinventory.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    route: LoadingRoute,
    selectedWarehouseName: String,
    warehouseBaskets: List<Basket>,
    onSelectModeAndItem: (LoadingMode, LoadingItem) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("路線詳情") },
//                navigationIcon = {
//                    IconButton(onClick = onBack) {
//                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
//                    }
//                },
//                colors = TopAppBarDefaults.topAppBarColors(
//                    containerColor = MaterialTheme.colorScheme.primary,
//                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
//                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
//                )
//            )
//        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 路線信息卡片
            item {
                RouteInfoCard(
                    route = route,
                    warehouseName = selectedWarehouseName
                )
            }

            // 整體完成進度
            item {
                OverallProgressCard(route = route)
            }

            // 產品列表
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "產品清單",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${route.items.size} 個產品",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(route.items) { item ->
                ProductDetailCard(
                    item = item,
                    warehouseBaskets = warehouseBaskets,
                    onSelectMode = { mode -> onSelectModeAndItem(mode, item) }
                )
            }
        }
    }
}

/**
 * 路線信息卡片
 */
@Composable
private fun RouteInfoCard(
    route: LoadingRoute,
    warehouseName: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = route.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    route.vehiclePlate?.let {
                        Text(
                            text = "車牌：$it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                RouteStatusBadge(status = route.status)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoChip(
                    icon = Icons.Default.Warehouse,
                    label = warehouseName,
                    modifier = Modifier.weight(1f)
                )
                InfoChip(
                    icon = Icons.Default.CalendarToday,
                    label = route.deliveryDate,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatItem(
                    label = "總數量",
                    value = route.totalQuantity.toString(),
                    icon = Icons.Default.Numbers,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "籃子數",
                    value = route.totalBaskets.toString(),
                    icon = Icons.Default.Inventory,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "品項",
                    value = route.items.size.toString(),
                    icon = Icons.Default.Category,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 整體完成進度卡片
 */
@Composable
private fun OverallProgressCard(route: LoadingRoute) {
    val status = route.completionStatus

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (status.isFullyCompleted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "整體進度",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (status.isFullyCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // 完整籃子進度
            ProgressSection(
                title = "完整籃子",
                icon = Icons.Default.Inventory,
                completed = status.fullBasketsCompleted,
                scanned = status.fullBasketsScannedCount,
                total = route.items.sumOf { it.fullTrolley * 5 + it.fullBaskets }
            )

            // 散貨進度
            ProgressSection(
                title = "散貨",
                icon = Icons.Default.ShoppingBasket,
                completed = status.looseItemsCompleted,
                scanned = status.looseItemsScannedCount,
                total = route.items.sumOf { it.looseQuantity }
            )
        }
    }
}

@Composable
private fun ProgressSection(
    title: String,
    icon: ImageVector,
    completed: Boolean,
    scanned: Int,
    total: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (completed) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (completed) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "已完成",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "$scanned / $total",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (completed) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.primary
                )
            }
        }

        LinearProgressIndicator(
            progress = { if (total == 0) 0f else scanned.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (completed) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 產品詳情卡片
 */
@Composable
private fun ProductDetailCard(
    item: LoadingItem,
    warehouseBaskets: List<Basket>,
    onSelectMode: (LoadingMode) -> Unit
) {
    val status = item.completionStatus
    val productBaskets = warehouseBaskets.filter { it.product?.id == item.productId }
    val fullBaskets = productBaskets.filter {
        it.quantity == it.product?.maxBasketCapacity
    }
    val looseBaskets = productBaskets.filter {
        it.quantity < (it.product?.maxBasketCapacity ?: 0)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 產品名稱和整體狀態
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (status.isFullyCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Text(
                text = "總計：${item.totalQuantity} 個",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            // 完整籃子部分
            if (item.fullTrolley > 0 || item.fullBaskets > 0) {
                ModeSection(
                    mode = LoadingMode.FULL_BASKETS,
                    title = "完整籃子",
                    icon = Icons.Default.Inventory,
                    completed = status.fullBasketsCompleted,
                    scanned = status.fullBasketsScanned,
                    expected = item.fullTrolley * 5 + item.fullBaskets,
                    quantity = item.totalQuantity - item.looseQuantity,
                    description = "${item.fullTrolley}車${item.fullBaskets}格",
                    availableStock = fullBaskets.size,
                    onSelect = { onSelectMode(LoadingMode.FULL_BASKETS) }
                )
            }

            // 散貨部分
            if (item.looseQuantity > 0) {
                ModeSection(
                    mode = LoadingMode.LOOSE_ITEMS,
                    title = "散貨",
                    icon = Icons.Default.ShoppingBasket,
                    completed = status.looseItemsCompleted,
                    scanned = status.looseItemsScanned,
                    expected = item.looseQuantity,
                    quantity = item.looseQuantity,
                    description = "${item.looseQuantity} 個",
                    availableStock = looseBaskets.sumOf { it.quantity },
                    onSelect = { onSelectMode(LoadingMode.LOOSE_ITEMS) }
                )
            }
        }
    }
}

@Composable
private fun ModeSection(
    mode: LoadingMode,
    title: String,
    icon: ImageVector,
    completed: Boolean,
    scanned: Int,
    expected: Int,
    quantity: Int,
    description: String,
    availableStock: Int,
    onSelect: () -> Unit
) {
    val onClick: () -> Unit = if (!completed && availableStock > 0) {
        onSelect
    } else {
        {}
    }

    Card(
        onClick = onClick,
        enabled = !completed && availableStock > 0,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                completed -> MaterialTheme.colorScheme.secondaryContainer
                availableStock > 0 -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (completed) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (completed) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (completed) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (completed) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "已完成",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (mode == LoadingMode.FULL_BASKETS) {
                    Text(
                        text = "已掃描：$scanned / $expected 籃 • $scanned/${quantity} 個",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (completed) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "已掃描：$scanned / $expected 個",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (completed) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            completed -> Icons.Default.CheckCircle
                            availableStock > 0 -> Icons.Default.Inventory2
                            else -> Icons.Default.Warning
                        },
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = when {
                            completed -> MaterialTheme.colorScheme.secondary
                            availableStock > 0 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = when {
                            completed -> "已完成上貨"
                            availableStock > 0 -> "庫存：$availableStock ${if (mode == LoadingMode.FULL_BASKETS) "籃" else "個"}"
                            else -> "無可用庫存"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            completed -> MaterialTheme.colorScheme.secondary
                            availableStock > 0 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            if (!completed && availableStock > 0) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}