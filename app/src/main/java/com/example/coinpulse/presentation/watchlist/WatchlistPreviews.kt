package com.example.coinpulse.presentation.watchlist

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.coinpulse.domain.FailureReason
import com.example.coinpulse.domain.WatchlistItem
import com.example.coinpulse.presentation.theme.CoinpulseTheme
import java.math.BigDecimal
import java.time.Instant

// Fixed, clearly fake data; never presented as live prices.
private val PreviewTime = Instant.parse("2026-01-01T12:30:00Z")

private fun coin(base: String, price: String, change: String, low: String, high: String) =
    WatchlistItem(base, "USDT", BigDecimal(price), BigDecimal(change), BigDecimal(low), BigDecimal(high))

private val BtcUp = coin("BTC", "67234.50", "2.31", "65100.00", "67890.00")
private val BtcDown = coin("BTC", "66012.08", "-0.87", "65500.00", "67100.00")
private val Eth = coin("ETH", "3512.07", "-0.87", "3450.00", "3580.00")
private val Sol = coin("SOL", "148.32", "4.10", "139.10", "150.00")
private val Xrp = coin("XRP", "0.5234", "0.00", "0.5100", "0.5400")
private val Ada = coin("ADA", "0.4412", "-1.62", "0.4400", "0.4680")

private fun content(
    vararg items: WatchlistItem,
    isStale: Boolean = false,
    refreshFailed: Boolean = false,
) =
    WatchlistUiState.Content(
        items = items.toList(),
        updatedAt = PreviewTime,
        isSample = true,
        isStale = isStale,
        refreshFailed = refreshFailed,
        isRefreshing = false,
    )

@Composable
private fun PreviewHost(state: WatchlistUiState) {
    CoinpulseTheme { AppScaffold { WatchlistScreen(state = state, onRefresh = {}, onCoinClick = {}) } }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PositiveChangePreview() = PreviewHost(content(BtcUp))

@WearPreviewDevices
@Composable
private fun NegativeChangePreview() = PreviewHost(content(BtcDown))

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun MultiCoinPreview() = PreviewHost(content(BtcUp, Eth, Sol, Xrp, Ada))

@WearPreviewDevices
@Composable
private fun LoadingPreview() = PreviewHost(WatchlistUiState.Loading)

@WearPreviewDevices
@Composable
private fun EmptyPreview() = PreviewHost(WatchlistUiState.Empty)

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun ErrorPreview() = PreviewHost(WatchlistUiState.Error(FailureReason.Offline))

@WearPreviewDevices
@Composable
private fun StalePreview() = PreviewHost(content(BtcUp, isStale = true))

@WearPreviewDevices
@Composable
private fun RefreshFailedPreview() = PreviewHost(content(BtcDown, Eth, isStale = true, refreshFailed = true))
