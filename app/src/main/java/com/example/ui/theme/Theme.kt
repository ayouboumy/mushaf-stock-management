package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// =========================================================================
// MATERIAL DESIGN 3: DEEP EMERALD & ROYAL GOLD COLOR SCHEME
// =========================================================================

val EmeraldGoldColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = TextOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,

    secondary = GoldPrimary,
    onSecondary = TextOnPrimary,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = GoldOnContainer,

    tertiary = GoldWarm,
    onTertiary = TextOnPrimary,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = GoldOnContainer,

    background = AppBackground,
    onBackground = TextPrimary,

    surface = AppSurface,
    onSurface = TextPrimary,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    outline = CardBorder,
    outlineVariant = AppBorder,

    error = MovementOutColor,
    onError = TextOnPrimary,
    errorContainer = MovementOutContainer,
    onErrorContainer = MovementOutOnContainer
)

// Refined Modern Material 3 Shapes (8–14px radius range for data-first minimalism)
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EmeraldGoldColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    AppTheme(content = content)
}
