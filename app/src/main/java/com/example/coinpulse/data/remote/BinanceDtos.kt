package com.example.coinpulse.data.remote

import kotlinx.serialization.Serializable

/**
 * Subset of the `GET /api/v3/ticker/24hr` (FULL) item we use. Prices and percentages are JSON
 * strings in the Binance API and are kept as strings here; only the mapper turns them into
 * BigDecimal. Unknown fields are ignored.
 */
@Serializable
internal data class Ticker24hDto(
    val symbol: String,
    val lastPrice: String,
    val priceChangePercent: String,
    val highPrice: String,
    val lowPrice: String,
)

/** One `GET /api/v3/klines` row: open time (ms since epoch) and close price, kept as a string. */
internal data class KlineDto(val openTimeMillis: Long, val close: String)
