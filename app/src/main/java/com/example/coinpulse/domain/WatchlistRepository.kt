package com.example.coinpulse.domain

interface WatchlistRepository {
    /** Never throws for expected failures; those are returned as [WatchlistResult.Failure]. */
    suspend fun getWatchlist(): WatchlistResult
}
