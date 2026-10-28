package com.example.coinpulse.domain

import java.math.BigDecimal
import java.time.Instant

data class PricePoint(val time: Instant, val price: BigDecimal)

sealed interface PriceHistoryResult {
    data class Success(val points: List<PricePoint>) : PriceHistoryResult
    data class Failure(val reason: FailureReason) : PriceHistoryResult
}

interface PriceHistoryRepository {
    /** Prices over the last 24 hours, oldest first. Expected failures are returned, not thrown. */
    suspend fun get24hHistory(baseAsset: String, quoteAsset: String): PriceHistoryResult
}
