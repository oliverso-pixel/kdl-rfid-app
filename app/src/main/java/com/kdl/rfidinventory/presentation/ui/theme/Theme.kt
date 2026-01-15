package com.kdl.rfidinventory.presentation.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    // Primary
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = StatusInProductionContainer,
    onPrimaryContainer = OnStatusInProduction,

    // Secondary - 使用更深的綠色
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SuccessContainer,
    onSecondaryContainer = OnSuccessContainer,

    // Tertiary
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = StatusReceivedContainer,
    onTertiaryContainer = OnStatusReceived,

    // Error
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,

    // Background & Surface - 更明顯的對比
    background = BackgroundLight,              // 淺藍灰背景
    onBackground = TextPrimary,
    surface = SurfaceLight,                    // 純白卡片
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondary,          // 使用更深的文字色

    // Outline
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,

    // Surface Tint
    surfaceTint = Primary,

    // Inverse
    inverseSurface = Color(0xFF2C2C2C),
    inverseOnSurface = Color.White,
    inversePrimary = PrimaryLight
)

private val DarkColorScheme = darkColorScheme(
    // Primary
    primary = PrimaryLight,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFD1E4FF),

    // Secondary
    secondary = SecondaryLight,
    onSecondary = Color(0xFF003A00),
    secondaryContainer = Color(0xFF00531F),
    onSecondaryContainer = Color(0xFFB8F3B8),

    // Tertiary
    tertiary = InfoLight,
    onTertiary = Color(0xFF003549),
    tertiaryContainer = Color(0xFF004D68),
    onTertiaryContainer = Color(0xFFB8E8FF),

    // Error
    error = ErrorLight,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // Background & Surface
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,

    // Outline
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,

    // Surface Tint
    surfaceTint = PrimaryLight,

    // Inverse
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Primary
)

@Composable
fun RFIDInventoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}