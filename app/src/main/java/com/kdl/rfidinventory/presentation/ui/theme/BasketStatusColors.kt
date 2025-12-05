package com.kdl.rfidinventory.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kdl.rfidinventory.data.model.BasketStatus

/**
 * 籃子狀態顏色輔助函數
 */
object BasketStatusColors {

    @Composable
    fun getStatusColor(status: BasketStatus): Color {
        return when (status) {
            BasketStatus.UNASSIGNED -> StatusUnassignedContainer
            BasketStatus.IN_PRODUCTION -> StatusInProductionContainer
            BasketStatus.RECEIVED -> StatusReceivedContainer
            BasketStatus.IN_STOCK -> StatusInStockContainer
            BasketStatus.SHIPPED -> StatusShippedContainer
            BasketStatus.SAMPLING -> StatusSamplingContainer
            BasketStatus.DAMAGED -> StatusSamplingContainer
            else -> StatusUnassigned
        }
    }

    @Composable
    fun getStatusTextColor(status: BasketStatus): Color {
        return when (status) {
            BasketStatus.UNASSIGNED -> OnStatusUnassigned
            BasketStatus.IN_PRODUCTION -> OnStatusInProduction
            BasketStatus.RECEIVED -> OnStatusReceived
            BasketStatus.IN_STOCK -> OnStatusInStock
            BasketStatus.SHIPPED -> OnStatusShipped
            BasketStatus.SAMPLING -> OnStatusSampling
            BasketStatus.DAMAGED -> OnStatusSampling
            else -> OnStatusUnassigned
        }
    }

    @Composable
    fun getValidStatusColor(): Color = MaterialTheme.colorScheme.surfaceVariant

    @Composable
    fun getInvalidStatusColor(): Color = MaterialTheme.colorScheme.errorContainer

    @Composable
    fun getInvalidStatusBorderColor(): Color = MaterialTheme.colorScheme.error
}

/**
 * 掃描模式顏色輔助函數
 */
object ScanModeColors {

    @Composable
    fun getRFIDColor(): Color = ScanModeRFID

    @Composable
    fun getRFIDContainerColor(): Color = ScanModeRFIDContainer

    @Composable
    fun getBarcodeColor(): Color = ScanModeBarcode

    @Composable
    fun getBarcodeContainerColor(): Color = ScanModeBarcodeContainer
}