package com.kdl.rfidinventory.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kdl.rfidinventory.util.ScanMode
import com.kdl.rfidinventory.util.ScanModeAvailability

@Composable
fun ScanModeSelector(
    currentMode: ScanMode,
    onModeChange: (ScanMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    availability: ScanModeAvailability = ScanModeAvailability.BOTH
) {
    // 如果是單一模式，不顯示選擇器
    if (availability == ScanModeAvailability.SINGLE_ONLY ||
        availability == ScanModeAvailability.CONTINUOUS_ONLY) {
        // 僅顯示當前固定模式的說明
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "掃描模式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 固定模式標籤
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = when (availability) {
                        ScanModeAvailability.SINGLE_ONLY -> "單次掃描"
                        ScanModeAvailability.CONTINUOUS_ONLY -> "連續掃描"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            // 模式說明
            Text(
                text = when (availability) {
                    ScanModeAvailability.SINGLE_ONLY -> "此場景僅支持單次掃描模式"
                    ScanModeAvailability.CONTINUOUS_ONLY -> "此場景僅支持連續掃描模式"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // 雙模式：顯示切換按鈕
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "掃描模式",
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 單次掃描按鈕
            FilterChip(
                selected = currentMode == ScanMode.SINGLE,
                onClick = {
                    if (enabled) {
                        onModeChange(ScanMode.SINGLE)
                    }
                },
                label = { Text("單次掃描") },
                modifier = Modifier.weight(1f),
                enabled = enabled
            )

            // 連續掃描按鈕
            FilterChip(
                selected = currentMode == ScanMode.CONTINUOUS,
                onClick = {
                    if (enabled) {
                        onModeChange(ScanMode.CONTINUOUS)
                    }
                },
                label = { Text("連續掃描") },
                modifier = Modifier.weight(1f),
                enabled = enabled
            )
        }

        // 模式說明
        if (enabled) {
            Text(
                text = when (currentMode) {
                    ScanMode.SINGLE -> "掃描一個籃子後自動停止"
                    ScanMode.CONTINUOUS -> "持續掃描多個籃子，需手動停止"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}