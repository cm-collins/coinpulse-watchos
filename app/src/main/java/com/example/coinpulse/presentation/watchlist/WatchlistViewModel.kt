package com.example.coinpulse.presentation.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.coinpulse.AppContainer
import com.example.coinpulse.domain.FailureReason
import com.example.coinpulse.domain.WatchlistRepository
import com.example.coinpulse.domain.WatchlistResult
import com.example.coinpulse.domain.WatchlistSnapshot
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WatchlistViewModel(
    private val repository: WatchlistRepository,
    private val clock: Clock,
    private val staleAfter: Duration = DEFAULT_STALE_AFTER,
    private val pollInterval: Duration = DEFAULT_POLL_INTERVAL,
) : ViewModel() {

    private sealed interface LoadState {
        data object Loading : LoadState

        data class Loaded(
            val snapshot: WatchlistSnapshot,
            val isRefreshing: Boolean = false,
            val refreshFailed: Boolean = false,
        ) : LoadState

        data class Failed(val reason: FailureReason) : LoadState
    }

    private val loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    private var refreshJob: Job? = null
    private var pollJob: Job? = null
    private var lastFailure: FailureReason? = null

    /**
     * Staleness depends on the clock, so state is re-derived periodically while observed.
     * Polling shares that lifecycle: it runs only while the screen is observed (plus the
     * [SharingStarted.WhileSubscribed] grace period) and stops when the UI goes away.
     */
    val uiState: StateFlow<WatchlistUiState> =
        combine(loadState, ticks()) { state, _ -> state.toUiState(clock.instant()) }
            .onStart { startPolling() }
            .onCompletion { pollJob?.cancel() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), WatchlistUiState.Loading)

    init {
        refresh(showProgress = true)
    }

    fun onRefresh() {
        refresh(showProgress = true)
    }

    /** Returns the in-flight refresh if there is one, so requests never overlap. */
    private fun refresh(showProgress: Boolean): Job {
        refreshJob?.takeIf { it.isActive }?.let { return it }
        return viewModelScope
            .launch {
                if (showProgress) {
                    loadState.update { current ->
                        when (current) {
                            is LoadState.Loaded -> current.copy(isRefreshing = true, refreshFailed = false)
                            else -> LoadState.Loading
                        }
                    }
                }
                when (val result = repository.getWatchlist()) {
                    is WatchlistResult.Success -> {
                        lastFailure = null
                        loadState.value = LoadState.Loaded(result.snapshot)
                    }
                    is WatchlistResult.Failure -> {
                        lastFailure = result.reason
                        loadState.update { current ->
                            when (current) {
                                is LoadState.Loaded -> current.copy(isRefreshing = false, refreshFailed = true)
                                else -> LoadState.Failed(result.reason)
                            }
                        }
                    }
                }
            }
            .also { refreshJob = it }
    }

    /**
     * Background polling: first lets any in-flight load finish, then waits out whatever remains
     * of [pollInterval] since the last data, refreshes silently, and waits a full interval again
     * whether or not the refresh failed (a failure can never cause a request loop). After a
     * rate-limit failure it pauses much longer, as Binance requires clients to back off.
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob =
            viewModelScope.launch {
                refreshJob?.join()
                var wait = nextPollDelay(firstAfterStart = true)
                while (true) {
                    delay(wait.toMillis())
                    refresh(showProgress = false).join()
                    wait = nextPollDelay(firstAfterStart = false)
                }
            }
    }

    private fun nextPollDelay(firstAfterStart: Boolean): Duration =
        when {
            lastFailure == FailureReason.RateLimited -> RATE_LIMITED_PAUSE
            firstAfterStart -> initialPollDelay()
            else -> pollInterval
        }

    private fun initialPollDelay(): Duration {
        val loaded = loadState.value as? LoadState.Loaded ?: return pollInterval
        val sinceUpdate = Duration.between(loaded.snapshot.updatedAt, clock.instant())
        return (pollInterval - sinceUpdate).coerceAtLeast(Duration.ZERO)
    }

    private fun LoadState.toUiState(now: Instant): WatchlistUiState =
        when (this) {
            LoadState.Loading -> WatchlistUiState.Loading
            is LoadState.Failed -> WatchlistUiState.Error(reason)
            is LoadState.Loaded ->
                if (snapshot.items.isEmpty()) {
                    WatchlistUiState.Empty
                } else {
                    WatchlistUiState.Content(
                        items = snapshot.items,
                        updatedAt = snapshot.updatedAt,
                        isSample = snapshot.isSample,
                        isStale = Duration.between(snapshot.updatedAt, now) > staleAfter,
                        refreshFailed = refreshFailed,
                        isRefreshing = isRefreshing,
                    )
                }
        }

    private fun ticks() = flow {
        while (true) {
            emit(Unit)
            delay(TICK_INTERVAL.toMillis())
        }
    }

    companion object {
        val DEFAULT_STALE_AFTER: Duration = Duration.ofMinutes(2)
        val DEFAULT_POLL_INTERVAL: Duration = Duration.ofSeconds(30)
        private val RATE_LIMITED_PAUSE: Duration = Duration.ofMinutes(5)
        private val TICK_INTERVAL: Duration = Duration.ofSeconds(15)
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { WatchlistViewModel(container.watchlistRepository, container.clock) }
        }
    }
}
