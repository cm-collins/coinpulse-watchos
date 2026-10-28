package com.example.coinpulse.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme

/** "Obsidian" palette from docs/DESIGN.md. Hex values live only in this file. */
private object Obsidian {
    val Background = Color(0xFF000000)
    val SurfaceContainerLow = Color(0xFF0B0D12)
    val SurfaceContainer = Color(0xFF12151C)
    val SurfaceContainerHigh = Color(0xFF1A1E28)
    val OnSurface = Color(0xFFECEFF6)
    val OnSurfaceVariant = Color(0xFF9AA3B5)
    val OutlineVariant = Color(0xFF2B3140)
    val Primary = Color(0xFF8AA4FF)
    val OnPrimary = Color(0xFF0A1033)
    val PrimaryContainer = Color(0xFF1F2A5C)
    val OnPrimaryContainer = Color(0xFFDCE3FF)
    val Tertiary = Color(0xFFFFC857)
    val Error = Color(0xFFFF6B7A)
    val Gain = Color(0xFF3DDC97)
    val Loss = Color(0xFFFF6B7A)

    // Not specified in DESIGN.md: neutral/derived values so no default M3 purple leaks in.
    val PrimaryDim = Color(0xFF6C86E0)
    val OnTertiary = Color(0xFF2B1F00)
    val TertiaryContainer = Color(0xFF2E2410)
    val OnTertiaryContainer = Color(0xFFFFE3A3)
    val OnError = Color(0xFF3B0009)
    val ErrorContainer = Color(0xFF3A1218)
    val OnErrorContainer = Color(0xFFFFD9DD)
}

internal val ObsidianColorScheme =
    ColorScheme(
        background = Obsidian.Background,
        onBackground = Obsidian.OnSurface,
        surfaceContainerLow = Obsidian.SurfaceContainerLow,
        surfaceContainer = Obsidian.SurfaceContainer,
        surfaceContainerHigh = Obsidian.SurfaceContainerHigh,
        onSurface = Obsidian.OnSurface,
        onSurfaceVariant = Obsidian.OnSurfaceVariant,
        outline = Obsidian.OnSurfaceVariant,
        outlineVariant = Obsidian.OutlineVariant,
        primary = Obsidian.Primary,
        primaryDim = Obsidian.PrimaryDim,
        onPrimary = Obsidian.OnPrimary,
        primaryContainer = Obsidian.PrimaryContainer,
        onPrimaryContainer = Obsidian.OnPrimaryContainer,
        secondary = Obsidian.OnSurfaceVariant,
        secondaryDim = Obsidian.OnSurfaceVariant,
        onSecondary = Obsidian.Background,
        secondaryContainer = Obsidian.SurfaceContainerHigh,
        onSecondaryContainer = Obsidian.OnSurface,
        tertiary = Obsidian.Tertiary,
        tertiaryDim = Obsidian.Tertiary,
        onTertiary = Obsidian.OnTertiary,
        tertiaryContainer = Obsidian.TertiaryContainer,
        onTertiaryContainer = Obsidian.OnTertiaryContainer,
        error = Obsidian.Error,
        errorDim = Obsidian.Error,
        onError = Obsidian.OnError,
        errorContainer = Obsidian.ErrorContainer,
        onErrorContainer = Obsidian.OnErrorContainer,
    )

/** Semantic colors Material 3 has no roles for. Direction must also use sign + arrow, never color alone. */
@Immutable
class CoinPulseColors(val gain: Color, val loss: Color, val stale: Color)

internal val ObsidianCoinPulseColors =
    CoinPulseColors(gain = Obsidian.Gain, loss = Obsidian.Loss, stale = Obsidian.Tertiary)

val LocalCoinPulseColors = staticCompositionLocalOf { ObsidianCoinPulseColors }
