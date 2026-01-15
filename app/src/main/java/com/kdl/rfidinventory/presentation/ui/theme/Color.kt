package com.kdl.rfidinventory.presentation.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Colors
val Primary = Color(0xFF1976D2)
val PrimaryVariant = Color(0xFF1565C0)
val PrimaryLight = Color(0xFF42A5F5)
val OnPrimary = Color(0xFFFFFFFF)

// Secondary Colors
val Secondary = Color(0xFF2E7D32)              // 加深綠色
val SecondaryVariant = Color(0xFF1B5E20)       // 更深的綠色
val SecondaryLight = Color(0xFF4CAF50)
val OnSecondary = Color(0xFFFFFFFF)

// Tertiary Colors
val Tertiary = Color(0xFF0097A7)
val TertiaryVariant = Color(0xFF00838F)
val OnTertiary = Color(0xFFFFFFFF)

// Status Colors
val ErrorRed = Color(0xFFD32F2F)
val ErrorLight = Color(0xFFEF5350)
val ErrorContainer = Color(0xFFFFEBEE)
val OnErrorContainer = Color(0xFFB71C1C)

val WarningOrange = Color(0xFFFF9800)
val WarningLight = Color(0xFFFFB74D)
val WarningContainer = Color(0xFFFFF3E0)
val OnWarningContainer = Color(0xFFE65100)

val SuccessGreen = Color(0xFF2E7D32)           // 改為更深的綠色
val SuccessLight = Color(0xFF388E3C)
val SuccessContainer = Color(0xFFC8E6C9)       // 稍微加深容器色
val OnSuccessContainer = Color(0xFF1B5E20)

val InfoBlue = Color(0xFF2196F3)
val InfoLight = Color(0xFF42A5F5)
val InfoContainer = Color(0xFFE3F2FD)
val OnInfoContainer = Color(0xFF0D47A1)

// Basket Status Colors
val StatusUnassigned = Color(0xFF757575)        // 未配置 - 深灰色
val StatusUnassignedContainer = Color(0xFFEEEEEE)
val OnStatusUnassigned = Color(0xFF424242)

val StatusInProduction = Color(0xFF1565C0)      // 生產中 - 深藍色
val StatusInProductionContainer = Color(0xFFBBDEFB)  // 加深容器色
val OnStatusInProduction = Color(0xFF0D47A1)

val StatusReceived = Color(0xFF00838F)          // 已收貨 - 深青色
val StatusReceivedContainer = Color(0xFFB2EBF2)      // 加深容器色
val OnStatusReceived = Color(0xFF006064)

val StatusInStock = Color(0xFF2E7D32)           // 在庫中 - 深綠色
val StatusInStockContainer = Color(0xFFC8E6C9)       // 加深容器色
val OnStatusInStock = Color(0xFF1B5E20)

val StatusShipped = Color(0xFF7B1FA2)           // 已出貨 - 深紫色
val StatusShippedContainer = Color(0xFFE1BEE7)       // 加深容器色
val OnStatusShipped = Color(0xFF4A148C)

val StatusSampling = Color(0xFFF9A825)          // 抽樣中 - 深黃色
val StatusSamplingContainer = Color(0xFFFFF59D)      // 加深容器色
val OnStatusSampling = Color(0xFFF57F17)

// Background & Surface - 增加對比度
val BackgroundLight = Color(0xFFECEFF1)         // 更深的背景色（淺藍灰）
val SurfaceLight = Color(0xFFFFFFFF)            // 純白卡片
val SurfaceVariantLight = Color(0xFFF5F5F5)     // 淺灰變體
val OnSurfaceVariantLight = Color(0xFF616161)   // 加深文字

val BackgroundDark = Color(0xFF0A0A0A)          // 更深的深色背景
val SurfaceDark = Color(0xFF1E1E1E)             // 深色卡片
val SurfaceVariantDark = Color(0xFF2C2C2C)      // 深灰變體
val OnSurfaceVariantDark = Color(0xFFB0B0B0)

// Text Colors
val TextPrimary = Color(0xFF212121)
val TextSecondary = Color(0xFF616161)           // 稍微加深
val TextHint = Color(0xFF9E9E9E)

val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFFB0B0B0)
val TextHintDark = Color(0xFF757575)

// Outline & Divider
val OutlineLight = Color(0xFFBDBDBD)            // 加深邊框
val OutlineVariantLight = Color(0xFFE0E0E0)
val OutlineDark = Color(0xFF424242)
val OutlineVariantDark = Color(0xFF333333)

// Scan Mode Colors - 加深顏色
val ScanModeRFID = Color(0xFF1565C0)            // RFID 模式 - 深藍色
val ScanModeRFIDContainer = Color(0xFFBBDEFB)   // 加深容器色
val ScanModeBarcode = Color(0xFF00838F)         // 條碼模式 - 深青色
val ScanModeBarcodeContainer = Color(0xFFB2EBF2) // 加深容器色

// 掃描中的特殊顏色
val ScanningActiveRFID = Color(0xFF1976D2)      // RFID 掃描中
val ScanningActiveBarcode = Color(0xFF0097A7)   // 條碼掃描中