package com.example.coinpulse.data

import com.example.coinpulse.data.remote.ApiResult
import com.example.coinpulse.data.remote.BinanceMarketApi
import com.example.coinpulse.data.remote.Ticker24hDto
import com.example.coinpulse.domain.FailureReason
import com.example.coinpulse.domain.WatchlistItem
import com.example.coinpulse.domain.WatchlistRepository
import com.example.coinpulse.domain.WatchlistResult
import com.example.coinpulse.domain.WatchlistSnapshot
import java.math.BigDecimal
import java.time.Clock

/** A Binance spot pair we track, e.g. BTC quoted in USDT. The Binance symbol is base + quote. */
data class TrackedPair(val baseAsset: String, val quoteAsset: String) {
    val symbol: String get() = baseAsset + quoteAsset
}

/** Live public market data from Binance's market-data-only host. */
internal class BinanceWatchlistRepository(
    private val api: BinanceMarketApi,
    private val pairs: List<TrackedPair>,
    private val clock: Clock,
) : WatchlistRepository {

    override suspend fun getWatchlist(): WatchlistResult {
        if (pairs.isEmpty()) return WatchlistResult.Success(snapshot(emptyList()))

        return when (val result = api.get24hTickers(pairs.map { it.symbol })) {
            is ApiResult.Failure -> WatchlistResult.Failure(result.kind.toFailureReason())
            is ApiResult.Success -> {
                val items = map(result.value)
                if (items == null) {
                    WatchlistResult.Failure(FailureReason.Unknown)
                } else {
                    WatchlistResult.Success(snapshot(items))
                }
            }
        }
    }

    /** Returns null if any requested pair is missing or has an unparseable/non-positive price. */
    private fun map(tickers: List<Ticker24hDto>): List<WatchlistItem>? {
        val bySymbol = tickers.associateBy { it.symbol }
        return pairs.map { pair ->
            val dto = bySymbol[pair.symbol] ?: return null
            val price = dto.lastPrice.toBigDecimalOrNull() ?: return null
            val change = dto.priceChangePercent.toBigDecimalOrNull() ?: return null
            val low = dto.lowPrice.toBigDecimalOrNull() ?: return null
            val high = dto.highPrice.toBigDecimalOrNull() ?: return null
            if (price.signum() <= 0) return null
            WatchlistItem(pair.baseAsset, pair.quoteAsset, price, change, low, high)
        }
    }

    private fun snapshot(items: List<WatchlistItem>) =
        WatchlistSnapshot(items = items, updatedAt = clock.instant(), isSample = false)
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try { BigDecimal(this) } catch (_: NumberFormatException) { null }
