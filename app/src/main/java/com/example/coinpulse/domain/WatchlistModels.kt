package com.example.coinpulse.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/** Number of decimals shown for percentage changes; also decides when a change counts as flat. */
const val CHANGE_PERCENT_DECIMALS = 2

enum class PriceDirection { Up, Down, Flat }

data class WatchlistItem(
    val baseAsset: String,
    val quoteAsset: String,
    val price: BigDecimal,
    val changePercent: BigDecimal,
    val lowPrice: BigDecimal,
    val highPrice: BigDecimal,
) {
    /** The Binance spot symbol, e.g. BTCUSDT. */
    val symbol: String get() = baseAsset + quoteAsset

    /** A change that rounds to 0.00% is [PriceDirection.Flat], matching what the UI displays. */
    val direction: PriceDirection
        get() = when (changePercent.setScale(CHANGE_PERCENT_DECIMALS, RoundingMode.HALF_UP).signum()) {
            1 -> PriceDirection.Up
            -1 -> PriceDirection.Down
            else -> PriceDirection.Flat
        }

    /**
     * Where [price] sits between the 24h low (0f) and high (1f), clamped; null when there is no
     * range to show. Computed in BigDecimal; the Float is only for drawing the marker.
     */
    val rangePosition: Float?
        get() {
            val span = highPrice - lowPrice
            if (span.signum() <= 0) return null
            val position = (price - lowPrice).divide(span, 4, RoundingMode.HALF_UP)
            return position.coerceIn(BigDecimal.ZERO, BigDecimal.ONE).toFloat()
        }
}

data class WatchlistSnapshot(
    val items: List<WatchlistItem>,
    val updatedAt: Instant,
    /** True when the data is built-in sample data rather than real market data. */
    val isSample: Boolean,
)

enum class FailureReason { Offline, RateLimited, Unknown }

sealed interface WatchlistResult {
    data class Success(val snapshot: WatchlistSnapshot) : WatchlistResult
    data class Failure(val reason: FailureReason) : WatchlistResult
}
