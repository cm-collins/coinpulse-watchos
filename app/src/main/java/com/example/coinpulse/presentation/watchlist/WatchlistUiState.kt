package com.example.coinpulse.presentation.watchlist

import androidx.compose.runtime.Immutable
import com.example.coinpulse.domain.FailureReason
import com.example.coinpulse.domain.PriceDirection
import com.example.coinpulse.domain.WatchlistItem
import java.time.Instant

@Immutable
sealed interface WatchlistUiState {
    data object Loading : WatchlistUiState

    data object Empty : WatchlistUiState

    data class Content(
        val items: List<WatchlistItem>,
        val updatedAt: Instant,
        val isSample: Boolean,
        val isStale: Boolean,
        /** The last refresh failed; [items] are the previous, possibly stale, data. */
        val refreshFailed: Boolean,
        val isRefreshing: Boolean,
    ) : WatchlistUiState {
        val upCount: Int get() = items.count { it.direction == PriceDirection.Up }
        val downCount: Int get() = items.count { it.direction == PriceDirection.Down }
    }

    data class Error(val reason: FailureReason) : WatchlistUiState
}
