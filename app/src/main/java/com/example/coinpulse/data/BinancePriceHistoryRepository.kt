package com.example.coinpulse.data

import com.example.coinpulse.data.remote.ApiResult
import com.example.coinpulse.data.remote.BinanceMarketApi
import com.example.coinpulse.domain.FailureReason
import com.example.coinpulse.domain.PriceHistoryRepository
import com.example.coinpulse.domain.PriceHistoryResult
import com.example.coinpulse.domain.PricePoint
import java.math.BigDecimal
import java.time.Instant

/** 24h price history from Binance public klines (15-minute candle closes). */
internal class BinancePriceHistoryRepository(private val api: BinanceMarketApi) : PriceHistoryRepository {

    override suspend fun get24hHistory(baseAsset: String, quoteAsset: String): PriceHistoryResult =
        when (val result = api.get24hKlines(baseAsset + quoteAsset)) {
            is ApiResult.Failure -> PriceHistoryResult.Failure(result.kind.toFailureReason())
            is ApiResult.Success -> {
                val points =
                    result.value.map { kline ->
                        val price = kline.close.toDecimalOrNull()
                        if (price == null || price.signum() <= 0) {
                            return PriceHistoryResult.Failure(FailureReason.Unknown)
                        }
                        PricePoint(Instant.ofEpochMilli(kline.openTimeMillis), price)
                    }
                PriceHistoryResult.Success(points.sortedBy { it.time })
            }
        }
}

private fun String.toDecimalOrNull(): BigDecimal? = try { BigDecimal(this) } catch (_: NumberFormatException) { null }
