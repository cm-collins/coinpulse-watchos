package com.example.coinpulse.presentation.watchlist

import com.example.coinpulse.domain.FailureReason
import com.example.coinpulse.domain.WatchlistItem
import com.example.coinpulse.domain.WatchlistRepository
import com.example.coinpulse.domain.WatchlistResult
import com.example.coinpulse.domain.WatchlistSnapshot
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals

import org.junit.Assert.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistViewModelTest {

    private val start = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = MutableClock(start)
    private val btc =
        WatchlistItem("BTC", "USDT", BigDecimal("67234.50"), BigDecimal("2.31"), BigDecimal("65100.00"), BigDecimal("67890.00"))

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun snapshot(items: List<WatchlistItem> = listOf(btc), at: Instant = clock.instant()) =
        WatchlistSnapshot(items, updatedAt = at, isSample = true)

    private fun TestScope.viewModel(repository: FakeRepository): WatchlistViewModel =
        WatchlistViewModel(repository, clock, staleAfter = Duration.ofMinutes(2)).also {
            // Keep the WhileSubscribed upstream active for the duration of the test.
            it.uiState.onEach {}.launchIn(backgroundScope)
        }

    @Test
    fun startsLoadingThenShowsContent() = runTest {
        val repository = FakeRepository(WatchlistResult.Success(snapshot()))
        val vm = viewModel(repository)
        assertEquals(WatchlistUiState.Loading, vm.uiState.value)

        runCurrent()

        val state = assertIs<WatchlistUiState.Content>(vm.uiState.value)
        assertEquals(listOf(btc), state.items)
        assertTrue(state.isSample)
        assertEquals(false, state.isStale)
    }

    @Test
    fun emptySnapshotShowsEmpty() = runTest {
        val vm = viewModel(FakeRepository(WatchlistResult.Success(snapshot(items = emptyList()))))
        runCurrent()
        assertEquals(WatchlistUiState.Empty, vm.uiState.value)
    }

    @Test
    fun failureWithoutDataShowsError() = runTest {
        val vm = viewModel(FakeRepository(WatchlistResult.Failure(FailureReason.Offline)))
        runCurrent()
        assertEquals(WatchlistUiState.Error(FailureReason.Offline), vm.uiState.value)
    }

    @Test
    fun retryAfterErrorShowsLoadingThenContent() = runTest {
        val repository = FakeRepository(WatchlistResult.Failure(FailureReason.RateLimited))
        val vm = viewModel(repository)
        runCurrent()
        assertIs<WatchlistUiState.Error>(vm.uiState.value)

        repository.result = WatchlistResult.Success(snapshot())
        repository.gate = CompletableDeferred()
        vm.onRefresh()
        runCurrent()
        assertEquals(WatchlistUiState.Loading, vm.uiState.value)

        repository.gate!!.complete(Unit)
        runCurrent()
        assertIs<WatchlistUiState.Content>(vm.uiState.value)
    }

    @Test
    fun refreshKeepsContentAndFlagsRefreshing() = runTest {
        val repository = FakeRepository(WatchlistResult.Success(snapshot()))
        val vm = viewModel(repository)
        runCurrent()

        repository.gate = CompletableDeferred()
        vm.onRefresh()
        runCurrent()
        assertTrue(assertIs<WatchlistUiState.Content>(vm.uiState.value).isRefreshing)

        repository.gate!!.complete(Unit)
        runCurrent()
        assertEquals(false, assertIs<WatchlistUiState.Content>(vm.uiState.value).isRefreshing)
    }

    @Test
    fun failedRefreshKeepsPreviousDataAndFlagsFailure() = runTest {
        val repository = FakeRepository(WatchlistResult.Success(snapshot()))
        val vm = viewModel(repository)
        runCurrent()

        repository.result = WatchlistResult.Failure(FailureReason.Offline)
        vm.onRefresh()
        runCurrent()

        val state = assertIs<WatchlistUiState.Content>(vm.uiState.value)
        assertEquals(listOf(btc), state.items)
        assertTrue(state.refreshFailed)
        assertEquals(false, state.isRefreshing)
    }

    @Test
    fun dataBecomesStaleAsTimePasses() = runTest {
        val vm = viewModel(FakeRepository(WatchlistResult.Success(snapshot())))
        runCurrent()
        assertEquals(false, assertIs<WatchlistUiState.Content>(vm.uiState.value).isStale)

        clock.advance(Duration.ofMinutes(3))
        advanceTimeBy(Duration.ofSeconds(20).toMillis())
        runCurrent()

        assertTrue(assertIs<WatchlistUiState.Content>(vm.uiState.value).isStale)
    }

    @Test
    fun overlappingRefreshRequestsAreIgnored() = runTest {
        val repository = FakeRepository(WatchlistResult.Success(snapshot()))
        repository.gate = CompletableDeferred()
        val vm = viewModel(repository)
        runCurrent()

        vm.onRefresh()
        vm.onRefresh()
        repository.gate!!.complete(Unit)
        runCurrent()

        assertEquals(1, repository.calls)
    }

    // --- Polling (virtual time; default interval is 30s) ---

    private fun TestScope.advanceBy(seconds: Long) {
        advanceTimeBy(Duration.ofSeconds(seconds).toMillis())
        runCurrent()
    }

    @Test
    fun pollsPeriodicallyWhileObserved() = runTest {
        val repository = FakeRepository(WatchlistResult.Success(snapshot()))
        viewModel(repository)
        runCurrent()
        assertEquals(1, repository.calls)

        advanceBy(30)
        assertEquals(2, repository.calls)
        advanceBy(30)
        assertEquals(3, repository.calls)
    }

    @Test
    fun backgroundPollDoesNotShowRefreshingState() = runTest {
        val repository = FakeRepository(WatchlistResult.Success(snapshot()))
        val vm = viewModel(repository)
        runCurrent()

        repository.gate = CompletableDeferred()
        advanceBy(30) // poll starts and is now waiting on the gate

        assertEquals(2, repository.calls)
        assertEquals(false, assertIs<WatchlistUiState.Content>(vm.uiState.value).isRefreshing)
        repository.gate!!.complete(Unit)
        runCurrent()
    }

    @Test
    fun doesNotPollWhenNothingObservesTheState() = runTest {
        val repository = FakeRepository(WatchlistResult.Success(snapshot()))
        WatchlistViewModel(repository, clock) // never collected
        runCurrent()

        advanceBy(300)

        assertEquals(1, repository.calls) // only the initial load
    }

    @Test
    fun stopsPollingOnceNoLongerObserved() = runTest {
        val repository = FakeRepository(WatchlistResult.Success(snapshot()))
        val vm = WatchlistViewModel(repository, clock)
        val collector = vm.uiState.onEach {}.launchIn(backgroundScope)
        runCurrent()
        assertEquals(1, repository.calls)

        collector.cancel()
        advanceBy(300)

        assertEquals(1, repository.calls)
    }

    @Test
    fun failuresDoNotCauseATightRetryLoop() = runTest {
        val repository = FakeRepository(WatchlistResult.Failure(FailureReason.Offline))
        viewModel(repository)
        runCurrent()
        assertEquals(1, repository.calls)

        advanceBy(95) // polls at 30s, 60s and 90s only

        assertEquals(4, repository.calls)
    }

    @Test
    fun rateLimitPausesPollingForMuchLongerThanTheInterval() = runTest {
        val repository = FakeRepository(WatchlistResult.Failure(FailureReason.RateLimited))
        viewModel(repository)
        runCurrent()
        assertEquals(1, repository.calls)

        advanceBy(240) // well past the normal interval, still inside the pause
        assertEquals(1, repository.calls)

        advanceBy(120) // pause (5 min) has elapsed
        assertEquals(2, repository.calls)
    }

    @Test
    fun observingAgainAfterALongBackgroundRefreshesImmediately() = runTest {
        val repository = FakeRepository(WatchlistResult.Success(snapshot()))
        val vm = WatchlistViewModel(repository, clock)
        val first = vm.uiState.onEach {}.launchIn(backgroundScope)
        runCurrent()
        assertEquals(1, repository.calls)

        first.cancel()
        clock.advance(Duration.ofMinutes(5))
        advanceBy(300)
        assertEquals(1, repository.calls)

        vm.uiState.onEach {}.launchIn(backgroundScope)
        runCurrent()

        assertEquals(2, repository.calls)
    }

    private class FakeRepository(var result: WatchlistResult) : WatchlistRepository {
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0

        override suspend fun getWatchlist(): WatchlistResult {
            calls++
            gate?.await()
            return result
        }
    }

    private class MutableClock(private var now: Instant) : Clock() {
        fun advance(duration: Duration) {
            now += duration
        }

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?): Clock = this

        override fun instant(): Instant = now
    }
}
