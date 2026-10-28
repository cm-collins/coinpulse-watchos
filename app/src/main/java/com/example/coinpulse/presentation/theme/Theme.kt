package com.example.coinpulse.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.wear.compose.material3.MaterialTheme

/**
 * Typography intentionally uses the library's default roles and system font (see "Implementation
 * status" in docs/DESIGN.md); no custom font is bundled.
 */
@Composable
fun CoinpulseTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalCoinPulseColors provides ObsidianCoinPulseColors) {
        MaterialTheme(
            colorScheme = ObsidianColorScheme,
            content = content
        )
    }
}
