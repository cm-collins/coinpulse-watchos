  package com.example.coinpulse.presentation.detail

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.coinpulse.domain.FailureReason
import com.example.coinpulse.domain.PricePoint
import com.example.coinpulse.domain.WatchlistItem
import com.example.coinpulse.presentation.theme.CoinpulseTheme
import java.math.BigDecimal
import java.time.Instant
import kotlin.math.sin

// Clearly fake, deterministic data; never presented as live prices.
private val Up = WatchlistItem("BTC", "USDT", BigDecimal("67234.50"), BigDecimal("2.31"), BigDecimal("65100.00"), BigDecimal("67890.00"))
private val Down = WatchlistItem("ETH", "USDT", BigDecimal("3512.07"), BigDecimal("-0.87"), BigDecimal("3450.00"), BigDecimal("3580.00"))

private fun series(start: Double, drift: Double): List<PricePoint> =
    (0 until 96).map { i ->
        PricePoint(
            Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 900L),
            BigDecimal(start + drift * i + 40 * sin(i / 6.0)).setScale(2, java.math.RoundingMode.HALF_UP),
        )
    }

@Composable
private fun PreviewHost(item: WatchlistItem, chart: ChartUiState) {
    CoinpulseTheme { AppScaffold { CoinDetailScreen(item = item, chart = chart, onRetry = {}) } }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun DetailUpPreview() = PreviewHost(Up, ChartUiState.Ready(series(66_000.0, 12.0)))

@WearPreviewDevices
@Composable
private fun DetailDownPreview() = PreviewHost(Down, ChartUiState.Ready(series(3_560.0, -0.6)))

@WearPreviewDevices
@Composable
private fun DetailLoadingPreview() = PreviewHost(Up, ChartUiState.Loading)

@WearPreviewDevices
@Composable
private fun DetailUnavailablePreview() = PreviewHost(Up, ChartUiState.Unavailable(FailureReason.Offline))
