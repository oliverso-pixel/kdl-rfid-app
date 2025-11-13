package com.kdl.rfidinventory.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kdl.rfidinventory.util.ScanMode

@Composable
fun ScanModeSelector(
    currentMode: ScanMode,
    onModeChange: (ScanMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
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

@Composable
private fun ScanModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text = text)
    }
}