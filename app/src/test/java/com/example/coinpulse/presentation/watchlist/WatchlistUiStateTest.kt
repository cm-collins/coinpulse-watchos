package com.example.coinpulse.presentation.watchlist

import com.example.coinpulse.domain.WatchlistItem
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistUiStateTest {
    private fun coin(change: String) =
        WatchlistItem("X", "USDT", BigDecimal.ONE, BigDecimal(change), BigDecimal.ONE, BigDecimal.ONE)

    private fun content(vararg changes: String) =
        WatchlistUiState.Content(
            items = changes.map(::coin),
            updatedAt = Instant.EPOCH,
            isSample = false,
            isStale = false,
            refreshFailed = false,
            isRefreshing = false,
        )

    @Test
    fun breadthCountsUpAndDownAndIgnoresFlat() {
        val state = content("2.31", "-0.87", "4.10", "0.00", "0.004", "-1.62")

        assertEquals(2, state.upCount)
        assertEquals(2, state.downCount)
    }
}
