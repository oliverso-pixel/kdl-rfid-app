package com.kdl.rfidinventory.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.getStatusColor
import com.kdl.rfidinventory.data.model.getStatusIcon

@Composable
fun BasketListItem(
    basket: Basket,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 狀態指示器
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = getStatusColor(basket.status),
                        shape = MaterialTheme.shapes.small
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 籃子信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UID: ${basket.uid.takeLast(8)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                basket.product?.let { product ->
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "數量: ${basket.quantity} / ${product.maxBasketCapacity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: run {
                    Text(
                        text = "未配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                basket.batch?.let { batch ->
                    Text(
                        text = "批次: ${batch.batch_code}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 狀態圖標
            Icon(
                imageVector = getStatusIcon(basket.status),
                contentDescription = basket.status.name,
                tint = getStatusColor(basket.status),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}